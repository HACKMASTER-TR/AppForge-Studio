package com.appforge.studio.build

import android.content.Context
import android.net.Uri
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class BuildCreateResult(
    val buildId: String,
    val status: String
)

data class BuildCancelResult(
    val status: String,
    val immediate: Boolean
)

data class BuildStatusResult(
    val buildId: String,
    val status: String,
    val progress: Int,
    val logs: List<String>,
    val preflight: List<String>,
    val apkAvailable: Boolean,
    val aabAvailable: Boolean,
    val exeAvailable: Boolean
)

data class DownloadTicketResult(
    val url: String,
    val direct: Boolean,
    val expiresInSeconds: Int
)

data class RemoteBuildHistoryItem(
    val buildId: String,
    val appName: String,
    val packageName: String,
    val status: String,
    val createdAt: Long
)


data class ArtifactTopFile(
    val path: String,
    val category: String,
    val sizeBytes: Long
)

data class ArtifactSizeReport(
    val kind: String,
    val fileSizeBytes: Long,
    val uncompressedBytes: Long,
    val entryCount: Int,
    val topFiles: List<ArtifactTopFile>,
    val groups: Map<String, Long>
)

data class SecurityInsight(
    val severity: String,
    val title: String,
    val detail: String
)

data class TestLabResult(
    val buildId: String,
    val appName: String,
    val packageName: String,
    val apk: ArtifactSizeReport?,
    val aab: ArtifactSizeReport?,
    val security: List<SecurityInsight>
)

data class BuildCompareResult(
    val leftBuildId: String,
    val rightBuildId: String,
    val apkDeltaBytes: Long,
    val aabDeltaBytes: Long,
    val changeCount: Int,
    val changes: List<String>,
    val releaseNotes: List<String>
)

