package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Test


class TerminalOutputBackpressureTest {

    @Test
    fun activeTerminalKeepsLowLatencyForNormalOutput() {
        assertEquals(
            32L,
            TerminalOutputBackpressure
                .targetPublishDelayMillis(
                    active = true,
                    pendingChars = 1_000L
                )
        )
    }


    @Test
    fun activeTerminalSlowsOnlyDuringLargeBursts() {
        assertEquals(
            48L,
            TerminalOutputBackpressure
                .targetPublishDelayMillis(
                    active = true,
                    pendingChars =
                        20L * 1024L
                )
        )

        assertEquals(
            64L,
            TerminalOutputBackpressure
                .targetPublishDelayMillis(
                    active = true,
                    pendingChars =
                        100L * 1024L
                )
        )

        assertEquals(
            96L,
            TerminalOutputBackpressure
                .targetPublishDelayMillis(
                    active = true,
                    pendingChars =
                        300L * 1024L
                )
        )
    }


    @Test
    fun backgroundTerminalUsesLowerRenderFrequency() {
        assertEquals(
            250L,
            TerminalOutputBackpressure
                .targetPublishDelayMillis(
                    active = false,
                    pendingChars = 1_000L
                )
        )

        assertEquals(
            325L,
            TerminalOutputBackpressure
                .targetPublishDelayMillis(
                    active = false,
                    pendingChars =
                        100L * 1024L
                )
        )

        assertEquals(
            400L,
            TerminalOutputBackpressure
                .targetPublishDelayMillis(
                    active = false,
                    pendingChars =
                        300L * 1024L
                )
        )
    }


    @Test
    fun pressureCounterIsBounded() {
        val first =
            TerminalOutputBackpressure
                .accumulatePendingChars(
                    current = 0L,
                    additional = 8_192
                )

        assertEquals(
            8_192L,
            first
        )

        val saturated =
            TerminalOutputBackpressure
                .accumulatePendingChars(
                    current =
                        Long.MAX_VALUE,
                    additional =
                        Int.MAX_VALUE
                )

        assertEquals(
            1024L * 1024L,
            saturated
        )
    }


    @Test
    fun negativeValuesCannotCorruptPressureState() {
        assertEquals(
            0L,
            TerminalOutputBackpressure
                .accumulatePendingChars(
                    current = -500L,
                    additional = -100
                )
        )
    }
}
