package com.appforge.studio.io

import android.content.Context
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.DEFAULT_BUILD_SERVICE_URL
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SavedProject(
    val id: String,
    val name: String,
    val packageName: String,
    val updatedAt: Long,
    val json: String
)

data class SavedBuild(
    val id: String,
    val projectName: String,
    val packageName: String,
    val status: String,
    val createdAt: Long,
    val apkUrl: String?,
    val aabUrl: String?,
    val exeUrl: String? = null
)

object ProjectLibrary {
    private fun projectFile(context: Context) =
        File(context.filesDir, "project_library.json")

    private fun buildFile(context: Context) =
        File(context.filesDir, "build_history.json")


    private fun slotFile(
        context: Context
    ) =
        File(
            context.filesDir,
            "free_project_slots.json"
        )

    fun freeProjectSlotsUsed(
        context: Context
    ): Int =
        loadFreeProjectSlots(
            context
        ).size

    fun hasClaimedFreeProject(
        context: Context,
        packageName: String
    ): Boolean =
        loadFreeProjectSlots(
            context
        ).contains(
            packageName.trim()
        )

    fun claimFreeProjectSlot(
        context: Context,
        packageName: String,
        limit: Int = 5
    ): Boolean {
        val safePackage =
            packageName.trim()

        if (
            safePackage.isBlank()
        ) {
            return false
        }

        val slots =
            loadFreeProjectSlots(
                context
            ).toMutableSet()

        if (
            slots.contains(
                safePackage
            )
        ) {
            return true
        }

        if (
            slots.size >=
            limit
        ) {
            return false
        }

        slots.add(
            safePackage
        )

        val arr =
            JSONArray()

        slots
            .sorted()
            .forEach {
                arr.put(it)
            }

        slotFile(context)
            .writeText(
                arr.toString(2)
            )

        return true
    }

    private fun loadFreeProjectSlots(
        context: Context
    ): Set<String> {
        val file =
            slotFile(
                context
            )

        if (!file.exists()) {
            return emptySet()
        }

        return runCatching {
            val arr =
                JSONArray(
                    file.readText()
                )

            buildSet {
                for (
                    i in 0 until
                    arr.length()
                ) {
                    val value =
                        arr
                            .optString(i)
                            .trim()

                    if (
                        value.isNotBlank()
                    ) {
                        add(value)
                    }
                }
            }
        }.getOrDefault(
            emptySet()
        )
    }

    fun save(context: Context, draft: ProjectDraft, existingId: String? = null): String {
        val list = load(context).toMutableList()
        val id = existingId ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val obj = serializeProject(id, now, draft)

        val index = list.indexOfFirst { it.id == id }
        val saved = SavedProject(
            id = id,
            name = draft.appName.ifBlank { "Adsız Proje" },
            packageName = draft.packageName,
            updatedAt = now,
            json = obj.toString()
        )

        if (index >= 0) list[index] = saved else list.add(saved)
        persistProjects(context, list)
        return id
    }

