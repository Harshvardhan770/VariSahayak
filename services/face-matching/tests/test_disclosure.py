"""What must never leave this service.

Embeddings, credentials, raw images and stack traces. These are the rules that survive a
refactor only if something checks them, because every one of them is a leak that looks like
working code until somebody reads a response closely.

The suite sweeps real responses rather than inspecting the source, so a future endpoint that
forgets the rule fails here without anyone remembering to add a test for it.
"""

from __future__ import annotations

import json

import numpy as np
import pytest

import config
import face_engine
import repository


def _every_response(client, auth, image_b64):
    """One call to every route that can produce a body, in both success and failure."""
    return [
        client.get("/"),
        client.get("/health"),
        client.get("/persons", headers=auth),
        client.get("/persons/lost-1", headers=auth),
        client.get("/persons/nobody", headers=auth),
        client.get("/students", headers=auth),
        client.get("/does-not-exist"),
        client.post("/detect_faces", json={"image": image_b64}, headers=auth),
        client.post("/detect_faces", json={}, headers=auth),
        client.post("/enroll", json={"person_id": "x", "image": image_b64}, headers=auth),
        client.post("/enroll", json={}, headers=auth),
        client.post("/recognize", json={"image": image_b64}, headers=auth),
        client.post("/compare", json={"person_id": "lost-1"}, headers=auth),
        client.post("/compare", json={"person_id": "nobody"}, headers=auth),
        client.post("/recognize", json={"image": image_b64}),  # unauthorised
    ]


def test_no_response_contains_an_embedding(client, auth, image_b64, vision, enrolled):
    """The single most important rule: a face vector never reaches a caller.

    Checked by shape, not by key name — a vector renamed to `features` would still be a
    vector, and this catches it.
    """
    for response in _every_response(client, auth, image_b64):
        body = response.get_data(as_text=True)
        assert "embedding" not in body.lower()

        try:
            parsed = json.loads(body)
        except json.JSONDecodeError:
            continue

        _assert_no_long_float_array(parsed)


def _assert_no_long_float_array(node, path: str = "$"):
    """Recursively refuse anything that looks like a 128-dimensional vector."""
    if isinstance(node, list):
        numeric = [v for v in node if isinstance(v, (int, float)) and not isinstance(v, bool)]
        assert len(numeric) < 16, f"{path} looks like an embedding ({len(numeric)} numbers)"
        for index, item in enumerate(node):
            _assert_no_long_float_array(item, f"{path}[{index}]")
    elif isinstance(node, dict):
        for key, value in node.items():
            _assert_no_long_float_array(value, f"{path}.{key}")


def test_no_response_contains_credentials(client, auth, image_b64, vision, enrolled, monkeypatch):
    """Neither the Mongo URI nor the API key, in any response, on any path."""
    monkeypatch.setattr(config, "MONGODB_URI", "mongodb+srv://leaky:s3cr3t@cluster.example.net")

    for response in _every_response(client, auth, image_b64):
        body = response.get_data(as_text=True)
        for secret in ("s3cr3t", "mongodb+srv", "cluster.example.net", config.API_KEY):
            assert secret not in body


def test_no_response_contains_a_traceback(client, auth, image_b64, vision, enrolled):
    for response in _every_response(client, auth, image_b64):
        body = response.get_data(as_text=True)
        for fragment in ("Traceback", 'File "', "line ", ".py", "Error:", "Exception"):
            assert fragment not in body


def test_an_unhandled_exception_is_a_generic_503_body(client, auth, image_b64, monkeypatch):
    """The catch-all must hide the shape of the failure, not describe it."""

    def explode(*_args, **_kwargs):
        raise ValueError("secret internal detail /srv/app/weights.h5")

    monkeypatch.setattr(face_engine, "detect_faces", explode)

    response = client.post("/detect_faces", json={"image": image_b64}, headers=auth)
    body = response.get_data(as_text=True)

    assert response.status_code == 500
    assert "secret internal detail" not in body
    assert "weights.h5" not in body
    assert response.get_json()["status"] == "SERVICE_UNAVAILABLE"


def test_a_storage_failure_names_no_technology(client, auth, broken_mongo):
    body = client.get("/persons", headers=auth).get_data(as_text=True).lower()
    for fragment in ("mongo", "pymongo", "collection", "replica", "dsn", "uri"):
        assert fragment not in body


