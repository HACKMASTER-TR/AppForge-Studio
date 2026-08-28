package com.appforge.studio.io

import android.content.Context
import android.net.Uri
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupImportResult(
    val draft: ProjectDraft,
    val importedFolder: File?
)

object ProjectBackupManager {
    private const val META =
        "appforge-project.json"

    fun exportToUri(
        context: Context,
        draft: ProjectDraft,
        target: Uri
    ) {
        val output =
            context.contentResolver
                .openOutputStream(target)
                ?: error(
                    "Yedek hedefi açılamadı."
                )

        output.use {
            stream ->
            ZipOutputStream(stream).use {
                zip ->
                zip.putNextEntry(
                    ZipEntry(META)
                )
                zip.write(
                    serialize(draft)
                        .toString(2)
                        .toByteArray()
                )
                zip.closeEntry()

                if (
                    draft.sourceMode ==
                    SourceMode.LOCAL
                ) {
                    val root =
                        draft.importedFolder
                            ?.let(::File)

                    if (
                        root != null &&
                        root.exists()
                    ) {
                        addDirectory(
                            zip,
                            root,
                            root,
                            "source/"
                        )
                    }
                }
            }
        }
    }

    fun importFromUri(
        context: Context,
        source: Uri
    ): BackupImportResult {
        val destination =
            File(
                context.filesDir,
                "backup-imports/" +
                    UUID.randomUUID()
                        .toString()
            ).apply {
                mkdirs()
            }

        var metadata:
            JSONObject? =
            null

        val input =
            context.contentResolver
                .openInputStream(source)
                ?: error(
                    "Yedek dosyası açılamadı."
                )

        input.use {
            raw ->
            ZipInputStream(raw).use {
                zip ->
                var entries =
                    0

                while (true) {
                    val entry =
                        zip.nextEntry
                            ?: break

                    entries++

                    require(
                        entries <=
                        5000
                    ) {
                        "ZIP çok fazla dosya içeriyor."
                    }

                    val safeName =
                        entry.name
                            .replace("\\", "/")
                            .trimStart('/')

                    require(
                        safeName.length <=
                        240
                    ) {
                        "ZIP yolu çok uzun."
                    }

                    require(
                        !safeName.contains("../") &&
                            safeName != ".."
                    ) {
                        "Güvensiz ZIP yolu."
                    }

                    if (
                        safeName ==
                        META
                    ) {
                        val bytes =
                            zip.readBytes()

                        require(
                            bytes.size <=
                            1_000_000
                        ) {
                            "Proje metadata dosyası çok büyük."
                        }

                        metadata =
                            JSONObject(
                                bytes.toString(
                                    Charsets.UTF_8
                                )
                            )

                        zip.closeEntry()
                        break
                    }

                    zip.closeEntry()
                }
            }
        }

        // Re-open for actual extraction because metadata may appear anywhere.
        val secondInput =
            context.contentResolver
                .openInputStream(source)
                ?: error(
                    "Yedek dosyası tekrar açılamadı."
                )

        secondInput.use {
            raw ->
            ZipInputStream(raw).use {
                zip ->
                var entries =
                    0

                var totalBytes =
                    0L

                val buffer =
                    ByteArray(
                        64 * 1024
                    )

                while (true) {
                    val entry =
                        zip.nextEntry
                            ?: break

                    entries++

                    require(
                        entries <=
                        5000
                    ) {
                        "ZIP çok fazla dosya içeriyor."
                    }

                    val safeName =
                        entry.name
                            .replace("\\", "/")
                            .trimStart('/')

                    require(
                        safeName.length <=
                        240
                    ) {
                        "ZIP yolu çok uzun."
                    }

                    require(
                        !safeName.contains("../") &&
                            safeName != ".."
                    ) {
                        "Güvensiz ZIP yolu."
                    }

                    if (
                        safeName.startsWith(
                            "source/"
                        ) &&
                        safeName.length >
                        "source/".length
                    ) {
                        val relative =
                            safeName.removePrefix(
                                "source/"
                            )

                        val target =
                            File(
                                destination,
                                relative
                            )

                        val canonicalRoot =
                            destination
                                .canonicalFile

                        val canonicalTarget =
                            target
                                .canonicalFile

                        require(
                            canonicalTarget.path
                                .startsWith(
                                    canonicalRoot.path +
                                        File.separator
                                )
                        ) {
                            "ZIP hedef yolu proje dışına çıkıyor."
                        }

                        if (entry.isDirectory) {
                            canonicalTarget.mkdirs()
                        } else {
                            canonicalTarget.parentFile
                                ?.mkdirs()

                            canonicalTarget
                                .outputStream()
                                .use {
                                    output ->
                                    var entryBytes =
                                        0L

                                    while (true) {
                                        val read =
                                            zip.read(
                                                buffer
                                            )

                                        if (
                                            read < 0
                                        ) {
                                            break
                                        }

                                        entryBytes +=
                                            read

                                        totalBytes +=
                                            read

                                        require(
                                            entryBytes <=
                                            50L *
                                            1024L *
                                            1024L
                                        ) {
                                            "ZIP içindeki tek dosya 50 MB sınırını aşıyor."
                                        }

                                        require(
                                            totalBytes <=
                                            250L *
                                            1024L *
                                            1024L
                                        ) {
                                            "ZIP toplam açılmış boyutu 250 MB sınırını aşıyor."
                                        }

                                        output.write(
                                            buffer,
                                            0,
                                            read
                                        )
                                    }
                                }
                        }
                    }

                    zip.closeEntry()
                }
            }
        }

        val meta =
            metadata
                ?: error(
                    "Bu dosyada AppForge proje bilgisi bulunamadı."
                )

        val draft =
            deserialize(
                meta,
                destination
            )

        return BackupImportResult(
            draft =
                draft,
            importedFolder =
                destination.takeIf {
                    draft.sourceMode ==
                    SourceMode.LOCAL
                }
        )
    }

