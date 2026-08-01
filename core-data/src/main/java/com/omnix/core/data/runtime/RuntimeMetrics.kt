package com.omnix.core.data.runtime

/**
 * A lightweight snapshot of the current AI runtime's operational metrics.
 *
 * All fields default to zero or empty so that a [RuntimeMetrics] can be
 * constructed immediately at startup before any real values are available.
 *
 * [RuntimeController] updates and exposes this via a [kotlinx.coroutines.flow.StateFlow].
 * Consumers observe the flow for reactive updates, or call
 * [RuntimeController.metrics] for an immediate snapshot.
 */
data class RuntimeMetrics(

    /**
     * The display name of the currently loaded model.
     * Empty string when no model is loaded.
     */
    val currentModelName: String = "",

    /**
     * Estimated resident memory used by the loaded model, in bytes.
     * Zero until a real model loader reports actual usage.
     */
    val memoryUsageBytes: Long = 0L,

    /**
     * Total number of tokens generated since the runtime was initialised.
     */
    val tokenCount: Int = 0,

    /**
     * Average generation speed in tokens per second.
     * Zero until at least one generation session has completed.
     */
    val inferenceSpeedTokensPerSec: Float = 0f,

    /**
     * Wall-clock milliseconds since [RuntimeController.initialize] was called.
     * Zero when the runtime is in the [RuntimeState.Idle] state.
     */
    val uptimeMs: Long = 0L,

    /**
     * Model loading progress from 0.0 to 1.0.
     * Only meaningful while the runtime is in [RuntimeState.Loading].
     */
    val loadingProgress: Float = 0f
)
