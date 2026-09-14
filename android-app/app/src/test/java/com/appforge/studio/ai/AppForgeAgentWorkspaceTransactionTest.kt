package com.appforge.studio.ai

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentWorkspaceTransactionTest {
    @Test
    fun applyAndRollbackNewFile() {
        val root = Files.createTempDirectory("appforge-v4-new").toFile()
        try {
            val project = project("src/main.txt", "hello")
            val result = AppForgeAgentWorkspaceTransaction.apply(root, project)
            val target = root.resolve("src/main.txt")
            assertEquals("hello", target.readText())

            AppForgeAgentWorkspaceTransaction.rollback(result.checkpoint)
            assertFalse(target.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rollbackRestoresOverwrittenFile() {
        val root = Files.createTempDirectory("appforge-v4-existing").toFile()
        try {
            val target = root.resolve("src/main.txt")
            target.parentFile.mkdirs()
            target.writeText("before")

            val result = AppForgeAgentWorkspaceTransaction.apply(
                root,
                project("src/main.txt", "after")
            )
            assertEquals("after", target.readText())

            AppForgeAgentWorkspaceTransaction.rollback(result.checkpoint)
            assertEquals("before", target.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun traversalIsRejected() {
        val root = Files.createTempDirectory("appforge-v4-traversal").toFile()
        try {
            AppForgeAgentWorkspaceTransaction.apply(
                root,
                project("../outside.txt", "bad")
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun protectedPathsAreRejected() {
        val root = Files.createTempDirectory("appforge-v4-protected").toFile()
        try {
            val git = runCatching {
                AppForgeAgentWorkspaceTransaction.apply(root, project(".git/config", "bad"))
            }
            val env = runCatching {
                AppForgeAgentWorkspaceTransaction.apply(root, project("config/.env", "bad"))
            }
            assertTrue(git.isFailure)
            assertTrue(env.isFailure)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun project(path: String, content: String) = AppForgeGeneratedProject(
        platform = AppForgeAgentPlatform.WEB,
        entryPoint = path,
        files = listOf(AppForgeGeneratedFile(path, content)),
        digestSha256 = "test-digest"
    )
}
