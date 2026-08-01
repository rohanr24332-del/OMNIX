package com.omnix.core.data.runtime

import com.omnix.core.data.inference.InferenceException
import com.omnix.core.data.inference.InferenceProvider
import com.omnix.core.data.inference.InferenceRequest
import com.omnix.core.data.inference.ModelRuntime
import com.omnix.core.data.inference.StreamingToken
import com.omnix.core.data.inference.providers.SimulatedInferenceProvider
import com.omnix.core.data.runtime.TokenStreamPipeline.withRuntimePipeline
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Central runtime coordinator for OMNIX.
 *
 * Phase 6.3–6.6 responsibilities:
 * - Implements [InferenceProvider] so [com.omnix.core.data.inference.InferenceEngine]
 *   and [com.omnix.feature.chat.ChatViewModel] require zero changes.
 * - Maintains the high-level [RuntimeState] and [RuntimeMetrics] observed by
 *   [com.omnix.app.OmnixApplication].
 * - Forwards all [RuntimeEvent]s from [RuntimeEventBus] (emitted by
 *   [ModelRuntimeManager]) to the [events] SharedFlow so external observers
 *   see both controller events and loader events through one stream.
 * - Wires the full generation pipeline:
 *
 *   streamTokens(request)
 *     1. Resolve active model via [ModelRuntime]
 *     2. [ModelRuntimeManager.ensureModelLoaded] — returns immediately if ready
 *     3. Create [GenerationSession], register with [ActiveSessionManager]
 *     4. [RuntimeMetricsTracker.sessionStarted]
 *     5. Emit [RuntimeEvent.GenerationStarted]
 *     6. [SimulatedInferenceProvider.streamTokens] wrapped by
 *        [TokenStreamPipeline.withRuntimePipeline] (cancellation + metrics + buffer)
 *     7. Emit each [StreamingToken] to the caller
 *     8. finally: finalise metrics, unregister session, update state, emit
 *        [RuntimeEvent.GenerationFinished]
 *
 * Phase 7 integration: replace step 6 with a real JNI or HTTP provider.
 * Steps 1–5 and 7–8 remain unchanged.
 */
