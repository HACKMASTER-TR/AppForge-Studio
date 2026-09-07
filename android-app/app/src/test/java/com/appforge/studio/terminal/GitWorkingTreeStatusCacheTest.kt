package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class GitWorkingTreeStatusCacheTest {

    @Test
    fun valueIsReusedInsideTtl() {
        var clock =
            1_000_000_000L

        val cache =
            TimedValueCache<
                String,
                String
                >(
                ttlMillis = 350L,
                nowNanos = {
                    clock
                }
            )

        cache.put(
            "repo",
            "first"
        )

        clock +=
            100_000_000L

        assertEquals(
            "first",
            cache.get(
                "repo"
            )
        )
    }


    @Test
    fun valueExpiresAfterTtl() {
        var clock =
            1_000_000_000L

        val cache =
            TimedValueCache<
                String,
                String
                >(
                ttlMillis = 350L,
                nowNanos = {
                    clock
                }
            )

        cache.put(
            "repo",
            "first"
        )

        clock +=
            351_000_000L

        assertNull(
            cache.get(
                "repo"
            )
        )

        assertEquals(
            0,
            cache.sizeForTests()
        )
    }


    @Test
    fun invalidationRemovesValueImmediately() {
        val cache =
            TimedValueCache<
                String,
                Int
                >(
                ttlMillis = 350L
            )

        cache.put(
            "repo",
            123
        )

        cache.remove(
            "repo"
        )

        assertNull(
            cache.get(
                "repo"
            )
        )
    }


    @Test
    fun repositoriesUseIndependentKeys() {
        val cache =
            TimedValueCache<
                String,
                String
                >(
                ttlMillis = 350L
            )

        cache.put(
            "repo-a",
            "A"
        )

        cache.put(
            "repo-b",
            "B"
        )

        assertEquals(
            "A",
            cache.get(
                "repo-a"
            )
        )

        assertEquals(
            "B",
            cache.get(
                "repo-b"
            )
        )
    }
}
