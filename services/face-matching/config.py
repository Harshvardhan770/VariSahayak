"""Every tunable the service has, resolved from the environment in one place.

Configuration is read once at import. A Dockerised deployment sets these through the
container's environment or a mounted ``.env``; nothing here reads a file at request time,
so a running container's behaviour cannot drift under it.

Values are validated on load and fall back to a documented default rather than raising,
with one exception: the API key. An unset key makes the service refuse every request,
because a face-matching service that answers anonymously is worse than one that is down.
"""

from __future__ import annotations

import logging
import os

from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)


def _int(name: str, default: int, minimum: int = 1) -> int:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError:
        logger.warning("%s is not an integer; using %d", name, default)
        return default
    if value < minimum:
        logger.warning("%s is below the minimum of %d; using %d", name, minimum, minimum)
        return minimum
    return value


def _float(name: str, default: float, minimum: float, maximum: float) -> float:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        value = float(raw)
    except ValueError:
        logger.warning("%s is not a number; using %s", name, default)
        return default
    if not minimum <= value <= maximum:
        logger.warning(
            "%s must be between %s and %s; using %s", name, minimum, maximum, default
        )
        return default
    return value


def _bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name, "").strip().lower()
    if not raw:
        return default
    return raw in {"1", "true", "yes", "on"}


