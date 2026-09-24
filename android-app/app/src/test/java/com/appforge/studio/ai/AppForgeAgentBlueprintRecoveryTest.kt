package com.appforge.studio.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentBlueprintRecoveryTest {
    private val game = "Neon renkli dokunmatik araba yarış oyunu yap. Skor tut."

    private fun validJson(platform: AppForgeAgentPlatform = AppForgeAgentPlatform.WEB): String =
        AppForgeAgentBlueprintJson.encode(
            AppForgeAgentBlueprint(
                appName = "Neon Game",
                prompt = "Modelden gelen özet",
                platform = platform,
                startRoute = "/home",
                screens = listOf(
                    AppForgeAgentScreenSpec(
                        id = "home",
                        title = "Ana ekran",
                        route = "/home",
                        purpose = "Oynanabilir oyun"
                    )
                )
            )
        )

    @Test fun validLocalResponseDoesNotRetry() = runBlocking {
        var calls = 0
        val result = AppForgeAgentBlueprintRecovery.generate(game, AppForgeAgentPlatform.WEB, {
            calls++
            validJson()
        })
        assertEquals(1, calls)
        assertEquals(AppForgeAgentBlueprintOrigin.LOCAL_AI, result.origin)
        assertEquals(game, result.blueprint.prompt)
    }

    @Test fun missingScreensRetriesExactlyOnce() = runBlocking {
        var calls = 0
        var retryCallback = 0
        val result = AppForgeAgentBlueprintRecovery.generate(game, AppForgeAgentPlatform.WEB, { prompt ->
            calls++
            if (calls == 1) """{"schemaVersion":1,"appName":"Neon Game","prompt":"x","platform":"WEB","startRoute":"/home"}"""
            else {
                assertTrue("screens MUTLAKA dizi olmalı" in prompt)
                validJson()
            }
        }, { retryCallback++ })
        assertEquals(2, calls)
        assertEquals(1, retryCallback)
        assertEquals(AppForgeAgentBlueprintOrigin.LOCAL_AI_RETRY, result.origin)
        assertEquals(game, result.blueprint.prompt)
    }

    @Test fun secondInvalidResponseGetsOnlyLabeledWebGameTemplate() = runBlocking {
        var calls = 0
        val result = AppForgeAgentBlueprintRecovery.generate(game, AppForgeAgentPlatform.WEB, {
            calls++
            """{"schemaVersion":1,"appName":"x","prompt":"x","platform":"WEB","startRoute":"/home"}"""
        })
        assertEquals(2, calls)
        assertEquals(AppForgeAgentBlueprintOrigin.VERIFIED_WEB_GAME_TEMPLATE, result.origin)
        assertTrue(result.notice.contains("hazır şablon"))
        assertTrue(AppForgeAgentBlueprintValidator.validate(result.blueprint).valid)
        val rendered = AppForgeAgentWebRenderer.render(result.blueprint)
        assertTrue(rendered.files.any { it.path == "web/app.js" && "requestAnimationFrame" in it.content })
    }

    @Test fun ordinaryAppPromptNeverGetsGameTemplate() = runBlocking {
        var calls = 0
        val result = runCatching {
            AppForgeAgentBlueprintRecovery.generate(
                "Stok takip uygulaması oluştur.", AppForgeAgentPlatform.WEB, {
                    calls++
                    """{"schemaVersion":1,"appName":"x","prompt":"x","platform":"WEB","startRoute":"/home"}"""
                }
            )
        }
        assertTrue(result.isFailure)
        assertEquals(2, calls)
    }

    @Test fun androidGamePromptNeverGetsWebTemplate() = runBlocking {
        val result = runCatching {
            AppForgeAgentBlueprintRecovery.generate(game, AppForgeAgentPlatform.ANDROID, {
                """{"schemaVersion":1,"appName":"x","prompt":"x","platform":"ANDROID","startRoute":"/home"}"""
            })
        }
        assertTrue(result.isFailure)
    }

    @Test fun modelFailureNeverTriggersTemplate() = runBlocking {
        var calls = 0
        val result = runCatching {
            AppForgeAgentBlueprintRecovery.generate(game, AppForgeAgentPlatform.WEB, {
                calls++
                error("Yerel model başlatılmadı")
            })
        }
        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test fun platformMismatchCannotFallBackToTemplate() = runBlocking {
        var calls = 0
        val result = runCatching {
            AppForgeAgentBlueprintRecovery.generate(game, AppForgeAgentPlatform.WEB, {
                calls++
                validJson(AppForgeAgentPlatform.ANDROID)
            })
        }
        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }
}
