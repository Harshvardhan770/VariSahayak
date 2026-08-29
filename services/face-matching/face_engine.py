"""Computer-vision core for Lost & Found face matching.

Adapted from the project's reference face-matching implementation, preserving its
processing pipeline — OpenCV decode and preprocessing, DeepFace/Facenet embeddings,
augmentation, and cosine-distance comparison — while replacing its student-enrolment
domain and its MongoDB persistence with VARI Sahayak's Lost & Found records in the
existing PostgreSQL database.

Nothing in this module talks to a database or to HTTP. It takes bytes and returns vectors
or errors, which is what makes it testable without a running service.
"""

from __future__ import annotations

import base64
import binascii
import logging
from dataclasses import dataclass
from enum import Enum
from typing import Optional, Sequence

import cv2
import numpy as np

logger = logging.getLogger(__name__)

# --- configuration -----------------------------------------------------------------------
# Fixed by Plan 07 section 7.21A. MATCH_TOLERANCE in particular is an inherited engineering
# starting point, NOT a proven real-world identity threshold; it must be validated against
# representative Wari data before anyone relies on it operationally.

MODEL_NAME = "Facenet"
DETECTOR_BACKEND_REGISTRATION = "opencv"
DETECTOR_BACKEND_RECOGNITION = "retinaface"
MATCH_TOLERANCE = 0.40

# A single photo from a volunteer's phone. Capped before decoding so an oversized or
# malicious payload is rejected without ever being expanded into memory.
MAX_IMAGE_BYTES = 8 * 1024 * 1024

# Below this the image is too small to hold a usable face and decoding it wastes a
# DeepFace call.
MIN_IMAGE_DIMENSION = 64


class FaceStatus(str, Enum):
    """Outcomes, mirroring the Android `FaceMatchStatus` enum.

    Every value except READY means "this report contributes no face signal". None of them
    means the report is invalid: a Lost & Found report is always saved on its non-photo
    fields, and a photo that cannot be processed simply stops contributing one signal out
    of ten.
    """

    READY = "READY"
    NO_FACE = "NO_FACE"
    MULTIPLE_FACES = "MULTIPLE_FACES"
    INVALID_IMAGE = "INVALID_IMAGE"
    SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"


#: Volunteer-facing wording. Deliberately actionable and free of anything technical —
#: section 7.21G forbids surfacing stack traces, library names, or internal paths.
USER_MESSAGES = {
    FaceStatus.READY: "Photo processed.",
    FaceStatus.NO_FACE: (
        "No face was detected. You can continue without a photo or upload another image."
    ),
    FaceStatus.MULTIPLE_FACES: (
        "Multiple faces were detected. Please upload a photo containing only the person."
    ),
    FaceStatus.INVALID_IMAGE: "Photo could not be processed. Please upload a clearer photo.",
    FaceStatus.SERVICE_UNAVAILABLE: (
        "Face matching is temporarily unavailable. The report was saved and will continue "
        "using other matching information."
    ),
}


@dataclass(frozen=True)
class EmbeddingResult:
    """A processed photo.

    ``embedding`` is populated only when ``status`` is READY. It never leaves the server:
    the Flask layer returns the status and, at most, distances — never the vector itself.
    """

    status: FaceStatus
    embedding: Optional[np.ndarray] = None
    sample_count: int = 0

    @property
    def message(self) -> str:
        return USER_MESSAGES[self.status]


def decode_image(payload: str | bytes) -> Optional[np.ndarray]:
    """Decode a base64 or raw-bytes image into a 3-channel BGR array.

    Defensive at every step, because this is the one function reachable with attacker-
    controlled bytes. A failure returns None rather than raising, so the caller can record
    INVALID_IMAGE and carry on saving the report.
    """
    try:
        if isinstance(payload, str):
            # Tolerate a data URL prefix: browsers and some clients include one.
            if "," in payload[:64] and payload.lstrip().startswith("data:"):
                payload = payload.split(",", 1)[1]
            raw = base64.b64decode(payload, validate=True)
        else:
            raw = payload
    except (binascii.Error, ValueError):
        logger.info("Rejected an image payload that was not valid base64")
        return None

    if not raw:
        return None

    if len(raw) > MAX_IMAGE_BYTES:
        # Checked before cv2 touches it: decoding first would mean allocating the very
        # thing the cap exists to prevent.
        logger.info("Rejected an image of %d bytes, over the cap", len(raw))
        return None

    buffer = np.frombuffer(raw, dtype=np.uint8)
    image = cv2.imdecode(buffer, cv2.IMREAD_COLOR)

    if image is None:
        logger.info("cv2 could not decode the image; treating as invalid")
        return None

    height, width = image.shape[:2]
    if height < MIN_IMAGE_DIMENSION or width < MIN_IMAGE_DIMENSION:
        return None

    # Grayscale and back to 3-channel BGR, from the reference implementation. Discarding
    # colour makes the pipeline insensitive to the wildly different white balance of cheap
    # phone cameras in direct sun, which is the normal capture condition on the route.
    grayscale = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    return cv2.cvtColor(grayscale, cv2.COLOR_GRAY2BGR)


def detect_faces(image: np.ndarray, detector: str) -> list[dict]:
    """Return the faces DeepFace can find, or an empty list.

    Any exception from the detector is swallowed into "no faces". A detector that cannot
    load its model is not a reason to fail a volunteer's report.
    """
    from deepface import DeepFace

    try:
        faces = DeepFace.extract_faces(
            img_path=image,
            detector_backend=detector,
            enforce_detection=False,
            align=True,
        )
    except Exception:
        logger.exception("Face detection failed")
        return []

    # extract_faces with enforce_detection=False returns a whole-image "face" with a
    # near-zero confidence when it finds nothing. Filter those out or every photo of a
    # wall would enrol successfully.
    return [f for f in faces if f.get("confidence", 0) > 0.5]


