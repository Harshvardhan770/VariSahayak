package com.varisahayak.core.di

import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.walkie.LiveKitWalkieController
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
     * The real transport: LiveKit over a self-hosted server.
     *
     * Bound unconditionally, including when LIVEKIT_URL is unset. Falling back to
     * SimulatedWalkieController in that case would be the wrong kind of graceful: the
     * simulator reports the channel as Connected and leaves push-to-talk enabled, so a
     * misconfigured build would hand a volunteer a live-looking button that carries
     * nothing. LiveKitWalkieController reports NotConfigured instead, and the widget says
     * the radio is unavailable.
     *
     * SimulatedWalkieController stays in the tree for UI work with no server to hand.
     * Swapping to it is this one line.
     */
    @Binds
    @Singleton
    abstract fun bindWalkieController(impl: LiveKitWalkieController): WalkieController
}
