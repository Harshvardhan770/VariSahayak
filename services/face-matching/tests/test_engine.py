"""Unit tests for the vision and ranking core.

These exercise the real OpenCV code — decode, grayscale, crop, augment — and the pure
maths. Only DeepFace itself is doubled.
"""

from __future__ import annotations

import base64

import cv2
import numpy as np
import pytest

import config
import face_engine


# --- decoding ----------------------------------------------------------------------------


def test_decode_accepts_a_data_url_prefix(image_b64):
    assert face_engine.decode_image(f"data:image/png;base64,{image_b64}") is not None


def test_decode_accepts_raw_bytes(image_b64):
    assert face_engine.decode_image(base64.b64decode(image_b64)) is not None


def test_decode_returns_three_channels(image_b64):
    """Grayscale then back to BGR, so the pipeline ignores phone white balance."""
    decoded = face_engine.decode_image(image_b64)
    assert decoded.shape[2] == 3
    # All three channels equal is the signature of the grayscale round trip.
    assert np.array_equal(decoded[:, :, 0], decoded[:, :, 1])
    assert np.array_equal(decoded[:, :, 1], decoded[:, :, 2])


@pytest.mark.parametrize("payload", ["", "not base64!!", "aGVsbG8="])
def test_decode_rejects_rubbish(payload):
    assert face_engine.decode_image(payload) is None


def test_decode_never_raises_on_hostile_input():
    """This is the one function reachable with attacker-controlled bytes."""
    for payload in (b"\x00" * 100, b"\xff\xd8\xff", "A" * 1000, b""):
        assert face_engine.decode_image(payload) is None


# --- cropping and augmentation -----------------------------------------------------------


def test_crop_stays_inside_the_image(image_b64):
    image = face_engine.decode_image(image_b64)
    crop = face_engine.crop_face(image, {"x": 200, "y": 200, "w": 300, "h": 300})

    assert crop.size > 0
    assert crop.shape[0] <= image.shape[0]
    assert crop.shape[1] <= image.shape[1]


def test_crop_falls_back_to_the_whole_image_when_degenerate(image_b64):
    image = face_engine.decode_image(image_b64)
    crop = face_engine.crop_face(image, {"x": 0, "y": 0, "w": 0, "h": 0})
    assert crop.shape == image.shape


def test_augment_produces_variants(image_b64):
    image = face_engine.decode_image(image_b64)
    variants = face_engine.augment(image)

    assert len(variants) >= 7
    assert all(v.size > 0 for v in variants)


def test_augmentation_can_be_switched_off(image_b64, monkeypatch):
    monkeypatch.setattr(config, "AUGMENTATION_ENABLED", False)
    assert face_engine.augment(face_engine.decode_image(image_b64)) == []


def test_individual_augmentations_are_switchable(image_b64, monkeypatch):
    image = face_engine.decode_image(image_b64)
    full = len(face_engine.augment(image))

    monkeypatch.setattr(config, "AUGMENT_FLIP", False)
    monkeypatch.setattr(config, "AUGMENT_ROTATION_DEGREES", 0.0)

    assert len(face_engine.augment(image)) == full - 3


def test_thumbnail_is_bounded(image_b64, monkeypatch):
    monkeypatch.setattr(config, "THUMBNAIL_MAX_EDGE", 32)
    image = face_engine.decode_image(image_b64)

    encoded = face_engine.encode_thumbnail(image)
    decoded = cv2.imdecode(
        np.frombuffer(base64.b64decode(encoded), np.uint8), cv2.IMREAD_COLOR
    )

    assert max(decoded.shape[:2]) <= 32


# --- cosine distance ---------------------------------------------------------------------


def test_identical_vectors_are_zero_distance():
    v = np.array([1.0, 2.0, 3.0])
    assert face_engine.cosine_distance(v, v) == pytest.approx(0.0, abs=1e-9)


