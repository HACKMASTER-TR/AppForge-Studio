package com.appforge.studio.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentFinalAcceptanceTest {
    @Test
    fun finalAcceptanceRequiresManualReviewGate() {
        val report = AppForgeAgentFinalAcceptance.evaluate(
            state = successfulState(),
            artifacts = artifacts(),
            releaseReviewState = review(
                ready = true,
                reviewRequired = false
            ),
            workspacePath = "/safe/workspace"
        )

        assertTrue(!report.ready)
        assertTrue(report.manualReviewRequired)
    }

    @Test
    fun finalAcceptanceBlocksWithoutReleaseReadiness() {
        val report = AppForgeAgentFinalAcceptance.evaluate(
            state = successfulState(),
            artifacts = artifacts(),
            releaseReviewState = review(
                ready = false,
                reviewRequired = true
            ),
            workspacePath = "/safe/workspace"
        )

        assertTrue(!report.ready)
    }

    @Test
    fun finalAcceptanceBlocksWithoutWorkspace() {
        val report = AppForgeAgentFinalAcceptance.evaluate(
            state = successfulState(),
            artifacts = artifacts(),
            releaseReviewState = review(
                ready = true,
                reviewRequired = true
            ),
            workspacePath = null
        )

        assertTrue(!report.ready)
    }

    @Test
    fun finalAcceptancePassesCompleteResultButNeverAutoDeploys() {
        val report = AppForgeAgentFinalAcceptance.evaluate(
            state = successfulState(),
            artifacts = artifacts(),
            releaseReviewState = review(
                ready = true,
                reviewRequired = true
            ),
            workspacePath = "/safe/workspace"
        )

        assertTrue(report.ready)
        assertTrue(report.manualReviewRequired)
        assertTrue(report.summary.contains("manual review"))
    }

    @Test
    fun qualityFailureBlocksFinalAcceptance() {
        val badQuality = AppForgeAgentQualityReport(
            score = 10,
            findings = listOf(
                AppForgeAgentQualityFinding(
                    AppForgeAgentQualityLevel.ERROR,
                    "test",
                    "blocked"
                )
            )
        )

        val report = AppForgeAgentFinalAcceptance.evaluate(
            state = successfulState(),
            artifacts = artifacts(),
            releaseReviewState = review(
                ready = true,
                reviewRequired = true
            ),
            workspacePath = "/safe/workspace",
            quality = badQuality
        )

        assertTrue(!report.ready)
    }

    private fun successfulState(): AppForgeAgentStudioState {
        val blueprint = AppForgeAgentBlueprint(
            appName = "Demo",
            prompt = "Demo",
            platform = AppForgeAgentPlatform.ANDROID,
            startRoute = "/home",
            screens = listOf(
                AppForgeAgentScreenSpec(
                    id = "home",
                    title = "Home",
                    route = "/home",
                    purpose = "Home"
                )
            )
        )

        return AppForgeAgentStudioState(
            step = AppForgeAgentStudioStep.RESULT,
            prompt = blueprint.prompt,
            platform = blueprint.platform,
            blueprint = blueprint,
            validation =
                AppForgeAgentBlueprintValidator.validate(
                    blueprint
                ),
            remoteBuild = AppForgeAgentRemoteBuildInfo(
                buildId = "build_12345678",
                buildNo = 1L,
                status = "success",
                progress = 100,
                apkAvailable = true,
                aabAvailable = true,
                exeAvailable = false
            )
        )
    }

    private fun artifacts() =
        AppForgeAgentArtifactState(
            buildId = "build_12345678",
            testLabAvailable = true
        )

    private fun review(
        ready: Boolean,
        reviewRequired: Boolean
    ) =
        AppForgeAgentReleaseReviewState(
            readiness =
                AppForgeAgentReleaseReadiness(
                    ready = ready,
                    reviewRequired = reviewRequired,
                    blockers =
                        if (ready) {
                            emptyList()
                        } else {
                            listOf("blocked")
                        }
                )
        )
}
