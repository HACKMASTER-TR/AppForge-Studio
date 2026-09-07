package com.appforge.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class BuildRuntimeStateTest {

    @Test
    fun defaultsMatchExistingBuildRuntime() {
        val state =
            BuildRuntimeState()

        assertEquals(
            "Hazır",
            state.status.value
        )

        assertEquals(
            0,
            state.progress.intValue
        )

        assertNull(
            state.buildStartedAtMs.value
        )

        assertEquals(
            0L,
            state.buildElapsedMs.longValue
        )

        assertFalse(
            state.buildTimerRunning.value
        )

        assertTrue(
            state.logs.value.isEmpty()
        )

        assertTrue(
            state.preflight.value.isEmpty()
        )

        assertNull(
            state.buildId.value
        )

        assertNull(
            state.buildNo.value
        )

        assertNull(
            state.apkUrl.value
        )

        assertNull(
            state.aabUrl.value
        )

        assertNull(
            state.exeUrl.value
        )

        assertNull(
            state.queuePosition.value
        )

        assertNull(
            state.queueAhead.value
        )

        assertEquals(
            0,
            state.queueWorkerSlots.intValue
        )

        assertNull(
            state.queueEtaSeconds.value
        )

        assertNull(
            state.queueEstimate.value
        )
    }


    @Test
    fun stateInstancesAreIndependent() {
        val first =
            BuildRuntimeState()

        val second =
            BuildRuntimeState()

        first.status.value =
            "Building"

        first.progress.intValue =
            72

        first.logs.value =
            listOf(
                "one",
                "two"
            )

        assertEquals(
            "Building",
            first.status.value
        )

        assertEquals(
            72,
            first.progress.intValue
        )

        assertEquals(
            "Hazır",
            second.status.value
        )

        assertEquals(
            0,
            second.progress.intValue
        )

        assertTrue(
            second.logs.value.isEmpty()
        )
    }
}
