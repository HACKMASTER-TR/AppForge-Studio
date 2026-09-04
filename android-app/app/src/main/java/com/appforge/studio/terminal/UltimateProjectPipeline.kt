package com.appforge.studio.terminal

import android.content.Context
import java.io.File

internal enum class ProjectPipelinePhase {
    HEALTH,
    TOOLCHAINS,
    INSTALL,
    TEST,
    BUILD,
    DEPLOY_GATE
}

internal enum class ProjectPipelineStatus {
    PASSED,
    SKIPPED,
    FAILED
}

internal enum class ProjectRuntimeAvailability {
    AVAILABLE,
    MISSING
}

internal enum class ProjectPipelineIssueLevel {
    INFO,
    WARNING,
    ERROR
}

internal data class ProjectRuntimeProbe(
    val command: String,
    val availability: ProjectRuntimeAvailability,
    val path: String = ""
)

internal data class ProjectPipelineIssue(
    val level: ProjectPipelineIssueLevel,
    val title: String,
    val detail: String
)

internal data class ProjectHealthReport(
    val issues: List<ProjectPipelineIssue>,
    val runtime: Map<String, ProjectRuntimeProbe>,
    val requiredCommands: Set<String>,
    val missingCommands: Set<String>
) {
    val blocking: Boolean
        get() =
            issues.any {
                it.level ==
                    ProjectPipelineIssueLevel.ERROR
            }
}

internal data class ProjectPipelineStepResult(
    val phase: ProjectPipelinePhase,
    val title: String,
    val status: ProjectPipelineStatus,
    val exitCode: Int? = null,
    val output: String = ""
)

internal data class ProjectPipelineRunResult(
    val success: Boolean,
    val deployReady: Boolean,
    val steps: List<ProjectPipelineStepResult>,
    val health: ProjectHealthReport,
    val failureContext: String? = null
)

internal object UltimateProjectHealthChecker {
    fun staticIssues(
        workspace: File,
        plan: UltimateProjectAutomationPlan
    ): List<ProjectPipelineIssue> {
        val root =
            workspace.canonicalFile

        return buildList {
            if (
                !root.isDirectory ||
                !root.canRead()
            ) {
                add(
                    ProjectPipelineIssue(
                        ProjectPipelineIssueLevel.ERROR,
                        "Çalışma alanı okunamıyor",
                        "Pipeline proje köküne erişemiyor."
                    )
                )

                return@buildList
            }

            if (!root.canWrite()) {
                add(
                    ProjectPipelineIssue(
                        ProjectPipelineIssueLevel.ERROR,
                        "Çalışma alanı salt okunur",
                        "Install/build adımları dosya üretebileceği için yazma izni gerekiyor."
                    )
                )
            }

            if (
                plan.projectKind ==
                AppForgeProjectKind.UNKNOWN
            ) {
                add(
                    ProjectPipelineIssue(
                        ProjectPipelineIssueLevel.WARNING,
                        "Proje türü belirsiz",
                        "Otomatik lifecycle komutları sınırlı olabilir."
                    )
                )
            }

            if (plan.steps.isEmpty()) {
                add(
                    ProjectPipelineIssue(
                        ProjectPipelineIssueLevel.WARNING,
                        "Lifecycle planı boş",
                        "Install/Test/Build için güvenilir otomatik komut üretilemedi."
                    )
                )
            }

            if (
                plan.projectKind in
                setOf(
                    AppForgeProjectKind.NODE,
                    AppForgeProjectKind.REACT,
                    AppForgeProjectKind.REACT_NATIVE
                )
            ) {
                val lockFiles =
                    listOf(
                        "package-lock.json",
                        "yarn.lock",
                        "pnpm-lock.yaml"
                    ).filter {
                        File(root, it).isFile
                    }

                if (lockFiles.size > 1) {
                    add(
                        ProjectPipelineIssue(
                            ProjectPipelineIssueLevel.WARNING,
                            "Birden fazla Node lock dosyası",
                            "Aynı projede ${lockFiles.joinToString()} bulundu. Paket yöneticisini doğrula."
                        )
                    )
                }
            }

            if (
                plan.projectKind ==
                AppForgeProjectKind.ANDROID &&
                !File(root, "gradlew").isFile
            ) {
                add(
                    ProjectPipelineIssue(
                        ProjectPipelineIssueLevel.ERROR,
                        "Gradle wrapper eksik",
                        "Android otomasyonu sistem Gradle'ına sessizce geçmez; projede gradlew gerekli."
                    )
                )
            }

            if (
                plan.projectKind ==
                AppForgeProjectKind.FLUTTER &&
                !File(root, "pubspec.yaml").isFile
            ) {
                add(
                    ProjectPipelineIssue(
                        ProjectPipelineIssueLevel.ERROR,
                        "pubspec.yaml eksik",
                        "Flutter proje kökü doğrulanamadı."
                    )
                )
            }

            if (plan.deployHints.isEmpty()) {
                add(
                    ProjectPipelineIssue(
                        ProjectPipelineIssueLevel.INFO,
                        "Deploy hedefi otomatik bulunamadı",
                        "Build sonrası Deployment Merkezi hedef seçimini kullanıcıya bırakacak."
                    )
                )
            } else {
                add(
                    ProjectPipelineIssue(
                        ProjectPipelineIssueLevel.INFO,
                        "Deploy hedefi algılandı",
                        plan.deployHints.joinToString {
                            it.title
                        }
                    )
                )
            }
        }
    }

