package com.appforge.studio.io

import org.json.JSONObject
import java.io.File

data class PwaReport(
    val detected: Boolean,
    val manifestPath: String?,
    val serviceWorkerFiles: List<String>,
    val appName: String?,
    val startUrl: String?,
    val display: String?,
    val themeColor: String?,
    val iconCount: Int
)

object PwaInspector {
    fun inspect(
        rootPath: String?
    ): PwaReport {
        val root =
            rootPath
                ?.let(::File)
                ?.takeIf {
                    it.exists() &&
                    it.isDirectory
                }
                ?: return PwaReport(
                    detected = false,
                    manifestPath = null,
                    serviceWorkerFiles = emptyList(),
                    appName = null,
                    startUrl = null,
                    display = null,
                    themeColor = null,
                    iconCount = 0
                )

        val candidates =
            root.walkTopDown()
                .maxDepth(6)
                .filter {
                    it.isFile &&
                    (
                        it.name.equals(
                            "manifest.webmanifest",
                            true
                        ) ||
                        it.name.equals(
                            "manifest.json",
                            true
                        )
                    )
                }
                .take(10)
                .toList()

        val manifest =
            candidates
                .firstOrNull {
                    runCatching {
                        val o =
                            JSONObject(
                                it.readText()
                            )

                        o.has(
                            "start_url"
                        ) ||
                        o.has(
                            "display"
                        ) ||
                        o.has(
                            "icons"
                        )
                    }.getOrDefault(
                        false
                    )
                }

        val manifestJson =
            manifest
                ?.let {
                    runCatching {
                        JSONObject(
                            it.readText()
                        )
                    }.getOrNull()
                }

        val serviceWorkers =
            root.walkTopDown()
                .maxDepth(6)
                .filter {
                    it.isFile &&
                    (
                        it.name.equals(
                            "service-worker.js",
                            true
                        ) ||
                        it.name.equals(
                            "sw.js",
                            true
                        ) ||
                        it.name.contains(
                            "serviceworker",
                            true
                        )
                    )
                }
                .take(20)
                .map {
                    it.relativeTo(root)
                        .invariantSeparatorsPath
                }
                .toList()

        val icons =
            manifestJson
                ?.optJSONArray(
                    "icons"
                )
                ?.length()
                ?: 0

        return PwaReport(
            detected =
                manifest != null ||
                serviceWorkers.isNotEmpty(),
            manifestPath =
                manifest
                    ?.relativeTo(root)
                    ?.invariantSeparatorsPath,
            serviceWorkerFiles =
                serviceWorkers,
            appName =
                manifestJson
                    ?.optString(
                        "name"
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    },
            startUrl =
                manifestJson
                    ?.optString(
                        "start_url"
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    },
            display =
                manifestJson
                    ?.optString(
                        "display"
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    },
            themeColor =
                manifestJson
                    ?.optString(
                        "theme_color"
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    },
            iconCount =
                icons
        )
    }
}
