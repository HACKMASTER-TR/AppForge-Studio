package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class TerminalPerformanceMetricsTest {

    @Test
    fun ptyResourcesReturnToZeroAfterBalancedRelease() {
        TerminalPerformanceMetrics
            .resetForTests()

        TerminalPerformanceMetrics
            .onPtySpawned()

        TerminalPerformanceMetrics
            .onPtySpawned()

        var snapshot =
            TerminalPerformanceMetrics
                .snapshot()

        assertEquals(
            2,
            snapshot.livePtyProcesses
        )

        assertEquals(
            6,
            snapshot.livePtyDescriptors
        )

        assertEquals(
            2,
            snapshot.peakLivePtyProcesses
        )

        assertEquals(
            6,
            snapshot.peakLivePtyDescriptors
        )

        TerminalPerformanceMetrics
            .onPtyResourcesReleased()

        TerminalPerformanceMetrics
            .onPtyResourcesReleased()

        snapshot =
            TerminalPerformanceMetrics
                .snapshot()

        assertEquals(
            0,
            snapshot.livePtyProcesses
        )

        assertEquals(
            0,
            snapshot.livePtyDescriptors
        )

        assertEquals(
            2L,
            snapshot.ptyStarts
        )

        assertEquals(
            2L,
            snapshot.ptyReleases
        )

        assertEquals(
            0L,
            snapshot.resourceBalanceErrors
        )
    }


    @Test
    fun detectsUnbalancedResourceRelease() {
        TerminalPerformanceMetrics
            .resetForTests()

        TerminalPerformanceMetrics
            .onPtyResourcesReleased()

        val snapshot =
            TerminalPerformanceMetrics
                .snapshot()

        assertEquals(
            0,
            snapshot.livePtyProcesses
        )

        assertEquals(
            0,
            snapshot.livePtyDescriptors
        )

        assertTrue(
            snapshot.resourceBalanceErrors >
                0L
        )
    }


    @Test
    fun recordsSnapshotCacheAndPublishTiming() {
        TerminalPerformanceMetrics
            .resetForTests()

        TerminalPerformanceMetrics
            .recordPublish(
                snapshotBuildCount = 1,
                snapshotReuseCount = 5,
                durationNanos = 900_000L
            )

        TerminalPerformanceMetrics
            .recordPublish(
                snapshotBuildCount = 0,
                snapshotReuseCount = 6,
                durationNanos = 300_000L
            )

        val snapshot =
            TerminalPerformanceMetrics
                .snapshot()

        assertEquals(
            2L,
            snapshot.publishCalls
        )

        assertEquals(
            1L,
            snapshot.snapshotsBuilt
        )

        assertEquals(
            11L,
            snapshot.snapshotsReused
        )

        assertEquals(
            600L,
            snapshot.averagePublishMicros
        )

        assertEquals(
            91,
            snapshot.snapshotReusePercent
        )

        assertEquals(
            900_000L,
            snapshot.maxPublishNanos
        )
    }
}
