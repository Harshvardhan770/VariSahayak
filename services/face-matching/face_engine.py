"""Computer-vision core for VARI Sahayak Lost & Found face matching.

The pipeline is unchanged from the reference implementation this service is adapted from —
OpenCV decode and preprocessing, DeepFace/Facenet embeddings, augmented averaging, cosine
distance — with the student-attendance domain stripped out. What used to be a PRN or GR
number is now an opaque ``person_id``: this module never learns what the identifier means,
which is exactly why it can serve both sides of a Lost & Found board.

Nothing here talks to a database or to HTTP. It takes bytes and returns vectors, rankings
or errors, which is what makes it testable without a running service or a Mongo instance.
"""

from __future__ import annotations

import base64
import binascii
import logging
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional, Sequence

import cv2
import numpy as np

import config

logger = logging.getLogger(__name__)

# Re-exported so callers do not each reach into config for the same three values.
MODEL_NAME = config.MODEL_NAME
DETECTOR_BACKEND_REGISTRATION = config.DETECTOR_BACKEND_REGISTRATION
DETECTOR_BACKEND_RECOGNITION = config.DETECTOR_BACKEND_RECOGNITION
MATCH_THRESHOLD = config.MATCH_THRESHOLD
MAX_IMAGE_BYTES = config.MAX_IMAGE_BYTES
MIN_IMAGE_DIMENSION = config.MIN_IMAGE_DIMENSION


class FaceStatus(str, Enum):
    """Outcomes, mirroring the Android ``FaceMatchStatus`` enum.

    Every value except READY means "this record contributes no face signal". None of them
    means the record is invalid: a Lost & Found report is always saved on its non-photo
    fields, and a photo that cannot be processed simply stops contributing one signal out
    of ten.
    """

    READY = "READY"
    NO_FACE = "NO_FACE"
    MULTIPLE_FACES = "MULTIPLE_FACES"
    INVALID_IMAGE = "INVALID_IMAGE"
    SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"


class MatchConfidence(str, Enum):
    """How strongly a candidate stands out, for a human reading the review screen.

    A label, never a decision. HIGH does not authorise anything: section 7.32 forbids any
    automatic reunification from facial similarity alone, and every one of these still
    lands in front of a person.
    """

    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


#: Volunteer-facing wording. Deliberately actionable and free of anything technical —
#: no stack traces, library names, internal paths, or model details.
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
        "Face matching is temporarily unavailable. The record was saved and will continue "
        "using other matching information."
    ),
}


@dataclass(frozen=True)
class DetectedFace:
    """One face found in a submitted image.

    ``thumbnail_base64`` is a crop of the caller's own upload, returned only when asked
    for. It is never logged and never stored.
    """

    index: int
    confidence: float
    x: int
    y: int
    w: int
    h: int
    thumbnail_base64: Optional[str] = None

    def location(self) -> dict:
        return {"x": self.x, "y": self.y, "w": self.w, "h": self.h}

    def to_dict(self, include_thumbnail: bool = True) -> dict:
        body = {
            "index": self.index,
            "confidence": round(self.confidence, 4),
            "location": self.location(),
        }
        if include_thumbnail and self.thumbnail_base64:
            body["thumbnail"] = self.thumbnail_base64
        return body


@dataclass(frozen=True)
class DetectionResult:
    status: FaceStatus
    faces: list[DetectedFace] = field(default_factory=list)

    @property
    def message(self) -> str:
        return USER_MESSAGES[self.status]


@dataclass(frozen=True)
class EmbeddingResult:
    """A processed photo.

    ``embedding`` is populated only when ``status`` is READY, and it never leaves the
    process: the HTTP layer returns a status and, at most, distances — never the vector.
    """

    status: FaceStatus
    embedding: Optional[np.ndarray] = None
    sample_count: int = 0
    source_image_count: int = 0

    @property
    def message(self) -> str:
        return USER_MESSAGES[self.status]


