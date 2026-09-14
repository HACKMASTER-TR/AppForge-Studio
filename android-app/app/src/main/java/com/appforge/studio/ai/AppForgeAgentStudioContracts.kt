package com.appforge.studio.ai

import java.io.File

internal enum class AppForgeAgentStudioStep {
    PROMPT,
    BLUEPRINT_REVIEW,
    DESIGN,
    BUILD,
    RESULT,
    BLOCKED
}

internal data class AppForgeAgentStudioState(
    val step: AppForgeAgentStudioStep = AppForgeAgentStudioStep.PROMPT,
    val prompt: String = "",
    val platform: AppForgeAgentPlatform = AppForgeAgentPlatform.ANDROID,
    val blueprint: AppForgeAgentBlueprint? = null,
    val validation: AgentBlueprintValidation? = null,
    val autonomousResult: AppForgeAgentAutonomousResult? = null,
    val message: String = "",
    val busy: Boolean = false,
    val remoteBuild: AppForgeAgentRemoteBuildInfo? = null
) {
    val canGenerate: Boolean
        get() = !busy && prompt.trim().isNotBlank() && prompt.length <= 4_000

    val canDesign: Boolean
        get() = !busy && blueprint != null && validation?.valid == true

    val canBuild: Boolean
        get() = !busy && canDesign && step in setOf(
            AppForgeAgentStudioStep.BLUEPRINT_REVIEW,
            AppForgeAgentStudioStep.DESIGN
        )

    val canRebuild: Boolean
        get() =
            !busy &&
                blueprint != null &&
                validation?.valid == true &&
                step in setOf(
                    AppForgeAgentStudioStep.RESULT,
                    AppForgeAgentStudioStep.BLOCKED
                )
}

internal data class AppForgeAgentStudioBuildRequest(
    val workspace: File,
    val fullStackContract: AppForgeAgentFullStackContract? = null,
    val maxRepairAttempts: Int = 2,
    val rollbackOnFailure: Boolean = true
) {
    init {
        require(maxRepairAttempts in 0..3) {
            "Studio repair sınırı 0..3 olmalı."
        }
    }
}