    fun load(context: Context): List<SavedProject> {
        val file = projectFile(context)
        if (!file.exists()) return emptyList()

        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        SavedProject(
                            id = obj.getString("id"),
                            name = obj.optString("appName", "Adsız Proje"),
                            packageName = obj.optString("packageName", ""),
                            updatedAt = obj.optLong("updatedAt", 0),
                            json = obj.toString()
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun restore(context: Context, id: String): ProjectDraft? {
        val saved = load(context).firstOrNull { it.id == id } ?: return null
        val obj = JSONObject(saved.json)

        val restoredPackageName =
            obj.optString(
                "packageName",
                "com.example.myapp"
            )

        /*
         * Yeni kayıtlar importedFolder/startPage saklar.
         *
         * Eski AppForge Studio kayıtlarında bu alanlar yoktu.
         * Bu nedenle eski projeler için filesDir/projects altında
         * packageName'den üretilmiş klasörü otomatik olarak buluyoruz.
         */
        val storedFolder =
            obj.optString(
                "importedFolder"
            )
                .takeIf {
                    it.isNotBlank() &&
                    it != "null"
                }
                ?.let {
                    File(it)
                }
                ?.takeIf {
                    it.isDirectory
                }

        val legacyFolder =
            File(
                context.filesDir,
                "projects/" +
                    restoredPackageName
                        .replace(
                            ".",
                            "_"
                        )
            )
                .takeIf {
                    it.isDirectory
                }

        val restoredFolder =
            storedFolder
                ?: legacyFolder

        val storedStartPage =
            obj.optString(
                "startPage"
            )
                .takeIf {
                    it.isNotBlank() &&
                    it != "null"
                }
                ?.let {
                    File(it)
                }
                ?.takeIf {
                    it.isFile
                }

        val detectedStartPage =
            restoredFolder
                ?.let {
                    folder ->

                    File(
                        folder,
                        "index.html"
                    )
                        .takeIf {
                            it.isFile
                        }
                        ?: folder
                            .walkTopDown()
                            .firstOrNull {
                                file ->
                                file.isFile &&
                                file.name.equals(
                                    "index.html",
                                    true
                                )
                            }
                }

        val restoredStartPage =
            storedStartPage
                ?: detectedStartPage

        return ProjectDraft(
            appName = obj.optString("appName"),
            packageName = restoredPackageName,
            sourceLabel =
                obj.optString(
                    "sourceLabel"
                ).ifBlank {
                    restoredStartPage
                        ?.name
                        ?: ""
                },
            importedFolder =
                restoredFolder
                    ?.absolutePath,
            startPage =
                restoredStartPage
                    ?.absolutePath,
            sourceMode = runCatching {
                SourceMode.valueOf(obj.optString("sourceMode", "LOCAL"))
            }.getOrDefault(SourceMode.LOCAL),
            sourceTechnology =
                obj.optString(
                    "sourceTechnology",
                    "web-static"
                ),
            sourceTechnologyLabel =
                obj.optString(
                    "sourceTechnologyLabel",
                    "HTML / CSS / JavaScript"
                ),
            sourceBuildEngine =
                obj.optString(
                    "sourceBuildEngine",
                    "webview-static"
                ),
            sourceBuildReady =
                obj.optBoolean(
                    "sourceBuildReady",
                    true
                ),
            webUrl = obj.optString("webUrl"),
            versionName = obj.optString("versionName", "1.0.0"),
            versionCode = obj.optInt("versionCode", 1),
            autoVersionCode = obj.optBoolean("autoVersionCode", false),
            buildOutput = obj.optString("buildOutput", "both"),
            orientation = obj.optString("orientation", "unspecified"),
            primaryColor = obj.optString("primaryColor", "#6B7CFF"),
            backgroundColor = obj.optString("backgroundColor", "#07101F"),
            statusBarColor = obj.optString("statusBarColor", "#07101F"),
            navigationBarColor = obj.optString("navigationBarColor", "#07101F"),
            splashEnabled = obj.optBoolean("splashEnabled", true),
            splashText = obj.optString("splashText"),
            iconUri =
                obj.optString(
                    "iconUri"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            iconName = obj.optString("iconName"),
            signingMode =
                runCatching {
                    SigningMode.valueOf(
                        obj.optString(
                            "signingMode",
                            "DEBUG"
                        )
                    )
                }.getOrDefault(
                    SigningMode.DEBUG
                ),
            keystoreUri =
                obj.optString(
                    "keystoreUri"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            keystoreName =
                obj.optString(
                    "keystoreName"
                ),
            keyAlias =
                obj.optString(
                    "keyAlias"
                ),
            fileUpload = obj.optBoolean("fileUpload", true),
            downloads = obj.optBoolean("downloads", true),
            fullscreen = obj.optBoolean("fullscreen", false),
            notifications = obj.optBoolean("notifications", false),
            camera = obj.optBoolean("camera", false),
            location = obj.optBoolean("location", false),
            offlineCache = obj.optBoolean("offlineCache", true),

            webJavaScriptEnabled =
                obj.optBoolean(
                    "webJavaScriptEnabled",
                    true
                ),
            webDomStorageEnabled =
                obj.optBoolean(
                    "webDomStorageEnabled",
                    true
                ),
            webZoomEnabled =
                obj.optBoolean(
                    "webZoomEnabled",
                    true
                ),
            webWideViewPortEnabled =
                obj.optBoolean(
                    "webWideViewPortEnabled",
                    true
                ),
            webOverviewModeEnabled =
                obj.optBoolean(
                    "webOverviewModeEnabled",
                    true
                ),
            webMediaAutoplayEnabled =
                obj.optBoolean(
                    "webMediaAutoplayEnabled",
                    true
                ),
            webMixedContentAllowed =
                obj.optBoolean(
                    "webMixedContentAllowed",
                    false
                ),

            deepLinkEnabled = obj.optBoolean("deepLinkEnabled", false),
            deepLinkScheme = obj.optString("deepLinkScheme", "https"),
            deepLinkHost = obj.optString("deepLinkHost"),
            deepLinkPathPrefix = obj.optString("deepLinkPathPrefix", "/"),
            javascriptBridge = obj.optBoolean("javascriptBridge", true),
            remoteBridgeAllowed = obj.optBoolean("remoteBridgeAllowed", false),
            shareBridge = obj.optBoolean("shareBridge", true),
            clipboardBridge = obj.optBoolean("clipboardBridge", true),
            vibrationBridge = obj.optBoolean("vibrationBridge", true),
            mediaPlayerBridge =
                obj.optBoolean(
                    "mediaPlayerBridge",
                    false
                ),
            qrScanner = obj.optBoolean("qrScanner", false),
            admobEnabled = obj.optBoolean("admobEnabled", false),
            admobAppId = obj.optString("admobAppId"),
            admobBannerUnitId = obj.optString("admobBannerUnitId"),
            admobInterstitialUnitId = obj.optString("admobInterstitialUnitId"),
            admobRewardedUnitId = obj.optString("admobRewardedUnitId"),
            umpConsentEnabled = obj.optBoolean("umpConsentEnabled", false),
            billingEnabled = obj.optBoolean("billingEnabled", false),
            billingProductIds = obj.optString("billingProductIds"),
            billingSubscriptionIds = obj.optString("billingSubscriptionIds"),
            consumableProductIds = obj.optString("consumableProductIds"),
            removeAdsProductId = obj.optString("removeAdsProductId"),
            purchaseVerificationUrl = obj.optString("purchaseVerificationUrl"),
            firebaseAnalyticsEnabled = obj.optBoolean("firebaseAnalyticsEnabled", false),
            firebaseCrashlyticsEnabled = obj.optBoolean("firebaseCrashlyticsEnabled", false),
            firebaseMessagingEnabled = obj.optBoolean("firebaseMessagingEnabled", false),
            firebaseConfigUri =
                obj.optString(
                    "firebaseConfigUri"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            firebaseConfigName =
                obj.optString(
                    "firebaseConfigName"
                ),
            buildServiceUrl =
                obj
                    .optString(
                        "buildServiceUrl",
                        DEFAULT_BUILD_SERVICE_URL
                    )
                    .trim()
                    .ifBlank {
                        DEFAULT_BUILD_SERVICE_URL
                    }
        )
    }

    fun delete(context: Context, id: String) {
        persistProjects(context, load(context).filterNot { it.id == id })
    }

    fun saveBuild(
        context: Context,
        buildId: String,
        draft: ProjectDraft,
        status: String,
        apkUrl: String?,
        aabUrl: String?,
        exeUrl: String? = null
    ) {
        val current = loadBuilds(context).toMutableList()
        current.removeAll { it.id == buildId }
        current.add(
            0,
            SavedBuild(
                id = buildId,
                projectName = draft.appName.ifBlank { "Adsız Proje" },
                packageName = draft.packageName,
                status = status,
                createdAt = System.currentTimeMillis(),
                apkUrl = apkUrl,
                aabUrl = aabUrl,
                exeUrl = exeUrl
            )
        )

        val arr = JSONArray()
        current.take(100).forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("projectName", b.projectName)
                    put("packageName", b.packageName)
                    put("status", b.status)
                    put("createdAt", b.createdAt)
                    put("apkUrl", b.apkUrl)
                    put("aabUrl", b.aabUrl)
                    put("exeUrl", b.exeUrl)
                }
            )
        }
        buildFile(context).writeText(arr.toString(2))
    }

    fun loadBuilds(context: Context): List<SavedBuild> {
        val file = buildFile(context)
        if (!file.exists()) return emptyList()

        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavedBuild(
                            id = o.getString("id"),
                            projectName = o.optString("projectName"),
                            packageName = o.optString("packageName"),
                            status = o.optString("status"),
                            createdAt = o.optLong("createdAt"),
                            apkUrl = o.optString("apkUrl").takeIf { it.isNotBlank() && it != "null" },
                            aabUrl = o.optString("aabUrl").takeIf { it.isNotBlank() && it != "null" },
                            exeUrl = o.optString("exeUrl").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    private fun persistProjects(context: Context, projects: List<SavedProject>) {
        val arr = JSONArray()
        projects.forEach { arr.put(JSONObject(it.json)) }
        projectFile(context).writeText(arr.toString(2))
    }

    private fun serializeProject(id: String, updatedAt: Long, d: ProjectDraft) =
        JSONObject().apply {
            put("id", id)
            put("updatedAt", updatedAt)
            put("appName", d.appName)
            put("packageName", d.packageName)
            put("sourceMode", d.sourceMode.name)
            put("sourceTechnology", d.sourceTechnology)
            put("sourceTechnologyLabel", d.sourceTechnologyLabel)
            put("sourceBuildEngine", d.sourceBuildEngine)
            put("sourceBuildReady", d.sourceBuildReady)

            // LOCAL kaynak uygulamanın özel depolamasına kopyalandığı için
            // bu yollar uygulama yeniden açıldığında güvenle kullanılabilir.
            put("sourceLabel", d.sourceLabel)
            put("importedFolder", d.importedFolder)
            put("startPage", d.startPage)

            put("webUrl", d.webUrl)
            put("versionName", d.versionName)
            put("versionCode", d.versionCode)
            put("autoVersionCode", d.autoVersionCode)
            put("buildOutput", d.buildOutput)

            put("orientation", d.orientation)
            put("primaryColor", d.primaryColor)
            put("backgroundColor", d.backgroundColor)
            put("statusBarColor", d.statusBarColor)
            put("navigationBarColor", d.navigationBarColor)
            put("splashEnabled", d.splashEnabled)
            put("splashText", d.splashText)
            put("iconUri", d.iconUri)
            put("iconName", d.iconName)

            put("signingMode", d.signingMode.name)
            put("keystoreUri", d.keystoreUri)
            put("keystoreName", d.keystoreName)
            put("keyAlias", d.keyAlias)

            put("fileUpload", d.fileUpload)
            put("downloads", d.downloads)
            put("fullscreen", d.fullscreen)
            put("notifications", d.notifications)
            put("camera", d.camera)
            put("location", d.location)
            put("offlineCache", d.offlineCache)

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

            put("deepLinkEnabled", d.deepLinkEnabled)
            put("deepLinkScheme", d.deepLinkScheme)
            put("deepLinkHost", d.deepLinkHost)
            put("deepLinkPathPrefix", d.deepLinkPathPrefix)

            put("javascriptBridge", d.javascriptBridge)
            put("remoteBridgeAllowed", d.remoteBridgeAllowed)
            put("shareBridge", d.shareBridge)
            put("clipboardBridge", d.clipboardBridge)
            put("vibrationBridge", d.vibrationBridge)
            put(
                "mediaPlayerBridge",
                d.mediaPlayerBridge
            )
            put("qrScanner", d.qrScanner)

            put("admobEnabled", d.admobEnabled)
            put("admobAppId", d.admobAppId)
            put("admobBannerUnitId", d.admobBannerUnitId)
            put("admobInterstitialUnitId", d.admobInterstitialUnitId)
            put("admobRewardedUnitId", d.admobRewardedUnitId)
            put("umpConsentEnabled", d.umpConsentEnabled)

            put("billingEnabled", d.billingEnabled)
            put("billingProductIds", d.billingProductIds)
            put("billingSubscriptionIds", d.billingSubscriptionIds)
            put("consumableProductIds", d.consumableProductIds)
            put("removeAdsProductId", d.removeAdsProductId)
            put("purchaseVerificationUrl", d.purchaseVerificationUrl)

            put("firebaseAnalyticsEnabled", d.firebaseAnalyticsEnabled)
            put("firebaseCrashlyticsEnabled", d.firebaseCrashlyticsEnabled)
            put("firebaseMessagingEnabled", d.firebaseMessagingEnabled)
            put("firebaseConfigUri", d.firebaseConfigUri)
            put("firebaseConfigName", d.firebaseConfigName)

            put("buildServiceUrl", d.buildServiceUrl)

            // Sensitive values intentionally excluded:
            // storePassword, keyPassword, buildApiKey
        }
}
