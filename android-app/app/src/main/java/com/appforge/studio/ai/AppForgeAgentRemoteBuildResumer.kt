package com.appforge.studio.ai

import android.content.Context
import com.appforge.studio.build.BuildApiClient
import com.appforge.studio.build.BuildApiException
import com.appforge.studio.build.BuildStatusResult
import java.io.IOException

internal enum class AppForgeAgentRemoteBuildResumeOutcome {
    RUNNING, SUCCESS, FAILURE, TRACKING_TIMEOUT
}

internal data class AppForgeAgentRemoteBuildResumeResult(
    val outcome: AppForgeAgentRemoteBuildResumeOutcome,
    val remote: AppForgeAgentRemoteBuildInfo?,
    val message: String
)

internal object AppForgeAgentRemoteBuildResumePolicy {
    private val failures = setOf(
        "failed", "failure", "error", "cancelled", "canceled"
    )

    fun classify(
        status: String,
        artifactAvailable: Boolean
    ): AppForgeAgentRemoteBuildResumeOutcome {
        val normalized = status.trim().lowercase()
        return when {
            normalized == "success" && artifactAvailable ->
                AppForgeAgentRemoteBuildResumeOutcome.SUCCESS
            normalized == "success" ->
                AppForgeAgentRemoteBuildResumeOutcome.FAILURE
            normalized in failures ->
                AppForgeAgentRemoteBuildResumeOutcome.FAILURE
            else ->
                AppForgeAgentRemoteBuildResumeOutcome.RUNNING
        }
    }

    fun requireSafeBuildId(buildId: String): String {
        val clean = buildId.trim()
        require(
            clean.length in 6..160 &&
                clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        ) {
            "Cloud Build kimliği geçersiz."
        }
        return clean
    }
}

internal class AppForgeAgentRemoteBuildResumer(
    context: Context,
    buildServiceUrl: String,
    buildApiKey: String
) {
    private val client = BuildApiClient(
        context = context.applicationContext,
        baseUrl = buildServiceUrl.trim().ifBlank {
            "https://api.appforgecloud.com"
        },
        apiKey = buildApiKey
    )

    fun resume(
        buildId: String,
        onStatus: (AppForgeAgentRemoteBuildInfo, String) -> Unit = { _, _ -> }
    ): AppForgeAgentRemoteBuildResumeResult {
        val safeBuildId =
            AppForgeAgentRemoteBuildResumePolicy.requireSafeBuildId(buildId)

        var lastRemote: AppForgeAgentRemoteBuildInfo? = null
        var consecutiveFailures = 0

        repeat(MAX_POLL_ATTEMPTS) { index ->
            try {
                val status = client.getBuild(safeBuildId)

                require(status.buildId == safeBuildId) {
                    "Cloud Build kimliği değişti."
                }

                consecutiveFailures = 0
                val remote = status.toRemoteInfo()
                lastRemote = remote

                onStatus(
                    remote,
                    "RESUME BUILD${remote.buildNo?.let { " #$it" }.orEmpty()} • " +
                        "${remote.status} • %${remote.progress}"
                )

                val outcome =
                    AppForgeAgentRemoteBuildResumePolicy.classify(
                        status = status.status,
                        artifactAvailable =
                            status.apkAvailable ||
                                status.aabAvailable ||
                                status.exeAvailable
                    )

                if (outcome == AppForgeAgentRemoteBuildResumeOutcome.SUCCESS) {
                    return AppForgeAgentRemoteBuildResumeResult(
                        outcome = outcome,
                        remote = remote,
                        message =
                            "Mevcut Cloud Build tamamlandı; yeni build başlatılmadı."
                    )
                }

                if (outcome == AppForgeAgentRemoteBuildResumeOutcome.FAILURE) {
                    return AppForgeAgentRemoteBuildResumeResult(
                        outcome = outcome,
                        remote = remote,
                        message = failureMessage(status)
                    )
                }
            } catch (error: Throwable) {
                if (!isTransientNetwork(error)) {
                    return AppForgeAgentRemoteBuildResumeResult(
                        outcome = AppForgeAgentRemoteBuildResumeOutcome.FAILURE,
                        remote = lastRemote,
                        message = safeFailure(error)
                    )
                }

                consecutiveFailures += 1
                if (consecutiveFailures > MAX_CONSECUTIVE_POLL_FAILURES) {
                    return AppForgeAgentRemoteBuildResumeResult(
                        outcome =
                            AppForgeAgentRemoteBuildResumeOutcome.TRACKING_TIMEOUT,
                        remote = lastRemote,
                        message =
                            "Cloud Build devam ediyor olabilir; bağlantı kesildi. " +
                                "Aynı buildId korundu ve yeni build başlatılmadı."
                    )
                }
            }

            if (index + 1 < MAX_POLL_ATTEMPTS) {
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }

        return AppForgeAgentRemoteBuildResumeResult(
            outcome = AppForgeAgentRemoteBuildResumeOutcome.TRACKING_TIMEOUT,
            remote = lastRemote,
            message =
                "Cloud Build takip süresi doldu. Uzak build iptal edilmedi; " +
                    "aynı buildId korundu."
        )
    }

    private fun BuildStatusResult.toRemoteInfo() =
        AppForgeAgentRemoteBuildInfo(
            buildId = buildId,
            buildNo = buildNo,
            status = status,
            progress = progress.coerceIn(0, 100),
            apkAvailable = apkAvailable,
            aabAvailable = aabAvailable,
            exeAvailable = exeAvailable,
            logs = logs.takeLast(120),
            preflight = preflight.takeLast(80)
        )

    private fun failureMessage(status: BuildStatusResult): String =
        buildString {
            append(
                "Cloud Build tamamlanamadı • buildId=${status.buildId} • " +
                    "status=${status.status}"
            )
            status.preflight.takeLast(20).forEach {
                appendLine()
                append(AppForgeAgentArtifactSafety.sanitize(it, 1_000))
            }
            status.logs.takeLast(40).forEach {
                appendLine()
                append(AppForgeAgentArtifactSafety.sanitize(it, 1_000))
            }
        }.takeLast(MAX_FAILURE_CHARS)

    private fun isTransientNetwork(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is IOException) return true
            if (
                current is BuildApiException &&
                (current.statusCode == 429 || current.statusCode >= 500)
            ) {
                return true
            }

            val text = current.message.orEmpty().lowercase()
            if (
                listOf(
                    "timed out",
                    "timeout",
                    "connection reset",
                    "unable to resolve host",
                    "failed to connect",
                    "network is unreachable"
                ).any(text::contains)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun safeFailure(error: Throwable): String =
        AppForgeAgentArtifactSafety.sanitize(
            error.message ?: error::class.simpleName ?: "Cloud Build resume başarısız.",
            MAX_FAILURE_CHARS
        )

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_POLL_ATTEMPTS = 600
        const val MAX_CONSECUTIVE_POLL_FAILURES = 4
        const val MAX_FAILURE_CHARS = 32 * 1024
    }
}
