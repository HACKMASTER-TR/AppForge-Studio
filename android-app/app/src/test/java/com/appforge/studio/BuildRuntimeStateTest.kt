package com.appforge.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildRuntimeStateTest {
    @Test fun defaultsMatchExistingBuildRuntime() {
        val state = BuildRuntimeState()
        assertEquals("Hazır", state.status.value)
        assertEquals(0, state.progress.intValue)
        assertNull(state.buildStartedAtMs.value)
        assertEquals(0L, state.buildElapsedMs.longValue)
        assertFalse(state.buildTimerRunning.value)
        assertTrue(state.logs.value.isEmpty())
        assertTrue(state.preflight.value.isEmpty())
        assertNull(state.buildId.value)
        assertNull(state.buildNo.value)
        assertNull(state.apkUrl.value)
        assertNull(state.aabUrl.value)
        assertNull(state.exeUrl.value)
    }

    @Test fun stateInstancesAreIndependent() {
        val first = BuildRuntimeState()
        val second = BuildRuntimeState()
        first.status.value = "Building"
        first.progress.intValue = 72
        assertEquals(72, first.progress.intValue)
        assertEquals("Hazır", second.status.value)
        assertEquals(0, second.progress.intValue)
    }

    @Test fun userFacingBuildNumberUsesTenDigits() {
        assertEquals("AF-0000001000", AppForgeBuildNumbers.label(1_000L))
        assertEquals("AF-0000001001", AppForgeBuildNumbers.label(1_001L))
    }

    @Test fun visibleProgressOnlySuccessCanReachOneHundred() {
        assertEquals(99, AppForgeBuildProgress.visible("building", 100))
        assertEquals(65, AppForgeBuildProgress.visible("failed", 65))
        assertEquals(65, AppForgeBuildProgress.visible("cancelled", 65))
        assertEquals(100, AppForgeBuildProgress.visible("success", 65))
    }
}
