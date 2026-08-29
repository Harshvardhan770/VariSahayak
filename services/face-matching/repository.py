"""MongoDB persistence for Lost & Found face profiles.

This service stores its own records and nothing else does. There is no PostgreSQL
connection, no Supabase client, and no service-role database key anywhere in the process —
the only credential it holds is a Mongo URI, and the only thing it can reach with it is the
face collection.

One document per person profile:

    {
      "person_id":     "<opaque id supplied by the caller>",   # unique
      "kind":          "LOST" | "FOUND",
      "subject_type":  "PERSON" | "ITEM",
      "status":        "READY" | "NO_FACE" | ...,
      "embedding":     [float, ...],     # absent unless status is READY
      "model":         "Facenet",
      "detector":      "retinaface",
      "sample_count":  9,
      "metadata":      { ... },          # caller-supplied, never interpreted here
      "created_at":    datetime,
      "updated_at":    datetime
    }

``person_id`` is opaque on purpose. This module never learns whether it is a Lost & Found
report id or something else, which is what keeps the service reusable and keeps identifying
information out of a store that holds biometric vectors.
"""

from __future__ import annotations

import logging
import os
import threading
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Iterable, Optional, Sequence

from pymongo import ASCENDING, MongoClient
from pymongo.collection import Collection
from pymongo.errors import DuplicateKeyError, PyMongoError

import config

logger = logging.getLogger(__name__)

#: Never selected on a read that leaves this module, with one exception — the comparison
#: loader, which needs vectors and returns them only into the ranking code.
_PUBLIC_PROJECTION = {
    "_id": 0,
    "person_id": 1,
    "kind": 1,
    "subject_type": 1,
    "status": 1,
    "model": 1,
    "detector": 1,
    "sample_count": 1,
    "metadata": 1,
    "created_at": 1,
    "updated_at": 1,
}


class DatabaseUnavailable(RuntimeError):
    """MongoDB could not be reached.

    Distinct from "no result" on purpose. An outage must surface to the volunteer as
    "saved locally, will sync" rather than as a face-matching failure, because the two lead
    to completely different next actions.
    """


@dataclass(frozen=True)
class StoredProfile:
    person_id: str
    embedding: list[float]
    metadata: dict


# --- connection --------------------------------------------------------------------------
#
# One client per process, created on first use and reused for the life of the worker.
# MongoClient is internally pooled and thread-safe, so this is the shape PyMongo is designed
# for; opening a client per request would spend a TLS handshake on every photograph.
#
# Created lazily rather than at import, which is what makes it safe under a forking WSGI
# server. A client constructed in the parent and inherited across fork() shares sockets
# between workers and corrupts them — so gunicorn must not preload this app, and with lazy
# construction each worker simply builds its own on the first request it serves.

_client: Optional[MongoClient] = None
_client_pid: Optional[int] = None
_lock = threading.Lock()


def _new_client() -> MongoClient:
    if not config.MONGODB_URI:
        raise DatabaseUnavailable("MONGODB_URI is not configured")

    return MongoClient(
        config.MONGODB_URI,
        serverSelectionTimeoutMS=config.MONGODB_SERVER_SELECTION_TIMEOUT_MS,
        connectTimeoutMS=config.MONGODB_CONNECT_TIMEOUT_MS,
        socketTimeoutMS=config.MONGODB_SOCKET_TIMEOUT_MS,
        maxPoolSize=config.MONGODB_MAX_POOL_SIZE,
        minPoolSize=config.MONGODB_MIN_POOL_SIZE,
        appname=config.SERVICE_NAME,
        retryWrites=True,
        tz_aware=True,
    )


def get_client() -> MongoClient:
    """The process-wide client, built on first use.

    The PID check is the fork guard: if this process was forked after a client existed,
    the inherited one is discarded rather than used, because its sockets belong to the
    parent.
    """
    global _client, _client_pid

    pid = os.getpid()
    if _client is not None and _client_pid == pid:
        return _client

    with _lock:
        if _client is not None and _client_pid == pid:
            return _client
        _client = _new_client()
        _client_pid = pid
        return _client


