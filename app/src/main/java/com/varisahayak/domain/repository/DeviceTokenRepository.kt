package com.varisahayak.domain.repository

import com.varisahayak.core.common.Outcome

/**
 * The FCM registration token for this device, tied to the signed-in profile.
 *
 * Registration is idempotent and must be driven from three places, because each covers a
 * gap the others leave:
 *
 *  - `onNewToken` — Firebase rotated the token.
 *  - sign-in — the token was issued while nobody was signed in, so it was never attached
 *    to a profile; or a different user is now using this device.
 *  - sign-out — the token must stop reaching a person who is no longer here. A shared
 *    device handed to the next volunteer would otherwise keep delivering the previous
 *    volunteer's assignments.
 */
interface DeviceTokenRepository {

    /**
     * Attaches [token] to the signed-in profile. Safe to call repeatedly.
     *
     * Fails quietly when nobody is signed in: a token issued before sign-in is registered
     * later by [registerCurrentToken], not lost.
     */
    suspend fun register(token: String): Outcome<Unit>

    /** Fetches the current token from Firebase and registers it. Called on sign-in. */
    suspend fun registerCurrentToken(): Outcome<Unit>

    /** Removes this device's token. Called on sign-out, before the session is cleared. */
    suspend fun unregister(): Outcome<Unit>
}