class BuildApiClient(
    private val context: Context,
    private val baseUrl: String,
    private val apiKey: String
) {
    fun createBuild(
        draft: ProjectDraft,
        projectZip: File?,
        idempotencyKey: String? = null
    ): BuildCreateResult {
        if (
            draft.signingMode == SigningMode.CUSTOM &&
            !baseUrl.startsWith("https://", ignoreCase = true)
        ) {
            error("Özel keystore ile imzalama için Build Service HTTPS olmalı.")
        }

        val directProjectKey =
            if (
                draft.sourceMode == SourceMode.LOCAL &&
                projectZip != null
            ) {
                createDirectProjectUpload(
                    projectZip
                )
            } else {
                null
            }

        val boundary = "----AppForge${UUID.randomUUID()}"
        val conn = connection("/api/builds").apply {
            requestMethod = "POST"
            if (!idempotencyKey.isNullOrBlank()) {
                setRequestProperty(
                    "Idempotency-Key",
                    idempotencyKey
                )
            }
            doOutput = true
            readTimeout = 600_000
            setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )
        }

        val config = JSONObject().apply {
            put("appName", draft.appName)
            put("packageName", draft.packageName)
            put("sourceMode", draft.sourceMode.name)
            put("webUrl", draft.webUrl)
            put("versionName", draft.versionName)
            put("versionCode", draft.versionCode)
            put("buildOutput", draft.buildOutput)

            put("orientation", draft.orientation)
            put("primaryColor", draft.primaryColor)
            put("backgroundColor", draft.backgroundColor)
            put("statusBarColor", draft.statusBarColor)
            put("navigationBarColor", draft.navigationBarColor)
            put("splashEnabled", draft.splashEnabled)
            put("splashText", draft.splashText)

            put("deepLink", JSONObject().apply {
                put("enabled", draft.deepLinkEnabled)
                put("scheme", draft.deepLinkScheme)
                put("host", draft.deepLinkHost)
                put("pathPrefix", draft.deepLinkPathPrefix)
            })

            put("webView", JSONObject().apply {
                put(
                    "javaScriptEnabled",
                    draft.webJavaScriptEnabled
                )
                put(
                    "domStorageEnabled",
                    draft.webDomStorageEnabled
                )
                put(
                    "zoomEnabled",
                    draft.webZoomEnabled
                )
                put(
                    "wideViewPortEnabled",
                    draft.webWideViewPortEnabled
                )
                put(
                    "overviewModeEnabled",
                    draft.webOverviewModeEnabled
                )
                put(
                    "mediaAutoplayEnabled",
                    draft.webMediaAutoplayEnabled
                )
                put(
                    "mixedContentAllowed",
                    draft.webMixedContentAllowed
                )
            })

            put("nativeBridge", JSONObject().apply {
                put(
                    "enabled",
                    draft.javascriptBridge &&
                        draft.webJavaScriptEnabled
                )
                put("allowRemote", draft.remoteBridgeAllowed)
                put("share", draft.shareBridge)
                put("clipboard", draft.clipboardBridge)
                put("vibration", draft.vibrationBridge)
                put("mediaPlayer", draft.mediaPlayerBridge)
                put("qrScanner", draft.qrScanner)
            })

            put("admob", JSONObject().apply {
                put("enabled", draft.admobEnabled)
                put("appId", draft.admobAppId)
                put("bannerUnitId", draft.admobBannerUnitId)
                put("interstitialUnitId", draft.admobInterstitialUnitId)
                put("rewardedUnitId", draft.admobRewardedUnitId)
                put("umpConsent", draft.umpConsentEnabled)
            })

            put("billing", JSONObject().apply {
                put("enabled", draft.billingEnabled)
                put("productIds", draft.billingProductIds)
                put("subscriptionIds", draft.billingSubscriptionIds)
                put("consumableProductIds", draft.consumableProductIds)
                put("removeAdsProductId", draft.removeAdsProductId)
                put("verificationUrl", draft.purchaseVerificationUrl)
            })

            put("firebase", JSONObject().apply {
                put("analytics", draft.firebaseAnalyticsEnabled)
                put("crashlytics", draft.firebaseCrashlyticsEnabled)
                put("hasConfig", !draft.firebaseConfigUri.isNullOrBlank())
            })

            put("signing", JSONObject().apply {
                put("mode", draft.signingMode.name)
                put("alias", draft.keyAlias)
                put("storePassword", draft.storePassword)
                put("keyPassword", draft.keyPassword)
            })

            put("features", JSONObject().apply {
                put("fileUpload", draft.fileUpload)
                put("downloads", draft.downloads)
                put("fullscreen", draft.fullscreen)
                put("notifications", draft.notifications)
                put("camera", draft.camera)
                put("location", draft.location)
                put("offlineCache", draft.offlineCache)
            })
        }.toString()

        val multipartBody = File.createTempFile(
            "appforge-build-",
            ".multipart",
            context.cacheDir
        )

        try {
            multipartBody.outputStream().buffered().use { out ->
                writeTextPart(
                    out,
                    boundary,
                    "config",
                    config
                )

                if (
                    !directProjectKey.isNullOrBlank()
                ) {
                    writeStringPart(
                        out,
                        boundary,
                        "projectObjectKey",
                        directProjectKey
                    )
                }

                if (
                    draft.signingMode == SigningMode.CUSTOM &&
                    !draft.keystoreUri.isNullOrBlank()
                ) {
                    val uri = Uri.parse(draft.keystoreUri)

                    writeUriPart(
                        out,
                        boundary,
                        "keystore",
                        draft.keystoreName.ifBlank {
                            "release.jks"
                        },
                        "application/octet-stream",
                        uri
                    )
                }

                if (!draft.iconUri.isNullOrBlank()) {
                    writeUriPart(
                        out,
                        boundary,
                        "icon",
                        draft.iconName.ifBlank {
                            "icon.png"
                        },
                        "image/png",
                        Uri.parse(draft.iconUri)
                    )
                }

                if (!draft.firebaseConfigUri.isNullOrBlank()) {
                    writeUriPart(
                        out,
                        boundary,
                        "firebaseConfig",
                        "google-services.json",
                        "application/json",
                        Uri.parse(draft.firebaseConfigUri)
                    )
                }

                out.write(
                    "--$boundary--\r\n".toByteArray()
                )
                out.flush()
            }

            val bodyLength = multipartBody.length()

            require(bodyLength > 0L) {
                "Multipart build isteği boş oluşturuldu."
            }

            // Content-Length önceden belli.
            // Railway'e chunked transfer kullanılmadan,
            // dosya RAM'e tamamen alınmadan gönderilir.
            conn.setFixedLengthStreamingMode(bodyLength)

            multipartBody.inputStream()
                .buffered()
                .use { input ->
                    conn.outputStream
                        .buffered()
                        .use { out ->
                            input.copyTo(
                                out,
                                1024 * 1024
                            )
                            out.flush()
                        }
                }

            val json = JSONObject(
                readResponse(conn)
            )

            return BuildCreateResult(
                buildId = json.getString("buildId"),
                status = json.getString("status")
            )
        } finally {
            multipartBody.delete()
        }
    }

    fun getBuild(buildId: String): BuildStatusResult {
        val conn = connection("/api/builds/$buildId").apply {
            requestMethod = "GET"
            readTimeout = 30_000
        }

        val json = JSONObject(readResponse(conn))

        fun array(name: String): List<String> {
            val a = json.optJSONArray(name) ?: return emptyList()
            return buildList {
                for (i in 0 until a.length()) {
                    add(a.optString(i))
                }
            }
        }

        val outputs = json.optJSONObject("outputs")

        return BuildStatusResult(
            buildId = buildId,
            status = json.optString("status", "unknown"),
            progress = json.optInt("progress", 0),
            logs = array("logs"),
            preflight = array("preflight"),
            apkAvailable = outputs?.has("apk") == true,
            aabAvailable = outputs?.has("aab") == true,
            exeAvailable = outputs?.has("exe") == true
        )
    }

    fun cancelBuild(
        buildId: String
    ): BuildCancelResult {
        val conn =
            connection(
                "/api/builds/$buildId/cancel"
            ).apply {
                requestMethod =
                    "POST"

                doOutput =
                    true

                readTimeout =
                    20_000

                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
            }

        conn.outputStream.use {
            it.write(
                "{}".toByteArray()
            )
        }

        val json =
            JSONObject(
                readResponse(
                    conn
                )
            )

        return BuildCancelResult(
            status =
                json.optString(
                    "status",
                    "unknown"
                ),
            immediate =
                json.optBoolean(
                    "immediate",
                    false
                )
        )
    }


    fun createDownloadTicket(
        buildId: String,
        kind: String
    ): DownloadTicketResult {
        val safeKind =
            when (
                kind.lowercase()
            ) {
                "aab" -> "aab"
                "exe" -> "exe"
                else -> "apk"
            }

        val conn = connection(
            "/api/builds/$buildId/download-ticket"
        ).apply {
            requestMethod = "POST"
            doOutput = true
            readTimeout = 20_000
            setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )
        }

        val body = JSONObject()
            .put("kind", safeKind)
            .toString()

        conn.outputStream.use {
            it.write(body.toByteArray())
        }

        val json = JSONObject(readResponse(conn))
        val rawUrl = json.getString("url")

        val resolved =
            if (
                rawUrl.startsWith("http://", true) ||
                rawUrl.startsWith("https://", true)
            ) {
                rawUrl
            } else {
                "${baseUrl.trimEnd('/')}/${rawUrl.trimStart('/')}"
            }

        return DownloadTicketResult(
            url = resolved,
            direct = json.optBoolean("direct", false),
            expiresInSeconds = json.optInt(
                "expiresInSeconds",
                300
            )
        )
    }

    fun history(): List<RemoteBuildHistoryItem> {
        val conn = connection("/api/builds").apply {
            requestMethod = "GET"
            readTimeout = 30_000
        }

        val json = JSONObject(readResponse(conn))
        val arr = json.optJSONArray("builds")
            ?: return emptyList()

        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    RemoteBuildHistoryItem(
                        buildId = o.optString("buildId"),
                        appName = o.optString("appName"),
                        packageName = o.optString("packageName"),
                        status = o.optString("status"),
                        createdAt = o.optLong("createdAt")
                    )
                )
            }
        }
    }



    fun testLab(
        buildId: String
    ): TestLabResult {
        val conn =
            connection(
                "/api/builds/$buildId/test-lab"
            ).apply {
                requestMethod =
                    "GET"
                readTimeout =
                    120_000
            }

        val json =
            JSONObject(
                readResponse(
                    conn
                )
            )

        fun artifact(
            name: String
        ): ArtifactSizeReport? {
            val o =
                json.optJSONObject(
                    name
                )
                    ?: return null

            val top =
                o.optJSONArray(
                    "topFiles"
                )

            val topFiles =
                buildList {
                    if (top != null) {
                        for (
                            i in
                            0 until
                            top.length()
                        ) {
                            val f =
                                top.getJSONObject(
                                    i
                                )

                            add(
                                ArtifactTopFile(
                                    path =
                                        f.optString(
                                            "path"
                                        ),
                                    category =
                                        f.optString(
                                            "category"
                                        ),
                                    sizeBytes =
                                        f.optLong(
                                            "sizeBytes"
                                        )
                                )
                            )
                        }
                    }
                }

            val groupsObject =
                o.optJSONObject(
                    "groups"
                )

            val groups =
                buildMap<
                    String,
                    Long
                > {
                    if (
                        groupsObject !=
                        null
                    ) {
                        val keys =
                            groupsObject.keys()

                        while (
                            keys.hasNext()
                        ) {
                            val key =
                                keys.next()

                            put(
                                key,
                                groupsObject
                                    .optLong(
                                        key
                                    )
                            )
                        }
                    }
                }

            return ArtifactSizeReport(
                kind =
                    o.optString(
                        "kind",
                        name
                    ),
                fileSizeBytes =
                    o.optLong(
                        "fileSizeBytes"
                    ),
                uncompressedBytes =
                    o.optLong(
                        "uncompressedBytes"
                    ),
                entryCount =
                    o.optInt(
                        "entryCount"
                    ),
                topFiles =
                    topFiles,
                groups =
                    groups
            )
        }

        val securityArray =
            json.optJSONArray(
                "security"
            )

        val security =
            buildList {
                if (
                    securityArray !=
                    null
                ) {
                    for (
                        i in
                        0 until
                        securityArray
                            .length()
                    ) {
                        val item =
                            securityArray
                                .getJSONObject(
                                    i
                                )

                        add(
                            SecurityInsight(
                                severity =
                                    item.optString(
                                        "severity"
                                    ),
                                title =
                                    item.optString(
                                        "title"
                                    ),
                                detail =
                                    item.optString(
                                        "detail"
                                    )
                            )
                        )
                    }
                }
            }

        return TestLabResult(
            buildId =
                json.optString(
                    "buildId",
                    buildId
                ),
            appName =
                json.optString(
                    "appName"
                ),
            packageName =
                json.optString(
                    "packageName"
                ),
            apk =
                artifact(
                    "apk"
                ),
            aab =
                artifact(
                    "aab"
                ),
            security =
                security
        )
    }

    fun compareBuilds(
        leftBuildId: String,
        rightBuildId: String
    ): BuildCompareResult {
        val conn =
            connection(
                "/api/builds/compare?left=$leftBuildId&right=$rightBuildId"
            ).apply {
                requestMethod =
                    "GET"
                readTimeout =
                    60_000
            }

        val json =
            JSONObject(
                readResponse(
                    conn
                )
            )

        fun strings(
            arrayName: String
        ): List<String> {
            val array =
                json.optJSONArray(
                    arrayName
                )
                    ?: return emptyList()

            return buildList {
                for (
                    i in
                    0 until
                    array.length()
                ) {
                    add(
                        array.optString(
                            i
                        )
                    )
                }
            }
        }

        val changesArray =
            json.optJSONArray(
                "changes"
            )

        val changes =
            buildList {
                if (
                    changesArray !=
                    null
                ) {
                    for (
                        i in
                        0 until
                        changesArray.length()
                    ) {
                        val item =
                            changesArray
                                .getJSONObject(
                                    i
                                )

                        add(
                            "${item.optString("path")}: ${item.opt("before")} → ${item.opt("after")}"
                        )
                    }
                }
            }

        return BuildCompareResult(
            leftBuildId =
                json.optJSONObject(
                    "left"
                )
                    ?.optString(
                        "buildId"
                    )
                    .orEmpty(),
            rightBuildId =
                json.optJSONObject(
                    "right"
                )
                    ?.optString(
                        "buildId"
                    )
                    .orEmpty(),
            apkDeltaBytes =
                json.optJSONObject(
                    "deltas"
                )
                    ?.optLong(
                        "apkBytes"
                    )
                    ?: 0L,
            aabDeltaBytes =
                json.optJSONObject(
                    "deltas"
                )
                    ?.optLong(
                        "aabBytes"
                    )
                    ?: 0L,
            changeCount =
                json.optInt(
                    "changeCount"
                ),
            changes =
                changes,
            releaseNotes =
                strings(
                    "releaseNotes"
                )
        )
    }

    fun releaseNotes(
        buildId: String
    ): List<String> {
        val conn =
            connection(
                "/api/builds/$buildId/release-notes"
            ).apply {
                requestMethod =
                    "GET"
                readTimeout =
                    30_000
            }

        val json =
            JSONObject(
                readResponse(
                    conn
                )
            )

        val array =
            json.optJSONArray(
                "releaseNotes"
            )
                ?: return emptyList()

        return buildList {
            for (
                i in
                0 until
                array.length()
            ) {
                add(
                    array.optString(
                        i
                    )
                )
            }
        }
    }

    fun getLogs(
        buildId: String,
        afterId: Long = 0
    ): List<Pair<Long, String>> {
        val conn = connection(
            "/api/builds/$buildId/logs?after=$afterId&limit=500"
        ).apply {
            requestMethod = "GET"
            readTimeout = 30_000
        }

        val json =
            JSONObject(
                readResponse(conn)
            )

        val arr =
            json.optJSONArray("logs")
                ?: return emptyList()

        return buildList {
            for (
                i in 0 until arr.length()
            ) {
                val item =
                    arr.getJSONObject(i)

                add(
                    item.optLong("id") to
                    item.optString("line")
                )
            }
        }
    }

    private fun createDirectProjectUpload(
        projectZip: File
    ): String {
        require(
            projectZip.exists() &&
            projectZip.isFile
        ) {
            "Proje ZIP dosyası bulunamadı."
        }

        require(
            projectZip.length() > 0L
        ) {
            "Proje ZIP dosyası boş."
        }

        val requestBody =
            JSONObject()
                .put(
                    "sizeBytes",
                    projectZip.length()
                )
                .toString()
                .toByteArray(
                    Charsets.UTF_8
                )

        val createConn =
            connection(
                "/api/uploads/build-input"
            ).apply {
                requestMethod =
                    "POST"

                doOutput =
                    true

                connectTimeout =
                    20_000

                readTimeout =
                    30_000

                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                setFixedLengthStreamingMode(
                    requestBody.size
                )
            }

        createConn.outputStream.use {
            it.write(
                requestBody
            )
            it.flush()
        }

        val uploadJson =
            JSONObject(
                readResponse(
                    createConn
                )
            )

        createConn.disconnect()

        val uploadUrl =
            uploadJson.getString(
                "uploadUrl"
            )

        val objectKey =
            uploadJson.getString(
                "objectKey"
            )

        uploadProjectToSignedUrl(
            uploadUrl,
            projectZip
        )

        return objectKey
    }


    private fun uploadProjectToSignedUrl(
        uploadUrl: String,
        projectZip: File
    ) {
        val conn =
            (
                URL(
                    uploadUrl
                ).openConnection()
                    as HttpURLConnection
            ).apply {
                requestMethod =
                    "PUT"

                doOutput =
                    true

                connectTimeout =
                    30_000

                readTimeout =
                    600_000

                setFixedLengthStreamingMode(
                    projectZip.length()
                )
            }

        try {
            projectZip
                .inputStream()
                .buffered()
                .use { input ->

                    conn.outputStream
                        .buffered()
                        .use { output ->

                            val buffer =
                                ByteArray(
                                    1024 * 1024
                                )

                            while (true) {
                                val count =
                                    input.read(
                                        buffer
                                    )

                                if (
                                    count < 0
                                ) {
                                    break
                                }

                                output.write(
                                    buffer,
                                    0,
                                    count
                                )
                            }

                            output.flush()
                        }
                }

            val status =
                conn.responseCode

            val responseText =
                (
                    if (
                        status in
                        200..299
                    ) {
                        conn.inputStream
                    } else {
                        conn.errorStream
                    }
                )
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()

            if (
                status !in
                200..299
            ) {
                error(
                    "S3 proje yükleme hatası " +
                    "$status: " +
                    responseText.take(
                        1000
                    )
                )
            }
        } finally {
            conn.disconnect()
        }
    }


    private fun writeStringPart(
        out: OutputStream,
        boundary: String,
        name: String,
        value: String
    ) {
        out.write(
            "--$boundary\r\n"
                .toByteArray()
        )

        out.write(
            (
                "Content-Disposition: " +
                "form-data; " +
                "name=\"$name\"\r\n"
            ).toByteArray()
        )

        out.write(
            (
                "Content-Type: " +
                "text/plain; " +
                "charset=UTF-8\r\n\r\n"
            ).toByteArray()
        )

        out.write(
            value.toByteArray(
                Charsets.UTF_8
            )
        )

        out.write(
            "\r\n"
                .toByteArray()
        )
    }


    private fun writeTextPart(
        out: OutputStream,
        boundary: String,
        name: String,
        value: String
    ) {
        out.write("--$boundary\r\n".toByteArray())
        out.write(
            "Content-Disposition: form-data; name=\"$name\"\r\n"
                .toByteArray()
        )
        out.write(
            "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                .toByteArray()
        )
        out.write(value.toByteArray())
        out.write("\r\n".toByteArray())
    }

    private fun writeUriPart(
        out: OutputStream,
        boundary: String,
        name: String,
        filename: String,
        mime: String,
        uri: Uri
    ) {
        val input = context.contentResolver
            .openInputStream(uri)
            ?: error("Dosya okunamadı: $uri")

        writeFilePart(
            out,
            boundary,
            name,
            filename,
            mime,
            input
        )
    }

    private fun writeFilePart(
        out: OutputStream,
        boundary: String,
        name: String,
        filename: String,
        mime: String,
        input: InputStream
    ) {
        input.use { stream ->
            out.write("--$boundary\r\n".toByteArray())
            out.write(
                "Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n"
                    .toByteArray()
            )
            out.write(
                "Content-Type: $mime\r\n\r\n"
                    .toByteArray()
            )

            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }

            out.write("\r\n".toByteArray())
        }
    }

    private fun connection(path: String) =
        (
            URL(
                "${baseUrl.trimEnd('/')}$path"
            ).openConnection()
            as HttpURLConnection
        ).apply {
            connectTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("X-AppForge-Key", apiKey)
            }
        }

    private fun readResponse(
        conn: HttpURLConnection
    ): String {
        val stream =
            if (
                conn.responseCode in 200..299
            ) {
                conn.inputStream
            } else {
                conn.errorStream
            }

        val body = stream
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()

        if (
            conn.responseCode !in 200..299
        ) {
            throw IllegalStateException(
                "Sunucu hatası ${conn.responseCode}: $body"
            )
        }

        return body
    }
}
