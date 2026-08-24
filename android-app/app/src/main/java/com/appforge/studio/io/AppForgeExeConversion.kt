package com.appforge.studio.io

import android.content.Context
import android.net.Uri
import com.appforge.studio.model.SourceMode
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipInputStream

data class AppForgeExeInfo(
    val cachedExe: File,
    val payloadOffset: Long,
    val payloadLength: Long
)

data class ExeConversionProject(
    val appName: String,
    val appId: String,
    val versionName: String,
    val versionCode: Int,
    val sourceMode: SourceMode,
    val webUrl: String,
    val projectDir: File?
)

object AppForgeExeConversion {

    private const val MAGIC_TEXT =
        "APPFORGE-EXE-V1!"

    private val MAGIC =
        MAGIC_TEXT.toByteArray(
            Charsets.US_ASCII
        )

    private const val PAYLOAD_MAGIC_TEXT =
        "AFEXEP01"

    private val PAYLOAD_MAGIC =
        PAYLOAD_MAGIC_TEXT.toByteArray(
            Charsets.US_ASCII
        )

    private const val FOOTER_BYTES =
        24L

    private const val PAYLOAD_HEADER_BYTES =
        12L

    private const val MAX_EXE_BYTES =
        1_073_741_824L

    private const val MAX_PAYLOAD_BYTES =
        536_870_912L

    private const val MAX_MANIFEST_BYTES =
        256 * 1024

    private const val MAX_SITE_BYTES =
        500L * 1024L * 1024L

    private const val MAX_SITE_FILES =
        10_000


    fun inspect(
        context: Context,
        uri: Uri
    ): AppForgeExeInfo {

        val root =
            File(
                context.cacheDir,
                "appforge-exe-conversion"
            )

        root.mkdirs()

        cleanupOldProjects(
            root
        )

        val workDir =
            File(
                root,
                "${System.currentTimeMillis()}-${System.nanoTime()}"
            )

        workDir.mkdirs()

        val cachedExe =
            File(
                workDir,
                "selected.exe"
            )

        try {
            copyExe(
                context,
                uri,
                cachedExe
            )

            return inspectCachedExe(
                cachedExe
            )
        } catch (
            t: Throwable
        ) {
            workDir
                .deleteRecursively()

            throw t
        }
    }


    fun extract(
        context: Context,
        uri: Uri
    ): ExeConversionProject {

        val info =
            inspect(
                context,
                uri
            )

        val workDir =
            info.cachedExe
                .parentFile
                ?: error(
                    "EXE dönüşüm klasörü bulunamadı."
                )

        try {
            val result =
                extractCachedPayload(
                    info,
                    workDir
                )

            /*
             * Büyük EXE artık gerekli değil.
             * Çıkarılmış site klasörü build bitene
             * kadar cache içinde kalacak.
             */
            runCatching {
                info.cachedExe.delete()
            }

            return result

        } catch (
            t: Throwable
        ) {
            workDir
                .deleteRecursively()

            throw t
        }
    }


