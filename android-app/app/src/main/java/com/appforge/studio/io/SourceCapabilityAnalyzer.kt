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

    val cameraReason: String? = null,
    val locationReason: String? = null,
    val notificationsReason: String? = null,
    val fileUploadReason: String? = null,
    val downloadsReason: String? = null,
    val mediaPlayerReason: String? = null,
    val qrScannerReason: String? = null,

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


    fun detectedDetails(): List<String> =
        buildList {

            if (camera) {
                add(
                    "Kamera • " +
                        (
                            cameraReason
                                ?: "Kamera kullanımı bulundu"
                        )
                )
            }

            if (location) {
                add(
                    "Konum • " +
                        (
                            locationReason
                                ?: "Konum kullanımı bulundu"
                        )
                )
            }

            if (notifications) {
                add(
                    "Bildirim • " +
                        (
                            notificationsReason
                                ?: "Bildirim kullanımı bulundu"
                        )
                )
            }

            if (fileUpload) {
                add(
                    "Dosya yükleme • " +
                        (
                            fileUploadReason
                                ?: "Dosya seçici bulundu"
                        )
                )
            }

            if (downloads) {
                add(
                    "DownloadManager • " +
                        (
                            downloadsReason
                                ?: "İndirme bağlantısı bulundu"
                        )
                )
            }

            if (mediaPlayer) {
                add(
                    "Media3 • " +
                        (
                            mediaPlayerReason
                                ?: "AppForge medya kullanımı bulundu"
                        )
                )
            }

            if (qrScanner) {
                add(
                    "QR • " +
                        (
                            qrScannerReason
                                ?: "QR tarayıcı kullanımı bulundu"
                        )
                )
            }
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


        var cameraReason: String? =
            null

        var locationReason: String? =
            null

        var notificationsReason: String? =
            null

        var fileUploadReason: String? =
            null

        var downloadsReason: String? =
            null

        var mediaPlayerReason: String? =
            null

        var qrScannerReason: String? =
            null


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

                fileUploadReason =
                    "<input type=\"file\">"
            }


            if (!camera) {

                when {

                    cameraInputRegex
                        .containsMatchIn(
                            text
                        ) -> {

                        camera =
                            true

                        cameraReason =
                            "<input type=\"file\" capture>"
                    }


                    getUserMediaVideoRegex
                        .containsMatchIn(
                            text
                        ) -> {

                        camera =
                            true

                        cameraReason =
                            "getUserMedia({ video: ... })"
                    }


                    lower.contains(
                        "facingmode"
                    ) -> {

                        camera =
                            true

                        cameraReason =
                            "facingMode kamera seçimi"
                    }
                }
            }


            if (!location) {

                when {

                    lower.contains(
                        "navigator.geolocation"
                    ) -> {

                        location =
                            true

                        locationReason =
                            "navigator.geolocation"
                    }


                    lower.contains(
                        "geolocation.getcurrentposition"
                    ) -> {

                        location =
                            true

                        locationReason =
                            "getCurrentPosition()"
                    }


                    lower.contains(
                        "geolocation.watchposition"
                    ) -> {

                        location =
                            true

                        locationReason =
                            "watchPosition()"
                    }
                }
            }


            if (!notifications) {

                when {

                    lower.contains(
                        "notification.requestpermission"
                    ) -> {

                        notifications =
                            true

                        notificationsReason =
                            "Notification.requestPermission()"
                    }


                    lower.contains(
                        "new notification("
                    ) -> {

                        notifications =
                            true

                        notificationsReason =
                            "new Notification()"
                    }


                    lower.contains(
                        ".shownotification("
                    ) -> {

                        notifications =
                            true

                        notificationsReason =
                            "showNotification()"
                    }
                }
            }


            if (!downloads) {

                when {

                    downloadAttributeRegex
                        .containsMatchIn(
                            text
                        ) -> {

                        downloads =
                            true

                        downloadsReason =
                            "<a download>"
                    }


                    lower.contains(
                        "appforgedownloads"
                    ) -> {

                        downloads =
                            true

                        downloadsReason =
                            "AppForgeDownloads"
                    }
                }
            }


            if (!mediaPlayer) {

                when {

                    lower.contains(
                        "appforgemedia"
                    ) -> {

                        mediaPlayer =
                            true

                        mediaPlayerReason =
                            "AppForgeMedia"
                    }


                    lower.contains(
                        "appforge.media"
                    ) -> {

                        mediaPlayer =
                            true

                        mediaPlayerReason =
                            "AppForge.media"
                    }
                }
            }


            if (
                !qrScanner &&
                (
                    lower.contains(
                        "appforge.scancode"
                    ) ||
                    (
                        lower.contains(
                            "\"scancode\""
                        ) &&
                        lower.contains(
                            "appforge"
                        )
                    )
                )
            ) {

                qrScanner =
                    true

                qrScannerReason =
                    "AppForge.scanCode()"
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

            cameraReason =
                cameraReason,

            locationReason =
                locationReason,

            notificationsReason =
                notificationsReason,

            fileUploadReason =
                fileUploadReason,

            downloadsReason =
                downloadsReason,

            mediaPlayerReason =
                mediaPlayerReason,

            qrScannerReason =
                qrScannerReason,

            scannedFiles =
                scannedFiles
        )
    }
}
