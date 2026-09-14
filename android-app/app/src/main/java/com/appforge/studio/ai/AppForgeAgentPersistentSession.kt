package com.appforge.studio.ai

import java.util.UUID

internal data class AppForgeAgentPersistentSession(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sessionId: String = UUID.randomUUID().toString(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val customName: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val state: AppForgeAgentStudioState = AppForgeAgentStudioState(),
    val artifactState: AppForgeAgentArtifactState = AppForgeAgentArtifactState(),
    val releaseReviewState: AppForgeAgentReleaseReviewState =
        AppForgeAgentReleaseReviewState(),
    val workspacePath: String? = null
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Desteklenmeyen Unified Agent session schema."
        }
        require(sessionId.length in 8..80) {
            "Unified Agent sessionId sınır dışında."
        }
        require(state.prompt.length <= 4_000) {
            "Unified Agent session prompt sınırını aştı."
        }
        require(customName == null || customName.length <= 80) {
            "Unified Agent session adı sınırını aştı."
        }
        require(workspacePath == null || workspacePath.length <= 1_024) {
            "Unified Agent workspace yolu sınırını aştı."
        }
    }

    val resumableBuildId: String?
        get() = state.remoteBuild
            ?.buildId
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

internal sealed interface AppForgeAgentSessionLoadResult {
    data object Empty : AppForgeAgentSessionLoadResult

    data class Loaded(
        val session: AppForgeAgentPersistentSession
    ) : AppForgeAgentSessionLoadResult

    data class Quarantined(
        val reason: String
    ) : AppForgeAgentSessionLoadResult
}
