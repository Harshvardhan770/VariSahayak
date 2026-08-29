package com.varisahayak.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.ResponderEntity
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ResponderDao_Impl(
  __db: RoomDatabase,
) : ResponderDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfResponderEntity: EntityUpsertAdapter<ResponderEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfResponderEntity = EntityUpsertAdapter<ResponderEntity>(object : EntityInsertAdapter<ResponderEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `responders` (`userId`,`displayName`,`role`,`availability`,`areaId`,`organisationId`,`capabilitiesCsv`,`lastLatitude`,`lastLongitude`,`lastLocationAtEpochMillis`,`activeAssignmentCount`,`cachedAtEpochMillis`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ResponderEntity) {
        statement.bindText(1, entity.userId)
        statement.bindText(2, entity.displayName)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.availability)
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpAreaId)
        }
        val _tmpOrganisationId: String? = entity.organisationId
        if (_tmpOrganisationId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpOrganisationId)
        }
        statement.bindText(7, entity.capabilitiesCsv)
        val _tmpLastLatitude: Double? = entity.lastLatitude
        if (_tmpLastLatitude == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpLastLatitude)
        }
        val _tmpLastLongitude: Double? = entity.lastLongitude
        if (_tmpLastLongitude == null) {
          statement.bindNull(9)
        } else {
          statement.bindDouble(9, _tmpLastLongitude)
        }
        val _tmpLastLocationAtEpochMillis: Long? = entity.lastLocationAtEpochMillis
        if (_tmpLastLocationAtEpochMillis == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpLastLocationAtEpochMillis)
        }
        statement.bindLong(11, entity.activeAssignmentCount.toLong())
        statement.bindLong(12, entity.cachedAtEpochMillis)
      }
    }, object : EntityDeleteOrUpdateAdapter<ResponderEntity>() {
      protected override fun createQuery(): String = "UPDATE `responders` SET `userId` = ?,`displayName` = ?,`role` = ?,`availability` = ?,`areaId` = ?,`organisationId` = ?,`capabilitiesCsv` = ?,`lastLatitude` = ?,`lastLongitude` = ?,`lastLocationAtEpochMillis` = ?,`activeAssignmentCount` = ?,`cachedAtEpochMillis` = ? WHERE `userId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ResponderEntity) {
        statement.bindText(1, entity.userId)
        statement.bindText(2, entity.displayName)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.availability)
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpAreaId)
        }
        val _tmpOrganisationId: String? = entity.organisationId
        if (_tmpOrganisationId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpOrganisationId)
        }
        statement.bindText(7, entity.capabilitiesCsv)
        val _tmpLastLatitude: Double? = entity.lastLatitude
        if (_tmpLastLatitude == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpLastLatitude)
        }
        val _tmpLastLongitude: Double? = entity.lastLongitude
        if (_tmpLastLongitude == null) {
          statement.bindNull(9)
        } else {
          statement.bindDouble(9, _tmpLastLongitude)
        }
        val _tmpLastLocationAtEpochMillis: Long? = entity.lastLocationAtEpochMillis
        if (_tmpLastLocationAtEpochMillis == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpLastLocationAtEpochMillis)
        }
        statement.bindLong(11, entity.activeAssignmentCount.toLong())
        statement.bindLong(12, entity.cachedAtEpochMillis)
        statement.bindText(13, entity.userId)
      }
    })
  }

  public override suspend fun upsertAll(responders: List<ResponderEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfResponderEntity.upsert(_connection, responders)
  }

  public override fun observeAvailable(): Flow<List<ResponderEntity>> {
    val _sql: String = "SELECT * FROM responders WHERE availability = 'AVAILABLE'"
    return createFlow(__db, false, arrayOf("responders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "displayName")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfAvailability: Int = getColumnIndexOrThrow(_stmt, "availability")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfCapabilitiesCsv: Int = getColumnIndexOrThrow(_stmt, "capabilitiesCsv")
        val _columnIndexOfLastLatitude: Int = getColumnIndexOrThrow(_stmt, "lastLatitude")
        val _columnIndexOfLastLongitude: Int = getColumnIndexOrThrow(_stmt, "lastLongitude")
        val _columnIndexOfLastLocationAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastLocationAtEpochMillis")
        val _columnIndexOfActiveAssignmentCount: Int = getColumnIndexOrThrow(_stmt, "activeAssignmentCount")
        val _columnIndexOfCachedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "cachedAtEpochMillis")
        val _result: MutableList<ResponderEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ResponderEntity
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpDisplayName: String
          _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpAvailability: String
          _tmpAvailability = _stmt.getText(_columnIndexOfAvailability)
          val _tmpAreaId: String?
          if (_stmt.isNull(_columnIndexOfAreaId)) {
            _tmpAreaId = null
          } else {
            _tmpAreaId = _stmt.getText(_columnIndexOfAreaId)
          }
          val _tmpOrganisationId: String?
          if (_stmt.isNull(_columnIndexOfOrganisationId)) {
            _tmpOrganisationId = null
          } else {
            _tmpOrganisationId = _stmt.getText(_columnIndexOfOrganisationId)
          }
          val _tmpCapabilitiesCsv: String
          _tmpCapabilitiesCsv = _stmt.getText(_columnIndexOfCapabilitiesCsv)
          val _tmpLastLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLastLatitude)) {
            _tmpLastLatitude = null
          } else {
            _tmpLastLatitude = _stmt.getDouble(_columnIndexOfLastLatitude)
          }
          val _tmpLastLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLastLongitude)) {
            _tmpLastLongitude = null
          } else {
            _tmpLastLongitude = _stmt.getDouble(_columnIndexOfLastLongitude)
          }
          val _tmpLastLocationAtEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastLocationAtEpochMillis)) {
            _tmpLastLocationAtEpochMillis = null
          } else {
            _tmpLastLocationAtEpochMillis = _stmt.getLong(_columnIndexOfLastLocationAtEpochMillis)
          }
          val _tmpActiveAssignmentCount: Int
          _tmpActiveAssignmentCount = _stmt.getLong(_columnIndexOfActiveAssignmentCount).toInt()
          val _tmpCachedAtEpochMillis: Long
          _tmpCachedAtEpochMillis = _stmt.getLong(_columnIndexOfCachedAtEpochMillis)
          _item = ResponderEntity(_tmpUserId,_tmpDisplayName,_tmpRole,_tmpAvailability,_tmpAreaId,_tmpOrganisationId,_tmpCapabilitiesCsv,_tmpLastLatitude,_tmpLastLongitude,_tmpLastLocationAtEpochMillis,_tmpActiveAssignmentCount,_tmpCachedAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeByArea(areaId: String): Flow<List<ResponderEntity>> {
    val _sql: String = "SELECT * FROM responders WHERE areaId = ?"
    return createFlow(__db, false, arrayOf("responders")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, areaId)
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfDisplayName: Int = getColumnIndexOrThrow(_stmt, "displayName")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfAvailability: Int = getColumnIndexOrThrow(_stmt, "availability")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfCapabilitiesCsv: Int = getColumnIndexOrThrow(_stmt, "capabilitiesCsv")
        val _columnIndexOfLastLatitude: Int = getColumnIndexOrThrow(_stmt, "lastLatitude")
        val _columnIndexOfLastLongitude: Int = getColumnIndexOrThrow(_stmt, "lastLongitude")
        val _columnIndexOfLastLocationAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastLocationAtEpochMillis")
        val _columnIndexOfActiveAssignmentCount: Int = getColumnIndexOrThrow(_stmt, "activeAssignmentCount")
        val _columnIndexOfCachedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "cachedAtEpochMillis")
        val _result: MutableList<ResponderEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ResponderEntity
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpDisplayName: String
          _tmpDisplayName = _stmt.getText(_columnIndexOfDisplayName)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpAvailability: String
          _tmpAvailability = _stmt.getText(_columnIndexOfAvailability)
          val _tmpAreaId: String?
          if (_stmt.isNull(_columnIndexOfAreaId)) {
            _tmpAreaId = null
          } else {
            _tmpAreaId = _stmt.getText(_columnIndexOfAreaId)
          }
          val _tmpOrganisationId: String?
          if (_stmt.isNull(_columnIndexOfOrganisationId)) {
            _tmpOrganisationId = null
          } else {
            _tmpOrganisationId = _stmt.getText(_columnIndexOfOrganisationId)
          }
          val _tmpCapabilitiesCsv: String
          _tmpCapabilitiesCsv = _stmt.getText(_columnIndexOfCapabilitiesCsv)
          val _tmpLastLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLastLatitude)) {
            _tmpLastLatitude = null
          } else {
            _tmpLastLatitude = _stmt.getDouble(_columnIndexOfLastLatitude)
          }
          val _tmpLastLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLastLongitude)) {
            _tmpLastLongitude = null
          } else {
            _tmpLastLongitude = _stmt.getDouble(_columnIndexOfLastLongitude)
          }
          val _tmpLastLocationAtEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastLocationAtEpochMillis)) {
            _tmpLastLocationAtEpochMillis = null
          } else {
            _tmpLastLocationAtEpochMillis = _stmt.getLong(_columnIndexOfLastLocationAtEpochMillis)
          }
          val _tmpActiveAssignmentCount: Int
          _tmpActiveAssignmentCount = _stmt.getLong(_columnIndexOfActiveAssignmentCount).toInt()
          val _tmpCachedAtEpochMillis: Long
          _tmpCachedAtEpochMillis = _stmt.getLong(_columnIndexOfCachedAtEpochMillis)
          _item = ResponderEntity(_tmpUserId,_tmpDisplayName,_tmpRole,_tmpAvailability,_tmpAreaId,_tmpOrganisationId,_tmpCapabilitiesCsv,_tmpLastLatitude,_tmpLastLongitude,_tmpLastLocationAtEpochMillis,_tmpActiveAssignmentCount,_tmpCachedAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM responders"
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
