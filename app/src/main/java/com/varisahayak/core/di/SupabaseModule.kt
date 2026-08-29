package com.varisahayak.core.di

import com.varisahayak.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/**
 * The single Supabase client for the process.
 *
 * Two things here are load-bearing and easy to get wrong:
 *
 * 1. The Ktor engine is OkHttp. `ktor-client-android` — which the official Supabase
 *    Android tutorial uses — has no WebSocket support, and installing Realtime on top of
 *    it throws at runtime, not at compile time.
 * 2. No Context is passed and no initializer is registered. `auth-kt` registers an
 *    androidx.startup Initializer that captures the application context itself, so session
 *    persistence works with no wiring. There is no `Supabase.initialize(context)`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        httpEngine = OkHttp.create()

        install(Auth) {
            // Defaults already give us alwaysAutoRefresh, autoLoadFromStorage and
            // autoSaveToStorage. enableLifecycleCallbacks stays on, which means
            // sessionStatus emits Initializing when the app backgrounds — AuthRepository
            // maps that to "unknown", never to "signed out".
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
    }
}
