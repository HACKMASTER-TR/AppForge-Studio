package com.appforge.studio.ai

import java.io.File

internal enum class AppForgeAgentRecoverySeverity {
    INFO,
    WARNING,
    BLOCKER
}

internal data class AppForgeAgentRecoveryIssue(
    val code: String,
    val severity: AppForgeAgentRecoverySeverity,
    val title: String,
    val detail: String
)

internal data class AppForgeAgentRecoveryAssessment(
    val sessionId: String,
    val safeToOpen: Boolean,
    val resumableBuildId: String? = null,
    val staleBuild: Boolean = false,
    val workspaceRestorable: Boolean = true,
    val quarantinedSessionCount: Int = 0,
    val issues: List<AppForgeAgentRecoveryIssue> = emptyList()
) {
    val headline: String
        get() =
            when {
                issues.any {
                    it.severity ==
                        AppForgeAgentRecoverySeverity.BLOCKER
                } ->
                    "Kurtarma gerekli"

                issues.any {
                    it.severity ==
                        AppForgeAgentRecoverySeverity.WARNING
                } ->
                    "Kontrol ederek devam et"

                else ->
                    "Kayıt sağlıklı"
            }
}

internal object AppForgeAgentRecoveryPolicy {
    private const val STALE_BUILD_MS =
        6L * 60L * 60L * 1000L

    fun assess(
        filesDir: File,
        session: AppForgeAgentPersistentSession,
        nowEpochMs: Long =
            System.currentTimeMillis(),
        quarantinedSessionCount: Int = 0
    ): AppForgeAgentRecoveryAssessment {
        val issues =
            mutableListOf<AppForgeAgentRecoveryIssue>()

        val buildId =
            session.resumableBuildId

        val ageMs =
            (
                nowEpochMs -
                    session.updatedAtEpochMs
                )
                .coerceAtLeast(
                    0L
                )

        val staleBuild =
            session.state.step ==
                AppForgeAgentStudioStep.BUILD &&
                !buildId.isNullOrBlank() &&
                ageMs >=
                    STALE_BUILD_MS

        if (
            session.state.step ==
                AppForgeAgentStudioStep.BUILD
        ) {
            if (
                buildId.isNullOrBlank()
            ) {
                issues +=
                    AppForgeAgentRecoveryIssue(
                        code =
                            "BUILD_ID_MISSING",
                        severity =
                            AppForgeAgentRecoverySeverity.BLOCKER,
                        title =
                            "Cloud Build kimliği eksik",
                        detail =
                            "Bu kayıt yeni build başlatmadan güvenli şekilde devam ettirilemez."
                    )
            } else {
                issues +=
                    AppForgeAgentRecoveryIssue(
                        code =
                            "BUILD_REATTACH_AVAILABLE",
                        severity =
                            AppForgeAgentRecoverySeverity.INFO,
                        title =
                            "Mevcut Cloud Build bulundu",
                        detail =
                            "Devam edildiğinde yeni build oluşturulmadan aynı buildId izlenecek."
                    )
            }
        }

        if (
            staleBuild
        ) {
            issues +=
                AppForgeAgentRecoveryIssue(
                    code =
                        "BUILD_STALE",
                    severity =
                        AppForgeAgentRecoverySeverity.WARNING,
                    title =
                        "Build kaydı eski",
                    detail =
                        "Build kaydı 6 saatten eski. Devam edildiğinde önce sunucudaki gerçek durum yeniden okunacak."
                )
        }

        val hadWorkspace =
            !session.workspacePath.isNullOrBlank()

        val restoredWorkspace =
            AppForgeAgentSessionRuntimePolicy
                .restoreWorkspacePath(
                    filesDir =
                        filesDir,
                    savedPath =
                        session.workspacePath
                )

        val workspaceRestorable =
            !hadWorkspace ||
                restoredWorkspace != null

        if (
            hadWorkspace &&
            restoredWorkspace == null
        ) {
            issues +=
                AppForgeAgentRecoveryIssue(
                    code =
                        "WORKSPACE_MISSING",
                    severity =
                        AppForgeAgentRecoverySeverity.WARNING,
                    title =
                        "Yerel workspace bulunamadı",
                    detail =
                        "Cloud artifact ve build geçmişi korunur; kaynak ZIP dışa aktarma kullanılamayabilir."
                )
        }

        if (
            quarantinedSessionCount >
                0
        ) {
            issues +=
                AppForgeAgentRecoveryIssue(
                    code =
                        "QUARANTINE_PRESENT",
                    severity =
                        AppForgeAgentRecoverySeverity.INFO,
                    title =
                        "Karantinaya alınmış kayıt var",
                    detail =
                        "$quarantinedSessionCount bozuk veya uyumsuz session güvenli şekilde aktif kayıtlardan ayrıldı."
                )
        }

        val safeToOpen =
            issues.none {
                it.severity ==
                    AppForgeAgentRecoverySeverity.BLOCKER
            }

        return AppForgeAgentRecoveryAssessment(
            sessionId =
                session.sessionId,
            safeToOpen =
                safeToOpen,
            resumableBuildId =
                buildId,
            staleBuild =
                staleBuild,
            workspaceRestorable =
                workspaceRestorable,
            quarantinedSessionCount =
                quarantinedSessionCount
                    .coerceAtLeast(
                        0
                    ),
            issues =
                issues
        )
    }
}
