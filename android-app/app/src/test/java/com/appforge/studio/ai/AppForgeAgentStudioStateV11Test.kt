package com.appforge.studio.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentStudioStateV11Test {
    @Test
    fun busyStateBlocksDuplicateBlueprintGeneration() {
        val state = AppForgeAgentStudioState(
            prompt = "Bir görev uygulaması oluştur",
            busy = true
        )

        assertFalse(state.canGenerate)
    }

    @Test
    fun busyStateBlocksDesignerAndBuildTransitions() {
        val blueprint = validBlueprint()
        val state = AppForgeAgentStudioState(
            step = AppForgeAgentStudioStep.BLUEPRINT_REVIEW,
            prompt = "Bir görev uygulaması oluştur",
            blueprint = blueprint,
            validation = AppForgeAgentBlueprintValidator.validate(blueprint),
            busy = true
        )

        assertFalse(state.canDesign)
        assertFalse(state.canBuild)
    }

    @Test
    fun validIdleStateAllowsBlueprintGeneration() {
        val state = AppForgeAgentStudioState(
            prompt = "Bir görev uygulaması oluştur"
        )

        assertTrue(state.canGenerate)
    }

    private fun validBlueprint() = AppForgeAgentBlueprint(
        appName = "TaskFlow",
        prompt = "Görev uygulaması",
        platform = AppForgeAgentPlatform.ANDROID,
        startRoute = "/home",
        screens = listOf(
            AppForgeAgentScreenSpec(
                id = "home",
                title = "Ana Sayfa",
                route = "/home",
                purpose = "Görevleri gösterir."
            )
        )
    )
}
