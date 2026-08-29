package com.varisahayak.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.LostFoundEntity
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
public class LostFoundDao_Impl(
  __db: RoomDatabase,
) : LostFoundDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfLostFoundEntity: EntityUpsertAdapter<LostFoundEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfLostFoundEntity = EntityUpsertAdapter<LostFoundEntity>(object : EntityInsertAdapter<LostFoundEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `lost_found_items` (`clientId`,`serverId`,`incidentClientId`,`kind`,`title`,`description`,`lastSeenLatitude`,`lastSeenLongitude`,`lastSeenAtEpochMillis`,`qrToken`,`photoLocalPath`,`status`,`reportedBy`,`reportedAtEpochMillis`,`syncState`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LostFoundEntity) {
        statement.bindText(1, entity.clientId)
        val _tmpServerId: String? = entity.serverId
        if (_tmpServerId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpServerId)
        }
        val _tmpIncidentClientId: String? = entity.incidentClientId
        if (_tmpIncidentClientId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpIncidentClientId)
        }
        statement.bindText(4, entity.kind)
        statement.bindText(5, entity.title)
        statement.bindText(6, entity.description)
        val _tmpLastSeenLatitude: Double? = entity.lastSeenLatitude
        if (_tmpLastSeenLatitude == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpLastSeenLatitude)
        }
        val _tmpLastSeenLongitude: Double? = entity.lastSeenLongitude
        if (_tmpLastSeenLongitude == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpLastSeenLongitude)
        }
        val _tmpLastSeenAtEpochMillis: Long? = entity.lastSeenAtEpochMillis
        if (_tmpLastSeenAtEpochMillis == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpLastSeenAtEpochMillis)
        }
        val _tmpQrToken: String? = entity.qrToken
        if (_tmpQrToken == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpQrToken)
        }
        val _tmpPhotoLocalPath: String? = entity.photoLocalPath
        if (_tmpPhotoLocalPath == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpPhotoLocalPath)
        }
        statement.bindText(12, entity.status)
        statement.bindText(13, entity.reportedBy)
        statement.bindLong(14, entity.reportedAtEpochMillis)
        statement.bindText(15, entity.syncState)
      }
    }, object : EntityDeleteOrUpdateAdapter<LostFoundEntity>() {
      protected override fun createQuery(): String = "UPDATE `lost_found_items` SET `clientId` = ?,`serverId` = ?,`incidentClientId` = ?,`kind` = ?,`title` = ?,`description` = ?,`lastSeenLatitude` = ?,`lastSeenLongitude` = ?,`lastSeenAtEpochMillis` = ?,`qrToken` = ?,`photoLocalPath` = ?,`status` = ?,`reportedBy` = ?,`reportedAtEpochMillis` = ?,`syncState` = ? WHERE `clientId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: LostFoundEntity) {
        statement.bindText(1, entity.clientId)
        val _tmpServerId: String? = entity.serverId
        if (_tmpServerId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpServerId)
        }
        val _tmpIncidentClientId: String? = entity.incidentClientId
        if (_tmpIncidentClientId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpIncidentClientId)
        }
        statement.bindText(4, entity.kind)
        statement.bindText(5, entity.title)
        statement.bindText(6, entity.description)
        val _tmpLastSeenLatitude: Double? = entity.lastSeenLatitude
        if (_tmpLastSeenLatitude == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpLastSeenLatitude)
        }
        val _tmpLastSeenLongitude: Double? = entity.lastSeenLongitude
        if (_tmpLastSeenLongitude == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpLastSeenLongitude)
        }
        val _tmpLastSeenAtEpochMillis: Long? = entity.lastSeenAtEpochMillis
        if (_tmpLastSeenAtEpochMillis == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpLastSeenAtEpochMillis)
        }
        val _tmpQrToken: String? = entity.qrToken
        if (_tmpQrToken == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpQrToken)
        }
        val _tmpPhotoLocalPath: String? = entity.photoLocalPath
        if (_tmpPhotoLocalPath == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpPhotoLocalPath)
        }
        statement.bindText(12, entity.status)
        statement.bindText(13, entity.reportedBy)
        statement.bindLong(14, entity.reportedAtEpochMillis)
        statement.bindText(15, entity.syncState)
        statement.bindText(16, entity.clientId)
      }
    })
  }

  public override suspend fun upsert(item: LostFoundEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfLostFoundEntity.upsert(_connection, item)
  }

  public override fun observeAll(): Flow<List<LostFoundEntity>> {
    val _sql: String = "SELECT * FROM lost_found_items ORDER BY reportedAtEpochMillis DESC"
    return createFlow(__db, false, arrayOf("lost_found_items")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfIncidentClientId: Int = getColumnIndexOrThrow(_stmt, "incidentClientId")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLastSeenLatitude: Int = getColumnIndexOrThrow(_stmt, "lastSeenLatitude")
        val _columnIndexOfLastSeenLongitude: Int = getColumnIndexOrThrow(_stmt, "lastSeenLongitude")
        val _columnIndexOfLastSeenAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSeenAtEpochMillis")
        val _columnIndexOfQrToken: Int = getColumnIndexOrThrow(_stmt, "qrToken")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfReportedBy: Int = getColumnIndexOrThrow(_stmt, "reportedBy")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _result: MutableList<LostFoundEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LostFoundEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpIncidentClientId: String?
          if (_stmt.isNull(_columnIndexOfIncidentClientId)) {
            _tmpIncidentClientId = null
          } else {
            _tmpIncidentClientId = _stmt.getText(_columnIndexOfIncidentClientId)
          }
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLastSeenLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLastSeenLatitude)) {
            _tmpLastSeenLatitude = null
          } else {
            _tmpLastSeenLatitude = _stmt.getDouble(_columnIndexOfLastSeenLatitude)
          }
          val _tmpLastSeenLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLastSeenLongitude)) {
            _tmpLastSeenLongitude = null
          } else {
            _tmpLastSeenLongitude = _stmt.getDouble(_columnIndexOfLastSeenLongitude)
          }
          val _tmpLastSeenAtEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSeenAtEpochMillis)) {
            _tmpLastSeenAtEpochMillis = null
          } else {
            _tmpLastSeenAtEpochMillis = _stmt.getLong(_columnIndexOfLastSeenAtEpochMillis)
          }
          val _tmpQrToken: String?
          if (_stmt.isNull(_columnIndexOfQrToken)) {
            _tmpQrToken = null
          } else {
            _tmpQrToken = _stmt.getText(_columnIndexOfQrToken)
          }
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpReportedBy: String
          _tmpReportedBy = _stmt.getText(_columnIndexOfReportedBy)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          _item = LostFoundEntity(_tmpClientId,_tmpServerId,_tmpIncidentClientId,_tmpKind,_tmpTitle,_tmpDescription,_tmpLastSeenLatitude,_tmpLastSeenLongitude,_tmpLastSeenAtEpochMillis,_tmpQrToken,_tmpPhotoLocalPath,_tmpStatus,_tmpReportedBy,_tmpReportedAtEpochMillis,_tmpSyncState)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun search(query: String): Flow<List<LostFoundEntity>> {
    val _sql: String = "SELECT * FROM lost_found_items WHERE title LIKE '%' || ? || '%' OR description LIKE '%' || ? || '%' ORDER BY reportedAtEpochMillis DESC"
    return createFlow(__db, false, arrayOf("lost_found_items")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfIncidentClientId: Int = getColumnIndexOrThrow(_stmt, "incidentClientId")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLastSeenLatitude: Int = getColumnIndexOrThrow(_stmt, "lastSeenLatitude")
        val _columnIndexOfLastSeenLongitude: Int = getColumnIndexOrThrow(_stmt, "lastSeenLongitude")
        val _columnIndexOfLastSeenAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSeenAtEpochMillis")
        val _columnIndexOfQrToken: Int = getColumnIndexOrThrow(_stmt, "qrToken")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfReportedBy: Int = getColumnIndexOrThrow(_stmt, "reportedBy")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _result: MutableList<LostFoundEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LostFoundEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpIncidentClientId: String?
          if (_stmt.isNull(_columnIndexOfIncidentClientId)) {
            _tmpIncidentClientId = null
          } else {
            _tmpIncidentClientId = _stmt.getText(_columnIndexOfIncidentClientId)
          }
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLastSeenLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLastSeenLatitude)) {
            _tmpLastSeenLatitude = null
          } else {
            _tmpLastSeenLatitude = _stmt.getDouble(_columnIndexOfLastSeenLatitude)
          }
          val _tmpLastSeenLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLastSeenLongitude)) {
            _tmpLastSeenLongitude = null
          } else {
            _tmpLastSeenLongitude = _stmt.getDouble(_columnIndexOfLastSeenLongitude)
          }
          val _tmpLastSeenAtEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSeenAtEpochMillis)) {
            _tmpLastSeenAtEpochMillis = null
          } else {
            _tmpLastSeenAtEpochMillis = _stmt.getLong(_columnIndexOfLastSeenAtEpochMillis)
          }
          val _tmpQrToken: String?
          if (_stmt.isNull(_columnIndexOfQrToken)) {
            _tmpQrToken = null
          } else {
            _tmpQrToken = _stmt.getText(_columnIndexOfQrToken)
          }
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpReportedBy: String
          _tmpReportedBy = _stmt.getText(_columnIndexOfReportedBy)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          _item = LostFoundEntity(_tmpClientId,_tmpServerId,_tmpIncidentClientId,_tmpKind,_tmpTitle,_tmpDescription,_tmpLastSeenLatitude,_tmpLastSeenLongitude,_tmpLastSeenAtEpochMillis,_tmpQrToken,_tmpPhotoLocalPath,_tmpStatus,_tmpReportedBy,_tmpReportedAtEpochMillis,_tmpSyncState)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPendingSync(): List<LostFoundEntity> {
    val _sql: String = "SELECT * FROM lost_found_items WHERE syncState IN ('PENDING', 'FAILED')"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfIncidentClientId: Int = getColumnIndexOrThrow(_stmt, "incidentClientId")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLastSeenLatitude: Int = getColumnIndexOrThrow(_stmt, "lastSeenLatitude")
        val _columnIndexOfLastSeenLongitude: Int = getColumnIndexOrThrow(_stmt, "lastSeenLongitude")
        val _columnIndexOfLastSeenAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSeenAtEpochMillis")
        val _columnIndexOfQrToken: Int = getColumnIndexOrThrow(_stmt, "qrToken")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfReportedBy: Int = getColumnIndexOrThrow(_stmt, "reportedBy")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _result: MutableList<LostFoundEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LostFoundEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpIncidentClientId: String?
          if (_stmt.isNull(_columnIndexOfIncidentClientId)) {
            _tmpIncidentClientId = null
          } else {
            _tmpIncidentClientId = _stmt.getText(_columnIndexOfIncidentClientId)
          }
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLastSeenLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLastSeenLatitude)) {
            _tmpLastSeenLatitude = null
          } else {
            _tmpLastSeenLatitude = _stmt.getDouble(_columnIndexOfLastSeenLatitude)
          }
          val _tmpLastSeenLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLastSeenLongitude)) {
            _tmpLastSeenLongitude = null
          } else {
            _tmpLastSeenLongitude = _stmt.getDouble(_columnIndexOfLastSeenLongitude)
          }
          val _tmpLastSeenAtEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSeenAtEpochMillis)) {
            _tmpLastSeenAtEpochMillis = null
          } else {
            _tmpLastSeenAtEpochMillis = _stmt.getLong(_columnIndexOfLastSeenAtEpochMillis)
          }
          val _tmpQrToken: String?
          if (_stmt.isNull(_columnIndexOfQrToken)) {
            _tmpQrToken = null
          } else {
            _tmpQrToken = _stmt.getText(_columnIndexOfQrToken)
          }
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpReportedBy: String
          _tmpReportedBy = _stmt.getText(_columnIndexOfReportedBy)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          _item = LostFoundEntity(_tmpClientId,_tmpServerId,_tmpIncidentClientId,_tmpKind,_tmpTitle,_tmpDescription,_tmpLastSeenLatitude,_tmpLastSeenLongitude,_tmpLastSeenAtEpochMillis,_tmpQrToken,_tmpPhotoLocalPath,_tmpStatus,_tmpReportedBy,_tmpReportedAtEpochMillis,_tmpSyncState)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markSynced(clientId: String, serverId: String) {
    val _sql: String = "UPDATE lost_found_items SET serverId = ?, syncState = 'SYNCED' WHERE clientId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, serverId)
        _argIndex = 2
        _stmt.bindText(_argIndex, clientId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM lost_found_items"
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
