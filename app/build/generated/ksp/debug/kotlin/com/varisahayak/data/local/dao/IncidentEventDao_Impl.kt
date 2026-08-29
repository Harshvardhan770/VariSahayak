package com.varisahayak.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.IncidentEventEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class IncidentEventDao_Impl(
  __db: RoomDatabase,
) : IncidentEventDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfIncidentEventEntity: EntityUpsertAdapter<IncidentEventEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfIncidentEventEntity = EntityUpsertAdapter<IncidentEventEntity>(object : EntityInsertAdapter<IncidentEventEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `incident_events` (`eventId`,`incidentClientId`,`incidentServerId`,`type`,`actorId`,`fromValue`,`toValue`,`note`,`occurredAtEpochMillis`,`synced`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: IncidentEventEntity) {
        statement.bindText(1, entity.eventId)
        statement.bindText(2, entity.incidentClientId)
        val _tmpIncidentServerId: String? = entity.incidentServerId
        if (_tmpIncidentServerId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpIncidentServerId)
        }
        statement.bindText(4, entity.type)
        val _tmpActorId: String? = entity.actorId
        if (_tmpActorId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpActorId)
        }
        val _tmpFromValue: String? = entity.fromValue
        if (_tmpFromValue == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpFromValue)
        }
        val _tmpToValue: String? = entity.toValue
        if (_tmpToValue == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpToValue)
        }
        val _tmpNote: String? = entity.note
        if (_tmpNote == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpNote)
        }
        statement.bindLong(9, entity.occurredAtEpochMillis)
        val _tmp: Int = if (entity.synced) 1 else 0
        statement.bindLong(10, _tmp.toLong())
      }
    }, object : EntityDeleteOrUpdateAdapter<IncidentEventEntity>() {
      protected override fun createQuery(): String = "UPDATE `incident_events` SET `eventId` = ?,`incidentClientId` = ?,`incidentServerId` = ?,`type` = ?,`actorId` = ?,`fromValue` = ?,`toValue` = ?,`note` = ?,`occurredAtEpochMillis` = ?,`synced` = ? WHERE `eventId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: IncidentEventEntity) {
        statement.bindText(1, entity.eventId)
        statement.bindText(2, entity.incidentClientId)
        val _tmpIncidentServerId: String? = entity.incidentServerId
        if (_tmpIncidentServerId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpIncidentServerId)
        }
        statement.bindText(4, entity.type)
        val _tmpActorId: String? = entity.actorId
        if (_tmpActorId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpActorId)
        }
        val _tmpFromValue: String? = entity.fromValue
        if (_tmpFromValue == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpFromValue)
        }
        val _tmpToValue: String? = entity.toValue
        if (_tmpToValue == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpToValue)
        }
        val _tmpNote: String? = entity.note
        if (_tmpNote == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpNote)
        }
        statement.bindLong(9, entity.occurredAtEpochMillis)
        val _tmp: Int = if (entity.synced) 1 else 0
        statement.bindLong(10, _tmp.toLong())
        statement.bindText(11, entity.eventId)
      }
    })
  }

  public override suspend fun upsert(event: IncidentEventEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfIncidentEventEntity.upsert(_connection, event)
  }

  public override suspend fun upsertAll(events: List<IncidentEventEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfIncidentEventEntity.upsert(_connection, events)
  }

  public override fun observeForIncident(clientId: String): Flow<List<IncidentEventEntity>> {
    val _sql: String = "SELECT * FROM incident_events WHERE incidentClientId = ? ORDER BY occurredAtEpochMillis ASC"
    return createFlow(__db, false, arrayOf("incident_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, clientId)
        val _columnIndexOfEventId: Int = getColumnIndexOrThrow(_stmt, "eventId")
        val _columnIndexOfIncidentClientId: Int = getColumnIndexOrThrow(_stmt, "incidentClientId")
        val _columnIndexOfIncidentServerId: Int = getColumnIndexOrThrow(_stmt, "incidentServerId")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfActorId: Int = getColumnIndexOrThrow(_stmt, "actorId")
        val _columnIndexOfFromValue: Int = getColumnIndexOrThrow(_stmt, "fromValue")
        val _columnIndexOfToValue: Int = getColumnIndexOrThrow(_stmt, "toValue")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfOccurredAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "occurredAtEpochMillis")
        val _columnIndexOfSynced: Int = getColumnIndexOrThrow(_stmt, "synced")
        val _result: MutableList<IncidentEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: IncidentEventEntity
          val _tmpEventId: String
          _tmpEventId = _stmt.getText(_columnIndexOfEventId)
          val _tmpIncidentClientId: String
          _tmpIncidentClientId = _stmt.getText(_columnIndexOfIncidentClientId)
          val _tmpIncidentServerId: String?
          if (_stmt.isNull(_columnIndexOfIncidentServerId)) {
            _tmpIncidentServerId = null
          } else {
            _tmpIncidentServerId = _stmt.getText(_columnIndexOfIncidentServerId)
          }
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpActorId: String?
          if (_stmt.isNull(_columnIndexOfActorId)) {
            _tmpActorId = null
          } else {
            _tmpActorId = _stmt.getText(_columnIndexOfActorId)
          }
          val _tmpFromValue: String?
          if (_stmt.isNull(_columnIndexOfFromValue)) {
            _tmpFromValue = null
          } else {
            _tmpFromValue = _stmt.getText(_columnIndexOfFromValue)
          }
          val _tmpToValue: String?
          if (_stmt.isNull(_columnIndexOfToValue)) {
            _tmpToValue = null
          } else {
            _tmpToValue = _stmt.getText(_columnIndexOfToValue)
          }
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          val _tmpOccurredAtEpochMillis: Long
          _tmpOccurredAtEpochMillis = _stmt.getLong(_columnIndexOfOccurredAtEpochMillis)
          val _tmpSynced: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfSynced).toInt()
          _tmpSynced = _tmp != 0
          _item = IncidentEventEntity(_tmpEventId,_tmpIncidentClientId,_tmpIncidentServerId,_tmpType,_tmpActorId,_tmpFromValue,_tmpToValue,_tmpNote,_tmpOccurredAtEpochMillis,_tmpSynced)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getUnsynced(): List<IncidentEventEntity> {
    val _sql: String = "SELECT * FROM incident_events WHERE synced = 0 ORDER BY occurredAtEpochMillis ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfEventId: Int = getColumnIndexOrThrow(_stmt, "eventId")
        val _columnIndexOfIncidentClientId: Int = getColumnIndexOrThrow(_stmt, "incidentClientId")
        val _columnIndexOfIncidentServerId: Int = getColumnIndexOrThrow(_stmt, "incidentServerId")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfActorId: Int = getColumnIndexOrThrow(_stmt, "actorId")
        val _columnIndexOfFromValue: Int = getColumnIndexOrThrow(_stmt, "fromValue")
        val _columnIndexOfToValue: Int = getColumnIndexOrThrow(_stmt, "toValue")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfOccurredAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "occurredAtEpochMillis")
        val _columnIndexOfSynced: Int = getColumnIndexOrThrow(_stmt, "synced")
        val _result: MutableList<IncidentEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: IncidentEventEntity
          val _tmpEventId: String
          _tmpEventId = _stmt.getText(_columnIndexOfEventId)
          val _tmpIncidentClientId: String
          _tmpIncidentClientId = _stmt.getText(_columnIndexOfIncidentClientId)
          val _tmpIncidentServerId: String?
          if (_stmt.isNull(_columnIndexOfIncidentServerId)) {
            _tmpIncidentServerId = null
          } else {
            _tmpIncidentServerId = _stmt.getText(_columnIndexOfIncidentServerId)
          }
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpActorId: String?
          if (_stmt.isNull(_columnIndexOfActorId)) {
            _tmpActorId = null
          } else {
            _tmpActorId = _stmt.getText(_columnIndexOfActorId)
          }
          val _tmpFromValue: String?
          if (_stmt.isNull(_columnIndexOfFromValue)) {
            _tmpFromValue = null
          } else {
            _tmpFromValue = _stmt.getText(_columnIndexOfFromValue)
          }
          val _tmpToValue: String?
          if (_stmt.isNull(_columnIndexOfToValue)) {
            _tmpToValue = null
          } else {
            _tmpToValue = _stmt.getText(_columnIndexOfToValue)
          }
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          val _tmpOccurredAtEpochMillis: Long
          _tmpOccurredAtEpochMillis = _stmt.getLong(_columnIndexOfOccurredAtEpochMillis)
          val _tmpSynced: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfSynced).toInt()
          _tmpSynced = _tmp != 0
          _item = IncidentEventEntity(_tmpEventId,_tmpIncidentClientId,_tmpIncidentServerId,_tmpType,_tmpActorId,_tmpFromValue,_tmpToValue,_tmpNote,_tmpOccurredAtEpochMillis,_tmpSynced)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markSynced(eventId: String) {
    val _sql: String = "UPDATE incident_events SET synced = 1 WHERE eventId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, eventId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM incident_events"
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
