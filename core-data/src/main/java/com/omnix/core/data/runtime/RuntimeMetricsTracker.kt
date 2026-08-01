package com.omnix.core.data.runtime

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Collects per-session performance data and maintains a rolling
 * [AggregateMetrics] [StateFlow] observable by the UI.
 *
 * Usage in [RuntimeController]:
 * 1. Call [sessionStarted] when a [GenerationSession] begins.
 * 2. Call [sessionEnded] when the session's flow completes, is cancelled,
 *    or errors. The [GenerationSession] carries live atomic counters
 *    updated by [TokenStreamPipeline.withMetrics].
 *
 * [latestSessionMetrics] surfaces the most recent [SessionMetrics] for
 * display in a diagnostics or stats panel.
 *
 * Note: this class tracks per-session detail ([SessionMetrics]).
 * The high-level operational snapshot is [RuntimeMetrics], which is
 * maintained separately by [RuntimeController.metricsFlow].
 */
@Singleton
class RuntimeMetricsTracker @Inject constructor() {

    private data class SessionStart(
        val modelId: String,
        val modelLoadTimeMs: Long,
        val promptTokens: Int,
        val startedAtMs: Long
    )

    private val pendingSessions = ConcurrentHashMap<String, SessionStart>()

    private val _aggregate = MutableStateFlow(AggregateMetrics())

    /** Rolling aggregate across all sessions since the process started. */
    val aggregate: StateFlow<AggregateMetrics> = _aggregate.asStateFlow()

    private val _latestSession = MutableStateFlow<SessionMetrics?>(null)

    /** Metrics for the most recently completed session. Null at startup. */
    val latestSessionMetrics: StateFlow<SessionMetrics?> = _latestSession.asStateFlow()

    /**
     * Records that a new generation session has started.
     *
     * @param session         the [GenerationSession] being tracked.
     * @param modelLoadTimeMs wall-clock ms spent loading the model before
     *                        this generation. Pass 0 if the model was
     *                        already in memory.
     */
    fun sessionStarted(
        session: GenerationSession,
        modelLoadTimeMs: Long = 0L
    ) {
        pendingSessions[session.id] = SessionStart(
            modelId = session.modelId,
            modelLoadTimeMs = modelLoadTimeMs,
            promptTokens = session.promptTokens,
            startedAtMs = session.startedAtMs
        )
    }

    /**
     * Finalises metrics for [session] and updates the aggregate.
     *
     * Safe to call from a finally block — will not throw even if the session
     * was never registered via [sessionStarted].
     *
     * @return the completed [SessionMetrics], or null if [sessionStarted]
     *         was never called for this session id.
     */
    fun sessionEnded(
        session: GenerationSession,
        finishReason: GenerationFinishReason
    ): SessionMetrics? {
        val start = pendingSessions.remove(session.id) ?: return null

        val now = System.currentTimeMillis()
        val totalDurationMs = now - start.startedAtMs
        val generationTimeMs = if (session.firstTokenTimeMs > 0L) {
            now - session.firstTokenTimeMs
        } else {
            0L
        }
        val generated = session.generatedTokens
        val tps = if (generationTimeMs > 0L) {
            generated / (generationTimeMs / 1_000f)
        } else {
            0f
        }

        val metrics = SessionMetrics(
            sessionId = session.id,
            modelId = start.modelId,
            modelLoadTimeMs = start.modelLoadTimeMs,
            generationTimeMs = generationTimeMs,
            totalDurationMs = totalDurationMs,
            promptTokens = start.promptTokens,
            generatedTokens = generated,
            tokensPerSecond = tps,
            peakMemoryBytes = 0L,
            finishReason = finishReason
        )

        _latestSession.value = metrics
        updateAggregate(metrics, finishReason)
        return metrics
    }

    private fun updateAggregate(
        metrics: SessionMetrics,
        reason: GenerationFinishReason
    ) {
        _aggregate.update { prev ->
            val newTotal = prev.totalSessions + 1
            val newCompleted = prev.completedSessions +
                if (reason == GenerationFinishReason.COMPLETED) 1 else 0
            val newCancelled = prev.cancelledSessions +
                if (reason == GenerationFinishReason.CANCELLED) 1 else 0
            val newFailed = prev.failedSessions +
                if (reason == GenerationFinishReason.ERROR) 1 else 0
            val newTokens = prev.totalTokensGenerated + metrics.generatedTokens
            val newPromptTokens = prev.totalPromptTokens + metrics.promptTokens

            val avgTps = if (newCompleted > 0) {
                ((prev.averageTokensPerSecond * (newCompleted - 1)) + metrics.tokensPerSecond) /
                    newCompleted
            } else {
                prev.averageTokensPerSecond
            }

            val avgGenMs = if (newCompleted > 0 && metrics.generationTimeMs > 0L) {
                ((prev.averageGenerationTimeMs * (newCompleted - 1)) + metrics.generationTimeMs) /
                    newCompleted
            } else {
                prev.averageGenerationTimeMs
            }

            val avgTotalMs =
                ((prev.averageSessionDurationMs * (newTotal - 1)) + metrics.totalDurationMs) /
                    newTotal

            prev.copy(
                totalSessions = newTotal,
                completedSessions = newCompleted,
                cancelledSessions = newCancelled,
                failedSessions = newFailed,
                totalTokensGenerated = newTokens,
                totalPromptTokens = newPromptTokens,
                averageTokensPerSecond = avgTps,
                averageGenerationTimeMs = avgGenMs,
                averageSessionDurationMs = avgTotalMs
            )
        }
    }

    /** Resets all tracked metrics. Useful for testing or a user-initiated reset. */
    fun reset() {
        pendingSessions.clear()
        _aggregate.value = AggregateMetrics()
        _latestSession.value = null
    }
}
