package com.varisahayak.domain.repository

import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.CustodyRecord
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.LostFoundKind
import com.varisahayak.domain.model.LostFoundMatch
import com.varisahayak.domain.model.LostFoundReport
import com.varisahayak.domain.model.LostFoundSubjectType
import com.varisahayak.domain.model.MatchStatus
import kotlinx.coroutines.flow.Flow

/**
 * Lost & Found: reports, custody, and candidate matches.
 *
 * Reads come from the local database so search and review work with no connectivity —
 * which is exactly when a volunteer on the route needs them. Writes go through the same
 * offline-first path as incidents: a report filed in a dead spot is queued, never lost,
 * and its photo is processed for face matching once the device reconnects.
 */
interface LostFoundRepository {

    fun observeAll(): Flow<List<LostFoundReport>>

    /** One side of the board — LOST or FOUND. */
    fun observeByKind(kind: LostFoundKind): Flow<List<LostFoundReport>>

    /** Everything still worth matching. Drives the live map. */
    fun observeActive(): Flow<List<LostFoundReport>>

    fun observeById(clientId: String): Flow<LostFoundReport?>

    /** Free-text search across every recorded attribute. Local, so it works offline. */
    fun search(query: String): Flow<List<LostFoundReport>>

    /** Structured search, for the filter panel in §7.24. */
    fun searchStructured(criteria: AttributeSearch): Flow<List<LostFoundReport>>

    fun observeUnsyncedCount(): Flow<Int>

    /**
     * Files a report.
     *
     * Everything except [title] and the two enums is optional, deliberately: a parent with
     * no photograph and half a description must still be able to file something the engine
     * can match on. A [LostFoundSubjectType.PERSON] report also raises a LOST_PERSON
     * incident so it reaches the normal priority, matching and notification pipeline.
     */
    suspend fun report(details: ReportDetails): Outcome<LostFoundReport>

    /** Corrects details on an existing report — including replacing the photo. */
    suspend fun update(
        clientId: String,
        mutate: (LostFoundReport) -> LostFoundReport,
    ): Outcome<LostFoundReport>

    suspend fun setStatus(clientId: String, status: com.varisahayak.domain.model.LostFoundStatus):
        Outcome<Unit>

    // --- custody -----------------------------------------------------------------------------

    fun observeCustodyChain(reportClientId: String): Flow<List<CustodyRecord>>

    /**
     * Records that a volunteer has taken responsibility for a found person.
     *
     * Closes the outgoing custodian and opens the incoming one atomically — a handover
     * that half-applies would leave a found child with two custodians or none.
     */
    suspend fun recordCustody(
        reportClientId: String,
        custodianUserId: String,
        custodianName: String?,
        helpPointName: String?,
        qrLocationToken: String?,
        location: GeoPoint?,
        handoverNote: String?,
    ): Outcome<CustodyRecord>

    // --- matching ----------------------------------------------------------------------------

    fun observeCandidateMatches(): Flow<List<LostFoundMatch>>

    fun observeMatchesForReport(reportClientId: String): Flow<List<LostFoundMatch>>

    fun observeMatchById(clientId: String): Flow<LostFoundMatch?>

    fun observeCandidateCount(): Flow<Int>

    /**
     * Runs the matching engine for one report against the active opposite side.
     *
     * Local and synchronous by design: a volunteer who has just filed a Found Person
     * report gets candidates immediately, with no network. Face similarity is folded in
     * only when the server has already produced embeddings for both sides.
     */
    suspend fun findCandidates(reportClientId: String): Outcome<List<LostFoundMatch>>

    /**
     * Records a human verdict. Nothing else may close a case.
     *
     * A rejection is kept rather than deleted — it is auditable, and it stops the engine
     * re-proposing the same pair on its next run.
     */
    suspend fun reviewMatch(
        matchClientId: String,
        verdict: MatchStatus,
        note: String?,
    ): Outcome<LostFoundMatch>

    suspend fun syncPending(): Outcome<Unit>

    suspend fun refreshFromServer(): Outcome<Unit>
}

/**
 * Everything a volunteer can capture when filing.
 *
 * A parameter object rather than a long argument list because most calls supply a handful
 * of these and a positional list of twenty nullable strings is a bug waiting to happen.
 */
data class ReportDetails(
    val kind: LostFoundKind,
    val subjectType: LostFoundSubjectType,
    val title: String,
    val description: String = "",
    val personName: String? = null,
    val approximateAge: Int? = null,
    val gender: String? = null,
    val approximateHeightCm: Int? = null,
    val clothingDescription: String? = null,
    val physicalDescription: String? = null,
    val language: String? = null,
    val condition: String? = null,
    val additionalNotes: String? = null,
    val guardianName: String? = null,
    val guardianPhone: String? = null,
    val qrLocationToken: String? = null,
    val qrLocationName: String? = null,
    val deviceLocation: GeoPoint? = null,
    val lastKnownLocation: GeoPoint? = null,
    val routeSegment: String? = null,
    val routeSequence: Int? = null,
    val occurredAtEpochMillis: Long? = null,
    val photoLocalPath: String? = null,
)

/** Filters for the manual candidate search in §7.24. All optional. */
data class AttributeSearch(
    val text: String? = null,
    val kind: LostFoundKind? = null,
    val subjectType: LostFoundSubjectType? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val gender: String? = null,
    val language: String? = null,
    val routeSequenceFrom: Int? = null,
    val routeSequenceTo: Int? = null,
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
    val status: com.varisahayak.domain.model.LostFoundStatus? = null,
    val onlyWithPhoto: Boolean = false,
)