    private fun copyExe(
        context: Context,
        uri: Uri,
        target: File
    ) {
        val input =
            context.contentResolver
                .openInputStream(
                    uri
                )
                ?: error(
                    "EXE dosyası açılamadı."
                )

        input.use { source ->
            FileOutputStream(
                target
            ).use { output ->

                val buffer =
                    ByteArray(
                        128 * 1024
                    )

                var total =
                    0L

                while (true) {
                    val read =
                        source.read(
                            buffer
                        )

                    if (
                        read < 0
                    ) {
                        break
                    }

                    total +=
                        read.toLong()

                    if (
                        total >
                        MAX_EXE_BYTES
                    ) {
                        error(
                            "EXE dosyası 1 GB sınırını aşıyor."
                        )
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


    private fun inspectCachedExe(
        exe: File
    ): AppForgeExeInfo {

        RandomAccessFile(
            exe,
            "r"
        ).use { raf ->

            val fileLength =
                raf.length()

            if (
                fileLength <
                FOOTER_BYTES + 2L
            ) {
                error(
                    "Geçerli bir Windows EXE dosyası değil."
                )
            }

            raf.seek(
                0L
            )

            val m =
                raf.read()

            val z =
                raf.read()

            if (
                m != 'M'.code ||
                z != 'Z'.code
            ) {
                error(
                    "Geçerli bir Windows EXE dosyası değil."
                )
            }

            raf.seek(
                fileLength -
                    MAGIC.size
            )

            val foundMagic =
                ByteArray(
                    MAGIC.size
                )

            raf.readFully(
                foundMagic
            )

            if (
                !foundMagic.contentEquals(
                    MAGIC
                )
            ) {
                unsupported()
            }

            raf.seek(
                fileLength -
                    FOOTER_BYTES
            )

            val payloadLength =
                raf.readLong()

            if (
                payloadLength <= 0L ||
                payloadLength >
                    MAX_PAYLOAD_BYTES
            ) {
                error(
                    "AppForge EXE dönüşüm verisi bozuk veya desteklenmiyor."
                )
            }

            val payloadOffset =
                fileLength -
                    FOOTER_BYTES -
                    payloadLength

            if (
                payloadOffset < 2L
            ) {
                error(
                    "AppForge EXE dönüşüm verisi geçersiz."
                )
            }

            return AppForgeExeInfo(
                cachedExe =
                    exe,
                payloadOffset =
                    payloadOffset,
                payloadLength =
                    payloadLength
            )
        }
    }


    private fun extractCachedPayload(
        info: AppForgeExeInfo,
        workDir: File
    ): ExeConversionProject {

        RandomAccessFile(
            info.cachedExe,
            "r"
        ).use { raf ->

            raf.seek(
                info.payloadOffset
            )

            val foundPayloadMagic =
                ByteArray(
                    PAYLOAD_MAGIC.size
                )

            raf.readFully(
                foundPayloadMagic
            )

            require(
                foundPayloadMagic.contentEquals(
                    PAYLOAD_MAGIC
                )
            ) {
                "AppForge EXE payload başlığı geçersiz."
            }

            val manifestLength =
                raf.readInt()

            require(
                manifestLength > 0 &&
                    manifestLength <=
                        MAX_MANIFEST_BYTES
            ) {
                "AppForge EXE manifest boyutu geçersiz."
            }

            val usedBytes =
                PAYLOAD_HEADER_BYTES +
                    manifestLength.toLong()

            require(
                usedBytes <=
                    info.payloadLength
            ) {
                "AppForge EXE manifesti payload sınırını aşıyor."
            }

            val manifestBytes =
                ByteArray(
                    manifestLength
                )

            raf.readFully(
                manifestBytes
            )

            val manifest =
                JSONObject(
                    manifestBytes.toString(
                        Charsets.UTF_8
                    )
                )

            validateManifest(
                manifest
            )

            val appName =
                manifest
                    .optString(
                        "appName"
                    )
                    .trim()

            val appId =
                manifest
                    .optString(
                        "appId"
                    )
                    .trim()

            val versionName =
                manifest
                    .optString(
                        "versionName",
                        "1.0.0"
                    )
                    .trim()
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

            require(
                appName.isNotBlank() &&
                    appName.length <=
                        120
            ) {
                "EXE uygulama adı geçersiz."
            }

            require(
                Regex(
                    "^[A-Za-z_]\\w*(\\.[A-Za-z_]\\w*)+$"
                ).matches(
                    appId
                )
            ) {
                "EXE uygulama kimliği geçersiz."
            }

            require(
                versionName.length <=
                    100
            ) {
                "EXE sürüm bilgisi geçersiz."
            }

            val sourceModeText =
                manifest
                    .optString(
                        "sourceMode"
                    )
                    .uppercase()

            require(
                sourceModeText ==
                    "LOCAL" ||
                    sourceModeText ==
                    "URL"
            ) {
                "EXE kaynak modu geçersiz."
            }

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

            val projectBytes =
                info.payloadLength -
                    usedBytes

            var projectDir:
                File? =
                null

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
                    "EXE içindeki web adresi geçersiz."
                }

                require(
                    projectBytes ==
                        0L
                ) {
                    "URL tabanlı EXE beklenmeyen yerel proje verisi içeriyor."
                }

            } else {

                require(
                    manifest.optString(
                        "projectRoot",
                        ""
                    ) ==
                        "project.zip"
                ) {
                    "EXE yerel proje tanımı geçersiz."
                }

                require(
                    projectBytes >
                        0L &&
                    projectBytes <=
                        MAX_PAYLOAD_BYTES
                ) {
                    "EXE proje paketi geçersiz."
                }

                val projectZip =
                    File(
                        workDir,
                        "project.zip"
                    )

                FileOutputStream(
                    projectZip
                ).use { output ->
                    copyExactBytes(
                        raf,
                        output,
                        projectBytes
                    )
                }

                val siteDir =
                    File(
                        workDir,
                        "site"
                    )

                extractProjectZip(
                    projectZip,
                    siteDir
                )

                runCatching {
                    projectZip.delete()
                }

                projectDir =
                    siteDir
            }

            return ExeConversionProject(
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
                    projectDir
            )
        }
    }


    private fun validateManifest(
        manifest: JSONObject
    ) {
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
                "producer"
            ) ==
                "AppForge Studio"
        ) {
            "EXE AppForge Studio tarafından oluşturulmamış."
        }

        require(
            manifest.optString(
                "platform"
            ) ==
                "windows"
        ) {
            "Bu dosya Windows AppForge çıktısı değil."
        }

        val conversion =
            manifest
                .optJSONObject(
                    "conversion"
                )
                ?: error(
                    "EXE dönüşüm bilgisi bulunamadı."
                )

        require(
            conversion.optBoolean(
                "exeToApk",
                false
            )
        ) {
            "Bu AppForge EXE, EXE → APK dönüşümünü desteklemiyor."
        }
    }


    private fun copyExactBytes(
        input: RandomAccessFile,
        output: FileOutputStream,
        length: Long
    ) {
        var remaining =
            length

        val buffer =
            ByteArray(
                256 * 1024
            )

        while (
            remaining >
            0L
        ) {
            val wanted =
                minOf(
                    buffer.size.toLong(),
                    remaining
                ).toInt()

            val read =
                input.read(
                    buffer,
                    0,
                    wanted
                )

            if (
                read <= 0
            ) {
                error(
                    "EXE proje paketi beklenenden erken bitti."
                )
            }

            output.write(
                buffer,
                0,
                read
            )

            remaining -=
                read.toLong()
        }
    }


    private fun extractProjectZip(
        zipFile: File,
        siteDir: File
    ) {
        siteDir.mkdirs()

        val root =
            siteDir
                .canonicalFile

        var totalBytes =
            0L

        var fileCount =
            0

        ZipInputStream(
            FileInputStream(
                zipFile
            ).buffered()
        ).use { zip ->

            val buffer =
                ByteArray(
                    128 * 1024
                )

            while (true) {
                val entry =
                    zip.nextEntry
                        ?: break

                if (
                    entry.isDirectory
                ) {
                    zip.closeEntry()
                    continue
                }

                val relative =
                    safeRelativePath(
                        entry.name
                    )

                val destination =
                    File(
                        siteDir,
                        relative
                    ).canonicalFile

                require(
                    destination.path ==
                        root.path ||
                    destination.path
                        .startsWith(
                            root.path +
                                File.separator
                        )
                ) {
                    "EXE proje paketinde güvensiz dosya yolu bulundu."
                }

                fileCount +=
                    1

                require(
                    fileCount <=
                        MAX_SITE_FILES
                ) {
                    "EXE proje paketi çok fazla dosya içeriyor."
                }

                destination
                    .parentFile
                    ?.mkdirs()

                FileOutputStream(
                    destination
                ).buffered().use {
                    output ->

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

                        totalBytes +=
                            read.toLong()

                        require(
                            totalBytes <=
                                MAX_SITE_BYTES
                        ) {
                            "EXE web projesi 500 MB sınırını aşıyor."
                        }

                        output.write(
                            buffer,
                            0,
                            read
                        )
                    }
                }

                zip.closeEntry()
            }
        }

        require(
            fileCount >
                0
        ) {
            "EXE proje paketi boş."
        }

        val htmlFound =
            siteDir
                .walkTopDown()
                .any {
                    it.isFile &&
                        it.extension.equals(
                            "html",
                            ignoreCase =
                                true
                        )
                }

        require(
            htmlFound
        ) {
            "EXE proje paketinde HTML başlangıç dosyası bulunamadı."
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
            !normalized.startsWith(
                "/"
            )
        ) {
            "Geçersiz EXE proje yolu."
        }

        val parts =
            normalized
                .split(
                    "/"
                )
                .filter {
                    it.isNotBlank()
                }

        require(
            parts.isNotEmpty() &&
            parts.none {
                it ==
                    ".." ||
                it ==
                    "."
            }
        ) {
            "Geçersiz EXE proje yolu."
        }

        return parts.joinToString(
            "/"
        )
    }


    private fun cleanupOldProjects(
        root: File
    ) {
        val cutoff =
            System.currentTimeMillis() -
                24L *
                60L *
                60L *
                1000L

        root
            .listFiles()
            ?.filter {
                it.lastModified() <
                    cutoff
            }
            ?.forEach {
                it.deleteRecursively()
            }
    }


    private fun unsupported(): Nothing {
        error(
            "Bu EXE AppForge projesi değil. Otomatik EXE → APK dönüşümü desteklenmiyor."
        )
    }
}