def get_collection() -> Collection:
    try:
        return get_client()[config.MONGODB_DB][config.MONGODB_COLLECTION]
    except DatabaseUnavailable:
        raise
    except PyMongoError as error:
        logger.error("Could not obtain the face collection: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error


def ensure_indexes() -> None:
    """Create the indexes the service depends on. Safe to call repeatedly.

    Best-effort at start-up: a replica that has not finished electing a primary must not
    stop the container from coming up, and the next call will create them.
    """
    try:
        collection = get_collection()
        collection.create_index([("person_id", ASCENDING)], unique=True, name="person_id_unique")
        collection.create_index(
            [("kind", ASCENDING), ("subject_type", ASCENDING), ("status", ASCENDING)],
            name="side_and_status",
        )
        collection.create_index([("updated_at", ASCENDING)], name="updated_at")
    except (DatabaseUnavailable, PyMongoError) as error:
        logger.warning("Index creation deferred: %s", type(error).__name__)


def ping() -> bool:
    """Whether the database answers. Never raises — callers want a boolean, not a branch."""
    try:
        get_client().admin.command("ping")
        return True
    except (DatabaseUnavailable, PyMongoError):
        return False


def close_client() -> None:
    """Release the client. Used by tests and by an orderly container shutdown."""
    global _client, _client_pid
    with _lock:
        if _client is not None:
            try:
                _client.close()
            except PyMongoError:
                pass
        _client = None
        _client_pid = None


# --- writes ------------------------------------------------------------------------------


def save_profile(
    person_id: str,
    embedding: Sequence[float],
    kind: str,
    subject_type: str,
    model: str,
    detector: str,
    sample_count: int,
    metadata: Optional[dict] = None,
) -> None:
    """Store or replace a person's averaged embedding and mark the profile matchable.

    An upsert on ``person_id``: re-enrolling with a better photograph must replace the
    profile, not create a second one that keeps matching on the old picture.
    """
    now = datetime.now(timezone.utc)
    try:
        get_collection().update_one(
            {"person_id": person_id},
            {
                "$set": {
                    "kind": kind,
                    "subject_type": subject_type,
                    "status": "READY",
                    "embedding": [float(value) for value in embedding],
                    "model": model,
                    "detector": detector,
                    "sample_count": int(sample_count),
                    "metadata": metadata or {},
                    "updated_at": now,
                },
                "$setOnInsert": {"person_id": person_id, "created_at": now},
            },
            upsert=True,
        )
    except DuplicateKeyError as error:
        # Two concurrent enrolments of the same id. The other one won and wrote an
        # equivalent profile, so this is a no-op rather than a failure.
        logger.info("Concurrent enrolment for the same person_id; keeping the stored profile")
        _ = error
    except PyMongoError as error:
        logger.error("Profile write failed: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error


def mark_status(
    person_id: str,
    status: str,
    kind: str = "LOST",
    subject_type: str = "PERSON",
    metadata: Optional[dict] = None,
) -> None:
    """Record a non-READY outcome and drop any stored vector.

    The vector is unset, not left in place: a record whose photograph has been replaced
    with an unusable one must stop matching on the old picture immediately.
    """
    now = datetime.now(timezone.utc)
    try:
        get_collection().update_one(
            {"person_id": person_id},
            {
                "$set": {
                    "kind": kind,
                    "subject_type": subject_type,
                    "status": status,
                    "metadata": metadata or {},
                    "updated_at": now,
                },
                "$setOnInsert": {"person_id": person_id, "created_at": now},
                "$unset": {"embedding": "", "sample_count": ""},
            },
            upsert=True,
        )
    except PyMongoError as error:
        logger.error("Status write failed: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error


def delete_profile(person_id: str) -> bool:
    """Remove a profile entirely. Returns whether one existed."""
    try:
        result = get_collection().delete_one({"person_id": person_id})
        return result.deleted_count > 0
    except PyMongoError as error:
        logger.error("Profile delete failed: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error


# --- reads -------------------------------------------------------------------------------


def get_profile(person_id: str) -> Optional[dict]:
    """A profile's non-sensitive fields.

    Projected rather than filtered after the fact: not selecting the embedding is a
    stronger guarantee than remembering to strip it.
    """
    try:
        document = get_collection().find_one({"person_id": person_id}, _PUBLIC_PROJECTION)
    except PyMongoError as error:
        logger.error("Profile read failed: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error

    return serialise_profile(document) if document else None


def list_profiles(
    kind: Optional[str] = None,
    subject_type: Optional[str] = None,
    status: Optional[str] = None,
    limit: int = 100,
    skip: int = 0,
) -> list[dict]:
    """Enrolled profiles, never including embeddings."""
    query: dict[str, Any] = {}
    if kind:
        query["kind"] = kind
    if subject_type:
        query["subject_type"] = subject_type
    if status:
        query["status"] = status

    try:
        cursor = (
            get_collection()
            .find(query, _PUBLIC_PROJECTION)
            .sort("updated_at", -1)
            .skip(max(0, skip))
            .limit(max(1, min(limit, 500)))
        )
        return [serialise_profile(document) for document in cursor]
    except PyMongoError as error:
        logger.error("Profile list failed: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error


def count_profiles(
    kind: Optional[str] = None,
    subject_type: Optional[str] = None,
    status: Optional[str] = None,
) -> int:
    query: dict[str, Any] = {}
    if kind:
        query["kind"] = kind
    if subject_type:
        query["subject_type"] = subject_type
    if status:
        query["status"] = status

    try:
        return get_collection().count_documents(query)
    except PyMongoError as error:
        logger.error("Profile count failed: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error


def get_embedding(person_id: str) -> Optional[list[float]]:
    """One profile's own vector, for use as a comparison subject.

    One of the two functions that returns an embedding. Its result never leaves the
    process: the HTTP layer computes distances from it and discards it.
    """
    try:
        document = get_collection().find_one(
            {"person_id": person_id, "status": "READY"}, {"_id": 0, "embedding": 1}
        )
    except PyMongoError as error:
        logger.error("Embedding read failed: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error

    if not document or not document.get("embedding"):
        return None
    return [float(value) for value in document["embedding"]]


def load_profiles_for_matching(
    kind: Optional[str] = None,
    subject_type: Optional[str] = None,
    exclude_person_ids: Optional[Iterable[str]] = None,
) -> list[StoredProfile]:
    """Every READY profile that a query may be compared against.

    ``kind`` filters to one side of the board. Callers matching a LOST report pass
    ``kind="FOUND"``, because a lost child is never matched against another lost child —
    but the filter is optional, since a caller with no sides (recognising a face against
    everybody enrolled) is an equally valid use of this service.
    """
    query: dict[str, Any] = {"status": "READY", "embedding": {"$exists": True}}
    if kind:
        query["kind"] = kind
    if subject_type:
        query["subject_type"] = subject_type

    excluded = list(exclude_person_ids or [])
    if excluded:
        query["person_id"] = {"$nin": excluded}

    try:
        cursor = get_collection().find(
            query, {"_id": 0, "person_id": 1, "embedding": 1, "metadata": 1}
        )
        return [
            StoredProfile(
                person_id=document["person_id"],
                embedding=[float(v) for v in document.get("embedding", [])],
                metadata=document.get("metadata") or {},
            )
            for document in cursor
            if document.get("embedding")
        ]
    except PyMongoError as error:
        logger.error("Candidate load failed: %s", type(error).__name__)
        raise DatabaseUnavailable("database unavailable") from error


def opposite_side(kind: Optional[str]) -> Optional[str]:
    """The side a record should be compared against, or None when it has no side."""
    if kind == "LOST":
        return "FOUND"
    if kind == "FOUND":
        return "LOST"
    return None


def serialise_profile(document: dict) -> dict:
    """Make a document JSON-safe. Datetimes become ISO-8601; nothing else is touched."""
    out = dict(document)
    for key in ("created_at", "updated_at"):
        value = out.get(key)
        if isinstance(value, datetime):
            out[key] = value.isoformat()
    return out
