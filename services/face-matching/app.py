"""Flask HTTP layer for the VARI Sahayak Lost & Found face-matching service.

API-only. There is no UI, no template, and no static directory: a client uploads a
photograph and receives a *status* and, at most, ranked person ids with distances. It never
supplies an embedding — one would be trivially forged — and never receives one, because a
face vector is biometric data about a child.

Every failure returns JSON with a volunteer-safe message. Nothing here renders an exception
to the caller: no Python traceback, no DeepFace or OpenCV message, no Mongo error text, no
file path, no vector, no credential. Technical detail is logged server-side only, and even
there the log lines carry exception *types* rather than messages that may embed a
connection string.
"""

from __future__ import annotations

import hmac
import logging
from typing import Optional

from flask import Flask, jsonify, request
from flask_cors import CORS

import config
import face_engine
import repository

logging.basicConfig(
    level=config.LOG_LEVEL,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("varisahayak.face")

app = Flask(__name__)

# Locked to the backend that calls this service. An empty list — the default — allows no
# browser origin at all, which is correct: this is never reached from the public QR site,
# and never from the Android app directly.
CORS(app, origins=config.ALLOWED_ORIGINS or [])

# Flask's own request cap, on top of the engine's byte check. Belt and braces: this one
# rejects an oversized upload before the body is read into memory. The headroom covers
# base64's 4/3 expansion plus JSON overhead.
app.config["MAX_CONTENT_LENGTH"] = int(config.MAX_IMAGE_BYTES * 1.4) + (256 * 1024)
app.config["JSON_SORT_KEYS"] = False

logger.info("Starting %s", config.redacted_summary())

if not config.API_KEY:
    logger.error("No API key configured; every authenticated route will refuse requests")
if not config.MONGODB_URI:
    logger.error("MONGODB_URI is not configured; storage-backed routes will report an outage")

repository.ensure_indexes()


# --- helpers -----------------------------------------------------------------------------


def _authorised() -> bool:
    """Shared-secret check.

    This service reads biometric vectors, so it must never be callable by anything but the
    backend. A missing key in configuration is treated as a closed door, not an open one.

    Both header names are accepted so a deployment that predates the rename keeps working.
    """
    if not config.API_KEY:
        logger.error("Refusing a request: no API key is configured")
        return False

    supplied = request.headers.get("X-API-Key") or request.headers.get("X-Service-Token") or ""
    # Constant-time comparison: a naive == leaks the key a character at a time.
    return hmac.compare_digest(supplied, config.API_KEY)


def _error(message: str, status_code: int = 400, face_status: Optional[str] = None):
    body = {"ok": False, "message": message}
    if face_status:
        body["status"] = face_status
    return jsonify(body), status_code


def _unauthorised():
    return _error("Not authorised.", 401)


def _outage():
    """The single response for any storage failure.

    Deliberately identical whatever went wrong underneath — an unreachable replica set, a
    failed authentication, a missing collection. Distinguishing them for the caller would
    describe the infrastructure to anybody who can reach the port.
    """
    return (
        jsonify(
            {
                "ok": False,
                "status": face_engine.FaceStatus.SERVICE_UNAVAILABLE.value,
                "message": face_engine.USER_MESSAGES[
                    face_engine.FaceStatus.SERVICE_UNAVAILABLE
                ],
            }
        ),
        503,
    )


def _payload() -> dict:
    return request.get_json(silent=True) or {}


def _images_from(payload: dict) -> list[str]:
    """The reference images in a request, from either the single or the plural field.

    ``image`` and ``images`` both exist because enrolment takes several references while
    detection and recognition take exactly one, and a caller should not have to wrap a
    single photograph in a list.
    """
    images: list[str] = []

    single = payload.get("image")
    if isinstance(single, str) and single.strip():
        images.append(single)

    plural = payload.get("images")
    if isinstance(plural, list):
        images.extend(item for item in plural if isinstance(item, str) and item.strip())

    return images[: config.MAX_REFERENCE_IMAGES]


def _normalise_kind(value: object, default: Optional[str] = "LOST") -> Optional[str]:
    if not isinstance(value, str):
        return default
    upper = value.strip().upper()
    return upper if upper in {"LOST", "FOUND"} else default


def _normalise_subject(value: object, default: Optional[str] = "PERSON") -> Optional[str]:
    if not isinstance(value, str):
        return default
    upper = value.strip().upper()
    return upper if upper in {"PERSON", "ITEM"} else default


def _threshold_from(payload: dict) -> float:
    """A per-request threshold override, clamped to a sane range.

    Configurable because the right bar differs between a controlled help-point photo and a
    phone snap in a moving crowd, and an operator tuning it should not need a redeploy.
    """
    raw = payload.get("threshold", payload.get("tolerance"))
    if raw is None:
        return config.MATCH_THRESHOLD
    try:
        value = float(raw)
    except (TypeError, ValueError):
        return config.MATCH_THRESHOLD
    return max(0.0, min(2.0, value))


# --- error handlers ----------------------------------------------------------------------


@app.errorhandler(404)
def _not_found(_):
    return _error("Not found.", 404)


@app.errorhandler(405)
def _method_not_allowed(_):
    return _error("Unsupported request.", 405)


@app.errorhandler(413)
def _too_large(_):
    return _error(
        face_engine.USER_MESSAGES[face_engine.FaceStatus.INVALID_IMAGE],
        413,
        face_engine.FaceStatus.INVALID_IMAGE.value,
    )


@app.errorhandler(Exception)
def _unhandled(error: Exception):
    # The catch-all that keeps a traceback off a volunteer's screen. The exception is
    # logged in full server-side; the caller learns only that the service is unavailable.
    logger.exception("Unhandled error: %s", type(error).__name__)
    return _error(
        face_engine.USER_MESSAGES[face_engine.FaceStatus.SERVICE_UNAVAILABLE],
        500,
        face_engine.FaceStatus.SERVICE_UNAVAILABLE.value,
    )


# --- status ------------------------------------------------------------------------------


@app.get("/")
def root():
    """Basic service-running status. Unauthenticated, and says nothing operational."""
    return jsonify(
        {
            "ok": True,
            "service": config.SERVICE_NAME,
            "version": config.SERVICE_VERSION,
            "status": "running",
        }
    )


@app.get("/health")
def health():
    """Liveness, for the container health check and the load balancer.

    Unauthenticated because a probe has no credentials, and therefore deliberately silent
    about the database, the model and the configuration. Anything it reported would be
    reported to anybody who can reach the port.
    """
    return jsonify({"ok": True, "status": "healthy"})


# --- detection ---------------------------------------------------------------------------


@app.post("/detect_faces")
def detect_faces():
    """Locate every face in an image, with optional crops.

    The cheap call. A client uses it to ask "is there a usable face in this photograph"
    before committing to an enrolment, so a volunteer finds out that the shot is unusable
    while the person is still in front of them.

    Thumbnails are crops of the caller's own upload, returned only when asked for and never
    logged or stored.
    """
    if not _authorised():
        return _unauthorised()

    payload = _payload()
    images = _images_from(payload)
    if not images:
        return _error("An image is required.", 400)

    include_thumbnails = bool(payload.get("include_thumbnails", True))

    result = face_engine.detect_faces(images[0], include_thumbnails=include_thumbnails)

    return jsonify(
        {
            "ok": result.status is face_engine.FaceStatus.READY,
            "status": result.status.value,
            "message": result.message,
            "face_count": len(result.faces),
            "faces": [face.to_dict(include_thumbnails) for face in result.faces],
        }
    )


# --- enrolment ---------------------------------------------------------------------------


@app.post("/enroll")
@app.route("/enrol", methods=["POST"])  # British spelling, for the existing backend
def enroll():
    """Register a Lost or Found person's face.

    Always returns 200 with a status when the photograph itself is the problem. A photo
    that yields no face is a normal outcome, not an HTTP error — the Lost & Found report it
    belongs to has already been saved and remains fully valid on its other attributes.

    A storage failure is different, and returns 503: the photo may have processed
    perfectly and we simply could not record it, so the caller should retry rather than
    mark the picture bad.
    """
    if not _authorised():
        return _unauthorised()

    payload = _payload()

    # report_client_id is the name the Supabase backend has always sent. Accepted as an
    # alias so this refactor does not require the two to deploy together.
    person_id = payload.get("person_id") or payload.get("report_client_id")
    if not isinstance(person_id, str) or not person_id.strip():
        return _error("person_id is required.", 400)
    person_id = person_id.strip()

    images = _images_from(payload)
    if not images:
        return _error("At least one image is required.", 400)

    kind = _normalise_kind(payload.get("kind"))
    subject_type = _normalise_subject(payload.get("subject_type"))
    metadata = payload.get("metadata") if isinstance(payload.get("metadata"), dict) else {}

    try:
        result = face_engine.embed_reference_images(images)
    except Exception:
        # Model load failures, out-of-memory, a corrupt weights file. Logged, then reported
        # as an outage so the caller retries instead of blaming the photograph.
        logger.exception("Enrolment processing failed")
        result = face_engine.EmbeddingResult(face_engine.FaceStatus.SERVICE_UNAVAILABLE)

    if result.status is face_engine.FaceStatus.SERVICE_UNAVAILABLE:
        return _outage()

    try:
        if result.status is face_engine.FaceStatus.READY and result.embedding is not None:
            repository.save_profile(
                person_id=person_id,
                embedding=result.embedding.tolist(),
                kind=kind,
                subject_type=subject_type,
                model=config.MODEL_NAME,
                detector=config.DETECTOR_BACKEND_RECOGNITION,
                sample_count=result.sample_count,
                metadata=metadata,
            )
        else:
            repository.mark_status(
                person_id=person_id,
                status=result.status.value,
                kind=kind,
                subject_type=subject_type,
                metadata=metadata,
            )
    except repository.DatabaseUnavailable:
        return _outage()

    return jsonify(
        {
            "ok": result.status is face_engine.FaceStatus.READY,
            "status": result.status.value,
            "message": result.message,
            "person_id": person_id,
            "kind": kind,
            "subject_type": subject_type,
            "sample_count": result.sample_count,
            "reference_images": result.source_image_count,
        }
    )


# --- recognition -------------------------------------------------------------------------


@app.post("/recognize")
def recognize():
    """Detect faces in an image and rank them against the enrolled profiles.

    Handles the things a single-face demo does not:

    * **Multiple faces.** A group shot at a help point is a legitimate query, and every
      face in it is ranked independently.
    * **Ranked candidates.** Each face returns up to ``max_results`` profiles inside the
      threshold, closest first, rather than one take-it-or-leave-it answer.
    * **Unmatched faces.** A face with nothing inside the threshold comes back with
      ``matched: false`` and a null best match. The nearest distance is reported so an
      operator can see the bar working, and it is never promoted into a match.
    * **Duplicates.** One enrolled person cannot be the best match for two faces in the
      same photograph; the closest face keeps them and the contest is reported.

    A weak match is never forced. If nothing qualifies, nothing is returned as a match.
    """
    if not _authorised():
        return _unauthorised()

    payload = _payload()
    images = _images_from(payload)
    if not images:
        return _error("An image is required.", 400)

    threshold = _threshold_from(payload)
    max_results = payload.get("max_results")
    try:
        max_results = int(max_results) if max_results is not None else config.MAX_MATCH_RESULTS
    except (TypeError, ValueError):
        max_results = config.MAX_MATCH_RESULTS
    max_results = max(1, min(max_results, config.MAX_MATCH_RESULTS))

    # Optional side filter. Absent means "compare against everybody enrolled", which is the
    # right default for a volunteer photographing someone at a help point with no report of
    # their own to anchor to.
    kind = _normalise_kind(payload.get("kind"), default=None)
    subject_type = _normalise_subject(payload.get("subject_type"), default=None)
    exclude = payload.get("exclude_person_ids")
    exclude = [item for item in exclude if isinstance(item, str)] if isinstance(exclude, list) else []

    try:
        status, embedded = face_engine.embed_query_faces(images[0])
    except Exception:
        logger.exception("Recognition processing failed")
        return _outage()

    if status is not face_engine.FaceStatus.READY:
        return jsonify(
            {
                "ok": False,
                "status": status.value,
                "message": face_engine.USER_MESSAGES[status],
                "threshold": threshold,
                "face_count": 0,
                "results": [],
                "duplicates": [],
            }
        )

    try:
        profiles = repository.load_profiles_for_matching(
            kind=kind, subject_type=subject_type, exclude_person_ids=exclude
        )
    except repository.DatabaseUnavailable:
        return _outage()

    catalogue = [(p.person_id, p.embedding, p.metadata) for p in profiles]

    rankings: dict[int, list[face_engine.Candidate]] = {}
    nearest_by_face: dict[int, Optional[float]] = {}

    for face, vector in embedded:
        candidates, nearest = face_engine.rank_candidates(
            query=vector, profiles=catalogue, threshold=threshold, max_results=max_results
        )
        rankings[face.index] = candidates
        nearest_by_face[face.index] = nearest

    resolved, duplicates = face_engine.resolve_duplicate_matches(rankings)

    results = []
    for face, _vector in embedded:
        candidates = resolved.get(face.index, [])
        nearest = nearest_by_face.get(face.index)
        results.append(
            {
                "face_index": face.index,
                "location": face.location(),
                "confidence": round(face.confidence, 4),
                "matched": bool(candidates),
                "best_match": candidates[0].to_dict() if candidates else None,
                "candidates": [candidate.to_dict() for candidate in candidates],
                # Present even when nothing matched. It is diagnostic, never a result:
                # reporting "the closest profile was 0.71 away" is how an operator sees the
                # threshold doing its job without anything weak being handed back as a hit.
                "nearest_distance": round(nearest, 4) if nearest is not None else None,
                "reason": None if candidates else "NO_MATCH_WITHIN_THRESHOLD",
            }
        )

    return jsonify(
        {
            "ok": True,
            "status": face_engine.FaceStatus.READY.value,
            "message": face_engine.USER_MESSAGES[face_engine.FaceStatus.READY],
            "threshold": threshold,
            "enrolled_compared": len(catalogue),
            "face_count": len(results),
            "results": results,
            "duplicates": duplicates,
        }
    )


# --- record-to-record comparison ---------------------------------------------------------


@app.post("/compare")
def compare():
    """Rank the opposite side of the board against an already-enrolled record.

    Retained unchanged in shape for the Supabase edge function, which calls it with a
    ``report_client_id`` and folds the returned distances into its own multi-attribute
    score. Face similarity is one signal of ten there and is never decisive on its own.
    """
    if not _authorised():
        return _unauthorised()

    payload = _payload()
    person_id = payload.get("person_id") or payload.get("report_client_id")
    if not isinstance(person_id, str) or not person_id.strip():
        return _error("person_id is required.", 400)
    person_id = person_id.strip()

    threshold = _threshold_from(payload)

    try:
        profile = repository.get_profile(person_id)
        if profile is None:
            return _error("Record not found.", 404)

        # No usable embedding on this side is not an error and not a mismatch: it means
        # "face comparison unavailable for these pairs". The caller continues on its other
        # signals.
        if profile.get("status") != "READY":
            return jsonify(
                {
                    "ok": True,
                    "face_available": False,
                    "distances": {},
                    "eligible": [],
                    "message": "Face comparison is not available for this record.",
                }
            )

        own = repository.get_embedding(person_id)
        candidates = repository.load_profiles_for_matching(
            kind=repository.opposite_side(profile.get("kind")),
            subject_type=profile.get("subject_type"),
            exclude_person_ids=[person_id],
        )
    except repository.DatabaseUnavailable:
        return (
            jsonify(
                {
                    "ok": False,
                    "face_available": False,
                    "status": face_engine.FaceStatus.SERVICE_UNAVAILABLE.value,
                    "message": face_engine.USER_MESSAGES[
                        face_engine.FaceStatus.SERVICE_UNAVAILABLE
                    ],
                }
            ),
            503,
        )

    # The status said READY but the vector is gone — a photo replaced between the two
    # reads, most likely. Treated as "cannot compare", never as a mismatch.
    if own is None:
        return jsonify({"ok": True, "face_available": False, "distances": {}, "eligible": []})

    distances = {
        candidate.person_id: round(face_engine.cosine_distance(own, candidate.embedding), 4)
        for candidate in candidates
    }
    eligible = {k: v for k, v in distances.items() if v <= threshold}

    return jsonify(
        {
            "ok": True,
            "face_available": True,
            "distances": distances,
            # Named "eligible", not "matches": passing the threshold makes a pair worth a
            # human's attention, and nothing more.
            "eligible": sorted(eligible, key=eligible.get),
            "threshold": threshold,
            # The old field name, kept so the deployed edge function keeps parsing.
            "tolerance": threshold,
        }
    )


# --- profile administration --------------------------------------------------------------


@app.get("/persons")
def list_persons():
    """Enrolled profiles, without embeddings.

    The Lost & Found replacement for the reference implementation's ``/students``. The
    projection in the repository is what guarantees no vector appears here — this route
    could not leak one even if it tried to.
    """
    if not _authorised():
        return _unauthorised()

    kind = _normalise_kind(request.args.get("kind"), default=None)
    subject_type = _normalise_subject(request.args.get("subject_type"), default=None)
    status = request.args.get("status")

    try:
        limit = int(request.args.get("limit", 100))
        skip = int(request.args.get("skip", 0))
    except ValueError:
        return _error("limit and skip must be whole numbers.", 400)

    try:
        persons = repository.list_profiles(
            kind=kind, subject_type=subject_type, status=status, limit=limit, skip=skip
        )
        total = repository.count_profiles(kind=kind, subject_type=subject_type, status=status)
    except repository.DatabaseUnavailable:
        return _outage()

    return jsonify({"ok": True, "count": len(persons), "total": total, "persons": persons})


@app.get("/persons/<person_id>")
def get_person(person_id: str):
    """One profile's non-sensitive fields."""
    if not _authorised():
        return _unauthorised()

    try:
        profile = repository.get_profile(person_id)
    except repository.DatabaseUnavailable:
        return _outage()

    if profile is None:
        return _error("Record not found.", 404)

    return jsonify({"ok": True, "person": profile})


@app.delete("/persons/<person_id>")
def delete_person(person_id: str):
    """Delete the face profile for a person.

    The Lost & Found replacement for ``DELETE /students/<prn_gr>``. A reunited child's
    biometric vector has no reason to stay on a server, and this is how it leaves.
    """
    if not _authorised():
        return _unauthorised()

    try:
        deleted = repository.delete_profile(person_id)
    except repository.DatabaseUnavailable:
        return _outage()

    if not deleted:
        return _error("Record not found.", 404)

    return jsonify({"ok": True, "person_id": person_id, "deleted": True})


# --- retired routes ----------------------------------------------------------------------


@app.route("/students", methods=["GET", "POST"])
@app.route("/students/<path:_ignored>", methods=["GET", "DELETE"])
def students_gone(_ignored: str = ""):
    """410 Gone, not 404.

    The student roster belonged to the attendance system this service was adapted from.
    Answering "gone, use /persons" tells an old client what happened; a 404 would leave it
    guessing that it had the URL wrong.
    """
    return (
        jsonify(
            {
                "ok": False,
                "message": (
                    "The student roster endpoints have been removed. This service now "
                    "stores Lost & Found person profiles; use /persons instead."
                ),
            }
        ),
        410,
    )


if __name__ == "__main__":
    # Development only. In the container this runs under gunicorn; debug is never on,
    # because a Flask debug page would expose exactly the internals this service hides.
    app.run(host=config.HOST, port=config.PORT, debug=False)
