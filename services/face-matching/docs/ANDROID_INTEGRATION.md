# Android integration

How the VARI Sahayak Android app reaches face matching — and why it does not reach the face
service directly.

> [!IMPORTANT]
> The **shape** of the path below is correct and current: app → Supabase edge function →
> face service → Mongo, with the key never leaving the server.
>
> The **route and secret names** below described *this* directory's service, which is not
> what is deployed. See the banner in [../README.md](../README.md). The deployed contract is
> `/v1/face/register`, `/v1/face/match`, `/v1/face/detect`, authenticated with `X-API-Key`,
> configured as `FACE_API_URL` / `FACE_API_KEY`. This file has been corrected to match.

---

## The path

```
Android app
   │  photograph (base64) + report client id
   ▼
Supabase edge function  (supabase/functions/process-face)
   │  authorises the caller under RLS
   │  X-API-Key
   ▼
Face-matching service   (this repository, Docker on a GCE VM)
   │  embedding stored / compared
   ▼
MongoDB                 (face vectors only)
```

The app talks to Supabase. Supabase talks to this service. The app never holds
`FACE_API_KEY` and never opens a socket to the face service.

That indirection is the whole security design, and it is worth being explicit about why:

1. **A key in an APK is a public key.** Anyone can unzip a release build and read it. If the
   app called this service directly, the shared secret would be on every device, and so
   would the ability to enrol arbitrary faces and query the whole enrolled population.
2. **Authorisation needs the database.** The edge function reads the Lost & Found report
   under the caller's row-level security before doing anything, so a volunteer can only
   trigger processing for a report they are actually permitted to see. This service has no
   idea who a volunteer is and cannot make that judgement.
3. **The blast radius stays small.** This service accepts uploads and holds biometric
   vectors. After this refactor it has no connection to the pilgrim database at all — the
   worst an attacker who reaches the port with a valid key can do is read face profiles, not
   names, phone numbers or locations.

Do not add a direct path from the app to this service. If you need one for a spike, use a
debug build with a separate key and a separate deployment.

---

## Configuring the URL

**The production address is never committed.** It is a placeholder everywhere:

```
FACE_API_URL=http://<face-service-host>:8080/
```

### Where it belongs

The service URL is configured **once, on the Supabase side**, because Supabase is the only
thing that calls it:

```bash
supabase secrets set FACE_API_URL="http://<face-service-host>"
supabase secrets set FACE_API_KEY="<the same value as the service's FACE_API_KEY>"
```

The names match what the deployed service calls them, so one name means one thing on both
sides of the hop.

A trailing slash is tolerated — the edge function strips it before appending a route — but
leave it off anyway. The routes it appends are `/v1/face/register`, `/v1/face/match` and
`/v1/face/detect`.

The older `FACE_SERVICE_URL` / `FACE_SERVICE_TOKEN` are still read as a fallback so an
existing deployment does not break on upgrade, but they are deprecated and should be
removed once the new names are set. `FACE_SERVICE_TOKEN` was sent as an `X-Service-Token`
header; the deployed service answers that with `401 {"error":"Unauthorized"}`, because it
reads `X-API-Key` and nothing else.

### If you do add an app-side URL

Should a debug build ever need one, keep it out of source the same way every other secret in
this project is kept out. The repository already reads client configuration from a
gitignored `.env` at the root, through `secret()` in `app/build.gradle.kts`:

```properties
# .env at the repository root — gitignored, never committed
FACE_API_URL=http://<face-service-host>
```

```kotlin
// app/build.gradle.kts, defaultConfig
buildConfigField("String", "FACE_API_URL", "\"${secret("FACE_API_URL")}\"")
```

Then read `BuildConfig.FACE_API_URL` — never a literal in a Kotlin file. An IP address in
source is one `git push` away from being public, and re-issuing a VM address is a great deal
more disruptive than editing a `.env`.

