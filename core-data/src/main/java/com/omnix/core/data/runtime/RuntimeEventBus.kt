package com.omnix.core.data.runtime

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-scoped event bus for [RuntimeEvent]s emitted by runtime infrastructure.
 *
 * [ModelRuntimeManager] emits events here during model load, warmup, and unload.
 * [RuntimeController] forwards all events from this bus into its own [events]
 * SharedFlow so that external consumers (including [com.omnix.app.OmnixApplication])
 * see both controller-level and loader-level events through a single stream.
 *
 * The replay=0 policy means late subscribers only see future events — appropriate
 * for transient lifecycle notifications rather than persistent state facts.
 *
 * [tryEmit] is safe to call from non-suspend contexts such as finally blocks.
 * It drops the event silently on buffer overflow, which is acceptable because
 * runtime lifecycle events are best-effort diagnostics.
 */
@Singleton
class RuntimeEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<RuntimeEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    /** Hot stream of all runtime lifecycle events. */
    val events: SharedFlow<RuntimeEvent> = _events.asSharedFlow()

    /**
     * Emits [event] to all current subscribers.
     * Suspends if the buffer is full until space is available.
     */
    suspend fun emit(event: RuntimeEvent) {
        _events.emit(event)
    }

    /**
     * Attempts to emit [event] without suspending.
     * Returns true if delivered; false if dropped due to a full buffer.
     * Suitable for use inside finally blocks and JNI callbacks.
     */
    fun tryEmit(event: RuntimeEvent): Boolean {
        return _events.tryEmit(event)
    }
}