@dataclass(frozen=True)
class Candidate:
    """A stored profile ranked against a queried face."""

    person_id: str
    distance: float
    confidence: MatchConfidence
    metadata: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "person_id": self.person_id,
            "distance": round(self.distance, 4),
            # Reported alongside distance because "0.79 similar" reads far more naturally
            # to a volunteer than "0.21 distant", and both are the same number.
            "similarity": round(1.0 - self.distance, 4),
            "confidence": self.confidence.value,
            **({"metadata": self.metadata} if self.metadata else {}),
        }


# --- image handling ----------------------------------------------------------------------


def decode_image(payload: str | bytes) -> Optional[np.ndarray]:
    """Decode a base64 or raw-bytes image into a 3-channel BGR array.

    Defensive at every step, because this is the one function reachable with attacker-
    controlled bytes. A failure returns None rather than raising, so the caller records
    INVALID_IMAGE and carries on saving the record.
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

    if len(raw) > config.MAX_IMAGE_BYTES:
        # Checked before cv2 touches it: decoding first would allocate the very thing the
        # cap exists to prevent.
        logger.info("Rejected an image of %d bytes, over the cap", len(raw))
        return None

    buffer = np.frombuffer(raw, dtype=np.uint8)
    image = cv2.imdecode(buffer, cv2.IMREAD_COLOR)

    if image is None:
        logger.info("cv2 could not decode the image; treating as invalid")
        return None

    height, width = image.shape[:2]
    if height < config.MIN_IMAGE_DIMENSION or width < config.MIN_IMAGE_DIMENSION:
        return None

    # Grayscale and back to 3-channel BGR, from the reference implementation. Discarding
    # colour makes the pipeline insensitive to the wildly different white balance of cheap
    # phone cameras in direct sun, which is the normal capture condition on the route.
    grayscale = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    return cv2.cvtColor(grayscale, cv2.COLOR_GRAY2BGR)


def _detect(image: np.ndarray, detector: str) -> list[dict]:
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

    return [
        f for f in faces
        if f.get("confidence", 0) > config.FACE_CONFIDENCE_THRESHOLD
    ]


def crop_face(image: np.ndarray, facial_area: dict) -> np.ndarray:
    """Crop to the detected face with a small margin, clamped to the image bounds."""
    height, width = image.shape[:2]

    x = int(facial_area.get("x", 0))
    y = int(facial_area.get("y", 0))
    w = int(facial_area.get("w", width))
    h = int(facial_area.get("h", height))

    margin_x = int(w * config.FACE_CROP_MARGIN_RATIO)
    margin_y = int(h * config.FACE_CROP_MARGIN_RATIO)

    left = max(0, x - margin_x)
    top = max(0, y - margin_y)
    right = min(width, x + w + margin_x)
    bottom = min(height, y + h + margin_y)

    if right <= left or bottom <= top:
        return image

    return image[top:bottom, left:right]


def encode_thumbnail(image: np.ndarray) -> Optional[str]:
    """A base64 JPEG of a crop, bounded so a crowd scene cannot inflate a response."""
    try:
        height, width = image.shape[:2]
        longest = max(height, width)
        if longest > config.THUMBNAIL_MAX_EDGE:
            scale = config.THUMBNAIL_MAX_EDGE / float(longest)
            image = cv2.resize(
                image,
                (max(1, int(width * scale)), max(1, int(height * scale))),
                interpolation=cv2.INTER_AREA,
            )

        ok, buffer = cv2.imencode(
            ".jpg", image, [int(cv2.IMWRITE_JPEG_QUALITY), config.THUMBNAIL_JPEG_QUALITY]
        )
        if not ok:
            return None
        return base64.b64encode(buffer.tobytes()).decode("ascii")
    except Exception:
        # A thumbnail is a convenience. Losing one must not fail the detection it
        # accompanies.
        logger.exception("Thumbnail encoding failed")
        return None


def augment(image: np.ndarray) -> list[np.ndarray]:
    """Synthetic variants of one crop, governed by the augmentation settings in ``config``.

    Every step is individually switchable so a slow VM can trade accuracy for latency
    without a code change.
    """
    if not config.AUGMENTATION_ENABLED:
        return []

    variants: list[np.ndarray] = []
    height, width = image.shape[:2]

    if config.AUGMENT_FLIP:
        variants.append(cv2.flip(image, 1))

    if config.AUGMENT_ROTATION_DEGREES > 0:
        for angle in (config.AUGMENT_ROTATION_DEGREES, -config.AUGMENT_ROTATION_DEGREES):
            matrix = cv2.getRotationMatrix2D((width / 2, height / 2), angle, 1.0)
            variants.append(
                cv2.warpAffine(
                    image, matrix, (width, height), borderMode=cv2.BORDER_REPLICATE
                )
            )

    if config.AUGMENT_BRIGHTNESS_DELTA > 0:
        delta = config.AUGMENT_BRIGHTNESS_DELTA
        variants.append(cv2.convertScaleAbs(image, alpha=1.0, beta=delta))
        variants.append(cv2.convertScaleAbs(image, alpha=1.0, beta=-delta))

    if config.AUGMENT_CONTRAST_HIGH > 1.0:
        variants.append(cv2.convertScaleAbs(image, alpha=config.AUGMENT_CONTRAST_HIGH, beta=0))
    if config.AUGMENT_CONTRAST_LOW < 1.0:
        variants.append(cv2.convertScaleAbs(image, alpha=config.AUGMENT_CONTRAST_LOW, beta=0))

    if config.AUGMENT_ZOOM_RATIO > 0:
        inset_x = int(width * config.AUGMENT_ZOOM_RATIO)
        inset_y = int(height * config.AUGMENT_ZOOM_RATIO)
        wide_enough = width - 2 * inset_x > config.MIN_IMAGE_DIMENSION
        tall_enough = height - 2 * inset_y > config.MIN_IMAGE_DIMENSION
        if wide_enough and tall_enough:
            cropped = image[inset_y:height - inset_y, inset_x:width - inset_x]
            variants.append(cv2.resize(cropped, (width, height)))

    return variants


def represent(image: np.ndarray, detector: str) -> Optional[np.ndarray]:
    """One Facenet embedding, or None if the image yields nothing usable."""
    from deepface import DeepFace

    try:
        result = DeepFace.represent(
            img_path=image,
            model_name=config.MODEL_NAME,
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


# --- public operations -------------------------------------------------------------------


def detect_faces(payload: str | bytes, include_thumbnails: bool = True) -> DetectionResult:
    """Locate every face in an image.

    Returns positions, and crops of the caller's own upload when asked. No embedding is
    computed here — this is the cheap call a client makes to ask "is there a usable face
    in this photo before I commit to it".
    """
    image = decode_image(payload)
    if image is None:
        return DetectionResult(FaceStatus.INVALID_IMAGE)

    raw_faces = _detect(image, config.DETECTOR_BACKEND_RECOGNITION)
    if not raw_faces:
        return DetectionResult(FaceStatus.NO_FACE)

    # Bounded before any per-face work. A photograph of a crowd is a legitimate upload and
    # an illegitimate amount of compute; the cap turns the second into a clear answer.
    if len(raw_faces) > config.MAX_FACES_PER_IMAGE:
        logger.info(
            "Image contained %d faces, over the cap of %d",
            len(raw_faces),
            config.MAX_FACES_PER_IMAGE,
        )
        raw_faces = raw_faces[: config.MAX_FACES_PER_IMAGE]

    faces: list[DetectedFace] = []
    for index, raw in enumerate(raw_faces):
        area = raw.get("facial_area", {}) or {}
        thumbnail = None
        if include_thumbnails and config.RETURN_THUMBNAILS:
            thumbnail = encode_thumbnail(crop_face(image, area))

        faces.append(
            DetectedFace(
                index=index,
                confidence=float(raw.get("confidence", 0.0)),
                x=int(area.get("x", 0)),
                y=int(area.get("y", 0)),
                w=int(area.get("w", 0)),
                h=int(area.get("h", 0)),
                thumbnail_base64=thumbnail,
            )
        )

    return DetectionResult(FaceStatus.READY, faces)


def embed_reference_images(payloads: Sequence[str | bytes]) -> EmbeddingResult:
    """Turn one or more reference photographs into a single averaged profile embedding.

    Exactly one usable face per reference image is required. Two faces in a photo of "the
    missing child" is genuinely ambiguous about which person the record is for, and
    guessing would be worse than declining — so the record is saved and the photo is
    marked unusable for automatic matching until a clearer one is supplied.

    Across several references the strictest failure wins, so a caller is told the real
    reason rather than being handed a profile silently built from a subset.
    """
    if not payloads:
        return EmbeddingResult(FaceStatus.INVALID_IMAGE)

    embeddings: list[np.ndarray] = []
    usable_images = 0

    for payload in payloads:
        image = decode_image(payload)
        if image is None:
            return EmbeddingResult(FaceStatus.INVALID_IMAGE)

        faces = _detect(image, config.DETECTOR_BACKEND_REGISTRATION)
        if not faces:
            return EmbeddingResult(FaceStatus.NO_FACE)
        if len(faces) > 1:
            return EmbeddingResult(FaceStatus.MULTIPLE_FACES)

        face = crop_face(image, faces[0].get("facial_area", {}) or {})

        base = represent(face, config.DETECTOR_BACKEND_RECOGNITION)
        if base is not None:
            embeddings.append(base)

        for variant in augment(face):
            vector = represent(variant, config.DETECTOR_BACKEND_RECOGNITION)
            # Variants that fail are skipped rather than aborting: losing two of eight
            # augmentations barely moves the average, and failing a whole enrolment over
            # one would throw away a perfectly good photograph.
            if vector is not None:
                embeddings.append(vector)

        usable_images += 1

    if not embeddings:
        return EmbeddingResult(FaceStatus.NO_FACE)

    averaged = np.mean(np.stack(embeddings), axis=0)
    return EmbeddingResult(
        status=FaceStatus.READY,
        embedding=averaged,
        sample_count=len(embeddings),
        source_image_count=usable_images,
    )


def embed_query_faces(payload: str | bytes) -> tuple[FaceStatus, list[tuple[DetectedFace, np.ndarray]]]:
    """Embed every face in a query image, for recognition.

    Unlike enrolment this accepts many faces on purpose: a volunteer photographing a group
    at a help point wants all of them checked, and refusing the photo would mean asking
    them to crop faces by hand in a crowd.

    Faces whose embedding fails are dropped rather than aborting the request — one
    unreadable face in a group of six must not cost the other five.
    """
    image = decode_image(payload)
    if image is None:
        return FaceStatus.INVALID_IMAGE, []

    raw_faces = _detect(image, config.DETECTOR_BACKEND_RECOGNITION)
    if not raw_faces:
        return FaceStatus.NO_FACE, []

    if len(raw_faces) > config.MAX_FACES_PER_IMAGE:
        logger.info("Query image had %d faces; capping", len(raw_faces))
        raw_faces = raw_faces[: config.MAX_FACES_PER_IMAGE]

    embedded: list[tuple[DetectedFace, np.ndarray]] = []
    for index, raw in enumerate(raw_faces):
        area = raw.get("facial_area", {}) or {}
        crop = crop_face(image, area)
        vector = represent(crop, config.DETECTOR_BACKEND_RECOGNITION)
        if vector is None:
            continue

        embedded.append(
            (
                DetectedFace(
                    index=index,
                    confidence=float(raw.get("confidence", 0.0)),
                    x=int(area.get("x", 0)),
                    y=int(area.get("y", 0)),
                    w=int(area.get("w", 0)),
                    h=int(area.get("h", 0)),
                    thumbnail_base64=None,
                ),
                vector,
            )
        )

    if not embedded:
        return FaceStatus.NO_FACE, []

    return FaceStatus.READY, embedded


# --- comparison --------------------------------------------------------------------------


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


def is_match(distance: float, threshold: Optional[float] = None) -> bool:
    """Whether a pair is eligible for the face signal.

    Eligibility is *not* identity. This produces a candidate for a human to review, and
    nothing in this service ever reunites anybody on facing similarity alone.
    """
    limit = config.MATCH_THRESHOLD if threshold is None else threshold
    return distance <= limit


def classify_confidence(
    distance: float, runner_up: Optional[float], threshold: float
) -> MatchConfidence:
    """Label a candidate by how far clear of the field, and of the bar, it sits.

    A candidate that only just scrapes the threshold, or that has a near-identical rival,
    is LOW however good its absolute distance looks. Both situations are exactly where a
    reviewer needs to look hardest.
    """
    if distance > threshold:
        return MatchConfidence.LOW

    clear_of_rival = runner_up is None or (runner_up - distance) >= config.MATCH_CONFIDENT_MARGIN
    comfortably_inside = distance <= threshold * 0.6

    if comfortably_inside and clear_of_rival:
        return MatchConfidence.HIGH
    if distance <= threshold * 0.85 and clear_of_rival:
        return MatchConfidence.MEDIUM
    return MatchConfidence.LOW


def rank_candidates(
    query: Sequence[float],
    profiles: Sequence[tuple[str, Sequence[float], dict]],
    threshold: float,
    max_results: int,
) -> tuple[list[Candidate], Optional[float]]:
    """Rank stored profiles against one queried face.

    Returns only candidates inside ``threshold``, plus the nearest distance overall. The
    nearest is reported even when nothing qualifies, because "closest was 0.71" tells an
    operator the threshold is doing its job — while remaining, deliberately, not a match.
    """
    scored = sorted(
        ((person_id, cosine_distance(query, vector), meta) for person_id, vector, meta in profiles),
        key=lambda row: row[1],
    )

    if not scored:
        return [], None

    nearest = scored[0][1]
    inside = [row for row in scored if row[1] <= threshold]

    candidates: list[Candidate] = []
    for position, (person_id, distance, meta) in enumerate(inside[:max_results]):
        runner_up = inside[position + 1][1] if position + 1 < len(inside) else None
        candidates.append(
            Candidate(
                person_id=person_id,
                distance=distance,
                confidence=classify_confidence(distance, runner_up, threshold),
                metadata=meta or {},
            )
        )

    return candidates, nearest


def resolve_duplicate_matches(
    rankings: dict[int, list[Candidate]],
) -> tuple[dict[int, list[Candidate]], list[dict]]:
    """Stop one stored person from being claimed by two faces in the same photograph.

    Two faces in a group shot cannot both be the same missing child. Left unresolved, the
    caller would be handed two contradictory matches and no way to choose, so the closest
    face keeps the person and the other falls through to its next candidate.

    Assignment is greedy over ascending distance, which is deterministic and reproducible
    when an assignment is later audited. Every displacement is reported rather than
    silently applied — a contested match is precisely the thing a reviewer should see.
    """
    claims: list[tuple[float, int, Candidate]] = [
        (candidate.distance, face_index, candidate)
        for face_index, candidates in rankings.items()
        for candidate in candidates
    ]
    # Sorted by distance, then face index, so equal distances resolve the same way twice.
    claims.sort(key=lambda row: (row[0], row[1]))

    winner_of: dict[str, int] = {}
    kept: dict[int, list[Candidate]] = {index: [] for index in rankings}
    contested: dict[str, set[int]] = {}

    for distance, face_index, candidate in claims:
        holder = winner_of.get(candidate.person_id)
        if holder is None:
            winner_of[candidate.person_id] = face_index
            kept[face_index].append(candidate)
        elif holder != face_index:
            contested.setdefault(candidate.person_id, {holder}).add(face_index)

    duplicates = [
        {"person_id": person_id, "face_indexes": sorted(faces), "assigned_to": winner_of[person_id]}
        for person_id, faces in sorted(contested.items())
    ]

    # Re-sorted: the greedy pass appends in global distance order, not per-face order.
    for face_index in kept:
        kept[face_index].sort(key=lambda c: c.distance)

    return kept, duplicates
