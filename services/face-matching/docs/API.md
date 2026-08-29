# API reference

Base URL is wherever the container is published. It is configurable everywhere it appears —
in the Android app, in the Supabase edge function, in your own tooling — and no production
address is committed to this repository. Examples below use a placeholder:

```
FACE_API_URL=http://<face-service-host>:8080/
```

All request and response bodies are JSON. All authenticated routes require the shared
secret:

```
X-API-Key: <FACE_API_KEY>
```

`X-Service-Token` is accepted as an alias, for the backend deployed before the rename.

---

## Conventions

**Status values.** Every photograph-processing route returns a `status` from this set, which
mirrors the Android `FaceMatchStatus` enum:

| Status                | Meaning                                                              |
| --------------------- | -------------------------------------------------------------------- |
| `READY`               | The photograph produced a usable face.                               |
| `NO_FACE`             | No face was detected.                                                |
| `MULTIPLE_FACES`      | More than one face where exactly one was required.                   |
| `INVALID_IMAGE`       | Not decodable, too small, or over the size cap.                      |
| `SERVICE_UNAVAILABLE` | The service or its storage could not complete the request.           |

Only `SERVICE_UNAVAILABLE` is retryable. The rest describe the photograph, and none of them
means the underlying Lost & Found report is invalid — a report is always saved on its
non-photo fields and stays matchable on the other signals.

**HTTP codes.**

