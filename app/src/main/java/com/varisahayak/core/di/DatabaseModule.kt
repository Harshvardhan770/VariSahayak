package com.varisahayak.core.di

import android.content.Context
import androidx.room.Room
import com.varisahayak.data.local.VariSahayakDatabase
import com.varisahayak.data.local.dao.DocumentDao
import com.varisahayak.data.local.dao.IncidentDao
import com.varisahayak.data.local.dao.IncidentEventDao
import com.varisahayak.data.local.dao.CustodyDao
import com.varisahayak.data.local.dao.LostFoundDao
import com.varisahayak.data.local.dao.LostFoundMatchDao
import com.varisahayak.data.local.dao.QrLocationDao
import com.varisahayak.data.local.dao.MessageDao
import com.varisahayak.data.local.dao.NotificationDao
import com.varisahayak.data.local.dao.OutboxDao
import com.varisahayak.data.local.dao.ProfileDao
import com.varisahayak.data.local.dao.ResponderDao
import com.varisahayak.data.local.migration.ALL_MIGRATIONS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): VariSahayakDatabase = Room.databaseBuilder(
        context,
        VariSahayakDatabase::class.java,
        VariSahayakDatabase.NAME,
    )
        // fallbackToDestructiveMigration is deliberately NOT set. Wiping the database on a
        // schema change would discard unsynced incidents — the one failure this product
        // cannot accept. A missing migration must fail loudly in development instead.
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    @Provides
    fun provideIncidentDao(db: VariSahayakDatabase): IncidentDao = db.incidentDao()

    @Provides
    fun provideProfileDao(db: VariSahayakDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideOutboxDao(db: VariSahayakDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideResponderDao(db: VariSahayakDatabase): ResponderDao = db.responderDao()

    @Provides
    fun provideIncidentEventDao(db: VariSahayakDatabase): IncidentEventDao =
        db.incidentEventDao()

    @Provides
    fun provideDocumentDao(db: VariSahayakDatabase): DocumentDao = db.documentDao()

    @Provides
    fun provideNotificationDao(db: VariSahayakDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideMessageDao(db: VariSahayakDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideLostFoundDao(db: VariSahayakDatabase): LostFoundDao = db.lostFoundDao()

    @Provides
    fun provideCustodyDao(db: VariSahayakDatabase): CustodyDao = db.custodyDao()

    @Provides
    fun provideLostFoundMatchDao(db: VariSahayakDatabase): LostFoundMatchDao =
        db.lostFoundMatchDao()

    @Provides
    fun provideQrLocationDao(db: VariSahayakDatabase): QrLocationDao = db.qrLocationDao()
}