    fun requiredCommands(
        plan: UltimateProjectAutomationPlan,
        selectedToolchains: Collection<LinuxToolchainId>
    ): Set<String> =
        buildSet {
            selectedToolchains.forEach { id ->
                addAll(
                    when (id) {
                        LinuxToolchainId.BASE ->
                            setOf("git")

                        LinuxToolchainId.PYTHON ->
                            setOf("python3", "pip3")

                        LinuxToolchainId.NODE ->
                            setOf("node", "npm")

                        LinuxToolchainId.JAVA ->
                            setOf("java", "javac")

                        LinuxToolchainId.PHP ->
                            setOf("php", "composer")

                        LinuxToolchainId.GO ->
                            setOf("go")

                        LinuxToolchainId.RUST ->
                            setOf("rustc", "cargo")

                        LinuxToolchainId.C_CPP ->
                            setOf("gcc", "g++", "cmake", "make")

                        LinuxToolchainId.ANDROID ->
                            setOf("adb", "fastboot")
                    }
                )
            }

            plan.steps.forEach { step ->
                val command =
                    step.command.trimStart()

                when {
                    command.startsWith("flutter ") ->
                        add("flutter")

                    command.startsWith("mvn ") ->
                        add("mvn")
                }
            }
        }

    fun parseRuntimeProbe(
        output: String
    ): Map<String, ProjectRuntimeProbe> =
        output
            .lineSequence()
            .mapNotNull { raw ->
                val line =
                    raw.trim()

                if (line.isBlank()) {
                    return@mapNotNull null
                }

                val parts =
                    line.split('\t')

                if (parts.size < 2) {
                    return@mapNotNull null
                }

                val command =
                    parts[0]
                        .trim()

                if (!COMMAND_PATTERN.matches(command)) {
                    return@mapNotNull null
                }

                val availability =
                    when (
                        parts[1]
                            .trim()
                            .uppercase()
                    ) {
                        "OK" ->
                            ProjectRuntimeAvailability.AVAILABLE

                        "YOK" ->
                            ProjectRuntimeAvailability.MISSING

                        else ->
                            return@mapNotNull null
                    }

                command to
                    ProjectRuntimeProbe(
                        command = command,
                        availability = availability,
                        path =
                            parts
                                .drop(2)
                                .joinToString("\t")
                                .trim()
                                .take(1_024)
                    )
            }
            .toMap()

    fun report(
        workspace: File,
        plan: UltimateProjectAutomationPlan,
        selectedToolchains: Collection<LinuxToolchainId>,
        runtimeOutput: String
    ): ProjectHealthReport {
        val runtime =
            parseRuntimeProbe(
                runtimeOutput
            )

        val required =
            requiredCommands(
                plan,
                selectedToolchains
            )

        val missing =
            required.filterTo(
                linkedSetOf()
            ) { command ->
                runtime[command]
                    ?.availability !=
                    ProjectRuntimeAvailability.AVAILABLE
            }

        val issues =
            staticIssues(
                workspace,
                plan
            ).toMutableList()

        if (missing.isNotEmpty()) {
            issues +=
                ProjectPipelineIssue(
                    ProjectPipelineIssueLevel.WARNING,
                    "Eksik Linux araçları",
                    missing.joinToString()
                )
        } else if (required.isNotEmpty()) {
            issues +=
                ProjectPipelineIssue(
                    ProjectPipelineIssueLevel.INFO,
                    "Araç zinciri hazır",
                    "Gerekli komutlar doğrulanmış rootfs içinde bulundu."
                )
        }

        return ProjectHealthReport(
            issues = issues,
            runtime = runtime,
            requiredCommands = required,
            missingCommands = missing
        )
    }

