package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateAgentModeTest {
    private fun health() =
        ProjectHealthReport(
            issues = emptyList(),
            runtime = emptyMap(),
            requiredCommands = emptySet(),
            missingCommands = emptySet()
        )

    private fun failedPipeline(
        phase: ProjectPipelinePhase,
        output: String
    ) =
        ProjectPipelineRunResult(
            success = false,
            deployReady = false,
            steps =
                listOf(
                    ProjectPipelineStepResult(
                        phase = phase,
                        title = "Test failure",
                        status = ProjectPipelineStatus.FAILED,
                        exitCode = 1,
                        output = output
                    )
                ),
            health = health(),
            failureContext = "masked packet"
        )

    @Test
    fun transientInstallFailureGetsBoundedSafeRetry() {
        val decision =
            UltimateAgentRepairPolicy.decide(
                pipeline =
                    failedPipeline(
                        ProjectPipelinePhase.INSTALL,
                        "npm ERR! ECONNRESET connection reset"
                    ),
                repairAttempts = 0,
                config = UltimateAgentModeConfig(maxRepairAttempts = 2)
            )

        assertEquals(
            UltimateAgentRepairDisposition.RETRY_SAFE,
            decision.disposition
        )
    }

    @Test
    fun buildFailureNeverRunsBlindShellFix() {
        val decision =
            UltimateAgentRepairPolicy.decide(
                pipeline =
                    failedPipeline(
                        ProjectPipelinePhase.BUILD,
                        "Compilation failed"
                    ),
                repairAttempts = 0,
                config = UltimateAgentModeConfig(maxRepairAttempts = 2)
            )

        assertEquals(
            UltimateAgentRepairDisposition.AI_PATCH_REQUIRED,
            decision.disposition
        )
        assertTrue(decision.reason.contains("AI", ignoreCase = true))
    }

    @Test
    fun retryBudgetStopsAutomation() {
        val decision =
            UltimateAgentRepairPolicy.decide(
                pipeline =
                    failedPipeline(
                        ProjectPipelinePhase.INSTALL,
                        "timeout"
                    ),
                repairAttempts = 2,
                config = UltimateAgentModeConfig(maxRepairAttempts = 2)
            )

        assertEquals(
            UltimateAgentRepairDisposition.MANUAL_REQUIRED,
            decision.disposition
        )
    }

    @Test
    fun configRejectsUnboundedRetries() {
        var rejected = false

        try {
            UltimateAgentModeConfig(maxRepairAttempts = 10)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertFalse(
            runCatching {
                UltimateAgentModeConfig(maxRepairAttempts = 3)
            }.isFailure
        )
    }
}
