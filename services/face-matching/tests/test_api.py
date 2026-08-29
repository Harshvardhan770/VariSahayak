"""End-to-end tests for the HTTP surface.

Covers what the refactor promised: registration, recognition, invalid images, no-face,
multiple faces, unmatched faces, duplicate matches, authentication, and MongoDB failure.

Every test also implicitly asserts the confidentiality rule, because
``test_disclosure.py`` sweeps every response these produce for embeddings, credentials and
tracebacks.
"""

from __future__ import annotations

import numpy as np
import pytest

import config
import face_engine
import repository


# --- status ------------------------------------------------------------------------------


def test_root_reports_running(client):
    response = client.get("/")
    assert response.status_code == 200
    body = response.get_json()
    assert body["ok"] is True
    assert body["status"] == "running"


def test_health_is_liveness_only(client):
    """No database, model or configuration detail: the probe is unauthenticated."""
    response = client.get("/health")
    assert response.status_code == 200
    body = response.get_json()
    assert body == {"ok": True, "status": "healthy"}


def test_root_and_health_need_no_key(client):
    assert client.get("/").status_code == 200
    assert client.get("/health").status_code == 200


# --- authentication ----------------------------------------------------------------------


@pytest.mark.parametrize(
    "method,path",
    [
        ("post", "/detect_faces"),
        ("post", "/enroll"),
        ("post", "/enrol"),
        ("post", "/recognize"),
        ("post", "/compare"),
        ("get", "/persons"),
        ("get", "/persons/anything"),
        ("delete", "/persons/anything"),
    ],
)
def test_every_data_route_requires_a_key(client, method, path):
    response = getattr(client, method)(path, json={})
    assert response.status_code == 401
    assert response.get_json()["ok"] is False


def test_wrong_key_is_refused(client, image_b64):
    response = client.post(
        "/detect_faces", json={"image": image_b64}, headers={"X-API-Key": "not-the-key"}
    )
    assert response.status_code == 401


def test_legacy_service_token_header_still_works(client, image_b64, vision, mongo):
    """The backend that predates the rename must keep working across this deploy."""
    response = client.post(
        "/detect_faces",
        json={"image": image_b64},
        headers={"X-Service-Token": config.API_KEY},
    )
    assert response.status_code == 200


def test_unconfigured_key_closes_the_door(client, image_b64, auth, monkeypatch):
    """No key configured means refuse everything, never run open."""
    monkeypatch.setattr(config, "API_KEY", "")
    response = client.post("/detect_faces", json={"image": image_b64}, headers=auth)
    assert response.status_code == 401


# --- detection ---------------------------------------------------------------------------


def test_detect_returns_locations_and_thumbnails(client, auth, image_b64, vision, make_face):
    vision.faces = [make_face(10, 10, 60, 60), make_face(120, 120, 50, 50)]

    response = client.post("/detect_faces", json={"image": image_b64}, headers=auth)
    body = response.get_json()

    assert response.status_code == 200
    assert body["status"] == "READY"
    assert body["face_count"] == 2
    assert body["faces"][0]["location"] == {"x": 10, "y": 10, "w": 60, "h": 60}
    assert body["faces"][0]["thumbnail"]


def test_detect_can_omit_thumbnails(client, auth, image_b64, vision):
    response = client.post(
        "/detect_faces", json={"image": image_b64, "include_thumbnails": False}, headers=auth
    )
    assert "thumbnail" not in response.get_json()["faces"][0]


def test_detect_requires_an_image(client, auth):
    response = client.post("/detect_faces", json={}, headers=auth)
    assert response.status_code == 400


def test_detect_caps_a_crowd(client, auth, image_b64, vision, make_face, monkeypatch):
    monkeypatch.setattr(config, "MAX_FACES_PER_IMAGE", 3)
    vision.faces = [make_face(i * 10, 0, 20, 20) for i in range(9)]

    body = client.post("/detect_faces", json={"image": image_b64}, headers=auth).get_json()
    assert body["face_count"] == 3