    private fun addDirectory(
        zip: ZipOutputStream,
        root: File,
        current: File,
        prefix: String
    ) {
        current.listFiles()
            ?.sortedBy {
                it.name
            }
            ?.forEach {
                file ->
                val relative =
                    file.relativeTo(root)
                        .invariantSeparatorsPath

                val entryName =
                    prefix +
                    relative

                if (file.isDirectory) {
                    zip.putNextEntry(
                        ZipEntry(
                            "$entryName/"
                        )
                    )
                    zip.closeEntry()

                    addDirectory(
                        zip,
                        root,
                        file,
                        prefix
                    )
                } else {
                    zip.putNextEntry(
                        ZipEntry(
                            entryName
                        )
                    )

                    file.inputStream()
                        .use {
                            input ->
                            input.copyTo(
                                zip
                            )
                        }

                    zip.closeEntry()
                }
            }
    }

    private fun serialize(
        d: ProjectDraft
    ) =
        JSONObject().apply {
            put(
                "formatVersion",
                1
            )
            put(
                "product",
                "AppForge Studio"
            )
            put(
                "appName",
                d.appName
            )
            put(
                "packageName",
                d.packageName
            )
            put(
                "sourceMode",
                d.sourceMode.name
            )
            put(
                "webUrl",
                d.webUrl
            )
            put(
                "versionName",
                d.versionName
            )
            put(
                "versionCode",
                d.versionCode
            )
            put(
                "autoVersionCode",
                d.autoVersionCode
            )
            put(
                "buildOutput",
                d.buildOutput
            )
            put(
                "orientation",
                d.orientation
            )
            put(
                "appCategory",
                d.appCategory
            )
            put(
                "primaryColor",
                d.primaryColor
            )
            put(
                "backgroundColor",
                d.backgroundColor
            )
            put(
                "statusBarColor",
                d.statusBarColor
            )
            put(
                "navigationBarColor",
                d.navigationBarColor
            )
            put(
                "splashEnabled",
                d.splashEnabled
            )
            put(
                "splashText",
                d.splashText
            )
            put(
                "fileUpload",
                d.fileUpload
            )
            put(
                "downloads",
                d.downloads
            )
            put(
                "fullscreen",
                d.fullscreen
            )
            put(
                "notifications",
                d.notifications
            )
            put(
                "camera",
                d.camera
            )
            put(
                "location",
                d.location
            )
            put(
                "offlineCache",
                d.offlineCache
            )

            put(
                "webJavaScriptEnabled",
                d.webJavaScriptEnabled
            )
            put(
                "webDomStorageEnabled",
                d.webDomStorageEnabled
            )
            put(
                "webZoomEnabled",
                d.webZoomEnabled
            )
            put(
                "webWideViewPortEnabled",
                d.webWideViewPortEnabled
            )
            put(
                "webOverviewModeEnabled",
                d.webOverviewModeEnabled
            )
            put(
                "webMediaAutoplayEnabled",
                d.webMediaAutoplayEnabled
            )
            put(
                "webMixedContentAllowed",
                d.webMixedContentAllowed
            )

            put(
                "deepLinkEnabled",
                d.deepLinkEnabled
            )
            put(
                "deepLinkScheme",
                d.deepLinkScheme
            )
            put(
                "deepLinkHost",
                d.deepLinkHost
            )
            put(
                "deepLinkPathPrefix",
                d.deepLinkPathPrefix
            )
            put(
                "javascriptBridge",
                d.javascriptBridge
            )
            put(
                "remoteBridgeAllowed",
                d.remoteBridgeAllowed
            )
            put(
                "shareBridge",
                d.shareBridge
            )
            put(
                "clipboardBridge",
                d.clipboardBridge
            )
            put(
                "vibrationBridge",
                d.vibrationBridge
            )
            put(
                "mediaPlayerBridge",
                d.mediaPlayerBridge
            )
            put(
                "qrScanner",
                d.qrScanner
            )
            put(
                "admobEnabled",
                d.admobEnabled
            )
            put(
                "admobAppId",
                d.admobAppId
            )
            put(
                "admobBannerUnitId",
                d.admobBannerUnitId
            )
            put(
                "admobInterstitialUnitId",
                d.admobInterstitialUnitId
            )
            put(
                "admobRewardedUnitId",
                d.admobRewardedUnitId
            )
            put(
                "umpConsentEnabled",
                d.umpConsentEnabled
            )
            put(
                "billingEnabled",
                d.billingEnabled
            )
            put(
                "billingProductIds",
                d.billingProductIds
            )
            put(
                "billingSubscriptionIds",
                d.billingSubscriptionIds
            )
            put(
                "consumableProductIds",
                d.consumableProductIds
            )
            put(
                "removeAdsProductId",
                d.removeAdsProductId
            )
            put(
                "purchaseVerificationUrl",
                d.purchaseVerificationUrl
            )
            put(
                "firebaseAnalyticsEnabled",
                d.firebaseAnalyticsEnabled
            )
            put(
                "firebaseCrashlyticsEnabled",
                d.firebaseCrashlyticsEnabled
            )
            put(
                "buildServiceUrl",
                d.buildServiceUrl
            )

            // Deliberately excluded:
            // keystore passwords and keystore files,
            // Build API keys,
            // Firebase secret/config file URI.
        }

