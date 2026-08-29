# VARI Sahayak — Lost & Found face-matching service

An API-only Python service that turns photographs into face embeddings, stores them in
MongoDB, and ranks a queried face against the ones already enrolled. It exists so a
volunteer holding a found child at a help point can be shown the handful of open "missing"
reports that might be them.

It is a **computation** service. It is not the system of record: VARI Sahayak's Lost &
Found reports live in the project's PostgreSQL schema and are reached through the Android
app and Supabase. This service holds face vectors and nothing else, in its own database,
with no route back to pilgrim records.

**Facial similarity never reunites anybody.** Every result here is a candidate for a human
to review. Nothing in this service confirms a match, and the review screen in the app is
where a person decides.

---

## What changed in this refactor

This service was adapted from a student-attendance face-recognition backend. That domain is
gone: there is no PRN or GR number, no branch, division, year or class code, and no
attendance logic anywhere in the codebase. A record is a `person_id` — an opaque string the
caller chooses — plus which side of the Lost & Found board it sits on.

Storage moved too. The previous revision wrote embeddings into `public.lost_found_face_data`
over a PostgreSQL connection using the database service role. **All PostgreSQL and Supabase
dependencies have been removed**; MongoDB is now the only datastore, and this process holds
no database credential that can reach pilgrim data. `psycopg` is no longer a dependency, and
a test asserts it can never come back.

The vision pipeline is unchanged: OpenCV decode and preprocessing, RetinaFace/OpenCV
detection, DeepFace with Facenet embeddings, augmented averaging, cosine-distance matching.
Embeddings produced before and after this refactor are directly comparable.

| Before                   | Now                        | Notes                                              |
| ------------------------ | -------------------------- | -------------------------------------------------- |
| `GET /health`            | `GET /health`              | Unchanged.                                          |
| —                        | `GET /`                    | New. Service-running status.                        |
| —                        | `POST /detect_faces`       | New. Locations and crops, no embedding.             |
| `POST /enrol`            | `POST /enroll`             | `/enrol` still works; `report_client_id` accepted.  |
| —                        | `POST /recognize`          | New. Photo-against-everybody, multi-face.           |
| `POST /compare`          | `POST /compare`            | Same shape, now backed by MongoDB.                  |
| `GET /students`          | `GET /persons`             | Replaced. Old path returns 410.                     |
| `DELETE /students/<prn>` | `DELETE /persons/<id>`     | Replaced. Old path returns 410.                     |

Because the service can no longer write to PostgreSQL, the Supabase edge function
(`supabase/functions/process-face`) now records `lost_found_items.face_match_status` itself
from the status the service returns. Deploy the two together.

`public.lost_found_face_data` in the SQL schema is left in place but is no longer written or
read by anything. Drop it once you are satisfied with the MongoDB deployment.

---

## Endpoints

Full request and response shapes: [`docs/API.md`](docs/API.md).

| Method   | Route                 | Auth | Description                                                     |
| -------- | --------------------- | ---- | --------------------------------------------------------------- |
| `GET`    | `/`                   | no   | Basic service-running status.                                   |
| `GET`    | `/health`             | no   | Liveness, for the container health check and load balancer.     |
| `POST`   | `/detect_faces`       | yes  | Locate every face; return positions and optional crops.         |
| `POST`   | `/enroll`             | yes  | Register a Lost or Found person's averaged face embedding.      |
| `POST`   | `/recognize`          | yes  | Match faces in a photo against the enrolled profiles.           |
| `POST`   | `/compare`            | yes  | Rank the opposite side of the board for one enrolled record.    |
| `GET`    | `/persons`            | yes  | List enrolled profiles. Never includes embeddings.              |
| `GET`    | `/persons/<id>`       | yes  | One profile's non-sensitive fields.                             |
| `DELETE` | `/persons/<id>`       | yes  | Delete a person's face profile.                                 |

Authenticated routes require the shared secret on `X-API-Key`. `X-Service-Token` is also
accepted so a backend deployed before the rename keeps working.

---

## Configuration

Everything is environment-driven — MongoDB, the matching threshold, the API key, image
limits and augmentation. Copy [`.env.example`](.env.example) to `.env` and fill it in; every
setting is documented there.

