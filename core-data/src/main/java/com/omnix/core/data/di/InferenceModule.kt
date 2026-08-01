package com.omnix.core.data.di

import com.omnix.core.data.inference.InferenceProvider
import com.omnix.core.data.inference.providers.LlamaCppProvider
import com.omnix.core.data.inference.providers.MediaPipeProvider
import com.omnix.core.data.inference.providers.OllamaProvider
import com.omnix.core.data.inference.providers.SimulatedInferenceProvider
import com.omnix.core.data.runtime.RuntimeController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt DI for the inference subsystem.
 *
 * Phase 6.3–6.6 change: [bindActiveProvider] now points to [RuntimeController]
 * instead of [SimulatedInferenceProvider].
 *
 * [RuntimeController] implements [InferenceProvider] and routes every request
 * through the full runtime lifecycle — model loading, session management,
 * metrics, events, cancellation, and the token pipeline — before delegating
 * actual token generation to [SimulatedInferenceProvider] internally.
 *
 * [com.omnix.core.data.inference.InferenceEngine] is unchanged. It still
 * injects [InferenceProvider] and calls [InferenceProvider.streamTokens].
 * It now receives [RuntimeController] behind that interface.
 *
 * The multibound provider registry (@IntoSet bindings) is retained for a
 * future settings screen that lets users inspect or switch inference backends.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceModule {

    /**
     * The active [InferenceProvider] injected into [com.omnix.core.data.inference.InferenceEngine].
     *
     * Phase 5:   SimulatedInferenceProvider (direct)
     * Phase 6.3: RuntimeController (wraps SimulatedInferenceProvider with full runtime pipeline)
     * Phase 7:   RuntimeController (wraps a real JNI or HTTP provider)
     */
    @Binds
    @Singleton
    abstract fun bindActiveProvider(
        impl: RuntimeController
    ): InferenceProvider

    // ─── Registry (all providers known to the system) ─────────────────────────

    @Binds
    @IntoSet
    abstract fun bindSimulatedProvider(
        impl: SimulatedInferenceProvider
    ): InferenceProvider

    @Binds
    @IntoSet
    abstract fun bindLlamaCppProvider(
        impl: LlamaCppProvider
    ): InferenceProvider

    @Binds
    @IntoSet
    abstract fun bindOllamaProvider(
        impl: OllamaProvider
    ): InferenceProvider

    @Binds
    @IntoSet
    abstract fun bindMediaPipeProvider(
        impl: MediaPipeProvider
    ): InferenceProvider
}
