package com.appforge.studio.tools

import android.content.Context

/**
 * Excel Tools ve VideoForge için cihaz-yerel kullanım sayacı.
 *
 * Sayaçlar araç bazında ayrıdır.
 *
 * FREE:
 *   Excel Tools = 1 kullanım
 *   VideoForge = 1 kullanım
 *
 * PRO:
 *   Excel Tools = 5 kullanım
 *   VideoForge = 5 kullanım
 *
 * PRO entitlement bu sınıfın otoritesi değildir.
 * proUnlocked, doğrulanmış dış entitlement durumundan gelir.
 *
 * Mevcut sayaç davranışı gibi kullanım bilgisi uygulama verisinde
 * kalıcı tutulur; uygulama yeniden açıldığında sıfırlanmaz.
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

    const val PRO_LIMIT =
        5

    private const val PREFS_NAME =
        "appforge_other_apps_usage"

    private fun limit(
        proUnlocked: Boolean
    ): Int =
        if (proUnlocked) {
            PRO_LIMIT
        } else {
            FREE_LIMIT
        }

    private fun key(
        tool: Tool,
        proUnlocked: Boolean
    ): String =
        "used_v2_${tool.storageKey}_${if (proUnlocked) "pro" else "free"}"

    fun used(
        context: Context,
        tool: Tool,
        proUnlocked: Boolean
    ): Int {
        val max =
            limit(
                proUnlocked
            )

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getInt(
                key(
                    tool,
                    proUnlocked
                ),
                0
            )
            .coerceIn(
                0,
                max
            )
    }

    fun remaining(
        context: Context,
        tool: Tool,
        proUnlocked: Boolean
    ): Int =
        (
            limit(
                proUnlocked
            ) -
                used(
                    context,
                    tool,
                    proUnlocked
                )
        ).coerceAtLeast(
            0
        )

    fun canUse(
        context: Context,
        proUnlocked: Boolean,
        tool: Tool,
        amount: Int = 1
    ): Boolean {
        val required =
            amount.coerceAtLeast(
                1
            )

        return remaining(
            context,
            tool,
            proUnlocked
        ) >= required
    }

    @Synchronized
    fun consume(
        context: Context,
        proUnlocked: Boolean,
        tool: Tool,
        amount: Int = 1
    ): Boolean {

        val required =
            amount.coerceAtLeast(
                1
            )

        val max =
            limit(
                proUnlocked
            )

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val usageKey =
            key(
                tool,
                proUnlocked
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
            max
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
