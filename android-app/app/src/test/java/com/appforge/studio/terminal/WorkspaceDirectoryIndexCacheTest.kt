package com.appforge.studio.terminal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class WorkspaceDirectoryIndexCacheTest {

    @Test
    fun directoryIndexIsReusedInsideTtl() {
        var clock =
            1_000_000_000L

        val cache =
            WorkspaceDirectoryIndexCache(
                ttlMillis =
                    1_500L,
                nowNanos = {
                    clock
                }
            )

        val children =
            listOf(
                File("/tmp/a"),
                File("/tmp/b")
            )

        cache.put(
            "/tmp/project",
            children
        )

        clock +=
            500_000_000L

        assertEquals(
            children,
            cache.get(
                "/tmp/project"
            )
        )
    }


    @Test
    fun directoryIndexExpiresAfterTtl() {
        var clock =
            1_000_000_000L

        val cache =
            WorkspaceDirectoryIndexCache(
                ttlMillis =
                    1_500L,
                nowNanos = {
                    clock
                }
            )

        cache.put(
            "/tmp/project",
            listOf(
                File("/tmp/a")
            )
        )

        clock +=
            1_501_000_000L

        assertNull(
            cache.get(
                "/tmp/project"
            )
        )

        assertEquals(
            0,
            cache.sizeForTests()
        )
    }


    @Test
    fun invalidationIsImmediate() {
        val cache =
            WorkspaceDirectoryIndexCache(
                ttlMillis =
                    1_500L
            )

        cache.put(
            "/tmp/project",
            listOf(
                File("/tmp/a")
            )
        )

        cache.remove(
            "/tmp/project"
        )

        assertNull(
            cache.get(
                "/tmp/project"
            )
        )
    }


    @Test
    fun cachedListUsesDefensiveCopy() {
        val cache =
            WorkspaceDirectoryIndexCache(
                ttlMillis =
                    1_500L
            )

        val mutable =
            mutableListOf(
                File("/tmp/a")
            )

        cache.put(
            "/tmp/project",
            mutable
        )

        mutable.add(
            File("/tmp/b")
        )

        assertEquals(
            1,
            cache.get(
                "/tmp/project"
            )
                ?.size
        )
    }
}
