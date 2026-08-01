package com.omnix.core.data.runtime

/**
 * Observable events emitted by the OMNIX AI runtime.
 *
 * Events flow through two channels:
 * - [RuntimeController._events] → consumed by [com.omnix.app.OmnixApplication]
 *   and any feature that injects [RuntimeController] directly.
 * - [RuntimeEventBus] → used internally by [ModelRuntimeManager]; forwarded
 *   to [RuntimeController._events] so external consumers see everything.
 *
 * Events carry data but contain no business logic.
 */
sealed class RuntimeEvent {

    // ── High-level controller events (Phase 6A.1, kept for OmnixApplication) ──

    /**
     * Emitted by [RuntimeController.initialize] once the runtime is ready.
     *
     * @param modelName a label for the runtime state (e.g. "OMNIX Runtime Ready"
     *                  before a real model is selected, or the model display name
     *                  once a model is loaded).
     * @param loadTimeMs wall-clock ms the initialisation took.
     */
    data class ModelLoaded(
        val modelName: String,
        val loadTimeMs: Long = 0L
    ) : RuntimeEvent()

    /**
     * Emitted when a generation request begins.
     *
     * @param sessionId unique identifier for this generation turn.
     * @param modelName the model handling this request.
     */
    data class GenerationStarted(
        val sessionId: String,
        val modelName: String
    ) : RuntimeEvent()

    /**
     * Emitted when a generation request finishes successfully.
     *
     * @param sessionId matches the id from [GenerationStarted].
     * @param tokenCount the number of tokens generated.
     * @param durationMs total generation time in milliseconds.
     */
    data class GenerationFinished(
        val sessionId: String,
        val tokenCount: Int,
        val durationMs: Long
    ) : RuntimeEvent()

    /**
     * Emitted when initialisation fails at the [RuntimeController] level.
     *
     * @param reason human-readable description of the failure.
     * @param cause the underlying exception, if available.
     */
    data class LoadingFailed(
        val reason: String,
        val cause: Throwable? = null
    ) : RuntimeEvent()

    /**
     * Emitted when [RuntimeController.shutdown] completes.
     */
    object RuntimeStopped : RuntimeEvent()

    // ── Model loader lifecycle events (emitted by ModelRuntimeManager) ─────────

    /**
     * Emitted when [ModelRuntimeManager] begins loading a model.
     *
     * @param modelId the catalog ID of the model being loaded.
     * @param modelDisplayName human-readable name for UI display.
     * @param estimatedSizeMb approximate size in megabytes.
     */
    data class ModelLoadStarted(
        val modelId: String,
        val modelDisplayName: String,
        val estimatedSizeMb: Int
    ) : RuntimeEvent()

    /**
     * Emitted repeatedly during loading to report incremental progress.
     *
     * @param modelId the catalog ID of the model being loaded.
     * @param progressFraction value from 0.0 to 1.0.
     */
    data class ModelLoadProgress(
        val modelId: String,
        val progressFraction: Float
    ) : RuntimeEvent()

    /**
     * Emitted when a model has been fully loaded into memory and is ready
     * for the warmup pass.
     *
     * @param modelId the catalog ID of the loaded model.
     * @param loadTimeMs wall-clock ms the load operation took.
     */
    data class ModelLoadCompleted(
        val modelId: String,
        val loadTimeMs: Long
    ) : RuntimeEvent()

    /**
     * Emitted when [ModelRuntimeManager] fails to load a model.
     *
     * @param modelId the catalog ID of the model that failed to load.
     * @param reason human-readable failure description.
     * @param cause the underlying exception, if available.
     */
    data class ModelLoadFailed(
        val modelId: String,
        val reason: String,
        val cause: Throwable? = null
    ) : RuntimeEvent()

    /**
     * Emitted when [ModelRuntimeManager] begins releasing a model from memory.
     *
     * @param modelId the catalog ID of the model being unloaded.
     */
    data class ModelUnloadStarted(
        val modelId: String
    ) : RuntimeEvent()

    /**
     * Emitted when a model has been fully released from memory.
     *
     * @param modelId the catalog ID of the model that was unloaded.
     */
    data class ModelUnloadCompleted(
        val modelId: String
    ) : RuntimeEvent()

    /**
     * Emitted when the warmup pass begins for a loaded model.
     *
     * @param modelId the catalog ID of the model being warmed up.
     */
    data class WarmupStarted(
        val modelId: String
    ) : RuntimeEvent()

    /**
     * Emitted when the warmup pass completes.
     *
     * @param modelId the catalog ID of the model that was warmed up.
     * @param durationMs wall-clock ms the warmup pass took.
     */
    data class WarmupCompleted(
        val modelId: String,
        val durationMs: Long
    ) : RuntimeEvent()

    /**
     * Emitted when a non-fatal runtime error occurs that does not fully
     * stop the runtime (e.g. a failed pre-load attempt).
     *
     * @param message human-readable description of the error.
     * @param cause the underlying exception, if available.
     */
    data class RuntimeError(
        val message: String,
        val cause: Throwable? = null
    ) : RuntimeEvent()
}
