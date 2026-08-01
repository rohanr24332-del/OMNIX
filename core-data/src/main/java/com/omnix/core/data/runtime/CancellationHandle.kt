package com.omnix.core.data.runtime

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A lightweight, thread-safe cancellation signal for one [GenerationSession].
 *
 * [RuntimeController] attaches one handle per request and passes it into
 * [TokenStreamPipeline]. The pipeline checks [isCancelled] before emitting
 * each token so cancellation takes effect within one token's delay rather
 * than waiting for the coroutine's next cooperative cancellation point.
 *
 * [ActiveSessionManager.cancel] calls [cancel] on the handle; the pipeline
 * then throws [GenerationCancelledException] which [RuntimeController] catches
 * and re-wraps as [com.omnix.core.data.inference.InferenceException.GenerationCancelled].
 *
 * This is intentionally separate from [kotlinx.coroutines.Job] cancellation.
 * Native model runners (llama.cpp, MediaPipe) can check [isCancelled] directly
 * from JNI callbacks without needing a coroutine scope in Phase 7.
 */
class CancellationHandle {

    private val cancelled = AtomicBoolean(false)

    /** True once [cancel] has been called. Thread-safe. */
    val isCancelled: Boolean
        get() = cancelled.get()

    /**
     * Signals cancellation. Idempotent — calling more than once is safe.
     * Returns true if this call was the one that set the flag; false if
     * it was already cancelled.
     */
    fun cancel(): Boolean {
        return cancelled.compareAndSet(false, true)
    }

    /**
     * Throws [GenerationCancelledException] if [isCancelled] is true.
     * Called by [TokenStreamPipeline] before each token is emitted.
     */
    @Throws(GenerationCancelledException::class)
    fun checkCancelled(sessionId: String) {
        if (isCancelled) {
            throw GenerationCancelledException(sessionId)
        }
    }

    override fun toString(): String {
        return "CancellationHandle(cancelled=${isCancelled})"
    }
}

/**
 * Thrown by [CancellationHandle.checkCancelled].
 * Internal to the runtime package — caught and re-wrapped by
 * [TokenStreamPipeline.withCancellation] as a public
 * [com.omnix.core.data.inference.InferenceException].
 */
internal class GenerationCancelledException(
    val sessionId: String
) : Exception("Session $sessionId was cancelled")
