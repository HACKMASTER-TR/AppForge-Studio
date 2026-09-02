package com.appforge.studio.tools

import android.content.Context

/**
 * Excel Tools + VideoForge için ortak ücretsiz kullanım sayacı.
 *
 * Free:
 *   Toplam 5 işlem.
 *
 * PRO:
 *   Sınırsız kullanım.
 *
 * Sayaç cihazdaki uygulama verisinde saklanır.
 */
object OtherAppsUsageGate {

    const val FREE_LIMIT = 5

    private const val PREFS_NAME =
        "appforge_other_apps_usage"

    private const val KEY_USED =
        "shared_used"

    fun used(
        context: Context
    ): Int =
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getInt(
                KEY_USED,
                0
            )
            .coerceIn(
                0,
                FREE_LIMIT
            )

    fun remaining(
        context: Context
    ): Int =
        (
            FREE_LIMIT -
                used(context)
        ).coerceAtLeast(0)

    fun canUse(
        context: Context,
        proUnlocked: Boolean,
        amount: Int = 1
    ): Boolean {

        if (proUnlocked) {
            return true
        }

        val required =
            amount.coerceAtLeast(1)

        return remaining(context) >=
            required
    }

    @Synchronized
    fun consume(
        context: Context,
        proUnlocked: Boolean,
        amount: Int = 1
    ): Boolean {

        if (proUnlocked) {
            return true
        }

        val required =
            amount.coerceAtLeast(1)

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val current =
            prefs
                .getInt(
                    KEY_USED,
                    0
                )
                .coerceAtLeast(0)

        if (
            current + required >
            FREE_LIMIT
        ) {
            return false
        }

        return prefs
            .edit()
            .putInt(
                KEY_USED,
                current + required
            )
            .commit()
    }
}
