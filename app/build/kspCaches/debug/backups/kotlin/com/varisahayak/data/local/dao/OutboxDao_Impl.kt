package com.varisahayak.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.OutboxEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class OutboxDao_Impl(
  __db: RoomDatabase,
) : OutboxDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfOutboxEntity: EntityInsertAdapter<OutboxEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfOutboxEntity = object : EntityInsertAdapter<OutboxEntity>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `outbox` (`id`,`operation`,`dedupeKey`,`payloadJson`,`createdAtEpochMillis`,`attemptCount`,`lastAttemptEpochMillis`,`lastError`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: OutboxEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.operation)
        statement.bindText(3, entity.dedupeKey)
        statement.bindText(4, entity.payloadJson)
        statement.bindLong(5, entity.createdAtEpochMillis)
        statement.bindLong(6, entity.attemptCount.toLong())
        val _tmpLastAttemptEpochMillis: Long? = entity.lastAttemptEpochMillis
        if (_tmpLastAttemptEpochMillis == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpLastAttemptEpochMillis)
        }
        val _tmpLastError: String? = entity.lastError
        if (_tmpLastError == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpLastError)
        }
      }
    }
  }

  public override suspend fun enqueue(entry: OutboxEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfOutboxEntity.insertAndReturnId(_connection, entry)
    _result
  }

  public override suspend fun peek(limit: Int): List<OutboxEntity> {
    val _sql: String = "SELECT * FROM outbox ORDER BY createdAtEpochMillis ASC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfOperation: Int = getColumnIndexOrThrow(_stmt, "operation")
        val _columnIndexOfDedupeKey: Int = getColumnIndexOrThrow(_stmt, "dedupeKey")
        val _columnIndexOfPayloadJson: Int = getColumnIndexOrThrow(_stmt, "payloadJson")
        val _columnIndexOfCreatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "createdAtEpochMillis")
        val _columnIndexOfAttemptCount: Int = getColumnIndexOrThrow(_stmt, "attemptCount")
        val _columnIndexOfLastAttemptEpochMillis: Int = getColumnIndexOrThrow(_stmt, "lastAttemptEpochMillis")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "lastError")
        val _result: MutableList<OutboxEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: OutboxEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpOperation: String
          _tmpOperation = _stmt.getText(_columnIndexOfOperation)
          val _tmpDedupeKey: String
          _tmpDedupeKey = _stmt.getText(_columnIndexOfDedupeKey)
          val _tmpPayloadJson: String
          _tmpPayloadJson = _stmt.getText(_columnIndexOfPayloadJson)
          val _tmpCreatedAtEpochMillis: Long
          _tmpCreatedAtEpochMillis = _stmt.getLong(_columnIndexOfCreatedAtEpochMillis)
          val _tmpAttemptCount: Int
          _tmpAttemptCount = _stmt.getLong(_columnIndexOfAttemptCount).toInt()
          val _tmpLastAttemptEpochMillis: Long?
          if (_stmt.isNull(_columnIndexOfLastAttemptEpochMillis)) {
            _tmpLastAttemptEpochMillis = null
          } else {
            _tmpLastAttemptEpochMillis = _stmt.getLong(_columnIndexOfLastAttemptEpochMillis)
          }
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          _item = OutboxEntity(_tmpId,_tmpOperation,_tmpDedupeKey,_tmpPayloadJson,_tmpCreatedAtEpochMillis,_tmpAttemptCount,_tmpLastAttemptEpochMillis,_tmpLastError)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observePendingCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM outbox"
    return createFlow(__db, false, arrayOf("outbox")) { _connection ->
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

  public override suspend fun remove(id: Long) {
    val _sql: String = "DELETE FROM outbox WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun recordFailure(
    id: Long,
    at: Long,
    error: String?,
  ) {
    val _sql: String = "UPDATE outbox SET attemptCount = attemptCount + 1, lastAttemptEpochMillis = ?, lastError = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, at)
        _argIndex = 2
        if (error == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, error)
        }
        _argIndex = 3
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM outbox"
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
