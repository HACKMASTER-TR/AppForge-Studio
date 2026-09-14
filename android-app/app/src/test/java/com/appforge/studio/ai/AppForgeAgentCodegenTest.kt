package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentCodegenTest {
    private fun blueprint() = AppForgeAgentBlueprint(
        appName = "Stok Cep",
        prompt = "Stok giriş çıkışlarını takip et",
        platform = AppForgeAgentPlatform.ANDROID,
        tokens = AppForgeAgentDesignTokens(
            primary = "#6750A4",
            secondary = "#625B71",
            background = "#FFFBFE",
            surface = "#FFFBFE",
            text = "#1D1B20",
            spacingUnitDp = 8,
            cornerRadiusDp = 16
        ),
        startRoute = "/home",
        screens = listOf(
            AppForgeAgentScreenSpec(
                id = "home",
                title = "Ana Sayfa",
                route = "/home",
                purpose = "Stok özetini göster",
                components = listOf("StockSummary"),
                actions = listOf(
                    AppForgeAgentActionSpec(
                        id = "openDetail",
                        label = "Detay",
                        targetRoute = "/detail"
                    )
                )
            ),
            AppForgeAgentScreenSpec(
                id = "detail",
                title = "Detay",
                route = "/detail",
                purpose = "Ürün detayını göster"
            )
        )
    )

    @Test
    fun generatesDeterministicAndroidSource() {
        val first = AppForgeAgentCodegen.generate(blueprint())
        val second = AppForgeAgentCodegen.generate(blueprint())

        assertEquals(AppForgeAgentPlatform.ANDROID, first.platform)
        assertEquals(first.digestSha256, second.digestSha256)
        assertTrue(first.files.any { it.path.endsWith("MainActivity.kt") })
        assertTrue(first.files.any { it.content.contains("/detail") })
        assertTrue(first.files.any { it.content.contains("0xFF6750A4L") })
    }

    @Test
    fun generatesAllSupportedPlatformsFromSameDesignContract() {
        val projects = AppForgeAgentCodegen.generateAll(blueprint())

        assertEquals(AppForgeAgentPlatform.entries.size, projects.size)
        assertTrue(projects.containsKey(AppForgeAgentPlatform.ANDROID))
        assertTrue(projects.containsKey(AppForgeAgentPlatform.FLUTTER))
        assertTrue(projects.containsKey(AppForgeAgentPlatform.REACT_NATIVE))
        assertTrue(projects.containsKey(AppForgeAgentPlatform.WEB))
        projects.values.forEach { project ->
            assertTrue(project.files.isNotEmpty())
            assertTrue(project.files.any { it.path == project.entryPoint })
            assertEquals(64, project.digestSha256.length)
        }
    }

    @Test
    fun platformOutputsHaveIndependentDigests() {
        val projects = AppForgeAgentCodegen.generateAll(blueprint())
        val digests = projects.values.map { it.digestSha256 }.toSet()
        assertEquals(4, digests.size)
    }

    @Test
    fun generatedPathsAreRelativeAndTraversalFree() {
        val projects = AppForgeAgentCodegen.generateAll(blueprint())
        projects.values.flatMap { it.files }.forEach { file ->
            assertFalse(file.path.startsWith('/'))
            assertFalse(file.path.contains(".."))
            assertFalse(file.path.contains('\\'))
        }
    }

    @Test
    fun rendererEscapesUserVisibleSourceText() {
        val special = blueprint().copy(
            appName = "Stok \"Cep\"",
            screens = blueprint().screens.mapIndexed { index, screen ->
                if (index == 0) screen.copy(title = "Ana <Sayfa> \"Özel\"") else screen
            }
        )
        val projects = AppForgeAgentCodegen.generateAll(special)
        assertEquals(4, projects.size)
        assertTrue(projects.values.all { it.files.all { file -> file.content.isNotBlank() } })
    }

    @Test
    fun designChangeChangesDigest() {
        val first = AppForgeAgentCodegen.generateFor(
            blueprint(),
            AppForgeAgentPlatform.WEB
        )
        val second = AppForgeAgentCodegen.generateFor(
            blueprint().copy(
                tokens = blueprint().tokens.copy(primary = "#0057B8")
            ),
            AppForgeAgentPlatform.WEB
        )
        assertNotEquals(first.digestSha256, second.digestSha256)
    }
}
