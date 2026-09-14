package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AppForgeAgentRemoteBuildResumePolicyTest {
    @Test
    fun runningStatusesRemainAttached() {
        listOf("queued", "running", "building", "preparing").forEach { status ->
            assertEquals(
                AppForgeAgentRemoteBuildResumeOutcome.RUNNING,
                AppForgeAgentRemoteBuildResumePolicy.classify(status, false)
            )
        }
    }

    @Test
    fun successWithArtifactCompletes() {
        assertEquals(
            AppForgeAgentRemoteBuildResumeOutcome.SUCCESS,
            AppForgeAgentRemoteBuildResumePolicy.classify("success", true)
        )
    }

    @Test
    fun successWithoutArtifactFailsSafely() {
        assertEquals(
            AppForgeAgentRemoteBuildResumeOutcome.FAILURE,
            AppForgeAgentRemoteBuildResumePolicy.classify("success", false)
        )
    }

    @Test
    fun terminalFailuresStayFailed() {
        listOf("failed", "error", "cancelled", "canceled").forEach { status ->
            assertEquals(
                AppForgeAgentRemoteBuildResumeOutcome.FAILURE,
                AppForgeAgentRemoteBuildResumePolicy.classify(status, false)
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsafeBuildIdIsRejected() {
        AppForgeAgentRemoteBuildResumePolicy.requireSafeBuildId("../other")
    }
}
