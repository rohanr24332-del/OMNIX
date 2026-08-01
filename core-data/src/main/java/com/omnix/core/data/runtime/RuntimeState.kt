package com.omnix.core.data.runtime

// ── High-level controller state ───────────────────────────────────────────────

/**
 * Lifecycle state of the OMNIX AI runtime as a whole.
 * Exposed by [RuntimeController.state] and observed by
 * [com.omnix.app.OmnixApplication].
 *
 * Normal transition order:
 *   Idle → Loading → Ready → Running → (Paused →) Ready
 *
 * Any state can transition to Error on failure, and from Error back to Idle
 * after the error is acknowledged and the runtime is reset via [RuntimeController.shutdown].
 */
sealed class RuntimeState {

    /**
     * The runtime has not been initialised. No model is in memory.
     * Initial state before [RuntimeController.initialize] is called.
     */
    object Idle : RuntimeState()

    /**
     * The runtime is initialising.
     * [progress] is 0.0..1.0 representing how far the loading has advanced.
     */
    data class Loading(
        val progress: Float = 0f
    ) : RuntimeState()

    /**
     * The runtime has initialised successfully and is ready to accept requests.
     */
    object Ready : RuntimeState()

    /**
     * A generation is actively running.
     * [modelName] identifies which model is handling the request.
     */
    data class Running(
        val modelName: String
    ) : RuntimeState()

    /**
     * Generation has been paused. The loaded model remains in memory.
     * Transition back to [Ready] via [RuntimeController.resume].
     */
    object Paused : RuntimeState()

    /**
     * A non-recoverable error occurred.
     * [message] describes what went wrong.
     * Transition back to [Idle] via [RuntimeController.shutdown].
     */
    data class Error(
        val message: String
    ) : RuntimeState()
}

/** Returns true if this state allows a new generation to start. */
fun RuntimeState.isGenerationAllowed(): Boolean {
    return this is RuntimeState.Ready
}

/** Returns true if a model is currently loaded in memory. */
fun RuntimeState.isModelInMemory(): Boolean {
    return this is RuntimeState.Ready ||
        this is RuntimeState.Running ||
        this is RuntimeState.Paused
}

// ── Model runtime manager state ───────────────────────────────────────────────

/**
 * Detailed lifecycle state of [ModelRuntimeManager].
 * More granular than [RuntimeState]; exposes the loading sub-phases
 * for progress indicators and diagnostics.
 */
enum class RuntimeManagerState {
    /** No model has been requested yet. */
    IDLE,

    /** A model load is in progress. */
    LOADING,

    /** A model has loaded and the warmup pass is running. */
    WARMING_UP,

    /** A model is loaded and the manager is ready to accept requests. */
    READY,

    /** The runtime is actively generating tokens. */
    GENERATING,

    /** The current model is being released from memory. */
    UNLOADING,

    /** A non-fatal error occurred; the manager can recover on the next request. */
    ERROR
}

// ── Model session state ───────────────────────────────────────────────────────

/**
 * Lifecycle state of a single [ModelSession] — one model in device memory.
 */
enum class ModelSessionState {
    /** The model file is being read into memory. */
    LOADING,

    /** The model is loaded and the warmup pass is executing. */
    WARMING_UP,

    /** The model is loaded, warmed up, and ready for inference. */
    READY,

    /** The model is being released from memory. */
    UNLOADING,

    /** The model failed to load or warm up. */
    FAILED
}

// ── Generation session state ──────────────────────────────────────────────────

/**
 * Lifecycle state of one [GenerationSession] — one prompt-to-token-stream request.
 */
enum class GenerationSessionState {
    /** The session has been created but generation has not started yet. */
    PENDING,

    /** Token generation is actively running. */
    RUNNING,

    /** Generation finished successfully; all tokens were delivered. */
    COMPLETED,

    /** Generation was cancelled by the caller. */
    CANCELLED,

    /** Generation failed with an error. */
    FAILED
}
