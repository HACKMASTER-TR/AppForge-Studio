package com.appforge.studio.terminal

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong


internal data class TerminalPerformanceSnapshot(
    val livePtyProcesses: Int,
    val livePtyDescriptors: Int,
    val peakLivePtyProcesses: Int,
    val peakLivePtyDescriptors: Int,
    val ptyStarts: Long,
    val ptyReleases: Long,
    val resourceBalanceErrors: Long,
    val publishCalls: Long,
    val snapshotsBuilt: Long,
    val snapshotsReused: Long,
    val totalPublishNanos: Long,
    val maxPublishNanos: Long
) {
    val averagePublishMicros: Long
        get() =
            if (
                publishCalls <= 0L
            ) {
                0L
            } else {
                (
                    totalPublishNanos /
                        publishCalls
                    ) /
                    1_000L
            }

    val snapshotReusePercent: Int
        get() {
            val total =
                snapshotsBuilt +
                    snapshotsReused

            if (total <= 0L) {
                return 0
            }

            return (
                snapshotsReused *
                    100L /
                    total
                )
                .toInt()
                .coerceIn(
                    0,
                    100
                )
        }
}


/*
 * Lightweight runtime metrics.
 *
 * No Compose state is published here. Counters are atomic so terminal
 * rendering never triggers extra recompositions merely for diagnostics.
 */
internal object TerminalPerformanceMetrics {

    private val livePtyProcesses =
        AtomicInteger(0)

    private val livePtyDescriptors =
        AtomicInteger(0)

    private val peakLivePtyProcesses =
        AtomicInteger(0)

    private val peakLivePtyDescriptors =
        AtomicInteger(0)

    private val ptyStarts =
        AtomicLong(0L)

    private val ptyReleases =
        AtomicLong(0L)

    private val resourceBalanceErrors =
        AtomicLong(0L)

    private val publishCalls =
        AtomicLong(0L)

    private val snapshotsBuilt =
        AtomicLong(0L)

    private val snapshotsReused =
        AtomicLong(0L)

    private val totalPublishNanos =
        AtomicLong(0L)

    private val maxPublishNanos =
        AtomicLong(0L)


    fun onPtySpawned(
        descriptorCount: Int =
            NATIVE_PTY_DESCRIPTOR_COUNT
    ) {
        require(
            descriptorCount >= 0
        )

        val processes =
            livePtyProcesses
                .incrementAndGet()

        val descriptors =
            livePtyDescriptors
                .addAndGet(
                    descriptorCount
                )

        ptyStarts.incrementAndGet()

        updateMaximum(
            peakLivePtyProcesses,
            processes
        )

        updateMaximum(
            peakLivePtyDescriptors,
            descriptors
        )
    }


    fun onPtyResourcesReleased(
        descriptorCount: Int =
            NATIVE_PTY_DESCRIPTOR_COUNT
    ) {
        require(
            descriptorCount >= 0
        )

        decrementNonNegative(
            livePtyProcesses,
            1
        )

        decrementNonNegative(
            livePtyDescriptors,
            descriptorCount
        )

        ptyReleases.incrementAndGet()
    }


    fun recordPublish(
        snapshotBuildCount: Int,
        snapshotReuseCount: Int,
        durationNanos: Long
    ) {
        publishCalls.incrementAndGet()

        snapshotsBuilt.addAndGet(
            snapshotBuildCount
                .coerceAtLeast(0)
                .toLong()
        )

        snapshotsReused.addAndGet(
            snapshotReuseCount
                .coerceAtLeast(0)
                .toLong()
        )

        val safeDuration =
            durationNanos
                .coerceAtLeast(0L)

        totalPublishNanos
            .addAndGet(
                safeDuration
            )

        updateMaximum(
            maxPublishNanos,
            safeDuration
        )
    }


    fun snapshot():
        TerminalPerformanceSnapshot =
        TerminalPerformanceSnapshot(
            livePtyProcesses =
                livePtyProcesses.get(),

            livePtyDescriptors =
                livePtyDescriptors.get(),

            peakLivePtyProcesses =
                peakLivePtyProcesses.get(),

            peakLivePtyDescriptors =
                peakLivePtyDescriptors.get(),

            ptyStarts =
                ptyStarts.get(),

            ptyReleases =
                ptyReleases.get(),

            resourceBalanceErrors =
                resourceBalanceErrors.get(),

            publishCalls =
                publishCalls.get(),

            snapshotsBuilt =
                snapshotsBuilt.get(),

            snapshotsReused =
                snapshotsReused.get(),

            totalPublishNanos =
                totalPublishNanos.get(),

            maxPublishNanos =
                maxPublishNanos.get()
        )


    /*
     * Unit-test only helper. Production code never resets the counters.
     */
    internal fun resetForTests() {
        livePtyProcesses.set(0)
        livePtyDescriptors.set(0)
        peakLivePtyProcesses.set(0)
        peakLivePtyDescriptors.set(0)
        ptyStarts.set(0L)
        ptyReleases.set(0L)
        resourceBalanceErrors.set(0L)
        publishCalls.set(0L)
        snapshotsBuilt.set(0L)
        snapshotsReused.set(0L)
        totalPublishNanos.set(0L)
        maxPublishNanos.set(0L)
    }


    private fun decrementNonNegative(
        counter: AtomicInteger,
        amount: Int
    ) {
        while (true) {
            val current =
                counter.get()

            val next =
                (
                    current -
                        amount
                    )
                    .coerceAtLeast(0)

            if (
                counter.compareAndSet(
                    current,
                    next
                )
            ) {
                if (
                    current <
                        amount
                ) {
                    resourceBalanceErrors
                        .incrementAndGet()
                }

                return
            }
        }
    }


    private fun updateMaximum(
        counter: AtomicInteger,
        candidate: Int
    ) {
        var current =
            counter.get()

        while (
            candidate >
                current
        ) {
            if (
                counter.compareAndSet(
                    current,
                    candidate
                )
            ) {
                return
            }

            current =
                counter.get()
        }
    }


    private fun updateMaximum(
        counter: AtomicLong,
        candidate: Long
    ) {
        var current =
            counter.get()

        while (
            candidate >
                current
        ) {
            if (
                counter.compareAndSet(
                    current,
                    candidate
                )
            ) {
                return
            }

            current =
                counter.get()
        }
    }


    private const val NATIVE_PTY_DESCRIPTOR_COUNT =
        3
}
