package com.varisahayak.data.repository

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.data.local.VariSahayakDatabase
import com.varisahayak.data.local.entity.toDomain
import com.varisahayak.data.local.entity.toEntity
import com.varisahayak.domain.model.Profile
import com.varisahayak.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val database: VariSahayakDatabase,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : ProfileRepository {

    private val profileDao = database.profileDao()

    override fun observeCurrentProfile(): Flow<Profile?> {
        return profileDao.observeFirst().map { it?.toDomain() }.distinctUntilChanged()
    }

    override suspend fun refresh(userId: String): Outcome<Profile> = withContext(dispatchers.io) {
        try {
            val dto = supabase.postgrest.from("profiles")
                .select(columns = Columns.raw("*, roles(wire_name), organisations(name), areas(name)")) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<ProfileDto>()

            val profile = dto.toDomain()
            profileDao.upsert(profile.toEntity(clock.nowEpochMillis()))
            Outcome.Success(profile)
        } catch (e: Exception) {
            Outcome.Failure(AppError.Network(cause = e))
        }
    }

    override suspend fun clearCache() {
        profileDao.clear()
    }
}

@Serializable
private data class ProfileDto(
    val id: String,
    val display_name: String,
    val organisation_id: String? = null,
    val area_id: String? = null,
    val phone: String? = null,
    val roles: RoleDto? = null,
    val organisations: OrgDto? = null,
    val areas: AreaDto? = null,
) {
    fun toDomain() = Profile(
        userId = id,
        displayName = display_name,
        role = com.varisahayak.domain.model.UserRole.fromWire(roles?.wire_name) 
            ?: throw IllegalStateException("Unknown role: ${roles?.wire_name}"),
        organisationId = organisation_id,
        organisationName = organisations?.name,
        areaId = area_id,
        areaName = areas?.name,
        phone = phone,
    )
}

@Serializable
private data class RoleDto(val wire_name: String)

@Serializable
private data class OrgDto(val name: String)

@Serializable
private data class AreaDto(val name: String)
