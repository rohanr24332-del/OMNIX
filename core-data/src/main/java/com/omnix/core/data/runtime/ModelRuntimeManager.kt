package com.omnix.core.data.runtime

import com.omnix.core.data.inference.ModelRuntime
import com.omnix.core.model.ModelInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central manager for the local model lifecycle.
 *
 * Holds at most one [ModelSession] in memory at a time (Phase 6).
 *
 * Responsibilities:
 * - [ensureModelLoaded]: called by [RuntimeController] before every generation.
 *   Returns immediately on fast path if the correct model is already READY.
 * - [unloadCurrentModel]: releases the resident model on demand.
 * - Reactive pre-loading: observes [ModelRuntime.activeModel] and begins loading
 *   the newly selected model proactively so the first request after a model
 *   selection change is faster.
 * - Emits [RuntimeEvent]s to [RuntimeEventBus] and calls [ModelLifecycle]
 *   callbacks throughout load, warmup, and unload.
 *
 * Thread safety: all state mutations are serialised through [mutex]. The reactive
 * pre-load path cancels any in-progress pre-load before starting a new one.
 */
@Singleton
class ModelRuntimeManager @Inject constructor(
    private val modelLoader: ModelLoader,
    private val eventBus: RuntimeEventBus,
    private val activeSessionManager: ActiveSessionManager,
    private val modelRuntime: ModelRuntime,
    private val lifecycleObservers: Set<@JvmSuppressWildcards ModelLifecycle>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _managerState = MutableStateFlow(RuntimeManagerState.IDLE)

    /** Observable [RuntimeManagerState] for diagnostics and future UI components. */
    val managerState: StateFlow<RuntimeManagerState> = _managerState.asStateFlow()

    private val _loadedSession = MutableStateFlow<ModelSession?>(null)

    /** The currently loaded [ModelSession], or null if no model is in memory. */
    val loadedSession: StateFlow<ModelSession?> = _loadedSession.asStateFlow()

    private var preloadJob: Job? = null

    init {
        observeActiveModelChanges()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ensures the model identified by [modelId] is loaded and READY.
     *
     * Fast path: if the correct model is already [ModelSessionState.READY],
     * returns immediately without acquiring [mutex].
     *
     * Slow path: acquires [mutex], unloads any wrong model if present,
     * then loads and warms up [modelInfo].
     */
    suspend fun ensureModelLoaded(
        modelId: String,
        modelInfo: ModelInfo
    ): ModelSession {
        _loadedSession.value
            ?.takeIf { it.modelId == modelId && it.isReady }
            ?.let { return it }

        return mutex.withLock {
            _loadedSession.value
                ?.takeIf { it.modelId == modelId && it.isReady }
                ?.let { return@withLock it }

            _loadedSession.value?.let { existing ->
                unloadInternal(existing)
            }

            loadInternal(modelInfo)
        }
    }

    /**
     * Unloads the currently resident model and cancels all active sessions.
     * No-op if no model is loaded.
     */
    suspend fun unloadCurrentModel() {
        mutex.withLock {
            _loadedSession.value?.let { unloadInternal(it) }
        }
    }

    // ── Internal load / unload ────────────────────────────────────────────────

    private suspend fun loadInternal(modelInfo: ModelInfo): ModelSession {
        val modelId = modelInfo.id
        val estimatedSizeMb = (modelInfo.sizeGb * 1_024f).toInt()

        _managerState.value = RuntimeManagerState.LOADING
        lifecycleObservers.forEach { it.onModelLoading(modelId, estimatedSizeMb) }
        eventBus.emit(
            RuntimeEvent.ModelLoadStarted(
                modelId = modelId,
                modelDisplayName = modelInfo.displayName,
                estimatedSizeMb = estimatedSizeMb
            )
        )

        val loadStartMs = System.currentTimeMillis()
        val session = try {
            modelLoader.load(modelInfo) { progress ->
                eventBus.tryEmit(RuntimeEvent.ModelLoadProgress(modelId, progress))
            }
        } catch (throwable: Throwable) {
            val reason = throwable.message ?: "Unknown load error"
            _managerState.value = RuntimeManagerState.ERROR
            eventBus.emit(RuntimeEvent.ModelLoadFailed(modelId, reason, throwable))
            throw ModelLoadException(modelId, reason, throwable)
        }

        val loadTimeMs = System.currentTimeMillis() - loadStartMs
        lifecycleObservers.forEach { it.onModelLoaded(modelId, loadTimeMs) }
        eventBus.emit(RuntimeEvent.ModelLoadCompleted(modelId, loadTimeMs))

        _managerState.value = RuntimeManagerState.WARMING_UP
        lifecycleObservers.forEach { it.onWarmupStarted(modelId) }
        eventBus.emit(RuntimeEvent.WarmupStarted(modelId))

        val warmupStartMs = System.currentTimeMillis()
        val readySession = modelLoader.warmup(session, WarmupConfig.DEFAULT)
        val warmupMs = System.currentTimeMillis() - warmupStartMs

        lifecycleObservers.forEach { it.onWarmupCompleted(modelId, warmupMs) }
        eventBus.emit(RuntimeEvent.WarmupCompleted(modelId, warmupMs))

        _loadedSession.value = readySession
        _managerState.value = RuntimeManagerState.READY
        return readySession
    }

    private suspend fun unloadInternal(session: ModelSession) {
        val modelId = session.modelId

        activeSessionManager.cancelAll()

        _managerState.value = RuntimeManagerState.UNLOADING
        lifecycleObservers.forEach { it.onModelUnloading(modelId) }
        eventBus.emit(RuntimeEvent.ModelUnloadStarted(modelId))

        modelLoader.unload(session)

        _loadedSession.value = null
        _managerState.value = RuntimeManagerState.IDLE
        lifecycleObservers.forEach { it.onModelUnloaded(modelId) }
        eventBus.emit(RuntimeEvent.ModelUnloadCompleted(modelId))
    }

    // ── Reactive pre-load ─────────────────────────────────────────────────────

    private fun observeActiveModelChanges() {
        scope.launch {
            modelRuntime.activeModel.collect { modelInfo ->
                modelInfo ?: return@collect

                val current = _loadedSession.value
                if (current != null &&
                    current.modelId == modelInfo.id &&
                    current.isReady
                ) {
                    return@collect
                }

                preloadJob?.cancel()
                preloadJob = scope.launch {
                    runCatching {
                        mutex.withLock {
                            val existing = _loadedSession.value
                            if (existing != null &&
                                existing.modelId == modelInfo.id &&
                                existing.isReady
                            ) {
                                return@withLock
                            }
                            existing?.let { unloadInternal(it) }
                            loadInternal(modelInfo)
                        }
                    }.onFailure { throwable ->
                        eventBus.tryEmit(
                            RuntimeEvent.RuntimeError(
                                message = "Pre-load failed: ${throwable.message}",
                                cause = throwable
                            )
                        )
                    }
                }
            }
        }
    }
}
