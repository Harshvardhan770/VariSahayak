"""Flask HTTP layer for the Lost & Found face-matching service.

Two endpoints, and a hard boundary: a client uploads a photograph and receives a *status*
and, at most, distances to named counterparts. It never supplies an embedding — one would
be trivially forged — and never receives one, because a face vector is biometric data
about a child.

Every failure returns JSON with a volunteer-safe message. Section 7.21G forbids surfacing
Python tracebacks, DeepFace or OpenCV exceptions, Flask debug pages, file paths, vectors,
or credentials, so nothing here renders an exception to the caller: technical detail is
logged server-side only.
"""

from __future__ import annotations

import logging
import os

from dotenv import load_dotenv
from flask import Flask, jsonify, request
from flask_cors import CORS

import face_engine
import repository

load_dotenv()

logging.basicConfig(
    level=os.environ.get("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("varisahayak.face")

app = Flask(__name__)

# Locked to the backend that calls this service. It is never reachable from a browser on
# the public QR site, and never from the Android app directly — the app talks to Supabase,
# and Supabase talks to this.
CORS(app, origins=os.environ.get("ALLOWED_ORIGINS", "").split(",") or None)

# Flask's own request cap, in addition to the engine's byte check. Belt and braces: this
# one rejects an oversized upload before the body is ever read into memory.
app.config["MAX_CONTENT_LENGTH"] = face_engine.MAX_IMAGE_BYTES + (256 * 1024)

SERVICE_TOKEN = os.environ.get("FACE_SERVICE_TOKEN")


def _authorised() -> bool:
    """Shared-secret check.

    This service holds the database service role and reads biometric vectors, so it must
    never be callable by anything but the backend. A missing token in configuration is
    treated as a closed door, not an open one.
    """
    if not SERVICE_TOKEN:
        logger.error("FACE_SERVICE_TOKEN is not configured; refusing every request")
        return False

    supplied = request.headers.get("X-Service-Token", "")
    # Constant-time comparison: a naive == leaks the token a character at a time.
    import hmac

    return hmac.compare_digest(supplied, SERVICE_TOKEN)


def _error(message: str, status_code: int = 400, face_status: str | None = None):
    body = {"ok": False, "message": message}
    if face_status:
        body["status"] = face_status
    return jsonify(body), status_code


@app.errorhandler(413)
def _too_large(_):
    return _error(
        face_engine.USER_MESSAGES[face_engine.FaceStatus.INVALID_IMAGE],
        413,
        face_engine.FaceStatus.INVALID_IMAGE.value,
    )


@app.errorhandler(Exception)
def _unhandled(error: Exception):
    # The catch-all that keeps a traceback off a volunteer's screen.
    logger.exception("Unhandled error: %s", error)
    return _error(
        face_engine.USER_MESSAGES[face_engine.FaceStatus.SERVICE_UNAVAILABLE],
        500,
        face_engine.FaceStatus.SERVICE_UNAVAILABLE.value,
    )


@app.get("/health")
def health():
    """Liveness only. Deliberately says nothing about the database or the model."""
    return jsonify({"ok": True, "model": face_engine.MODEL_NAME})


@app.post("/enrol")
def enrol():
    """Process a report's photograph into a stored embedding.

    Always returns 200 with a status. A photo that yields no face is a normal outcome, not
    an HTTP error — the report it belongs to has already been saved and remains fully
    valid on its other attributes.
    """
    if not _authorised():
        return _error("Not authorised.", 401)

    payload = request.get_json(silent=True) or {}
    report_client_id = payload.get("report_client_id")
    image = payload.get("image")

    if not report_client_id or not image:
        return _error("report_client_id and image are required.", 400)

    try:
        result = face_engine.enrol(image)
    except Exception:
        logger.exception("Enrolment failed for %s", report_client_id)
        result = face_engine.EmbeddingResult(face_engine.FaceStatus.SERVICE_UNAVAILABLE)

    try:
        if result.status is face_engine.FaceStatus.READY and result.embedding is not None:
            repository.save_embedding(
                report_client_id=report_client_id,
                embedding=result.embedding.tolist(),
                detector=face_engine.DETECTOR_BACKEND_RECOGNITION,
                model=face_engine.MODEL_NAME,
                sample_count=result.sample_count,
            )
        else:
            repository.mark_status(report_client_id, result.status.value)
    except repository.DatabaseUnavailable:
        # The photo may have processed perfectly; we simply could not record it. Reported
        # as retryable so the backend tries again rather than marking the photo bad.
        return (
            jsonify(
                {
                    "ok": False,
                    "status": face_engine.FaceStatus.SERVICE_UNAVAILABLE.value,
                    "message": (
                        "Database service is temporarily unavailable. Your report is saved "
                        "and will sync when connectivity returns."
                    ),
                }
            ),
            503,
        )

    return jsonify(
        {
            "ok": result.status is face_engine.FaceStatus.READY,
            "status": result.status.value,
            "message": result.message,
            "sample_count": result.sample_count,
        }
    )


@app.post("/compare")
def compare():
    """Rank the opposite side of the board by face distance.

    Returns distances keyed by report id — a ranking signal against named counterparts,
    never a vector. The caller folds these into its own multi-attribute score; face
    similarity is one signal of ten and is never decisive on its own.
    """
    if not _authorised():
        return _error("Not authorised.", 401)

    payload = request.get_json(silent=True) or {}
    report_client_id = payload.get("report_client_id")

    if not report_client_id:
        return _error("report_client_id is required.", 400)

    try:
        report = repository.get_report(report_client_id)
        if report is None:
            return _error("Report not found.", 404)

        # No usable embedding on this side is not an error and not a mismatch: it means
        # "face comparison unavailable for these pairs". The caller continues on the other
        # nine signals.
        if report["face_match_status"] != "READY":
            return jsonify(
                {
                    "ok": True,
                    "face_available": False,
                    "distances": {},
                    "message": "Face comparison is not available for this report.",
                }
            )

        own_embedding = repository.get_embedding(report_client_id)
        candidates = repository.load_opposite_side_embeddings(report_client_id)
    except repository.DatabaseUnavailable:
        return (
            jsonify(
                {
                    "ok": False,
                    "face_available": False,
                    "message": face_engine.USER_MESSAGES[
                        face_engine.FaceStatus.SERVICE_UNAVAILABLE
                    ],
                }
            ),
            503,
        )

    # The status said READY but the vector is gone — a photo replaced between the two
    # reads, most likely. Treated as "cannot compare", never as a mismatch.
    if own_embedding is None:
        return jsonify({"ok": True, "face_available": False, "distances": {}})

    distances = {
        candidate.report_client_id: round(
            face_engine.cosine_distance(own_embedding, candidate.embedding), 4
        )
        for candidate in candidates
    }

    eligible = {k: v for k, v in distances.items() if face_engine.is_match(v)}

    return jsonify(
        {
            "ok": True,
            "face_available": True,
            "distances": distances,
            # Named "eligible", not "matches": passing the threshold makes a pair worth a
            # human's attention, and nothing more.
            "eligible": sorted(eligible, key=eligible.get),
            "tolerance": face_engine.MATCH_TOLERANCE,
        }
    )


if __name__ == "__main__":
    # Development only. In deployment this runs behind a WSGI server; debug is never on,
    # because a Flask debug page would expose exactly the internals section 7.21G forbids.
    app.run(
        host=os.environ.get("HOST", "127.0.0.1"),
        port=int(os.environ.get("PORT", "8000")),
        debug=False,
    )