def crop_face(image: np.ndarray, facial_area: dict) -> np.ndarray:
    """Crop to the detected face with a small margin, clamped to the image bounds."""
    height, width = image.shape[:2]

    x = int(facial_area.get("x", 0))
    y = int(facial_area.get("y", 0))
    w = int(facial_area.get("w", width))
    h = int(facial_area.get("h", height))

    # A margin, because Facenet was trained on faces with some surrounding context and a
    # tight crop measurably degrades the embedding.
    margin_x = int(w * 0.15)
    margin_y = int(h * 0.15)

    left = max(0, x - margin_x)
    top = max(0, y - margin_y)
    right = min(width, x + w + margin_x)
    bottom = min(height, y + h + margin_y)

    if right <= left or bottom <= top:
        return image

    return image[top:bottom, left:right]


def augment(image: np.ndarray) -> list[np.ndarray]:
    """The reference implementation's eight synthetic variants.

    Averaging embeddings across small perturbations makes the stored vector far less
    sensitive to the angle and lighting of the one photograph a volunteer happened to
    take. On a walking route in changing daylight that is the difference between a usable
    match and a near miss.
    """
    variants: list[np.ndarray] = []
    height, width = image.shape[:2]

    variants.append(cv2.flip(image, 1))

    for angle in (12, -12):
        matrix = cv2.getRotationMatrix2D((width / 2, height / 2), angle, 1.0)
        variants.append(
            cv2.warpAffine(image, matrix, (width, height), borderMode=cv2.BORDER_REPLICATE)
        )

    variants.append(cv2.convertScaleAbs(image, alpha=1.0, beta=30))    # brighter
    variants.append(cv2.convertScaleAbs(image, alpha=1.0, beta=-30))   # darker
    variants.append(cv2.convertScaleAbs(image, alpha=1.3, beta=0))     # more contrast
    variants.append(cv2.convertScaleAbs(image, alpha=0.75, beta=0))    # less contrast

    # Slight central crop, approximating the subject standing a step closer.
    inset_x, inset_y = int(width * 0.08), int(height * 0.08)
    if width - 2 * inset_x > MIN_IMAGE_DIMENSION and height - 2 * inset_y > MIN_IMAGE_DIMENSION:
        cropped = image[inset_y:height - inset_y, inset_x:width - inset_x]
        variants.append(cv2.resize(cropped, (width, height)))

    return variants


def represent(image: np.ndarray, detector: str) -> Optional[np.ndarray]:
    """One Facenet embedding, or None if the image yields nothing usable."""
    from deepface import DeepFace

    try:
        result = DeepFace.represent(
            img_path=image,
            model_name=MODEL_NAME,
            detector_backend=detector,
            enforce_detection=False,
            align=True,
        )
    except Exception:
        logger.exception("Embedding generation failed")
        return None

    if not result:
        return None

    vector = np.asarray(result[0].get("embedding", []), dtype=np.float64)
    return vector if vector.size else None


def enrol(payload: str | bytes) -> EmbeddingResult:
    """Turn a submitted photograph into a stored profile embedding.

    Exactly one usable face is required. Two faces in a photo of "the missing child" is
    genuinely ambiguous about which person the report is for, and guessing would be worse
    than declining — so the report is saved and the photo is marked unusable for automatic
    matching until a clearer one is supplied.
    """
    image = decode_image(payload)
    if image is None:
        return EmbeddingResult(FaceStatus.INVALID_IMAGE)

    faces = detect_faces(image, DETECTOR_BACKEND_REGISTRATION)

    if not faces:
        return EmbeddingResult(FaceStatus.NO_FACE)
    if len(faces) > 1:
        return EmbeddingResult(FaceStatus.MULTIPLE_FACES)

    face = crop_face(image, faces[0].get("facial_area", {}))

    embeddings: list[np.ndarray] = []
    base = represent(face, DETECTOR_BACKEND_RECOGNITION)
    if base is not None:
        embeddings.append(base)

    for variant in augment(face):
        vector = represent(variant, DETECTOR_BACKEND_RECOGNITION)
        # Variants that fail are skipped rather than aborting: losing two of eight
        # augmentations barely moves the average, and failing the whole enrolment over one
        # would throw away a perfectly good photograph.
        if vector is not None:
            embeddings.append(vector)

    if not embeddings:
        return EmbeddingResult(FaceStatus.NO_FACE)

    averaged = np.mean(np.stack(embeddings), axis=0)
    return EmbeddingResult(
        status=FaceStatus.READY,
        embedding=averaged,
        sample_count=len(embeddings),
    )


def cosine_distance(a: Sequence[float], b: Sequence[float]) -> float:
    """``1 - cosine_similarity``, matching the reference implementation.

    Returns 1.0 (maximally unrelated) rather than raising for a zero vector or a length
    mismatch, so one malformed stored embedding cannot abort a whole comparison run.
    """
    va = np.asarray(a, dtype=np.float64)
    vb = np.asarray(b, dtype=np.float64)

    if va.size == 0 or vb.size == 0 or va.size != vb.size:
        return 1.0

    denominator = np.linalg.norm(va) * np.linalg.norm(vb)
    if denominator == 0:
        return 1.0

    similarity = float(np.dot(va, vb) / denominator)
    # Clamp: floating-point error can put this a hair outside [-1, 1].
    return 1.0 - max(-1.0, min(1.0, similarity))


def is_match(distance: float) -> bool:
    """Whether a pair is eligible for the face signal.

    Eligibility is *not* identity. This produces a candidate for a human to review, and
    section 7.32 forbids any automatic reunification from facial similarity alone.
    """
    return distance <= MATCH_TOLERANCE
