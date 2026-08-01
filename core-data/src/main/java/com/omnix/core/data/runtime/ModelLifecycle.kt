package com.omnix.core.data.runtime

/**
 * Callback interface for components that need to react to model load and
 * unload events synchronously, inside the loading coroutine, before
 * [ModelRuntimeManager] transitions to [RuntimeManagerState.READY].
 *
 * Most consumers should prefer [RuntimeEventBus] instead. This interface
 * is for components that must act before the model is considered available,
 * for example a resource pre-allocator or a warmup cache primer.
 *
 * Implementations are registered via a Hilt @Multibinds Set<ModelLifecycle>
 * declared in [com.omnix.core.data.di.RuntimeModule]. An empty set is the
 * default; add observers by providing @Binds @IntoSet bindings.
 *
 * All callbacks have default no-op implementations so implementors only
 * override the hooks they need.
 */
interface ModelLifecycle {

    /**
     * Called when [ModelRuntimeManager] begins loading [modelId].
     * Runs inside the loading coroutine. Keep this fast.
     */
    suspend fun onModelLoading(modelId: String, estimatedSizeMb: Int) {}

    /**
     * Called once the model is fully resident in device memory and the
     * [ModelSession] transitions to [ModelSessionState.WARMING_UP].
     */
    suspend fun onModelLoaded(modelId: String, loadTimeMs: Long) {}

    /**
     * Called before [ModelRuntimeManager] releases the model from memory.
     * Implementations should flush any caches that depend on model state.
     */
    suspend fun onModelUnloading(modelId: String) {}

    /**
     * Called once the model is fully released and device memory is reclaimed.
     */
    suspend fun onModelUnloaded(modelId: String) {}

    /**
     * Called immediately before the warmup pass begins.
     */
    suspend fun onWarmupStarted(modelId: String) {}

    /**
     * Called once the warmup pass completes and the model transitions
     * to [ModelSessionState.READY].
     */
    suspend fun onWarmupCompleted(modelId: String, durationMs: Long) {}
}
