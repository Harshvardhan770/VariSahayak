# LiveKit — walkie-talkie voice server

Self-hosted single-node LiveKit on a Google Cloud Compute Engine VM. This is what carries
audio between phones when a volunteer holds the push-to-talk button.

Everything below is run by a human, once. The Android app and the `livekit-token` edge
function are already written against it.

---

## What this is and is not

**Is:** one Docker container on one VM, reachable on a bare IP. Good for a demo and a small
pilot.

**Is not:** production. There is no TLS, no domain, no autoscaling, no recording, and no
server-side mute. Signalling runs over `ws://`, which means the join token, room name, and
participant identities are readable by anyone on the network path. The *audio* is not —
WebRTC media is SRTP-encrypted regardless of how the peers were introduced. Making this
production-grade means putting a domain and a certificate in front of it and switching the
app to `wss://`.

---

## 1. Create the VM

```bash
# Pick the region closest to where the demo will actually happen.
export ZONE=asia-south1-a
export VM=varisahayak-livekit

gcloud compute instances create "$VM" \
  --zone="$ZONE" \
  --machine-type=e2-small \
  --image-family=ubuntu-2404-lts-amd64 \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=20GB \
  --tags=livekit
```

`e2-small` (2 vCPU burst, 2 GB) is ample: LiveKit is a selective forwarding unit, so it
relays audio packets rather than mixing them. A handful of 32 kbps Opus streams is nothing.

## 2. Open the ports

Three rules, and they are not interchangeable — signalling and media are different
transports, and a firewall that allows only the first produces the worst possible symptom:
the app connects, reports itself Connected, and carries no sound.

```bash
# WebSocket signalling. Without this nothing connects at all.
gcloud compute firewall-rules create livekit-signalling \
  --allow=tcp:7880 --target-tags=livekit --source-ranges=0.0.0.0/0

# Media over UDP. This is the one that carries voice.
gcloud compute firewall-rules create livekit-media-udp \
  --allow=udp:7882 --target-tags=livekit --source-ranges=0.0.0.0/0

# TCP media fallback, for phones on networks that block UDP — which describes a lot of
# public and corporate Wi-Fi. Skip it and the radio works on mobile data and fails
# mysteriously on the venue guest network.
gcloud compute firewall-rules create livekit-media-tcp \
  --allow=tcp:7881 --target-tags=livekit --source-ranges=0.0.0.0/0
```

## 3. Note the external IP

```bash
gcloud compute instances describe "$VM" --zone="$ZONE" \
  --format='get(networkInterfaces[0].accessConfigs[0].natIP)'
```

Write it down. It goes in three places: `livekit.yaml`, the repository-root `.env`, and a
Supabase secret.

> The IP is ephemeral by default and **changes if the VM is stopped and started**. For a
> demo spanning more than one day, reserve it first:
> `gcloud compute addresses create varisahayak-livekit-ip --region=asia-south1`

## 4. Install Docker and write the config

```bash
gcloud compute ssh "$VM" --zone="$ZONE"
```

Then, on the VM:

```bash
sudo apt-get update && sudo apt-get install -y docker.io
sudo systemctl enable --now docker

# Generate the key pair. These are the credentials that sign join tokens: the secret is the
# only thing standing between a stranger and the emergency channel.
LIVEKIT_API_KEY="API$(openssl rand -hex 6)"
LIVEKIT_API_SECRET="$(openssl rand -hex 24)"

echo "LIVEKIT_API_KEY=$LIVEKIT_API_KEY"
echo "LIVEKIT_API_SECRET=$LIVEKIT_API_SECRET"
```

**Copy both values now.** The secret is needed off this machine exactly once, for
`supabase secrets set` in step 6, and then never again. It must not go into `.env`, into
Gradle, into a Kotlin file, or anywhere else that ends up in the APK — a signing key in a
shipped app is a public key.

```bash
sudo mkdir -p /opt/livekit
sudo tee /opt/livekit/livekit.yaml >/dev/null <<YAML
port: 7880

rtc:
  # Single UDP port rather than a 50000-60000 range: one firewall rule instead of ten
  # thousand, which matters when the rule has to be explained to somebody at 2am.
  udp_port: 7882
  # TCP fallback for clients whose network blocks UDP.
  tcp_port: 7881
  # Advertise the VM public address. Without this LiveKit hands out 10.x.x.x candidates
  # that no phone on the internet can route to, and calls connect but stay silent.
  use_external_ip: true

keys:
  $LIVEKIT_API_KEY: $LIVEKIT_API_SECRET

audio:
  # Speaker detection drives two things in the app: the "Amit + 2 others" label and the
  # waveform. The defaults are tuned for video conferencing and are too sluggish for a
  # radio, where the label has to appear while somebody is still talking.
  #
  # active_level is a sensitivity threshold in dBov, where HIGHER is more sensitive. 40
  # picks up someone speaking normally at arm length in a noisy outdoor crowd.
  active_level: 40
  min_percentile: 40
  # Milliseconds between speaker updates. 200 is fast enough that the waveform tracks
  # speech; the app samples every 60ms and smooths between updates.
  update_interval: 200
  smooth_intervals: 2

logging:
  level: info
YAML
```

## 5. Run it

