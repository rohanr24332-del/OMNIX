package com.omnix.core.data.runtime

import com.omnix.core.data.inference.InferenceException
import com.omnix.core.data.inference.StreamingToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform

/**
 * Composable operators that enrich a raw [Flow] of [StreamingToken]s with
 * runtime concerns: cancellation checking, metrics recording, and buffering.
 *
 * Defined as an object so that [RuntimeController] can import
 * [withRuntimePipeline] as a member extension function:
 *
 *   import com.omnix.core.data.runtime.TokenStreamPipeline.withRuntimePipeline
 *
 * All operators are cold — they activate only when the flow is collected.
 */
object TokenStreamPipeline {

    /**
     * Checks [GenerationSession.cancellationHandle] before each token is emitted.
     *
     * If the handle has been cancelled, throws
     * [InferenceException.GenerationCancelled] so the outer
     * [RuntimeController.streamTokens] catch block can handle it cleanly.
     */
    fun Flow<StreamingToken>.withCancellation(
        session: GenerationSession
    ): Flow<StreamingToken> {
        return transform { token ->
            try {
                session.cancellationHandle.checkCancelled(session.id)
                emit(token)
            } catch (e: GenerationCancelledException) {
                throw InferenceException.GenerationCancelled(e.sessionId)
            }
        }
    }

    /**
     * Records per-token progress on [session].
     *
     * Sets [GenerationSession._firstTokenTimeMs] exactly once via
     * [java.util.concurrent.atomic.AtomicLong.compareAndSet] to ensure only one
     * thread wins. Increments [GenerationSession._generatedTokens] on every token.
     */
    fun Flow<StreamingToken>.withMetrics(
        session: GenerationSession
    ): Flow<StreamingToken> {
        return onEach {
            session._firstTokenTimeMs.compareAndSet(0L, System.currentTimeMillis())
            session._generatedTokens.incrementAndGet()
        }
    }

    /**
     * Inserts a bounded buffer between the token producer and the collector.
     *
     * [capacity] defaults to 16 tokens, balancing latency and memory use.
     */
    fun Flow<StreamingToken>.withBuffer(
        capacity: Int = 16
    ): Flow<StreamingToken> {
        return buffer(capacity)
    }

    /**
     * Applies cancellation, metrics, and buffering in the correct order.
     *
     * Always prefer this over chaining the individual operators manually
     * to avoid ordering mistakes.
     *
     * Used by [RuntimeController] as:
     *   simulatedProvider.streamTokens(request)
     *       .withRuntimePipeline(genSession)
     *       .collect { token -> emit(token) }
     */
    fun Flow<StreamingToken>.withRuntimePipeline(
        session: GenerationSession
    ): Flow<StreamingToken> {
        return withCancellation(session)
            .withMetrics(session)
            .withBuffer()
    }
}
