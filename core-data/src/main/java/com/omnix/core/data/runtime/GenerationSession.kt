package com.omnix.core.data.runtime

import com.omnix.core.model.AiMode
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents one active prompt-to-token-stream generation request.
 *
 * Carries its own [CancellationHandle] and atomic metric counters so that
 * [TokenStreamPipeline] can record progress from within the hot token loop
 * without locks, and so native providers in Phase 7 can check cancellation
 * directly from JNI callbacks without needing a coroutine scope.
 *
 * This is a regular class (not a data class) because it holds mutable
 * [AtomicInteger] and [AtomicLong] fields that must not be captured by a
 * copy operation — mutation must always happen on the live instance.
 *
 * Lifecycle:
 *   Created by [RuntimeController] -> registered with [ActiveSessionManager]
 *   -> state set to RUNNING -> tokens collected -> unregistered in finally block.
 */
class GenerationSession(
    val id: String,
    val modelId: String,
    val mode: AiMode,
    val startedAtMs: Long = System.currentTimeMillis(),
    val cancellationHandle: CancellationHandle = CancellationHandle(),
    val promptTokens: Int = 0
) {
    /**
     * Current lifecycle state of this session.
     *
     * Written by [RuntimeController] throughout the generation lifecycle.
     * [@Volatile] ensures visibility across threads without full locking overhead.
     */
    @Volatile
    var state: GenerationSessionState = GenerationSessionState.PENDING

    /**
     * Total tokens emitted so far. Incremented atomically by
     * [TokenStreamPipeline.withMetrics] on every token.
     */
    internal val _generatedTokens = AtomicInteger(0)

    /**
     * Wall-clock ms when the first token was emitted. Set once by
     * [TokenStreamPipeline.withMetrics] via [AtomicLong.compareAndSet].
     * Zero until the first token arrives.
     */
    internal val _firstTokenTimeMs = AtomicLong(0L)

    /** Number of tokens emitted so far. Thread-safe read. */
    val generatedTokens: Int
        get() = _generatedTokens.get()

    /** Wall-clock ms of first token emission. Zero if none yet. */
    val firstTokenTimeMs: Long
        get() = _firstTokenTimeMs.get()

    /** True if the session has not yet finished, failed, or been cancelled. */
    fun isActive(): Boolean {
        return state == GenerationSessionState.PENDING ||
            state == GenerationSessionState.RUNNING
    }

    /**
     * Returns the current generation speed in tokens per second.
     * Returns zero if generation has not started or no time has elapsed.
     */
    fun currentTokensPerSecond(): Float {
        val first = firstTokenTimeMs
        if (first == 0L) return 0f
        val elapsedSeconds = (System.currentTimeMillis() - first) / 1_000f
        if (elapsedSeconds <= 0f) return 0f
        return generatedTokens / elapsedSeconds
    }

    /**
     * Signals cancellation via [CancellationHandle] and transitions [state]
     * to [GenerationSessionState.CANCELLED]. Idempotent and thread-safe.
     */
    fun cancel() {
        cancellationHandle.cancel()
        state = GenerationSessionState.CANCELLED
    }

    override fun toString(): String {
        return "GenerationSession(" +
            "id=$id, " +
            "modelId=$modelId, " +
            "state=$state, " +
            "tokens=${generatedTokens}" +
            ")"
    }
}
