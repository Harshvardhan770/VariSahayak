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
     * [role] is what the sign-up form offered, but it is NOT sent to the server. The
     * database trigger always assigns VOLUNTEER — a client-supplied role would be a
     * privilege-escalation hole. Elevated roles come from the administrator flow.
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        role: com.varisahayak.domain.model.UserRole,
    ): Outcome<SignUpResult>

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
}

data class SyncSummary(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
) {
    val hasFailures: Boolean get() = failed > 0
}

interface ResponderRepository {
    fun observeAvailable(): Flow<List<Responder>>

    suspend fun setAvailability(availability: ResponderAvailability): Outcome<Unit>

    suspend fun reportLocation(location: GeoPoint): Outcome<Unit>

    suspend fun refresh(areaId: String?): Outcome<Unit>
}