# --- invalid images ----------------------------------------------------------------------


@pytest.mark.parametrize("field", ["image", "images"])
def test_undecodable_payload_is_invalid_image(client, auth, not_an_image_b64, vision, field):
    payload = {field: not_an_image_b64 if field == "image" else [not_an_image_b64]}
    body = client.post("/detect_faces", json=payload, headers=auth).get_json()

    assert body["status"] == "INVALID_IMAGE"
    assert body["ok"] is False


def test_not_base64_is_invalid_image(client, auth, vision):
    body = client.post("/detect_faces", json={"image": "%%%not base64%%%"}, headers=auth).get_json()
    assert body["status"] == "INVALID_IMAGE"


def test_image_below_minimum_dimension_is_invalid(client, auth, tiny_image_b64, vision):
    body = client.post("/detect_faces", json={"image": tiny_image_b64}, headers=auth).get_json()
    assert body["status"] == "INVALID_IMAGE"


def test_oversized_image_is_rejected_before_decode(monkeypatch, image_b64):
    monkeypatch.setattr(config, "MAX_IMAGE_BYTES", 16)
    assert face_engine.decode_image(image_b64) is None


# --- registration ------------------------------------------------------------------------


def test_enroll_stores_an_averaged_profile(client, auth, image_b64, vision, mongo):
    response = client.post(
        "/enroll",
        json={"person_id": "lost-42", "kind": "LOST", "image": image_b64},
        headers=auth,
    )
    body = response.get_json()

    assert response.status_code == 200
    assert body["ok"] is True
    assert body["status"] == "READY"
    assert body["person_id"] == "lost-42"
    # One base embedding plus every augmentation variant that produced one.
    assert body["sample_count"] > 1

    stored = mongo.find_one({"person_id": "lost-42"})
    assert stored["status"] == "READY"
    assert stored["kind"] == "LOST"
    assert len(stored["embedding"]) == 128


def test_enroll_accepts_multiple_reference_images(client, auth, image_b64, vision, mongo):
    body = client.post(
        "/enroll",
        json={"person_id": "found-7", "kind": "FOUND", "images": [image_b64, image_b64]},
        headers=auth,
    ).get_json()

    assert body["reference_images"] == 2
    assert mongo.find_one({"person_id": "found-7"})["kind"] == "FOUND"


def test_enroll_honours_the_reference_image_cap(client, auth, image_b64, vision, monkeypatch, mongo):
    monkeypatch.setattr(config, "MAX_REFERENCE_IMAGES", 2)
    body = client.post(
        "/enroll", json={"person_id": "p", "images": [image_b64] * 6}, headers=auth
    ).get_json()
    assert body["reference_images"] == 2


def test_enrol_british_spelling_is_the_same_route(client, auth, image_b64, vision, mongo):
    """The deployed Supabase edge function calls /enrol; it must not break."""
    response = client.post(
        "/enrol", json={"report_client_id": "legacy-1", "image": image_b64}, headers=auth
    )
    assert response.status_code == 200
    assert mongo.find_one({"person_id": "legacy-1"})["status"] == "READY"


def test_enroll_requires_a_person_id(client, auth, image_b64, vision):
    response = client.post("/enroll", json={"image": image_b64}, headers=auth)
    assert response.status_code == 400


def test_enroll_requires_an_image(client, auth):
    response = client.post("/enroll", json={"person_id": "x"}, headers=auth)
    assert response.status_code == 400


def test_re_enrolling_replaces_rather_than_duplicates(client, auth, image_b64, vision, mongo):
    for _ in range(2):
        client.post("/enroll", json={"person_id": "same", "image": image_b64}, headers=auth)

    assert mongo.count_documents({"person_id": "same"}) == 1


# --- no face / multiple faces ------------------------------------------------------------


