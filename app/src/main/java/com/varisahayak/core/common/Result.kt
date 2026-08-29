package com.varisahayak.core.common

/**
 * The outcome of an operation that can fail in a way the UI must distinguish.
 *
 * Deliberately not Kotlin's [kotlin.Result]: this app needs to tell "you are offline, your
 * work is safe" apart from "the server rejected this", and a Throwable alone does not
 * carry that.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline suspend fun <T, R> Outcome<T>.map(transform: suspend (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

inline suspend fun <T> Outcome<T>.onSuccess(crossinline action: suspend (T) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Success) action(data)
}

inline suspend fun <T> Outcome<T>.onFailure(crossinline action: suspend (AppError) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Failure) action(error)
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.data

/**
 * Failure categories the UI reacts to differently.
 *
 * [Offline] is not an error state in this product — it is a normal operating mode. Screens
 * show "saved, will sync" rather than a failure banner.
 */
sealed interface AppError {
    val cause: Throwable?

    data class Offline(override val cause: Throwable? = null) : AppError

    data class Network(
        val statusCode: Int? = null,
        override val cause: Throwable? = null,
    ) : AppError

    data class Unauthorised(override val cause: Throwable? = null) : AppError

    /** The session expired. Local unsynced data must be preserved. */
    data class SessionExpired(override val cause: Throwable? = null) : AppError

    /**
     * Credentials were accepted but the account's profile could not be resolved, so there
     * is no role to route on.
     *
     * Its own category rather than a [Network] failure because the two need different
     * words: the connection is fine, and retrying without someone fixing the account — or
     * the table privileges behind it — will fail exactly the same way.
     */
    data class ProfileUnavailable(override val cause: Throwable? = null) : AppError

    data class Validation(
        val field: String? = null,
        val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    data class NotFound(override val cause: Throwable? = null) : AppError

    data class PermissionDenied(
        val permission: String,
        override val cause: Throwable? = null,
    ) : AppError

    data class Unknown(override val cause: Throwable? = null) : AppError
}
