package com.varisahayak.domain.repository

import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.LostFoundItem
import com.varisahayak.domain.model.LostFoundKind
import kotlinx.coroutines.flow.Flow

/**
 * Lost & Found reports.
 *
 * Reads come from the local database so search works with no connectivity, and writes go
 * through the same offline-first path as incidents — a report made in a dead spot is not
 * lost, it is queued.
 */
interface LostFoundRepository {

    fun observeAll(): Flow<List<LostFoundItem>>

    /** Local search. Works offline; matches on title and description. */
    fun search(query: String): Flow<List<LostFoundItem>>

    /**
     * Files a report. A [LostFoundKind.PERSON] report also raises a LOST_PERSON incident
     * so it reaches the normal matching and notification pipeline.
     */
    suspend fun report(
        kind: LostFoundKind,
        title: String,
        description: String,
        lastSeenLocation: GeoPoint?,
        qrToken: String?,
        photoLocalPath: String?,
    ): Outcome<LostFoundItem>

    suspend fun syncPending(): Outcome<Unit>
}
