package com.omnix.core.data.di

import com.omnix.core.data.runtime.ModelLifecycle
import com.omnix.core.data.runtime.ModelLoader
import com.omnix.core.data.runtime.SimulatedModelLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Hilt module for the Phase 6 runtime execution layer.
 *
 * Bindings:
 * - [ModelLoader] -> [SimulatedModelLoader]
 *   Phase 7: swap this binding for LlamaCppModelLoader or MediaPipeModelLoader.
 *   Nothing else in the codebase needs to change to switch the backend.
 *
 * - An empty @Multibinds Set<ModelLifecycle>.
 *   [com.omnix.core.data.runtime.ModelRuntimeManager] calls each observer during
 *   load, warmup, and unload. Add observers with @Binds @IntoSet bindings here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeModule {

    /**
     * Binds the active [ModelLoader] implementation.
     *
     * Phase 6: [SimulatedModelLoader] — realistic delays, no JNI.
     * Phase 7: replace with a real backend loader.
     */
    @Binds
    @Singleton
    abstract fun bindModelLoader(
        impl: SimulatedModelLoader
    ): ModelLoader

    /**
     * Declares the empty default set of [ModelLifecycle] observers.
     *
     * Add observers by providing @Binds @IntoSet bindings in this module
     * or in a child Hilt module.
     */
    @Multibinds
    abstract fun bindModelLifecycleObservers(): Set<ModelLifecycle>
}
