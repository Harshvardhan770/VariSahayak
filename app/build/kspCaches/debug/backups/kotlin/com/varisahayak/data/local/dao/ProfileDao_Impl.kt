package com.varisahayak.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.ProfileEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ProfileDao_Impl(
  __db: RoomDatabase,
) : ProfileDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfProfileEntity: EntityUpsertAdapter<ProfileEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfProfileEntity = EntityUpsertAdapter<ProfileEntity>(object : EntityInsertAdapter<ProfileEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `profiles` (`userId`,`displayName`,`role`,`organisationId`,`organisationName`,`areaId`,`areaName`,`phone`,`capabilitiesCsv`,`cachedAtEpochMillis`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ProfileEntity) {
        statement.bindText(1, entity.userId)
        statement.bindText(2, entity.displayName)
        statement.bindText(3, entity.role)
        val _tmpOrganisationId: String? = entity.organisationId
        if (_tmpOrganisationId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpOrganisationId)
        }
        val _tmpOrganisationName: String? = entity.organisationName
        if (_tmpOrganisationName == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpOrganisationName)
        }
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpAreaId)
        }
        val _tmpAreaName: String? = entity.areaName
        if (_tmpAreaName == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpAreaName)
        }
        val _tmpPhone: String? = entity.phone
        if (_tmpPhone == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpPhone)
        }
        statement.bindText(9, entity.capabilitiesCsv)
        statement.bindLong(10, entity.cachedAtEpochMillis)
      }
    }, object : EntityDeleteOrUpdateAdapter<ProfileEntity>() {
      protected override fun createQuery(): String = "UPDATE `profiles` SET `userId` = ?,`displayName` = ?,`role` = ?,`organisationId` = ?,`organisationName` = ?,`areaId` = ?,`areaName` = ?,`phone` = ?,`capabilitiesCsv` = ?,`cachedAtEpochMillis` = ? WHERE `userId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ProfileEntity) {
        statement.bindText(1, entity.userId)
        statement.bindText(2, entity.displayName)
        statement.bindText(3, entity.role)
        val _tmpOrganisationId: String? = entity.organisationId
        if (_tmpOrganisationId == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpOrganisationId)
        }
        val _tmpOrganisationName: String? = entity.organisationName
        if (_tmpOrganisationName == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpOrganisationName)
        }
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpAreaId)
        }
        val _tmpAreaName: String? = entity.areaName
        if (_tmpAreaName == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpAreaName)
        }
        val _tmpPhone: String? = entity.phone
        if (_tmpPhone == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpPhone)
        }
        statement.bindText(9, entity.capabilitiesCsv)
        statement.bindLong(10, entity.cachedAtEpochMillis)
        statement.bindText(11, entity.userId)
      }
    })
  }

  public override suspend fun upsert(profile: ProfileEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfProfileEntity.upsert(_connection, profile)
  }

  public override fun observe(userId: String): Flow<ProfileEntity?> {
    val _sql: String = "SELECT * FROM profiles WHERE userId = ?"
    return createFlow(__db, false, arrayOf("profiles")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, userId)
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "displayName")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfOrganisationName: Int = getColumnIndexOrThrow(_stmt, "organisationName")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfAreaName: Int = getColumnIndexOrThrow(_stmt, "areaName")
        val _columnIndexOfPhone: Int = getColumnIndexOrThrow(_stmt, "phone")
        val _columnIndexOfCapabilitiesCsv: Int = getColumnIndexOrThrow(_stmt, "capabilitiesCsv")
        val _columnIndexOfCachedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "cachedAtEpochMillis")
        val _result: ProfileEntity?
        if (_stmt.step()) {
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpDisplayName: String
          _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpOrganisationId: String?
          if (_stmt.isNull(_columnIndexOfOrganisationId)) {
            _tmpOrganisationId = null
          } else {
            _tmpOrganisationId = _stmt.getText(_columnIndexOfOrganisationId)
          }
          val _tmpOrganisationName: String?
          if (_stmt.isNull(_columnIndexOfOrganisationName)) {
            _tmpOrganisationName = null
          } else {
            _tmpOrganisationName = _stmt.getText(_columnIndexOfOrganisationName)
          }
          val _tmpAreaId: String?
          if (_stmt.isNull(_columnIndexOfAreaId)) {
            _tmpAreaId = null
          } else {
            _tmpAreaId = _stmt.getText(_columnIndexOfAreaId)
          }
          val _tmpAreaName: String?
          if (_stmt.isNull(_columnIndexOfAreaName)) {
            _tmpAreaName = null
          } else {
            _tmpAreaName = _stmt.getText(_columnIndexOfAreaName)
          }
          val _tmpPhone: String?
          if (_stmt.isNull(_columnIndexOfPhone)) {
            _tmpPhone = null
          } else {
            _tmpPhone = _stmt.getText(_columnIndexOfPhone)
          }
          val _tmpCapabilitiesCsv: String
          _tmpCapabilitiesCsv = _stmt.getText(_columnIndexOfCapabilitiesCsv)
          val _tmpCachedAtEpochMillis: Long
          _tmpCachedAtEpochMillis = _stmt.getLong(_columnIndexOfCachedAtEpochMillis)
          _result = ProfileEntity(_tmpUserId,_tmpDisplayName,_tmpRole,_tmpOrganisationId,_tmpOrganisationName,_tmpAreaId,_tmpAreaName,_tmpPhone,_tmpCapabilitiesCsv,_tmpCachedAtEpochMillis)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeFirst(): Flow<ProfileEntity?> {
    val _sql: String = "SELECT * FROM profiles LIMIT 1"
    return createFlow(__db, false, arrayOf("profiles")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "displayName")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfOrganisationName: Int = getColumnIndexOrThrow(_stmt, "organisationName")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfAreaName: Int = getColumnIndexOrThrow(_stmt, "areaName")
        val _columnIndexOfPhone: Int = getColumnIndexOrThrow(_stmt, "phone")
        val _columnIndexOfCapabilitiesCsv: Int = getColumnIndexOrThrow(_stmt, "capabilitiesCsv")
        val _columnIndexOfCachedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "cachedAtEpochMillis")
        val _result: ProfileEntity?
        if (_stmt.step()) {
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpDisplayName: String
          _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpOrganisationId: String?
          if (_stmt.isNull(_columnIndexOfOrganisationId)) {
            _tmpOrganisationId = null
          } else {
            _tmpOrganisationId = _stmt.getText(_columnIndexOfOrganisationId)
          }
          val _tmpOrganisationName: String?
          if (_stmt.isNull(_columnIndexOfOrganisationName)) {
            _tmpOrganisationName = null
          } else {
            _tmpOrganisationName = _stmt.getText(_columnIndexOfOrganisationName)
          }
          val _tmpAreaId: String?
          if (_stmt.isNull(_columnIndexOfAreaId)) {
            _tmpAreaId = null
          } else {
            _tmpAreaId = _stmt.getText(_columnIndexOfAreaId)
          }
          val _tmpAreaName: String?
          if (_stmt.isNull(_columnIndexOfAreaName)) {
            _tmpAreaName = null
          } else {
            _tmpAreaName = _stmt.getText(_columnIndexOfAreaName)
          }
          val _tmpPhone: String?
          if (_stmt.isNull(_columnIndexOfPhone)) {
            _tmpPhone = null
          } else {
            _tmpPhone = _stmt.getText(_columnIndexOfPhone)
          }
          val _tmpCapabilitiesCsv: String
          _tmpCapabilitiesCsv = _stmt.getText(_columnIndexOfCapabilitiesCsv)
          val _tmpCachedAtEpochMillis: Long
          _tmpCachedAtEpochMillis = _stmt.getLong(_columnIndexOfCachedAtEpochMillis)
          _result = ProfileEntity(_tmpUserId,_tmpDisplayName,_tmpRole,_tmpOrganisationId,_tmpOrganisationName,_tmpAreaId,_tmpAreaName,_tmpPhone,_tmpCapabilitiesCsv,_tmpCachedAtEpochMillis)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun `get`(userId: String): ProfileEntity? {
    val _sql: String = "SELECT * FROM profiles WHERE userId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, userId)
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "displayName")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfOrganisationName: Int = getColumnIndexOrThrow(_stmt, "organisationName")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfAreaName: Int = getColumnIndexOrThrow(_stmt, "areaName")
        val _columnIndexOfPhone: Int = getColumnIndexOrThrow(_stmt, "phone")
        val _columnIndexOfCapabilitiesCsv: Int = getColumnIndexOrThrow(_stmt, "capabilitiesCsv")
        val _columnIndexOfCachedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "cachedAtEpochMillis")
        val _result: ProfileEntity?
        if (_stmt.step()) {
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpDisplayName: String
          _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpOrganisationId: String?
          if (_stmt.isNull(_columnIndexOfOrganisationId)) {
            _tmpOrganisationId = null
          } else {
            _tmpOrganisationId = _stmt.getText(_columnIndexOfOrganisationId)
          }
          val _tmpOrganisationName: String?
          if (_stmt.isNull(_columnIndexOfOrganisationName)) {
            _tmpOrganisationName = null
          } else {
            _tmpOrganisationName = _stmt.getText(_columnIndexOfOrganisationName)
          }
          val _tmpAreaId: String?
          if (_stmt.isNull(_columnIndexOfAreaId)) {
            _tmpAreaId = null
          } else {
            _tmpAreaId = _stmt.getText(_columnIndexOfAreaId)
          }
          val _tmpAreaName: String?
          if (_stmt.isNull(_columnIndexOfAreaName)) {
            _tmpAreaName = null
          } else {
            _tmpAreaName = _stmt.getText(_columnIndexOfAreaName)
          }
          val _tmpPhone: String?
          if (_stmt.isNull(_columnIndexOfPhone)) {
            _tmpPhone = null
          } else {
            _tmpPhone = _stmt.getText(_columnIndexOfPhone)
          }
          val _tmpCapabilitiesCsv: String
          _tmpCapabilitiesCsv = _stmt.getText(_columnIndexOfCapabilitiesCsv)
          val _tmpCachedAtEpochMillis: Long
          _tmpCachedAtEpochMillis = _stmt.getLong(_columnIndexOfCachedAtEpochMillis)
          _result = ProfileEntity(_tmpUserId,_tmpDisplayName,_tmpRole,_tmpOrganisationId,_tmpOrganisationName,_tmpAreaId,_tmpAreaName,_tmpPhone,_tmpCapabilitiesCsv,_tmpCachedAtEpochMillis)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAny(): ProfileEntity? {
    val _sql: String = "SELECT * FROM profiles LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "displayName")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfOrganisationName: Int = getColumnIndexOrThrow(_stmt, "organisationName")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfAreaName: Int = getColumnIndexOrThrow(_stmt, "areaName")
        val _columnIndexOfPhone: Int = getColumnIndexOrThrow(_stmt, "phone")
        val _columnIndexOfCapabilitiesCsv: Int = getColumnIndexOrThrow(_stmt, "capabilitiesCsv")
        val _columnIndexOfCachedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "cachedAtEpochMillis")
        val _result: ProfileEntity?
        if (_stmt.step()) {
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpDisplayName: String
          _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpOrganisationId: String?
          if (_stmt.isNull(_columnIndexOfOrganisationId)) {
            _tmpOrganisationId = null
          } else {
            _tmpOrganisationId = _stmt.getText(_columnIndexOfOrganisationId)
          }
          val _tmpOrganisationName: String?
          if (_stmt.isNull(_columnIndexOfOrganisationName)) {
            _tmpOrganisationName = null
          } else {
            _tmpOrganisationName = _stmt.getText(_columnIndexOfOrganisationName)
          }
          val _tmpAreaId: String?
          if (_stmt.isNull(_columnIndexOfAreaId)) {
            _tmpAreaId = null
          } else {
            _tmpAreaId = _stmt.getText(_columnIndexOfAreaId)
          }
          val _tmpAreaName: String?
          if (_stmt.isNull(_columnIndexOfAreaName)) {
            _tmpAreaName = null
          } else {
            _tmpAreaName = _stmt.getText(_columnIndexOfAreaName)
          }
          val _tmpPhone: String?
          if (_stmt.isNull(_columnIndexOfPhone)) {
            _tmpPhone = null
          } else {
            _tmpPhone = _stmt.getText(_columnIndexOfPhone)
          }
          val _tmpCapabilitiesCsv: String
          _tmpCapabilitiesCsv = _stmt.getText(_columnIndexOfCapabilitiesCsv)
          val _tmpCachedAtEpochMillis: Long
          _tmpCachedAtEpochMillis = _stmt.getLong(_columnIndexOfCachedAtEpochMillis)
          _result = ProfileEntity(_tmpUserId,_tmpDisplayName,_tmpRole,_tmpOrganisationId,_tmpOrganisationName,_tmpAreaId,_tmpAreaName,_tmpPhone,_tmpCapabilitiesCsv,_tmpCachedAtEpochMillis)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM profiles"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
