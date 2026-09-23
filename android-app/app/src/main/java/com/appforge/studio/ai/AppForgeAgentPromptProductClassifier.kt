package com.appforge.studio.ai

internal enum class AppForgeAgentProductKind {
    APPLICATION,
    GAME
}

internal enum class AppForgeAgentGameMode {
    ARCADE,
    RACING
}

internal data class AppForgeAgentProductProfile(
    val kind: AppForgeAgentProductKind,
    val gameMode: AppForgeAgentGameMode? = null
)

internal object AppForgeAgentPromptProductClassifier {
    private val gameTerms = listOf(
        "oyun",
        "game",
        "yarış",
        "race",
        "racing",
        "arcade",
        "platformer",
        "platform oyunu",
        "skor",
        "puan",
        "boss",
        "canavar",
        "level",
        "seviye"
    )

    private val racingTerms = listOf(
        "yarış",
        "race",
        "racing",
        "araba",
        "car",
        "drift",
        "motor",
        "motosiklet",
        "scooter",
        "pist"
    )

    fun classify(
        blueprint: AppForgeAgentBlueprint
    ): AppForgeAgentProductProfile {
        val searchable = buildString {
            appendLine(blueprint.appName)
            appendLine(blueprint.prompt)
            blueprint.screens.forEach { screen ->
                appendLine(screen.title)
                appendLine(screen.purpose)
                screen.components.forEach(::appendLine)
                screen.actions.forEach { action ->
                    appendLine(action.label)
                }
            }
        }.lowercase()

        if (gameTerms.none(searchable::contains)) {
            return AppForgeAgentProductProfile(
                kind = AppForgeAgentProductKind.APPLICATION
            )
        }

        val mode =
            if (racingTerms.any(searchable::contains)) {
                AppForgeAgentGameMode.RACING
            } else {
                AppForgeAgentGameMode.ARCADE
            }

        return AppForgeAgentProductProfile(
            kind = AppForgeAgentProductKind.GAME,
            gameMode = mode
        )
    }
}
