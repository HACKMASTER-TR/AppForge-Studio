package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test

class BuildStageTimingsTest {
    @Test fun reportsResultAndRethrowsWithoutLoggingExceptionDetails() {
        val events = mutableListOf<String>()
        val stages = BuildStageTimings(events::add)
        assertEquals(42, stages.measure(BuildStageTimings.Stage.ASSETS) { 42 })
        val failure = IllegalStateException("sensitive signing input")
        assertSame(failure, runCatching { stages.measure(BuildStageTimings.Stage.SIGNING) { throw failure } }.exceptionOrNull())
        assertTrue(events[0].matches(Regex("APPFORGE_BUILD_STAGE ASSETS PASS [0-9]+ms")))
        assertTrue(events[1].matches(Regex("APPFORGE_BUILD_STAGE SIGNING FAIL [0-9]+ms")))
        assertFalse(events.joinToString().contains("sensitive"))
        assertEquals(7, BuildStageTimings { error("telemetry failure") }.measure(BuildStageTimings.Stage.TEMPLATE) { 7 })
    }
}