    private val COMMAND_PATTERN =
        Regex("^[A-Za-z0-9+._-]{1,64}$")
}

internal object UltimatePipelineAiContextBuilder {
    private const val MAX_PACKET_CHARS =
        24 * 1024

    fun build(
        projectKind: AppForgeProjectKind,
        failedTitle: String,
        failedCommand: String,
        exitCode: Int?,
        output: String,
        health: ProjectHealthReport
    ): String {
        val sanitizedOutput =
            TerminalSecretMasker.redact(
                TerminalTextSanitizer.clean(
                    output
                )
            )
                .take(12 * 1024)

        val sanitizedCommand =
            TerminalSecretMasker.redact(
                failedCommand
            )
                .take(4 * 1024)

        return TerminalSecretMasker.redact(
            buildString {
                appendLine("AppForge Ultimate AI hata aktarımı")
                appendLine("Proje türü: ${projectKind.title}")
                appendLine("Başarısız adım: $failedTitle")
                appendLine("Exit code: ${exitCode ?: -1}")
                appendLine()
                appendLine("Komut:")
                appendLine(sanitizedCommand)
                appendLine()
                appendLine("Sağlık notları:")
                health.issues
                    .take(20)
                    .forEach {
                        appendLine(
                            "- ${it.level}: ${it.title} — ${it.detail}"
                        )
                    }
                appendLine()
                appendLine("Çıktı:")
                append(sanitizedOutput)
            }
        ).take(MAX_PACKET_CHARS)
    }
}

internal object UltimateAiHandoffStore {
    @Volatile
    private var latest: String? =
        null

    fun publish(
        packet: String
    ) {
        latest =
            TerminalSecretMasker.redact(
                packet
            ).take(
                24 * 1024
            )
    }

    fun peek(): String? =
        latest

    fun clear() {
        latest =
            null
    }
}

