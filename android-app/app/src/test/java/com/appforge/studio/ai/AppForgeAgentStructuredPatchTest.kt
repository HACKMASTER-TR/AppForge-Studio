package com.appforge.studio.ai

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentStructuredPatchTest {
    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversal() {
        AppForgeAgentStructuredPatch.validate(
            AppForgeAgentPatchPlan(
                failureFingerprint = "a".repeat(64),
                operations = listOf(
                    AppForgeAgentPatchOperation(
                        path = "../secret.txt",
                        baseSha256 = null,
                        replacementContent = "blocked"
                    )
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCredentialFiles() {
        AppForgeAgentStructuredPatch.validate(
            AppForgeAgentPatchPlan(
                failureFingerprint = "a".repeat(64),
                operations = listOf(
                    AppForgeAgentPatchOperation(
                        path = ".env",
                        baseSha256 = null,
                        replacementContent = "TOKEN=x"
                    )
                )
            )
        )
    }

    @Test
    fun applyAndRollbackRestoresBaseFile() {
        val workspace = Files.createTempDirectory("appforge-v5-patch").toFile()
        try {
            val target = File(workspace, "src/Main.kt")
            target.parentFile.mkdirs()
            target.writeText("old\n")
            val base = sha256(target.readBytes())

            val result = AppForgeAgentStructuredPatch.apply(
                workspace,
                AppForgeAgentPatchPlan(
                    failureFingerprint = "b".repeat(64),
                    operations = listOf(
                        AppForgeAgentPatchOperation(
                            path = "src/Main.kt",
                            baseSha256 = base,
                            replacementContent = "new"
                        )
                    )
                )
            )

            assertEquals("new\n", target.readText())
            AppForgeAgentWorkspaceTransaction.rollback(result.checkpoint)
            assertEquals("old\n", target.readText())
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun createsNewFileAndRollbackRemovesIt() {
        val workspace = Files.createTempDirectory("appforge-v5-new").toFile()
        try {
            val target = File(workspace, "src/New.kt")
            val result = AppForgeAgentStructuredPatch.apply(
                workspace,
                AppForgeAgentPatchPlan(
                    failureFingerprint = "c".repeat(64),
                    operations = listOf(
                        AppForgeAgentPatchOperation(
                            path = "src/New.kt",
                            baseSha256 = null,
                            replacementContent = "created"
                        )
                    )
                )
            )

            assertTrue(target.isFile)
            AppForgeAgentWorkspaceTransaction.rollback(result.checkpoint)
            assertFalse(target.exists())
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun staleBaseHashIsRejected() {
        val workspace = Files.createTempDirectory("appforge-v5-stale").toFile()
        try {
            File(workspace, "a.txt").writeText("current")
            AppForgeAgentStructuredPatch.apply(
                workspace,
                AppForgeAgentPatchPlan(
                    failureFingerprint = "d".repeat(64),
                    operations = listOf(
                        AppForgeAgentPatchOperation(
                            path = "a.txt",
                            baseSha256 = "0".repeat(64),
                            replacementContent = "bad overwrite"
                        )
                    )
                )
            )
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
