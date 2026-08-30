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


    fun exportAllProjectsToUri(
        context: Context,
        target: Uri
    ) {
        val projects =
            ProjectLibrary
                .load(
                    context
                )

        require(
            projects.isNotEmpty()
        ) {
            "Dışa aktarılacak proje yok."
        }

        val output =
            context.contentResolver
                .openOutputStream(
                    target
                )
                ?: error(
                    "ZIP hedefi açılamadı."
                )

        output.use {
            stream ->

            ZipOutputStream(
                stream
            ).use {
                zip ->

                val manifest =
                    JSONObject()
                        .apply {
                            put(
                                "formatVersion",
                                1
                            )

                            put(
                                "product",
                                "AppForge Studio"
                            )

                            put(
                                "type",
                                "project-library"
                            )

                            put(
                                "projectCount",
                                projects.size
                            )

                            put(
                                "exportedAt",
                                System.currentTimeMillis()
                            )
                        }

                putText(
                    zip,
                    "appforge-library.json",
                    manifest
                        .toString(2)
                )

                projects.forEach {
                    saved ->

                    val draft =
                        ProjectLibrary
                            .restore(
                                context,
                                saved.id
                            )
                            ?: return@forEach

                    val folder =
                        "projects/" +
                            safeFolderName(
                                saved.name
                            ) +
                            "_" +
                            saved.id
                                .take(8) +
                            "/"

                    putText(
                        zip,
                        folder +
                            META,
                        serialize(
                            draft
                        ).toString(2)
                    )

                    if (
                        draft.sourceMode ==
                        SourceMode.LOCAL
                    ) {
                        val root =
                            draft
                                .importedFolder
                                ?.let(::File)

                        if (
                            root != null &&
                            root.isDirectory
                        ) {
                            addDirectory(
                                zip,
                                root,
                                root,
                                folder +
                                    "source/"
                            )
                        }
                    }
                }
            }
        }
    }


    fun exportAllAndroidProjectsToUri(
        context: Context,
        target: Uri
    ) {
        val projects =
            ProjectLibrary
                .load(
                    context
                )

        require(
            projects.isNotEmpty()
        ) {
            "Dışa aktarılacak proje yok."
        }

        val output =
            context.contentResolver
                .openOutputStream(
                    target
                )
                ?: error(
                    "ZIP hedefi açılamadı."
                )

        output.use {
            stream ->

            ZipOutputStream(
                stream
            ).use {
                zip ->

                projects.forEach {
                    saved ->

                    val draft =
                        ProjectLibrary
                            .restore(
                                context,
                                saved.id
                            )
                            ?: return@forEach

                    val projectFolder =
                        safeFolderName(
                            saved.name
                        ) +
                            "_" +
                            saved.id
                                .take(8)

                    writeAndroidProject(
                        zip =
                            zip,
                        base =
                            "$projectFolder/",
                        draft =
                            draft
                    )
                }
            }
        }
    }


    fun importManyFromUri(
        context: Context,
        source: Uri
    ): List<BackupImportResult> {
        var hasRootProject =
            false

        val libraryProjects =
            mutableListOf<
                Pair<String, JSONObject>
            >()

        val firstInput =
            context.contentResolver
                .openInputStream(source)
                ?: error(
                    "Yedek dosyası açılamadı."
                )

        firstInput.use {
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
                            .replace(
                                "\\",
                                "/"
                            )
                            .trimStart('/')

                    require(
                        safeName.length <=
                            300
                    ) {
                        "ZIP yolu çok uzun."
                    }

                    require(
                        !safeName.contains(
                            "../"
                        ) &&
                            safeName !=
                            ".."
                    ) {
                        "Güvensiz ZIP yolu."
                    }

                    if (
                        safeName ==
                            META
                    ) {
                        hasRootProject =
                            true
                    } else if (
                        safeName.startsWith(
                            "projects/"
                        ) &&
                            safeName.endsWith(
                                "/$META"
                            )
                    ) {
                        val bytes =
                            zip.readBytes()

                        require(
                            bytes.size <=
                                1_000_000
                        ) {
                            "Proje metadata dosyası çok büyük."
                        }

                        libraryProjects +=
                            safeName to
                                JSONObject(
                                    bytes.toString(
                                        Charsets.UTF_8
                                    )
                                )
                    }

                    zip.closeEntry()
                }
            }
        }

        if (
            hasRootProject
        ) {
            return listOf(
                importFromUri(
                    context,
                    source
                )
            )
        }

        require(
            libraryProjects.isNotEmpty()
        ) {
            "Bu ZIP içinde AppForge proje bilgisi bulunamadı."
        }

        return libraryProjects
            .distinctBy {
                it.first
            }
            .mapIndexed {
                index,
                project ->

                val metadataPath =
                    project.first

                val metadata =
                    project.second

                val projectPrefix =
                    metadataPath
                        .removeSuffix(
                            META
                        )

                val sourcePrefix =
                    projectPrefix +
                        "source/"

                val destination =
                    File(
                        context.filesDir,
                        "backup-imports/" +
                            UUID.randomUUID()
                                .toString() +
                            "_$index"
                    ).apply {
                        mkdirs()
                    }

                try {
                    val secondInput =
                        context.contentResolver
                            .openInputStream(
                                source
                            )
                            ?: error(
                                "Yedek dosyası tekrar açılamadı."
                            )

                    secondInput.use {
                        raw ->
                        ZipInputStream(
                            raw
                        ).use {
                            zip ->
                            var entries =
                                0

                            var totalBytes =
                                0L

                            val buffer =
                                ByteArray(
                                    64 *
                                        1024
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
                                        .replace(
                                            "\\",
                                            "/"
                                        )
                                        .trimStart('/')

                                if (
                                    safeName.startsWith(
                                        sourcePrefix
                                    ) &&
                                        safeName.length >
                                        sourcePrefix.length
                                ) {
                                    val relative =
                                        safeName
                                            .removePrefix(
                                                sourcePrefix
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

                                    if (
                                        entry.isDirectory
                                    ) {
                                        canonicalTarget
                                            .mkdirs()
                                    } else {
                                        canonicalTarget
                                            .parentFile
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
                                                        "Proje kaynakları 250 MB sınırını aşıyor."
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

                    val draft =
                        deserialize(
                            metadata,
                            destination
                        )

                    if (
                        draft.sourceMode !=
                            SourceMode.LOCAL
                    ) {
                        destination
                            .deleteRecursively()
                    }

                    BackupImportResult(
                        draft =
                            draft,
                        importedFolder =
                            destination.takeIf {
                                draft.sourceMode ==
                                    SourceMode.LOCAL
                            }
                    )
                } catch (
                    t: Throwable
                ) {
                    destination
                        .deleteRecursively()

                    throw t
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


    private fun safeFolderName(
        value: String
    ): String =
        value
            .trim()
            .replace(
                Regex(
                    "[^A-Za-z0-9._-]+"
                ),
                "_"
            )
            .trim('_')
            .ifBlank {
                "AppForgeProject"
            }


    private fun putText(
        zip: ZipOutputStream,
        path: String,
        text: String
    ) {
        zip.putNextEntry(
            ZipEntry(
                path
            )
        )

        zip.write(
            text.toByteArray(
                Charsets.UTF_8
            )
        )

        zip.closeEntry()
    }


    private fun xmlEscape(
        value: String
    ): String =
        value
            .replace(
                "&",
                "&amp;"
            )
            .replace(
                "\"",
                "&quot;"
            )
            .replace(
                "<",
                "&lt;"
            )
            .replace(
                ">",
                "&gt;"
            )


    private fun javaEscape(
        value: String
    ): String =
        value
            .replace(
                "\\",
                "\\\\"
            )
            .replace(
                "\"",
                "\\\""
            )
            .replace(
                "\r",
                ""
            )
            .replace(
                "\n",
                "\\n"
            )


    private fun writeAndroidProject(
        zip: ZipOutputStream,
        base: String,
        draft: ProjectDraft
    ) {
        val packageName =
            draft.packageName
                .ifBlank {
                    "com.appforge.exported"
                }

        val packagePath =
            packageName
                .replace(
                    ".",
                    "/"
                )

        val safeVersionName =
            draft.versionName
                .replace(
                    Regex(
                        "[^A-Za-z0-9._-]+"
                    ),
                    "_"
                )
                .ifBlank {
                    "1.0.0"
                }

        val appLabel =
            xmlEscape(
                draft.appName
                    .ifBlank {
                        "AppForge App"
                    }
            )

        val sourceRoot =
            draft.importedFolder
                ?.let(::File)
                ?.takeIf {
                    it.isDirectory
                }

        val localStart =
            if (
                draft.sourceMode ==
                    SourceMode.LOCAL &&
                sourceRoot != null
            ) {
                draft.startPage
                    ?.let(::File)
                    ?.takeIf {
                        it.isFile
                    }
                    ?.let {
                        runCatching {
                            it.relativeTo(
                                sourceRoot
                            ).invariantSeparatorsPath
                        }.getOrNull()
                    }
                    ?: "index.html"
            } else {
                null
            }

        val launchUrl =
            if (
                draft.sourceMode ==
                    SourceMode.LOCAL
            ) {
                "file:///android_asset/" +
                    (
                        localStart
                            ?: "index.html"
                    )
            } else {
                draft.webUrl
                    .ifBlank {
                        "about:blank"
                    }
            }

        val permissions =
            buildList {
                add(
                    "android.permission.INTERNET"
                )

                if (
                    draft.networkState
                ) {
                    add(
                        "android.permission.ACCESS_NETWORK_STATE"
                    )
                }

                if (
                    draft.camera
                ) {
                    add(
                        "android.permission.CAMERA"
                    )
                }

                if (
                    draft.microphone
                ) {
                    add(
                        "android.permission.RECORD_AUDIO"
                    )
                }

                if (
                    draft.location
                ) {
                    add(
                        "android.permission.ACCESS_FINE_LOCATION"
                    )
                }

                if (
                    draft.notifications
                ) {
                    add(
                        "android.permission.POST_NOTIFICATIONS"
                    )
                }

                if (
                    draft.wakeLock
                ) {
                    add(
                        "android.permission.WAKE_LOCK"
                    )
                }

                if (
                    draft.nfc
                ) {
                    add(
                        "android.permission.NFC"
                    )
                }

                draft.additionalPermissions
                    .forEach {
                        permission ->

                        val normalized =
                            permission
                                .trim()

                        if (
                            normalized.isNotBlank()
                        ) {
                            add(
                                if (
                                    normalized.contains(
                                        "."
                                    )
                                ) {
                                    normalized
                                } else {
                                    "android.permission.$normalized"
                                }
                            )
                        }
                    }
            }
                .distinct()

        val permissionXml =
            permissions
                .joinToString(
                    "\n"
                ) {
                    permission ->

                    """    <uses-permission android:name="${xmlEscape(permission)}" />"""
                }

        val orientationAttribute =
            when (
                draft.orientation
            ) {
                "portrait",
                "landscape" ->
                    """ android:screenOrientation="${draft.orientation}""""

                else ->
                    ""
            }

        putText(
            zip,
            base +
                "settings.gradle.kts",
            """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "${safeFolderName(draft.appName)}"
include(":app")
""".trimIndent() +
                "\n"
        )

        putText(
            zip,
            base +
                "build.gradle.kts",
            """
plugins {
    id("com.android.application") version "9.1.1" apply false
}
""".trimIndent() +
                "\n"
        )

        putText(
            zip,
            base +
                "gradle.properties",
            """
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
android.useAndroidX=true
""".trimIndent() +
                "\n"
        )

        putText(
            zip,
            base +
                "app/build.gradle.kts",
            """
plugins {
    id("com.android.application")
}

android {
    namespace = "$packageName"
    compileSdk = 37

    defaultConfig {
        applicationId = "$packageName"
        minSdk = ${draft.minSdk}
        targetSdk = ${draft.targetSdk}
        versionCode = ${draft.versionCode.coerceAtLeast(1)}
        versionName = "$safeVersionName"
    }
}
""".trimIndent() +
                "\n"
        )

        putText(
            zip,
            base +
                "app/src/main/AndroidManifest.xml",
            """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
$permissionXml

    <application
        android:allowBackup="true"
        android:label="$appLabel"
        android:usesCleartextTraffic="${draft.webMixedContentAllowed}">
        <activity
            android:name=".MainActivity"
            android:exported="true"$orientationAttribute>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimIndent() +
                "\n"
        )

        val mixedContentLine =
            if (
                draft.webMixedContentAllowed
            ) {
                """
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            );
        }
"""
            } else {
                ""
            }

        putText(
            zip,
            base +
                "app/src/main/java/" +
                packagePath +
                "/MainActivity.java",
            """
package $packageName;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView =
            new WebView(this);

        WebSettings settings =
            webView.getSettings();

        settings.setJavaScriptEnabled(${draft.webJavaScriptEnabled});
        settings.setDomStorageEnabled(${draft.webDomStorageEnabled});
        settings.setSupportZoom(${draft.webZoomEnabled});
        settings.setBuiltInZoomControls(${draft.webZoomEnabled});
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(${draft.webWideViewPortEnabled});
        settings.setLoadWithOverviewMode(${draft.webOverviewModeEnabled});
$mixedContentLine
        webView.setWebViewClient(
            new WebViewClient()
        );

        setContentView(
            webView
        );

        webView.loadUrl(
            "${javaEscape(launchUrl)}"
        );
    }
}
""".trimIndent() +
                "\n"
        )

        /*
         * AppForge ayarlarının tamamını da kaynak projeye ekle.
         * Böylece gelişmiş Bridge/Firebase/Billing gibi ayarlar
         * kaybolmaz ve daha sonra AppForge tarafından okunabilir.
         */
        putText(
            zip,
            base +
                META,
            serialize(
                draft
            ).toString(2)
        )

        putText(
            zip,
            base +
                "README_APPFORGE.txt",
            """
AppForge Studio Android Project Export

Bu klasör Android Studio ile açılabilir bir WebView kaynak projesidir.

AppForge proje ayarlarının tam kopyası:
$appLabel -> $META

Not:
Firebase, Billing, AdMob ve AppForge Native Bridge gibi AppForge'e özel
gelişmiş üretim özelliklerinin yapılandırma bilgileri $META içinde korunur.
""".trimIndent() +
                "\n"
        )

        if (
            draft.sourceMode ==
                SourceMode.LOCAL &&
            sourceRoot != null
        ) {
            addDirectory(
                zip,
                sourceRoot,
                sourceRoot,
                base +
                    "app/src/main/assets/"
            )
        }
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
                "minSdk",
                d.minSdk
            )
            put(
                "targetSdk",
                d.targetSdk
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
                "microphone",
                d.microphone
            )
            put(
                "location",
                d.location
            )
            put(
                "networkState",
                d.networkState
            )
            put(
                "wakeLock",
                d.wakeLock
            )
            put(
                "nfc",
                d.nfc
            )
            put(
                "additionalPermissions",
                org.json.JSONArray(
                    d.additionalPermissions.sorted()
                )
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
            minSdk =
                o.optInt(
                    "minSdk",
                    26
                ),
            targetSdk =
                o.optInt(
                    "targetSdk",
                    37
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
            microphone =
                o.optBoolean(
                    "microphone",
                    false
                ),
            location =
                o.optBoolean(
                    "location",
                    false
                ),
            networkState =
                o.optBoolean(
                    "networkState",
                    true
                ),
            wakeLock =
                o.optBoolean(
                    "wakeLock",
                    false
                ),
            nfc =
                o.optBoolean(
                    "nfc",
                    false
                ),
            additionalPermissions =
                o.optJSONArray(
                    "additionalPermissions"
                )?.let { array ->
                    buildSet {
                        for (index in 0 until array.length()) {
                            array.optString(index)
                                .takeIf { it.isNotBlank() }
                                ?.let(::add)
                        }
                    }
                } ?: emptySet(),
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