Because the deployment is plain HTTP on a VM port, a debug build pointing at it also needs a
network-security-config exception for that host. Do not disable cleartext globally; scope it
to the one domain, in a `debug`-only manifest.

---

## What the app sends and receives

The app calls the edge function, not this service. Request:

```json
{
  "report_client_id": "<the Lost & Found report's client id>",
  "action": "enrol",
  "image": "<base64 jpeg>"
}
```

`action` is `enrol` (also spelled `enroll`) or `compare`. Response, for either:

```json
{
  "ok": true,
  "status": "READY",
  "message": "Photo processed.",
  "face_available": true,
  "distances": {},
  "eligible": [],
  "sample_count": 9
}
```

`status` maps one-to-one onto the Android `FaceMatchStatus` enum and onto
`lost_found_items.face_match_status`:

| `status`              | Meaning for the volunteer                                              |
| --------------------- | ---------------------------------------------------------------------- |
| `READY`               | The photo is contributing to matching.                                 |
| `NO_FACE`             | No face found. Offer another photo; do not block the report.           |
| `MULTIPLE_FACES`      | Ask for a photo of just the one person.                                |
| `INVALID_IMAGE`       | Ask for a clearer photo.                                               |
| `SERVICE_UNAVAILABLE` | Say nothing about faces. The report is saved and matches on the rest.  |

**The status write now happens in the edge function.** Before this refactor the Python
service wrote `face_match_status` into PostgreSQL itself; it no longer has a database
connection, so the edge function records the status it gets back, under the caller's RLS.
Deploy the edge function and the service together — an old edge function against the new
service will process photographs correctly and never record the outcome.

`SERVICE_UNAVAILABLE` is deliberately *not* written to the report. It is transient, and
persisting it would turn a retryable outage into a permanent state that the next attempt
could not tell apart from a genuinely unusable photograph.

---

## Rules the client must keep

**A photograph is never mandatory.** This is the product rule, and it is enforced in the
schema: every descriptive column on `lost_found_items` is nullable so a parent who reaches a
volunteer at dusk with no picture and half a description can still file a report the
matching engine can work with. Face processing must never gate the save.

**Save first, process second.** The report is written locally and enqueued for sync before
any face call is made. Face processing enriches a report that already exists; a failure at
this step changes a status field and nothing else.

**Never send an embedding, never expect one.** The app uploads photographs. A vector never
travels in either direction — not in a response, not in a log, not in a crash report.

**Never show a raw failure.** Every message a volunteer sees comes from the table above.
Nothing from the far side is rendered verbatim.

**Photographs are not stored by this service.** It computes an embedding and discards the
image. The photo the volunteer took lives wherever the app already keeps it
(`lost_found_items.photo_path`), under the existing rules.

---

## Closing a case

When a Lost & Found case reaches `REUNITED` or `CLOSED`, delete the face profile:

```
DELETE /persons/<report_client_id>
```

A reunited child's biometric vector has no reason to stay on a server. This is not wired
automatically today — a scheduled job or an edge function triggered on the status change is
the natural place for it, and it is the one piece of this integration still to build.

---

## Checking a deployment

From the VM, or from anywhere allowed through the firewall:

```bash
curl -s http://<face-service-host>:8080/health
# {"ok":true,"status":"healthy"}
```

Then end to end, from a device or emulator signed in as a volunteer: file a Lost & Found
report with a photograph and confirm `lost_found_items.face_match_status` moves from
`PENDING` to `READY`. If it stays `PENDING`, check in this order —

1. `supabase functions logs process-face` — is `FACE_API_URL` set, and is the call
   reaching the VM at all?
2. `docker compose logs face-matching` on the VM — did a request arrive? A `401` means the
   two secrets disagree.
3. The GCP firewall rule — can Supabase's egress reach the port?
4. Cold start — the first request after a container restart downloads model weights and can
   take close to a minute. The edge function's timeout is 25 seconds, so the very first
   attempt after a deploy may legitimately fail. Warm the service with a `/health` call and
   one throwaway enrolment before relying on it.
