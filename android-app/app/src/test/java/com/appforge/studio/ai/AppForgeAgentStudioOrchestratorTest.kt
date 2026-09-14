package com.appforge.studio.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentStudioOrchestratorTest {
    @Test
    fun validPromptCreatesReviewableBlueprint() {
        val orchestrator = orchestrator(
            blueprintProvider = AppForgeAgentBlueprintProvider {
                AppForgeAgentBlueprintJson.encode(validBlueprint("TaskFlow"))
            }
        )
        val start = orchestrator.initialState()
        val prompted = orchestrator.updatePrompt(start, "Görev takip uygulaması oluştur")
        val state = orchestrator.generateBlueprint(prompted)

        assertEquals(AppForgeAgentStudioStep.BLUEPRINT_REVIEW, state.step)
        assertEquals("TaskFlow", state.blueprint?.appName)
        assertTrue(state.validation?.valid == true)
        assertTrue(state.canBuild)
    }

    @Test
    fun invalidAiOutputIsBlockedBeforeWorkspaceMutation() {
        val orchestrator = orchestrator(
            blueprintProvider = AppForgeAgentBlueprintProvider { "not-json" }
        )
        val prompted = orchestrator.updatePrompt(
            orchestrator.initialState(),
            "Not uygulaması oluştur"
        )
        val state = orchestrator.generateBlueprint(prompted)

        assertEquals(AppForgeAgentStudioStep.BLOCKED, state.step)
        assertTrue(state.blueprint == null)
    }

    @Test
    fun platformChangeClearsOldBlueprint() {
        val orchestrator = orchestrator()
        val old = validBlueprint("Old")
        val state = AppForgeAgentStudioState(
            step = AppForgeAgentStudioStep.BLUEPRINT_REVIEW,
            prompt = "test",
            platform = AppForgeAgentPlatform.ANDROID,
            blueprint = old,
            validation = AppForgeAgentBlueprintValidator.validate(old)
        )

        val changed = orchestrator.updatePlatform(
            state,
            AppForgeAgentPlatform.WEB
        )

        assertEquals(AppForgeAgentStudioStep.PROMPT, changed.step)
        assertEquals(AppForgeAgentPlatform.WEB, changed.platform)
        assertTrue(changed.blueprint == null)
    }

    @Test
    fun invalidDesignerEditCannotBuild() {
        var stageCalls = 0
        val orchestrator = orchestrator(
            stageRunner = AppForgeAgentStageRunner { _, _ ->
                stageCalls += 1
                AppForgeAgentExecutionResult(true)
            }
        )
        val original = validBlueprint("TaskFlow")
        val state = AppForgeAgentStudioState(
            step = AppForgeAgentStudioStep.DESIGN,
            prompt = "test",
            platform = AppForgeAgentPlatform.ANDROID,
            blueprint = original,
            validation = AppForgeAgentBlueprintValidator.validate(original)
        )
        val invalid = original.copy(startRoute = "/missing")
        val edited = orchestrator.updateBlueprint(state, invalid)

        assertFalse(edited.canBuild)

        val built = orchestrator.build(
            edited,
            AppForgeAgentStudioBuildRequest(tempWorkspace())
        )

        assertEquals(AppForgeAgentStudioStep.DESIGN, built.step)
        assertEquals(0, stageCalls)
    }

    @Test
    fun editedBlueprintIsTheOneSentToAutonomousPipeline() {
        val orchestrator = orchestrator()
        val original = validBlueprint("Original")
        val state = AppForgeAgentStudioState(
            step = AppForgeAgentStudioStep.DESIGN,
            prompt = "Görev takip uygulaması oluştur",
            platform = AppForgeAgentPlatform.ANDROID,
            blueprint = original,
            validation = AppForgeAgentBlueprintValidator.validate(original)
        )
        val edited = orchestrator.updateBlueprint(
            state,
            original.copy(appName = "EditedApp")
        )

        val built = orchestrator.build(
            edited,
            AppForgeAgentStudioBuildRequest(tempWorkspace())
        )

        assertEquals(AppForgeAgentStudioStep.RESULT, built.step)
        assertEquals("EditedApp", built.autonomousResult?.blueprint?.appName)
        assertEquals(
            AppForgeAgentDeployGateV8.REVIEW_REQUIRED,
            built.autonomousResult?.deployGate
        )
    }

    @Test
    fun successfulBuildRunsTestBeforeBuild() {
        val stages = mutableListOf<AppForgeAgentExecutionStage>()
        val orchestrator = orchestrator(
            stageRunner = AppForgeAgentStageRunner { stage, _ ->
                stages += stage
                AppForgeAgentExecutionResult(true)
            }
        )
        val blueprint = validBlueprint("TaskFlow")
        val state = AppForgeAgentStudioState(
            step = AppForgeAgentStudioStep.BLUEPRINT_REVIEW,
            prompt = "Görev takip uygulaması oluştur",
            platform = AppForgeAgentPlatform.ANDROID,
            blueprint = blueprint,
            validation = AppForgeAgentBlueprintValidator.validate(blueprint)
        )

        val built = orchestrator.build(
            state,
            AppForgeAgentStudioBuildRequest(tempWorkspace())
        )

        assertEquals(
            listOf(
                AppForgeAgentExecutionStage.TEST,
                AppForgeAgentExecutionStage.BUILD
            ),
            stages
        )
        assertEquals(AppForgeAgentStudioStep.RESULT, built.step)
    }

    private fun orchestrator(
        blueprintProvider: AppForgeAgentBlueprintProvider =
            AppForgeAgentBlueprintProvider {
                AppForgeAgentBlueprintJson.encode(validBlueprint("TaskFlow"))
            },
        stageRunner: AppForgeAgentStageRunner =
            AppForgeAgentStageRunner { _, _ -> AppForgeAgentExecutionResult(true) }
    ) = AppForgeAgentStudioOrchestrator(
        blueprintProvider = blueprintProvider,
        stageRunner = stageRunner
    )

    private fun validBlueprint(appName: String) = AppForgeAgentBlueprint(
        appName = appName,
        prompt = "Görev takip uygulaması",
        platform = AppForgeAgentPlatform.ANDROID,
        startRoute = "/home",
        screens = listOf(
            AppForgeAgentScreenSpec(
                id = "home",
                title = "Ana Sayfa",
                route = "/home",
                purpose = "Görevleri gösterir.",
                components = listOf("TaskList")
            )
        ),
        maxRepairAttempts = 2
    )

    private fun tempWorkspace(): File =
        kotlin.io.path.createTempDirectory("appforge-v11-").toFile().canonicalFile
}
