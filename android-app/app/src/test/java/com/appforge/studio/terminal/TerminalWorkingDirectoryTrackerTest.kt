package com.appforge.studio.terminal

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class TerminalWorkingDirectoryTrackerTest {

    @Test
    fun markerCanSpanMultiplePtyChunks() {
        val tracker =
            TerminalWorkingDirectoryTracker()

        assertNull(
            tracker.feed(
                "\u001b]777;appforge-"
            )
        )

        assertEquals(
            "/root/AppForge-Studio",
            tracker.feed(
                "cwd=/root/AppForge-Studio\u0007"
            )
        )
    }

    @Test
    fun latestCompleteMarkerWins() {
        val tracker =
            TerminalWorkingDirectoryTracker()

        assertEquals(
            "/workspace/two",
            tracker.feed(
                "\u001b]777;appforge-cwd=/workspace/one\u0007" +
                    "\u001b]777;appforge-cwd=/workspace/two\u0007"
            )
        )
    }

    @Test
    fun relativeMarkerIsRejected() {
        val tracker =
            TerminalWorkingDirectoryTracker()

        assertNull(
            tracker.feed(
                "\u001b]777;appforge-cwd=relative/path\u0007"
            )
        )
    }

    @Test
    fun guestPathsMapToWorkspaceAndRootfs() {
        val base =
            Files.createTempDirectory(
                "appforge-cwd"
            ).toFile()

        try {
            val rootfs =
                File(
                    base,
                    "rootfs"
                ).apply {
                    mkdirs()
                }

            val workspace =
                File(
                    base,
                    "workspace"
                ).apply {
                    mkdirs()
                }

            val src =
                File(
                    workspace,
                    "src"
                ).apply {
                    mkdirs()
                }

            val repo =
                File(
                    rootfs,
                    "root/AppForge-Studio"
                ).apply {
                    mkdirs()
                }

            assertEquals(
                src.canonicalFile,
                TerminalWorkingDirectoryResolver
                    .resolveLinuxGuestDirectory(
                        rootfs,
                        workspace,
                        "/workspace/src"
                    )
            )

            assertEquals(
                repo.canonicalFile,
                TerminalWorkingDirectoryResolver
                    .resolveLinuxGuestDirectory(
                        rootfs,
                        workspace,
                        "/root/AppForge-Studio"
                    )
            )

            assertNull(
                TerminalWorkingDirectoryResolver
                    .resolveLinuxGuestDirectory(
                        rootfs,
                        workspace,
                        "/workspace/../../escape"
                    )
            )
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun nearestGitRootIsUsed() {
        val base =
            Files.createTempDirectory(
                "appforge-git-root"
            ).toFile()

        try {
            val repo =
                File(
                    base,
                    "repo"
                ).apply {
                    mkdirs()
                }

            File(
                repo,
                ".git"
            ).mkdirs()

            val child =
                File(
                    repo,
                    "src/main"
                ).apply {
                    mkdirs()
                }

            assertEquals(
                repo.canonicalFile,
                TerminalWorkingDirectoryResolver
                    .findGitRoot(
                        child
                    )
            )
        } finally {
            base.deleteRecursively()
        }
    }
}
