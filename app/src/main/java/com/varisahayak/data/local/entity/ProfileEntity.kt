package com.varisahayak.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.varisahayak.domain.model.Profile
import com.varisahayak.domain.model.UserRole

/**
 * Cached identity for the signed-in user.
 *
 * Exists so role-aware navigation still resolves on a cold start with no connectivity —
 * a volunteer opening the app on the route should not be stranded on a spinner because
 * the profile lookup cannot reach the server.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val role: String,
    val organisationId: String? = null,
    val organisationName: String? = null,
    val areaId: String? = null,
    val areaName: String? = null,
    val phone: String? = null,
    val capabilitiesCsv: String = "",
    val cachedAtEpochMillis: Long,
)

/**
 * Returns null when the cached role is no longer recognised. Callers route to an explicit
 * "no role" state — an unknown role must never silently fall back to a privileged one.
 */
fun ProfileEntity.toDomain(): Profile? {
    val parsedRole = UserRole.fromWire(role) ?: return null
    return Profile(
        userId = userId,
        displayName = displayName,
        role = parsedRole,
        organisationId = organisationId,
        organisationName = organisationName,
        areaId = areaId,
        areaName = areaName,
        phone = phone,
        capabilities = capabilitiesCsv.split(',').filter { it.isNotBlank() }.toSet(),
    )
}

fun Profile.toEntity(cachedAtEpochMillis: Long): ProfileEntity = ProfileEntity(
    userId = userId,
    displayName = displayName,
    role = role.wireName,
    organisationId = organisationId,
    organisationName = organisationName,
    areaId = areaId,
    areaName = areaName,
    phone = phone,
    capabilitiesCsv = capabilities.joinToString(","),
    cachedAtEpochMillis = cachedAtEpochMillis,
)
