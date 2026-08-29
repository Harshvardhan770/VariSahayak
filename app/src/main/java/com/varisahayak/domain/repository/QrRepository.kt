package com.varisahayak.domain.repository

import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.QrToken

/**
 * Resolves SOS Bridge identifiers.
 *
 * Resolution is a **server-side** operation. The device sends the opaque token and the
 * server decides what to return based on the caller's role — a volunteer scanning a
 * wristband gets enough to raise a help request; a medical responder assigned to the
 * resulting incident may get more. The app never holds a lookup table of pilgrim details.
 */
interface QrRepository {

    /**
     * Looks the token up.
     *
     * Returns [QrResolution.Offline] rather than an error when there is no connectivity:
     * the SOS Bridge must still be able to raise a help request against the raw token,
     * with resolution deferred until sync. Nothing about getting help for a person in
     * front of you should depend on a working network.
     */
    suspend fun resolve(token: QrToken): Outcome<QrResolution>

    /** Records that a token was resolved, for the audit trail. Queued when offline. */
    suspend fun recordResolution(
        token: QrToken,
        incidentClientId: String?,
    ): Outcome<Unit>
}

/**
 * What the server was willing to tell this caller about a token.
 *
 * Note what is deliberately absent: there is no medical history, no contact number, no
 * identity reference. [subjectReference] is a short non-identifying label — something like
 * a registration group or a tag serial — that lets a responder confirm they are with the
 * right person without the app ever holding their details.
 */
sealed interface QrResolution {

    data class Resolved(
        val token: QrToken,
        val subjectReference: String,
        val areaId: String? = null,
        val organisationId: String? = null,
        val hasActiveIncident: Boolean = false,
    ) : QrResolution

    /** The server does not know this token, or it has been revoked. */
    data object Unknown : QrResolution

    /** No connectivity. The caller proceeds with the raw token; sync resolves it later. */
    data class Offline(val token: QrToken) : QrResolution
}