The four that matter most:

```bash
MONGODB_URI=mongodb+srv://<user>:<password>@<cluster-host>/
FACE_API_KEY=              # openssl rand -hex 32 — empty means refuse every request
MATCH_THRESHOLD=0.40       # cosine distance; lower is stricter
PORT=8080
```

`MATCH_THRESHOLD` is an **inherited engineering starting point, not a validated identity
threshold**. Tune it against representative Wari photographs before relying on it
operationally, and expect the right value to differ between a controlled help-point photo
and a phone snap in a moving crowd. Callers can override it per request.

Changing `FACE_MODEL` invalidates every stored embedding — vectors from different models are
not comparable, and every profile would need re-enrolling.

---

## Running it

### Docker on a Google Cloud Compute Engine VM

This is the deployment target: a Linux Compute Engine VM running Docker, with the service
behind the VM firewall or a reverse proxy.

```bash
git clone <repo> && cd services/face-matching
cp .env.example .env && $EDITOR .env      # set MONGODB_URI and FACE_API_KEY
docker compose up -d --build
docker compose logs -f face-matching      # first boot downloads model weights
```

The first request after a cold start downloads the Facenet and RetinaFace weights — expect
up to a minute, which is why the health check has a 90-second start period and gunicorn's
timeout is 120 seconds. The weights persist in the `deepface-weights` volume, so later
restarts are fast.

**Exposure.** `docker-compose.yml` binds the published port to `127.0.0.1` by default, which
is what you want with a reverse proxy terminating TLS on the same VM. To publish it
directly instead:

```bash
FACE_BIND=0.0.0.0 FACE_PORT=8080 docker compose up -d
```

and add a GCP firewall rule allowing that port **only from the addresses that legitimately
call it**. This service accepts photographs and holds biometric vectors; it should never be
reachable from the open internet, and the API key is the last line of defence, not the
first.

**Sizing.** Keep `GUNICORN_WORKERS=1` unless the VM has the RAM for more — each worker loads
its own copy of the model weights, so a second worker on a 2-vCPU machine buys queueing
rather than throughput. Scale with `GUNICORN_THREADS` first. An `e2-standard-2` handles the
expected load; anything smaller will struggle during enrolment, which runs the model nine
times per reference image.

### Locally, for development

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env && $EDITOR .env
python app.py
```

---

## Tests

```bash
pip install -r requirements-dev.txt
python -m pytest
```

The suite runs in about a second and needs neither a MongoDB server nor TensorFlow: Mongo is
faked with `mongomock`, and DeepFace's two entry points are patched. Everything around them —
decode, crop, augment, cosine distance, ranking, duplicate resolution, every HTTP path — runs
for real.

Covered: registration (single and multiple references, re-enrolment), recognition (multiple
faces, ranked candidates, unmatched faces, duplicate matches, per-request thresholds),
invalid images, no-face, multiple faces, authentication, MongoDB failure, model failure, and
a disclosure sweep that fails if any response or log line contains an embedding, a
credential, a raw image or a traceback.

---

## What this service will not do

* **Return an embedding.** Not on any route, under any parameter. A face vector is biometric
  data about a child, and the only things that leave here are statuses, ids and distances.
  The Mongo projections are written so a route could not leak one even by accident.
* **Accept an embedding.** Callers upload photographs. A client-supplied vector would be
  trivially forged into a match.
* **Confirm a match.** `/recognize` returns candidates. If nothing is inside the threshold it
  says so and returns nothing, and the nearest distance it reports is explicitly not a
  result. A weak match is never promoted into a strong one.
* **Show you a traceback.** Every failure is a JSON body with a volunteer-safe message.
  Technical detail goes to the container log, and even there exception *types* are logged
  rather than messages that might embed a connection string.
* **Log an image or a vector.** Neither appears at any log level, including `DEBUG`.
* **Serve a UI.** There are no templates and no static files.

---

## Documentation

* [`docs/API.md`](docs/API.md) — endpoint reference with request and response bodies.
* [`docs/ANDROID_INTEGRATION.md`](docs/ANDROID_INTEGRATION.md) — how the Android app reaches
  this service, and why it does not reach it directly.
