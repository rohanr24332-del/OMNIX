package com.omnix.core.data.runtime

/**
 * Per-session performance metrics for one completed generation request.
 * Produced by [RuntimeMetricsTracker] when a [GenerationSession] ends.
 *
 * Distinct from [RuntimeMetrics], which is the high-level operational
 * snapshot exposed by [RuntimeController.metricsFlow] and observed by
 * [com.omnix.app.OmnixApplication].
 */
data class SessionMetrics(
    val sessionId: String,
    val modelId: String,

    /** Wall-clock ms spent loading the model before generation started. */
    val modelLoadTimeMs: Long,

    /** Wall-clock ms from the first token emitted to the last token emitted. */
    val generationTimeMs: Long,

    /** Total wall-clock ms from request start to session teardown. */
    val totalDurationMs: Long,

    /** Estimated prompt token count (4-chars-per-token heuristic). */
    val promptTokens: Int,

    /** Number of tokens emitted during generation. */
    val generatedTokens: Int,

    /** Average generation speed in tokens per second. */
    val tokensPerSecond: Float,

    /** Peak resident memory in bytes; populated by a real loader, zero for simulated. */
    val peakMemoryBytes: Long,

    /** How this session ended. */
    val finishReason: GenerationFinishReason
)

/**
 * Reason a [GenerationSession] ended.
 */
enum class GenerationFinishReason {
    /** Generation completed normally and all tokens were delivered. */
    COMPLETED,

    /** Generation was explicitly cancelled via [ActiveSessionManager.cancel]. */
    CANCELLED,

    /** The provider threw an unrecoverable error. */
    ERROR,

    /** Generation stopped because [com.omnix.core.data.inference.GenerationParameters.maxNewTokens] was reached. */
    MAX_TOKENS,

    /** Generation stopped because a stop-sequence token was produced. */
    STOP_SEQUENCE
}

/**
 * Rolling aggregate of all generation sessions since the runtime started.
 * Exposed as a [kotlinx.coroutines.flow.StateFlow] by [RuntimeMetricsTracker.aggregate].
 */
data class AggregateMetrics(
    val totalSessions: Int = 0,
    val completedSessions: Int = 0,
    val cancelledSessions: Int = 0,
    val failedSessions: Int = 0,
    val totalTokensGenerated: Int = 0,
    val totalPromptTokens: Int = 0,
    val averageTokensPerSecond: Float = 0f,
    val averageGenerationTimeMs: Long = 0L,
    val averageSessionDurationMs: Long = 0L
)
