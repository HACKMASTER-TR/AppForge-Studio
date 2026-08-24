package com.appforge.studio.io

import android.content.Context
import android.net.Uri
import com.appforge.studio.model.SourceMode
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

data class ApkConversionProject(
    val appName: String,
    val appId: String,
    val versionName: String,
    val versionCode: Int,
    val sourceMode: SourceMode,
    val webUrl: String,
    val projectDir: File?
)

object AppForgeApkConversion {

    private const val MANIFEST_ENTRY =
        "assets/appforge-project.json"

    private const val SITE_PREFIX =
        "assets/site/"

    private const val MAX_MANIFEST_BYTES =
        256 * 1024

    private const val MAX_SITE_BYTES =
        500L * 1024L * 1024L

    fun extract(
        context: Context,
        uri: Uri
    ): ApkConversionProject {

        cleanupOldProjects(
            context
        )

        val workDir =
            File(
                context.cacheDir,
                "appforge-conversion/${System.currentTimeMillis()}"
            )

        val siteDir =
            File(
                workDir,
                "site"
            )

        siteDir.mkdirs()

        try {
            var manifestBytes:
                ByteArray? =
                null

            var siteFileCount =
                0

            var totalSiteBytes =
                0L

            val input =
                context
                    .contentResolver
                    .openInputStream(
                        uri
                    )
                    ?: error(
                        "APK dosyası açılamadı."
                    )

            ZipInputStream(
                input.buffered()
            ).use {
                zip ->

                val buffer =
                    ByteArray(
                        1024 * 1024
                    )

                while (true) {
                    val entry =
                        zip.nextEntry
                            ?: break

                    val name =
                        entry.name
                            .replace(
                                '\\',
                                '/'
                            )

                    if (
                        entry.isDirectory
                    ) {
                        zip.closeEntry()
                        continue
                    }

                    when {
                        name ==
                            MANIFEST_ENTRY -> {

                            val out =
                                ByteArrayOutputStream()

                            var count =
                                0

                            while (true) {
                                val read =
                                    zip.read(
                                        buffer
                                    )

                                if (
                                    read <= 0
                                ) {
                                    break
                                }

                                count +=
                                    read

                                require(
                                    count <=
                                        MAX_MANIFEST_BYTES
                                ) {
                                    "AppForge manifesti beklenenden büyük."
                                }

                                out.write(
                                    buffer,
                                    0,
                                    read
                                )
                            }

                            manifestBytes =
                                out.toByteArray()
                        }

                        name.startsWith(
                            SITE_PREFIX
                        ) -> {

                            val rawRelative =
                                name.removePrefix(
                                    SITE_PREFIX
                                )

                            if (
                                rawRelative
                                    .isBlank()
                            ) {
                                zip.closeEntry()
                                continue
                            }

                            val relative =
                                safeRelativePath(
                                    rawRelative
                                )

                            val destination =
                                File(
                                    siteDir,
                                    relative
                                ).canonicalFile

                            val root =
                                siteDir
                                    .canonicalFile

                            require(
                                destination.path ==
                                    root.path ||
                                destination.path
                                    .startsWith(
                                        root.path +
                                            File.separator
                                    )
                            ) {
                                "APK içinde güvensiz dosya yolu bulundu."
                            }

                            destination
                                .parentFile
                                ?.mkdirs()

                            destination
                                .outputStream()
                                .buffered()
                                .use {
                                    out ->

                                    while (true) {
                                        val read =
                                            zip.read(
                                                buffer
                                            )

                                        if (
                                            read <= 0
                                        ) {
                                            break
                                        }

                                        totalSiteBytes +=
                                            read

                                        require(
                                            totalSiteBytes <=
                                                MAX_SITE_BYTES
                                        ) {
                                            "APK web projesi 500 MB sınırını aşıyor."
                                        }

                                        out.write(
                                            buffer,
                                            0,
                                            read
                                        )
                                    }
                                }

                            siteFileCount +=
                                1
                        }
                    }

                    zip.closeEntry()
                }
            }

            val bytes =
                manifestBytes
                    ?: error(
                        "Bu APK AppForge dönüşüm manifesti içermiyor."
                    )

            val manifest =
                JSONObject(
                    bytes.toString(
                        Charsets.UTF_8
                    )
                )

            require(
                manifest.optString(
                    "format"
                ) ==
                    "appforge-project"
            ) {
                "Geçersiz AppForge proje formatı."
            }

            require(
                manifest.optInt(
                    "formatVersion"
                ) ==
                    1
            ) {
                "Desteklenmeyen AppForge proje sürümü."
            }

            require(
                manifest.optString(
                    "platform"
                ) ==
                    "android"
            ) {
                "Bu dosya Android AppForge çıktısı değil."
            }

            val conversion =
                manifest
                    .optJSONObject(
                        "conversion"
                    )
                    ?: error(
                        "Dönüşüm bilgisi bulunamadı."
                    )

            require(
                conversion.optBoolean(
                    "apkToExe",
                    false
                )
            ) {
                "Bu AppForge APK, APK → EXE dönüşümünü desteklemiyor."
            }

            val appName =
                manifest
                    .optString(
                        "appName"
                    )
                    .trim()

            require(
                appName.isNotBlank()
            ) {
                "APK uygulama adı geçersiz."
            }

            val appId =
                manifest
                    .optString(
                        "appId"
                    )
                    .trim()

            require(
                Regex(
                    "^[A-Za-z_]\\w*(\\.[A-Za-z_]\\w*)+$"
                ).matches(
                    appId
                )
            ) {
                "APK uygulama kimliği geçersiz."
            }

            val versionName =
                manifest
                    .optString(
                        "versionName",
                        "1.0.0"
                    )
                    .ifBlank {
                        "1.0.0"
                    }

            val versionCode =
                manifest
                    .optInt(
                        "versionCode",
                        1
                    )
                    .coerceAtLeast(
                        1
                    )

            val sourceModeText =
                manifest
                    .optString(
                        "sourceMode",
                        "LOCAL"
                    )
                    .uppercase()

            val sourceMode =
                if (
                    sourceModeText ==
                        "URL"
                ) {
                    SourceMode.URL
                } else {
                    SourceMode.LOCAL
                }

            val webUrl =
                manifest
                    .optString(
                        "webUrl",
                        ""
                    )
                    .trim()

            if (
                sourceMode ==
                    SourceMode.URL
            ) {
                require(
                    webUrl.startsWith(
                        "https://",
                        ignoreCase =
                            true
                    )
                ) {
                    "APK içindeki web adresi geçersiz."
                }
            } else {
                require(
                    siteFileCount >
                        0
                ) {
                    "APK içinde assets/site proje dosyaları bulunamadı."
                }
            }

            return ApkConversionProject(
                appName =
                    appName,
                appId =
                    appId,
                versionName =
                    versionName,
                versionCode =
                    versionCode,
                sourceMode =
                    sourceMode,
                webUrl =
                    webUrl,
                projectDir =
                    if (
                        sourceMode ==
                            SourceMode.LOCAL
                    ) {
                        siteDir
                    } else {
                        null
                    }
            )

        } catch (
            t: Throwable
        ) {
            workDir
                .deleteRecursively()

            throw t
        }
    }

    private fun safeRelativePath(
        raw: String
    ): String {

        val normalized =
            raw.replace(
                '\\',
                '/'
            )

        require(
            !normalized
                .startsWith("/")
        ) {
            "Geçersiz APK yolu."
        }

        val parts =
            normalized
                .split("/")
                .filter {
                    it.isNotBlank()
                }

        require(
            parts.isNotEmpty() &&
            parts.none {
                it == ".." ||
                it == "."
            }
        ) {
            "Geçersiz APK yolu."
        }

        return parts.joinToString(
            "/"
        )
    }

    private fun cleanupOldProjects(
        context: Context
    ) {
        val root =
            File(
                context.cacheDir,
                "appforge-conversion"
            )

        if (
            !root.exists()
        ) {
            return
        }

        val cutoff =
            System.currentTimeMillis() -
                24L *
                60L *
                60L *
                1000L

        root.listFiles()
            ?.filter {
                it.lastModified() <
                    cutoff
            }
            ?.forEach {
                it.deleteRecursively()
            }
    }
}
