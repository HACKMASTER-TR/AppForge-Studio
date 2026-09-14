package com.appforge.studio.ai

import android.content.Context
import com.appforge.studio.build.BuildApiClient
import com.appforge.studio.build.BuildApiException
import com.appforge.studio.build.BuildStatusResult
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode
import java.io.File
import java.io.IOException

internal data class AppForgeAgentRemoteBuildInfo(
    val buildId: String,
    val buildNo: Long?,
    val status: String,
    val progress: Int,
    val apkAvailable: Boolean,
    val aabAvailable: Boolean,
    val exeAvailable: Boolean,
    val logs: List<String> = emptyList(),
    val preflight: List<String> = emptyList()
)

internal class AppForgeAgentBuildServiceStageRunner(
    context: Context,
    private val blueprint: AppForgeAgentBlueprint,
    private val buildServiceUrl: String,
    private val buildApiKey: String,
    private val onStatus: (
        AppForgeAgentRemoteBuildInfo?,
        String
    ) -> Unit = { _, _ -> }
) : AppForgeAgentStageRunner {
    private val appContext = context.applicationContext
    private val effectiveBuildServiceUrl =
        buildServiceUrl.trim().ifBlank {
            "https://api.appforgecloud.com"
        }

    private val client = BuildApiClient(
        context = appContext,
        baseUrl = effectiveBuildServiceUrl,
        apiKey = buildApiKey
    )

    @Volatile
    var lastBuild: AppForgeAgentRemoteBuildInfo? = null
        private set

    override fun run(
        stage: AppForgeAgentExecutionStage,
        workspace: File
    ): AppForgeAgentExecutionResult =
        when (stage) {
            AppForgeAgentExecutionStage.TEST ->
                runTestGate(workspace)

            AppForgeAgentExecutionStage.BUILD ->
                runBuildGate(workspace)
        }

    private fun runTestGate(
        workspace: File
    ): AppForgeAgentExecutionResult {
        var prepared: AppForgeAgentPreparedBuildProject? = null
        return try {
            onStatus(
                null,
                "TEST • proje yapısı ve AppForge build motoru doğrulanıyor..."
            )

            prepared = AppForgeAgentBuildProjectPreparer.prepare(
                cacheRoot = appContext.cacheDir,
                workspace = workspace,
                blueprint = blueprint
            )

            require(prepared.zipFile.length() > 0L) {
                "TEST gate build ZIP'i boş."
            }

            val digest =
                AppForgeAgentBuildProjectPreparer.workspaceDigest(
                    workspace,
                    blueprint.platform
                )

            AppForgeAgentExecutionResult(
                success = true,
                output =
                    "TEST PASS • ${prepared.sourceTechnologyLabel} • " +
                    "${prepared.buildEngine} • digest=${digest.take(16)}"
            )
        } catch (error: Throwable) {
            AppForgeAgentExecutionResult(
                success = false,
                exitCode = 1,
                output = safeFailure(error)
            )
        } finally {
            prepared?.cleanup()
        }
    }

    private fun runBuildGate(
        workspace: File
    ): AppForgeAgentExecutionResult {
        var prepared: AppForgeAgentPreparedBuildProject? = null

        return try {
            prepared = AppForgeAgentBuildProjectPreparer.prepare(
                cacheRoot = appContext.cacheDir,
                workspace = workspace,
                blueprint = blueprint
            )

            val digest =
                AppForgeAgentBuildProjectPreparer.workspaceDigest(
                    workspace,
                    blueprint.platform
                )

            val draft = projectDraft(
                prepared = prepared,
                blueprint = blueprint
            )

            onStatus(
                null,
                "BUILD • AppForge Build Service'e güvenli kaynak yükleniyor..."
            )

            val created = retryNetwork {
                client.createBuild(
                    draft = draft,
                    projectZip = prepared.zipFile,
                    idempotencyKey =
                        "ua-${blueprint.platform.name.lowercase()}-${digest.take(48)}"
                )
            }

            var consecutivePollFailures = 0
            repeat(MAX_POLL_ATTEMPTS) { index ->
                try {
                    val status = client.getBuild(created.buildId)
                    consecutivePollFailures = 0

                    val info = status.toRemoteInfo()
                    lastBuild = info

                    onStatus(
                        info,
                        buildStatusMessage(info)
                    )

                    val normalized =
                        status.status.trim().lowercase()

                    if (normalized == "success") {
                        require(
                            status.apkAvailable ||
                                status.aabAvailable ||
                                status.exeAvailable
                        ) {
                            "Build success döndü ancak artifact bulunamadı."
                        }

                        return AppForgeAgentExecutionResult(
                            success = true,
                            output =
                                "BUILD PASS • ${status.buildId} • " +
                                "apk=${status.apkAvailable} • " +
                                "aab=${status.aabAvailable} • " +
                                "exe=${status.exeAvailable}"
                        )
                    }

                    if (normalized in TERMINAL_FAILURE_STATES) {
                        return AppForgeAgentExecutionResult(
                            success = false,
                            exitCode = 1,
                            output = failureOutput(status)
                        )
                    }
                } catch (error: Throwable) {
                    if (!isTransientNetwork(error)) {
                        throw error
                    }

                    consecutivePollFailures += 1
                    if (
                        consecutivePollFailures >
                        MAX_CONSECUTIVE_POLL_FAILURES
                    ) {
                        throw error
                    }

                    onStatus(
                        lastBuild,
                        "BUILD • bağlantı geçici kesildi, durum tekrar kontrol ediliyor..."
                    )
                }

                if (index + 1 < MAX_POLL_ATTEMPTS) {
                    Thread.sleep(POLL_INTERVAL_MS)
                }
            }

            runCatching {
                client.cancelBuild(created.buildId)
            }

            AppForgeAgentExecutionResult(
                success = false,
                exitCode = 124,
                output =
                    "BUILD timeout • buildId=${created.buildId} • " +
                    "maksimum bekleme süresi aşıldı."
            )
        } catch (error: Throwable) {
            AppForgeAgentExecutionResult(
                success = false,
                exitCode = 1,
                output = safeFailure(error)
            )
        } finally {
            prepared?.cleanup()
        }
    }

    private fun projectDraft(
        prepared: AppForgeAgentPreparedBuildProject,
        blueprint: AppForgeAgentBlueprint
    ): ProjectDraft =
        ProjectDraft(
            appName = blueprint.appName.take(80),
            packageName = prepared.packageName,
            sourceMode = SourceMode.LOCAL,
            sourceLabel = "Unified Agent V11",
            importedFolder = prepared.projectRoot.absolutePath,
            sourceTechnology = prepared.sourceTechnology,
            sourceTechnologyLabel = prepared.sourceTechnologyLabel,
            sourceBuildEngine = prepared.buildEngine,
            sourceBuildReady = true,
            versionName = "1.0.0",
            versionCode = 1,
            buildOutput = "both",
            minSdk = 26,
            targetSdk = 37,
            primaryColor = blueprint.tokens.primary.take(7),
            backgroundColor = blueprint.tokens.background.take(7),
            statusBarColor = blueprint.tokens.background.take(7),
            navigationBarColor = blueprint.tokens.background.take(7),
            signingMode = SigningMode.DEBUG,
            fileUpload = false,
            downloads = false,
            notifications = false,
            camera = false,
            microphone = false,
            location = false,
            networkState = true,
            wakeLock = false,
            nfc = false,
            additionalPermissions = emptySet(),
            javascriptBridge = false,
            remoteBridgeAllowed = false,
            shareBridge = false,
            clipboardBridge = false,
            vibrationBridge = false,
            mediaPlayerBridge = false,
            qrScanner = false,
            admobEnabled = false,
            billingEnabled = false,
            firebaseAnalyticsEnabled = false,
            firebaseCrashlyticsEnabled = false,
            firebaseMessagingEnabled = false,
            buildServiceUrl = effectiveBuildServiceUrl,
            buildApiKey = ""
        )

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

    private fun buildStatusMessage(
        info: AppForgeAgentRemoteBuildInfo
    ): String {
        val no = info.buildNo
            ?.let { " #$it" }
            .orEmpty()

        return "BUILD$no • ${info.status} • %${info.progress}"
    }

    private fun failureOutput(
        status: BuildStatusResult
    ): String =
        buildString {
            appendLine(
                "AppForge Build Service başarısız • " +
                    "buildId=${status.buildId} • status=${status.status}"
            )

            if (status.preflight.isNotEmpty()) {
                appendLine("PREFLIGHT:")
                status.preflight
                    .takeLast(50)
                    .forEach(::appendLine)
            }

            if (status.logs.isNotEmpty()) {
                appendLine("LOGS:")
                status.logs
                    .takeLast(100)
                    .forEach(::appendLine)
            }
        }.takeLast(MAX_FAILURE_CHARS)

    private fun <T> retryNetwork(
        action: () -> T
    ): T {
        var last: Throwable? = null

        repeat(3) { attempt ->
            try {
                return action()
            } catch (error: Throwable) {
                last = error

                if (
                    !isTransientNetwork(error) ||
                    attempt == 2
                ) {
                    throw error
                }

                Thread.sleep(
                    1_500L * (attempt + 1)
                )
            }
        }

        throw requireNotNull(last)
    }

    private fun isTransientNetwork(
        error: Throwable
    ): Boolean {
        var current: Throwable? = error

        while (current != null) {
            if (current is IOException) {
                return true
            }

            if (
                current is BuildApiException &&
                (
                    current.statusCode == 429 ||
                        current.statusCode >= 500
                    )
            ) {
                return true
            }

            val text = current.message
                .orEmpty()
                .lowercase()

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

    private fun safeFailure(
        error: Throwable
    ): String =
        (error.message ?: error::class.simpleName ?: "Build stage başarısız.")
            .replace(
                Regex("ghp_[A-Za-z0-9]{20,}"),
                "[REDACTED]"
            )
            .replace(
                Regex("github_pat_[A-Za-z0-9_]{20,}"),
                "[REDACTED]"
            )
            .replace(
                Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b"),
                "[REDACTED]"
            )
            .takeLast(MAX_FAILURE_CHARS)

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MAX_POLL_ATTEMPTS = 600
        const val MAX_CONSECUTIVE_POLL_FAILURES = 4
        const val MAX_FAILURE_CHARS = 64 * 1024

        val TERMINAL_FAILURE_STATES = setOf(
            "failed",
            "failure",
            "error",
            "cancelled",
            "canceled"
        )
    }
}

internal object AppForgeAgentWorkspaceStore {
    private const val MAX_WORKSPACES = 8

    fun create(
        filesDir: File,
        appName: String
    ): File {
        val root = File(
            filesDir,
            "unified-agent-workspaces"
        ).apply {
            mkdirs()
        }

        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_WORKSPACES - 1)
            .forEach { old ->
                old.deleteRecursively()
            }

        val safe = appName
            .lowercase()
            .map { ch ->
                if (ch.code < 128 && ch.isLetterOrDigit()) ch else '-'
            }
            .joinToString("")
            .trim('-')
            .ifBlank { "app" }
            .take(32)

        return File(
            root,
            "${System.currentTimeMillis()}-$safe"
        ).apply {
            require(mkdirs()) {
                "Unified Agent workspace oluşturulamadı."
            }
        }
    }
}
