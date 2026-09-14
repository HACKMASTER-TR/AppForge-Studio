package com.appforge.studio.ai

internal data class AppForgeAgentBuildHistoryItem(
    val buildId: String,
    val buildNo: Long?,
    val status: String,
    val createdAt: Long
)

internal data class AppForgeAgentBuildComparison(
    val previousBuildId: String,
    val currentBuildId: String,
    val apkDeltaBytes: Long,
    val aabDeltaBytes: Long,
    val changeCount: Int,
    val changes: List<String>
)

internal data class AppForgeAgentReleaseReadiness(
    val ready: Boolean,
    val reviewRequired: Boolean = true,
    val checks: List<String> = emptyList(),
    val blockers: List<String> = emptyList()
)

internal data class AppForgeAgentReleaseReviewState(
    val busy: Boolean = false,
    val message: String = "",
    val history: List<AppForgeAgentBuildHistoryItem> = emptyList(),
    val comparison: AppForgeAgentBuildComparison? = null,
    val releaseNotes: List<String> = emptyList(),
    val readiness: AppForgeAgentReleaseReadiness =
        AppForgeAgentReleaseReadiness(
            ready = false,
            reviewRequired = true,
            blockers = listOf(
                "Release kontrolü henüz çalıştırılmadı."
            )
        )
)

internal object AppForgeAgentReleaseReadinessEvaluator {
    fun evaluate(
        remote: AppForgeAgentRemoteBuildInfo?,
        artifacts: AppForgeAgentArtifactState
    ): AppForgeAgentReleaseReadiness {
        val checks = mutableListOf<String>()
        val blockers = mutableListOf<String>()

        if (remote == null) {
            blockers += "Cloud build sonucu bulunamadı."
            return AppForgeAgentReleaseReadiness(
                ready = false,
                checks = checks,
                blockers = blockers
            )
        }

        if (remote.status.equals("success", ignoreCase = true)) {
            checks += "Cloud build SUCCESS."
        } else {
            blockers += "Cloud build SUCCESS değil: ${remote.status}."
        }

        val artifactAvailable =
            remote.apkAvailable ||
                remote.aabAvailable ||
                remote.exeAvailable

        if (artifactAvailable) {
            checks += "En az bir release artifact hazır."
        } else {
            blockers += "Release artifact bulunamadı."
        }

        val mobileArtifact =
            remote.apkAvailable ||
                remote.aabAvailable

        if (mobileArtifact) {
            if (
                artifacts.buildId == remote.buildId &&
                artifacts.testLabAvailable
            ) {
                checks += "Test Lab raporu mevcut."
            } else {
                blockers += "APK/AAB için Test Lab raporu gerekli."
            }
        }

        val highSecurityFindings =
            artifacts.security.filter {
                val severity =
                    it.severity.trim().lowercase()

                severity in setOf(
                    "high",
                    "critical",
                    "yüksek",
                    "kritik"
                )
            }

        if (highSecurityFindings.isEmpty()) {
            checks += "HIGH/CRITICAL güvenlik bulgusu yok."
        } else {
            blockers +=
                "${highSecurityFindings.size} HIGH/CRITICAL güvenlik bulgusu var."
        }

        checks += "Deploy otomatik değil; manuel REVIEW_REQUIRED korunuyor."

        return AppForgeAgentReleaseReadiness(
            ready = blockers.isEmpty(),
            reviewRequired = true,
            checks = checks,
            blockers = blockers
        )
    }
}
