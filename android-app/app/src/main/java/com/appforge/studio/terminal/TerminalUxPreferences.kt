package com.appforge.studio.terminal

import android.content.Context

internal data class TerminalUxPreferenceSnapshot(
    val fontSizeSp: Float,
    val productivityKeysExpanded: Boolean
)

internal object TerminalUxPreferences {
    fun load(
        context: Context
    ): TerminalUxPreferenceSnapshot {
        val prefs =
            context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

        return TerminalUxPreferenceSnapshot(
            fontSizeSp =
                prefs.getFloat(
                    KEY_FONT_SIZE,
                    DEFAULT_FONT_SIZE_SP
                ).coerceIn(
                    MIN_FONT_SIZE_SP,
                    MAX_FONT_SIZE_SP
                ),
            productivityKeysExpanded =
                prefs.getBoolean(
                    KEY_PRODUCTIVITY_KEYS,
                    false
                )
        )
    }

    fun saveFontSize(
        context: Context,
        fontSizeSp: Float
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putFloat(
                KEY_FONT_SIZE,
                fontSizeSp.coerceIn(
                    MIN_FONT_SIZE_SP,
                    MAX_FONT_SIZE_SP
                )
            )
            .apply()
    }

    fun saveProductivityKeysExpanded(
        context: Context,
        expanded: Boolean
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_PRODUCTIVITY_KEYS,
                expanded
            )
            .apply()
    }

    private const val PREFS_NAME =
        "appforge_terminal_ux_preferences"

    private const val KEY_FONT_SIZE =
        "font_size_sp_v1"

    private const val KEY_PRODUCTIVITY_KEYS =
        "productivity_keys_expanded_v1"

    private const val DEFAULT_FONT_SIZE_SP = 10f
    private const val MIN_FONT_SIZE_SP = 8f
    private const val MAX_FONT_SIZE_SP = 18f
}
