package com.omnix.core.data.runtime

import com.omnix.core.model.ModelInfo

/**
 * Abstraction over the mechanism that loads a model into device memory.
 *
 * Current implementation: [SimulatedModelLoader] for Phase 6.
 * Phase 7 implementations: LlamaCppModelLoader (JNI), MediaPipeModelLoader.
 *
 * [ModelRuntimeManager] owns the decision of which model to load and when to
 * unload it. This interface is purely responsible for how the load happens.
 *
 * [load] returns a [ModelSession] in [ModelSessionState.WARMING_UP] state.
 * Call [warmup] immediately after to obtain a [ModelSessionState.READY] session.
 *
 * Implementations are bound in [com.omnix.core.data.di.RuntimeModule].
 * Swapping the backend for Phase 7 requires only changing that one binding.
 */
interface ModelLoader {

    /**
     * Loads [modelInfo] into device memory.
     *
     * Implementations must invoke [onProgress] periodically with a 0.0 to 1.0
     * fraction so that [ModelRuntimeManager] can emit [RuntimeEvent.ModelLoadProgress]
     * events for UI progress indicators.
     *
     * @param modelInfo  metadata of the model to load.
     * @param onProgress suspend callback receiving incremental progress 0.0..1.0.
     * @return a [ModelSession] in [ModelSessionState.WARMING_UP] state.
     * @throws [ModelLoadException] if loading fails for any reason.
     */
    suspend fun load(
        modelInfo: ModelInfo,
        onProgress: suspend (Float) -> Unit = {}
    ): ModelSession

    /**
     * Runs the warmup pass for an already-loaded [ModelSession].
     *
     * Returns the updated session in [ModelSessionState.READY].
     * If [config] strategy is [WarmupStrategy.NONE], returns immediately.
     *
     * @param session the session returned by [load].
     * @param config  warmup strategy and timeout.
     */
    suspend fun warmup(
        session: ModelSession,
        config: WarmupConfig = WarmupConfig.DEFAULT
    ): ModelSession

    /**
     * Releases the model from device memory.
     *
     * After this call, any [ModelSession.nativeHandle] from the previous
     * [load] is invalid and must not be used again.
     */
    suspend fun unload(session: ModelSession)

    /**
     * Returns true if a model with [modelId] is currently held in memory
     * and its session handle is still valid.
     */
    fun isLoaded(modelId: String): Boolean
}

/**
 * Thrown by [ModelLoader.load] when a model cannot be loaded.
 */
class ModelLoadException(
    val modelId: String,
    message: String,
    cause: Throwable? = null
) : Exception("Failed to load model '$modelId': $message", cause)