def test_enroll_with_no_face_is_recorded_not_failed(client, auth, image_b64, vision, mongo):
    """A photo with no face is a normal outcome. The report it belongs to stays valid."""
    vision.faces = []

    response = client.post(
        "/enroll", json={"person_id": "no-face", "image": image_b64}, headers=auth
    )
    body = response.get_json()

    assert response.status_code == 200
    assert body["ok"] is False
    assert body["status"] == "NO_FACE"

    stored = mongo.find_one({"person_id": "no-face"})
    assert stored["status"] == "NO_FACE"
    assert "embedding" not in stored


def test_enroll_with_two_faces_declines_rather_than_guessing(
    client, auth, image_b64, vision, make_face, mongo
):
    vision.faces = [make_face(10, 10), make_face(120, 120)]

    body = client.post(
        "/enroll", json={"person_id": "two-faces", "image": image_b64}, headers=auth
    ).get_json()

    assert body["status"] == "MULTIPLE_FACES"
    assert "embedding" not in mongo.find_one({"person_id": "two-faces"})


def test_unusable_photo_clears_a_previous_embedding(client, auth, image_b64, vision, mongo):
    """A replaced photograph must stop the record matching on the old picture."""
    client.post("/enroll", json={"person_id": "swap", "image": image_b64}, headers=auth)
    assert mongo.find_one({"person_id": "swap"})["embedding"]

    vision.faces = []
    client.post("/enroll", json={"person_id": "swap", "image": image_b64}, headers=auth)

    stored = mongo.find_one({"person_id": "swap"})
    assert stored["status"] == "NO_FACE"
    assert "embedding" not in stored


def test_detect_with_no_face(client, auth, image_b64, vision):
    vision.faces = []
    body = client.post("/detect_faces", json={"image": image_b64}, headers=auth).get_json()
    assert body["status"] == "NO_FACE"
    assert body["face_count"] == 0


# --- recognition -------------------------------------------------------------------------


def test_recognize_matches_an_enrolled_profile(client, auth, image_b64, vision, enrolled):
    vision.vectors = [enrolled["found"]]

    body = client.post(
        "/recognize", json={"image": image_b64, "kind": "FOUND"}, headers=auth
    ).get_json()

    assert body["ok"] is True
    assert body["face_count"] == 1
    result = body["results"][0]
    assert result["matched"] is True
    assert result["best_match"]["person_id"] == "found-1"
    assert result["best_match"]["distance"] == pytest.approx(0.0, abs=1e-6)
    assert result["best_match"]["confidence"] == "HIGH"


def test_recognize_returns_ranked_candidates(client, auth, image_b64, vision, mongo, monkeypatch):
    """Several profiles inside the threshold come back closest-first, not just the winner."""
    monkeypatch.setattr(config, "MATCH_THRESHOLD", 1.0)

    base = np.zeros(128)
    base[0] = 1.0
    for index, tilt in enumerate((0.0, 0.3, 0.6)):
        vector = base.copy()
        vector[1] = tilt
        repository.save_profile(
            person_id=f"cand-{index}",
            embedding=vector.tolist(),
            kind="FOUND",
            subject_type="PERSON",
            model="Facenet",
            detector="retinaface",
            sample_count=1,
        )

    vision.vectors = [base]
    body = client.post("/recognize", json={"image": image_b64}, headers=auth).get_json()

    candidates = body["results"][0]["candidates"]
    assert [c["person_id"] for c in candidates] == ["cand-0", "cand-1", "cand-2"]
    assert candidates == sorted(candidates, key=lambda c: c["distance"])


def test_recognize_respects_max_results(client, auth, image_b64, vision, mongo, monkeypatch):
    monkeypatch.setattr(config, "MATCH_THRESHOLD", 1.0)
    monkeypatch.setattr(config, "MAX_MATCH_RESULTS", 5)

    base = np.zeros(128)
    base[0] = 1.0
    for index in range(5):
        vector = base.copy()
        vector[1] = index * 0.1
        repository.save_profile(
            person_id=f"m-{index}",
            embedding=vector.tolist(),
            kind="FOUND",
            subject_type="PERSON",
            model="Facenet",
            detector="retinaface",
            sample_count=1,
        )

    vision.vectors = [base]
    body = client.post(
        "/recognize", json={"image": image_b64, "max_results": 2}, headers=auth
    ).get_json()

    assert len(body["results"][0]["candidates"]) == 2


