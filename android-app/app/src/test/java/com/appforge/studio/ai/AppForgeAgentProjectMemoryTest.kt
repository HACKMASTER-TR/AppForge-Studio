package com.appforge.studio.ai

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentProjectMemoryTest {
    @Test
    fun checkpointRoundTripRestoresWorkspaceFiles() {
        val filesDir = tempRoot()
        val workspace = workspace(filesDir, "demo")
        File(workspace, "src/main.txt").apply {
            parentFile.mkdirs()
            writeText("v1")
        }

        val store = AppForgeAgentProjectMemoryStore(filesDir) { 1_000L }
        val checkpoint = store.createCheckpoint(
            "session_12345678",
            workspace,
            "before change"
        )

        File(workspace, "src/main.txt").writeText("v2")
        File(workspace, "new.txt").writeText("remove me")

        val restored = store.rollbackTo(
            "session_12345678",
            checkpoint.id,
            workspace
        )

        assertEquals("v1", File(workspace, "src/main.txt").readText())
        assertTrue(!File(workspace, "new.txt").exists())
        assertEquals(1, restored.restoredFiles)
    }

    @Test
    fun checkpointRejectsWorkspaceOutsideUnifiedAgentRoot() {
        val filesDir = tempRoot()
        val outside = File(filesDir, "outside").apply { mkdirs() }
        val store = AppForgeAgentProjectMemoryStore(filesDir)

        assertTrue(
            runCatching {
                store.createCheckpoint(
                    "session_12345678",
                    outside,
                    "unsafe"
                )
            }.isFailure
        )
    }

    @Test
    fun rollbackRejectsCorruptedPayload() {
        val filesDir = tempRoot()
        val workspace = workspace(filesDir, "demo")
        File(workspace, "a.txt").writeText("safe")
        val store = AppForgeAgentProjectMemoryStore(filesDir) { 2_000L }

        val checkpoint = store.createCheckpoint(
            "session_12345678",
            workspace,
            "safe"
        )

        File(
            filesDir,
            "unified-agent-project-memory/session_12345678/checkpoints/${checkpoint.id}/payload/a.txt"
        ).writeText("tampered")

        assertTrue(
            runCatching {
                store.rollbackLatest(
                    "session_12345678",
                    workspace
                )
            }.isFailure
        )
    }

    @Test
    fun checkpointHistoryIsBounded() {
        val filesDir = tempRoot()
        val workspace = workspace(filesDir, "demo")
        File(workspace, "a.txt").writeText("x")
        var now = 10_000L
        val store = AppForgeAgentProjectMemoryStore(filesDir) {
            now++
        }

        repeat(12) {
            store.createCheckpoint(
                "session_12345678",
                workspace,
                "c$it"
            )
        }

        assertEquals(
            8,
            store.listCheckpoints("session_12345678").size
        )
    }

    @Test
    fun symlinkIsNeverCheckpointedWhenSupported() {
        val filesDir = tempRoot()
        val workspace = workspace(filesDir, "demo")
        File(workspace, "a.txt").writeText("x")
        val outside = File(filesDir, "secret.txt").apply {
            writeText("secret")
        }

        val created = runCatching {
            Files.createSymbolicLink(
                File(workspace, "link.txt").toPath(),
                outside.toPath()
            )
        }.isSuccess

        if (created) {
            val store = AppForgeAgentProjectMemoryStore(filesDir)
            assertTrue(
                runCatching {
                    store.createCheckpoint(
                        "session_12345678",
                        workspace,
                        "symlink"
                    )
                }.isFailure
            )
        } else {
            assertTrue(true)
        }
    }

    private fun tempRoot(): File =
        Files.createTempDirectory(
            "appforge-v13-memory"
        ).toFile()

    private fun workspace(
        filesDir: File,
        name: String
    ): File =
        File(
            filesDir,
            "unified-agent-workspaces/$name"
        ).apply {
            mkdirs()
        }
}
