package com.appforge.studio.terminal

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateCodeEditorCoreTest {
    @Test
    fun blocksWorkspaceEscapeAndBinarySecrets() {
        withWorkspace { root ->
            File(root, "main.kt")
                .writeText("fun main() = Unit\n")

            assertEquals(
                "fun main() = Unit\n",
                UltimateEditorWorkspace.readText(
                    root,
                    "main.kt"
                )
            )

            val escaped =
                runCatching {
                    UltimateEditorWorkspace.readText(
                        root,
                        "../outside.txt"
                    )
                }

            assertTrue(escaped.isFailure)

            File(root, "signing.jks")
                .writeBytes(byteArrayOf(1, 2, 3))

            assertFalse(
                UltimateEditorWorkspace
                    .listFiles(root)
                    .any {
                        it.relativePath ==
                            "signing.jks"
                    }
            )
        }
    }

    @Test
    fun saveCreatesRestorePointExceptForEnvSecrets() {
        withWorkspace { root ->
            File(root, "app.js")
                .writeText("const value = 1;\n")

            val result =
                UltimateEditorWorkspace.saveText(
                    root,
                    "app.js",
                    "const value = 2;\n"
                )

            assertNotNull(result.restorePoint)
            assertEquals(
                "const value = 2;\n",
                File(root, "app.js").readText()
            )

            File(root, ".env")
                .writeText("TOKEN=old\n")

            val envResult =
                UltimateEditorWorkspace.saveText(
                    root,
                    ".env",
                    "TOKEN=new\n"
                )

            assertNull(envResult.restorePoint)
        }
    }

    @Test
    fun searchReplaceDiffAndUndoWorkTogether() {
        val before =
            "alpha\nbeta\ngamma"

        val matches =
            UltimateEditorSearch.find(
                before,
                "BETA"
            )

        assertEquals(1, matches.size)
        assertEquals(2, matches.first().line)

        val replaced =
            UltimateEditorSearch.replaceAll(
                before,
                "beta",
                "delta"
            )

        assertTrue(
            UltimateEditorDiff.compare(
                before,
                replaced
            ).any {
                it.kind == EditorDiffKind.ADDED &&
                    it.text == "delta"
            }
        )

        val history =
            EditorUndoBuffer(before)

        history.edit(replaced)
        assertEquals(before, history.undo())
        assertEquals(replaced, history.redo())
    }

    @Test
    fun lspCatalogOnlyPreparesKnownServerCommands() {
        val profile =
            UltimateLspCatalog.forPath(
                "src/App.tsx"
            )

        assertNotNull(profile)
        assertEquals(
            "typescript-language-server --stdio",
            profile?.serverCommand
        )

        assertNull(
            UltimateLspCatalog.forPath(
                "assets/logo.png"
            )
        )
    }

    private fun withWorkspace(
        block: (File) -> Unit
    ) {
        val root =
            Files
                .createTempDirectory(
                    "appforge-editor"
                )
                .toFile()

        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