internal class UltimateProjectPipelineEngine(
    context: Context
) {
    private val executor =
        UltimateProjectAutomationExecutor(
            context.applicationContext
        )

    suspend fun checkHealth(
        workspace: File,
        distribution: LinuxDistribution,
        plan: UltimateProjectAutomationPlan,
        selectedToolchains: Collection<LinuxToolchainId>
    ): ProjectHealthReport {
        val probe =
            executor.execute(
                workspace = workspace,
                distribution = distribution,
                command =
                    UltimateProjectAutomationPlanner
                        .runtimeProbeCommand(),
                confirmed = true,
                timeoutMs = 60_000L
            )

        require(
            probe.exitCode == 0 &&
                !probe.timedOut
        ) {
            "Linux araç taraması tamamlanamadı (exit ${probe.exitCode})."
        }

        return UltimateProjectHealthChecker
            .report(
                workspace = workspace,
                plan = plan,
                selectedToolchains =
                    selectedToolchains,
                runtimeOutput = probe.output
            )
    }

    suspend fun run(
        workspace: File,
        distribution: LinuxDistribution,
        plan: UltimateProjectAutomationPlan,
        selectedToolchains: Collection<LinuxToolchainId>,
        confirmed: Boolean
    ): ProjectPipelineRunResult {
        require(confirmed) {
            "Pipeline açık kullanıcı onayı olmadan başlatılamaz."
        }

        val results =
            mutableListOf<
                ProjectPipelineStepResult
                >()

        var health =
            checkHealth(
                workspace,
                distribution,
                plan,
                selectedToolchains
            )

        results +=
            ProjectPipelineStepResult(
                phase =
                    ProjectPipelinePhase.HEALTH,
                title =
                    "Proje sağlık kontrolü",
                status =
                    if (health.blocking) {
                        ProjectPipelineStatus.FAILED
                    } else {
                        ProjectPipelineStatus.PASSED
                    },
                output =
                    health.issues
                        .joinToString("\n") {
                            "${it.level}: ${it.title} — ${it.detail}"
                        }
                        .take(32 * 1024)
            )

        if (health.blocking) {
            return failure(
                plan = plan,
                results = results,
                health = health,
                title =
                    "Proje sağlık kontrolü",
                command =
                    "health-check",
                exitCode =
                    null,
                output =
                    results.last().output
            )
        }

        if (health.missingCommands.isNotEmpty()) {
            val installCommand =
                executor.packageInstallCommand(
                    selectedToolchains
                )

            val install =
                executor.execute(
                    workspace = workspace,
                    distribution = distribution,
                    command = installCommand,
                    confirmed = true,
                    timeoutMs = 15 * 60_000L
                )

            val installResult =
                ProjectPipelineStepResult(
                    phase =
                        ProjectPipelinePhase.TOOLCHAINS,
                    title =
                        "Eksik Linux araç zincirlerini kur",
                    status =
                        statusOf(install),
                    exitCode =
                        install.exitCode,
                    output =
                        safeOutput(
                            install.output
                        )
                )

            results +=
                installResult

            if (
                installResult.status ==
                ProjectPipelineStatus.FAILED
            ) {
                return failure(
                    plan,
                    results,
                    health,
                    installResult.title,
                    installCommand,
                    install.exitCode,
                    install.output
                )
            }

            health =
                checkHealth(
                    workspace,
                    distribution,
                    plan,
                    selectedToolchains
                )

            if (health.missingCommands.isNotEmpty()) {
                return failure(
                    plan,
                    results,
                    health,
                    "Araç zinciri yeniden doğrulama",
                    UltimateProjectAutomationPlanner
                        .runtimeProbeCommand(),
                    null,
                    "Kurulum sonrasında eksik kalan araçlar: ${health.missingCommands.joinToString()}"
                )
            }
        } else {
            results +=
                ProjectPipelineStepResult(
                    phase =
                        ProjectPipelinePhase.TOOLCHAINS,
                    title =
                        "Linux araç zinciri",
                    status =
                        ProjectPipelineStatus.SKIPPED,
                    output =
                        "Gerekli araçlar zaten kurulu."
                )
        }

        val lifecycle =
            plan.steps.sortedBy {
                when (it.kind) {
                    ProjectAutomationStepKind.INSTALL -> 0
                    ProjectAutomationStepKind.TEST -> 1
                    ProjectAutomationStepKind.BUILD -> 2
                }
            }

        lifecycle.forEach { step ->
            val execution =
                executor.execute(
                    workspace = workspace,
                    distribution = distribution,
                    command = step.command,
                    confirmed = true,
                    timeoutMs = 20 * 60_000L
                )

            val result =
                ProjectPipelineStepResult(
                    phase =
                        when (step.kind) {
                            ProjectAutomationStepKind.INSTALL ->
                                ProjectPipelinePhase.INSTALL

                            ProjectAutomationStepKind.TEST ->
                                ProjectPipelinePhase.TEST

                            ProjectAutomationStepKind.BUILD ->
                                ProjectPipelinePhase.BUILD
                        },
                    title = step.title,
                    status = statusOf(execution),
                    exitCode = execution.exitCode,
                    output = safeOutput(execution.output)
                )

            results += result

            if (
                result.status ==
                ProjectPipelineStatus.FAILED
            ) {
                return failure(
                    plan,
                    results,
                    health,
                    step.title,
                    step.command,
                    execution.exitCode,
                    execution.output
                )
            }
        }

        results +=
            ProjectPipelineStepResult(
                phase =
                    ProjectPipelinePhase.DEPLOY_GATE,
                title =
                    "Deploy onay kapısı",
                status =
                    ProjectPipelineStatus.PASSED,
                output =
                    if (
                        plan.deployHints.isEmpty()
                    ) {
                        "Build tamamlandı. Deployment Merkezi hedef seçimi için açılabilir."
                    } else {
                        "Build tamamlandı. Algılanan hedefler: ${plan.deployHints.joinToString { it.title }}"
                    }
            )

        return ProjectPipelineRunResult(
            success = true,
            deployReady = true,
            steps = results,
            health = health,
            failureContext = null
        )
    }

    private fun failure(
        plan: UltimateProjectAutomationPlan,
        results: List<ProjectPipelineStepResult>,
        health: ProjectHealthReport,
        title: String,
        command: String,
        exitCode: Int?,
        output: String
    ): ProjectPipelineRunResult {
        val packet =
            UltimatePipelineAiContextBuilder
                .build(
                    projectKind =
                        plan.projectKind,
                    failedTitle = title,
                    failedCommand = command,
                    exitCode = exitCode,
                    output = output,
                    health = health
                )

        UltimateAiHandoffStore
            .publish(packet)

        return ProjectPipelineRunResult(
            success = false,
            deployReady = false,
            steps = results,
            health = health,
            failureContext = packet
        )
    }

    private fun statusOf(
        result: LinuxShellResult
    ): ProjectPipelineStatus =
        if (
            result.exitCode == 0 &&
            !result.timedOut
        ) {
            ProjectPipelineStatus.PASSED
        } else {
            ProjectPipelineStatus.FAILED
        }

    private fun safeOutput(
        value: String
    ): String =
        TerminalSecretMasker.redact(
            TerminalTextSanitizer.clean(
                value
            )
        ).take(64 * 1024)
}
