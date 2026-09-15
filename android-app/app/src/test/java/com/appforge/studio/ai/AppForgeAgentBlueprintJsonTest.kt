package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppForgeAgentBlueprintJsonTest {
    private fun validJson(): String = """
        {
          "schemaVersion": 1,
          "appName": "Stok Cep",
          "prompt": "Stok giriş çıkışlarını takip et",
          "platform": "ANDROID",
          "tokens": {
            "primary": "#6750A4",
            "secondary": "#625B71",
            "background": "#FFFBFE",
            "surface": "#FFFBFE",
            "text": "#1D1B20",
            "spacingUnitDp": 8,
            "cornerRadiusDp": 16
          },
          "startRoute": "/home",
          "screens": [
            {
              "id": "home",
              "title": "Ana Sayfa",
              "route": "/home",
              "purpose": "Stok özetini göster",
              "components": ["StockSummary", "PrimaryButton"],
              "actions": [
                {"id":"openDetail","label":"Detay","targetRoute":"/detail"}
              ]
            },
            {
              "id": "detail",
              "title": "Detay",
              "route": "/detail",
              "purpose": "Ürün detayını göster",
              "components": [],
              "actions": []
            }
          ],
          "maxRepairAttempts": 2
        }
    """.trimIndent()

    @Test
    fun parsesValidatedBlueprint() {
        val blueprint = AppForgeAgentBlueprintJson.parse(validJson())

        assertEquals("Stok Cep", blueprint.appName)
        assertEquals(AppForgeAgentPlatform.ANDROID, blueprint.platform)
        assertEquals("/home", blueprint.startRoute)
        assertEquals(2, blueprint.screens.size)
        assertEquals("/detail", blueprint.screens.first().actions.first().targetRoute)
    }

    @Test
    fun acceptsFinalAnswerAndJsonFenceOnly() {
        val wrapped = "<final_answer>```json\n${validJson()}\n```</final_answer>"
        val blueprint = AppForgeAgentBlueprintJson.parse(wrapped)
        assertEquals("Stok Cep", blueprint.appName)
    }

    @Test
    fun roundTripIsStableAndValidated() {
        val first = AppForgeAgentBlueprintJson.parse(validJson())
        val encoded = AppForgeAgentBlueprintJson.encode(first)
        val second = AppForgeAgentBlueprintJson.parse(encoded)
        assertEquals(first, second)
    }

    @Test
    fun rejectsUnknownFields() {
        val invalid = validJson().replace(
            "\"maxRepairAttempts\": 2",
            "\"maxRepairAttempts\": 2, \"shellCommand\": \"rm -rf /\""
        )
        expectFailure { AppForgeAgentBlueprintJson.parse(invalid) }
    }

    @Test
    fun rejectsUndefinedTargetRoute() {
        val invalid = validJson().replace("/detail\"}", "/missing\"}")
        expectFailure { AppForgeAgentBlueprintJson.parse(invalid) }
    }

    @Test
    fun rejectsProseAroundJson() {
        expectFailure {
            AppForgeAgentBlueprintJson.parse("İşte JSON:\n${validJson()}")
        }
    }

    @Test
    fun fillsMissingPlatformFromSelectedFallback() {
        val withoutPlatform =
            validJson().replace(
                Regex(
                    """(?m)^\s*"platform":\s*"ANDROID",\s*\n"""
                ),
                ""
            )

        val blueprint =
            AppForgeAgentBlueprintJson.parse(
                raw = withoutPlatform,
                fallbackPlatform =
                    AppForgeAgentPlatform.FLUTTER
            )

        assertEquals(
            AppForgeAgentPlatform.FLUTTER,
            blueprint.platform
        )
    }

    @Test
    fun stillRejectsMissingPlatformWithoutFallback() {
        val withoutPlatform =
            validJson().replace(
                Regex(
                    """(?m)^\s*"platform":\s*"ANDROID",\s*\n"""
                ),
                ""
            )

        expectFailure {
            AppForgeAgentBlueprintJson.parse(
                withoutPlatform
            )
        }
    }

    @Test
    fun fallbackDoesNotOverrideExplicitAiPlatform() {
        val blueprint =
            AppForgeAgentBlueprintJson.parse(
                raw = validJson(),
                fallbackPlatform =
                    AppForgeAgentPlatform.WEB
            )

        assertEquals(
            AppForgeAgentPlatform.ANDROID,
            blueprint.platform
        )
    }

    @Test
    fun normalizesRawControlCharactersInsideJsonStringsOnly() {
        val raw = validJson()
            .replace(
                "\"prompt\": \"Stok giriş çıkışlarını takip et\"",
                "\"prompt\": \"Satır 1\nSatır 2\tSekmeli\""
            )

        val blueprint = AppForgeAgentBlueprintJson.parse(raw)

        assertEquals(
            "Satır 1\nSatır 2\tSekmeli",
            blueprint.prompt
        )
    }

    @Test
    fun doesNotRepairStructuralControlCharactersOutsideStrings() {
        val invalid =
            validJson().replaceFirst(
                "{",
                "{\u0000"
            )

        expectFailure {
            AppForgeAgentBlueprintJson.parse(invalid)
        }
    }

    @Test
    fun keepsAlreadyEscapedJsonStringsStable() {
        val raw = validJson()
            .replace(
                "\"prompt\": \"Stok giriş çıkışlarını takip et\"",
                "\"prompt\": \"Satır 1\\\\nSatır 2\\\\tSekmeli\""
            )

        val normalized =
            AppForgeAgentBlueprintJson
                .normalizeRawStringControlCharacters(raw)

        assertEquals(raw, normalized)
    }

    @Test
    fun promptContractForcesStrictSchema() {
        val prompt = AppForgeAgentBlueprintPrompt.build(
            userPrompt = "Bana görev takip uygulaması yap",
            preferredPlatform = AppForgeAgentPlatform.ANDROID
        )

        assertTrue(prompt.contains("schemaVersion=1"))
        assertTrue(prompt.contains("platform=ANDROID"))
        assertTrue(prompt.contains("Bilinmeyen alan ekleme"))
        assertTrue(prompt.contains("kontrol karakteri kullanma"))
        assertTrue(prompt.contains("\\n, \\t, \\r"))
        assertTrue(prompt.contains("<user_request>"))
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Exception bekleniyordu.")
        } catch (_: AppForgeAgentBlueprintJsonException) {
            // Expected.
        }
    }
}
