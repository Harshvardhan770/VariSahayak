package com.varisahayak.data.repository

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.domain.model.UserRole
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.AuthState
import com.varisahayak.domain.repository.SignUpResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val connectivity: ConnectivityObserver,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    override val authState: Flow<AuthState> = supabase.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user?.id ?: "")
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut(status.isSignOut)
            // Emitted on cold start AND every time the app is backgrounded. It means
            // "unknown", never "signed out" — mapping it to SignedOut bounces the user to
            // the login screen on every app switch.
            is SessionStatus.Initializing -> AuthState.Unknown
            is SessionStatus.RefreshFailure -> AuthState.SessionExpired
        }
    }

    override fun currentUserId(): String? = supabase.auth.currentSessionOrNull()?.user?.id

    override suspend fun signIn(email: String, password: String): Outcome<Unit> =
        withContext(dispatchers.io) {
            val validation = validateCredentials(email, password)
            if (validation != null) return@withContext Outcome.Failure(validation)

            if (!connectivity.isCurrentlyOnline()) {
                return@withContext Outcome.Failure(AppError.Offline())
            }

            try {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
                Outcome.Success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Outcome.Failure(error.toAuthError())
            }
        }

    /**
     * Creates the account.
     *
     * The profile row in `public.profiles` is created by the `on_auth_user_created`
     * database trigger, not here — see migration 20260829140000. A second client call
     * would have no session to authenticate with when email confirmation is enabled, and
     * would leave an orphaned account if the app died between the two calls.
     *
     * [role] is accepted for the sign-up form's benefit but is deliberately **not** sent
     * to the server. The trigger always assigns VOLUNTEER; trusting a client-supplied
     * role would let anyone self-register as an administrator.
     */
    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        role: UserRole,
    ): Outcome<SignUpResult> = withContext(dispatchers.io) {
        val validation = validateSignUp(email, password, displayName)
        if (validation != null) return@withContext Outcome.Failure(validation)

        if (!connectivity.isCurrentlyOnline()) {
            return@withContext Outcome.Failure(AppError.Offline())
        }

        try {
            supabase.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                data = buildJsonObject {
                    put("display_name", displayName.trim())
                }
            }

            // With email confirmation enabled there is no session yet. Reporting "account
            // created, now sign in" as success would strand the user at a login that
            // cannot succeed until they click the link.
            val result = if (supabase.auth.currentSessionOrNull() != null) {
                SignUpResult.SignedIn
            } else {
                SignUpResult.ConfirmationRequired(email.trim())
            }
            Outcome.Success(result)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Outcome.Failure(error.toAuthError())
        }
    }

    override suspend fun signOut(): Outcome<Unit> = withContext(dispatchers.io) {
        try {
            supabase.auth.signOut()
            Outcome.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Outcome.Failure(error.toAuthError())
        }
    }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        withContext(dispatchers.io) {
            if (!isValidEmail(email)) {
                return@withContext Outcome.Failure(
                    AppError.Validation(field = FIELD_EMAIL, message = MSG_EMAIL_INVALID),
                )
            }
            if (!connectivity.isCurrentlyOnline()) {
                return@withContext Outcome.Failure(AppError.Offline())
            }

            try {
                supabase.auth.resetPasswordForEmail(email.trim())
                Outcome.Success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Outcome.Failure(error.toAuthError())
            }
        }

    // --- validation -------------------------------------------------------------------

    private fun validateCredentials(email: String, password: String): AppError? = when {
        email.isBlank() -> AppError.Validation(FIELD_EMAIL, MSG_EMAIL_REQUIRED)
        !isValidEmail(email) -> AppError.Validation(FIELD_EMAIL, MSG_EMAIL_INVALID)
        password.isBlank() -> AppError.Validation(FIELD_PASSWORD, MSG_PASSWORD_REQUIRED)
        else -> null
    }

    private fun validateSignUp(
        email: String,
        password: String,
        displayName: String,
    ): AppError? = when {
        displayName.isBlank() -> AppError.Validation(FIELD_NAME, MSG_NAME_REQUIRED)
        email.isBlank() -> AppError.Validation(FIELD_EMAIL, MSG_EMAIL_REQUIRED)
        !isValidEmail(email) -> AppError.Validation(FIELD_EMAIL, MSG_EMAIL_INVALID)
        password.isBlank() -> AppError.Validation(FIELD_PASSWORD, MSG_PASSWORD_REQUIRED)
        password.length < MIN_PASSWORD_LENGTH ->
            AppError.Validation(FIELD_PASSWORD, MSG_PASSWORD_TOO_SHORT)
        else -> null
    }

    private fun isValidEmail(email: String): Boolean =
        EMAIL_PATTERN.matches(email.trim())

    /**
     * Maps a Supabase/network failure onto something a volunteer can act on.
     *
     * Supabase returns its reason in the message body rather than as a typed error, so
     * matching on text is unavoidable. Anything unrecognised becomes a generic message —
     * a raw exception must never reach the screen.
     */
    private fun Exception.toAuthError(): AppError {
        val text = (message ?: "").lowercase()

        return when {
            this is IOException -> AppError.Offline(this)

            "already registered" in text ||
                "already been registered" in text ||
                "user already exists" in text ||
                "duplicate key" in text ->
                AppError.Validation(FIELD_EMAIL, MSG_EMAIL_TAKEN, this)

            "invalid login credentials" in text || "invalid_credentials" in text ->
                AppError.Validation(field = null, message = MSG_INVALID_CREDENTIALS, cause = this)

            "email not confirmed" in text ->
                AppError.Validation(field = null, message = MSG_EMAIL_NOT_CONFIRMED, cause = this)

            "password should be at least" in text || "weak password" in text ->
                AppError.Validation(FIELD_PASSWORD, MSG_PASSWORD_TOO_SHORT, this)

            "unable to validate email" in text || "invalid email" in text ->
                AppError.Validation(FIELD_EMAIL, MSG_EMAIL_INVALID, this)

            "rate limit" in text || "too many requests" in text ->
                AppError.Validation(field = null, message = MSG_RATE_LIMITED, cause = this)

            "network" in text || "timeout" in text || "unreachable" in text ->
                AppError.Offline(this)

            else -> AppError.Unknown(this)
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8

        const val FIELD_EMAIL = "email"
        const val FIELD_PASSWORD = "password"
        const val FIELD_NAME = "displayName"

        const val MSG_EMAIL_REQUIRED = "Enter your email address."
        const val MSG_EMAIL_INVALID = "That does not look like a valid email address."
        const val MSG_EMAIL_TAKEN = "An account already exists for this email. Sign in instead."
        const val MSG_EMAIL_NOT_CONFIRMED =
            "Confirm your email address first. Check your inbox for the link."
        const val MSG_PASSWORD_REQUIRED = "Enter your password."
        const val MSG_PASSWORD_TOO_SHORT = "Password must be at least 8 characters."
        const val MSG_NAME_REQUIRED = "Enter your full name."
        const val MSG_INVALID_CREDENTIALS = "Email or password is incorrect."
        const val MSG_RATE_LIMITED = "Too many attempts. Wait a minute and try again."

        val EMAIL_PATTERN = Regex("""^[\w.+-]+@[\w-]+\.[\w.-]{2,}$""")
    }
}