def test_orthogonal_vectors_are_one():
    assert face_engine.cosine_distance([1.0, 0.0], [0.0, 1.0]) == pytest.approx(1.0)


def test_opposite_vectors_are_two():
    assert face_engine.cosine_distance([1.0, 0.0], [-1.0, 0.0]) == pytest.approx(2.0)


@pytest.mark.parametrize(
    "a,b",
    [([], [1.0]), ([1.0], []), ([1.0, 2.0], [1.0]), ([0.0, 0.0], [1.0, 1.0])],
)
def test_degenerate_pairs_are_maximally_unrelated_not_errors(a, b):
    """One malformed stored vector must not abort a whole comparison run."""
    assert face_engine.cosine_distance(a, b) == 1.0


def test_distance_never_escapes_its_range():
    rng = np.random.default_rng(7)
    for _ in range(200):
        a, b = rng.normal(size=128), rng.normal(size=128)
        assert 0.0 <= face_engine.cosine_distance(a, b) <= 2.0


# --- thresholds and confidence -----------------------------------------------------------


def test_is_match_uses_the_configured_threshold(monkeypatch):
    monkeypatch.setattr(config, "MATCH_THRESHOLD", 0.4)
    assert face_engine.is_match(0.39)
    assert face_engine.is_match(0.40)
    assert not face_engine.is_match(0.41)


def test_is_match_accepts_an_override():
    assert face_engine.is_match(0.8, threshold=0.9)
    assert not face_engine.is_match(0.8, threshold=0.5)


def test_a_candidate_outside_the_threshold_is_never_confident():
    assert face_engine.classify_confidence(0.9, None, 0.4) is face_engine.MatchConfidence.LOW


def test_a_close_and_uncontested_candidate_is_high():
    assert face_engine.classify_confidence(0.1, 0.9, 0.4) is face_engine.MatchConfidence.HIGH


def test_a_near_identical_rival_drops_confidence():
    """Two candidates a hair apart is exactly where a reviewer must look hardest."""
    assert face_engine.classify_confidence(0.10, 0.11, 0.4) is not face_engine.MatchConfidence.HIGH


def test_a_candidate_that_only_just_scrapes_in_is_low():
    assert face_engine.classify_confidence(0.39, None, 0.4) is face_engine.MatchConfidence.LOW


# --- ranking -----------------------------------------------------------------------------


def _unit(index: int, size: int = 8) -> list[float]:
    vector = [0.0] * size
    vector[index] = 1.0
    return vector


def test_ranking_orders_by_distance():
    query = _unit(0)
    profiles = [
        ("far", _unit(1), {}),
        ("exact", _unit(0), {}),
        ("near", [1.0, 0.2, 0, 0, 0, 0, 0, 0], {}),
    ]

    candidates, nearest = face_engine.rank_candidates(query, profiles, threshold=1.0, max_results=5)

    assert [c.person_id for c in candidates] == ["exact", "near", "far"]
    assert nearest == pytest.approx(0.0, abs=1e-9)


def test_ranking_excludes_everything_outside_the_threshold():
    candidates, nearest = face_engine.rank_candidates(
        _unit(0), [("far", _unit(1), {})], threshold=0.4, max_results=5
    )

    assert candidates == []
    # Still reported, so an operator can see how close the field came.
    assert nearest == pytest.approx(1.0)


def test_ranking_caps_results():
    profiles = [(f"p{i}", _unit(0), {}) for i in range(10)]
    candidates, _ = face_engine.rank_candidates(_unit(0), profiles, threshold=1.0, max_results=3)
    assert len(candidates) == 3


def test_ranking_on_an_empty_catalogue():
    candidates, nearest = face_engine.rank_candidates(_unit(0), [], threshold=0.4, max_results=5)
    assert candidates == []
    assert nearest is None


