package com.varisahayak.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.NotificationEntity
import javax.`annotation`.processing.Generated
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
public class NotificationDao_Impl(
  __db: RoomDatabase,
) : NotificationDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfNotificationEntity: EntityUpsertAdapter<NotificationEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfNotificationEntity = EntityUpsertAdapter<NotificationEntity>(object : EntityInsertAdapter<NotificationEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `notifications` (`notificationId`,`type`,`title`,`body`,`incidentServerId`,`receivedAtEpochMillis`,`readAtEpochMillis`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: NotificationEntity) {
        statement.bindText(1, entity.notificationId)
        statement.bindText(2, entity.type)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.body)
        val _tmpIncidentServerId: String? = entity.incidentServerId
        if (_tmpIncidentServerId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpIncidentServerId)
        }
        statement.bindLong(6, entity.receivedAtEpochMillis)
        val _tmpReadAtEpochMillis: Long? = entity.readAtEpochMillis
        if (_tmpReadAtEpochMillis == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpReadAtEpochMillis)
        }
      }
    }, object : EntityDeleteOrUpdateAdapter<NotificationEntity>() {
      protected override fun createQuery(): String = "UPDATE `notifications` SET `notificationId` = ?,`type` = ?,`title` = ?,`body` = ?,`incidentServerId` = ?,`receivedAtEpochMillis` = ?,`readAtEpochMillis` = ? WHERE `notificationId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: NotificationEntity) {
        statement.bindText(1, entity.notificationId)
        statement.bindText(2, entity.type)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.body)
        val _tmpIncidentServerId: String? = entity.incidentServerId
        if (_tmpIncidentServerId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpIncidentServerId)
        }
        statement.bindLong(6, entity.receivedAtEpochMillis)
        val _tmpReadAtEpochMillis: Long? = entity.readAtEpochMillis
        if (_tmpReadAtEpochMillis == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpReadAtEpochMillis)
        }
        statement.bindText(8, entity.notificationId)
      }
    })
  }

  public override suspend fun upsert(notification: NotificationEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfNotificationEntity.upsert(_connection, notification)
  }

  public override fun observeAll(): Flow<List<NotificationEntity>> {
    val _sql: String = "SELECT * FROM notifications ORDER BY receivedAtEpochMillis DESC"
    return createFlow(__db, false, arrayOf("notifications")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfNotificationId: Int = getColumnIndexOrThrow(_stmt, "notificationId")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfIncidentServerId: Int = getColumnIndexOrThrow(_stmt, "incidentServerId")
        val _columnIndexOfReceivedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "receivedAtEpochMillis")
        val _columnIndexOfReadAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "readAtEpochMillis")
        val _result: MutableList<NotificationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: NotificationEntity
          val _tmpNotificationId: String
          _tmpNotificationId = _stmt.getText(_columnIndexOfNotificationId)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpIncidentServerId: String?
          if (_stmt.isNull(_columnIndexOfIncidentServerId)) {
            _tmpIncidentServerId = null
          } else {
            _tmpIncidentServerId = _stmt.getText(_columnIndexOfIncidentServerId)
          }
          val _tmpReceivedAtEpochMillis: Long
          _tmpReceivedAtEpochMillis = _stmt.getLong(_columnIndexOfReceivedAtEpochMillis)
          val _tmpReadAtEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfReadAtEpochMillis)) {
            _tmpReadAtEpochMillis = null
          } else {
            _tmpReadAtEpochMillis = _stmt.getLong(_columnIndexOfReadAtEpochMillis)
          }
          _item = NotificationEntity(_tmpNotificationId,_tmpType,_tmpTitle,_tmpBody,_tmpIncidentServerId,_tmpReceivedAtEpochMillis,_tmpReadAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeUnreadCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM notifications WHERE readAtEpochMillis IS NULL"
    return createFlow(__db, false, arrayOf("notifications")) { _connection ->
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

  public override suspend fun markRead(id: String, at: Long) {
    val _sql: String = "UPDATE notifications SET readAtEpochMillis = ? WHERE notificationId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, at)
        _argIndex = 2
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM notifications"
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
