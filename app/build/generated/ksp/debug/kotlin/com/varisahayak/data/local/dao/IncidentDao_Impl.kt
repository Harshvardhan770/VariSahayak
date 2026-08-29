package com.varisahayak.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.IncidentEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Double
import kotlin.Float
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
public class IncidentDao_Impl(
  __db: RoomDatabase,
) : IncidentDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfIncidentEntity: EntityInsertAdapter<IncidentEntity>

  private val __upsertAdapterOfIncidentEntity: EntityUpsertAdapter<IncidentEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfIncidentEntity = object : EntityInsertAdapter<IncidentEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `incidents` (`clientId`,`serverId`,`category`,`description`,`latitude`,`longitude`,`locationAccuracyMeters`,`locationIsApproximate`,`reporterId`,`reportedAtEpochMillis`,`photoLocalPath`,`photoRemotePath`,`affectedPersonNote`,`status`,`priority`,`syncState`,`isSos`,`sosBridgeToken`,`assigneeId`,`areaId`,`organisationId`,`lastSyncAttemptEpochMillis`,`syncAttemptCount`,`updatedAtEpochMillis`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: IncidentEntity) {
        statement.bindText(1, entity.clientId)
        val _tmpServerId: String? = entity.serverId
        if (_tmpServerId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpServerId)
        }
        statement.bindText(3, entity.category)
        statement.bindText(4, entity.description)
        val _tmpLatitude: Double? = entity.latitude
        if (_tmpLatitude == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpLatitude)
        }
        val _tmpLongitude: Double? = entity.longitude
        if (_tmpLongitude == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpLongitude)
        }
        val _tmpLocationAccuracyMeters: Float? = entity.locationAccuracyMeters
        if (_tmpLocationAccuracyMeters == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpLocationAccuracyMeters.toDouble())
        }
        val _tmp: Int = if (entity.locationIsApproximate) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindText(9, entity.reporterId)
        statement.bindLong(10, entity.reportedAtEpochMillis)
        val _tmpPhotoLocalPath: String? = entity.photoLocalPath
        if (_tmpPhotoLocalPath == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpPhotoLocalPath)
        }
        val _tmpPhotoRemotePath: String? = entity.photoRemotePath
        if (_tmpPhotoRemotePath == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpPhotoRemotePath)
        }
        val _tmpAffectedPersonNote: String? = entity.affectedPersonNote
        if (_tmpAffectedPersonNote == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpAffectedPersonNote)
        }
        statement.bindText(14, entity.status)
        statement.bindText(15, entity.priority)
        statement.bindText(16, entity.syncState)
        val _tmp_1: Int = if (entity.isSos) 1 else 0
        statement.bindLong(17, _tmp_1.toLong())
        val _tmpSosBridgeToken: String? = entity.sosBridgeToken
        if (_tmpSosBridgeToken == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpSosBridgeToken)
        }
        val _tmpAssigneeId: String? = entity.assigneeId
        if (_tmpAssigneeId == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpAssigneeId)
        }
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(20)
        } else {
          statement.bindText(20, _tmpAreaId)
        }
        val _tmpOrganisationId: String? = entity.organisationId
        if (_tmpOrganisationId == null) {
          statement.bindNull(21)
        } else {
          statement.bindText(21, _tmpOrganisationId)
        }
        val _tmpLastSyncAttemptEpochMillis: Long? = entity.lastSyncAttemptEpochMillis
        if (_tmpLastSyncAttemptEpochMillis == null) {
          statement.bindNull(22)
        } else {
          statement.bindLong(22, _tmpLastSyncAttemptEpochMillis)
        }
        statement.bindLong(23, entity.syncAttemptCount.toLong())
        statement.bindLong(24, entity.updatedAtEpochMillis)
      }
    }
    this.__upsertAdapterOfIncidentEntity = EntityUpsertAdapter<IncidentEntity>(object : EntityInsertAdapter<IncidentEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `incidents` (`clientId`,`serverId`,`category`,`description`,`latitude`,`longitude`,`locationAccuracyMeters`,`locationIsApproximate`,`reporterId`,`reportedAtEpochMillis`,`photoLocalPath`,`photoRemotePath`,`affectedPersonNote`,`status`,`priority`,`syncState`,`isSos`,`sosBridgeToken`,`assigneeId`,`areaId`,`organisationId`,`lastSyncAttemptEpochMillis`,`syncAttemptCount`,`updatedAtEpochMillis`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: IncidentEntity) {
        statement.bindText(1, entity.clientId)
        val _tmpServerId: String? = entity.serverId
        if (_tmpServerId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpServerId)
        }
        statement.bindText(3, entity.category)
        statement.bindText(4, entity.description)
        val _tmpLatitude: Double? = entity.latitude
        if (_tmpLatitude == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpLatitude)
        }
        val _tmpLongitude: Double? = entity.longitude
        if (_tmpLongitude == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpLongitude)
        }
        val _tmpLocationAccuracyMeters: Float? = entity.locationAccuracyMeters
        if (_tmpLocationAccuracyMeters == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpLocationAccuracyMeters.toDouble())
        }
        val _tmp: Int = if (entity.locationIsApproximate) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindText(9, entity.reporterId)
        statement.bindLong(10, entity.reportedAtEpochMillis)
        val _tmpPhotoLocalPath: String? = entity.photoLocalPath
        if (_tmpPhotoLocalPath == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpPhotoLocalPath)
        }
        val _tmpPhotoRemotePath: String? = entity.photoRemotePath
        if (_tmpPhotoRemotePath == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpPhotoRemotePath)
        }
        val _tmpAffectedPersonNote: String? = entity.affectedPersonNote
        if (_tmpAffectedPersonNote == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpAffectedPersonNote)
        }
        statement.bindText(14, entity.status)
        statement.bindText(15, entity.priority)
        statement.bindText(16, entity.syncState)
        val _tmp_1: Int = if (entity.isSos) 1 else 0
        statement.bindLong(17, _tmp_1.toLong())
        val _tmpSosBridgeToken: String? = entity.sosBridgeToken
        if (_tmpSosBridgeToken == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpSosBridgeToken)
        }
        val _tmpAssigneeId: String? = entity.assigneeId
        if (_tmpAssigneeId == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpAssigneeId)
        }
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(20)
        } else {
          statement.bindText(20, _tmpAreaId)
        }
        val _tmpOrganisationId: String? = entity.organisationId
        if (_tmpOrganisationId == null) {
          statement.bindNull(21)
        } else {
          statement.bindText(21, _tmpOrganisationId)
        }
        val _tmpLastSyncAttemptEpochMillis: Long? = entity.lastSyncAttemptEpochMillis
        if (_tmpLastSyncAttemptEpochMillis == null) {
          statement.bindNull(22)
        } else {
          statement.bindLong(22, _tmpLastSyncAttemptEpochMillis)
        }
        statement.bindLong(23, entity.syncAttemptCount.toLong())
        statement.bindLong(24, entity.updatedAtEpochMillis)
      }
    }, object : EntityDeleteOrUpdateAdapter<IncidentEntity>() {
      protected override fun createQuery(): String = "UPDATE `incidents` SET `clientId` = ?,`serverId` = ?,`category` = ?,`description` = ?,`latitude` = ?,`longitude` = ?,`locationAccuracyMeters` = ?,`locationIsApproximate` = ?,`reporterId` = ?,`reportedAtEpochMillis` = ?,`photoLocalPath` = ?,`photoRemotePath` = ?,`affectedPersonNote` = ?,`status` = ?,`priority` = ?,`syncState` = ?,`isSos` = ?,`sosBridgeToken` = ?,`assigneeId` = ?,`areaId` = ?,`organisationId` = ?,`lastSyncAttemptEpochMillis` = ?,`syncAttemptCount` = ?,`updatedAtEpochMillis` = ? WHERE `clientId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: IncidentEntity) {
        statement.bindText(1, entity.clientId)
        val _tmpServerId: String? = entity.serverId
        if (_tmpServerId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpServerId)
        }
        statement.bindText(3, entity.category)
        statement.bindText(4, entity.description)
        val _tmpLatitude: Double? = entity.latitude
        if (_tmpLatitude == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpLatitude)
        }
        val _tmpLongitude: Double? = entity.longitude
        if (_tmpLongitude == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpLongitude)
        }
        val _tmpLocationAccuracyMeters: Float? = entity.locationAccuracyMeters
        if (_tmpLocationAccuracyMeters == null) {
          statement.bindNull(7)
        } else {
          statement.bindDouble(7, _tmpLocationAccuracyMeters.toDouble())
        }
        val _tmp: Int = if (entity.locationIsApproximate) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindText(9, entity.reporterId)
        statement.bindLong(10, entity.reportedAtEpochMillis)
        val _tmpPhotoLocalPath: String? = entity.photoLocalPath
        if (_tmpPhotoLocalPath == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpPhotoLocalPath)
        }
        val _tmpPhotoRemotePath: String? = entity.photoRemotePath
        if (_tmpPhotoRemotePath == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpPhotoRemotePath)
        }
        val _tmpAffectedPersonNote: String? = entity.affectedPersonNote
        if (_tmpAffectedPersonNote == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpAffectedPersonNote)
        }
        statement.bindText(14, entity.status)
        statement.bindText(15, entity.priority)
        statement.bindText(16, entity.syncState)
        val _tmp_1: Int = if (entity.isSos) 1 else 0
        statement.bindLong(17, _tmp_1.toLong())
        val _tmpSosBridgeToken: String? = entity.sosBridgeToken
        if (_tmpSosBridgeToken == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpSosBridgeToken)
        }
        val _tmpAssigneeId: String? = entity.assigneeId
        if (_tmpAssigneeId == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpAssigneeId)
        }
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(20)
        } else {
          statement.bindText(20, _tmpAreaId)
        }
        val _tmpOrganisationId: String? = entity.organisationId
        if (_tmpOrganisationId == null) {
          statement.bindNull(21)
        } else {
          statement.bindText(21, _tmpOrganisationId)
        }
        val _tmpLastSyncAttemptEpochMillis: Long? = entity.lastSyncAttemptEpochMillis
        if (_tmpLastSyncAttemptEpochMillis == null) {
          statement.bindNull(22)
        } else {
          statement.bindLong(22, _tmpLastSyncAttemptEpochMillis)
        }
        statement.bindLong(23, entity.syncAttemptCount.toLong())
        statement.bindLong(24, entity.updatedAtEpochMillis)
        statement.bindText(25, entity.clientId)
      }
    })
  }

  public override suspend fun insert(incident: IncidentEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfIncidentEntity.insert(_connection, incident)
  }

  public override suspend fun reconcileFromServer(remote: IncidentEntity): Unit = performInTransactionSuspending(__db) {
    super@IncidentDao_Impl.reconcileFromServer(remote)
  }

  public override suspend fun upsert(incident: IncidentEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfIncidentEntity.upsert(_connection, incident)
  }

  public override suspend fun upsertAll(incidents: List<IncidentEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfIncidentEntity.upsert(_connection, incidents)
  }

  public override fun observeAll(): Flow<List<IncidentEntity>> {
    val _sql: String = "SELECT * FROM incidents ORDER BY reportedAtEpochMillis DESC"
    return createFlow(__db, false, arrayOf("incidents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationAccuracyMeters: Int = getColumnIndexOrThrow(_stmt, "locationAccuracyMeters")
        val _columnIndexOfLocationIsApproximate: Int = getColumnIndexOrThrow(_stmt, "locationIsApproximate")
        val _columnIndexOfReporterId: Int = getColumnIndexOrThrow(_stmt, "reporterId")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfPhotoRemotePath: Int = getColumnIndexOrThrow(_stmt, "photoRemotePath")
        val _columnIndexOfAffectedPersonNote: Int = getColumnIndexOrThrow(_stmt, "affectedPersonNote")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _columnIndexOfIsSos: Int = getColumnIndexOrThrow(_stmt, "isSos")
        val _columnIndexOfSosBridgeToken: Int = getColumnIndexOrThrow(_stmt, "sosBridgeToken")
        val _columnIndexOfAssigneeId: Int = getColumnIndexOrThrow(_stmt, "assigneeId")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfLastSyncAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSyncAttemptEpochMillis")
        val _columnIndexOfSyncAttemptCount: Int = getColumnIndexOrThrow(_stmt, "syncAttemptCount")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _result: MutableList<IncidentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: IncidentEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          }
          val _tmpLocationAccuracyMeters: Float?
          if (_stmt.isNull(_columnIndexOfLocationAccuracyMeters)) {
            _tmpLocationAccuracyMeters = null
          } else {
            _tmpLocationAccuracyMeters = _stmt.getDouble(_columnIndexOfLocationAccuracyMeters).toFloat()
          }
          val _tmpLocationIsApproximate: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLocationIsApproximate).toInt()
          _tmpLocationIsApproximate = _tmp != 0
          val _tmpReporterId: String
          _tmpReporterId = _stmt.getText(_columnIndexOfReporterId)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpPhotoRemotePath: String?
          if (_stmt.isNull(_columnIndexOfPhotoRemotePath)) {
            _tmpPhotoRemotePath = null
          } else {
            _tmpPhotoRemotePath = _stmt.getText(_columnIndexOfPhotoRemotePath)
          }
          val _tmpAffectedPersonNote: String?
          if (_stmt.isNull(_columnIndexOfAffectedPersonNote)) {
            _tmpAffectedPersonNote = null
          } else {
            _tmpAffectedPersonNote = _stmt.getText(_columnIndexOfAffectedPersonNote)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpPriority: String
          _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          val _tmpIsSos: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsSos).toInt()
          _tmpIsSos = _tmp_1 != 0
          val _tmpSosBridgeToken: String?
          if (_stmt.isNull(_columnIndexOfSosBridgeToken)) {
            _tmpSosBridgeToken = null
          } else {
            _tmpSosBridgeToken = _stmt.getText(_columnIndexOfSosBridgeToken)
          }
          val _tmpAssigneeId: String?
          if (_stmt.isNull(_columnIndexOfAssigneeId)) {
            _tmpAssigneeId = null
          } else {
            _tmpAssigneeId = _stmt.getText(_columnIndexOfAssigneeId)
          }
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
          val _tmpLastSyncAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSyncAttemptEpochMillis)) {
            _tmpLastSyncAttemptEpochMillis = null
          } else {
            _tmpLastSyncAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastSyncAttemptEpochMillis)
          }
          val _tmpSyncAttemptCount: Int
          _tmpSyncAttemptCount = _stmt.getLong(_columnIndexOfSyncAttemptCount).toInt()
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          _item = IncidentEntity(_tmpClientId,_tmpServerId,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpLocationAccuracyMeters,_tmpLocationIsApproximate,_tmpReporterId,_tmpReportedAtEpochMillis,_tmpPhotoLocalPath,_tmpPhotoRemotePath,_tmpAffectedPersonNote,_tmpStatus,_tmpPriority,_tmpSyncState,_tmpIsSos,_tmpSosBridgeToken,_tmpAssigneeId,_tmpAreaId,_tmpOrganisationId,_tmpLastSyncAttemptEpochMillis,_tmpSyncAttemptCount,_tmpUpdatedAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeOpen(): Flow<List<IncidentEntity>> {
    val _sql: String = """
        |
        |        SELECT * FROM incidents
        |        WHERE status NOT IN ('RESOLVED', 'CANCELLED')
        |        ORDER BY
        |            CASE priority
        |                WHEN 'CRITICAL' THEN 0
        |                WHEN 'HIGH' THEN 1
        |                WHEN 'MEDIUM' THEN 2
        |                ELSE 3
        |            END,
        |            reportedAtEpochMillis DESC
        |        
        """.trimMargin()
    return createFlow(__db, false, arrayOf("incidents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationAccuracyMeters: Int = getColumnIndexOrThrow(_stmt, "locationAccuracyMeters")
        val _columnIndexOfLocationIsApproximate: Int = getColumnIndexOrThrow(_stmt, "locationIsApproximate")
        val _columnIndexOfReporterId: Int = getColumnIndexOrThrow(_stmt, "reporterId")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfPhotoRemotePath: Int = getColumnIndexOrThrow(_stmt, "photoRemotePath")
        val _columnIndexOfAffectedPersonNote: Int = getColumnIndexOrThrow(_stmt, "affectedPersonNote")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _columnIndexOfIsSos: Int = getColumnIndexOrThrow(_stmt, "isSos")
        val _columnIndexOfSosBridgeToken: Int = getColumnIndexOrThrow(_stmt, "sosBridgeToken")
        val _columnIndexOfAssigneeId: Int = getColumnIndexOrThrow(_stmt, "assigneeId")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfLastSyncAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSyncAttemptEpochMillis")
        val _columnIndexOfSyncAttemptCount: Int = getColumnIndexOrThrow(_stmt, "syncAttemptCount")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _result: MutableList<IncidentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: IncidentEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          }
          val _tmpLocationAccuracyMeters: Float?
          if (_stmt.isNull(_columnIndexOfLocationAccuracyMeters)) {
            _tmpLocationAccuracyMeters = null
          } else {
            _tmpLocationAccuracyMeters = _stmt.getDouble(_columnIndexOfLocationAccuracyMeters).toFloat()
          }
          val _tmpLocationIsApproximate: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLocationIsApproximate).toInt()
          _tmpLocationIsApproximate = _tmp != 0
          val _tmpReporterId: String
          _tmpReporterId = _stmt.getText(_columnIndexOfReporterId)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpPhotoRemotePath: String?
          if (_stmt.isNull(_columnIndexOfPhotoRemotePath)) {
            _tmpPhotoRemotePath = null
          } else {
            _tmpPhotoRemotePath = _stmt.getText(_columnIndexOfPhotoRemotePath)
          }
          val _tmpAffectedPersonNote: String?
          if (_stmt.isNull(_columnIndexOfAffectedPersonNote)) {
            _tmpAffectedPersonNote = null
          } else {
            _tmpAffectedPersonNote = _stmt.getText(_columnIndexOfAffectedPersonNote)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpPriority: String
          _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          val _tmpIsSos: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsSos).toInt()
          _tmpIsSos = _tmp_1 != 0
          val _tmpSosBridgeToken: String?
          if (_stmt.isNull(_columnIndexOfSosBridgeToken)) {
            _tmpSosBridgeToken = null
          } else {
            _tmpSosBridgeToken = _stmt.getText(_columnIndexOfSosBridgeToken)
          }
          val _tmpAssigneeId: String?
          if (_stmt.isNull(_columnIndexOfAssigneeId)) {
            _tmpAssigneeId = null
          } else {
            _tmpAssigneeId = _stmt.getText(_columnIndexOfAssigneeId)
          }
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
          val _tmpLastSyncAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSyncAttemptEpochMillis)) {
            _tmpLastSyncAttemptEpochMillis = null
          } else {
            _tmpLastSyncAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastSyncAttemptEpochMillis)
          }
          val _tmpSyncAttemptCount: Int
          _tmpSyncAttemptCount = _stmt.getLong(_columnIndexOfSyncAttemptCount).toInt()
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          _item = IncidentEntity(_tmpClientId,_tmpServerId,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpLocationAccuracyMeters,_tmpLocationIsApproximate,_tmpReporterId,_tmpReportedAtEpochMillis,_tmpPhotoLocalPath,_tmpPhotoRemotePath,_tmpAffectedPersonNote,_tmpStatus,_tmpPriority,_tmpSyncState,_tmpIsSos,_tmpSosBridgeToken,_tmpAssigneeId,_tmpAreaId,_tmpOrganisationId,_tmpLastSyncAttemptEpochMillis,_tmpSyncAttemptCount,_tmpUpdatedAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeAssignedTo(userId: String): Flow<List<IncidentEntity>> {
    val _sql: String = "SELECT * FROM incidents WHERE assigneeId = ? AND status NOT IN ('RESOLVED', 'CANCELLED') ORDER BY reportedAtEpochMillis DESC"
    return createFlow(__db, false, arrayOf("incidents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, userId)
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationAccuracyMeters: Int = getColumnIndexOrThrow(_stmt, "locationAccuracyMeters")
        val _columnIndexOfLocationIsApproximate: Int = getColumnIndexOrThrow(_stmt, "locationIsApproximate")
        val _columnIndexOfReporterId: Int = getColumnIndexOrThrow(_stmt, "reporterId")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfPhotoRemotePath: Int = getColumnIndexOrThrow(_stmt, "photoRemotePath")
        val _columnIndexOfAffectedPersonNote: Int = getColumnIndexOrThrow(_stmt, "affectedPersonNote")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _columnIndexOfIsSos: Int = getColumnIndexOrThrow(_stmt, "isSos")
        val _columnIndexOfSosBridgeToken: Int = getColumnIndexOrThrow(_stmt, "sosBridgeToken")
        val _columnIndexOfAssigneeId: Int = getColumnIndexOrThrow(_stmt, "assigneeId")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfLastSyncAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSyncAttemptEpochMillis")
        val _columnIndexOfSyncAttemptCount: Int = getColumnIndexOrThrow(_stmt, "syncAttemptCount")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _result: MutableList<IncidentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: IncidentEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          }
          val _tmpLocationAccuracyMeters: Float?
          if (_stmt.isNull(_columnIndexOfLocationAccuracyMeters)) {
            _tmpLocationAccuracyMeters = null
          } else {
            _tmpLocationAccuracyMeters = _stmt.getDouble(_columnIndexOfLocationAccuracyMeters).toFloat()
          }
          val _tmpLocationIsApproximate: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLocationIsApproximate).toInt()
          _tmpLocationIsApproximate = _tmp != 0
          val _tmpReporterId: String
          _tmpReporterId = _stmt.getText(_columnIndexOfReporterId)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpPhotoRemotePath: String?
          if (_stmt.isNull(_columnIndexOfPhotoRemotePath)) {
            _tmpPhotoRemotePath = null
          } else {
            _tmpPhotoRemotePath = _stmt.getText(_columnIndexOfPhotoRemotePath)
          }
          val _tmpAffectedPersonNote: String?
          if (_stmt.isNull(_columnIndexOfAffectedPersonNote)) {
            _tmpAffectedPersonNote = null
          } else {
            _tmpAffectedPersonNote = _stmt.getText(_columnIndexOfAffectedPersonNote)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpPriority: String
          _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          val _tmpIsSos: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsSos).toInt()
          _tmpIsSos = _tmp_1 != 0
          val _tmpSosBridgeToken: String?
          if (_stmt.isNull(_columnIndexOfSosBridgeToken)) {
            _tmpSosBridgeToken = null
          } else {
            _tmpSosBridgeToken = _stmt.getText(_columnIndexOfSosBridgeToken)
          }
          val _tmpAssigneeId: String?
          if (_stmt.isNull(_columnIndexOfAssigneeId)) {
            _tmpAssigneeId = null
          } else {
            _tmpAssigneeId = _stmt.getText(_columnIndexOfAssigneeId)
          }
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
          val _tmpLastSyncAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSyncAttemptEpochMillis)) {
            _tmpLastSyncAttemptEpochMillis = null
          } else {
            _tmpLastSyncAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastSyncAttemptEpochMillis)
          }
          val _tmpSyncAttemptCount: Int
          _tmpSyncAttemptCount = _stmt.getLong(_columnIndexOfSyncAttemptCount).toInt()
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          _item = IncidentEntity(_tmpClientId,_tmpServerId,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpLocationAccuracyMeters,_tmpLocationIsApproximate,_tmpReporterId,_tmpReportedAtEpochMillis,_tmpPhotoLocalPath,_tmpPhotoRemotePath,_tmpAffectedPersonNote,_tmpStatus,_tmpPriority,_tmpSyncState,_tmpIsSos,_tmpSosBridgeToken,_tmpAssigneeId,_tmpAreaId,_tmpOrganisationId,_tmpLastSyncAttemptEpochMillis,_tmpSyncAttemptCount,_tmpUpdatedAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeActiveSos(): Flow<List<IncidentEntity>> {
    val _sql: String = "SELECT * FROM incidents WHERE isSos = 1 AND status NOT IN ('RESOLVED', 'CANCELLED') ORDER BY reportedAtEpochMillis DESC"
    return createFlow(__db, false, arrayOf("incidents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationAccuracyMeters: Int = getColumnIndexOrThrow(_stmt, "locationAccuracyMeters")
        val _columnIndexOfLocationIsApproximate: Int = getColumnIndexOrThrow(_stmt, "locationIsApproximate")
        val _columnIndexOfReporterId: Int = getColumnIndexOrThrow(_stmt, "reporterId")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfPhotoRemotePath: Int = getColumnIndexOrThrow(_stmt, "photoRemotePath")
        val _columnIndexOfAffectedPersonNote: Int = getColumnIndexOrThrow(_stmt, "affectedPersonNote")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _columnIndexOfIsSos: Int = getColumnIndexOrThrow(_stmt, "isSos")
        val _columnIndexOfSosBridgeToken: Int = getColumnIndexOrThrow(_stmt, "sosBridgeToken")
        val _columnIndexOfAssigneeId: Int = getColumnIndexOrThrow(_stmt, "assigneeId")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfLastSyncAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSyncAttemptEpochMillis")
        val _columnIndexOfSyncAttemptCount: Int = getColumnIndexOrThrow(_stmt, "syncAttemptCount")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _result: MutableList<IncidentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: IncidentEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          }
          val _tmpLocationAccuracyMeters: Float?
          if (_stmt.isNull(_columnIndexOfLocationAccuracyMeters)) {
            _tmpLocationAccuracyMeters = null
          } else {
            _tmpLocationAccuracyMeters = _stmt.getDouble(_columnIndexOfLocationAccuracyMeters).toFloat()
          }
          val _tmpLocationIsApproximate: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLocationIsApproximate).toInt()
          _tmpLocationIsApproximate = _tmp != 0
          val _tmpReporterId: String
          _tmpReporterId = _stmt.getText(_columnIndexOfReporterId)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpPhotoRemotePath: String?
          if (_stmt.isNull(_columnIndexOfPhotoRemotePath)) {
            _tmpPhotoRemotePath = null
          } else {
            _tmpPhotoRemotePath = _stmt.getText(_columnIndexOfPhotoRemotePath)
          }
          val _tmpAffectedPersonNote: String?
          if (_stmt.isNull(_columnIndexOfAffectedPersonNote)) {
            _tmpAffectedPersonNote = null
          } else {
            _tmpAffectedPersonNote = _stmt.getText(_columnIndexOfAffectedPersonNote)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpPriority: String
          _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          val _tmpIsSos: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsSos).toInt()
          _tmpIsSos = _tmp_1 != 0
          val _tmpSosBridgeToken: String?
          if (_stmt.isNull(_columnIndexOfSosBridgeToken)) {
            _tmpSosBridgeToken = null
          } else {
            _tmpSosBridgeToken = _stmt.getText(_columnIndexOfSosBridgeToken)
          }
          val _tmpAssigneeId: String?
          if (_stmt.isNull(_columnIndexOfAssigneeId)) {
            _tmpAssigneeId = null
          } else {
            _tmpAssigneeId = _stmt.getText(_columnIndexOfAssigneeId)
          }
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
          val _tmpLastSyncAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSyncAttemptEpochMillis)) {
            _tmpLastSyncAttemptEpochMillis = null
          } else {
            _tmpLastSyncAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastSyncAttemptEpochMillis)
          }
          val _tmpSyncAttemptCount: Int
          _tmpSyncAttemptCount = _stmt.getLong(_columnIndexOfSyncAttemptCount).toInt()
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          _item = IncidentEntity(_tmpClientId,_tmpServerId,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpLocationAccuracyMeters,_tmpLocationIsApproximate,_tmpReporterId,_tmpReportedAtEpochMillis,_tmpPhotoLocalPath,_tmpPhotoRemotePath,_tmpAffectedPersonNote,_tmpStatus,_tmpPriority,_tmpSyncState,_tmpIsSos,_tmpSosBridgeToken,_tmpAssigneeId,_tmpAreaId,_tmpOrganisationId,_tmpLastSyncAttemptEpochMillis,_tmpSyncAttemptCount,_tmpUpdatedAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeByClientId(clientId: String): Flow<IncidentEntity?> {
    val _sql: String = "SELECT * FROM incidents WHERE clientId = ?"
    return createFlow(__db, false, arrayOf("incidents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, clientId)
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationAccuracyMeters: Int = getColumnIndexOrThrow(_stmt, "locationAccuracyMeters")
        val _columnIndexOfLocationIsApproximate: Int = getColumnIndexOrThrow(_stmt, "locationIsApproximate")
        val _columnIndexOfReporterId: Int = getColumnIndexOrThrow(_stmt, "reporterId")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfPhotoRemotePath: Int = getColumnIndexOrThrow(_stmt, "photoRemotePath")
        val _columnIndexOfAffectedPersonNote: Int = getColumnIndexOrThrow(_stmt, "affectedPersonNote")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _columnIndexOfIsSos: Int = getColumnIndexOrThrow(_stmt, "isSos")
        val _columnIndexOfSosBridgeToken: Int = getColumnIndexOrThrow(_stmt, "sosBridgeToken")
        val _columnIndexOfAssigneeId: Int = getColumnIndexOrThrow(_stmt, "assigneeId")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfLastSyncAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSyncAttemptEpochMillis")
        val _columnIndexOfSyncAttemptCount: Int = getColumnIndexOrThrow(_stmt, "syncAttemptCount")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _result: IncidentEntity?
        if (_stmt.step()) {
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          }
          val _tmpLocationAccuracyMeters: Float?
          if (_stmt.isNull(_columnIndexOfLocationAccuracyMeters)) {
            _tmpLocationAccuracyMeters = null
          } else {
            _tmpLocationAccuracyMeters = _stmt.getDouble(_columnIndexOfLocationAccuracyMeters).toFloat()
          }
          val _tmpLocationIsApproximate: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLocationIsApproximate).toInt()
          _tmpLocationIsApproximate = _tmp != 0
          val _tmpReporterId: String
          _tmpReporterId = _stmt.getText(_columnIndexOfReporterId)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpPhotoRemotePath: String?
          if (_stmt.isNull(_columnIndexOfPhotoRemotePath)) {
            _tmpPhotoRemotePath = null
          } else {
            _tmpPhotoRemotePath = _stmt.getText(_columnIndexOfPhotoRemotePath)
          }
          val _tmpAffectedPersonNote: String?
          if (_stmt.isNull(_columnIndexOfAffectedPersonNote)) {
            _tmpAffectedPersonNote = null
          } else {
            _tmpAffectedPersonNote = _stmt.getText(_columnIndexOfAffectedPersonNote)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpPriority: String
          _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          val _tmpIsSos: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsSos).toInt()
          _tmpIsSos = _tmp_1 != 0
          val _tmpSosBridgeToken: String?
          if (_stmt.isNull(_columnIndexOfSosBridgeToken)) {
            _tmpSosBridgeToken = null
          } else {
            _tmpSosBridgeToken = _stmt.getText(_columnIndexOfSosBridgeToken)
          }
          val _tmpAssigneeId: String?
          if (_stmt.isNull(_columnIndexOfAssigneeId)) {
            _tmpAssigneeId = null
          } else {
            _tmpAssigneeId = _stmt.getText(_columnIndexOfAssigneeId)
          }
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
          val _tmpLastSyncAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSyncAttemptEpochMillis)) {
            _tmpLastSyncAttemptEpochMillis = null
          } else {
            _tmpLastSyncAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastSyncAttemptEpochMillis)
          }
          val _tmpSyncAttemptCount: Int
          _tmpSyncAttemptCount = _stmt.getLong(_columnIndexOfSyncAttemptCount).toInt()
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          _result = IncidentEntity(_tmpClientId,_tmpServerId,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpLocationAccuracyMeters,_tmpLocationIsApproximate,_tmpReporterId,_tmpReportedAtEpochMillis,_tmpPhotoLocalPath,_tmpPhotoRemotePath,_tmpAffectedPersonNote,_tmpStatus,_tmpPriority,_tmpSyncState,_tmpIsSos,_tmpSosBridgeToken,_tmpAssigneeId,_tmpAreaId,_tmpOrganisationId,_tmpLastSyncAttemptEpochMillis,_tmpSyncAttemptCount,_tmpUpdatedAtEpochMillis)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByClientId(clientId: String): IncidentEntity? {
    val _sql: String = "SELECT * FROM incidents WHERE clientId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, clientId)
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationAccuracyMeters: Int = getColumnIndexOrThrow(_stmt, "locationAccuracyMeters")
        val _columnIndexOfLocationIsApproximate: Int = getColumnIndexOrThrow(_stmt, "locationIsApproximate")
        val _columnIndexOfReporterId: Int = getColumnIndexOrThrow(_stmt, "reporterId")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfPhotoRemotePath: Int = getColumnIndexOrThrow(_stmt, "photoRemotePath")
        val _columnIndexOfAffectedPersonNote: Int = getColumnIndexOrThrow(_stmt, "affectedPersonNote")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _columnIndexOfIsSos: Int = getColumnIndexOrThrow(_stmt, "isSos")
        val _columnIndexOfSosBridgeToken: Int = getColumnIndexOrThrow(_stmt, "sosBridgeToken")
        val _columnIndexOfAssigneeId: Int = getColumnIndexOrThrow(_stmt, "assigneeId")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfLastSyncAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSyncAttemptEpochMillis")
        val _columnIndexOfSyncAttemptCount: Int = getColumnIndexOrThrow(_stmt, "syncAttemptCount")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _result: IncidentEntity?
        if (_stmt.step()) {
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          }
          val _tmpLocationAccuracyMeters: Float?
          if (_stmt.isNull(_columnIndexOfLocationAccuracyMeters)) {
            _tmpLocationAccuracyMeters = null
          } else {
            _tmpLocationAccuracyMeters = _stmt.getDouble(_columnIndexOfLocationAccuracyMeters).toFloat()
          }
          val _tmpLocationIsApproximate: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLocationIsApproximate).toInt()
          _tmpLocationIsApproximate = _tmp != 0
          val _tmpReporterId: String
          _tmpReporterId = _stmt.getText(_columnIndexOfReporterId)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpPhotoRemotePath: String?
          if (_stmt.isNull(_columnIndexOfPhotoRemotePath)) {
            _tmpPhotoRemotePath = null
          } else {
            _tmpPhotoRemotePath = _stmt.getText(_columnIndexOfPhotoRemotePath)
          }
          val _tmpAffectedPersonNote: String?
          if (_stmt.isNull(_columnIndexOfAffectedPersonNote)) {
            _tmpAffectedPersonNote = null
          } else {
            _tmpAffectedPersonNote = _stmt.getText(_columnIndexOfAffectedPersonNote)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpPriority: String
          _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          val _tmpIsSos: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsSos).toInt()
          _tmpIsSos = _tmp_1 != 0
          val _tmpSosBridgeToken: String?
          if (_stmt.isNull(_columnIndexOfSosBridgeToken)) {
            _tmpSosBridgeToken = null
          } else {
            _tmpSosBridgeToken = _stmt.getText(_columnIndexOfSosBridgeToken)
          }
          val _tmpAssigneeId: String?
          if (_stmt.isNull(_columnIndexOfAssigneeId)) {
            _tmpAssigneeId = null
          } else {
            _tmpAssigneeId = _stmt.getText(_columnIndexOfAssigneeId)
          }
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
          val _tmpLastSyncAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSyncAttemptEpochMillis)) {
            _tmpLastSyncAttemptEpochMillis = null
          } else {
            _tmpLastSyncAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastSyncAttemptEpochMillis)
          }
          val _tmpSyncAttemptCount: Int
          _tmpSyncAttemptCount = _stmt.getLong(_columnIndexOfSyncAttemptCount).toInt()
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          _result = IncidentEntity(_tmpClientId,_tmpServerId,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpLocationAccuracyMeters,_tmpLocationIsApproximate,_tmpReporterId,_tmpReportedAtEpochMillis,_tmpPhotoLocalPath,_tmpPhotoRemotePath,_tmpAffectedPersonNote,_tmpStatus,_tmpPriority,_tmpSyncState,_tmpIsSos,_tmpSosBridgeToken,_tmpAssigneeId,_tmpAreaId,_tmpOrganisationId,_tmpLastSyncAttemptEpochMillis,_tmpSyncAttemptCount,_tmpUpdatedAtEpochMillis)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByServerId(serverId: String): IncidentEntity? {
    val _sql: String = "SELECT * FROM incidents WHERE serverId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, serverId)
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationAccuracyMeters: Int = getColumnIndexOrThrow(_stmt, "locationAccuracyMeters")
        val _columnIndexOfLocationIsApproximate: Int = getColumnIndexOrThrow(_stmt, "locationIsApproximate")
        val _columnIndexOfReporterId: Int = getColumnIndexOrThrow(_stmt, "reporterId")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfPhotoRemotePath: Int = getColumnIndexOrThrow(_stmt, "photoRemotePath")
        val _columnIndexOfAffectedPersonNote: Int = getColumnIndexOrThrow(_stmt, "affectedPersonNote")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _columnIndexOfIsSos: Int = getColumnIndexOrThrow(_stmt, "isSos")
        val _columnIndexOfSosBridgeToken: Int = getColumnIndexOrThrow(_stmt, "sosBridgeToken")
        val _columnIndexOfAssigneeId: Int = getColumnIndexOrThrow(_stmt, "assigneeId")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfLastSyncAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSyncAttemptEpochMillis")
        val _columnIndexOfSyncAttemptCount: Int = getColumnIndexOrThrow(_stmt, "syncAttemptCount")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _result: IncidentEntity?
        if (_stmt.step()) {
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          }
          val _tmpLocationAccuracyMeters: Float?
          if (_stmt.isNull(_columnIndexOfLocationAccuracyMeters)) {
            _tmpLocationAccuracyMeters = null
          } else {
            _tmpLocationAccuracyMeters = _stmt.getDouble(_columnIndexOfLocationAccuracyMeters).toFloat()
          }
          val _tmpLocationIsApproximate: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLocationIsApproximate).toInt()
          _tmpLocationIsApproximate = _tmp != 0
          val _tmpReporterId: String
          _tmpReporterId = _stmt.getText(_columnIndexOfReporterId)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpPhotoRemotePath: String?
          if (_stmt.isNull(_columnIndexOfPhotoRemotePath)) {
            _tmpPhotoRemotePath = null
          } else {
            _tmpPhotoRemotePath = _stmt.getText(_columnIndexOfPhotoRemotePath)
          }
          val _tmpAffectedPersonNote: String?
          if (_stmt.isNull(_columnIndexOfAffectedPersonNote)) {
            _tmpAffectedPersonNote = null
          } else {
            _tmpAffectedPersonNote = _stmt.getText(_columnIndexOfAffectedPersonNote)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpPriority: String
          _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          val _tmpIsSos: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsSos).toInt()
          _tmpIsSos = _tmp_1 != 0
          val _tmpSosBridgeToken: String?
          if (_stmt.isNull(_columnIndexOfSosBridgeToken)) {
            _tmpSosBridgeToken = null
          } else {
            _tmpSosBridgeToken = _stmt.getText(_columnIndexOfSosBridgeToken)
          }
          val _tmpAssigneeId: String?
          if (_stmt.isNull(_columnIndexOfAssigneeId)) {
            _tmpAssigneeId = null
          } else {
            _tmpAssigneeId = _stmt.getText(_columnIndexOfAssigneeId)
          }
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
          val _tmpLastSyncAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSyncAttemptEpochMillis)) {
            _tmpLastSyncAttemptEpochMillis = null
          } else {
            _tmpLastSyncAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastSyncAttemptEpochMillis)
          }
          val _tmpSyncAttemptCount: Int
          _tmpSyncAttemptCount = _stmt.getLong(_columnIndexOfSyncAttemptCount).toInt()
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          _result = IncidentEntity(_tmpClientId,_tmpServerId,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpLocationAccuracyMeters,_tmpLocationIsApproximate,_tmpReporterId,_tmpReportedAtEpochMillis,_tmpPhotoLocalPath,_tmpPhotoRemotePath,_tmpAffectedPersonNote,_tmpStatus,_tmpPriority,_tmpSyncState,_tmpIsSos,_tmpSosBridgeToken,_tmpAssigneeId,_tmpAreaId,_tmpOrganisationId,_tmpLastSyncAttemptEpochMillis,_tmpSyncAttemptCount,_tmpUpdatedAtEpochMillis)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPendingSync(): List<IncidentEntity> {
    val _sql: String = "SELECT * FROM incidents WHERE syncState IN ('PENDING', 'FAILED') ORDER BY reportedAtEpochMillis ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationAccuracyMeters: Int = getColumnIndexOrThrow(_stmt, "locationAccuracyMeters")
        val _columnIndexOfLocationIsApproximate: Int = getColumnIndexOrThrow(_stmt, "locationIsApproximate")
        val _columnIndexOfReporterId: Int = getColumnIndexOrThrow(_stmt, "reporterId")
        val _columnIndexOfReportedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "reportedAtEpochMillis")
        val _columnIndexOfPhotoLocalPath: Int = getColumnIndexOrThrow(_stmt, "photoLocalPath")
        val _columnIndexOfPhotoRemotePath: Int = getColumnIndexOrThrow(_stmt, "photoRemotePath")
        val _columnIndexOfAffectedPersonNote: Int = getColumnIndexOrThrow(_stmt, "affectedPersonNote")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfPriority: Int = getColumnIndexOrThrow(_stmt, "priority")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _columnIndexOfIsSos: Int = getColumnIndexOrThrow(_stmt, "isSos")
        val _columnIndexOfSosBridgeToken: Int = getColumnIndexOrThrow(_stmt, "sosBridgeToken")
        val _columnIndexOfAssigneeId: Int = getColumnIndexOrThrow(_stmt, "assigneeId")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfOrganisationId: Int = getColumnIndexOrThrow(_stmt, "organisationId")
        val _columnIndexOfLastSyncAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastSyncAttemptEpochMillis")
        val _columnIndexOfSyncAttemptCount: Int = getColumnIndexOrThrow(_stmt, "syncAttemptCount")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _result: MutableList<IncidentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: IncidentEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpLatitude: Double?
          if (_stmt.isNull(_columnIndexOfLatitude)) {
            _tmpLatitude = null
          } else {
            _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          }
          val _tmpLongitude: Double?
          if (_stmt.isNull(_columnIndexOfLongitude)) {
            _tmpLongitude = null
          } else {
            _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          }
          val _tmpLocationAccuracyMeters: Float?
          if (_stmt.isNull(_columnIndexOfLocationAccuracyMeters)) {
            _tmpLocationAccuracyMeters = null
          } else {
            _tmpLocationAccuracyMeters = _stmt.getDouble(_columnIndexOfLocationAccuracyMeters).toFloat()
          }
          val _tmpLocationIsApproximate: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfLocationIsApproximate).toInt()
          _tmpLocationIsApproximate = _tmp != 0
          val _tmpReporterId: String
          _tmpReporterId = _stmt.getText(_columnIndexOfReporterId)
          val _tmpReportedAtEpochMillis: Long
          _tmpReportedAtEpochMillis = _stmt.getLong(_columnIndexOfReportedAtEpochMillis)
          val _tmpPhotoLocalPath: String?
          if (_stmt.isNull(_columnIndexOfPhotoLocalPath)) {
            _tmpPhotoLocalPath = null
          } else {
            _tmpPhotoLocalPath = _stmt.getText(_columnIndexOfPhotoLocalPath)
          }
          val _tmpPhotoRemotePath: String?
          if (_stmt.isNull(_columnIndexOfPhotoRemotePath)) {
            _tmpPhotoRemotePath = null
          } else {
            _tmpPhotoRemotePath = _stmt.getText(_columnIndexOfPhotoRemotePath)
          }
          val _tmpAffectedPersonNote: String?
          if (_stmt.isNull(_columnIndexOfAffectedPersonNote)) {
            _tmpAffectedPersonNote = null
          } else {
            _tmpAffectedPersonNote = _stmt.getText(_columnIndexOfAffectedPersonNote)
          }
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpPriority: String
          _tmpPriority = _stmt.getText(_columnIndexOfPriority)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          val _tmpIsSos: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsSos).toInt()
          _tmpIsSos = _tmp_1 != 0
          val _tmpSosBridgeToken: String?
          if (_stmt.isNull(_columnIndexOfSosBridgeToken)) {
            _tmpSosBridgeToken = null
          } else {
            _tmpSosBridgeToken = _stmt.getText(_columnIndexOfSosBridgeToken)
          }
          val _tmpAssigneeId: String?
          if (_stmt.isNull(_columnIndexOfAssigneeId)) {
            _tmpAssigneeId = null
          } else {
            _tmpAssigneeId = _stmt.getText(_columnIndexOfAssigneeId)
          }
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
          val _tmpLastSyncAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastSyncAttemptEpochMillis)) {
            _tmpLastSyncAttemptEpochMillis = null
          } else {
            _tmpLastSyncAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastSyncAttemptEpochMillis)
          }
          val _tmpSyncAttemptCount: Int
          _tmpSyncAttemptCount = _stmt.getLong(_columnIndexOfSyncAttemptCount).toInt()
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          _item = IncidentEntity(_tmpClientId,_tmpServerId,_tmpCategory,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpLocationAccuracyMeters,_tmpLocationIsApproximate,_tmpReporterId,_tmpReportedAtEpochMillis,_tmpPhotoLocalPath,_tmpPhotoRemotePath,_tmpAffectedPersonNote,_tmpStatus,_tmpPriority,_tmpSyncState,_tmpIsSos,_tmpSosBridgeToken,_tmpAssigneeId,_tmpAreaId,_tmpOrganisationId,_tmpLastSyncAttemptEpochMillis,_tmpSyncAttemptCount,_tmpUpdatedAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeUnsyncedCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM incidents WHERE syncState IN ('PENDING', 'FAILED')"
    return createFlow(__db, false, arrayOf("incidents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markSyncAttempt(
    clientId: String,
    syncState: String,
    attemptedAt: Long,
  ) {
    val _sql: String = "UPDATE incidents SET syncState = ?, lastSyncAttemptEpochMillis = ?, syncAttemptCount = syncAttemptCount + 1 WHERE clientId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, syncState)
        _argIndex = 2
        _stmt.bindLong(_argIndex, attemptedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, clientId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markSynced(
    clientId: String,
    serverId: String,
    status: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE incidents SET serverId = ?, status = ?, syncState = 'SYNCED', updatedAtEpochMillis = ? WHERE clientId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, serverId)
        _argIndex = 2
        _stmt.bindText(_argIndex, status)
        _argIndex = 3
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 4
        _stmt.bindText(_argIndex, clientId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setSyncState(clientId: String, syncState: String) {
    val _sql: String = "UPDATE incidents SET syncState = ? WHERE clientId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, syncState)
        _argIndex = 2
        _stmt.bindText(_argIndex, clientId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setStatus(
    clientId: String,
    status: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE incidents SET status = ?, updatedAtEpochMillis = ? WHERE clientId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, status)
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, clientId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setPriority(
    clientId: String,
    priority: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE incidents SET priority = ?, updatedAtEpochMillis = ? WHERE clientId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, priority)
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, clientId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM incidents"
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