def _str(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


# --- service -----------------------------------------------------------------------------

SERVICE_NAME = "varisahayak-face-matching"
SERVICE_VERSION = _str("SERVICE_VERSION", "2.0.0")

HOST = _str("HOST", "0.0.0.0")
PORT = _int("PORT", 8080, minimum=1)
LOG_LEVEL = _str("LOG_LEVEL", "INFO").upper()

#: Comma-separated origins. Empty means no browser origin is allowed, which is the correct
#: default: this service is called by a backend, never from a page.
ALLOWED_ORIGINS = [o for o in _str("ALLOWED_ORIGINS").split(",") if o]

#: Accepted on ``X-API-Key`` or, for the backend that predates the rename,
#: ``X-Service-Token``. Either environment name works so an existing deployment keeps
#: running unchanged.
API_KEY = _str("FACE_API_KEY") or _str("FACE_SERVICE_TOKEN")


# --- MongoDB -----------------------------------------------------------------------------

MONGODB_URI = _str("MONGODB_URI")
MONGODB_DB = _str("MONGODB_DB", "varisahayak_faces")
MONGODB_COLLECTION = _str("MONGODB_COLLECTION", "face_profiles")

#: Fail fast rather than hanging a volunteer's request behind an unreachable replica set.
MONGODB_SERVER_SELECTION_TIMEOUT_MS = _int("MONGODB_SERVER_SELECTION_TIMEOUT_MS", 5_000)
MONGODB_CONNECT_TIMEOUT_MS = _int("MONGODB_CONNECT_TIMEOUT_MS", 5_000)
MONGODB_SOCKET_TIMEOUT_MS = _int("MONGODB_SOCKET_TIMEOUT_MS", 20_000)

#: One client per worker process, pooled. Sized for a handful of concurrent recognitions,
#: which is what a single GPU-less VM can actually chew through.
MONGODB_MAX_POOL_SIZE = _int("MONGODB_MAX_POOL_SIZE", 20)
MONGODB_MIN_POOL_SIZE = _int("MONGODB_MIN_POOL_SIZE", 0, minimum=0)


# --- matching ----------------------------------------------------------------------------

MODEL_NAME = _str("FACE_MODEL", "Facenet")
DETECTOR_BACKEND_REGISTRATION = _str("DETECTOR_REGISTRATION", "opencv")
DETECTOR_BACKEND_RECOGNITION = _str("DETECTOR_RECOGNITION", "retinaface")

#: Cosine distance. Lower is more similar; a pair at or below this is worth a human's
#: attention. An inherited engineering starting point, NOT a validated identity threshold —
#: it must be tuned against representative Wari photographs before anyone relies on it.
MATCH_THRESHOLD = _float("MATCH_THRESHOLD", 0.40, minimum=0.0, maximum=2.0)

#: Distances this much better than the runner-up make a match unambiguous. Used to label
#: confidence, never to promote something that failed the threshold.
MATCH_CONFIDENT_MARGIN = _float("MATCH_CONFIDENT_MARGIN", 0.08, minimum=0.0, maximum=2.0)

#: How many ranked candidates a single face may return.
MAX_MATCH_RESULTS = _int("MAX_MATCH_RESULTS", 5)


# --- images ------------------------------------------------------------------------------

MAX_IMAGE_BYTES = _int("MAX_IMAGE_BYTES", 8 * 1024 * 1024)
MIN_IMAGE_DIMENSION = _int("MIN_IMAGE_DIMENSION", 64)

#: Refuse a crowd photo outright rather than spending a minute embedding forty faces.
MAX_FACES_PER_IMAGE = _int("MAX_FACES_PER_IMAGE", 10)

#: How many reference images one enrolment call may average over.
MAX_REFERENCE_IMAGES = _int("MAX_REFERENCE_IMAGES", 5)

#: Confidence below which a detection is discarded. ``extract_faces`` with
#: ``enforce_detection=False`` returns the whole frame as a near-zero-confidence "face"
#: when it finds nothing, so without this every photograph of a wall would enrol.
FACE_CONFIDENCE_THRESHOLD = _float("FACE_CONFIDENCE_THRESHOLD", 0.5, 0.0, 1.0)

#: Longest edge of a returned crop, in pixels. Thumbnails echo the caller's own upload back
#: to them; they are still bounded so a response cannot balloon on a crowd scene.
THUMBNAIL_MAX_EDGE = _int("THUMBNAIL_MAX_EDGE", 160)
THUMBNAIL_JPEG_QUALITY = _int("THUMBNAIL_JPEG_QUALITY", 80)
RETURN_THUMBNAILS = _bool("DETECT_RETURN_THUMBNAILS", True)


# --- augmentation ------------------------------------------------------------------------
# Averaging embeddings over small perturbations makes the stored vector far less sensitive
# to the angle and light of the single photograph a volunteer happened to take. On a walking
# route in changing daylight that is the difference between a match and a near miss.

AUGMENTATION_ENABLED = _bool("AUGMENTATION_ENABLED", True)
AUGMENT_FLIP = _bool("AUGMENT_FLIP", True)
AUGMENT_ROTATION_DEGREES = _float("AUGMENT_ROTATION_DEGREES", 12.0, 0.0, 45.0)
AUGMENT_BRIGHTNESS_DELTA = _float("AUGMENT_BRIGHTNESS_DELTA", 30.0, 0.0, 128.0)
AUGMENT_CONTRAST_HIGH = _float("AUGMENT_CONTRAST_HIGH", 1.3, 1.0, 3.0)
AUGMENT_CONTRAST_LOW = _float("AUGMENT_CONTRAST_LOW", 0.75, 0.1, 1.0)
AUGMENT_ZOOM_RATIO = _float("AUGMENT_ZOOM_RATIO", 0.08, 0.0, 0.4)

#: Face crops keep some surrounding context; Facenet was trained that way and a tight crop
#: measurably degrades the embedding.
FACE_CROP_MARGIN_RATIO = _float("FACE_CROP_MARGIN_RATIO", 0.15, 0.0, 1.0)


def redacted_summary() -> dict:
    """Configuration safe to log at start-up.

    The URI is omitted entirely rather than masked. A partially-masked connection string
    still leaks the host and the username, and neither belongs in a log file.
    """
    return {
        "service": SERVICE_NAME,
        "version": SERVICE_VERSION,
        "model": MODEL_NAME,
        "detector_registration": DETECTOR_BACKEND_REGISTRATION,
        "detector_recognition": DETECTOR_BACKEND_RECOGNITION,
        "match_threshold": MATCH_THRESHOLD,
        "max_image_bytes": MAX_IMAGE_BYTES,
        "max_faces_per_image": MAX_FACES_PER_IMAGE,
        "augmentation_enabled": AUGMENTATION_ENABLED,
        "mongodb_configured": bool(MONGODB_URI),
        "mongodb_database": MONGODB_DB,
        "api_key_configured": bool(API_KEY),
    }
