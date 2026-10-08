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
            assertTrue(snapshot.files.any { it.path == "src/App.tsx" })
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

    @Test
    fun htmlEntryPointIsIncludedWithoutLeakingSecretLikeLines() {
        val root = createTempDirectory("appforge-ai-html-context-").toFile()

        try {
            File(root, "index.html")
                .writeText(
                    """
                    <!doctype html>
                    <html>
                    <head>
                      <title>Samuray Pro</title>
                      <link rel="stylesheet" href="style.css">
                    </head>
                    <body>
                      <main id="app">Samuray Arena</main>
                      <script>
                        const apiKey = "must_not_leave_device"
                      </script>
                      <script src="app.js"></script>
                    </body>
                    </html>
                    """.trimIndent()
                )

            File(root, "style.css")
                .writeText("body { margin: 0; }")

            File(root, "app.js")
                .writeText("document.body.dataset.ready = 'true'")

            val snapshot = AiProjectContextCollector.collect(
                projectId = "samuray-project",
                draft = ProjectDraft(
                    appName = "Samuray Pro",
                    packageName = "com.appforgestudio.samuraypro",
                    importedFolder = root.absolutePath,
                    sourceTechnologyLabel = "HTML / CSS / JavaScript",
                    sourceBuildEngine = "webview-static"
                )
            )

            assertTrue(
                snapshot.files.any {
                    it.path == "index.html"
                }
            )

            val html = snapshot.files
                .first { it.path == "index.html" }
                .content

            assertTrue(html.contains("Samuray Pro"))
            assertTrue(html.contains("Samuray Arena"))
            assertTrue(
                html.contains("[REDACTED_SECRET-LIKE LINE]")
            )
            assertFalse(
                html.contains("must_not_leave_device")
            )
        } finally {
            root.deleteRecursively()
        }
    }

}
