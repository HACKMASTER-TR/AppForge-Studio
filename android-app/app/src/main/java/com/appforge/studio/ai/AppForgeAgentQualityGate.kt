package com.appforge.studio.ai

internal enum class AppForgeAgentQualityLevel {
    INFO,
    WARNING,
    ERROR
}

internal data class AppForgeAgentQualityFinding(
    val level: AppForgeAgentQualityLevel,
    val code: String,
    val message: String
)

internal data class AppForgeAgentQualityReport(
    val score: Int,
    val findings: List<AppForgeAgentQualityFinding>
) {
    val pass: Boolean
        get() = findings.none {
            it.level == AppForgeAgentQualityLevel.ERROR
        }

    val summary: String
        get() = if (pass) {
            "V14 Quality Gate PASS • $score/100"
        } else {
            "V14 Quality Gate BLOCKED • $score/100"
        }
}

internal object AppForgeAgentQualityGate {
    fun assess(
        blueprint: AppForgeAgentBlueprint
    ): AppForgeAgentQualityReport {
        val findings = mutableListOf<AppForgeAgentQualityFinding>()
        val validation = AppForgeAgentBlueprintValidator.validate(blueprint)

        validation.issues.forEach { issue ->
            findings += AppForgeAgentQualityFinding(
                level = when (issue.level) {
                    AgentBlueprintIssueLevel.ERROR ->
                        AppForgeAgentQualityLevel.ERROR
                    AgentBlueprintIssueLevel.WARNING ->
                        AppForgeAgentQualityLevel.WARNING
                },
                code = "blueprint.${issue.field}",
                message = issue.message
            )
        }

        if (blueprint.screens.size >= 12) {
            findings += AppForgeAgentQualityFinding(
                AppForgeAgentQualityLevel.WARNING,
                "complexity.screen_count",
                "Ekran sayısı yüksek; üretim ve regresyon süresi artabilir."
            )
        }

        val noActionScreens = blueprint.screens.count {
            it.actions.isEmpty()
        }

        if (
            blueprint.screens.size > 1 &&
            noActionScreens == blueprint.screens.size
        ) {
            findings += AppForgeAgentQualityFinding(
                AppForgeAgentQualityLevel.WARNING,
                "ux.no_actions",
                "Çok ekranlı projede hiçbir ekran aksiyon tanımlamıyor."
            )
        }

        val missingPurpose = blueprint.screens.count {
            it.purpose.trim().isBlank()
        }

        if (missingPurpose > 0) {
            findings += AppForgeAgentQualityFinding(
                AppForgeAgentQualityLevel.WARNING,
                "spec.missing_purpose",
                "$missingPurpose ekranın amacı boş."
            )
        }

        if (blueprint.maxRepairAttempts == 0) {
            findings += AppForgeAgentQualityFinding(
                AppForgeAgentQualityLevel.INFO,
                "repair.disabled",
                "Otomatik repair kapalı; ilk test/build hatasında pipeline durur."
            )
        }

        val errorCount = findings.count {
            it.level == AppForgeAgentQualityLevel.ERROR
        }
        val warningCount = findings.count {
            it.level == AppForgeAgentQualityLevel.WARNING
        }
        val infoCount = findings.count {
            it.level == AppForgeAgentQualityLevel.INFO
        }

        val score = (
            100 -
                errorCount * 35 -
                warningCount * 8 -
                infoCount * 2
            ).coerceIn(0, 100)

        return AppForgeAgentQualityReport(
            score = score,
            findings = findings
        )
    }

    fun boundedRepairBudget(
        blueprint: AppForgeAgentBlueprint,
        requestedAttempts: Int
    ): Int {
        require(requestedAttempts in 0..3) {
            "Repair isteği 0..3 aralığında olmalı."
        }

        return minOf(
            requestedAttempts,
            blueprint.maxRepairAttempts,
            3
        )
    }

    fun validationTargets(
        platform: AppForgeAgentPlatform
    ): List<String> = when (platform) {
        AppForgeAgentPlatform.ANDROID ->
            listOf("unit-test", "android-build", "apk", "aab", "security")
        AppForgeAgentPlatform.FLUTTER ->
            listOf("unit-test", "flutter-build", "apk", "aab", "security")
        AppForgeAgentPlatform.REACT_NATIVE ->
            listOf("unit-test", "react-native-build", "apk", "aab", "security")
        AppForgeAgentPlatform.WEB ->
            listOf("unit-test", "web-build", "source", "security")
    }
}
