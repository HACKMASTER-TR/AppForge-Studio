package com.appforge.studio.ai

import java.security.MessageDigest

internal enum class AppForgeAgentFailurePhase {
    HEALTH,
    TOOLCHAINS,
    INSTALL,
    TEST,
    BUILD,
    DEPLOY_GATE,
    UNKNOWN
}

internal data class AppForgeAgentFailure(
    val phase: AppForgeAgentFailurePhase,
    val exitCode: Int? = null,
    val output: String,
    val command: String? = null
)

internal enum class AppForgeAgentRepairAction {
    RETRY_PIPELINE,
    REQUEST_STRUCTURED_PATCH,
    STOP_MANUAL
}

internal data class AppForgeAgentRepairDecisionV5(
    val action: AppForgeAgentRepairAction,
    val fingerprint: String,
    val attempt: Int,
    val reason: String
)

internal data class AppForgeAgentRepairLoopConfig(
    val maxRepairAttempts: Int = 2,
    val maxSameFingerprint: Int = 1
) {
    init {
        require(maxRepairAttempts in 0..3) {
            "Repair attempt sınırı 0..3 aralığında olmalı."
        }
        require(maxSameFingerprint in 1..2) {
            "Aynı hata fingerprint sınırı 1..2 aralığında olmalı."
        }
    }
}

internal class AppForgeAgentRepairLoop(
    private val config: AppForgeAgentRepairLoopConfig = AppForgeAgentRepairLoopConfig()
) {
    private val fingerprintCounts = linkedMapOf<String, Int>()
    private var repairAttempts = 0

    val attempts: Int
        get() = repairAttempts

    fun decide(failure: AppForgeAgentFailure): AppForgeAgentRepairDecisionV5 {
        val fingerprint = AppForgeAgentFailureFingerprint.of(failure)
        val seen = (fingerprintCounts[fingerprint] ?: 0) + 1
        fingerprintCounts[fingerprint] = seen

        if (repairAttempts >= config.maxRepairAttempts) {
            return stop(
                fingerprint = fingerprint,
                reason = "Agent repair güvenli deneme sınırına ulaştı."
            )
        }

        if (seen > config.maxSameFingerprint) {
            return stop(
                fingerprint = fingerprint,
                reason = "Aynı hata fingerprint'i tekrarlandı; sonsuz düzeltme döngüsü engellendi."
            )
        }

        return when (failure.phase) {
            AppForgeAgentFailurePhase.INSTALL -> {
                if (AppForgeAgentFailureFingerprint.isTransientInstall(failure.output)) {
                    repairAttempts += 1
                    AppForgeAgentRepairDecisionV5(
                        action = AppForgeAgentRepairAction.RETRY_PIPELINE,
                        fingerprint = fingerprint,
                        attempt = repairAttempts,
                        reason = "Geçici bağımlılık/ağ hatası algılandı; doğrulanmış pipeline sınırlı biçimde yeniden denenebilir."
                    )
                } else {
                    requestPatch(fingerprint)
                }
            }

            AppForgeAgentFailurePhase.TEST,
            AppForgeAgentFailurePhase.BUILD -> requestPatch(fingerprint)

            AppForgeAgentFailurePhase.HEALTH,
            AppForgeAgentFailurePhase.TOOLCHAINS,
            AppForgeAgentFailurePhase.DEPLOY_GATE,
            AppForgeAgentFailurePhase.UNKNOWN -> stop(
                fingerprint = fingerprint,
                reason = "Bu hata aşaması otomatik kaynak düzeltmesine uygun değil; manuel kontrol gerekli."
            )
        }
    }

    fun reset() {
        repairAttempts = 0
        fingerprintCounts.clear()
    }

    private fun requestPatch(fingerprint: String): AppForgeAgentRepairDecisionV5 {
        repairAttempts += 1
        return AppForgeAgentRepairDecisionV5(
            action = AppForgeAgentRepairAction.REQUEST_STRUCTURED_PATCH,
            fingerprint = fingerprint,
            attempt = repairAttempts,
            reason = "Kaynak/config düzeltmesi yalnız doğrulanmış structured patch planı ile uygulanabilir."
        )
    }

    private fun stop(
        fingerprint: String,
        reason: String
    ) = AppForgeAgentRepairDecisionV5(
        action = AppForgeAgentRepairAction.STOP_MANUAL,
        fingerprint = fingerprint,
        attempt = repairAttempts,
        reason = reason
    )
}

internal object AppForgeAgentFailureFingerprint {
    private const val MAX_NORMALIZED_OUTPUT = 12_000

    private val transientInstallSignals = listOf(
        "timed out",
        "timeout",
        "temporarily unavailable",
        "temporary failure",
        "connection reset",
        "econnreset",
        "eai_again",
        "too many requests",
        "http 429",
        "network is unreachable",
        "socket hang up",
        "could not resolve host"
    )

    private val secretPatterns = listOf(
        Regex("ghp_[A-Za-z0-9]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b"),
        Regex("(?i)(password|token|secret|api[_ -]?key)\\s*[:=]\\s*\\S+")
    )

    fun of(failure: AppForgeAgentFailure): String {
        val normalized = normalize(failure.output)
        val command = failure.command
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(500)
            .orEmpty()

        val input = buildString {
            append(failure.phase.name)
            append('\n')
            append(failure.exitCode ?: -1)
            append('\n')
            append(command)
            append('\n')
            append(normalized)
        }

        return sha256(input)
    }

    fun normalize(raw: String): String {
        var text = raw
            .replace("\\r\\n", "\\n")
            .replace('\r', '\n')

        secretPatterns.forEach { pattern ->
            text = text.replace(pattern, "[REDACTED]")
        }

        return text
            .lineSequence()
            .map { line ->
                line.trim()
                    .replace(Regex("0x[0-9A-Fa-f]+"), "0xADDR")
                    .replace(Regex("\\b\\d{4}-\\d{2}-\\d{2}[T ][0-9:.+Z-]+\\b"), "TIMESTAMP")
                    .replace(Regex("\\b\\d+(?:\\.\\d+)?ms\\b", RegexOption.IGNORE_CASE), "DURATION")
                    .replace(Regex("\\s+"), " ")
            }
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(120)
            .joinToString("\n")
            .take(MAX_NORMALIZED_OUTPUT)
    }

    fun isTransientInstall(output: String): Boolean {
        val lower = output.lowercase()
        return transientInstallSignals.any { signal -> signal in lower }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
