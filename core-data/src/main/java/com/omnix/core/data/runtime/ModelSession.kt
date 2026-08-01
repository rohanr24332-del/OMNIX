package com.omnix.core.data.runtime

import com.omnix.core.model.ModelInfo

/**
 * Represents one model that has been loaded into device memory by [ModelLoader].
 *
 * [ModelRuntimeManager] holds at most one [ModelSession] at a time in Phase 6.
 * The API is designed so that supporting multiple loaded models later only requires
 * changing [ModelRuntimeManager]'s internal storage — no consumer interface changes.
 *
 * For real backends in Phase 7 (llama.cpp, MediaPipe), [nativeHandle] will carry
 * an opaque reference to the loaded model in native memory. The simulated loader
 * leaves it null.
 *
 * This is an immutable snapshot. State transitions produce a new copy via
 * [withState], [withLoadCompleted], or [withWarmupCompleted].
 */
data class ModelSession(
    val modelId: String,
    val modelInfo: ModelInfo,
    val state: ModelSessionState,
    val loadStartedAtMs: Long,
    val loadCompletedAtMs: Long = 0L,
    val warmupCompletedAtMs: Long = 0L,

    /**
     * Opaque reference to the loaded model in native memory.
     * Null for the simulated loader. Non-null for real backends in Phase 7.
     */
    val nativeHandle: Any? = null
) {
    /**
     * Wall-clock milliseconds the load operation took.
     * Zero until [loadCompletedAtMs] is set by [withLoadCompleted].
     */
    val loadTimeMs: Long
        get() {
            return if (loadCompletedAtMs > 0L) {
                loadCompletedAtMs - loadStartedAtMs
            } else {
                0L
            }
        }

    /**
     * Wall-clock milliseconds the warmup pass took.
     * Zero until [warmupCompletedAtMs] is set by [withWarmupCompleted].
     */
    val warmupTimeMs: Long
        get() {
            return if (warmupCompletedAtMs > 0L && loadCompletedAtMs > 0L) {
                warmupCompletedAtMs - loadCompletedAtMs
            } else {
                0L
            }
        }

    /** True when the session is [ModelSessionState.READY] for inference. */
    val isReady: Boolean
        get() = state == ModelSessionState.READY

    /** Returns a copy of this session with [state] replaced. */
    fun withState(newState: ModelSessionState): ModelSession {
        return copy(state = newState)
    }

    /**
     * Returns a copy marking the load as complete.
     * Transitions [state] to [ModelSessionState.WARMING_UP].
     */
    fun withLoadCompleted(
        timestampMs: Long = System.currentTimeMillis()
    ): ModelSession {
        return copy(
            state = ModelSessionState.WARMING_UP,
            loadCompletedAtMs = timestampMs
        )
    }

    /**
     * Returns a copy marking warmup as complete.
     * Transitions [state] to [ModelSessionState.READY].
     */
    fun withWarmupCompleted(
        timestampMs: Long = System.currentTimeMillis()
    ): ModelSession {
        return copy(
            state = ModelSessionState.READY,
            warmupCompletedAtMs = timestampMs
        )
    }
}
