package com.appforge.studio.ai

internal data class AppForgeAgentFinalAcceptanceReport(
    val ready: Boolean,
    val manualReviewRequired: Boolean = true,
    val checks: List<String> = emptyList(),
    val blockers: List<String> = emptyList()
) {
    val summary: String
        get() = if (ready) {
            "V15 FINAL READY • manual review required"
        } else {
            "V15 FINAL BLOCKED • ${blockers.size} blocker"
        }
}

internal object AppForgeAgentFinalAcceptance {
    fun evaluate(
        state: AppForgeAgentStudioState,
        artifacts: AppForgeAgentArtifactState,
        releaseReviewState: AppForgeAgentReleaseReviewState,
        workspacePath: String?,
        quality: AppForgeAgentQualityReport? =
            state.blueprint?.let(AppForgeAgentQualityGate::assess)
    ): AppForgeAgentFinalAcceptanceReport {
        val checks = mutableListOf<String>()
        val blockers = mutableListOf<String>()

        if (state.step == AppForgeAgentStudioStep.RESULT) {
            checks += "Unified Agent RESULT durumunda."
        } else {
            blockers += "Unified Agent RESULT durumunda değil."
        }

        val blueprint = state.blueprint
        if (blueprint != null) {
            if (AppForgeAgentBlueprintValidator.validate(blueprint).valid) {
                checks += "Blueprint doğrulaması PASS."
            } else {
                blockers += "Blueprint doğrulaması başarısız."
            }
        } else {
            blockers += "Blueprint bulunamadı."
        }

        if (quality?.pass == true) {
            checks += quality.summary
        } else {
            blockers += quality?.summary ?: "V14 kalite raporu bulunamadı."
        }

        val remote = state.remoteBuild
        if (
            remote != null &&
            remote.status.equals("success", ignoreCase = true)
        ) {
            checks += "Cloud build SUCCESS."
        } else {
            blockers += "Cloud build SUCCESS değil."
        }

        if (!workspacePath.isNullOrBlank()) {
            checks += "Kaynak workspace mevcut."
        } else {
            blockers += "Kaynak workspace bulunamadı."
        }

        if (
            remote != null &&
            artifacts.buildId == remote.buildId
        ) {
            checks += "Artifact incelemesi güncel build ile eşleşiyor."
        } else {
            blockers += "Artifact incelemesi güncel build ile eşleşmiyor."
        }

        val readiness = releaseReviewState.readiness
        if (readiness.ready) {
            checks += "Release readiness PASS."
        } else {
            blockers += readiness.blockers.ifEmpty {
                listOf("Release readiness tamamlanmadı.")
            }
        }

        if (readiness.reviewRequired) {
            checks += "Manuel REVIEW_REQUIRED kapısı korunuyor."
        } else {
            blockers += "Manuel review kapısı devre dışı bırakılamaz."
        }

        return AppForgeAgentFinalAcceptanceReport(
            ready = blockers.isEmpty(),
            manualReviewRequired = true,
            checks = checks,
            blockers = blockers.distinct()
        )
    }
}
