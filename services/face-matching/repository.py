"""Persistence for face embeddings, against the project's existing database.

Plan 07 section 7.21E is explicit: no new database, no parallel Lost & Found store, no
MongoDB. The reference implementation's document store is dropped entirely; embeddings
live in `public.lost_found_face_data`, keyed by the `client_id` of the report that already
exists in `public.lost_found_items`.

That table grants nothing to `authenticated` or `anon` — this service connects with the
service role, and it is the only thing that ever reads a vector.
"""

from __future__ import annotations

import logging
import os
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Iterator, Optional, Sequence

import psycopg
from psycopg.rows import dict_row

logger = logging.getLogger(__name__)


class DatabaseUnavailable(RuntimeError):
    """The database could not be reached.

    Distinct from "no result" on purpose. An outage must surface to the volunteer as
    "saved locally, will sync" rather than as a face-matching failure, because the two
    lead to completely different next actions.
    """


@dataclass(frozen=True)
class CandidateEmbedding:
    report_client_id: str
    embedding: list[float]


def _dsn() -> str:
    dsn = os.environ.get("DATABASE_URL")
    if not dsn:
        raise DatabaseUnavailable("DATABASE_URL is not configured")
    return dsn


@contextmanager
def connection() -> Iterator[psycopg.Connection]:
    """A short-lived connection.

    Opened per request rather than pooled: this service is called a handful of times a
    minute at most, and a pool would be one more thing to get wrong for no measurable
    benefit at that rate.
    """
    try:
        with psycopg.connect(_dsn(), row_factory=dict_row, connect_timeout=5) as conn:
            yield conn
    except psycopg.Error as error:
        # Logged with detail server-side; the caller gets a generic outage.
        logger.error("Database error: %s", error)
        raise DatabaseUnavailable("database unavailable") from error


def save_embedding(
    report_client_id: str,
    embedding: Sequence[float],
    detector: str,
    model: str,
    sample_count: int,
) -> None:
    """Store or replace a report's embedding, and mark the report face-matchable.

    One transaction: an embedding without a READY status would never be compared, and a
    READY status without an embedding would crash every comparison that trusted it.
    """
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            """
            insert into public.lost_found_face_data
                (report_client_id, embedding, model, detector, sample_count, processed_at)
            values (%s, %s, %s, %s, %s, now())
            on conflict (report_client_id) do update
               set embedding = excluded.embedding,
                   model = excluded.model,
                   detector = excluded.detector,
                   sample_count = excluded.sample_count,
                   processed_at = now()
            """,
            (report_client_id, list(embedding), model, detector, sample_count),
        )
        cur.execute(
            "update public.lost_found_items set face_match_status = 'READY' "
            "where client_id = %s",
            (report_client_id,),
        )
        conn.commit()


def mark_status(report_client_id: str, status: str) -> None:
    """Record a non-READY outcome.

    Any stored embedding is removed at the same time: a report whose photo has been
    replaced with an unusable one must not keep matching on the old picture.
    """
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            "update public.lost_found_items set face_match_status = %s where client_id = %s",
            (status, report_client_id),
        )
        cur.execute(
            "delete from public.lost_found_face_data where report_client_id = %s",
            (report_client_id,),
        )
        conn.commit()


def load_opposite_side_embeddings(report_client_id: str) -> list[CandidateEmbedding]:
    """Every active embedding on the other side of the separation.

    The join does the filtering the matching rules require: opposite `kind`, same
    `subject_type`, still active, and never the report itself. Pairs a human has already
    ruled on are excluded so a rejected candidate is not re-proposed on the next run.
    """
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            """
            with subject as (
                select client_id, kind, subject_type
                  from public.lost_found_items
                 where client_id = %s
            )
            select f.report_client_id, f.embedding
              from public.lost_found_face_data f
              join public.lost_found_items i on i.client_id = f.report_client_id
              cross join subject s
             where i.kind <> s.kind
               and i.subject_type = s.subject_type
               and i.status in ('OPEN', 'MATCHED')
               and i.face_match_status = 'READY'
               and i.client_id <> s.client_id
               and not exists (
                   select 1 from public.lost_found_matches m
                    where m.status in ('CONFIRMED', 'REJECTED')
                      and (
                          (m.lost_report_client_id = s.client_id
                           and m.found_report_client_id = i.client_id)
                       or (m.found_report_client_id = s.client_id
                           and m.lost_report_client_id = i.client_id)
                      )
               )
            """,
            (report_client_id,),
        )
        return [
            CandidateEmbedding(
                report_client_id=row["report_client_id"],
                embedding=list(row["embedding"]),
            )
            for row in cur.fetchall()
        ]


def get_embedding(report_client_id: str) -> Optional[list[float]]:
    """One report's own vector, for use as the comparison subject.

    The only function that returns an embedding, and its result never leaves the process:
    `app.py` uses it to compute distances and discards it.
    """
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            "select embedding from public.lost_found_face_data where report_client_id = %s",
            (report_client_id,),
        )
        row = cur.fetchone()
        return list(row["embedding"]) if row else None


def get_report(report_client_id: str) -> Optional[dict]:
    """The report's non-sensitive fields, for the caller's own scoring.

    Deliberately does not select the embedding: nothing outside this module needs a
    vector, and not selecting one is a stronger guarantee than remembering not to return
    it.
    """
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            """
            select client_id, kind, subject_type, status, face_match_status,
                   person_name, approximate_age, gender, clothing_description,
                   physical_description, language, route_sequence, occurred_at,
                   last_known_latitude, last_known_longitude, reported_by
              from public.lost_found_items
             where client_id = %s
            """,
            (report_client_id,),
        )
        return cur.fetchone()
