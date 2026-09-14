package com.appforge.studio.ai

import java.io.File

internal data class AppForgeAgentSessionResumeInfo(
    val sessionId: String,
    val title: String,
    val platform: String,
    val step: String,
    val buildId: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false
)

internal object AppForgeAgentSessionRuntimePolicy {
    fun shouldPersist(
        state: AppForgeAgentStudioState,
        artifactState: AppForgeAgentArtifactState,
        releaseReviewState: AppForgeAgentReleaseReviewState,
        workspacePath: String?
    ): Boolean =
        state.prompt.isNotBlank() ||
            state.blueprint != null ||
            state.remoteBuild != null ||
            !workspacePath.isNullOrBlank() ||
            !artifactState.buildId.isNullOrBlank() ||
            artifactState.logs.isNotEmpty() ||
            releaseReviewState.history.isNotEmpty() ||
            releaseReviewState.releaseNotes.isNotEmpty()

    fun resumeInfo(
        session: AppForgeAgentPersistentSession
    ): AppForgeAgentSessionResumeInfo {
        val title =
            session.customName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: session.state.blueprint
                    ?.appName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: session.state.prompt
                    .trim()
                    .replace(
                        Regex("\\s+"),
                        " "
                    )
                    .take(72)
                    .ifBlank {
                        "Unified Agent çalışması"
                    }

        return AppForgeAgentSessionResumeInfo(
            sessionId = session.sessionId,
            title = title,
            platform =
                session.state.platform.title,
            step =
                when (
                    session.state.step
                ) {
                    AppForgeAgentStudioStep.PROMPT ->
                        "Prompt"

                    AppForgeAgentStudioStep.BLUEPRINT_REVIEW ->
                        "Blueprint"

                    AppForgeAgentStudioStep.DESIGN ->
                        "Visual Designer"

                    AppForgeAgentStudioStep.BUILD ->
                        "Cloud Build"

                    AppForgeAgentStudioStep.RESULT ->
                        "Sonuç"

                    AppForgeAgentStudioStep.BLOCKED ->
                        "İnceleme gerekli"
                },
            buildId =
                session.resumableBuildId,
            pinned =
                session.pinned,
            archived =
                session.archived
        )
    }

    fun restoreWorkspacePath(
        filesDir: File,
        savedPath: String?
    ): String? {
        if (
            savedPath.isNullOrBlank()
        ) {
            return null
        }

        val root =
            File(
                filesDir,
                "unified-agent-workspaces"
            ).canonicalFile

        val candidate =
            runCatching {
                File(
                    savedPath
                ).canonicalFile
            }.getOrNull()
                ?: return null

        if (
            !candidate.isDirectory
        ) {
            return null
        }

        val rootPrefix =
            root.path
                .trimEnd(
                    File.separatorChar
                ) +
                File.separator

        return candidate.path
            .takeIf {
                candidate == root ||
                    candidate.path.startsWith(
                        rootPrefix
                    )
            }
    }
}
