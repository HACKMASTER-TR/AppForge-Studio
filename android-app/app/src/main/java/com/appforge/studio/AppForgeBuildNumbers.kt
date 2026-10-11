package com.appforge.studio

import android.content.Context

object AppForgeBuildNumbers {
    fun label(buildNo: Long?): String {
        val value = buildNo?.takeIf { it > 0L } ?: return "AF----------"
        return "AF-" + value.toString().padStart(10, '0')
    }
}

/* APPFORGE_PERSISTENT_BUILD_NO_V1 */
internal object AppForgeBuildNumberStore {
    private const val PREFS = "appforge_build_numbers_v1"
    private const val KEY_LAST = "last_build_no"
    private const val FIRST_BUILD_NO = 1_000L
    private const val MAX_BUILD_NO = 9_999_999_999L
    private val lock = Any()

    fun next(context: Context): Long = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST, FIRST_BUILD_NO - 1L)
            .coerceAtLeast(FIRST_BUILD_NO - 1L)
        check(last < MAX_BUILD_NO) { "AppForge Build No aralığı doldu." }
        val next = last + 1L
        check(prefs.edit().putLong(KEY_LAST, next).commit()) {
            "AppForge Build No kalıcı olarak kaydedilemedi."
        }
        next
    }
}

/* APPFORGE_BUILD_PROGRESS_SINGLE_SOURCE_V1 */
internal object AppForgeBuildProgress {
    fun visible(status: String, progress: Int): Int =
        if (status.trim().lowercase() == "success") 100
        else progress.coerceIn(0, 99)
}

object AppForgeUiSanitizer {
    fun preflight(
        source: List<String>
    ): List<String> =
        source.filterNot {
            line ->

            val text =
                line.lowercase()

            listOf(
                "build cache",
                "cache hit",
                "cache miss",
                "worker",
                "gradle",
                "jvm",
                "daemon",
                "build engine",
                "kaynak motoru",
                "source engine",
                "shared worker",
                "container sandbox"
            ).any {
                text.contains(
                    it
                )
            }
        }
}
