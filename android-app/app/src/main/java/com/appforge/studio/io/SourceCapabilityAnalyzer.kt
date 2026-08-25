package com.appforge.studio.io

import java.io.File


data class SourceCapabilityAnalysis(
    val camera: Boolean = false,
    val location: Boolean = false,
    val notifications: Boolean = false,
    val fileUpload: Boolean = false,
    val downloads: Boolean = false,
    val mediaPlayer: Boolean = false,
    val qrScanner: Boolean = false,
    val scannedFiles: Int = 0
) {
    fun detectedLabels(): List<String> =
        buildList {
            if (camera) add("Kamera")
            if (location) add("Konum")
            if (notifications) add("Bildirim")
            if (fileUpload) add("Dosya yükleme")
            if (downloads) add("DownloadManager")
            if (mediaPlayer) add("Media3")
            if (qrScanner) add("QR")
        }
}


object SourceCapabilityAnalyzer {

    private const val MAX_FILES =
        800

    private const val MAX_FILE_BYTES =
        4L * 1024L * 1024L

    private const val MAX_TOTAL_BYTES =
        20L * 1024L * 1024L

    private val textExtensions =
        setOf(
            "html",
            "htm",
            "js",
            "mjs",
            "cjs",
            "ts",
            "tsx",
            "jsx",
            "json",
            "css",
            "vue",
            "svelte"
        )

    private val fileInputRegex =
        Regex(
            """<input\b[^>]*\btype\s*=\s*["']?file["']?[^>]*>""",
            RegexOption.IGNORE_CASE
        )

    private val cameraInputRegex =
        Regex(
            """<input\b(?=[^>]*\btype\s*=\s*["']?file["']?)(?=[^>]*\bcapture\b)[^>]*>""",
            RegexOption.IGNORE_CASE
        )

    private val getUserMediaVideoRegex =
        Regex(
            """getUserMedia\s*\(\s*\{.{0,2000}?\bvideo\s*:\s*(?:true|\{)""",
            setOf(
                RegexOption.IGNORE_CASE,
                RegexOption.DOT_MATCHES_ALL
            )
        )

    private val downloadAttributeRegex =
        Regex(
            """<a\b[^>]*\bdownload(?:\s*=|\s|>)""",
            RegexOption.IGNORE_CASE
        )


    fun analyze(
        root: File
    ): SourceCapabilityAnalysis {

        if (
            !root.exists() ||
            !root.isDirectory
        ) {
            return SourceCapabilityAnalysis()
        }

        var camera =
            false

        var location =
            false

        var notifications =
            false

        var fileUpload =
            false

        var downloads =
            false

        var mediaPlayer =
            false

        var qrScanner =
            false

        var scannedFiles =
            0

        var totalBytes =
            0L


        for (
            file in
            root.walkTopDown()
        ) {

            if (
                scannedFiles >=
                    MAX_FILES ||
                totalBytes >=
                    MAX_TOTAL_BYTES
            ) {
                break
            }

            if (
                !file.isFile ||
                file.extension
                    .lowercase() !in
                    textExtensions
            ) {
                continue
            }

            val size =
                runCatching {
                    file.length()
                }.getOrDefault(
                    0L
                )

            if (
                size <= 0L ||
                size >
                    MAX_FILE_BYTES ||
                totalBytes +
                    size >
                    MAX_TOTAL_BYTES
            ) {
                continue
            }

            val text =
                runCatching {
                    file.readText(
                        Charsets.UTF_8
                    )
                }.getOrNull()
                    ?: continue

            scannedFiles +=
                1

            totalBytes +=
                size

            val lower =
                text.lowercase()


            if (
                !fileUpload &&
                fileInputRegex
                    .containsMatchIn(
                        text
                    )
            ) {
                fileUpload =
                    true
            }


            if (
                !camera &&
                (
                    cameraInputRegex
                        .containsMatchIn(
                            text
                        ) ||
                    getUserMediaVideoRegex
                        .containsMatchIn(
                            text
                        ) ||
                    lower.contains(
                        "facingmode"
                    )
                )
            ) {
                camera =
                    true
            }


            if (
                !location &&
                (
                    lower.contains(
                        "navigator.geolocation"
                    ) ||
                    lower.contains(
                        "geolocation.getcurrentposition"
                    ) ||
                    lower.contains(
                        "geolocation.watchposition"
                    )
                )
            ) {
                location =
                    true
            }


            if (
                !notifications &&
                (
                    lower.contains(
                        "notification.requestpermission"
                    ) ||
                    lower.contains(
                        "new notification("
                    ) ||
                    lower.contains(
                        ".shownotification("
                    )
                )
            ) {
                notifications =
                    true
            }


            if (
                !downloads &&
                (
                    downloadAttributeRegex
                        .containsMatchIn(
                            text
                        ) ||
                    lower.contains(
                        "appforgedownloads"
                    )
                )
            ) {
                downloads =
                    true
            }


            if (
                !mediaPlayer &&
                (
                    lower.contains(
                        "appforgemedia"
                    ) ||
                    lower.contains(
                        "appforge.media"
                    )
                )
            ) {
                mediaPlayer =
                    true
            }


            if (
                !qrScanner &&
                (
                    lower.contains(
                        "appforge.scancode"
                    ) ||
                    lower.contains(
                        "\"scancode\""
                    ) &&
                    lower.contains(
                        "appforge"
                    )
                )
            ) {
                qrScanner =
                    true
            }


            if (
                camera &&
                location &&
                notifications &&
                fileUpload &&
                downloads &&
                mediaPlayer &&
                qrScanner
            ) {
                break
            }
        }


        return SourceCapabilityAnalysis(
            camera =
                camera,
            location =
                location,
            notifications =
                notifications,
            fileUpload =
                fileUpload,
            downloads =
                downloads,
            mediaPlayer =
                mediaPlayer,
            qrScanner =
                qrScanner,
            scannedFiles =
                scannedFiles
        )
    }
}
