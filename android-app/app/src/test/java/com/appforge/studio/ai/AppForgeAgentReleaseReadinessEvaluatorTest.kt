package com.appforge.studio.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentReleaseReadinessEvaluatorTest {
    @Test
    fun successfulMobileBuildWithTestLabCanBecomeReady() {
        val remote =
            remote(
                status = "success",
                apk = true
            )

        val artifacts =
            AppForgeAgentArtifactState(
                buildId = remote.buildId,
                testLabAvailable = true
            )

        val result =
            AppForgeAgentReleaseReadinessEvaluator.evaluate(
                remote,
                artifacts
            )

        assertTrue(result.ready)
        assertTrue(result.reviewRequired)
        assertTrue(result.blockers.isEmpty())
    }

    @Test
    fun failedBuildIsBlocked() {
        val result =
            AppForgeAgentReleaseReadinessEvaluator.evaluate(
                remote(
                    status = "failed",
                    apk = true
                ),
                AppForgeAgentArtifactState(
                    buildId = "build-1",
                    testLabAvailable = true
                )
            )

        assertFalse(result.ready)
        assertTrue(
            result.blockers.any {
                it.contains("SUCCESS")
            }
        )
    }

    @Test
    fun mobileArtifactRequiresTestLab() {
        val result =
            AppForgeAgentReleaseReadinessEvaluator.evaluate(
                remote(
                    status = "success",
                    apk = true
                ),
                AppForgeAgentArtifactState(
                    buildId = "build-1",
                    testLabAvailable = false
                )
            )

        assertFalse(result.ready)
        assertTrue(
            result.blockers.any {
                it.contains("Test Lab")
            }
        )
    }

    @Test
    fun highSecurityFindingBlocksRelease() {
        val result =
            AppForgeAgentReleaseReadinessEvaluator.evaluate(
                remote(
                    status = "success",
                    apk = true
                ),
                AppForgeAgentArtifactState(
                    buildId = "build-1",
                    testLabAvailable = true,
                    security = listOf(
                        AppForgeAgentSecurityFinding(
                            severity = "HIGH",
                            title = "Risk",
                            detail = "Test"
                        )
                    )
                )
            )

        assertFalse(result.ready)
        assertTrue(
            result.blockers.any {
                it.contains("HIGH/CRITICAL")
            }
        )
    }

    @Test
    fun exeOnlyBuildDoesNotRequireMobileTestLab() {
        val result =
            AppForgeAgentReleaseReadinessEvaluator.evaluate(
                remote(
                    status = "success",
                    exe = true
                ),
                AppForgeAgentArtifactState(
                    buildId = "build-1",
                    testLabAvailable = false
                )
            )

        assertTrue(result.ready)
        assertTrue(result.reviewRequired)
    }

    private fun remote(
        status: String,
        apk: Boolean = false,
        aab: Boolean = false,
        exe: Boolean = false
    ) =
        AppForgeAgentRemoteBuildInfo(
            buildId = "build-1",
            buildNo = 42,
            status = status,
            progress =
                if (
                    status.equals(
                        "success",
                        ignoreCase = true
                    )
                ) {
                    100
                } else {
                    75
                },
            apkAvailable = apk,
            aabAvailable = aab,
            exeAvailable = exe
        )
}
