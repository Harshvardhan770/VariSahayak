package com.varisahayak.domain.repository

import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.Profile
import com.varisahayak.domain.model.Responder
import com.varisahayak.domain.model.ResponderAvailability
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts.
 *
 * These live in the domain layer so use cases depend on an interface, not on Room or
 * supabase-kt. Implementations sit in data/repository.
 */

/** Authentication state as the rest of the app understands it. */
sealed interface AuthState {
    /**
     * Session status is not yet known. Emitted on cold start AND every time the app is
     * backgrounded — supabase-kt's lifecycle callbacks reset status to Initializing in
     * onStop. Screens must hold, never route to sign-in.
     */
    data object Unknown : AuthState

    data class SignedIn(val userId: String) : AuthState

    data class SignedOut(val wasExplicit: Boolean) : AuthState

    /** Token refresh failed. Prompt re-auth; never discard unsynced local data. */
    data object SessionExpired : AuthState
}

interface AuthRepository {
    val authState: Flow<AuthState>

    fun currentUserId(): String?

    suspend fun signIn(email: String, password: String): Outcome<Unit>

    /**
     * Creates an account.
     *
     * [role] is a *request*, not an instruction. It is sent to the server, and the
     * `handle_new_user` trigger grants it only if `roles.self_assignable` is true for
     * that role, falling back to VOLUNTEER otherwise. The client can therefore never
     * grant itself a role the database has not opened up.
     *
     * [organisationName] is required when [role] is a responder role and ignored
     * otherwise — a responder with no organisation cannot be routed to.
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        role: com.varisahayak.domain.model.UserRole,
        organisationName: String? = null,
    ): Outcome<SignUpResult>

    /**
     * Creates multiple accounts from a validated list.
     * Returns a summary of successes and failures.
     */
    suspend fun bulkSignUp(
        users: List<BulkUserRequest>
    ): BulkSignUpResult

    suspend fun signOut(): Outcome<Unit>

    /** Sends a password-reset email. Succeeds silently for unknown addresses. */
    suspend fun sendPasswordReset(email: String): Outcome<Unit>
}

/**
 * Whether the new account is usable immediately.
 *
 * With email confirmation enabled Supabase creates the user but issues no session, so the
 * UI must say "check your inbox" rather than sending the user to a login that cannot yet
 * succeed.
 */
sealed interface SignUpResult {
    data object SignedIn : SignUpResult
    data class ConfirmationRequired(val email: String) : SignUpResult
}

data class BulkUserRequest(
    val email: String,
    val displayName: String,
    val role: com.varisahayak.domain.model.UserRole,
    val organisationName: String? = null,
    val areaName: String? = null,
    val phone: String? = null,
    val rowNumber: Int
)

data class BulkSignUpResult(
    val created: List<BulkUserRequest>,
    val failed: List<BulkUserFailure>
)

data class BulkUserFailure(
    val request: BulkUserRequest,
    val reason: String,
    val suggestion: String? = null
)

interface ProfileRepository {
    /** Cached-first: emits the local profile immediately, then refreshes from the server. */
    fun observeCurrentProfile(): Flow<Profile?>

    suspend fun refresh(userId: String): Outcome<Profile>

    suspend fun clearCache()
}

interface IncidentRepository {
    fun observeAll(): Flow<List<Incident>>

    fun observeOpen(): Flow<List<Incident>>

    fun observeAssignedTo(userId: String): Flow<List<Incident>>

    fun observeActiveSos(): Flow<List<Incident>>

    fun observeById(clientId: String): Flow<Incident?>

    fun observeUnsyncedCount(): Flow<Int>

    fun observeReportedCount(userId: String): Flow<Int>

    fun observeResolvedCount(userId: String): Flow<Int>

    /**
     * Creates an incident locally and returns immediately.
     *
     * This never touches the network. It validates, writes to the database, enqueues sync,
     * and returns — so it succeeds with no connectivity, and the caller can show the
     * incident straight away.
     */
    suspend fun createIncident(
        category: IncidentCategory,
        description: String,
        location: GeoPoint?,
        photoLocalPath: String?,
        affectedPersonNote: String?,
        isSos: Boolean,
        sosBridgeToken: String?,
    ): Outcome<Incident>

    /** Applies a status change through the state machine and queues it for sync. */
    suspend fun updateStatus(
        clientId: String,
        newStatus: IncidentStatus,
        note: String? = null,
    ): Outcome<Incident>

    /** Runs the pending-sync queue. Safe to call repeatedly; retries are idempotent. */
    suspend fun syncPending(): Outcome<SyncSummary>

    /** Pulls server state and reconciles it into the local store. */
    suspend fun refreshFromServer(): Outcome<Unit>

    /**
     * Resolves a server incident id to the local client id the UI navigates by.
     *
     * Needed for notification deep links: a push is composed server-side and can only name
     * the server id, while every route in the app is keyed by the device-generated client
     * id. Returns null when the incident has not reached this device yet — the caller
     * should sync and try again rather than navigate to nothing.
     */
    suspend fun findClientIdByServerId(serverId: String): String?
}

data class SyncSummary(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
) {
    val hasFailures: Boolean get() = failed > 0
}

/**
 * The signed-in device's own position track.
 *
 * Separate from [ResponderRepository.reportLocation], which maintains the single
 * denormalised fix the matcher ranks on. This one appends history, and it applies to every
 * role — a volunteer has no responder row but still has a location worth knowing.
 */
interface LocationRepository {
    /** Best-effort. A failure is logged and swallowed; nothing upstream waits on this. */
    suspend fun record(point: GeoPoint): Outcome<Unit>
}

interface ResponderRepository {
    fun observeAvailable(): Flow<List<Responder>>

    /**
     * The signed-in responder's own availability, from the local cache.
     *
     * Emits null for anyone who is not a responder, and for a responder whose roster row
     * has not been fetched yet. The control renders from this so it is correct offline.
     */
    fun observeOwnAvailability(): Flow<ResponderAvailability?>

    /**
     * Publishes a new availability state.
     *
     * Writes locally first so the toggle responds immediately, then pushes. A failed push
     * leaves the local value in place rather than snapping the control back — going off
     * shift must not silently fail to a state the responder thinks they left.
     */
    suspend fun setAvailability(availability: ResponderAvailability): Outcome<Unit>

    suspend fun reportLocation(location: GeoPoint): Outcome<Unit>

    suspend fun refresh(areaId: String?): Outcome<Unit>
}
