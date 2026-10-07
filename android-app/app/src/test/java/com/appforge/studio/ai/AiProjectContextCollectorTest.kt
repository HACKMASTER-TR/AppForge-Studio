package com.appforge.studio.ai

import com.appforge.studio.model.ProjectDraft
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProjectContextCollectorTest {
    @Test
    fun contextSkipsGeneratedAndSecretPathsAndRedactsSecretLikeLines() {
        val root = createTempDirectory("appforge-ai-context-").toFile()

        try {
            File(root, "package.json")
                .writeText("""{"scripts":{"build":"vite"}}""")

            File(root, ".env")
                .writeText("API_KEY=private_should_never_leave")

            File(root, "build.gradle.kts")
                .writeText(
                    """
                    val password = "private_should_never_leave"
                    plugins {}
                    """.trimIndent()
                )

            File(root, "node_modules").mkdirs()
            File(root, "node_modules/ignored.txt").writeText("ignored")

            File(root, "src").mkdirs()
            File(root, "src/App.tsx").writeText("export default 1")

            val snapshot = AiProjectContextCollector.collect(
                projectId = "project-1",
                draft = ProjectDraft(
                    appName = "Demo",
                    packageName = "com.example.demo",
                    importedFolder = root.absolutePath,
                    sourceTechnologyLabel = "React / Vite",
                    sourceBuildEngine = "node-web"
                )
            )

            assertFalse(snapshot.tree.contains(".env"))
            assertFalse(snapshot.tree.contains("node_modules"))
            assertTrue(snapshot.files.any { it.path == "package.json" })
            assertFalse(snapshot.files.any { it.path == ".env" })
            assertTrue(
                snapshot.files
                    .first { it.path == "build.gradle.kts" }
                    .content
                    .contains("[REDACTED_SECRET-LIKE LINE]")
            )
            assertFalse(
                snapshot.files.any {
                    it.content.contains("private_should_never_leave")
                }
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
