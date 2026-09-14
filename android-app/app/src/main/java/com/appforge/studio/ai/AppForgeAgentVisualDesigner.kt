package com.appforge.studio.ai

internal enum class AppForgeAgentPreviewDevice(
    val title: String,
    val widthDp: Int,
    val heightDp: Int
) {
    PHONE("Phone", 390, 844),
    TABLET("Tablet", 800, 1280),
    DESKTOP("Desktop", 1440, 900)
}

internal data class AppForgeAgentPreviewNode(
    val key: String,
    val kind: String,
    val label: String
)

internal data class AppForgeAgentScreenPreview(
    val screenId: String,
    val title: String,
    val route: String,
    val device: AppForgeAgentPreviewDevice,
    val nodes: List<AppForgeAgentPreviewNode>
)

internal data class AppForgeAgentDesignerState(
    val blueprint: AppForgeAgentBlueprint,
    val selectedScreenId: String,
    val device: AppForgeAgentPreviewDevice = AppForgeAgentPreviewDevice.PHONE,
    val revision: Long = 0L
)

internal data class AppForgeAgentDesignerHistory(
    val current: AppForgeAgentDesignerState,
    val undo: List<AppForgeAgentDesignerState> = emptyList(),
    val redo: List<AppForgeAgentDesignerState> = emptyList()
)

internal object AppForgeAgentVisualDesigner {
    private const val MAX_HISTORY = 20
    private const val MAX_COMPONENT_LABEL = 80
    private val safeComponent = Regex("^[A-Za-z][A-Za-z0-9 _./(){}:+-]{0,79}$")
    private val safeRoute = Regex("^/[A-Za-z0-9_./{}-]*$")
    private val safeId = Regex("^[A-Za-z][A-Za-z0-9_-]{0,63}$")

    fun create(blueprint: AppForgeAgentBlueprint): AppForgeAgentDesignerHistory {
        requireValid(blueprint)
        val selected = blueprint.screens.first().id
        return AppForgeAgentDesignerHistory(
            current = AppForgeAgentDesignerState(
                blueprint = blueprint,
                selectedScreenId = selected
            )
        )
    }

    fun selectScreen(
        history: AppForgeAgentDesignerHistory,
        screenId: String
    ): AppForgeAgentDesignerHistory {
        val normalized = screenId.trim()
        require(history.current.blueprint.screens.any { it.id == normalized }) {
            "Designer screen bulunamadı: $normalized"
        }
        return history.copy(
            current = history.current.copy(selectedScreenId = normalized)
        )
    }

    fun setDevice(
        history: AppForgeAgentDesignerHistory,
        device: AppForgeAgentPreviewDevice
    ): AppForgeAgentDesignerHistory = history.copy(
        current = history.current.copy(device = device)
    )

    fun updateTokens(
        history: AppForgeAgentDesignerHistory,
        tokens: AppForgeAgentDesignTokens
    ): AppForgeAgentDesignerHistory = mutate(history) { blueprint ->
        blueprint.copy(tokens = tokens)
    }

    fun updateScreenText(
        history: AppForgeAgentDesignerHistory,
        screenId: String,
        title: String? = null,
        purpose: String? = null
    ): AppForgeAgentDesignerHistory = mutate(history) { blueprint ->
        blueprint.copy(
            screens = blueprint.screens.map { screen ->
                if (screen.id != screenId) screen
                else screen.copy(
                    title = title?.trim()?.take(120) ?: screen.title,
                    purpose = purpose?.trim()?.take(500) ?: screen.purpose
                )
            }
        )
    }

    fun renameRoute(
        history: AppForgeAgentDesignerHistory,
        screenId: String,
        newRoute: String
    ): AppForgeAgentDesignerHistory {
        val route = newRoute.trim()
        require(safeRoute.matches(route)) { "Güvensiz designer route: $route" }

        return mutate(history) { blueprint ->
            val target = blueprint.screens.firstOrNull { it.id == screenId }
                ?: error("Designer screen bulunamadı: $screenId")
            val oldRoute = target.route

            blueprint.copy(
                startRoute = if (blueprint.startRoute == oldRoute) route else blueprint.startRoute,
                screens = blueprint.screens.map { screen ->
                    val renamed = if (screen.id == screenId) screen.copy(route = route) else screen
                    renamed.copy(
                        actions = renamed.actions.map { action ->
                            if (action.targetRoute == oldRoute) action.copy(targetRoute = route)
                            else action
                        }
                    )
                }
            )
        }
    }

    fun addScreen(
        history: AppForgeAgentDesignerHistory,
        id: String,
        title: String,
        route: String
    ): AppForgeAgentDesignerHistory {
        val normalizedId = id.trim()
        val normalizedRoute = route.trim()
        require(safeId.matches(normalizedId)) { "Güvensiz screen id: $normalizedId" }
        require(safeRoute.matches(normalizedRoute)) { "Güvensiz screen route: $normalizedRoute" }

        return mutate(history, selectAfter = normalizedId) { blueprint ->
            blueprint.copy(
                screens = blueprint.screens + AppForgeAgentScreenSpec(
                    id = normalizedId,
                    title = title.trim().take(120),
                    route = normalizedRoute,
                    purpose = "Visual Designer ile oluşturuldu."
                )
            )
        }
    }

