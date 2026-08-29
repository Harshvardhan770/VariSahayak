package com.varisahayak.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.varisahayak.data.local.dao.DocumentDao
import com.varisahayak.data.local.dao.IncidentDao
import com.varisahayak.data.local.dao.IncidentEventDao
import com.varisahayak.data.local.dao.CustodyDao
import com.varisahayak.data.local.dao.LostFoundDao
import com.varisahayak.data.local.dao.LostFoundMatchDao
import com.varisahayak.data.local.dao.QrLocationDao
import com.varisahayak.data.local.dao.RewardDao
import com.varisahayak.data.local.dao.MessageDao
import com.varisahayak.data.local.dao.NotificationDao
import com.varisahayak.data.local.dao.OutboxDao
import com.varisahayak.data.local.dao.ProfileDao
import com.varisahayak.data.local.dao.ResponderDao
import com.varisahayak.data.local.entity.DocumentEntity
import com.varisahayak.data.local.entity.IncidentEntity
import com.varisahayak.data.local.entity.IncidentEventEntity
import com.varisahayak.data.local.entity.CustodyEntity
import com.varisahayak.data.local.entity.LostFoundEntity
import com.varisahayak.data.local.entity.LostFoundMatchEntity
import com.varisahayak.data.local.entity.QrLocationEntity
import com.varisahayak.data.local.entity.RewardProfileEntity
import com.varisahayak.data.local.entity.XPTransactionEntity
import com.varisahayak.data.local.entity.UserBadgeEntity
import com.varisahayak.data.local.entity.MessageEntity
import com.varisahayak.data.local.entity.NotificationEntity
import com.varisahayak.data.local.entity.OutboxEntity
import com.varisahayak.data.local.entity.ProfileEntity
import com.varisahayak.data.local.entity.ResponderEntity

/**
 * The device's operational store.
 *
 * Room 2.x is used deliberately rather than Room 3 — Room 3 publishes no minSdk and the
 * product targets API 23. See plans/00-api-contract.md §0.3.
 *
 * Schemas are exported to app/schemas and committed. Never bump [version] without adding
 * a migration: destructive fallback would discard unsynced incidents, which is the one
 * thing this app must never do.
 */
@Database(
    entities = [
        IncidentEntity::class,
        ProfileEntity::class,
        OutboxEntity::class,
        ResponderEntity::class,
        IncidentEventEntity::class,
        DocumentEntity::class,
        NotificationEntity::class,
        MessageEntity::class,
        LostFoundEntity::class,
        CustodyEntity::class,
        LostFoundMatchEntity::class,
        QrLocationEntity::class,
        RewardProfileEntity::class,
        XPTransactionEntity::class,
        UserBadgeEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class VariSahayakDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun profileDao(): ProfileDao
    abstract fun outboxDao(): OutboxDao
    abstract fun responderDao(): ResponderDao
    abstract fun incidentEventDao(): IncidentEventDao
    abstract fun documentDao(): DocumentDao
    abstract fun notificationDao(): NotificationDao
    abstract fun messageDao(): MessageDao
    abstract fun lostFoundDao(): LostFoundDao
    abstract fun custodyDao(): CustodyDao
    abstract fun lostFoundMatchDao(): LostFoundMatchDao
    abstract fun qrLocationDao(): QrLocationDao
    abstract fun rewardDao(): RewardDao

    companion object {
        const val NAME = "varisahayak.db"
    }
}
