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

data class DeletedProject(
    val id: String,
    val name: String,
    val packageName: String,
    val deletedAt: Long,
    val purgeAt: Long,
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
    val exeUrl: String? = null,
    val buildNo: Long? = null
)

object ProjectLibrary {
    private const val TRASH_RETENTION_MS =
        30L * 24L * 60L * 60L * 1000L

    @Volatile
    private var activeAccountScope =
        "guest"

    private fun safeAccountScope(
        userId: String?
    ): String {
        val value =
            userId
                ?.trim()
                ?.lowercase()
                .orEmpty()

        if (
            value.isBlank()
        ) {
            return "guest"
        }

        return value
            .replace(
                Regex(
                    "[^a-z0-9_-]"
                ),
                "_"
            )
            .take(
                96
            )
            .ifBlank {
                "guest"
            }
    }

    fun setAccountScope(
        context: Context,
        userId: String?
    ) {
        val next =
            safeAccountScope(
                userId
            )

        if (
            activeAccountScope ==
            next
        ) {
            return
        }

        activeAccountScope =
            next

        if (
            !userId
                .isNullOrBlank()
        ) {
            migrateLegacyFilesOnce(
                context
            )
        }
    }

    private fun scopedFile(
        context: Context,
        baseName: String
    ): File {
        val stem =
            baseName
                .removeSuffix(
                    ".json"
                )

        return File(
            context.filesDir,
            "${stem}_${activeAccountScope}.json"
        )
    }

    private fun migrateLegacyFilesOnce(
        context: Context
    ) {
        val marker =
            File(
                context.filesDir,
                "account_scoped_library_v1.done"
            )

        if (
            marker.exists()
        ) {
            return
        }

        listOf(
            "project_library.json",
            "project_trash.json",
            "build_history.json",
            "free_project_slots.json"
        ).forEach {
            baseName ->

            val legacy =
                File(
                    context.filesDir,
                    baseName
                )

            val target =
                scopedFile(
                    context,
                    baseName
                )

            if (
                legacy.isFile &&
                !target.exists()
            ) {
                runCatching {
                    legacy.copyTo(
                        target,
                        overwrite = false
                    )

                    legacy.delete()
                }
            }
        }

        marker.writeText(
            activeAccountScope
        )
    }

    private fun projectFile(
        context: Context
    ) =
        scopedFile(
            context,
            "project_library.json"
        )

    private fun trashFile(
        context: Context
    ) =
        scopedFile(
            context,
            "project_trash.json"
        )

    private fun buildFile(
        context: Context
    ) =
        scopedFile(
            context,
            "build_history.json"
        )

