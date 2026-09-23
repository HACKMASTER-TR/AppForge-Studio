package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentPromptToProductV1Test {
    @Test
    fun racingPromptBecomesPlayableRacingGame() {
        val blueprint = blueprint(
            prompt = "İki kişilik motosiklet yarış oyunu yap, puan ve engeller olsun."
        )

        val profile =
            AppForgeAgentPromptProductClassifier.classify(
                blueprint
            )

        assertEquals(
            AppForgeAgentProductKind.GAME,
            profile.kind
        )
        assertEquals(
            AppForgeAgentGameMode.RACING,
            profile.gameMode
        )

        val rendered =
            AppForgeAgentWebRenderer.render(
                blueprint
            )

        val html = rendered.files
            .first { it.path == "web/index.html" }
            .content
        val js = rendered.files
            .first { it.path == "web/app.js" }
            .content

        assertTrue("<canvas" in html)
        assertTrue("const mode = \"racing\"" in js)
        assertTrue("requestAnimationFrame" in js)
        assertTrue("pointerdown" in js)
    }

    @Test
    fun normalUtilityPromptStaysApplication() {
        val blueprint = blueprint(
            prompt = "Stok kayıtlarını ve teslim durumunu gösteren iş uygulaması yap."
        )

        val profile =
            AppForgeAgentPromptProductClassifier.classify(
                blueprint
            )

        assertEquals(
            AppForgeAgentProductKind.APPLICATION,
            profile.kind
        )

        val rendered =
            AppForgeAgentWebRenderer.render(
                blueprint
            )

        val html = rendered.files
            .first { it.path == "web/index.html" }
            .content

        assertTrue("<main id=\"app\"" in html)
    }

    private fun blueprint(
        prompt: String
    ) = AppForgeAgentBlueprint(
        appName = "PromptProduct",
        prompt = prompt,
        platform = AppForgeAgentPlatform.WEB,
        startRoute = "/home",
        screens = listOf(
            AppForgeAgentScreenSpec(
                id = "home",
                title = "Ana ekran",
                route = "/home",
                purpose = prompt,
                components = listOf("Ana içerik")
            )
        )
    )
}
