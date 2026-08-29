package com.varisahayak.core.di

import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.walkie.SimulatedWalkieController
import com.varisahayak.core.walkie.WalkieController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that outlives any screen.
 *
 * The radio channel is not owned by a composable: a responder navigating from the map to
 * an incident must not drop off the net mid-transmission.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object WalkieProvidesModule {

    /**
     * SupervisorJob so a failure in one long-lived subscriber cannot tear down the others
     * that share this scope.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WalkieBindsModule {

    /**
     * Bound to the simulated implementation because no audio transport exists yet. When
     * one lands, this is the only line that changes.
     */
    @Binds
    @Singleton
    abstract fun bindWalkieController(impl: SimulatedWalkieController): WalkieController
}
