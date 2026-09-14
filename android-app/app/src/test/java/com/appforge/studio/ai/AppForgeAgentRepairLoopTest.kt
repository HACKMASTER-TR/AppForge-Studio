package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentRepairLoopTest {
    @Test
    fun transientInstallRetriesWithinBound() {
        val loop = AppForgeAgentRepairLoop(
            AppForgeAgentRepairLoopConfig(
                maxRepairAttempts = 2,
                maxSameFingerprint = 2
            )
        )

        val first = loop.decide(
            AppForgeAgentFailure(
                phase = AppForgeAgentFailurePhase.INSTALL,
                exitCode = 1,
                output = "temporary failure: could not resolve host"
            )
        )

        assertEquals(AppForgeAgentRepairAction.RETRY_PIPELINE, first.action)
        assertEquals(1, first.attempt)
    }

    @Test
    fun buildFailureRequestsStructuredPatch() {
        val loop = AppForgeAgentRepairLoop()
        val decision = loop.decide(
            AppForgeAgentFailure(
                phase = AppForgeAgentFailurePhase.BUILD,
                exitCode = 1,
                output = "Compilation failed: unresolved reference"
            )
        )

        assertEquals(AppForgeAgentRepairAction.REQUEST_STRUCTURED_PATCH, decision.action)
        assertTrue(decision.fingerprint.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun repeatedFingerprintStopsLoop() {
        val loop = AppForgeAgentRepairLoop(
            AppForgeAgentRepairLoopConfig(
                maxRepairAttempts = 3,
                maxSameFingerprint = 1
            )
        )
        val failure = AppForgeAgentFailure(
            phase = AppForgeAgentFailurePhase.TEST,
            exitCode = 1,
            output = "same deterministic failure"
        )

        assertEquals(
            AppForgeAgentRepairAction.REQUEST_STRUCTURED_PATCH,
            loop.decide(failure).action
        )
        assertEquals(
            AppForgeAgentRepairAction.STOP_MANUAL,
            loop.decide(failure).action
        )
    }

    @Test
    fun fingerprintRedactsVolatileDurations() {
        val first = AppForgeAgentFailureFingerprint.of(
            AppForgeAgentFailure(
                phase = AppForgeAgentFailurePhase.TEST,
                output = "failed after 123.4ms at 0xABCDEF"
            )
        )
        val second = AppForgeAgentFailureFingerprint.of(
            AppForgeAgentFailure(
                phase = AppForgeAgentFailurePhase.TEST,
                output = "failed after 999ms at 0x123456"
            )
        )

        assertEquals(first, second)
    }

    @Test
    fun phaseChangesFingerprint() {
        val output = "same output"
        val test = AppForgeAgentFailureFingerprint.of(
            AppForgeAgentFailure(AppForgeAgentFailurePhase.TEST, 1, output)
        )
        val build = AppForgeAgentFailureFingerprint.of(
            AppForgeAgentFailure(AppForgeAgentFailurePhase.BUILD, 1, output)
        )
        assertNotEquals(test, build)
    }
}