def test_ranking_carries_metadata_through():
    candidates, _ = face_engine.rank_candidates(
        _unit(0), [("p", _unit(0), {"label": "x"})], threshold=1.0, max_results=1
    )
    assert candidates[0].to_dict()["metadata"] == {"label": "x"}


def test_candidate_reports_similarity_alongside_distance():
    candidate = face_engine.Candidate("p", 0.25, face_engine.MatchConfidence.HIGH)
    body = candidate.to_dict()
    assert body["distance"] == 0.25
    assert body["similarity"] == 0.75


# --- duplicate resolution ----------------------------------------------------------------


def _candidate(person_id: str, distance: float) -> face_engine.Candidate:
    return face_engine.Candidate(person_id, distance, face_engine.MatchConfidence.MEDIUM)


def test_closest_face_wins_a_contested_person():
    rankings = {
        0: [_candidate("alice", 0.30)],
        1: [_candidate("alice", 0.10)],
    }

    kept, duplicates = face_engine.resolve_duplicate_matches(rankings)

    assert [c.person_id for c in kept[1]] == ["alice"]
    assert kept[0] == []
    assert duplicates == [{"person_id": "alice", "face_indexes": [0, 1], "assigned_to": 1}]


def test_a_displaced_face_falls_through_to_its_next_candidate():
    rankings = {
        0: [_candidate("alice", 0.30), _candidate("bob", 0.35)],
        1: [_candidate("alice", 0.10)],
    }

    kept, duplicates = face_engine.resolve_duplicate_matches(rankings)

    assert [c.person_id for c in kept[0]] == ["bob"]
    assert [c.person_id for c in kept[1]] == ["alice"]
    assert len(duplicates) == 1


def test_uncontested_rankings_are_untouched():
    rankings = {0: [_candidate("alice", 0.1)], 1: [_candidate("bob", 0.2)]}

    kept, duplicates = face_engine.resolve_duplicate_matches(rankings)

    assert [c.person_id for c in kept[0]] == ["alice"]
    assert [c.person_id for c in kept[1]] == ["bob"]
    assert duplicates == []


def test_resolution_is_deterministic_on_a_tie():
    """An assignment has to be reproducible when it is later audited."""
    rankings = {1: [_candidate("alice", 0.2)], 0: [_candidate("alice", 0.2)]}

    first, _ = face_engine.resolve_duplicate_matches(rankings)
    second, _ = face_engine.resolve_duplicate_matches(rankings)

    assert {k: [c.person_id for c in v] for k, v in first.items()} == {
        k: [c.person_id for c in v] for k, v in second.items()
    }
    # Lowest face index breaks the tie.
    assert [c.person_id for c in first[0]] == ["alice"]


def test_kept_candidates_stay_sorted_per_face():
    rankings = {
        0: [_candidate("a", 0.10), _candidate("b", 0.20), _candidate("c", 0.30)],
        1: [_candidate("d", 0.15)],
    }

    kept, _ = face_engine.resolve_duplicate_matches(rankings)

    assert [c.distance for c in kept[0]] == sorted(c.distance for c in kept[0])


def test_resolution_handles_an_empty_ranking():
    kept, duplicates = face_engine.resolve_duplicate_matches({0: [], 1: []})
    assert kept == {0: [], 1: []}
    assert duplicates == []


# --- enrolment pipeline ------------------------------------------------------------------


def test_enrolment_averages_the_augmented_embeddings(image_b64, vision):
    vision.vectors = [np.full(4, 2.0), np.full(4, 4.0)]

    result = face_engine.embed_reference_images([image_b64])

    assert result.status is face_engine.FaceStatus.READY
    assert result.sample_count > 1
    # First call yields 2.0, every later call yields 4.0, so the mean sits between them.
    assert 2.0 < float(result.embedding[0]) <= 4.0


def test_enrolment_survives_some_failing_variants(image_b64, vision):
    """Losing two of eight augmentations barely moves the average; failing would waste a photo."""
    vision.vectors = [np.ones(4), None, np.ones(4)]

    result = face_engine.embed_reference_images([image_b64])
    assert result.status is face_engine.FaceStatus.READY


