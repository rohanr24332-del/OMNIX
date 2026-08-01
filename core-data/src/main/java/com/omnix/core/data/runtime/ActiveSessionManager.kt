package com.omnix.core.data.runtime

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe registry of every [GenerationSession] that is currently active.
 *
 * [RuntimeController] registers a session at the start of [RuntimeController.streamTokens]
 * and unregisters it in the flow's finally block, regardless of how the session ended.
 *
 * [cancel] is called by [RuntimeController.cancel] when an external caller (such as
 * a stop button in the chat UI) signals that generation should stop.
 *
 * [cancelAll] is called by [RuntimeController.shutdown] and by
 * [ModelRuntimeManager] before a model is released from memory, ensuring no
 * generation coroutine continues to reference a model that is no longer valid.
 */
@Singleton
class ActiveSessionManager @Inject constructor() {

    private val sessions = ConcurrentHashMap<String, GenerationSession>()

    /**
     * Registers [session] in the active registry.
     * Silently replaces any existing session with the same id.
     */
    fun register(session: GenerationSession) {
        sessions[session.id] = session
    }

    /**
     * Removes the session identified by [sessionId] from the registry.
     * Called in the finally block of [RuntimeController.streamTokens].
     */
    fun unregister(sessionId: String) {
        sessions.remove(sessionId)
    }

    /**
     * Signals cancellation for [sessionId].
     *
     * Returns true if the session was found and [GenerationSession.cancel] was
     * called. Returns false if no session with that id is registered.
     */
    fun cancel(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        session.cancel()
        return true
    }

    /**
     * Cancels every active session and clears the registry.
     * Called before a model is unloaded to prevent use-after-free in Phase 7.
     */
    fun cancelAll() {
        sessions.values.forEach { it.cancel() }
        sessions.clear()
    }

    /** Returns the session registered under [sessionId], or null if not found. */
    fun getSession(sessionId: String): GenerationSession? {
        return sessions[sessionId]
    }

    /** Number of sessions currently in the registry. */
    val activeCount: Int
        get() = sessions.size

    /** Returns true if at least one session is currently registered. */
    fun hasActiveSessions(): Boolean {
        return sessions.isNotEmpty()
    }
}