    private fun slotFile(
        context: Context
    ) =
        scopedFile(
            context,
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

    fun cloneProject(
        context: Context,
        id: String
    ): SavedProject? {

        val original =
            restore(
                context,
                id
            ) ?: return null

        val existing =
            load(
                context
            )

        val baseName =
            original.appName
                .ifBlank {
                    "Adsız Proje"
                }

        var cloneName =
            "$baseName Kopya"

        var index =
            2

        while (
            existing.any {
                it.name.equals(
                    cloneName,
                    ignoreCase = true
                )
            }
        ) {
            cloneName =
                "$baseName Kopya $index"

            index++
        }

        val cloneId =
            UUID.randomUUID()
                .toString()

        var clonedFolder =
            original.importedFolder

        var clonedStartPage =
            original.startPage

        if (
            original.sourceMode ==
            SourceMode.LOCAL
        ) {
            val sourceFolder =
                original.importedFolder
                    ?.let(::File)
                    ?.takeIf {
                        it.isDirectory
                    }

            if (
                sourceFolder != null
            ) {
                val destination =
                    File(
                        context.filesDir,
                        "projects/clone_" +
                            cloneId.replace(
                                "-",
                                ""
                            )
                    )

                destination
                    .deleteRecursively()

                val copied =
                    sourceFolder
                        .copyRecursively(
                            target =
                                destination,
                            overwrite =
                                true
                        )

                if (copied) {
                    clonedFolder =
                        destination.absolutePath

                    val oldStart =
                        original.startPage
                            ?.let(::File)

                    val relativeStart =
                        if (
                            oldStart != null &&
                            oldStart.isFile
                        ) {
                            runCatching {
                                oldStart
                                    .relativeTo(
                                        sourceFolder
                                    )
                                    .path
                            }.getOrNull()
                        } else {
                            null
                        }

                    clonedStartPage =
                        relativeStart
                            ?.let {
                                File(
                                    destination,
                                    it
                                )
                            }
                            ?.takeIf {
                                it.isFile
                            }
                            ?.absolutePath
                            ?: File(
                                destination,
                                "index.html"
                            )
                                .takeIf {
                                    it.isFile
                                }
                                ?.absolutePath
                            ?: destination
                                .walkTopDown()
                                .firstOrNull {
                                    it.isFile &&
                                    it.name.equals(
                                        "index.html",
                                        true
                                    )
                                }
                                ?.absolutePath
                }
            }
        }

        val clone =
            original.copy(
                appName =
                    cloneName,
                importedFolder =
                    clonedFolder,
                startPage =
                    clonedStartPage
            )

        save(
            context =
                context,
            draft =
                clone,
            existingId =
                cloneId
        )

        return load(
            context
        ).firstOrNull {
            it.id ==
                cloneId
        }
    }

    fun load(context: Context): List<SavedProject> {
        purgeExpiredTrash(context)

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
            minSdk = obj.optInt("minSdk", 26),
            targetSdk = obj.optInt("targetSdk", 37),
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
            appCategory =
                obj.optString(
                    "appCategory",
                    "auto"
                ),
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
            microphone = obj.optBoolean("microphone", false),
            location = obj.optBoolean("location", false),
            networkState = obj.optBoolean("networkState", true),
            wakeLock = obj.optBoolean("wakeLock", false),
            nfc = obj.optBoolean("nfc", false),
            additionalPermissions =
                obj.optJSONArray("additionalPermissions")
                    ?.let { array ->
                        buildSet {
                            for (index in 0 until array.length()) {
                                array.optString(index)
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }
                    }
                    ?: emptySet(),
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
        val active =
            load(context)

        val project =
            active.firstOrNull {
                it.id == id
            } ?: return

        val now =
            System.currentTimeMillis()

        val trash =
            loadTrashInternal(context)
                .filterNot {
                    it.id == id
                }
                .toMutableList()

        trash.add(
            0,
            DeletedProject(
                id = project.id,
                name = project.name,
                packageName = project.packageName,
                deletedAt = now,
                purgeAt = now + TRASH_RETENTION_MS,
                json = project.json
            )
        )

        persistTrash(
            context,
            trash
        )

        persistProjects(
            context,
            active.filterNot {
                it.id == id
            }
        )
    }

    fun loadTrash(
        context: Context
    ): List<DeletedProject> {
        purgeExpiredTrash(context)

        return loadTrashInternal(context)
            .sortedByDescending {
                it.deletedAt
            }
    }

    fun restoreDeleted(
        context: Context,
        id: String
    ): Boolean {
        val trash =
            loadTrash(context)

        val deleted =
            trash.firstOrNull {
                it.id == id
            } ?: return false

        val active =
            load(context)
                .filterNot {
                    it.id == id
                }
                .toMutableList()

        val restoredJson =
            JSONObject(
                deleted.json
            ).apply {
                put(
                    "updatedAt",
                    System.currentTimeMillis()
                )
            }

        active.add(
            SavedProject(
                id = deleted.id,
                name = deleted.name,
                packageName = deleted.packageName,
                updatedAt =
                    restoredJson.optLong(
                        "updatedAt"
                    ),
                json = restoredJson.toString()
            )
        )

        persistProjects(
            context,
            active
        )

        persistTrash(
            context,
            trash.filterNot {
                it.id == id
            }
        )

        return true
    }

    fun deletePermanently(
        context: Context,
        id: String
    ) {
        val current =
            loadTrashInternal(context)

        val deleted =
            current.firstOrNull {
                it.id == id
            }

        persistTrash(
            context,
            current
                .filterNot {
                    it.id == id
                }
        )

        deleted?.let {
            cleanupProjectFiles(
                context,
                it.json
            )
        }
    }

    fun saveBuild(
        context: Context,
        buildId: String,
        draft: ProjectDraft,
        status: String,
        apkUrl: String?,
        aabUrl: String?,
        exeUrl: String? = null,
        buildNo: Long? = null
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
                exeUrl = exeUrl,
                buildNo = buildNo
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
                    put("buildNo", b.buildNo)
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
                            exeUrl = o.optString("exeUrl").takeIf { it.isNotBlank() && it != "null" },
                            buildNo =
                                o.optLong(
                                    "buildNo",
                                    0L
                                ).takeIf {
                                    it > 0L
                                }
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

    private fun loadTrashInternal(
        context: Context
    ): List<DeletedProject> {
        val file =
            trashFile(context)

        if (!file.exists()) {
            return emptyList()
        }

        return runCatching {
            val array =
                JSONArray(
                    file.readText()
                )

            buildList {
                for (
                    index in 0 until
                    array.length()
                ) {
                    val item =
                        array.getJSONObject(
                            index
                        )

                    val project =
                        item.getJSONObject(
                            "project"
                        )

                    add(
                        DeletedProject(
                            id = project.getString("id"),
                            name = project.optString("appName", "Adsız Proje"),
                            packageName = project.optString("packageName", ""),
                            deletedAt = item.optLong("deletedAt"),
                            purgeAt = item.optLong("purgeAt"),
                            json = project.toString()
                        )
                    )
                }
            }
        }.getOrDefault(
            emptyList()
        )
    }

    private fun persistTrash(
        context: Context,
        projects: List<DeletedProject>
    ) {
        val array =
            JSONArray()

        projects.forEach {
            deleted ->

            array.put(
                JSONObject().apply {
                    put("deletedAt", deleted.deletedAt)
                    put("purgeAt", deleted.purgeAt)
                    put("project", JSONObject(deleted.json))
                }
            )
        }

        trashFile(context)
            .writeText(
                array.toString(2)
            )
    }

    private fun purgeExpiredTrash(
        context: Context,
        now: Long = System.currentTimeMillis()
    ) {
        val current =
            loadTrashInternal(context)

        val retained =
            current.filter {
                it.purgeAt > now
            }

        if (
            retained.size != current.size
        ) {
            persistTrash(
                context,
                retained
            )

            current
                .filter {
                    it.purgeAt <= now
                }
                .forEach {
                    cleanupProjectFiles(
                        context,
                        it.json
                    )
                }
        }
    }

    private fun cleanupProjectFiles(
        context: Context,
        projectJson: String
    ) {
        val project =
            runCatching {
                JSONObject(
                    projectJson
                )
            }.getOrNull()
                ?: return

        val protectedProjects =
            buildList {
                addAll(
                    runCatching {
                        val file =
                            projectFile(context)

                        if (!file.exists()) {
                            emptyList()
                        } else {
                            val array =
                                JSONArray(
                                    file.readText()
                                )

                            buildList {
                                for (
                                    index in 0 until
                                        array.length()
                                ) {
                                    add(
                                        array.getJSONObject(index)
                                            .toString()
                                    )
                                }
                            }
                        }
                    }.getOrDefault(
                        emptyList()
                    )
                )
                addAll(
                    loadTrashInternal(context).map {
                        it.json
                    }
                )
            }

        fun isStillReferenced(
            field: String,
            value: String
        ): Boolean =
            protectedProjects.any {
                candidate ->

                runCatching {
                    val candidateJson =
                        JSONObject(candidate)

                    candidateJson.optString("id") !=
                        project.optString("id") &&
                        candidateJson.optString(field) == value
                }.getOrDefault(false)
            }

        val allowedProjectRoot =
            File(
                context.filesDir,
                "projects"
            ).canonicalFile

        project
            .optString(
                "importedFolder"
            )
            .takeIf {
                it.isNotBlank() &&
                    it != "null"
            }
            ?.let {
                path ->

                if (
                    isStillReferenced(
                        "importedFolder",
                        path
                    )
                ) {
                    return@let
                }

                runCatching {
                    val target =
                        File(path)
                            .canonicalFile

                    if (
                        target.path.startsWith(
                            allowedProjectRoot.path +
                                File.separator
                        )
                    ) {
                        target.deleteRecursively()
                    }
                }
            }

        val allowedIconRoot =
            File(
                context.filesDir,
                "prepared-icons"
            ).canonicalFile

        project
            .optString(
                "iconUri"
            )
            .takeIf {
                it.startsWith(
                    "file:",
                    true
                )
            }
            ?.let {
                value ->

                if (
                    isStillReferenced(
                        "iconUri",
                        value
                    )
                ) {
                    return@let
                }

                runCatching {
                    val target =
                        File(
                            android.net.Uri
                                .parse(value)
                                .path
                                .orEmpty()
                        ).canonicalFile

                    if (
                        target.isFile &&
                        target.path.startsWith(
                            allowedIconRoot.path +
                                File.separator
                        )
                    ) {
                        target.delete()
                    }
                }
            }
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
            put("minSdk", d.minSdk)
            put("targetSdk", d.targetSdk)

            put("orientation", d.orientation)
            put("primaryColor", d.primaryColor)
            put("backgroundColor", d.backgroundColor)
            put("statusBarColor", d.statusBarColor)
            put("navigationBarColor", d.navigationBarColor)
            put("splashEnabled", d.splashEnabled)
            put("splashText", d.splashText)
            put("iconUri", d.iconUri)
            put("iconName", d.iconName)
            put("appCategory", d.appCategory)

            put("signingMode", d.signingMode.name)
            put("keystoreUri", d.keystoreUri)
            put("keystoreName", d.keystoreName)
            put("keyAlias", d.keyAlias)

            put("fileUpload", d.fileUpload)
            put("downloads", d.downloads)
            put("fullscreen", d.fullscreen)
            put("notifications", d.notifications)
            put("camera", d.camera)
            put("microphone", d.microphone)
            put("location", d.location)
            put("networkState", d.networkState)
            put("wakeLock", d.wakeLock)
            put("nfc", d.nfc)
            put(
                "additionalPermissions",
                JSONArray(d.additionalPermissions.sorted())
            )
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