def test_enrolment_with_no_usable_vector_at_all(image_b64, vision):
    vision.vectors = [None]
    assert face_engine.embed_reference_images([image_b64]).status is face_engine.FaceStatus.NO_FACE


def test_enrolment_with_no_images():
    assert face_engine.embed_reference_images([]).status is face_engine.FaceStatus.INVALID_IMAGE


def test_the_strictest_failure_wins_across_references(image_b64, vision, make_face):
    """A caller must hear the real reason, not get a profile built from a silent subset."""
    vision.faces = [make_face(), make_face(120, 120)]
    assert (
        face_engine.embed_reference_images([image_b64, image_b64]).status
        is face_engine.FaceStatus.MULTIPLE_FACES
    )


def test_query_embedding_returns_one_entry_per_face(image_b64, vision, make_face):
    vision.faces = [make_face(10, 10), make_face(120, 120)]
    vision.vectors = [np.ones(4), np.zeros(4) + 2]

    status, embedded = face_engine.embed_query_faces(image_b64)

    assert status is face_engine.FaceStatus.READY
    assert len(embedded) == 2
    assert [face.index for face, _ in embedded] == [0, 1]


def _fake_deepface(monkeypatch, extract=None, represent=None):
    """Install a stand-in `deepface` module.

    Both engine functions import DeepFace lazily inside the call, so replacing the module
    in sys.modules is enough — and it is the only way to test the real bodies of `_detect`
    and `represent` rather than the fixture's replacements for them.
    """
    import sys
    import types

    class DeepFace:
        @staticmethod
        def extract_faces(**kwargs):
            if extract is None:
                raise AssertionError("extract_faces was not expected")
            return extract(**kwargs)

        @staticmethod
        def represent(**kwargs):
            if represent is None:
                raise AssertionError("represent was not expected")
            return represent(**kwargs)

    module = types.ModuleType("deepface")
    module.DeepFace = DeepFace
    monkeypatch.setitem(sys.modules, "deepface", module)


def test_a_detector_exception_becomes_no_faces(image_b64, monkeypatch):
    """A detector that cannot load its model is not a reason to fail a volunteer's report."""

    def explode(**_kwargs):
        raise RuntimeError("model weights are missing")

    _fake_deepface(monkeypatch, extract=explode)

    image = face_engine.decode_image(image_b64)
    assert face_engine._detect(image, "opencv") == []


def test_low_confidence_detections_are_discarded(image_b64, monkeypatch):
    """extract_faces returns the whole frame at near-zero confidence when it finds nothing.

    Without this filter every photograph of a wall would enrol successfully.
    """
    monkeypatch.setattr(config, "FACE_CONFIDENCE_THRESHOLD", 0.5)

    _fake_deepface(
        monkeypatch,
        extract=lambda **_k: [
            {"facial_area": {"x": 0, "y": 0, "w": 10, "h": 10}, "confidence": 0.01},
            {"facial_area": {"x": 5, "y": 5, "w": 20, "h": 20}, "confidence": 0.98},
        ],
    )

    image = face_engine.decode_image(image_b64)
    faces = face_engine._detect(image, "opencv")

    assert len(faces) == 1
    assert faces[0]["confidence"] == 0.98


def test_an_embedding_exception_becomes_none(image_b64, monkeypatch):
    def explode(**_kwargs):
        raise RuntimeError("out of memory")

    _fake_deepface(monkeypatch, represent=explode)

    assert face_engine.represent(face_engine.decode_image(image_b64), "opencv") is None


def test_an_empty_embedding_response_becomes_none(image_b64, monkeypatch):
    _fake_deepface(monkeypatch, represent=lambda **_k: [{"embedding": []}])
    assert face_engine.represent(face_engine.decode_image(image_b64), "opencv") is None