```bash
sudo docker run -d \
  --name livekit \
  --restart unless-stopped \
  --network host \
  -v /opt/livekit/livekit.yaml:/livekit.yaml \
  livekit/livekit-server:latest \
  --config /livekit.yaml
```

`--network host` rather than published ports: WebRTC negotiates its own addresses, and
Docker NAT would have LiveKit advertise container-internal candidates.

Check it:

```bash
sudo docker logs livekit | tail -20        # expect "starting LiveKit server"
curl -s http://localhost:7880              # expect: OK
```

And from your laptop — the check that actually matters, because it proves the firewall rule
works and not just the container:

```bash
curl -s http://<EXTERNAL_IP>:7880          # expect: OK
```

## 6. Give Supabase the credentials

The API secret lives here and nowhere else. The `livekit-token` edge function reads it to
sign join tokens; the app never sees it.

```bash
# From the repository root.
npx supabase secrets set \
  LIVEKIT_URL=ws://<EXTERNAL_IP>:7880 \
  LIVEKIT_API_KEY=<the key from step 4> \
  LIVEKIT_API_SECRET=<the secret from step 4>

npx supabase functions deploy livekit-token
```

`npx supabase` rather than a global `supabase`: the global install in this environment has
been unreliable. If the CLI will not run at all, both steps can be done from the Supabase
dashboard — Edge Functions → Secrets, and Edge Functions → Deploy new function, pasting
`supabase/functions/livekit-token/index.ts`.

Verify the function answers. This needs a **user** access token — the JWT Supabase issues to
a signed-in account. Not the anon key: the anon key identifies the app, `verify_jwt` wants a
person, and sending the anon key here returns 401.

Get one by signing in over the Auth REST API with any existing volunteer account:

```bash
# From the repository root. SUPABASE_URL and SUPABASE_ANON_KEY come from .env.
set -a; . ./.env; set +a

ACCESS_TOKEN=$(curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"email":"volunteer@example.com","password":"<their password>"}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

echo "${ACCESS_TOKEN:0:24}…"   # sanity check: should start with eyJ
```

If that prints a KeyError instead of a token, the sign-in itself failed — rerun the first
curl without the pipe and read the `error_description`.

Then call the function:

```bash
curl -s -X POST "$SUPABASE_URL/functions/v1/livekit-token" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"room":"comm-1"}'
```

The access token expires after an hour (`jwt_expiry = 3600` in `supabase/config.toml`), so
re-run the sign-in if a later test suddenly starts returning 401.

Expect `{"ok":true,"token":"eyJ...","identity":"<uuid>","name":"<display name>",...}`.
A 401 means the token was missing or expired. `{"ok":false,"message":"The radio is not
configured."}` means the secrets did not land.

## 7. Point the app at it

In the repository-root `.env`, which is git-ignored:

```
LIVEKIT_URL=ws://<EXTERNAL_IP>:7880
```

Then rebuild. This value becomes `BuildConfig.LIVEKIT_URL` **and** the single host named in
the debug build cleartext exception, which is generated from it by
`:app:generateLivekitNetworkConfig`. Changing the IP without rebuilding leaves the app
pointed at the old address.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Leave `LIVEKIT_URL` empty and the widget reports "Radio not configured" and disables
push-to-talk. That is intentional: a PTT button that looks live and carries nothing is the
most dangerous thing that screen can show.

---

## Two-device check

Two phones, both signed in as different accounts, both on the same channel:

1. Open the radio panel on each — the toggle in the top bar. Grant the microphone.
2. The subtitle should show a member count, not "Connecting…".
3. Hold PTT on device A. Device B should hear it inside a second, and B's subtitle should
   name A's display name.
4. Hold both at once. Both should be heard, and each subtitle should name the other.
5. Switch A to Medical. A leaves the Comm 1 room and B stops hearing A.
6. Hold PTT for 30 seconds without releasing. It should unkey itself and the button should
   return to "Hold to talk".

## When it does not work

| Symptom | Cause |
|---|---|
| Subtitle stuck on "Connecting…" | Port 7880 blocked, or the container is not running. `curl http://<IP>:7880` from a laptop. |
| Subtitle reads "Disconnected" immediately | The token call failed. Check `npx supabase functions logs livekit-token`. |
| Subtitle reads "Radio not configured" | `LIVEKIT_URL` is empty in `.env`, or the APK predates setting it. |
| Connects and shows the right member count, but no audio | Media ports. UDP 7882 blocked, or `use_external_ip` is false and LiveKit is advertising a 10.x address. |
| Works on mobile data, silent on venue Wi-Fi | UDP blocked by that network. This is what the TCP 7881 rule is for. |
| Speaker label shows a UUID | That account has no `display_name` on its profile row. |
| Subtitle reads "Disconnected" on every channel after an app update | The `livekit-token` allowlist is stale. The default room was renamed `route-main` → `comm-1`; redeploy the function (step 6). |
| Audio comes out of the earpiece, not the speaker | A Bluetooth or wired headset is connected — that wins on purpose. Disconnect it and the loudspeaker takes over. |
| Connection drops when the app is backgrounded | Known and out of scope — there is no foreground service. |

## Teardown

```bash
gcloud compute instances delete "$VM" --zone="$ZONE"
gcloud compute firewall-rules delete livekit-signalling livekit-media-udp livekit-media-tcp
```
