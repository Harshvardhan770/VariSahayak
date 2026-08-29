package com.varisahayak.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.varisahayak.`data`.local.dao.DocumentDao
import com.varisahayak.`data`.local.dao.DocumentDao_Impl
import com.varisahayak.`data`.local.dao.IncidentDao
import com.varisahayak.`data`.local.dao.IncidentDao_Impl
import com.varisahayak.`data`.local.dao.IncidentEventDao
import com.varisahayak.`data`.local.dao.IncidentEventDao_Impl
import com.varisahayak.`data`.local.dao.LostFoundDao
import com.varisahayak.`data`.local.dao.LostFoundDao_Impl
import com.varisahayak.`data`.local.dao.MessageDao
import com.varisahayak.`data`.local.dao.MessageDao_Impl
import com.varisahayak.`data`.local.dao.NotificationDao
import com.varisahayak.`data`.local.dao.NotificationDao_Impl
import com.varisahayak.`data`.local.dao.OutboxDao
import com.varisahayak.`data`.local.dao.OutboxDao_Impl
import com.varisahayak.`data`.local.dao.ProfileDao
import com.varisahayak.`data`.local.dao.ProfileDao_Impl
import com.varisahayak.`data`.local.dao.ResponderDao
import com.varisahayak.`data`.local.dao.ResponderDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class VariSahayakDatabase_Impl : VariSahayakDatabase() {
  private val _incidentDao: Lazy<IncidentDao> = lazy {
    IncidentDao_Impl(this)
  }

  private val _profileDao: Lazy<ProfileDao> = lazy {
    ProfileDao_Impl(this)
  }

  private val _outboxDao: Lazy<OutboxDao> = lazy {
    OutboxDao_Impl(this)
  }

  private val _responderDao: Lazy<ResponderDao> = lazy {
    ResponderDao_Impl(this)
  }

  private val _incidentEventDao: Lazy<IncidentEventDao> = lazy {
    IncidentEventDao_Impl(this)
  }

  private val _documentDao: Lazy<DocumentDao> = lazy {
    DocumentDao_Impl(this)
  }

  private val _notificationDao: Lazy<NotificationDao> = lazy {
    NotificationDao_Impl(this)
  }

  private val _messageDao: Lazy<MessageDao> = lazy {
    MessageDao_Impl(this)
  }

  private val _lostFoundDao: Lazy<LostFoundDao> = lazy {
    LostFoundDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "3662dc204d1e61d84deb2570c9c46c14", "7a8a4cafd777d5a0ec099399920befe7") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `incidents` (`clientId` TEXT NOT NULL, `serverId` TEXT, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `locationAccuracyMeters` REAL, `locationIsApproximate` INTEGER NOT NULL, `reporterId` TEXT NOT NULL, `reportedAtEpochMillis` INTEGER NOT NULL, `photoLocalPath` TEXT, `photoRemotePath` TEXT, `affectedPersonNote` TEXT, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `syncState` TEXT NOT NULL, `isSos` INTEGER NOT NULL, `sosBridgeToken` TEXT, `assigneeId` TEXT, `areaId` TEXT, `organisationId` TEXT, `lastSyncAttemptEpochMillis` INTEGER, `syncAttemptCount` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`clientId`))")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_incidents_serverId` ON `incidents` (`serverId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_syncState` ON `incidents` (`syncState`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_status` ON `incidents` (`status`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_assigneeId` ON `incidents` (`assigneeId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_reportedAtEpochMillis` ON `incidents` (`reportedAtEpochMillis`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `profiles` (`userId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `role` TEXT NOT NULL, `organisationId` TEXT, `organisationName` TEXT, `areaId` TEXT, `areaName` TEXT, `phone` TEXT, `capabilitiesCsv` TEXT NOT NULL, `cachedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operation` TEXT NOT NULL, `dedupeKey` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL, `lastAttemptEpochMillis` INTEGER, `lastError` TEXT)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_dedupeKey` ON `outbox` (`dedupeKey`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_createdAtEpochMillis` ON `outbox` (`createdAtEpochMillis`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `responders` (`userId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `role` TEXT NOT NULL, `availability` TEXT NOT NULL, `areaId` TEXT, `organisationId` TEXT, `capabilitiesCsv` TEXT NOT NULL, `lastLatitude` REAL, `lastLongitude` REAL, `lastLocationAtEpochMillis` INTEGER, `activeAssignmentCount` INTEGER NOT NULL, `cachedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_responders_areaId` ON `responders` (`areaId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_responders_availability` ON `responders` (`availability`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `incident_events` (`eventId` TEXT NOT NULL, `incidentClientId` TEXT NOT NULL, `incidentServerId` TEXT, `type` TEXT NOT NULL, `actorId` TEXT, `fromValue` TEXT, `toValue` TEXT, `note` TEXT, `occurredAtEpochMillis` INTEGER NOT NULL, `synced` INTEGER NOT NULL, PRIMARY KEY(`eventId`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_events_incidentClientId` ON `incident_events` (`incidentClientId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_events_occurredAtEpochMillis` ON `incident_events` (`occurredAtEpochMillis`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `documents` (`documentId` TEXT NOT NULL, `title` TEXT NOT NULL, `bodyMarkdown` TEXT NOT NULL, `languageTag` TEXT NOT NULL, `version` INTEGER NOT NULL, `areaId` TEXT, `updatedAtEpochMillis` INTEGER NOT NULL, `cachedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`documentId`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_languageTag` ON `documents` (`languageTag`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`notificationId` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `incidentServerId` TEXT, `receivedAtEpochMillis` INTEGER NOT NULL, `readAtEpochMillis` INTEGER, PRIMARY KEY(`notificationId`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_receivedAtEpochMillis` ON `notifications` (`receivedAtEpochMillis`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`clientId` TEXT NOT NULL, `serverId` TEXT, `channelId` TEXT NOT NULL, `senderId` TEXT NOT NULL, `senderName` TEXT, `body` TEXT NOT NULL, `sentAtEpochMillis` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`clientId`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_channelId` ON `messages` (`channelId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sentAtEpochMillis` ON `messages` (`sentAtEpochMillis`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `lost_found_items` (`clientId` TEXT NOT NULL, `serverId` TEXT, `incidentClientId` TEXT, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `lastSeenLatitude` REAL, `lastSeenLongitude` REAL, `lastSeenAtEpochMillis` INTEGER, `qrToken` TEXT, `photoLocalPath` TEXT, `status` TEXT NOT NULL, `reportedBy` TEXT NOT NULL, `reportedAtEpochMillis` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`clientId`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_lost_found_items_status` ON `lost_found_items` (`status`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3662dc204d1e61d84deb2570c9c46c14')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `incidents`")
        connection.execSQL("DROP TABLE IF EXISTS `profiles`")
        connection.execSQL("DROP TABLE IF EXISTS `outbox`")
        connection.execSQL("DROP TABLE IF EXISTS `responders`")
        connection.execSQL("DROP TABLE IF EXISTS `incident_events`")
        connection.execSQL("DROP TABLE IF EXISTS `documents`")
        connection.execSQL("DROP TABLE IF EXISTS `notifications`")
        connection.execSQL("DROP TABLE IF EXISTS `messages`")
        connection.execSQL("DROP TABLE IF EXISTS `lost_found_items`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsIncidents: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsIncidents.put("clientId", TableInfo.Column("clientId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("serverId", TableInfo.Column("serverId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("category", TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("description", TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("latitude", TableInfo.Column("latitude", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("longitude", TableInfo.Column("longitude", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("locationAccuracyMeters", TableInfo.Column("locationAccuracyMeters", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("locationIsApproximate", TableInfo.Column("locationIsApproximate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("reporterId", TableInfo.Column("reporterId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("reportedAtEpochMillis", TableInfo.Column("reportedAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("photoLocalPath", TableInfo.Column("photoLocalPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("photoRemotePath", TableInfo.Column("photoRemotePath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("affectedPersonNote", TableInfo.Column("affectedPersonNote", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("priority", TableInfo.Column("priority", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("syncState", TableInfo.Column("syncState", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("isSos", TableInfo.Column("isSos", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("sosBridgeToken", TableInfo.Column("sosBridgeToken", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("assigneeId", TableInfo.Column("assigneeId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("areaId", TableInfo.Column("areaId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("organisationId", TableInfo.Column("organisationId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("lastSyncAttemptEpochMillis", TableInfo.Column("lastSyncAttemptEpochMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("syncAttemptCount", TableInfo.Column("syncAttemptCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidents.put("updatedAtEpochMillis", TableInfo.Column("updatedAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysIncidents: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesIncidents: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesIncidents.add(TableInfo.Index("index_incidents_serverId", true, listOf("serverId"), listOf("ASC")))
        _indicesIncidents.add(TableInfo.Index("index_incidents_syncState", false, listOf("syncState"), listOf("ASC")))
        _indicesIncidents.add(TableInfo.Index("index_incidents_status", false, listOf("status"), listOf("ASC")))
        _indicesIncidents.add(TableInfo.Index("index_incidents_assigneeId", false, listOf("assigneeId"), listOf("ASC")))
        _indicesIncidents.add(TableInfo.Index("index_incidents_reportedAtEpochMillis", false, listOf("reportedAtEpochMillis"), listOf("ASC")))
        val _infoIncidents: TableInfo = TableInfo("incidents", _columnsIncidents, _foreignKeysIncidents, _indicesIncidents)
        val _existingIncidents: TableInfo = read(connection, "incidents")
        if (!_infoIncidents.equals(_existingIncidents)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |incidents(com.varisahayak.data.local.entity.IncidentEntity).
              | Expected:
              |""".trimMargin() + _infoIncidents + """
              |
              | Found:
              |""".trimMargin() + _existingIncidents)
        }
        val _columnsProfiles: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProfiles.put("userId", TableInfo.Column("userId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("displayName", TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("role", TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("organisationId", TableInfo.Column("organisationId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("organisationName", TableInfo.Column("organisationName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("areaId", TableInfo.Column("areaId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("areaName", TableInfo.Column("areaName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("phone", TableInfo.Column("phone", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("capabilitiesCsv", TableInfo.Column("capabilitiesCsv", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfiles.put("cachedAtEpochMillis", TableInfo.Column("cachedAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProfiles: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProfiles: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoProfiles: TableInfo = TableInfo("profiles", _columnsProfiles, _foreignKeysProfiles, _indicesProfiles)
        val _existingProfiles: TableInfo = read(connection, "profiles")
        if (!_infoProfiles.equals(_existingProfiles)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |profiles(com.varisahayak.data.local.entity.ProfileEntity).
              | Expected:
              |""".trimMargin() + _infoProfiles + """
              |
              | Found:
              |""".trimMargin() + _existingProfiles)
        }
        val _columnsOutbox: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsOutbox.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("operation", TableInfo.Column("operation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("dedupeKey", TableInfo.Column("dedupeKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("payloadJson", TableInfo.Column("payloadJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("createdAtEpochMillis", TableInfo.Column("createdAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("attemptCount", TableInfo.Column("attemptCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("lastAttemptEpochMillis", TableInfo.Column("lastAttemptEpochMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOutbox.put("lastError", TableInfo.Column("lastError", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysOutbox: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesOutbox: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesOutbox.add(TableInfo.Index("index_outbox_dedupeKey", true, listOf("dedupeKey"), listOf("ASC")))
        _indicesOutbox.add(TableInfo.Index("index_outbox_createdAtEpochMillis", false, listOf("createdAtEpochMillis"), listOf("ASC")))
        val _infoOutbox: TableInfo = TableInfo("outbox", _columnsOutbox, _foreignKeysOutbox, _indicesOutbox)
        val _existingOutbox: TableInfo = read(connection, "outbox")
        if (!_infoOutbox.equals(_existingOutbox)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |outbox(com.varisahayak.data.local.entity.OutboxEntity).
              | Expected:
              |""".trimMargin() + _infoOutbox + """
              |
              | Found:
              |""".trimMargin() + _existingOutbox)
        }
        val _columnsResponders: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsResponders.put("userId", TableInfo.Column("userId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("displayName", TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("role", TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("availability", TableInfo.Column("availability", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("areaId", TableInfo.Column("areaId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("organisationId", TableInfo.Column("organisationId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("capabilitiesCsv", TableInfo.Column("capabilitiesCsv", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("lastLatitude", TableInfo.Column("lastLatitude", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("lastLongitude", TableInfo.Column("lastLongitude", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("lastLocationAtEpochMillis", TableInfo.Column("lastLocationAtEpochMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("activeAssignmentCount", TableInfo.Column("activeAssignmentCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsResponders.put("cachedAtEpochMillis", TableInfo.Column("cachedAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysResponders: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesResponders: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesResponders.add(TableInfo.Index("index_responders_areaId", false, listOf("areaId"), listOf("ASC")))
        _indicesResponders.add(TableInfo.Index("index_responders_availability", false, listOf("availability"), listOf("ASC")))
        val _infoResponders: TableInfo = TableInfo("responders", _columnsResponders, _foreignKeysResponders, _indicesResponders)
        val _existingResponders: TableInfo = read(connection, "responders")
        if (!_infoResponders.equals(_existingResponders)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |responders(com.varisahayak.data.local.entity.ResponderEntity).
              | Expected:
              |""".trimMargin() + _infoResponders + """
              |
              | Found:
              |""".trimMargin() + _existingResponders)
        }
        val _columnsIncidentEvents: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsIncidentEvents.put("eventId", TableInfo.Column("eventId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("incidentClientId", TableInfo.Column("incidentClientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("incidentServerId", TableInfo.Column("incidentServerId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("actorId", TableInfo.Column("actorId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("fromValue", TableInfo.Column("fromValue", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("toValue", TableInfo.Column("toValue", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("note", TableInfo.Column("note", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("occurredAtEpochMillis", TableInfo.Column("occurredAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsIncidentEvents.put("synced", TableInfo.Column("synced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysIncidentEvents: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesIncidentEvents: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesIncidentEvents.add(TableInfo.Index("index_incident_events_incidentClientId", false, listOf("incidentClientId"), listOf("ASC")))
        _indicesIncidentEvents.add(TableInfo.Index("index_incident_events_occurredAtEpochMillis", false, listOf("occurredAtEpochMillis"), listOf("ASC")))
        val _infoIncidentEvents: TableInfo = TableInfo("incident_events", _columnsIncidentEvents, _foreignKeysIncidentEvents, _indicesIncidentEvents)
        val _existingIncidentEvents: TableInfo = read(connection, "incident_events")
        if (!_infoIncidentEvents.equals(_existingIncidentEvents)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |incident_events(com.varisahayak.data.local.entity.IncidentEventEntity).
              | Expected:
              |""".trimMargin() + _infoIncidentEvents + """
              |
              | Found:
              |""".trimMargin() + _existingIncidentEvents)
        }
        val _columnsDocuments: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDocuments.put("documentId", TableInfo.Column("documentId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("bodyMarkdown", TableInfo.Column("bodyMarkdown", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("languageTag", TableInfo.Column("languageTag", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("version", TableInfo.Column("version", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("areaId", TableInfo.Column("areaId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("updatedAtEpochMillis", TableInfo.Column("updatedAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("cachedAtEpochMillis", TableInfo.Column("cachedAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDocuments: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDocuments: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesDocuments.add(TableInfo.Index("index_documents_languageTag", false, listOf("languageTag"), listOf("ASC")))
        val _infoDocuments: TableInfo = TableInfo("documents", _columnsDocuments, _foreignKeysDocuments, _indicesDocuments)
        val _existingDocuments: TableInfo = read(connection, "documents")
        if (!_infoDocuments.equals(_existingDocuments)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |documents(com.varisahayak.data.local.entity.DocumentEntity).
              | Expected:
              |""".trimMargin() + _infoDocuments + """
              |
              | Found:
              |""".trimMargin() + _existingDocuments)
        }
        val _columnsNotifications: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsNotifications.put("notificationId", TableInfo.Column("notificationId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("body", TableInfo.Column("body", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("incidentServerId", TableInfo.Column("incidentServerId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("receivedAtEpochMillis", TableInfo.Column("receivedAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("readAtEpochMillis", TableInfo.Column("readAtEpochMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysNotifications: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesNotifications: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesNotifications.add(TableInfo.Index("index_notifications_receivedAtEpochMillis", false, listOf("receivedAtEpochMillis"), listOf("ASC")))
        val _infoNotifications: TableInfo = TableInfo("notifications", _columnsNotifications, _foreignKeysNotifications, _indicesNotifications)
        val _existingNotifications: TableInfo = read(connection, "notifications")
        if (!_infoNotifications.equals(_existingNotifications)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |notifications(com.varisahayak.data.local.entity.NotificationEntity).
              | Expected:
              |""".trimMargin() + _infoNotifications + """
              |
              | Found:
              |""".trimMargin() + _existingNotifications)
        }
        val _columnsMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMessages.put("clientId", TableInfo.Column("clientId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("serverId", TableInfo.Column("serverId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("channelId", TableInfo.Column("channelId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("senderId", TableInfo.Column("senderId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("senderName", TableInfo.Column("senderName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("body", TableInfo.Column("body", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("sentAtEpochMillis", TableInfo.Column("sentAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("syncState", TableInfo.Column("syncState", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesMessages.add(TableInfo.Index("index_messages_channelId", false, listOf("channelId"), listOf("ASC")))
        _indicesMessages.add(TableInfo.Index("index_messages_sentAtEpochMillis", false, listOf("sentAtEpochMillis"), listOf("ASC")))
        val _infoMessages: TableInfo = TableInfo("messages", _columnsMessages, _foreignKeysMessages, _indicesMessages)
        val _existingMessages: TableInfo = read(connection, "messages")
        if (!_infoMessages.equals(_existingMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |messages(com.varisahayak.data.local.entity.MessageEntity).
              | Expected:
              |""".trimMargin() + _infoMessages + """
              |
              | Found:
              |""".trimMargin() + _existingMessages)
        }
        val _columnsLostFoundItems: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLostFoundItems.put("clientId", TableInfo.Column("clientId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("serverId", TableInfo.Column("serverId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("incidentClientId", TableInfo.Column("incidentClientId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("kind", TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("description", TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("lastSeenLatitude", TableInfo.Column("lastSeenLatitude", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("lastSeenLongitude", TableInfo.Column("lastSeenLongitude", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("lastSeenAtEpochMillis", TableInfo.Column("lastSeenAtEpochMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("qrToken", TableInfo.Column("qrToken", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("photoLocalPath", TableInfo.Column("photoLocalPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("reportedBy", TableInfo.Column("reportedBy", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("reportedAtEpochMillis", TableInfo.Column("reportedAtEpochMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLostFoundItems.put("syncState", TableInfo.Column("syncState", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLostFoundItems: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLostFoundItems: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesLostFoundItems.add(TableInfo.Index("index_lost_found_items_status", false, listOf("status"), listOf("ASC")))
        val _infoLostFoundItems: TableInfo = TableInfo("lost_found_items", _columnsLostFoundItems, _foreignKeysLostFoundItems, _indicesLostFoundItems)
        val _existingLostFoundItems: TableInfo = read(connection, "lost_found_items")
        if (!_infoLostFoundItems.equals(_existingLostFoundItems)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |lost_found_items(com.varisahayak.data.local.entity.LostFoundEntity).
              | Expected:
              |""".trimMargin() + _infoLostFoundItems + """
              |
              | Found:
              |""".trimMargin() + _existingLostFoundItems)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "incidents", "profiles", "outbox", "responders", "incident_events", "documents", "notifications", "messages", "lost_found_items")
  }

  public override fun clearAllTables() {
    super.performClear(false, "incidents", "profiles", "outbox", "responders", "incident_events", "documents", "notifications", "messages", "lost_found_items")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(IncidentDao::class, IncidentDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ProfileDao::class, ProfileDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(OutboxDao::class, OutboxDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ResponderDao::class, ResponderDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(IncidentEventDao::class, IncidentEventDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DocumentDao::class, DocumentDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(NotificationDao::class, NotificationDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(MessageDao::class, MessageDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LostFoundDao::class, LostFoundDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun incidentDao(): IncidentDao = _incidentDao.value

  public override fun profileDao(): ProfileDao = _profileDao.value

  public override fun outboxDao(): OutboxDao = _outboxDao.value

  public override fun responderDao(): ResponderDao = _responderDao.value

  public override fun incidentEventDao(): IncidentEventDao = _incidentEventDao.value

  public override fun documentDao(): DocumentDao = _documentDao.value

  public override fun notificationDao(): NotificationDao = _notificationDao.value

  public override fun messageDao(): MessageDao = _messageDao.value

  public override fun lostFoundDao(): LostFoundDao = _lostFoundDao.value
}
