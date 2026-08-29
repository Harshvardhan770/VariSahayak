package com.varisahayak.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.MessageEntity
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
public class MessageDao_Impl(
  __db: RoomDatabase,
) : MessageDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfMessageEntity: EntityUpsertAdapter<MessageEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfMessageEntity = EntityUpsertAdapter<MessageEntity>(object : EntityInsertAdapter<MessageEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `messages` (`clientId`,`serverId`,`channelId`,`senderId`,`senderName`,`body`,`sentAtEpochMillis`,`syncState`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindText(1, entity.clientId)
        val _tmpServerId: String? = entity.serverId
        if (_tmpServerId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpServerId)
        }
        statement.bindText(3, entity.channelId)
        statement.bindText(4, entity.senderId)
        val _tmpSenderName: String? = entity.senderName
        if (_tmpSenderName == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpSenderName)
        }
        statement.bindText(6, entity.body)
        statement.bindLong(7, entity.sentAtEpochMillis)
        statement.bindText(8, entity.syncState)
      }
    }, object : EntityDeleteOrUpdateAdapter<MessageEntity>() {
      protected override fun createQuery(): String = "UPDATE `messages` SET `clientId` = ?,`serverId` = ?,`channelId` = ?,`senderId` = ?,`senderName` = ?,`body` = ?,`sentAtEpochMillis` = ?,`syncState` = ? WHERE `clientId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindText(1, entity.clientId)
        val _tmpServerId: String? = entity.serverId
        if (_tmpServerId == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpServerId)
        }
        statement.bindText(3, entity.channelId)
        statement.bindText(4, entity.senderId)
        val _tmpSenderName: String? = entity.senderName
        if (_tmpSenderName == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpSenderName)
        }
        statement.bindText(6, entity.body)
        statement.bindLong(7, entity.sentAtEpochMillis)
        statement.bindText(8, entity.syncState)
        statement.bindText(9, entity.clientId)
      }
    })
  }

  public override suspend fun upsert(message: MessageEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfMessageEntity.upsert(_connection, message)
  }

  public override suspend fun upsertAll(messages: List<MessageEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfMessageEntity.upsert(_connection, messages)
  }

  public override fun observeChannel(channelId: String): Flow<List<MessageEntity>> {
    val _sql: String = "SELECT * FROM messages WHERE channelId = ? ORDER BY sentAtEpochMillis ASC"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, channelId)
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfChannelId: Int = getColumnIndexOrThrow(_stmt, "channelId")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "senderId")
        val _columnIndexOfSenderName: Int = getColumnIndexOrThrow(_stmt, "senderName")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfSentAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "sentAtEpochMillis")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpChannelId: String
          _tmpChannelId = _stmt.getText(_columnIndexOfChannelId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpSenderName: String?
          if (_stmt.isNull(_columnIndexOfSenderName)) {
            _tmpSenderName = null
          } else {
            _tmpSenderName = _stmt.getText(_columnIndexOfSenderName)
          }
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpSentAtEpochMillis: Long
          _tmpSentAtEpochMillis = _stmt.getLong(_columnIndexOfSentAtEpochMillis)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          _item = MessageEntity(_tmpClientId,_tmpServerId,_tmpChannelId,_tmpSenderId,_tmpSenderName,_tmpBody,_tmpSentAtEpochMillis,_tmpSyncState)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPendingSync(): List<MessageEntity> {
    val _sql: String = "SELECT * FROM messages WHERE syncState IN ('PENDING', 'FAILED')"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfClientId: Int = getColumnIndexOrThrow(_stmt, "clientId")
        val _columnIndexOfServerId: Int = getColumnIndexOrThrow(_stmt, "serverId")
        val _columnIndexOfChannelId: Int = getColumnIndexOrThrow(_stmt, "channelId")
        val _columnIndexOfSenderId: Int = getColumnIndexOrThrow(_stmt, "senderId")
        val _columnIndexOfSenderName: Int = getColumnIndexOrThrow(_stmt, "senderName")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfSentAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "sentAtEpochMillis")
        val _columnIndexOfSyncState: Int = getColumnIndexOrThrow(_stmt, "syncState")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpClientId: String
          _tmpClientId = _stmt.getText(_columnIndexOfClientId)
          val _tmpServerId: String?
          if (_stmt.isNull(_columnIndexOfServerId)) {
            _tmpServerId = null
          } else {
            _tmpServerId = _stmt.getText(_columnIndexOfServerId)
          }
          val _tmpChannelId: String
          _tmpChannelId = _stmt.getText(_columnIndexOfChannelId)
          val _tmpSenderId: String
          _tmpSenderId = _stmt.getText(_columnIndexOfSenderId)
          val _tmpSenderName: String?
          if (_stmt.isNull(_columnIndexOfSenderName)) {
            _tmpSenderName = null
          } else {
            _tmpSenderName = _stmt.getText(_columnIndexOfSenderName)
          }
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpSentAtEpochMillis: Long
          _tmpSentAtEpochMillis = _stmt.getLong(_columnIndexOfSentAtEpochMillis)
          val _tmpSyncState: String
          _tmpSyncState = _stmt.getText(_columnIndexOfSyncState)
          _item = MessageEntity(_tmpClientId,_tmpServerId,_tmpChannelId,_tmpSenderId,_tmpSenderName,_tmpBody,_tmpSentAtEpochMillis,_tmpSyncState)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markSynced(clientId: String, serverId: String) {
    val _sql: String = "UPDATE messages SET serverId = ?, syncState = 'SYNCED' WHERE clientId = ?"
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
    val _sql: String = "DELETE FROM messages"
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