    private fun deserialize(
        o: JSONObject,
        sourceRoot: File
    ): ProjectDraft {
        val sourceMode =
            runCatching {
                SourceMode.valueOf(
                    o.optString(
                        "sourceMode",
                        "LOCAL"
                    )
                )
            }.getOrDefault(
                SourceMode.LOCAL
            )

        val startPage =
            if (
                sourceMode ==
                SourceMode.LOCAL
            ) {
                listOf(
                    "index.html",
                    "index.htm"
                )
                    .map {
                        File(
                            sourceRoot,
                            it
                        )
                    }
                    .firstOrNull {
                        it.exists()
                    }
                    ?: sourceRoot
                        .walkTopDown()
                        .firstOrNull {
                            it.isFile &&
                                (
                                    it.name.equals(
                                        "index.html",
                                        true
                                    ) ||
                                    it.name.equals(
                                        "index.htm",
                                        true
                                    )
                                )
                        }
            } else {
                null
            }

        return ProjectDraft(
            appName =
                o.optString(
                    "appName"
                ),
            packageName =
                o.optString(
                    "packageName",
                    "com.example.myapp"
                ),
            sourceMode =
                sourceMode,
            sourceLabel =
                if (
                    sourceMode ==
                    SourceMode.LOCAL
                ) {
                    "AppForge yedeği"
                } else {
                    ""
                },
            importedFolder =
                if (
                    sourceMode ==
                    SourceMode.LOCAL
                ) {
                    sourceRoot
                        .absolutePath
                } else {
                    null
                },
            startPage =
                startPage
                    ?.absolutePath,
            webUrl =
                o.optString(
                    "webUrl"
                ),
            versionName =
                o.optString(
                    "versionName",
                    "1.0.0"
                ),
            versionCode =
                o.optInt(
                    "versionCode",
                    1
                ),
            autoVersionCode =
                o.optBoolean(
                    "autoVersionCode",
                    false
                ),
            buildOutput =
                o.optString(
                    "buildOutput",
                    "both"
                ),
            orientation =
                o.optString(
                    "orientation",
                    "unspecified"
                ),
            appCategory =
                o.optString(
                    "appCategory",
                    "auto"
                ),
            primaryColor =
                o.optString(
                    "primaryColor",
                    "#6B7CFF"
                ),
            backgroundColor =
                o.optString(
                    "backgroundColor",
                    "#07101F"
                ),
            statusBarColor =
                o.optString(
                    "statusBarColor",
                    "#07101F"
                ),
            navigationBarColor =
                o.optString(
                    "navigationBarColor",
                    "#07101F"
                ),
            splashEnabled =
                o.optBoolean(
                    "splashEnabled",
                    true
                ),
            splashText =
                o.optString(
                    "splashText"
                ),
            signingMode =
                SigningMode.DEBUG,
            fileUpload =
                o.optBoolean(
                    "fileUpload",
                    true
                ),
            downloads =
                o.optBoolean(
                    "downloads",
                    true
                ),
            fullscreen =
                o.optBoolean(
                    "fullscreen",
                    false
                ),
            notifications =
                o.optBoolean(
                    "notifications",
                    false
                ),
            camera =
                o.optBoolean(
                    "camera",
                    false
                ),
            location =
                o.optBoolean(
                    "location",
                    false
                ),
            offlineCache =
                o.optBoolean(
                    "offlineCache",
                    true
                ),

            webJavaScriptEnabled =
                o.optBoolean(
                    "webJavaScriptEnabled",
                    true
                ),
            webDomStorageEnabled =
                o.optBoolean(
                    "webDomStorageEnabled",
                    true
                ),
            webZoomEnabled =
                o.optBoolean(
                    "webZoomEnabled",
                    true
                ),
            webWideViewPortEnabled =
                o.optBoolean(
                    "webWideViewPortEnabled",
                    true
                ),
            webOverviewModeEnabled =
                o.optBoolean(
                    "webOverviewModeEnabled",
                    true
                ),
            webMediaAutoplayEnabled =
                o.optBoolean(
                    "webMediaAutoplayEnabled",
                    true
                ),
            webMixedContentAllowed =
                o.optBoolean(
                    "webMixedContentAllowed",
                    false
                ),

            deepLinkEnabled =
                o.optBoolean(
                    "deepLinkEnabled",
                    false
                ),
            deepLinkScheme =
                o.optString(
                    "deepLinkScheme",
                    "https"
                ),
            deepLinkHost =
                o.optString(
                    "deepLinkHost"
                ),
            deepLinkPathPrefix =
                o.optString(
                    "deepLinkPathPrefix",
                    "/"
                ),
            javascriptBridge =
                o.optBoolean(
                    "javascriptBridge",
                    true
                ),
            remoteBridgeAllowed =
                o.optBoolean(
                    "remoteBridgeAllowed",
                    false
                ),
            shareBridge =
                o.optBoolean(
                    "shareBridge",
                    true
                ),
            clipboardBridge =
                o.optBoolean(
                    "clipboardBridge",
                    true
                ),
            vibrationBridge =
                o.optBoolean(
                    "vibrationBridge",
                    true
                ),
            mediaPlayerBridge =
                o.optBoolean(
                    "mediaPlayerBridge",
                    false
                ),
            qrScanner =
                o.optBoolean(
                    "qrScanner",
                    false
                ),
            admobEnabled =
                o.optBoolean(
                    "admobEnabled",
                    false
                ),
            admobAppId =
                o.optString(
                    "admobAppId"
                ),
            admobBannerUnitId =
                o.optString(
                    "admobBannerUnitId"
                ),
            admobInterstitialUnitId =
                o.optString(
                    "admobInterstitialUnitId"
                ),
            admobRewardedUnitId =
                o.optString(
                    "admobRewardedUnitId"
                ),
            umpConsentEnabled =
                o.optBoolean(
                    "umpConsentEnabled",
                    false
                ),
            billingEnabled =
                o.optBoolean(
                    "billingEnabled",
                    false
                ),
            billingProductIds =
                o.optString(
                    "billingProductIds"
                ),
            billingSubscriptionIds =
                o.optString(
                    "billingSubscriptionIds"
                ),
            consumableProductIds =
                o.optString(
                    "consumableProductIds"
                ),
            removeAdsProductId =
                o.optString(
                    "removeAdsProductId"
                ),
            purchaseVerificationUrl =
                o.optString(
                    "purchaseVerificationUrl"
                ),
            firebaseAnalyticsEnabled =
                o.optBoolean(
                    "firebaseAnalyticsEnabled",
                    false
                ),
            firebaseCrashlyticsEnabled =
                o.optBoolean(
                    "firebaseCrashlyticsEnabled",
                    false
                ),
            buildServiceUrl =
                o.optString(
                    "buildServiceUrl",
                    "http://10.0.2.2:8080"
                )
        )
    }
}
