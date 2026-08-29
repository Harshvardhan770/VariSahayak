"""Test fixtures for the face-matching service.

Two things are faked and nothing else:

* **MongoDB**, via mongomock, so the suite needs no server and each test starts empty.
* **DeepFace**, via patched module functions, so the suite needs no TensorFlow, no model
  weights, and no minutes of download. The CV code around DeepFace — decode, crop, augment,
  cosine distance, ranking, duplicate resolution — runs for real, because that is the code
  with the bugs in it.

Everything else is the actual application: the real Flask app, the real repository, the
real config.
"""

from __future__ import annotations

import base64
import os
import sys
from pathlib import Path

import numpy as np
import pytest

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

# Set before config is imported: it reads the environment once, at import.
os.environ.setdefault("FACE_API_KEY", "test-api-key")
os.environ.setdefault("MONGODB_URI", "mongodb://localhost:27017/test")
os.environ.setdefault("MONGODB_DB", "test_faces")
os.environ.setdefault("MONGODB_COLLECTION", "test_profiles")
os.environ.setdefault("LOG_LEVEL", "CRITICAL")

API_KEY = os.environ["FACE_API_KEY"]

import cv2  # noqa: E402

import config  # noqa: E402
import face_engine  # noqa: E402
import repository  # noqa: E402


# --- images ------------------------------------------------------------------------------


def _encode(image: np.ndarray) -> str:
    ok, buffer = cv2.imencode(".png", image)
    assert ok, "test image could not be encoded"
    return base64.b64encode(buffer.tobytes()).decode("ascii")


@pytest.fixture
def image_b64() -> str:
    """A valid, decodable image of the minimum usable size and then some."""
    canvas = np.full((240, 240, 3), 128, dtype=np.uint8)
    # Some structure, so grayscale conversion and cropping have something to act on.
    cv2.rectangle(canvas, (60, 60), (180, 180), (200, 200, 200), -1)
    return _encode(canvas)


@pytest.fixture
def tiny_image_b64() -> str:
    """Below MIN_IMAGE_DIMENSION — decodable, but too small to hold a face."""
    return _encode(np.full((16, 16, 3), 200, dtype=np.uint8))


@pytest.fixture
def not_an_image_b64() -> str:
    """Valid base64 that is not an image. cv2 must refuse it."""
    return base64.b64encode(b"this is plain text, not a PNG").decode("ascii")


# --- DeepFace doubles --------------------------------------------------------------------


def _face(x: int = 20, y: int = 20, w: int = 80, h: int = 80, confidence: float = 0.99) -> dict:
    return {"facial_area": {"x": x, "y": y, "w": w, "h": h}, "confidence": confidence}


class FakeVision:
    """A scriptable stand-in for the two DeepFace calls ``face_engine`` makes.

    ``faces`` is what detection returns. ``vectors`` is a queue consumed one per
    ``represent`` call; when it runs dry the last value repeats, which is what makes an
    augmented enrolment (nine calls) as easy to script as a single one.
    """

    def __init__(self) -> None:
        self.faces: list[dict] = [_face()]
        self.vectors: list[np.ndarray | None] = [np.ones(128, dtype=np.float64)]
        self.detect_calls = 0
        self.represent_calls = 0
        self.detect_error: Exception | None = None
        self.represent_error: Exception | None = None

    def extract_faces(self, image, detector):
        self.detect_calls += 1
        if self.detect_error:
            raise self.detect_error
        return list(self.faces)

    def represent(self, image, detector):
        self.represent_calls += 1
        if self.represent_error:
            raise self.represent_error
        if not self.vectors:
            return None
        if len(self.vectors) == 1:
            return self.vectors[0]
        return self.vectors.pop(0)


@pytest.fixture
def vision(monkeypatch) -> FakeVision:
    """Replaces face_engine's two DeepFace entry points for the duration of a test."""
    fake = FakeVision()

    monkeypatch.setattr(face_engine, "_detect", lambda image, detector: fake.extract_faces(image, detector))
    monkeypatch.setattr(face_engine, "represent", lambda image, detector: fake.represent(image, detector))

    return fake


@pytest.fixture
def make_face():
    """Builder for detector output, so tests can express 'two faces' in one line."""
    return _face


# --- database ----------------------------------------------------------------------------


@pytest.fixture
def mongo(monkeypatch):
    """An empty in-process MongoDB for one test.

    Patched at ``get_client`` rather than at each read and write, so the repository's real
    queries, projections and upserts all execute — including the unique index that makes a
    duplicate enrolment behave the way production does.
    """
    import mongomock

    client = mongomock.MongoClient()
    monkeypatch.setattr(repository, "get_client", lambda: client)

    collection = client[config.MONGODB_DB][config.MONGODB_COLLECTION]
    collection.create_index("person_id", unique=True)

    yield collection

    client.close()


@pytest.fixture
def broken_mongo(monkeypatch):
    """Every repository call raises the outage error, as it would during a real failure."""

    def unavailable(*_args, **_kwargs):
        raise repository.DatabaseUnavailable("database unavailable")

    for name in (
        "save_profile",
        "mark_status",
        "delete_profile",
        "get_profile",
        "list_profiles",
        "count_profiles",
        "get_embedding",
        "load_profiles_for_matching",
    ):
        monkeypatch.setattr(repository, name, unavailable)


# --- HTTP --------------------------------------------------------------------------------


@pytest.fixture
def client(monkeypatch):
    """The real Flask app, with start-up index creation stubbed out."""
    monkeypatch.setattr(repository, "ensure_indexes", lambda: None)

    import app as application

    application.app.config.update(TESTING=True)
    return application.app.test_client()


@pytest.fixture
def auth() -> dict:
    return {"X-API-Key": API_KEY}


@pytest.fixture
def enrolled(mongo):
    """Two READY profiles on opposite sides, with known, deliberately distinct vectors.

    The vectors are orthogonal-ish rather than random so distances in assertions are
    predictable: a query equal to one of them lands at distance 0 and far from the other.
    """
    lost = np.zeros(128, dtype=np.float64)
    lost[0] = 1.0

    found = np.zeros(128, dtype=np.float64)
    found[1] = 1.0

    repository.save_profile(
        person_id="lost-1",
        embedding=lost.tolist(),
        kind="LOST",
        subject_type="PERSON",
        model="Facenet",
        detector="retinaface",
        sample_count=9,
        metadata={"label": "child in blue"},
    )
    repository.save_profile(
        person_id="found-1",
        embedding=found.tolist(),
        kind="FOUND",
        subject_type="PERSON",
        model="Facenet",
        detector="retinaface",
        sample_count=9,
    )

    return {"lost": lost, "found": found}
