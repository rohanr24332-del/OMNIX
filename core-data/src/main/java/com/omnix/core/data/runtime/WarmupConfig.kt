package com.omnix.core.data.runtime

/**
 * Controls how aggressively the runtime warms up a model after loading.
 *
 * NONE       — skip warmup; the first real request bears the cold-start cost.
 * MINIMAL    — one short prompt; catches JIT and cache misses cheaply.
 * STANDARD   — a few prompts covering typical request shapes.
 * AGGRESSIVE — a full set of prompts; maximises steady-state throughput
 *              at the cost of a longer startup delay.
 */
enum class WarmupStrategy {
    NONE,
    MINIMAL,
    STANDARD,
    AGGRESSIVE
}

/**
 * Configuration for the warmup pass run immediately after a model loads.
 *
 * [strategy] controls the number and length of warmup prompts.
 * [timeoutMs] is a hard ceiling on the whole warmup pass. If the pass
 * exceeds this duration it is cancelled and the model is still marked READY —
 * a partial warmup is better than blocking startup indefinitely.
 */
data class WarmupConfig(
    val strategy: WarmupStrategy = WarmupStrategy.MINIMAL,
    val timeoutMs: Long = 8_000L
) {
    companion object {
        /** Default warmup used by [ModelRuntimeManager]. */
        val DEFAULT = WarmupConfig()

        /** Skip warmup entirely. */
        val NONE = WarmupConfig(strategy = WarmupStrategy.NONE)

        /** Aggressive warmup for devices that can afford the startup cost. */
        val AGGRESSIVE = WarmupConfig(
            strategy = WarmupStrategy.AGGRESSIVE,
            timeoutMs = 30_000L
        )
    }

    /** Returns the list of prompts to run for the configured strategy. */
    val warmupPrompts: List<String>
        get() = when (strategy) {
            WarmupStrategy.NONE -> emptyList()

            WarmupStrategy.MINIMAL -> listOf(
                "Hello"
            )

            WarmupStrategy.STANDARD -> listOf(
                "Hello",
                "Explain photosynthesis briefly.",
                "Write a short function in Python."
            )

            WarmupStrategy.AGGRESSIVE -> listOf(
                "Hello",
                "Explain photosynthesis in detail.",
                "Write a binary search in Kotlin.",
                "Summarise the French Revolution in three sentences.",
                "Translate good morning into Japanese."
            )
        }
}
