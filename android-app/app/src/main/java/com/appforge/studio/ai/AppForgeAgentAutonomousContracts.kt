package com.appforge.studio.ai

import java.io.File

internal enum class AppForgeAgentAutonomousStatus {
    SUCCESS,
    BLOCKED
}

internal enum class AppForgeAgentAutonomousStage {
    BLUEPRINT,
    GENERATE,
    APPLY,
    TEST,
    BUILD,
    REPAIR,
    COMPLETE,
    BLOCKED
}

internal enum class AppForgeAgentExecutionStage {
    TEST,
    BUILD
}

internal enum class AppForgeAgentDeployGateV8 {
    REVIEW_REQUIRED,
    NOT_REACHED
}

internal data class AppForgeAgentExecutionResult(
    val success: Boolean,
    val exitCode: Int = if (success) 0 else 1,
    val output: String = ""
)

internal data class AppForgeAgentAutonomousRequest(
    val userPrompt: String,
    val preferredPlatform: AppForgeAgentPlatform,
    val workspace: File,
    val fullStackContract: AppForgeAgentFullStackContract? = null,
    val maxRepairAttempts: Int = 2,
    val rollbackOnFailure: Boolean = true
) {
    init {
        require(userPrompt.trim().isNotBlank()) { "Autonomous Agent prompt boş olamaz." }
        require(userPrompt.length <= 4_000) { "Autonomous Agent prompt 4000 karakteri aşamaz." }
        require(maxRepairAttempts in 0..3) { "Autonomous Agent repair sınırı 0..3 olmalı." }
    }
}

internal data class AppForgeAgentAutonomousEvent(
    val stage: AppForgeAgentAutonomousStage,
    val message: String,
    val attempt: Int = 0,
    val fingerprint: String? = null
)

internal data class AppForgeAgentPatchRequestV8(
    val failureFingerprint: String,
    val attempt: Int,
    val phase: AppForgeAgentFailurePhase,
    val normalizedFailureOutput: String,
    val allowedRootPrefixes: Set<String>,
    val generatedProjectDigestSha256: String
)

internal data class AppForgeAgentAutonomousResult(
    val status: AppForgeAgentAutonomousStatus,
    val message: String,
    val blueprint: AppForgeAgentBlueprint?,
    val generatedProject: AppForgeGeneratedProject?,
    val checkpoints: List<AppForgeWorkspaceCheckpoint>,
    val events: List<AppForgeAgentAutonomousEvent>,
    val deployGate: AppForgeAgentDeployGateV8
)

internal fun interface AppForgeAgentBlueprintProvider {
    fun createBlueprint(promptContract: String): String
}

internal fun interface AppForgeAgentPatchProviderV8 {
    fun createPatch(request: AppForgeAgentPatchRequestV8): AppForgeAgentPatchPlan
}

internal fun interface AppForgeAgentStageRunner {
    fun run(stage: AppForgeAgentExecutionStage, workspace: File): AppForgeAgentExecutionResult
}
