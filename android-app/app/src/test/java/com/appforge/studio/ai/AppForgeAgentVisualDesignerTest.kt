package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentVisualDesignerTest {
    private fun blueprint() = AppForgeAgentBlueprint(
        appName = "DesignerDemo",
        prompt = "Designer test uygulaması",
        platform = AppForgeAgentPlatform.ANDROID,
        startRoute = "/home",
        screens = listOf(
            AppForgeAgentScreenSpec(
                id = "home",
                title = "Home",
                route = "/home",
                purpose = "Ana ekran",
                components = listOf("TaskList"),
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
                title = "Detail",
                route = "/detail",
                purpose = "Detay ekranı",
                components = listOf("Text", "SaveButton")
            )
        )
    )

    @Test
    fun routeRenameIsAtomicAcrossReferences() {
        var history = AppForgeAgentVisualDesigner.create(blueprint())
        history = AppForgeAgentVisualDesigner.renameRoute(history, "detail", "/task")
        val updated = history.current.blueprint
        assertTrue(updated.screens.any { it.id == "detail" && it.route == "/task" })
        assertEquals(
            "/task",
            updated.screens.first { it.id == "home" }.actions.first().targetRoute
        )
        assertTrue(AppForgeAgentBlueprintValidator.validate(updated).valid)
    }

    @Test
    fun tokenEditCanBeUndoneAndRedone() {
        val initial = AppForgeAgentVisualDesigner.create(blueprint())
        val changed = AppForgeAgentVisualDesigner.updateTokens(
            initial,
            initial.current.blueprint.tokens.copy(primary = "#112233")
        )
        assertEquals("#112233", changed.current.blueprint.tokens.primary)
        val undone = AppForgeAgentVisualDesigner.undo(changed)
        assertEquals("#6750A4", undone.current.blueprint.tokens.primary)
        val redone = AppForgeAgentVisualDesigner.redo(undone)
        assertEquals("#112233", redone.current.blueprint.tokens.primary)
    }

    @Test
    fun previewMapsComponentsAndActions() {
        var history = AppForgeAgentVisualDesigner.create(blueprint())
        history = AppForgeAgentVisualDesigner.selectScreen(history, "home")
        val preview = AppForgeAgentVisualDesigner.preview(history)
        assertEquals("home", preview.screenId)
        assertTrue(preview.nodes.any { it.kind == "COLLECTION" })
        assertTrue(preview.nodes.any { it.kind == "ACTION" })
    }

    @Test
    fun cannotDeleteReferencedScreen() {
        val history = AppForgeAgentVisualDesigner.create(blueprint())
        val result = runCatching {
            AppForgeAgentVisualDesigner.removeScreen(history, "detail")
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun safeComponentMutationPreservesBlueprintValidity() {
        val initial = AppForgeAgentVisualDesigner.create(blueprint())
        val changed = AppForgeAgentVisualDesigner.addComponent(initial, "detail", "InfoCard")
        assertTrue(
            changed.current.blueprint.screens
                .first { it.id == "detail" }
                .components
                .contains("InfoCard")
        )
        assertTrue(AppForgeAgentBlueprintValidator.validate(changed.current.blueprint).valid)
    }

    @Test
    fun invalidComponentIsRejected() {
        val initial = AppForgeAgentVisualDesigner.create(blueprint())
        val result = runCatching {
            AppForgeAgentVisualDesigner.addComponent(initial, "detail", "../../secret")
        }
        assertTrue(result.isFailure)
        assertFalse(initial.current.blueprint.screens.first { it.id == "detail" }.components.contains("../../secret"))
    }
}
