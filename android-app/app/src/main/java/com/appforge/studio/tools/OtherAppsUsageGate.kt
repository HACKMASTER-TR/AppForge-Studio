package com.appforge.studio.tools

import android.content.Context

/**
 * Other Apps usage policy.
 *
 * FREE:
 *   Excel Tools = 1 local use
 *   VideoForge = 1 local use
 *
 * PRO Monthly:
 *   Local counter is bypassed.
 *   Every use is charged authoritatively to the server-side
 *   AppForge project quota.
 *
 * The server, not this class, is authoritative for PRO quota.
 */
object OtherAppsUsageGate {

    enum class Tool(
        val storageKey: String
    ) {
        EXCEL_TOOLS(
            "excel_tools"
        ),
        VIDEO_FORGE(
            "video_forge"
        )
    }

    const val FREE_LIMIT =
        1

    private const val PREFS_NAME =
        "appforge_other_apps_usage"

    private fun key(
        tool: Tool
    ): String =
        "free_used_v3_${tool.storageKey}"

    fun used(
        context: Context,
        tool: Tool
    ): Int =
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getInt(
                key(tool),
                0
            )
            .coerceIn(
                0,
                FREE_LIMIT
            )

    fun remaining(
        context: Context,
        tool: Tool
    ): Int =
        (
            FREE_LIMIT -
                used(
                    context,
                    tool
                )
        ).coerceAtLeast(
            0
        )

    /*
     * Compatibility overload for existing callers.
     * PRO does not use this number as its authority.
     */
    fun remaining(
        context: Context,
        tool: Tool,
        proUnlocked: Boolean
    ): Int =
        if (proUnlocked) {
            FREE_LIMIT
        } else {
            remaining(
                context,
                tool
            )
        }

    fun canUse(
        context: Context,
        proUnlocked: Boolean,
        tool: Tool,
        amount: Int = 1
    ): Boolean {

        if (proUnlocked) {
            return true
        }

        return remaining(
            context,
            tool
        ) >=
            amount.coerceAtLeast(
                1
            )
    }

    @Synchronized
    fun consume(
        context: Context,
        proUnlocked: Boolean,
        tool: Tool,
        amount: Int = 1
    ): Boolean {

        /*
         * PRO consumption MUST be completed by the server
         * project-quota endpoint.
         */
        if (proUnlocked) {
            return true
        }

        val required =
            amount.coerceAtLeast(
                1
            )

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val usageKey =
            key(
                tool
            )

        val current =
            prefs
                .getInt(
                    usageKey,
                    0
                )
                .coerceAtLeast(
                    0
                )

        if (
            current +
                required >
            FREE_LIMIT
        ) {
            return false
        }

        return prefs
            .edit()
            .putInt(
                usageKey,
                current +
                    required
            )
            .commit()
    }
}
