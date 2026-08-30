package com.appforge.studio.io

import java.io.File


data class SourceCapabilityAnalysis(
    val technologyId: String = "unknown",
    val technologyLabel: String = "Bilinmeyen proje",
    val buildEngine: String = "unknown",
    val buildReady: Boolean = false,
    val technologyReason: String? = null,

    val camera: Boolean = false,
    val microphone: Boolean = false,
    val location: Boolean = false,
    val notifications: Boolean = false,
    val networkState: Boolean = false,
    val wakeLock: Boolean = false,
    val nfc: Boolean = false,
    val additionalPermissions: Set<String> = emptySet(),
    val fileUpload: Boolean = false,
    val downloads: Boolean = false,
    val mediaPlayer: Boolean = false,
    val qrScanner: Boolean = false,

    val cameraReason: String? = null,
    val microphoneReason: String? = null,
    val locationReason: String? = null,
    val notificationsReason: String? = null,
    val networkStateReason: String? = null,
    val wakeLockReason: String? = null,
    val nfcReason: String? = null,
    val fileUploadReason: String? = null,
    val downloadsReason: String? = null,
    val mediaPlayerReason: String? = null,
    val qrScannerReason: String? = null,

    val scannedFiles: Int = 0
) {

    fun detectedLabels(): List<String> =
        buildList {
            if (
                technologyId !=
                    "unknown"
            ) {
                add(
                    technologyLabel
                )
            }
            if (camera) add("Kamera")
            if (microphone) add("Mikrofon")
            if (location) add("Konum")
            if (notifications) add("Bildirim")
            if (networkState) add("Ağ durumu")
            if (wakeLock) add("Uyanık tutma")
            if (nfc) add("NFC")
            additionalPermissions.forEach { add(it.replace('_', ' ')) }
            if (fileUpload) add("Dosya yükleme")
            if (downloads) add("DownloadManager")
            if (mediaPlayer) add("Media3")
            if (qrScanner) add("QR")
        }


    fun detectedDetails(): List<String> =
        buildList {

            add(
                "Proje türü • $technologyLabel" +
                    (
                        technologyReason
                            ?.let {
                                " • $it"
                            }
                            ?: ""
                    )
            )

            if (camera) {
                add(
                    "Kamera • " +
                        (
                            cameraReason
                                ?: "Kamera kullanımı bulundu"
                        )
                )
            }

            if (microphone) {
                add(
                    "Mikrofon • " +
                        (microphoneReason ?: "Ses yakalama kullanımı bulundu")
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

            if (networkState) {
                add("Ağ durumu • " + (networkStateReason ?: "Bağlantı kontrolü bulundu"))
            }

            if (wakeLock) {
                add("Uyanık tutma • " + (wakeLockReason ?: "Wake Lock kullanımı bulundu"))
            }

            if (nfc) {
                add("NFC • " + (nfcReason ?: "NFC kullanımı bulundu"))
            }

            additionalPermissions.forEach {
                add("Gelişmiş izin • ${it.replace('_', ' ')}")
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
            "svelte",
            "xml",
            "kt",
            "kts",
            "java",
            "gradle",
            "dart",
            "cs",
            "cpp",
            "cc",
            "c",
            "h",
            "hpp",
            "py",
            "rs",
            "go",
            "rb",
            "php",
            "swift",
            "m",
            "mm",
            "lua",
            "sh",
            "bash",
            "zsh",
            "ps1",
            "yaml",
            "yml",
            "toml",
            "groovy",
            "scss",
            "sass",
            "less"
        )


    private val fileInputRegex =
        Regex(
            """<input\b[^>]*\btype\s*=\s*["']?file["']?[^>]*>""",
            RegexOption.IGNORE_CASE
        )


    private val cameraInputRegex =
        Regex(
            """<input\b(?=[^>]*\btype\s*=\s*["']?file["']?)(?=[^>]*\bcapture\b)(?![^>]*\baccept\s*=\s*["'][^"']*audio/)[^>]*>""",
            RegexOption.IGNORE_CASE
        )


    private val audioCaptureInputRegex =
        Regex(
            """<input\b(?=[^>]*\btype\s*=\s*["']?file["']?)(?=[^>]*\baccept\s*=\s*["'][^"']*audio/)(?=[^>]*\bcapture\b)[^>]*>""",
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

        val technology =
            ProjectTechnologyDetector
                .detect(
                    root
                )

        if (
            !root.exists() ||
            !root.isDirectory
        ) {
            return SourceCapabilityAnalysis(
                technologyId =
                    technology.id,
                technologyLabel =
                    technology.label,
                buildEngine =
                    technology.buildEngine,
                buildReady =
                    technology.buildReady,
                technologyReason =
                    technology.reason
            )
        }


        var camera =
            false

        var microphone =
            false

        var location =
            false

        var notifications =
            false

        var networkState =
            false

        var wakeLock =
            false

        var nfc =
            false

        val additionalPermissions =
            mutableSetOf<String>()

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

        var microphoneReason: String? =
            null

        var locationReason: String? =
            null

        var notificationsReason: String? =
            null

        var networkStateReason: String? =
            null

        var wakeLockReason: String? =
            null

        var nfcReason: String? =
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

                    lower.contains("android.permission.camera") ||
                        lower.contains("manifest.permission.camera") -> {
                        camera = true
                        cameraReason = "Android CAMERA izni"
                    }
                }
            }

            if (
                !microphone &&
                (
                    lower.contains("android.permission.record_audio") ||
                    lower.contains("manifest.permission.record_audio") ||
                    (
                        lower.contains("getusermedia") &&
                        (
                            lower.contains("audio:true") ||
                            lower.contains("audio: true") ||
                            lower.contains("audio: {")
                        )
                    ) ||
                    audioCaptureInputRegex.containsMatchIn(text)
                )
            ) {
                microphone = true
                microphoneReason =
                    if (lower.contains("record_audio")) {
                        "Android RECORD_AUDIO izni"
                    } else {
                        "Ses yakalama API'si"
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

                    lower.contains("android.permission.access_fine_location") ||
                        lower.contains("android.permission.access_coarse_location") ||
                        lower.contains("manifest.permission.access_fine_location") -> {
                        location = true
                        locationReason = "Android konum izni"
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

                    lower.contains("android.permission.post_notifications") ||
                        lower.contains("manifest.permission.post_notifications") -> {
                        notifications = true
                        notificationsReason = "Android POST_NOTIFICATIONS izni"
                    }
                }
            }

            if (
                !networkState &&
                listOf(
                    "navigator.online",
                    "navigator.onLine".lowercase(),
                    "connectivitymanager",
                    "android.permission.access_network_state",
                    "manifest.permission.access_network_state"
                ).any { lower.contains(it) }
            ) {
                networkState = true
                networkStateReason = "Bağlantı/ağ durumu API'si"
            }

            if (
                !wakeLock &&
                listOf(
                    "navigator.wakelock",
                    "flag_keep_screen_on",
                    "android.permission.wake_lock",
                    "manifest.permission.wake_lock",
                    ".newwakelock("
                ).any { lower.contains(it) }
            ) {
                wakeLock = true
                wakeLockReason = "Wake Lock / ekranı açık tutma kullanımı"
            }

            if (
                !nfc &&
                listOf(
                    "ndefreader",
                    "nfcadapter",
                    "android.permission.nfc",
                    "manifest.permission.nfc",
                    "navigator.nfc"
                ).any { lower.contains(it) }
            ) {
                nfc = true
                nfcReason = "NFC / NDEF kullanımı"
            }

            mapOf(
                "BLUETOOTH" to listOf("bluetooth_scan", "bluetooth_connect", "bluetoothadapter"),
                "BIOMETRIC" to listOf("use_biometric", "biometricprompt"),
                "CALENDAR" to listOf("read_calendar", "write_calendar", "calendarcontract"),
                "CONTACTS" to listOf("read_contacts", "write_contacts", "contactscontract"),
                "BACKGROUND_LOCATION" to listOf("access_background_location"),
                "EXACT_ALARM" to listOf("schedule_exact_alarm", "setexactandallowwhileidle"),
                "MEDIA_IMAGES" to listOf("read_media_images"),
                "MEDIA_VIDEO" to listOf("read_media_video"),
                "ACTIVITY_RECOGNITION" to listOf("activity_recognition", "step_detector")
            ).forEach { (key, markers) ->
                if (markers.any { lower.contains(it) }) {
                    additionalPermissions.add(key)
                }
            }

            if ("BACKGROUND_LOCATION" in additionalPermissions && !location) {
                location = true
                locationReason = "Arka plan konumu temel konum izni gerektiriyor"
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
                microphone &&
                location &&
                notifications &&
                networkState &&
                wakeLock &&
                nfc &&
                fileUpload &&
                downloads &&
                mediaPlayer &&
                qrScanner
            ) {
                break
            }
        }


        return SourceCapabilityAnalysis(
            technologyId =
                technology.id,

            technologyLabel =
                technology.label,

            buildEngine =
                technology.buildEngine,

            buildReady =
                technology.buildReady,

            technologyReason =
                technology.reason,

            camera =
                camera,

            microphone =
                microphone,

            location =
                location,

            notifications =
                notifications,

            networkState =
                networkState,

            wakeLock =
                wakeLock,

            nfc =
                nfc,

            additionalPermissions =
                additionalPermissions,

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

            microphoneReason =
                microphoneReason,

            locationReason =
                locationReason,

            notificationsReason =
                notificationsReason,

            networkStateReason =
                networkStateReason,

            wakeLockReason =
                wakeLockReason,

            nfcReason =
                nfcReason,

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