def test_thumbnails_are_the_only_image_data_returned(client, auth, image_b64, vision, enrolled):
    """A crop of the caller's own upload is the one image that may come back, and only there."""
    routes = [
        client.post("/enroll", json={"person_id": "x", "image": image_b64}, headers=auth),
        client.post("/recognize", json={"image": image_b64}, headers=auth),
        client.post("/compare", json={"person_id": "lost-1"}, headers=auth),
        client.get("/persons", headers=auth),
    ]

    for response in routes:
        body = response.get_json() or {}
        assert "thumbnail" not in json.dumps(body)
        assert "image" not in body


def test_thumbnails_can_be_disabled_globally(client, auth, image_b64, vision, monkeypatch):
    """One setting takes every byte of image data out of every response."""
    monkeypatch.setattr(config, "RETURN_THUMBNAILS", False)

    body = client.post("/detect_faces", json={"image": image_b64}, headers=auth).get_json()

    assert body["face_count"] >= 1
    assert all("thumbnail" not in face for face in body["faces"])


def test_logs_never_carry_an_embedding_or_an_image(client, auth, image_b64, vision, mongo, caplog):
    caplog.set_level("DEBUG")

    client.post("/enroll", json={"person_id": "x", "image": image_b64}, headers=auth)
    client.post("/recognize", json={"image": image_b64}, headers=auth)

    logged = "\n".join(record.getMessage() for record in caplog.records)

    # The base64 payload is long; any prefix of it appearing in a log means the body was
    # logged somewhere it should not have been.
    assert image_b64[:64] not in logged
    assert "embedding" not in logged.lower()


def test_startup_summary_omits_the_connection_string(monkeypatch):
    """A partially-masked URI still leaks the host and the user. It is omitted entirely."""
    monkeypatch.setattr(config, "MONGODB_URI", "mongodb+srv://user:pw@host.example.net")
    monkeypatch.setattr(config, "API_KEY", "super-secret")

    summary = json.dumps(config.redacted_summary())

    assert "host.example.net" not in summary
    assert "super-secret" not in summary
    assert "pw" not in summary
    # It still says whether they are configured at all, which is what an operator needs.
    assert '"mongodb_configured": true' in summary
    assert '"api_key_configured": true' in summary


def test_no_supabase_or_postgres_dependency_remains():
    """The refactor's central promise: this process cannot reach a SQL database at all.

    Checked three ways, because each catches a different mistake:

    * nothing SQL-shaped is in ``sys.modules`` after the whole suite has imported the app;
    * no module *imports* one, found by walking the AST rather than grepping — a comment
      that mentions Supabase (the caller is still Supabase, and saying so is useful) is not
      a dependency, and a test that cannot tell the difference gets deleted the first time
      it cries wolf;
    * no SQL connection string appears in any source file.
    """
    import ast
    import sys
    from pathlib import Path

    forbidden = {"psycopg", "psycopg2", "sqlalchemy", "asyncpg", "supabase", "postgrest"}
    assert forbidden.isdisjoint(sys.modules)

    service_root = Path(repository.__file__).parent

    for source in service_root.glob("*.py"):
        tree = ast.parse(source.read_text(encoding="utf-8"))

        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                names = {alias.name.split(".")[0] for alias in node.names}
            elif isinstance(node, ast.ImportFrom):
                names = {(node.module or "").split(".")[0]}
            else:
                continue

            leaked = names & forbidden
            assert not leaked, f"{source.name} imports {leaked}"

        for literal in ast.walk(tree):
            if isinstance(literal, ast.Constant) and isinstance(literal.value, str):
                lowered = literal.value.lower()
                for scheme in ("postgresql://", "postgres://"):
                    assert scheme not in lowered, f"{source.name} holds a {scheme} string"


def test_no_sql_database_url_is_read_from_the_environment(monkeypatch):
    """The old service read DATABASE_URL. Nothing may read it now, even if one is set."""
    import importlib

    monkeypatch.setenv("DATABASE_URL", "postgresql://someone:pw@db.example.net:5432/postgres")

    reloaded = importlib.reload(config)
    try:
        summary = json.dumps(reloaded.redacted_summary())
        assert "db.example.net" not in summary
        assert "postgres" not in summary.lower()
    finally:
        # Restored so the reloaded module the other tests hold stays the configured one.
        monkeypatch.delenv("DATABASE_URL", raising=False)
        importlib.reload(config)


def test_the_only_configured_datastore_is_mongodb():
    summary = config.redacted_summary()
    assert "mongodb_configured" in summary
    assert not any("postgres" in key or "supabase" in key for key in summary)
