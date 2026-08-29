package com.varisahayak.data.repository

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.data.remote.dto.DeviceTokenDto
import com.varisahayak.domain.repository.DeviceTokenRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceTokenRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val dispatchers: DispatcherProvider,
) : DeviceTokenRepository {

    override suspend fun register(token: String): Outcome<Unit> = withContext(dispatchers.io) {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return@withContext Outcome.Failure(AppError.Unauthorised())

        try {
            // Upsert on the token primary key: the same device re-registering after a
            // reinstall or a user switch must move the row to the new profile, not create
            // a second row that keeps notifying the previous owner.
            supabase.from("device_tokens")
                .upsert(DeviceTokenDto(userId = userId, token = token)) {
                    onConflict = "token"
                }
            Outcome.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Never surfaced to the user. Push is best-effort by design — the in-app
            // notification centre is the authoritative record, so a failed registration
            // degrades delivery rather than breaking the workflow.
            Log.w(TAG, "Device token registration failed", error)
            Outcome.Failure(AppError.Network(cause = error))
        }
    }

    override suspend fun registerCurrentToken(): Outcome<Unit> = withContext(dispatchers.io) {
        try {
            register(FirebaseMessaging.getInstance().token.await())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Thrown on a device with no Play Services, and in any build with no
            // google-services.json. Both are survivable: everything except push works.
            Log.w(TAG, "Could not obtain an FCM token", error)
            Outcome.Failure(AppError.Network(cause = error))
        }
    }

    override suspend fun unregister(): Outcome<Unit> = withContext(dispatchers.io) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()

            // Deleted by token, not by user: on a shared device this must remove exactly
            // this handset's registration and leave the same person's other devices alone.
            supabase.from("device_tokens").delete {
                filter { eq("token", token) }
            }
            Outcome.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "Device token removal failed", error)
            Outcome.Failure(AppError.Network(cause = error))
        }
    }

    private companion object {
        const val TAG = "DeviceTokenRepository"
    }
}
