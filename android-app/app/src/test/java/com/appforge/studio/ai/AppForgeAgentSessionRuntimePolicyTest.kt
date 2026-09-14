package com.appforge.studio.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentSessionRuntimePolicyTest {
    @Test
    fun emptyStudioStateIsNotPersisted() {
        assertFalse(
            AppForgeAgentSessionRuntimePolicy.shouldPersist(
                state =
                    AppForgeAgentStudioState(),
                artifactState =
                    AppForgeAgentArtifactState(),
                releaseReviewState =
                    AppForgeAgentReleaseReviewState(),
                workspacePath = null
            )
        )
    }

    @Test
    fun promptOrRemoteBuildMakesSessionPersistent() {
        assertTrue(
            AppForgeAgentSessionRuntimePolicy.shouldPersist(
                state =
                    AppForgeAgentStudioState(
                        prompt =
                            "Görev uygulaması"
                    ),
                artifactState =
                    AppForgeAgentArtifactState(),
                releaseReviewState =
                    AppForgeAgentReleaseReviewState(),
                workspacePath = null
            )
        )

        assertTrue(
            AppForgeAgentSessionRuntimePolicy.shouldPersist(
                state =
                    AppForgeAgentStudioState(
                        remoteBuild =
                            AppForgeAgentRemoteBuildInfo(
                                buildId =
                                    "build-44",
                                buildNo = 44,
                                status =
                                    "running",
                                progress = 30,
                                apkAvailable =
                                    false,
                                aabAvailable =
                                    false,
                                exeAvailable =
                                    false
                            )
                    ),
                artifactState =
                    AppForgeAgentArtifactState(),
                releaseReviewState =
                    AppForgeAgentReleaseReviewState(),
                workspacePath = null
            )
        )
    }

    @Test
    fun resumeInfoUsesBlueprintAndBuildIdentity() {
        val blueprint =
            AppForgeAgentBlueprint(
                appName = "TaskFlow",
                prompt =
                    "Görev takip uygulaması",
                platform =
                    AppForgeAgentPlatform.ANDROID,
                startRoute =
                    "/home",
                screens =
                    listOf(
                        AppForgeAgentScreenSpec(
                            id = "home",
                            title =
                                "Ana Sayfa",
                            route =
                                "/home",
                            purpose =
                                "Görevleri gösterir."
                        )
                    )
            )

        val info =
            AppForgeAgentSessionRuntimePolicy.resumeInfo(
                AppForgeAgentPersistentSession(
                    sessionId =
                        "session-runtime-01",
                    state =
                        AppForgeAgentStudioState(
                            step =
                                AppForgeAgentStudioStep.BUILD,
                            prompt =
                                "Görev takip uygulaması",
                            blueprint =
                                blueprint,
                            validation =
                                AppForgeAgentBlueprintValidator.validate(
                                    blueprint
                                ),
                            remoteBuild =
                                AppForgeAgentRemoteBuildInfo(
                                    buildId =
                                        "build-99",
                                    buildNo =
                                        99,
                                    status =
                                        "running",
                                    progress =
                                        61,
                                    apkAvailable =
                                        false,
                                    aabAvailable =
                                        false,
                                    exeAvailable =
                                        false
                                )
                        )
                )
            )

        assertEquals(
            "TaskFlow",
            info.title
        )
        assertEquals(
            "Cloud Build",
            info.step
        )
        assertEquals(
            "build-99",
            info.buildId
        )
    }

    @Test
    fun workspaceRestoreAcceptsOnlyUnifiedAgentWorkspaceRoot() {
        val filesDir =
            tempRoot()
        val safeRoot =
            File(
                filesDir,
                "unified-agent-workspaces"
            ).apply {
                mkdirs()
            }
        val safe =
            File(
                safeRoot,
                "session-a"
            ).apply {
                mkdirs()
            }
        val outside =
            File(
                filesDir,
                "outside"
            ).apply {
                mkdirs()
            }

        assertEquals(
            safe.canonicalPath,
            AppForgeAgentSessionRuntimePolicy.restoreWorkspacePath(
                filesDir,
                safe.absolutePath
            )
        )

        assertNull(
            AppForgeAgentSessionRuntimePolicy.restoreWorkspacePath(
                filesDir,
                outside.absolutePath
            )
        )

        filesDir.deleteRecursively()
    }

    @Test
    fun missingWorkspaceIsNotRestored() {
        val filesDir =
            tempRoot()

        assertNull(
            AppForgeAgentSessionRuntimePolicy.restoreWorkspacePath(
                filesDir,
                File(
                    filesDir,
                    "unified-agent-workspaces/missing"
                ).absolutePath
            )
        )

        filesDir.deleteRecursively()
    }

    private fun tempRoot(): File =
        kotlin.io.path
            .createTempDirectory(
                "appforge-v12-runtime-"
            )
            .toFile()
            .canonicalFile
}