def test_recognize_never_forces_a_weak_match(client, auth, image_b64, vision, enrolled):
    """Nothing inside the threshold means no match — not the nearest thing available."""
    orthogonal = np.zeros(128)
    orthogonal[5] = 1.0
    vision.vectors = [orthogonal]

    body = client.post("/recognize", json={"image": image_b64}, headers=auth).get_json()
    result = body["results"][0]

    assert result["matched"] is False
    assert result["best_match"] is None
    assert result["candidates"] == []
    assert result["reason"] == "NO_MATCH_WITHIN_THRESHOLD"
    # Reported for diagnosis, and unmistakably not a match.
    assert result["nearest_distance"] > config.MATCH_THRESHOLD


def test_recognize_handles_multiple_faces_independently(
    client, auth, image_b64, vision, make_face, enrolled
):
    vision.faces = [make_face(10, 10), make_face(120, 120)]

    unmatched = np.zeros(128)
    unmatched[7] = 1.0
    vision.vectors = [enrolled["found"], unmatched]

    body = client.post("/recognize", json={"image": image_b64}, headers=auth).get_json()

    assert body["face_count"] == 2
    assert body["results"][0]["matched"] is True
    assert body["results"][1]["matched"] is False


def test_recognize_resolves_duplicate_matches(
    client, auth, image_b64, vision, make_face, mongo, monkeypatch
):
    """One enrolled person cannot be the answer for two faces in the same photograph."""
    monkeypatch.setattr(config, "MATCH_THRESHOLD", 1.0)

    target = np.zeros(128)
    target[0] = 1.0
    repository.save_profile(
        person_id="only-one",
        embedding=target.tolist(),
        kind="FOUND",
        subject_type="PERSON",
        model="Facenet",
        detector="retinaface",
        sample_count=1,
    )

    # The second face is a slightly worse version of the first, so both rank the same
    # profile first and the closer one must win it.
    near = target.copy()
    near[1] = 0.35
    vision.faces = [make_face(10, 10), make_face(120, 120)]
    vision.vectors = [target, near]

    body = client.post("/recognize", json={"image": image_b64}, headers=auth).get_json()

    assert body["results"][0]["best_match"]["person_id"] == "only-one"
    assert body["results"][1]["matched"] is False

    assert body["duplicates"] == [
        {"person_id": "only-one", "face_indexes": [0, 1], "assigned_to": 0}
    ]


def test_recognize_threshold_is_configurable_per_request(client, auth, image_b64, vision, enrolled):
    loose = np.zeros(128)
    loose[0] = 1.0
    loose[1] = 0.9
    vision.vectors = [loose]

    strict = client.post(
        "/recognize", json={"image": image_b64, "threshold": 0.01}, headers=auth
    ).get_json()
    relaxed = client.post(
        "/recognize", json={"image": image_b64, "threshold": 0.9}, headers=auth
    ).get_json()

    assert strict["results"][0]["matched"] is False
    assert relaxed["results"][0]["matched"] is True
    assert relaxed["threshold"] == 0.9


def test_recognize_filters_by_side(client, auth, image_b64, vision, enrolled):
    """Searching the FOUND side must not return a LOST profile."""
    vision.vectors = [enrolled["lost"]]

    body = client.post(
        "/recognize", json={"image": image_b64, "kind": "FOUND"}, headers=auth
    ).get_json()

    assert body["results"][0]["matched"] is False


def test_recognize_can_exclude_ids(client, auth, image_b64, vision, enrolled):
    vision.vectors = [enrolled["found"]]

    body = client.post(
        "/recognize",
        json={"image": image_b64, "exclude_person_ids": ["found-1"]},
        headers=auth,
    ).get_json()

    assert body["results"][0]["matched"] is False


