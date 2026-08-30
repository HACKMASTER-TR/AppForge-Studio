package com.appforge.studio

object AppForgeBuildNumbers {
    fun label(
        buildNo: Long?
    ): String {
        val value =
            buildNo
                ?.takeIf {
                    it > 0L
                }
                ?: return "AF------"

        return "AF-" +
            value
                .toString()
                .padStart(
                    6,
                    '0'
                )
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
