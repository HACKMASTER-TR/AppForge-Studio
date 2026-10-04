package com.appforge.studio.play

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayReviewGateTest {

    @Test
    fun `fewer than three successful builds never requests review`() {
        assertFalse(
            shouldRequestPlayReview(
                successfulBuilds = 2,
                lastAttemptAtMs = 0L,
                nowMs = 1_000L
            )
        )
    }

    @Test
    fun `third successful build can request first review`() {
        assertTrue(
            shouldRequestPlayReview(
                successfulBuilds = 3,
                lastAttemptAtMs = 0L,
                nowMs = 1_000L
            )
        )
    }

    @Test
    fun `review cooldown blocks repeated requests`() {
        val last =
            1_000_000L

        assertFalse(
            shouldRequestPlayReview(
                successfulBuilds = 10,
                lastAttemptAtMs = last,
                nowMs =
                    last +
                        PLAY_REVIEW_COOLDOWN_MS -
                        1L
            )
        )
    }

    @Test
    fun `review becomes eligible after cooldown`() {
        val last =
            1_000_000L

        assertTrue(
            shouldRequestPlayReview(
                successfulBuilds = 10,
                lastAttemptAtMs = last,
                nowMs =
                    last +
                        PLAY_REVIEW_COOLDOWN_MS
            )
        )
    }
}