def test_recognize_with_no_enrolled_profiles(client, auth, image_b64, vision, mongo):
    body = client.post("/recognize", json={"image": image_b64}, headers=auth).get_json()

    assert body["ok"] is True
    assert body["enrolled_compared"] == 0
    assert body["results"][0]["matched"] is False
    assert body["results"][0]["nearest_distance"] is None


def test_recognize_with_no_face(client, auth, image_b64, vision, mongo):
    vision.faces = []
    body = client.post("/recognize", json={"image": image_b64}, headers=auth).get_json()

    assert body["ok"] is False
    assert body["status"] == "NO_FACE"
    assert body["results"] == []


def test_recognize_with_invalid_image(client, auth, not_an_image_b64, vision, mongo):
    body = client.post("/recognize", json={"image": not_an_image_b64}, headers=auth).get_json()
    assert body["status"] == "INVALID_IMAGE"


# --- compare (existing consumer) ---------------------------------------------------------


def test_compare_ranks_the_opposite_side(client, auth, enrolled):
    body = client.post("/compare", json={"report_client_id": "lost-1"}, headers=auth).get_json()

    assert body["ok"] is True
    assert body["face_available"] is True
    assert "found-1" in body["distances"]
    # A LOST record is never compared against another LOST record.
    assert "lost-1" not in body["distances"]


def test_compare_reports_unavailable_for_a_record_with_no_face(client, auth, mongo):
    repository.mark_status("no-photo", "NO_FACE", kind="LOST")

    body = client.post("/compare", json={"person_id": "no-photo"}, headers=auth).get_json()

    assert body["ok"] is True
    assert body["face_available"] is False
    assert body["distances"] == {}


def test_compare_on_an_unknown_record_is_404(client, auth, mongo):
    response = client.post("/compare", json={"person_id": "nobody"}, headers=auth)
    assert response.status_code == 404


def test_compare_keeps_the_legacy_tolerance_field(client, auth, enrolled):
    """The deployed edge function reads `tolerance`; renaming it would break it silently."""
    body = client.post("/compare", json={"person_id": "lost-1"}, headers=auth).get_json()
    assert body["tolerance"] == body["threshold"]


# --- profile administration --------------------------------------------------------------


def test_list_persons_excludes_embeddings(client, auth, enrolled):
    body = client.get("/persons", headers=auth).get_json()

    assert body["total"] == 2
    for person in body["persons"]:
        assert "embedding" not in person


def test_list_persons_filters_by_side(client, auth, enrolled):
    body = client.get("/persons?kind=FOUND", headers=auth).get_json()
    assert [p["person_id"] for p in body["persons"]] == ["found-1"]


def test_list_persons_rejects_a_non_numeric_limit(client, auth, mongo):
    assert client.get("/persons?limit=all", headers=auth).status_code == 400


def test_get_person_returns_metadata_not_vectors(client, auth, enrolled):
    body = client.get("/persons/lost-1", headers=auth).get_json()

    assert body["person"]["person_id"] == "lost-1"
    assert body["person"]["metadata"] == {"label": "child in blue"}
    assert "embedding" not in body["person"]


def test_delete_person_removes_the_profile(client, auth, enrolled, mongo):
    response = client.delete("/persons/lost-1", headers=auth)

    assert response.status_code == 200
    assert response.get_json()["deleted"] is True
    assert mongo.find_one({"person_id": "lost-1"}) is None


def test_delete_unknown_person_is_404(client, auth, mongo):
    assert client.delete("/persons/nobody", headers=auth).status_code == 404


# --- retired routes ----------------------------------------------------------------------


@pytest.mark.parametrize("path", ["/students", "/students/12345"])
def test_student_routes_are_gone_not_missing(client, auth, path):
    """410 tells an old attendance client what happened; 404 would leave it guessing."""
    response = client.get(path, headers=auth)

    assert response.status_code == 410
    assert "/persons" in response.get_json()["message"]


