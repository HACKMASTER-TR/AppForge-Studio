package com.appforge.studio

import android.content.Context

object AppForgeBuildNumbers {
    fun label(
        context: Context,
        buildId: String?
    ): String {
        if (buildId.isNullOrBlank()) {
            return "AF------"
        }

        val prefs =
            context.getSharedPreferences(
                "appforge_public_build_numbers",
                Context.MODE_PRIVATE
            )

        val key =
            "build_$buildId"

        prefs.getString(
            key,
            null
        )?.let {
            return it
        }

        synchronized(this) {
            prefs.getString(
                key,
                null
            )?.let {
                return it
            }

            val next =
                prefs.getLong(
                    "next_number",
                    1L
                ).coerceAtLeast(
                    1L
                )

            val label =
                "AF-%06d".format(
                    next
                )

            prefs.edit()
                .putString(
                    key,
                    label
                )
                .putLong(
                    "next_number",
                    next + 1L
                )
                .apply()

            return label
        }
    }
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