    fun removeScreen(
        history: AppForgeAgentDesignerHistory,
        screenId: String
    ): AppForgeAgentDesignerHistory {
        val blueprint = history.current.blueprint
        val target = blueprint.screens.firstOrNull { it.id == screenId }
            ?: error("Designer screen bulunamadı: $screenId")
        require(blueprint.screens.size > 1) { "Son ekran silinemez." }
        require(blueprint.startRoute != target.route) { "Başlangıç ekranı silinmeden önce startRoute değiştirilmelidir." }
        require(
            blueprint.screens.none { screen ->
                screen.actions.any { it.targetRoute == target.route }
            }
        ) { "Bu ekrana yönlenen aksiyonlar varken ekran silinemez." }

        val remaining = blueprint.screens.filterNot { it.id == screenId }
        val nextSelected = if (history.current.selectedScreenId == screenId) remaining.first().id
        else history.current.selectedScreenId

        return mutate(history, selectAfter = nextSelected) { source ->
            source.copy(screens = source.screens.filterNot { it.id == screenId })
        }
    }

    fun moveScreen(
        history: AppForgeAgentDesignerHistory,
        screenId: String,
        direction: Int
    ): AppForgeAgentDesignerHistory {
        require(direction == -1 || direction == 1) { "direction yalnız -1 veya 1 olabilir." }
        return mutate(history) { blueprint ->
            val items = blueprint.screens.toMutableList()
            val index = items.indexOfFirst { it.id == screenId }
            require(index >= 0) { "Designer screen bulunamadı: $screenId" }
            val target = index + direction
            if (target !in items.indices) return@mutate blueprint
            val item = items.removeAt(index)
            items.add(target, item)
            blueprint.copy(screens = items)
        }
    }

    fun addComponent(
        history: AppForgeAgentDesignerHistory,
        screenId: String,
        component: String
    ): AppForgeAgentDesignerHistory {
        val label = component.trim().take(MAX_COMPONENT_LABEL)
        require(safeComponent.matches(label)) { "Güvensiz component etiketi." }

        return mutate(history) { blueprint ->
            blueprint.copy(
                screens = blueprint.screens.map { screen ->
                    if (screen.id != screenId) screen
                    else screen.copy(components = screen.components + label)
                }
            )
        }
    }

    fun removeComponent(
        history: AppForgeAgentDesignerHistory,
        screenId: String,
        index: Int
    ): AppForgeAgentDesignerHistory = mutate(history) { blueprint ->
        blueprint.copy(
            screens = blueprint.screens.map { screen ->
                if (screen.id != screenId) screen
                else {
                    require(index in screen.components.indices) { "Component index geçersiz." }
                    screen.copy(components = screen.components.filterIndexed { i, _ -> i != index })
                }
            }
        )
    }

    fun preview(history: AppForgeAgentDesignerHistory): AppForgeAgentScreenPreview {
        val state = history.current
        val screen = state.blueprint.screens.first { it.id == state.selectedScreenId }
        val nodes = buildList {
            add(AppForgeAgentPreviewNode("title", "TITLE", screen.title.trim()))
            screen.components.forEachIndexed { index, component ->
                add(
                    AppForgeAgentPreviewNode(
                        key = "component-$index",
                        kind = inferKind(component),
                        label = component.trim().take(MAX_COMPONENT_LABEL)
                    )
                )
            }
            screen.actions.forEachIndexed { index, action ->
                add(
                    AppForgeAgentPreviewNode(
                        key = "action-$index",
                        kind = "ACTION",
                        label = action.label.trim().take(MAX_COMPONENT_LABEL)
                    )
                )
            }
        }
        return AppForgeAgentScreenPreview(
            screenId = screen.id,
            title = screen.title,
            route = screen.route,
            device = state.device,
            nodes = nodes
        )
    }

    fun undo(history: AppForgeAgentDesignerHistory): AppForgeAgentDesignerHistory {
        val previous = history.undo.lastOrNull() ?: return history
        return history.copy(
            current = previous,
            undo = history.undo.dropLast(1),
            redo = (history.redo + history.current).takeLast(MAX_HISTORY)
        )
    }

    fun redo(history: AppForgeAgentDesignerHistory): AppForgeAgentDesignerHistory {
        val next = history.redo.lastOrNull() ?: return history
        return history.copy(
            current = next,
            undo = (history.undo + history.current).takeLast(MAX_HISTORY),
            redo = history.redo.dropLast(1)
        )
    }

    private fun mutate(
        history: AppForgeAgentDesignerHistory,
        selectAfter: String? = null,
        block: (AppForgeAgentBlueprint) -> AppForgeAgentBlueprint
    ): AppForgeAgentDesignerHistory {
        val before = history.current
        val nextBlueprint = block(before.blueprint)
        requireValid(nextBlueprint)
        val selected = selectAfter ?: before.selectedScreenId
        require(nextBlueprint.screens.any { it.id == selected }) {
            "Designer selection geçersiz: $selected"
        }
        val next = before.copy(
            blueprint = nextBlueprint,
            selectedScreenId = selected,
            revision = before.revision + 1
        )
        if (next == before) return history
        return AppForgeAgentDesignerHistory(
            current = next,
            undo = (history.undo + before).takeLast(MAX_HISTORY),
            redo = emptyList()
        )
    }

    private fun requireValid(blueprint: AppForgeAgentBlueprint) {
        val validation = AppForgeAgentBlueprintValidator.validate(blueprint)
        require(validation.valid) {
            validation.issues
                .filter { it.level == AgentBlueprintIssueLevel.ERROR }
                .joinToString(
                    prefix = "Visual Designer geçersiz blueprint üretti: ",
                    separator = " | "
                ) { "${it.field}: ${it.message}" }
        }
    }

    private fun inferKind(component: String): String {
        val value = component.lowercase()
        return when {
            "button" in value || "fab" in value -> "BUTTON"
            "field" in value || "input" in value -> "INPUT"
            "image" in value || "photo" in value -> "IMAGE"
            "list" in value || "grid" in value -> "COLLECTION"
            "card" in value -> "CARD"
            "text" in value || "label" in value -> "TEXT"
            else -> "COMPONENT"
        }
    }
}
