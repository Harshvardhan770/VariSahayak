package com.varisahayak.data.repository

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.AuthState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    override val authState: Flow<AuthState> = supabase.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user?.id ?: "")
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut(status.isSignOut)
            is SessionStatus.Initializing -> AuthState.Unknown
            is SessionStatus.RefreshFailure -> AuthState.SessionExpired
        }
    }

    override fun currentUserId(): String? =
        (supabase.auth.currentSessionOrNull())?.user?.id

    override suspend fun signIn(email: String, password: String): Outcome<Unit> =
        withContext(dispatchers.io) {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                Outcome.Success(Unit)
            } catch (e: Exception) {
                val error = when {
                    e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                        AppError.Unauthorised(e)
                    else -> AppError.Network(cause = e)
                }
                Outcome.Failure(error)
            }
        }

    override suspend fun signOut(): Outcome<Unit> = withContext(dispatchers.io) {
        try {
            supabase.auth.signOut()
            Outcome.Success(Unit)
        } catch (e: Exception) {
            Outcome.Failure(AppError.Network(cause = e))
        }
    }
}