@Singleton
class RuntimeController @Inject constructor(
    private val runtimeManager: ModelRuntimeManager,
    private val activeSessionManager: ActiveSessionManager,
    private val metricsTracker: RuntimeMetricsTracker,
    private val eventBus: RuntimeEventBus,
    private val modelRuntime: ModelRuntime,
    private val simulatedProvider: SimulatedInferenceProvider
) : InferenceProvider {

    // ── InferenceProvider identity ────────────────────────────────────────────

    override val providerId: String = "omnix_runtime_v6"
    override val displayName: String = "OMNIX Runtime"

    // ── Coroutine scope ───────────────────────────────────────────────────────

    /**
     * Long-lived scope for background tasks: event bus forwarding and
     * any future proactive operations. Uses [SupervisorJob] so a failed
     * child does not cancel siblings.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)

    /**
     * High-level lifecycle state of the runtime.
     * Observed by [com.omnix.app.OmnixApplication] and feature ViewModels.
     */
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    // ── Events ────────────────────────────────────────────────────────────────

    private val _events = MutableSharedFlow<RuntimeEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    /**
     * Hot stream of all runtime events.
     *
     * Includes both events emitted directly by this controller and events
     * forwarded from [RuntimeEventBus] (e.g. model-load progress from
     * [ModelRuntimeManager]).
     */
    val events: SharedFlow<RuntimeEvent> = _events.asSharedFlow()

    // ── Metrics ───────────────────────────────────────────────────────────────

    private val _metrics = MutableStateFlow(RuntimeMetrics())

    /**
     * High-level operational snapshot. Updated on each state change and
     * after each generation session completes.
     */
    val metricsFlow: StateFlow<RuntimeMetrics> = _metrics.asStateFlow()

    /** Returns the current [RuntimeState] as an immediate value. */
    fun currentState(): RuntimeState = _state.value

    /** Returns a [RuntimeMetrics] snapshot with a live [RuntimeMetrics.uptimeMs]. */
    fun metrics(): RuntimeMetrics {
        val uptime = if (initTimestampMs > 0L) System.currentTimeMillis() - initTimestampMs else 0L
        return _metrics.value.copy(uptimeMs = uptime)
    }

    /** Timestamp recorded when [initialize] is called. */
    private var initTimestampMs: Long = 0L

    // ── Initialization ────────────────────────────────────────────────────────

    init {
        forwardEventBusToEventStream()
    }

    /**
     * Forwards all events emitted by [RuntimeEventBus] (primarily from
     * [ModelRuntimeManager]) to this controller's [events] SharedFlow so
     * external consumers see a unified event stream.
     */
    private fun forwardEventBusToEventStream() {
        scope.launch {
            eventBus.events.collect { event ->
                _events.tryEmit(event)
            }
        }
    }

    // ── Lifecycle methods ─────────────────────────────────────────────────────

    /**
     * Initialises the runtime on application startup.
     *
     * Transitions: [RuntimeState.Idle] → [RuntimeState.Loading] → [RuntimeState.Ready]
     *
     * Actual model loading is reactive — it happens automatically in
     * [ModelRuntimeManager] when the user selects a model in Model Manager.
     * This method only establishes that the runtime framework is ready.
     *
     * Idempotent: returns immediately if already [RuntimeState.Ready] or
     * [RuntimeState.Running].
     */
    suspend fun initialize() {
        val current = _state.value
        if (current is RuntimeState.Ready || current is RuntimeState.Running) {
            return
        }

        _state.value = RuntimeState.Loading(progress = 0f)
        initTimestampMs = System.currentTimeMillis()
        updateMetrics(loadingProgress = 0f)

        // Transition through Loading to show framework initialisation.
        // Model loading happens separately via ModelRuntimeManager.
        _state.value = RuntimeState.Loading(progress = 1f)
        updateMetrics(loadingProgress = 1f)

        _state.value = RuntimeState.Ready
        updateMetrics(
            currentModelName = RUNTIME_READY_LABEL,
            loadingProgress = 1f
        )

        _events.emit(
            RuntimeEvent.ModelLoaded(
                modelName = RUNTIME_READY_LABEL,
                loadTimeMs = System.currentTimeMillis() - initTimestampMs
            )
        )
    }

    /**
     * Shuts the runtime down and releases all resources.
     *
     * Cancels all active generation sessions, transitions to
     * [RuntimeState.Idle], and emits [RuntimeEvent.RuntimeStopped].
     */
    suspend fun shutdown() {
        activeSessionManager.cancelAll()
        _state.value = RuntimeState.Idle
        initTimestampMs = 0L
        _metrics.value = RuntimeMetrics()
        _events.emit(RuntimeEvent.RuntimeStopped)
    }

    /**
     * Pauses an active generation. Only transitions if currently [RuntimeState.Running].
     */
    fun pause() {
        _state.update { current ->
            if (current is RuntimeState.Running) {
                RuntimeState.Paused
            } else {
                current
            }
        }
    }

    /**
     * Resumes from [RuntimeState.Paused] back to [RuntimeState.Ready].
     */
    fun resume() {
        _state.update { current ->
            if (current is RuntimeState.Paused) {
                RuntimeState.Ready
            } else {
                current
            }
        }
    }

    // ── InferenceProvider implementation ──────────────────────────────────────

    override suspend fun isAvailable(): Boolean {
        return _state.value.isGenerationAllowed() || _state.value.isModelInMemory()
    }

    /**
     * Streams tokens for [request] through the full runtime pipeline.
     *
     * This is the seam that wires:
     *   RuntimeController → ModelRuntimeManager → ModelLoader
     *     → GenerationSession → TokenStreamPipeline → caller
     */
    override fun streamTokens(request: InferenceRequest): Flow<StreamingToken> = flow {

        // 1. Resolve the active model. Throws if none is selected.
        val modelInfo = modelRuntime.activeModel.value
            ?: throw InferenceException.NoActiveModel()

        // 2. Ensure the model is loaded and ready.
        //    Returns immediately if already READY; loads synchronously if not.
        val loadStartMs = System.currentTimeMillis()
        val modelSession = runtimeManager.ensureModelLoaded(modelInfo.id, modelInfo)
        val loadTimeMs = if (modelSession.loadTimeMs > 0L) {
            modelSession.loadTimeMs
        } else {
            System.currentTimeMillis() - loadStartMs
        }

        // 3. Create and register the generation session.
        val sessionId = request.sessionId.ifBlank { UUID.randomUUID().toString() }
        val genSession = GenerationSession(
            id = sessionId,
            modelId = modelInfo.id,
            mode = request.mode,
            promptTokens = estimatePromptTokens(request)
        )
        genSession.state = GenerationSessionState.RUNNING
        activeSessionManager.register(genSession)

        // 4. Start metrics tracking.
        metricsTracker.sessionStarted(genSession, loadTimeMs)

        // 5. Transition runtime state and announce start.
        _state.value = RuntimeState.Running(modelInfo.displayName)
        eventBus.tryEmit(RuntimeEvent.GenerationStarted(sessionId, modelInfo.displayName))

        var finishReason = GenerationFinishReason.COMPLETED

        try {
            // 6. Stream tokens from the provider through the runtime pipeline.
            //    withRuntimePipeline applies: cancellation check, token counter,
            //    and backpressure buffer in that order.
            simulatedProvider
                .streamTokens(request)
                .withRuntimePipeline(genSession)
                .collect { token ->
                    emit(token)
                }

        } catch (e: InferenceException.GenerationCancelled) {
            finishReason = GenerationFinishReason.CANCELLED
            genSession.state = GenerationSessionState.CANCELLED
            throw e

        } catch (e: CancellationException) {
            finishReason = GenerationFinishReason.CANCELLED
            genSession.state = GenerationSessionState.CANCELLED
            throw e

        } catch (e: InferenceException) {
            finishReason = GenerationFinishReason.ERROR
            genSession.state = GenerationSessionState.FAILED
            throw e

        } catch (e: Throwable) {
            finishReason = GenerationFinishReason.ERROR
            genSession.state = GenerationSessionState.FAILED
            throw InferenceException.ProviderError(
                providerId = providerId,
                detail = e.message ?: "Unknown error",
                cause = e
            )

        } finally {
            // 7. Finalise regardless of how the session ended.
            if (genSession.state == GenerationSessionState.RUNNING) {
                genSession.state = GenerationSessionState.COMPLETED
            }

            val sessionMetrics = metricsTracker.sessionEnded(genSession, finishReason)
            activeSessionManager.unregister(sessionId)

            if (sessionMetrics != null && finishReason == GenerationFinishReason.COMPLETED) {
                updateMetrics(
                    tokenCount = sessionMetrics.generatedTokens,
                    inferenceSpeedTokensPerSec = sessionMetrics.tokensPerSecond
                )
                _events.tryEmit(
                    RuntimeEvent.GenerationFinished(
                        sessionId = sessionId,
                        tokenCount = sessionMetrics.generatedTokens,
                        durationMs = sessionMetrics.generationTimeMs
                    )
                )
            }

            // Return to Ready once no more sessions are active.
            if (!activeSessionManager.hasActiveSessions()) {
                _state.value = RuntimeState.Ready
            }
        }
    }

    override suspend fun cancel(sessionId: String) {
        activeSessionManager.cancel(sessionId)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Updates the [_metrics] StateFlow with the provided delta values.
     * Any parameter left null retains its current value.
     */
    private fun updateMetrics(
        currentModelName: String? = null,
        memoryUsageBytes: Long? = null,
        tokenCount: Int? = null,
        inferenceSpeedTokensPerSec: Float? = null,
        loadingProgress: Float? = null
    ) {
        _metrics.update { current ->
            val uptime = if (initTimestampMs > 0L) {
                System.currentTimeMillis() - initTimestampMs
            } else {
                0L
            }
            current.copy(
                currentModelName = currentModelName ?: current.currentModelName,
                memoryUsageBytes = memoryUsageBytes ?: current.memoryUsageBytes,
                tokenCount = tokenCount ?: current.tokenCount,
                inferenceSpeedTokensPerSec = inferenceSpeedTokensPerSec
                    ?: current.inferenceSpeedTokensPerSec,
                uptimeMs = uptime,
                loadingProgress = loadingProgress ?: current.loadingProgress
            )
        }
    }

    /**
     * Estimates the prompt token count for [request] using a 4-chars-per-token
     * heuristic. Replaced by a real tokenizer call once a native backend is
     * integrated in Phase 7.
     */
    private fun estimatePromptTokens(request: InferenceRequest): Int {
        return request.messages.sumOf { it.content.length } / 4
    }

    private companion object {
        const val RUNTIME_READY_LABEL = "OMNIX Runtime Ready"
    }
}