| Code  | When                                                                          |
| ----- | ----------------------------------------------------------------------------- |
| `200` | Processed. Check `ok` and `status` — a `NO_FACE` result is a 200.              |
| `400` | The request was malformed: missing field, unreadable body.                     |
| `401` | Missing or wrong API key, or no key configured on the server.                  |
| `404` | No such record.                                                                |
| `405` | Wrong method.                                                                  |
| `410` | A retired endpoint. See [Retired endpoints](#retired-endpoints).               |
| `413` | Request body over the size cap.                                                |
| `500` | Unexpected failure. The body is generic; detail is in the container log.       |
| `503` | Storage or model unavailable. Retry.                                           |

**Images** are base64-encoded strings. A `data:image/jpeg;base64,` prefix is tolerated and
stripped. The cap is `MAX_IMAGE_BYTES` (8 MB by default), applied to the decoded bytes
before anything expands them into memory.

**Distances** are cosine distances in `[0, 2]`. Lower is more similar; `0` is identical.
`similarity` is reported alongside as `1 - distance`, because "0.79 similar" reads more
naturally than "0.21 distant".

---

## `GET /`

Basic service-running status. No authentication.

```json
{
  "ok": true,
  "service": "varisahayak-face-matching",
  "version": "2.0.0",
  "status": "running"
}
```

---

## `GET /health`

Liveness, for the Docker health check and any load balancer. No authentication.

```json
{ "ok": true, "status": "healthy" }
```

It deliberately reports nothing about the database, the model or the configuration. The
endpoint is unauthenticated, so anything it said would be said to anybody who can reach the
port. To check storage, call an authenticated route and look for a `503`.

---

## `POST /detect_faces`

Locate every face in an image. No embedding is computed and nothing is stored — this is the
cheap call a client makes to ask "is there a usable face here" before committing to an
enrolment, so a volunteer learns the shot is unusable while the person is still in front of
them.

### Request

| Field                | Type    | Required | Default | Notes                                     |
| -------------------- | ------- | -------- | ------- | ----------------------------------------- |
| `image`              | string  | yes      | —       | Base64. `images` is also accepted; the first is used. |
| `include_thumbnails` | boolean | no       | `true`  | Crops of your own upload, base64 JPEG.    |

```json
{ "image": "<base64>", "include_thumbnails": true }
```

### Response

```json
{
  "ok": true,
  "status": "READY",
  "message": "Photo processed.",
  "face_count": 2,
  "faces": [
    {
      "index": 0,
      "confidence": 0.9987,
      "location": { "x": 142, "y": 88, "w": 96, "h": 118 },
      "thumbnail": "<base64 jpeg>"
    }
  ]
}
```

`location` is in pixels on the submitted image. Thumbnails are bounded by
`THUMBNAIL_MAX_EDGE` and can be switched off service-wide with
`DETECT_RETURN_THUMBNAILS=false`. Faces beyond `MAX_FACES_PER_IMAGE` are dropped rather than
processed — a crowd photo is a legitimate upload and an illegitimate amount of compute.

---

## `POST /enroll`

Register a Lost or Found person's face. Also reachable at `POST /enrol`.

The averaged embedding is computed from the reference image and, unless augmentation is
disabled, several synthetic variants of it. That averaging is what makes the stored vector
resilient to the angle and light of the one photograph a volunteer happened to take.

Exactly one face per reference image is required. Two faces in a photo of "the missing
child" is genuinely ambiguous about who the record is for, and guessing would be worse than
declining.

### Request

| Field           | Type          | Required | Default    | Notes                                   |
| --------------- | ------------- | -------- | ---------- | --------------------------------------- |
| `person_id`     | string        | yes      | —          | Opaque, unique. `report_client_id` accepted as an alias. |
| `image`         | string        | yes*     | —          | Base64.                                 |
| `images`        | string[]      | yes*     | —          | Several references, averaged together. Capped at `MAX_REFERENCE_IMAGES`. |
| `kind`          | string        | no       | `LOST`     | `LOST` or `FOUND`.                      |
| `subject_type`  | string        | no       | `PERSON`   | `PERSON` or `ITEM`.                     |
| `metadata`      | object        | no       | `{}`       | Stored and echoed back, never interpreted. Keep identifying detail out of it. |

\* At least one of `image` or `images`.

```json
{
  "person_id": "lf-3f9a21",
  "kind": "FOUND",
  "subject_type": "PERSON",
  "images": ["<base64>", "<base64>"],
  "metadata": { "help_point": "Checkpoint 7" }
}
```

### Response

```json
{
  "ok": true,
  "status": "READY",
  "message": "Photo processed.",
  "person_id": "lf-3f9a21",
  "kind": "FOUND",
  "subject_type": "PERSON",
  "sample_count": 9,
  "reference_images": 2
}
```

`sample_count` is how many embeddings were averaged: one per reference image plus its
augmentation variants that produced a vector.

Re-enrolling the same `person_id` **replaces** the profile. A photograph that turns out to
be unusable clears any stored vector, so a record cannot keep matching on a picture that has
been superseded.

### Failures

`NO_FACE`, `MULTIPLE_FACES` and `INVALID_IMAGE` all return **200** with `ok: false` and are
recorded against the profile. A storage or model failure returns **503** and is *not*
recorded — the photograph may be perfect and simply unsaved, so the caller should retry
rather than mark the picture bad.

---

## `POST /recognize`

Detect faces in a photograph and rank them against the enrolled profiles.

### Request

| Field                | Type     | Required | Default              | Notes                                    |
| -------------------- | -------- | -------- | -------------------- | ---------------------------------------- |
| `image`              | string   | yes      | —                    | Base64.                                  |
| `threshold`          | number   | no       | `MATCH_THRESHOLD`    | Cosine distance ceiling, `0`–`2`. Lower is stricter. |
| `max_results`        | integer  | no       | `MAX_MATCH_RESULTS`  | Ranked candidates per face.              |
| `kind`               | string   | no       | all                  | Restrict to `LOST` or `FOUND` profiles.  |
| `subject_type`       | string   | no       | all                  | `PERSON` or `ITEM`.                      |
| `exclude_person_ids` | string[] | no       | `[]`                 | Skip these profiles.                     |

```json
{ "image": "<base64>", "kind": "LOST", "threshold": 0.38, "max_results": 3 }
```

### Response

```json
{
  "ok": true,
  "status": "READY",
  "threshold": 0.38,
  "enrolled_compared": 214,
  "face_count": 2,
  "results": [
    {
      "face_index": 0,
      "location": { "x": 142, "y": 88, "w": 96, "h": 118 },
      "confidence": 0.9987,
      "matched": true,
      "best_match": {
        "person_id": "lf-77c1",
        "distance": 0.2114,
        "similarity": 0.7886,
        "confidence": "HIGH",
        "metadata": { "help_point": "Checkpoint 7" }
      },
      "candidates": [ { "person_id": "lf-77c1", "distance": 0.2114, "...": "..." } ],
      "nearest_distance": 0.2114,
      "reason": null
    },
    {
      "face_index": 1,
      "location": { "x": 401, "y": 92, "w": 88, "h": 104 },
      "confidence": 0.9912,
      "matched": false,
      "best_match": null,
      "candidates": [],
      "nearest_distance": 0.7402,
      "reason": "NO_MATCH_WITHIN_THRESHOLD"
    }
  ],
  "duplicates": []
}
```

### The behaviour that matters

**Multiple faces.** Every detected face is ranked independently, up to
`MAX_FACES_PER_IMAGE`. A group shot at a help point is a normal query.

**Ranked candidates.** `candidates` holds up to `max_results` profiles inside the threshold,
closest first. `best_match` is the first of them, repeated for convenience.

**Unmatched faces.** A face with nothing inside the threshold returns `matched: false`,
`best_match: null`, an empty `candidates`, and `reason: "NO_MATCH_WITHIN_THRESHOLD"`.
`nearest_distance` is still reported so an operator can see how close the field came — it is
diagnostic, and it is **not a match**. A weak match is never promoted.

**Duplicate matches.** One enrolled person cannot be the best match for two faces in the
same photograph — two faces in a group shot are not the same missing child. The closest face
keeps the profile, the displaced face falls through to its next candidate (or becomes
unmatched), and the contest is reported:

```json
"duplicates": [
  { "person_id": "lf-77c1", "face_indexes": [0, 2], "assigned_to": 0 }
]
```

Assignment is greedy over ascending distance and ties break on the lower face index, so an
assignment is reproducible when it is later audited.

**Confidence** is `HIGH`, `MEDIUM` or `LOW`, from how far inside the threshold a candidate
sits and how far clear of the runner-up. It is a label for a reviewer, never an
authorisation: a candidate that only just scrapes in, or has a near-identical rival, is
`LOW` however good its absolute distance looks.

### Failures

`NO_FACE` and `INVALID_IMAGE` return **200** with `ok: false`, empty `results`, and no
`duplicates`. Storage failure returns **503**.

---

## `POST /compare`

Rank the opposite side of the board against one already-enrolled record. This is the call
the Supabase edge function makes; the caller folds the returned distances into its own
multi-attribute score, where face similarity is one signal of ten and never decisive.

### Request

| Field       | Type   | Required | Default           | Notes                                     |
| ----------- | ------ | -------- | ----------------- | ----------------------------------------- |
| `person_id` | string | yes      | —                 | `report_client_id` accepted as an alias.  |
| `threshold` | number | no       | `MATCH_THRESHOLD` | `tolerance` accepted as an alias.         |

### Response

```json
{
  "ok": true,
  "face_available": true,
  "distances": { "lf-77c1": 0.2114, "lf-9d02": 0.6633 },
  "eligible": ["lf-77c1"],
  "threshold": 0.4,
  "tolerance": 0.4
}
```

`distances` covers every candidate on the opposite side with a matching `subject_type`.
`eligible` is those inside the threshold, closest first — *eligible*, not *matched*: passing
the threshold makes a pair worth a human's attention and nothing more.

A record with no usable embedding returns `200` with `face_available: false` and empty
results. That is not a mismatch; it means face comparison is unavailable for these pairs and
the caller should continue on its other signals. An unknown record returns `404`.

`tolerance` duplicates `threshold` so the deployed edge function keeps parsing.

---

## `GET /persons`

List enrolled profiles. **Never includes embeddings** — the database projection makes that
structural rather than a thing this route remembers to do.

### Query parameters

| Parameter      | Default | Notes                             |
| -------------- | ------- | --------------------------------- |
| `kind`         | all     | `LOST` or `FOUND`.                |
| `subject_type` | all     | `PERSON` or `ITEM`.               |
| `status`       | all     | Any status value.                 |
| `limit`        | `100`   | Capped at 500.                    |
| `skip`         | `0`     | Offset.                           |

### Response

```json
{
  "ok": true,
  "count": 2,
  "total": 214,
  "persons": [
    {
      "person_id": "lf-77c1",
      "kind": "FOUND",
      "subject_type": "PERSON",
      "status": "READY",
      "model": "Facenet",
      "detector": "retinaface",
      "sample_count": 9,
      "metadata": { "help_point": "Checkpoint 7" },
      "created_at": "2026-08-30T09:14:02.881000+00:00",
      "updated_at": "2026-08-30T09:14:02.881000+00:00"
    }
  ]
}
```

---

## `GET /persons/<person_id>`

One profile's non-sensitive fields, in the same shape as a list entry:

```json
{ "ok": true, "person": { "person_id": "lf-77c1", "...": "..." } }
```

`404` if there is no such profile.

---

## `DELETE /persons/<person_id>`

Delete a person's face profile.

```json
{ "ok": true, "person_id": "lf-77c1", "deleted": true }
```

`404` if there was nothing to delete. Call this when a Lost & Found case closes: a reunited
child's biometric vector has no reason to stay on a server, and this is how it leaves.

---

## Retired endpoints

`GET /students`, `POST /students`, `GET /students/<prn_gr>` and `DELETE /students/<prn_gr>`
belonged to the attendance system this service was adapted from. They return **410 Gone**:

```json
{
  "ok": false,
  "message": "The student roster endpoints have been removed. This service now stores Lost & Found person profiles; use /persons instead."
}
```

410 rather than 404 on purpose — it tells an old client what happened instead of leaving it
to conclude it has the URL wrong.

---

## Errors

Every error is a JSON body. There is never an HTML page, a traceback, a library name, a file
path, a vector or a credential in a response.

```json
{ "ok": false, "message": "Not authorised." }
```

Photograph-processing errors also carry `status`:

```json
{
  "ok": false,
  "status": "SERVICE_UNAVAILABLE",
  "message": "Face matching is temporarily unavailable. The record was saved and will continue using other matching information."
}
```

Storage failures return one identical body whatever went wrong underneath — an unreachable
replica set, a failed authentication, a missing collection. Distinguishing them for the
caller would describe the infrastructure to anybody who can reach the port.

---

## curl examples

```bash
FACE_API_URL=http://<face-service-host>:8080
FACE_API_KEY=<your key>
IMAGE=$(base64 -w0 photo.jpg)

curl -s "$FACE_API_URL/health"

curl -s -X POST "$FACE_API_URL/detect_faces" \
  -H "X-API-Key: $FACE_API_KEY" -H 'Content-Type: application/json' \
  -d "{\"image\":\"$IMAGE\"}"

curl -s -X POST "$FACE_API_URL/enroll" \
  -H "X-API-Key: $FACE_API_KEY" -H 'Content-Type: application/json' \
  -d "{\"person_id\":\"lf-3f9a21\",\"kind\":\"FOUND\",\"image\":\"$IMAGE\"}"

curl -s -X POST "$FACE_API_URL/recognize" \
  -H "X-API-Key: $FACE_API_KEY" -H 'Content-Type: application/json' \
  -d "{\"image\":\"$IMAGE\",\"kind\":\"LOST\",\"max_results\":3}"

curl -s "$FACE_API_URL/persons?kind=FOUND&limit=10" -H "X-API-Key: $FACE_API_KEY"

curl -s -X DELETE "$FACE_API_URL/persons/lf-3f9a21" -H "X-API-Key: $FACE_API_KEY"
```
