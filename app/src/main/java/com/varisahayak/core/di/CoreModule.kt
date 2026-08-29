package com.varisahayak.core.di

import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.DefaultDispatcherProvider
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.SystemClock
import com.varisahayak.core.network.AndroidConnectivityObserver
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.data.sync.SyncScheduler
import com.varisahayak.data.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreProvidesModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    /**
     * ignoreUnknownKeys is not laxness — it is a field-safety requirement. A server that
     * adds a column must not crash an older APK that a volunteer cannot update on the
     * route.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreBindsModule {

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(
        impl: AndroidConnectivityObserver,
    ): ConnectivityObserver

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(
        impl: WorkManagerSyncScheduler,
    ): SyncScheduler
}
