package com.appforge.studio.ai

import com.appforge.studio.io.SourceCapabilityAnalyzer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentBuildProjectPreparerTest {
    @Test
    fun androidGeneratedSourceBecomesBuildReadyGradleProject() {
        val blueprint = blueprint(AppForgeAgentPlatform.ANDROID, "Görev Cep")
        withPrepared(blueprint) { prepared ->
            val analysis =
                SourceCapabilityAnalyzer.analyze(prepared.projectRoot)

            assertEquals("android-gradle", analysis.buildEngine)
            assertTrue(analysis.buildReady)
            assertTrue(
                File(
                    prepared.projectRoot,
                    "settings.gradle.kts"
                ).isFile
            )
            assertTrue(
                File(
                    prepared.projectRoot,
                    "app/build.gradle.kts"
                ).isFile
            )
            assertTrue(prepared.zipFile.length() > 0L)
            assertEquals(
                "com.appforge.generated.g_rev_cep",
                prepared.packageName
            )
        }
    }

    @Test
    fun webGeneratedSourceUsesStaticWebEngine() {
        val blueprint = blueprint(AppForgeAgentPlatform.WEB, "Web Cep")
        withPrepared(blueprint) { prepared ->
            val analysis =
                SourceCapabilityAnalyzer.analyze(prepared.projectRoot)

            assertEquals("web-static", analysis.technologyId)
            assertEquals("webview-static", analysis.buildEngine)
            assertTrue(analysis.buildReady)
        }
    }

    @Test
    fun flutterGeneratedSourceGetsBuildShell() {
        val blueprint = blueprint(AppForgeAgentPlatform.FLUTTER, "Flutter Cep")
        withPrepared(blueprint) { prepared ->
            val analysis =
                SourceCapabilityAnalyzer.analyze(prepared.projectRoot)

            assertEquals("flutter", analysis.buildEngine)
            assertTrue(analysis.buildReady)
            assertTrue(
                File(
                    prepared.projectRoot,
                    "android/app/build.gradle.kts"
                ).isFile
            )
            assertTrue(
                File(
                    prepared.projectRoot,
                    "android/app/src/main/AndroidManifest.xml"
                ).isFile
            )
        }
    }

    @Test
    fun reactNativeGeneratedSourceUsesExpoAndroidEngine() {
        val blueprint =
            blueprint(AppForgeAgentPlatform.REACT_NATIVE, "React Cep")

        withPrepared(blueprint) { prepared ->
            val analysis =
                SourceCapabilityAnalyzer.analyze(prepared.projectRoot)

            assertEquals("expo", analysis.technologyId)
            assertEquals("expo-android", analysis.buildEngine)
            assertTrue(analysis.buildReady)
            assertTrue(
                File(prepared.projectRoot, "app.json").isFile
            )
        }
    }

    @Test
    fun androidRendererUsesFullyQualifiedActivityClass() {
        val blueprint = blueprint(
            AppForgeAgentPlatform.ANDROID,
            "Stok Cep"
        )

        val generated =
            AppForgeAgentCodegen.generate(blueprint)

        val manifest = generated.files
            .first {
                it.path.endsWith("AndroidManifest.xml")
            }
            .content

        assertTrue(
            manifest.contains(
                "android:name=\"com.appforge.generated.stok_cep.MainActivity\""
            )
        )
        assertFalse(
            manifest.contains(
                "android:name=\".com.appforge.generated"
            )
        )
    }

    private fun withPrepared(
        blueprint: AppForgeAgentBlueprint,
        block: (AppForgeAgentPreparedBuildProject) -> Unit
    ) {
        val root =
            kotlin.io.path.createTempDirectory(
                "appforge-v11-stage4-"
            ).toFile()

        val workspace =
            File(root, "workspace").apply {
                mkdirs()
            }

        val generated =
            AppForgeAgentCodegen.generate(blueprint)

        generated.files.forEach { generatedFile ->
            val file =
                File(workspace, generatedFile.path)

            file.parentFile?.mkdirs()
            file.writeText(generatedFile.content)
        }

        val prepared =
            AppForgeAgentBuildProjectPreparer.prepare(
                cacheRoot = File(root, "cache"),
                workspace = workspace,
                blueprint = blueprint
            )

        try {
            block(prepared)
        } finally {
            prepared.cleanup()
            root.deleteRecursively()
        }
    }

    private fun blueprint(
        platform: AppForgeAgentPlatform,
        appName: String
    ) = AppForgeAgentBlueprint(
        appName = appName,
        prompt = "Görev takip uygulaması",
        platform = platform,
        startRoute = "/home",
        screens = listOf(
            AppForgeAgentScreenSpec(
                id = "home",
                title = "Ana Sayfa",
                route = "/home",
                purpose = "Görevleri gösterir.",
                components = listOf("TaskList")
            )
        )
    )
}
