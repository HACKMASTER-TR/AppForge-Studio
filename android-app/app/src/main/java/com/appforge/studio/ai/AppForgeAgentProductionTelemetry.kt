package com.appforge.studio.ai

import java.util.ArrayDeque

internal enum class AppForgeAgentTelemetryKindV10 {
    ADMISSION,
    QUEUED,
    STARTED,
    FINISHED,
    BLOCKED,
    CACHE_HIT,
    CACHE_MISS
}

internal data class AppForgeAgentTelemetryEventV10(
    val kind: AppForgeAgentTelemetryKindV10,
    val tenantScope: String,
    val jobScope: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val attributes: Map<String, String> = emptyMap()
)

internal fun interface AppForgeAgentTelemetrySinkV10 {
    fun emit(event: AppForgeAgentTelemetryEventV10)
}

internal object AppForgeAgentTelemetrySanitizerV10 {
    private val secretPatterns = listOf(
        Regex("ghp_[A-Za-z0-9]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b"),
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
    )
    private val keyPattern = Regex("^[A-Za-z][A-Za-z0-9_.-]{0,63}$")

    fun sanitize(attributes: Map<String, String>): Map<String, String> = attributes
        .entries
        .asSequence()
        .filter { keyPattern.matches(it.key) }
        .take(32)
        .associate { (key, rawValue) ->
            var value = rawValue.take(1_024)
            secretPatterns.forEach { pattern ->
                value = pattern.replace(value, "[REDACTED]")
            }
            key to value
        }
}

internal class AppForgeAgentBoundedTelemetrySinkV10(
    private val maxEvents: Int = 1_000
) : AppForgeAgentTelemetrySinkV10 {
    init {
        require(maxEvents in 1..100_000)
    }

    private val events = ArrayDeque<AppForgeAgentTelemetryEventV10>()

    @Synchronized
    override fun emit(event: AppForgeAgentTelemetryEventV10) {
        val safeEvent = event.copy(
            tenantScope = event.tenantScope.take(32),
            jobScope = event.jobScope.take(32),
            attributes = AppForgeAgentTelemetrySanitizerV10.sanitize(event.attributes)
        )
        while (events.size >= maxEvents) events.removeFirst()
        events.addLast(safeEvent)
    }

    @Synchronized
    fun snapshot(): List<AppForgeAgentTelemetryEventV10> = events.toList()
}