def test_no_prn_or_attendance_vocabulary_survives(client, auth, enrolled, image_b64, vision):
    """The student domain is gone from the wire format, not merely unused in code."""
    payloads = [
        client.get("/persons", headers=auth).get_data(as_text=True),
        client.get("/persons/lost-1", headers=auth).get_data(as_text=True),
        client.post("/compare", json={"person_id": "lost-1"}, headers=auth).get_data(as_text=True),
    ]

    for body in payloads:
        lowered = body.lower()
        for term in ("prn", "gr_no", "branch", "division", "class_code", "attendance", "student"):
            assert term not in lowered


# --- MongoDB failure ---------------------------------------------------------------------


def test_enroll_during_an_outage_is_retryable(client, auth, image_b64, vision, broken_mongo):
    """503, not a bad-photo status: the picture may be perfect and simply unrecorded."""
    response = client.post(
        "/enroll", json={"person_id": "x", "image": image_b64}, headers=auth
    )

    assert response.status_code == 503
    assert response.get_json()["status"] == "SERVICE_UNAVAILABLE"


def test_recognize_during_an_outage(client, auth, image_b64, vision, broken_mongo):
    response = client.post("/recognize", json={"image": image_b64}, headers=auth)
    assert response.status_code == 503


def test_compare_during_an_outage(client, auth, broken_mongo):
    response = client.post("/compare", json={"person_id": "lost-1"}, headers=auth)
    assert response.status_code == 503
    assert response.get_json()["face_available"] is False


def test_listing_during_an_outage(client, auth, broken_mongo):
    assert client.get("/persons", headers=auth).status_code == 503


def test_delete_during_an_outage(client, auth, broken_mongo):
    assert client.delete("/persons/x", headers=auth).status_code == 503


def test_detection_still_works_without_a_database(client, auth, image_b64, vision, broken_mongo):
    """Detection touches no storage, so an outage must not take it down with everything else."""
    response = client.post("/detect_faces", json={"image": image_b64}, headers=auth)
    assert response.status_code == 200


def test_outage_response_is_identical_whatever_failed(client, auth, broken_mongo):
    """One message for every storage failure: the shape of the infrastructure is not public."""
    listing = client.get("/persons", headers=auth).get_json()
    deletion = client.delete("/persons/x", headers=auth).get_json()

    assert listing["message"] == deletion["message"]
    assert "mongo" not in listing["message"].lower()


# --- vision failure ----------------------------------------------------------------------


def test_model_failure_during_enrolment_is_an_outage(client, auth, image_b64, vision, mongo, monkeypatch):
    """A model that cannot load is retryable, and must not be blamed on the photograph."""

    def explode(*_args, **_kwargs):
        raise RuntimeError("weights file is corrupt")

    monkeypatch.setattr(face_engine, "embed_reference_images", explode)

    response = client.post(
        "/enroll", json={"person_id": "x", "image": image_b64}, headers=auth
    )

    assert response.status_code == 503
    assert response.get_json()["status"] == "SERVICE_UNAVAILABLE"
    assert mongo.find_one({"person_id": "x"}) is None


def test_a_face_that_will_not_embed_is_skipped_not_fatal(
    client, auth, image_b64, vision, make_face, enrolled
):
    """One unreadable face in a group must not cost the others their match."""
    vision.faces = [make_face(10, 10), make_face(120, 120)]
    vision.vectors = [None, enrolled["found"]]

    body = client.post("/recognize", json={"image": image_b64}, headers=auth).get_json()

    assert body["face_count"] == 1
    assert body["results"][0]["matched"] is True


# --- malformed requests ------------------------------------------------------------------


def test_unknown_route_is_json_not_html(client):
    response = client.get("/nope")
    assert response.status_code == 404
    assert response.get_json()["ok"] is False


def test_wrong_method_is_json_not_html(client, auth):
    response = client.get("/recognize", headers=auth)
    assert response.status_code == 405
    assert response.get_json()["ok"] is False


def test_body_that_is_not_json_is_a_clean_400(client, auth):
    response = client.post(
        "/enroll", data="not json", content_type="application/json", headers=auth
    )
    assert response.status_code == 400
    assert response.get_json()["ok"] is False
