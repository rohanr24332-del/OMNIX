package com.omnix.app

import android.app.Application
import android.util.Log
import com.omnix.core.data.runtime.RuntimeController
import com.omnix.core.data.runtime.RuntimeEvent
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * OMNIX Application entry point.
 *
 * Phase 6B.1 additions:
 * - Injects [RuntimeController] and initialises it on app startup.
 * - Observes [RuntimeController.state] for lifecycle diagnostics.
 * - Observes [RuntimeController.events] for runtime event propagation.
 * - Observes [RuntimeController.metricsFlow] for runtime metrics updates.
 * - Cancels the [applicationScope] in [onTerminate] to cleanly stop all observers.
 *
 * The [applicationScope] is tied to the process lifetime via [SupervisorJob].
 * Individual features that need runtime state can inject [RuntimeController]
 * directly and collect its flows within their own scopes.
 */
@HiltAndroidApp
class OmnixApplication : Application() {

    @Inject
    lateinit var runtimeController: RuntimeController

    /**
     * Application-scoped coroutine scope tied to the process lifetime.
     *
     * Uses [SupervisorJob] so a failed child coroutine does not cancel
     * the other observers. Uses [Dispatchers.Default] to keep runtime
     * coordination off the main thread.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initializeRuntime()
        observeRuntimeState()
        observeRuntimeEvents()
        observeRuntimeMetrics()
    }

    override fun onTerminate() {
        super.onTerminate()
        // Cancel the application scope, which stops all active observers
        // and any coroutines launched within it.
        applicationScope.cancel()
        Log.d(TAG, "Application terminated — runtime scope cancelled")
    }

    // ── Runtime lifecycle ─────────────────────────────────────────────────────

    /**
     * Initialises the runtime asynchronously on app startup.
     *
     * [RuntimeController.initialize] transitions the runtime from
     * [com.omnix.core.data.runtime.RuntimeState.Idle] through
     * [com.omnix.core.data.runtime.RuntimeState.Loading] to
     * [com.omnix.core.data.runtime.RuntimeState.Ready].
     *
     * In Phase 6B.1 this uses the placeholder implementation in
     * [RuntimeController]. No real model is loaded.
     */
    private fun initializeRuntime() {
        applicationScope.launch {
            Log.d(TAG, "Initializing OMNIX runtime")
            runtimeController.initialize()
            Log.d(TAG, "OMNIX runtime initialization complete")
        }
    }

    // ── Runtime observation ───────────────────────────────────────────────────

    /**
     * Collects [RuntimeController.state] for the application lifetime.
     *
     * Logs each state transition. Future UI components observe the same
     * flow independently by injecting [RuntimeController] into their
     * ViewModels without any additional wiring here.
     */
    private fun observeRuntimeState() {
        applicationScope.launch {
            runtimeController.state.collect { state ->
                Log.d(TAG, "Runtime state → $state")
            }
        }
    }

    /**
     * Collects [RuntimeController.events] for the application lifetime.
     *
     * Handles each [RuntimeEvent] variant explicitly so that future
     * cross-cutting concerns (analytics, crash reporting, telemetry)
     * have a single place to be wired in without touching individual
     * feature modules.
     */
    private fun observeRuntimeEvents() {
        applicationScope.launch {
            runtimeController.events.collect { event ->
                handleRuntimeEvent(event)
            }
        }
    }

    /**
     * Dispatches a [RuntimeEvent] to the appropriate handler.
     *
     * Each branch is a separate, intentional no-op logging statement in
     * Phase 6B.1. Real actions (updating a notification, firing analytics,
     * etc.) are added per branch in later phases without changing the
     * collection infrastructure.
     */
    private fun handleRuntimeEvent(event: RuntimeEvent) {
    when (event) {

        is RuntimeEvent.ModelLoaded -> {
            Log.i(
                TAG,
                "Event: ModelLoaded — model='${event.modelName}' loadTime=${event.loadTimeMs}ms"
            )
        }

        is RuntimeEvent.GenerationStarted -> {
            Log.d(
                TAG,
                "Event: GenerationStarted — session='${event.sessionId}' model='${event.modelName}'"
            )
        }

        is RuntimeEvent.GenerationFinished -> {
            Log.d(
                TAG,
                "Event: GenerationFinished — session='${event.sessionId}' tokens=${event.tokenCount} duration=${event.durationMs}ms"
            )
        }

        is RuntimeEvent.LoadingFailed -> {
            Log.e(
                TAG,
                "Event: LoadingFailed — reason='${event.reason}'",
                event.cause
            )
        }

        RuntimeEvent.RuntimeStopped -> {
            Log.i(TAG, "Event: RuntimeStopped")
        }

        is RuntimeEvent.ModelLoadStarted -> {
            Log.d(TAG, "Model load started: ${event.modelDisplayName}")
        }

        is RuntimeEvent.ModelLoadProgress -> {
            Log.d(TAG, "Loading ${event.modelId}: ${(event.progressFraction * 100).toInt()}%")
        }

        is RuntimeEvent.ModelLoadCompleted -> {
            Log.d(TAG, "Model loaded: ${event.modelId}")
        }

        is RuntimeEvent.ModelLoadFailed -> {
            Log.e(TAG, "Model load failed: ${event.reason}", event.cause)
        }

        is RuntimeEvent.ModelUnloadStarted -> {
            Log.d(TAG, "Model unload started: ${event.modelId}")
        }

        is RuntimeEvent.ModelUnloadCompleted -> {
            Log.d(TAG, "Model unloaded: ${event.modelId}")
        }

        is RuntimeEvent.WarmupStarted -> {
            Log.d(TAG, "Warmup started: ${event.modelId}")
        }

        is RuntimeEvent.WarmupCompleted -> {
            Log.d(TAG, "Warmup completed: ${event.modelId} (${event.durationMs} ms)")
        }

        is RuntimeEvent.RuntimeError -> {
            Log.e(TAG, event.message, event.cause)
        }
    }
}

    /**
     * Collects [RuntimeController.metricsFlow] for the application lifetime.
     *
     * Logs metrics whenever the model name is populated, which means the
     * runtime has progressed past the initial Idle state. Avoids flooding
     * the log with empty-metrics updates at startup.
     */
    private fun observeRuntimeMetrics() {
        applicationScope.launch {
            runtimeController.metricsFlow.collect { metrics ->
                if (metrics.currentModelName.isNotEmpty()) {
                    Log.v(
                        TAG,
                        "Metrics — model='${metrics.currentModelName}' " +
                            "uptime=${metrics.uptimeMs}ms " +
                            "tokens=${metrics.tokenCount} " +
                            "speed=${metrics.inferenceSpeedTokensPerSec}t/s " +
                            "memory=${metrics.memoryUsageBytes}B " +
                            "loadProgress=${metrics.loadingProgress}"
                    )
                }
            }
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        const val TAG = "OmnixRuntime"
    }
}
