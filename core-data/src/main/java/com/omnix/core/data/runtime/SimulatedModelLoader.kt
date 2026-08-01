package com.omnix.core.data.runtime

import com.omnix.core.model.ModelInfo
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Simulated [ModelLoader] for Phase 6.
 *
 * Produces realistic observable behaviour without JNI or file I/O:
 * - Load delay is proportional to [ModelInfo.sizeGb] at [MS_PER_GB] ms per GB,
 *   with a minimum of [MIN_LOAD_MS].
 * - Progress is emitted in 20 increments of 5% so UI progress bars animate smoothly.
 * - Warmup runs one short delay per [WarmupConfig.warmupPrompts] entry.
 * - Unload has a short fixed delay simulating memory deallocation.
 *
 * Phase 7 replacement: implement [ModelLoader], bind it in
 * [com.omnix.core.data.di.RuntimeModule], and remove this class's binding.
 * Nothing else changes.
 */
@Singleton
class SimulatedModelLoader @Inject constructor() : ModelLoader {

    private val loadedModels = ConcurrentHashMap<String, ModelSession>()

    override suspend fun load(
        modelInfo: ModelInfo,
        onProgress: suspend (Float) -> Unit
    ): ModelSession {
        val startMs = System.currentTimeMillis()
        val totalDelayMs = (modelInfo.sizeGb * MS_PER_GB)
            .toLong()
            .coerceAtLeast(MIN_LOAD_MS)
        val steps = 20
        val stepDelayMs = totalDelayMs / steps

        repeat(steps) { step ->
            delay(stepDelayMs)
            val progress = (step + 1).toFloat() / steps.toFloat()
            onProgress(progress)
        }

        val session = ModelSession(
            modelId = modelInfo.id,
            modelInfo = modelInfo,
            state = ModelSessionState.WARMING_UP,
            loadStartedAtMs = startMs,
            loadCompletedAtMs = System.currentTimeMillis()
        )
        loadedModels[modelInfo.id] = session
        return session
    }

    override suspend fun warmup(
        session: ModelSession,
        config: WarmupConfig
    ): ModelSession {
        if (config.strategy == WarmupStrategy.NONE) {
            val readySession = session.withWarmupCompleted()
            loadedModels[session.modelId] = readySession
            return readySession
        }

        config.warmupPrompts.forEach { _ ->
            delay(WARMUP_PROMPT_DELAY_MS)
        }

        val readySession = session.withWarmupCompleted()
        loadedModels[session.modelId] = readySession
        return readySession
    }

    override suspend fun unload(session: ModelSession) {
        delay(UNLOAD_DELAY_MS)
        loadedModels.remove(session.modelId)
    }

    override fun isLoaded(modelId: String): Boolean {
        return loadedModels.containsKey(modelId)
    }

    private companion object {
        const val MS_PER_GB = 800L
        const val MIN_LOAD_MS = 600L
        const val WARMUP_PROMPT_DELAY_MS = 150L
        const val UNLOAD_DELAY_MS = 150L
    }
}
