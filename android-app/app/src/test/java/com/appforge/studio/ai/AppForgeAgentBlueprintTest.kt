package com.appforge.studio.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentBlueprintTest {
    private fun validBlueprint() =
        AppForgeAgentBlueprint(
            appName = "TaskFlow",
            prompt =
                "Görev oluşturma, listeleme ve profil ekranı olan sade bir uygulama.",
            platform = AppForgeAgentPlatform.ANDROID,
            startRoute = "/home",
            screens =
                listOf(
                    AppForgeAgentScreenSpec(
                        id = "home",
                        title = "Ana Sayfa",
                        route = "/home",
                        purpose = "Görevleri listeler.",
                        components =
                            listOf(
                                "TopAppBar",
                                "TaskList",
                                "FloatingActionButton"
                            ),
                        actions =
                            listOf(
                                AppForgeAgentActionSpec(
                                    id = "createTask",
                                    label = "Görev ekle",
                                    targetRoute = "/create"
                                )
                            )
                    ),
                    AppForgeAgentScreenSpec(
                        id = "create",
                        title = "Görev Oluştur",
                        route = "/create",
                        purpose = "Yeni görev bilgilerini toplar.",
                        components = listOf("TextField", "SaveButton"),
                        actions =
                            listOf(
                                AppForgeAgentActionSpec(
                                    id = "save",
                                    label = "Kaydet",
                                    targetRoute = "/home"
                                )
                            )
                    )
                )
        )

    @Test
    fun validBlueprintProducesConsistentPacket() {
        val blueprint = validBlueprint()
        val validation =
            AppForgeAgentBlueprintValidator.validate(blueprint)
        val packet =
            AppForgeAgentBlueprintValidator.consistencyPacket(blueprint)

        assertTrue(validation.valid)
        assertTrue(packet.contains("APPFORGE AGENT DESIGN CONTRACT V1"))
        assertTrue(packet.contains("/home"))
        assertTrue(packet.contains("spacingUnitDp=8"))
        assertTrue(packet.length <= 16 * 1024)
    }

    @Test
    fun duplicateRoutesAreRejected() {
        val original = validBlueprint()
        val broken =
            original.copy(
                screens =
                    original.screens +
                        AppForgeAgentScreenSpec(
                            id = "other",
                            title = "Diğer",
                            route = "/home",
                            purpose = "Çakışan route."
                        )
            )

        val validation =
            AppForgeAgentBlueprintValidator.validate(broken)

        assertFalse(validation.valid)
        assertTrue(
            validation.issues.any {
                it.field.contains("route") &&
                    it.level == AgentBlueprintIssueLevel.ERROR
            }
        )
    }

    @Test
    fun missingActionTargetIsRejected() {
        val original = validBlueprint()
        val broken =
            original.copy(
                screens =
                    original.screens.mapIndexed { index, screen ->
                        if (index == 0) {
                            screen.copy(
                                actions =
                                    listOf(
                                        AppForgeAgentActionSpec(
                                            id = "missing",
                                            label = "Eksik",
                                            targetRoute = "/missing"
                                        )
                                    )
                            )
                        } else {
                            screen
                        }
                    }
            )

        val validation =
            AppForgeAgentBlueprintValidator.validate(broken)

        assertFalse(validation.valid)
        assertTrue(validation.issues.any { it.field.contains("targetRoute") })
    }

    @Test
    fun unsafeTokenValuesAreRejected() {
        val original = validBlueprint()
        val broken =
            original.copy(
                tokens =
                    original.tokens.copy(
                        primary = "purple",
                        spacingUnitDp = 100
                    )
            )

        assertFalse(AppForgeAgentBlueprintValidator.validate(broken).valid)
    }
}
