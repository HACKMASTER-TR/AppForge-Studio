package com.appforge.studio.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentRecoveryPolicyTest {
    @Test
    fun freshBuildWithIdIsSafeAndReattachable() {
        val filesDir =
            tempRoot()

        val assessment =
            AppForgeAgentRecoveryPolicy.assess(
                filesDir =
                    filesDir,
                session =
                    buildSession(
                        updatedAt =
                            10_000L,
                        buildId =
                            "build-100"
                    ),
                nowEpochMs =
                    20_000L
            )

        assertTrue(
            assessment.safeToOpen
        )

        assertEquals(
            "build-100",
            assessment.resumableBuildId
        )

        assertFalse(
            assessment.staleBuild
        )

        filesDir.deleteRecursively()
    }

    @Test
    fun staleBuildIsWarningButStillSafeToOpen() {
        val filesDir =
            tempRoot()

        val assessment =
            AppForgeAgentRecoveryPolicy.assess(
                filesDir =
                    filesDir,
                session =
                    buildSession(
                        updatedAt =
                            1_000L,
                        buildId =
                            "build-101"
                    ),
                nowEpochMs =
                    7L * 60L * 60L * 1000L
            )

        assertTrue(
            assessment.safeToOpen
        )

        assertTrue(
            assessment.staleBuild
        )

        assertTrue(
            assessment.issues.any {
                it.code ==
                    "BUILD_STALE"
            }
        )

        filesDir.deleteRecursively()
    }

    @Test
    fun buildWithoutRemoteIdIsBlocked() {
        val filesDir =
            tempRoot()

        val assessment =
            AppForgeAgentRecoveryPolicy.assess(
                filesDir =
                    filesDir,
                session =
                    AppForgeAgentPersistentSession(
                        sessionId =
                            "session-recovery-03",
                        updatedAtEpochMs =
                            1_000L,
                        state =
                            AppForgeAgentStudioState(
                                step =
                                    AppForgeAgentStudioStep.BUILD,
                                prompt =
                                    "Uygulama"
                            )
                    ),
                nowEpochMs =
                    2_000L
            )

        assertFalse(
            assessment.safeToOpen
        )

        assertTrue(
            assessment.issues.any {
                it.code ==
                    "BUILD_ID_MISSING" &&
                    it.severity ==
                        AppForgeAgentRecoverySeverity.BLOCKER
            }
        )

        filesDir.deleteRecursively()
    }

    @Test
    fun missingWorkspaceProducesNonFatalWarning() {
        val filesDir =
            tempRoot()

        val assessment =
            AppForgeAgentRecoveryPolicy.assess(
                filesDir =
                    filesDir,
                session =
                    AppForgeAgentPersistentSession(
                        sessionId =
                            "session-recovery-04",
                        updatedAtEpochMs =
                            1_000L,
                        state =
                            AppForgeAgentStudioState(
                                step =
                                    AppForgeAgentStudioStep.RESULT,
                                prompt =
                                    "Uygulama"
                            ),
                        workspacePath =
                            File(
                                filesDir,
                                "unified-agent-workspaces/missing"
                            ).absolutePath
                    ),
                nowEpochMs =
                    2_000L
            )

        assertTrue(
            assessment.safeToOpen
        )

        assertFalse(
            assessment.workspaceRestorable
        )

        assertTrue(
            assessment.issues.any {
                it.code ==
                    "WORKSPACE_MISSING"
            }
        )

        filesDir.deleteRecursively()
    }

    @Test
    fun quarantineCountIsSurfacedWithoutBlockingHealthySession() {
        val filesDir =
            tempRoot()

        val assessment =
            AppForgeAgentRecoveryPolicy.assess(
                filesDir =
                    filesDir,
                session =
                    AppForgeAgentPersistentSession(
                        sessionId =
                            "session-recovery-05",
                        updatedAtEpochMs =
                            1_000L,
                        state =
                            AppForgeAgentStudioState(
                                prompt =
                                    "Uygulama"
                            )
                    ),
                nowEpochMs =
                    2_000L,
                quarantinedSessionCount =
                    2
            )

        assertTrue(
            assessment.safeToOpen
        )

        assertEquals(
            2,
            assessment.quarantinedSessionCount
        )

        assertTrue(
            assessment.issues.any {
                it.code ==
                    "QUARANTINE_PRESENT"
            }
        )

        filesDir.deleteRecursively()
    }

    private fun buildSession(
        updatedAt: Long,
        buildId: String
    ) =
        AppForgeAgentPersistentSession(
            sessionId =
                "session-recovery-build",
            updatedAtEpochMs =
                updatedAt,
            state =
                AppForgeAgentStudioState(
                    step =
                        AppForgeAgentStudioStep.BUILD,
                    prompt =
                        "Uygulama",
                    remoteBuild =
                        AppForgeAgentRemoteBuildInfo(
                            buildId =
                                buildId,
                            buildNo =
                                1,
                            status =
                                "running",
                            progress =
                                20,
                            apkAvailable =
                                false,
                            aabAvailable =
                                false,
                            exeAvailable =
                                false
                        )
                )
        )

    private fun tempRoot(): File =
        kotlin.io.path
            .createTempDirectory(
                "appforge-v12-recovery-"
            )
            .toFile()
            .canonicalFile
}
