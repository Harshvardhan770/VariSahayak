package com.varisahayak.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.varisahayak.`data`.local.entity.DocumentEntity
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
public class DocumentDao_Impl(
  __db: RoomDatabase,
) : DocumentDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfDocumentEntity: EntityUpsertAdapter<DocumentEntity>
  init {
    this.__db = __db
    this.__upsertAdapterOfDocumentEntity = EntityUpsertAdapter<DocumentEntity>(object : EntityInsertAdapter<DocumentEntity>() {
      protected override fun createQuery(): String = "INSERT INTO `documents` (`documentId`,`title`,`bodyMarkdown`,`languageTag`,`version`,`areaId`,`updatedAtEpochMillis`,`cachedAtEpochMillis`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DocumentEntity) {
        statement.bindText(1, entity.documentId)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.bodyMarkdown)
        statement.bindText(4, entity.languageTag)
        statement.bindLong(5, entity.version.toLong())
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpAreaId)
        }
        statement.bindLong(7, entity.updatedAtEpochMillis)
        statement.bindLong(8, entity.cachedAtEpochMillis)
      }
    }, object : EntityDeleteOrUpdateAdapter<DocumentEntity>() {
      protected override fun createQuery(): String = "UPDATE `documents` SET `documentId` = ?,`title` = ?,`bodyMarkdown` = ?,`languageTag` = ?,`version` = ?,`areaId` = ?,`updatedAtEpochMillis` = ?,`cachedAtEpochMillis` = ? WHERE `documentId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: DocumentEntity) {
        statement.bindText(1, entity.documentId)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.bodyMarkdown)
        statement.bindText(4, entity.languageTag)
        statement.bindLong(5, entity.version.toLong())
        val _tmpAreaId: String? = entity.areaId
        if (_tmpAreaId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpAreaId)
        }
        statement.bindLong(7, entity.updatedAtEpochMillis)
        statement.bindLong(8, entity.cachedAtEpochMillis)
        statement.bindText(9, entity.documentId)
      }
    })
  }

  public override suspend fun upsert(document: DocumentEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfDocumentEntity.upsert(_connection, document)
  }

  public override fun observeByLanguage(languageTag: String): Flow<List<DocumentEntity>> {
    val _sql: String = "SELECT * FROM documents WHERE languageTag = ? ORDER BY title ASC"
    return createFlow(__db, false, arrayOf("documents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, languageTag)
        val _columnIndexOfDocumentId: Int = getColumnIndexOrThrow(_stmt, "documentId")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfBodyMarkdown: Int = getColumnIndexOrThrow(_stmt, "bodyMarkdown")
        val _columnIndexOfLanguageTag: Int = getColumnIndexOrThrow(_stmt, "languageTag")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _columnIndexOfCachedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "cachedAtEpochMillis")
        val _result: MutableList<DocumentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DocumentEntity
          val _tmpDocumentId: String
          _tmpDocumentId = _stmt.getText(_columnIndexOfDocumentId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpBodyMarkdown: String
          _tmpBodyMarkdown = _stmt.getText(_columnIndexOfBodyMarkdown)
          val _tmpLanguageTag: String
          _tmpLanguageTag = _stmt.getText(_columnIndexOfLanguageTag)
          val _tmpVersion: Int
          _tmpVersion = _stmt.getLong(_columnIndexOfVersion).toInt()
          val _tmpAreaId: String?
          if (_stmt.isNull(_columnIndexOfAreaId)) {
            _tmpAreaId = null
          } else {
            _tmpAreaId = _stmt.getText(_columnIndexOfAreaId)
          }
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          val _tmpCachedAtEpochMillis: Long
          _tmpCachedAtEpochMillis = _stmt.getLong(_columnIndexOfCachedAtEpochMillis)
          _item = DocumentEntity(_tmpDocumentId,_tmpTitle,_tmpBodyMarkdown,_tmpLanguageTag,_tmpVersion,_tmpAreaId,_tmpUpdatedAtEpochMillis,_tmpCachedAtEpochMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeOne(documentId: String, languageTag: String): Flow<DocumentEntity?> {
    val _sql: String = "SELECT * FROM documents WHERE documentId = ? AND languageTag = ?"
    return createFlow(__db, false, arrayOf("documents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, documentId)
        _argIndex = 2
        _stmt.bindText(_argIndex, languageTag)
        val _columnIndexOfDocumentId: Int = getColumnIndexOrThrow(_stmt, "documentId")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfBodyMarkdown: Int = getColumnIndexOrThrow(_stmt, "bodyMarkdown")
        val _columnIndexOfLanguageTag: Int = getColumnIndexOrThrow(_stmt, "languageTag")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfAreaId: Int = getColumnIndexOrThrow(_stmt, "areaId")
        val _columnIndexOfUpdatedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "updatedAtEpochMillis")
        val _columnIndexOfCachedAtEpochMillis: Int = getColumnIndexOrThrow(_stmt, "cachedAtEpochMillis")
        val _result: DocumentEntity?
        if (_stmt.step()) {
          val _tmpDocumentId: String
          _tmpDocumentId = _stmt.getText(_columnIndexOfDocumentId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpBodyMarkdown: String
          _tmpBodyMarkdown = _stmt.getText(_columnIndexOfBodyMarkdown)
          val _tmpLanguageTag: String
          _tmpLanguageTag = _stmt.getText(_columnIndexOfLanguageTag)
          val _tmpVersion: Int
          _tmpVersion = _stmt.getLong(_columnIndexOfVersion).toInt()
          val _tmpAreaId: String?
          if (_stmt.isNull(_columnIndexOfAreaId)) {
            _tmpAreaId = null
          } else {
            _tmpAreaId = _stmt.getText(_columnIndexOfAreaId)
          }
          val _tmpUpdatedAtEpochMillis: Long
          _tmpUpdatedAtEpochMillis = _stmt.getLong(_columnIndexOfUpdatedAtEpochMillis)
          val _tmpCachedAtEpochMillis: Long
          _tmpCachedAtEpochMillis = _stmt.getLong(_columnIndexOfCachedAtEpochMillis)
          _result = DocumentEntity(_tmpDocumentId,_tmpTitle,_tmpBodyMarkdown,_tmpLanguageTag,_tmpVersion,_tmpAreaId,_tmpUpdatedAtEpochMillis,_tmpCachedAtEpochMillis)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getVersion(documentId: String, languageTag: String): Int? {
    val _sql: String = "SELECT version FROM documents WHERE documentId = ? AND languageTag = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, documentId)
        _argIndex = 2
        _stmt.bindText(_argIndex, languageTag)
        val _result: Int?
        if (_stmt.step()) {
          if (_stmt.isNull(0)) {
            _result = null
          } else {
            _result = _stmt.getLong(0).toInt()
          }
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
    val _sql: String = "DELETE FROM documents"
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
