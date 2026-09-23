package com.appforge.studio.build

import android.content.Context
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class WindowsPortablePackageSpec(
    val appName: String,
    val appId: String,
    val versionName: String,
    val versionCode: Int,
    val siteRoot: File,
    val startPage: String
)

internal object WindowsPortableExePackager {

    private val PAYLOAD_MAGIC =
        "AFEXEP01".toByteArray(Charsets.US_ASCII)

    private val FOOTER_MAGIC =
        "APPFORGE-EXE-V1!".toByteArray(Charsets.US_ASCII)

    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val MAX_SITE_BYTES = 500L * 1024L * 1024L
    private const val MAX_SITE_FILES = 10_000
    private const val MAX_PAYLOAD_BYTES = 512L * 1024L * 1024L
    private const val COPY_BUFFER = 1024 * 1024

    suspend fun createAcceptanceSmoke(
        context: Context,
        onProgress: (String) -> Unit = {}
    ): File =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val work = File(
                appContext.cacheDir,
                "windows-exe-acceptance"
            )

            work.deleteRecursively()

            val site = File(work, "site").apply {
                mkdirs()
            }

            File(site, "index.html").writeText(
                """
                <!doctype html>
                <html lang="tr">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>AppForge Windows Device Test</title>
                    <style>
                        body {
                            margin: 0;
                            min-height: 100vh;
                            display: grid;
                            place-items: center;
                            background: #07101f;
                            color: #f8fafc;
                            font-family: system-ui, sans-serif;
                        }
                        main { text-align: center; padding: 32px; }
                        h1 { color: #48c7ea; }
                    </style>
                </head>
                <body>
                    <main>
                        <h1>APPFORGE_WINDOWS_DEVICE_SMOKE_OK</h1>
                        <p>Bu EXE Android cihaz üzerinde çevrimdışı paketlendi.</p>
                    </main>
                </body>
                </html>
                """.trimIndent(),
                Charsets.UTF_8
            )

            val target = File(
                work,
                "AppForge-Windows-Device-Smoke.exe"
            )

            packageLocalSite(
                context = appContext,
                spec = WindowsPortablePackageSpec(
                    appName = "AppForge Windows Device Smoke",
                    appId = "com.appforge.windows.devicesmoke",
                    versionName = "1.0.0",
                    versionCode = 1,
                    siteRoot = site,
                    startPage = "index.html"
                ),
                target = target,
                onProgress = onProgress
            )

            target
        }

    suspend fun packageLocalSite(
        context: Context,
        spec: WindowsPortablePackageSpec,
        target: File,
        onProgress: (String) -> Unit = {}
    ): File =
        withContext(Dispatchers.IO) {
            validateSpec(spec)

            val host =
                WindowsPortableHostStore.requireVerifiedHost(
                    context.applicationContext
                )

            onProgress("Windows EXE • proje payload hazırlanıyor...")

            val temporaryRoot = File(
                context.cacheDir,
                "windows-exe-packager/${UUID.randomUUID()}"
            ).apply { mkdirs() }

            val projectZip = File(
                temporaryRoot,
                "project.zip"
            )

            try {
                createProjectZip(
                    siteRoot = spec.siteRoot,
                    target = projectZip
                )

                val manifest = createManifest(spec)
                val manifestBytes =
                    manifest.toString().toByteArray(Charsets.UTF_8)

                require(
                    manifestBytes.size in 1..MAX_MANIFEST_BYTES
                ) {
                    "Windows EXE manifest boyutu geçersiz."
                }

                val payloadLength =
                    12L +
                        manifestBytes.size.toLong() +
                        projectZip.length()

                require(
                    payloadLength in 1..MAX_PAYLOAD_BYTES
                ) {
                    "Windows EXE payload'ı 512 MB sınırını aşıyor."
                }

                val parent =
                    target.parentFile
                        ?: error(
                            "Windows EXE hedef klasörü bulunamadı."
                        )

                parent.mkdirs()

                val part = File(
                    parent,
                    "${target.name}.part"
                )

                part.delete()

                onProgress(
                    "Windows EXE • doğrulanmış host kopyalanıyor..."
                )

                copyHost(
                    source = host,
                    target = part
                )

                require(
                    part.length() ==
                        WindowsPortableHostStore.HOST_BYTES
                ) {
                    "Windows Host kopyası beklenen boyutta değil."
                }

                onProgress(
                    "Windows EXE • AppForge proje payload'ı ekleniyor..."
                )

                appendPayload(
                    target = part,
                    manifest = manifestBytes,
                    projectZip = projectZip,
                    payloadLength = payloadLength
                )

                verifyPackagedExe(
                    file = part,
                    expectedPayloadLength = payloadLength
                )

                if (target.exists()) {
                    require(target.delete()) {
                        "Eski Windows EXE çıktısı silinemedi."
                    }
                }

                require(part.renameTo(target)) {
                    "Windows EXE final konuma taşınamadı."
                }

                require(
                    target.isFile &&
                        target.length() >
                            WindowsPortableHostStore.HOST_BYTES
                ) {
                    "Windows EXE çıktısı geçersiz."
                }

                onProgress(
                    "Windows EXE • cihaz-local paketleme tamamlandı."
                )

                target

            } finally {
                temporaryRoot.deleteRecursively()
            }
        }


    suspend fun packageProject(
        context: Context,
        draft: ProjectDraft,
        siteRoot: File?,
        target: File,
        onProgress: (String) -> Unit = {}
    ): File =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val local = draft.sourceMode == SourceMode.LOCAL
            val localSite = if (local) {
                siteRoot?.canonicalFile
                    ?: error("Windows LOCAL proje çıktısı bulunamadı.")
            } else null

            if (local) {
                require(
                    localSite?.isDirectory == true &&
                        File(localSite, "index.html").isFile
                ) { "Windows LOCAL proje index.html bulunamadı." }
            } else {
                require(draft.webUrl.startsWith("https://", ignoreCase = true)) {
                    "Windows URL modu HTTPS gerektirir."
                }
            }

            require(draft.appName.trim().isNotBlank()) {
                "Windows uygulama adı gerekli."
            }
            require(Regex("""^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$""").matches(draft.packageName)) {
                "Windows app ID geçersiz."
            }

            val host = WindowsPortableHostStore.requireVerifiedHost(appContext)
            val temporaryRoot = File(
                appContext.cacheDir,
                "windows-project-packager/${UUID.randomUUID()}"
            ).apply { mkdirs() }
            val projectZip = if (local) {
                File(temporaryRoot, "project.zip").also {
                    createProjectZip(localSite!!, it)
                }
            } else null

            try {
                val manifest = createProjectManifest(
                    draft,
                    if (local) "index.html" else ""
                )
                val manifestBytes = manifest.toString().toByteArray(Charsets.UTF_8)
                require(manifestBytes.size in 1..MAX_MANIFEST_BYTES) {
                    "Windows EXE manifest boyutu geçersiz."
                }
                val payloadLength =
                    12L + manifestBytes.size.toLong() + (projectZip?.length() ?: 0L)
                require(payloadLength in 1..MAX_PAYLOAD_BYTES) {
                    "Windows EXE payload'ı 512 MB sınırını aşıyor."
                }

                val parent = target.parentFile
                    ?: error("Windows EXE hedef klasörü bulunamadı.")
                parent.mkdirs()
                val part = File(parent, "${target.name}.part")
                part.delete()

                onProgress("Windows EXE • doğrulanmış host kopyalanıyor...")
                copyHost(host, part)
                require(part.length() == WindowsPortableHostStore.HOST_BYTES) {
                    "Windows Host kopyası beklenen boyutta değil."
                }
                if (!draft.iconUri.isNullOrBlank()) {
                    onProgress("Windows EXE • uygulama simgesi gömülüyor...")
                    val icons = WindowsPeIconPatcher.install(appContext, draft.iconUri!!, part)
                    require(icons > 0 && part.length() == WindowsPortableHostStore.HOST_BYTES) {
                        "Windows EXE özel ikonu doğrulanamadı."
                    }
                }

                onProgress("Windows EXE • gerçek proje payload'ı ekleniyor...")
                appendPayload(part, manifestBytes, projectZip, payloadLength)
                verifyPackagedExe(part, payloadLength)

                if (target.exists()) {
                    require(target.delete()) { "Eski Windows EXE çıktısı silinemedi." }
                }
                require(part.renameTo(target)) {
                    "Windows EXE final konuma taşınamadı."
                }
                require(
                    target.isFile &&
                        target.length() > WindowsPortableHostStore.HOST_BYTES
                ) { "Windows EXE çıktısı geçersiz." }

                onProgress("Windows EXE • gerçek proje cihaz üzerinde paketlendi.")
                target
            } finally {
                temporaryRoot.deleteRecursively()
            }
        }

    private fun createProjectManifest(
        draft: ProjectDraft,
        startPage: String
    ): JSONObject {
        val local = draft.sourceMode == SourceMode.LOCAL
        return JSONObject()
            .put("format", "appforge-project")
            .put("formatVersion", 1)
            .put("producer", "AppForge Studio")
            .put("platform", "windows")
            .put("appName", draft.appName.trim())
            .put("appId", draft.packageName)
            .put("versionName", draft.versionName.ifBlank { "1.0.0" })
            .put("versionCode", draft.versionCode.coerceAtLeast(1))
            .put("sourceMode", if (local) "LOCAL" else "URL")
            .put("webUrl", if (local) "" else draft.webUrl)
            .put("projectRoot", if (local) "project.zip" else JSONObject.NULL)
            .put(
                "startPage",
                if (local) normalizeRelativePath(startPage) else ""
            )
            .put(
                "webView",
                JSONObject()
                    .put("javaScriptEnabled", draft.webJavaScriptEnabled)
                    .put("domStorageEnabled", draft.webDomStorageEnabled)
                    .put("zoomEnabled", draft.webZoomEnabled)
                    .put("wideViewPortEnabled", draft.webWideViewPortEnabled)
                    .put("overviewModeEnabled", draft.webOverviewModeEnabled)
                    .put("mediaAutoplayEnabled", draft.webMediaAutoplayEnabled)
                    .put("mixedContentAllowed", draft.webMixedContentAllowed)
            )
            .put(
                "nativeBridge",
                JSONObject().put("mediaPlayer", draft.mediaPlayerBridge)
            )
            .put(
                "conversion",
                JSONObject().put("apkToExe", true).put("exeToApk", true)
            )
            .put("createdBy", "AppForge Studio")
            .put("target", "windows-x64")
    }

    private fun validateSpec(
        spec: WindowsPortablePackageSpec
    ) {
        require(
            spec.appName.trim().isNotBlank() &&
                spec.appName.length <= 120
        ) {
            "Windows uygulama adı geçersiz."
        }

        require(
            Regex(
                """^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$"""
            ).matches(spec.appId)
        ) {
            "Windows app ID geçersiz."
        }

        require(spec.versionName.length <= 100) {
            "Windows sürüm adı geçersiz."
        }

        require(spec.versionCode > 0) {
            "Windows versionCode geçersiz."
        }

        val root = spec.siteRoot.canonicalFile

        require(root.isDirectory) {
            "Windows web proje klasörü bulunamadı."
        }

        val startPage =
            normalizeRelativePath(spec.startPage)

        val startFile =
            File(root, startPage).canonicalFile

        require(
            startFile.isFile &&
                isInside(root, startFile)
        ) {
            "Windows başlangıç sayfası geçersiz."
        }
    }

    private fun createManifest(
        spec: WindowsPortablePackageSpec
    ): JSONObject =
        JSONObject()
            .put("format", "appforge-project")
            .put("formatVersion", 1)
            .put("producer", "AppForge Studio")
            .put("platform", "windows")
            .put("appName", spec.appName.trim())
            .put("appId", spec.appId)
            .put("versionName", spec.versionName)
            .put("versionCode", spec.versionCode)
            .put("sourceMode", "LOCAL")
            .put("webUrl", "")
            .put("projectRoot", "project.zip")
            .put(
                "startPage",
                normalizeRelativePath(spec.startPage)
            )
            .put(
                "conversion",
                JSONObject()
                    .put("apkToExe", true)
                    .put("exeToApk", true)
            )
            .put("createdBy", "AppForge Studio")
            .put("target", "windows-x64")

    private fun createProjectZip(
        siteRoot: File,
        target: File
    ) {
        val root = siteRoot.canonicalFile

        require(root.isDirectory) {
            "Windows site klasörü bulunamadı."
        }

        target.parentFile?.mkdirs()

        var fileCount = 0
        var totalBytes = 0L

        ZipOutputStream(
            BufferedOutputStream(
                FileOutputStream(target),
                COPY_BUFFER
            )
        ).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)

            root.walkTopDown().forEach { candidate ->
                if (!candidate.isFile) {
                    return@forEach
                }

                val canonical =
                    candidate.canonicalFile

                require(
                    isInside(root, canonical)
                ) {
                    "Windows proje dosyası site klasörü dışına çıkıyor."
                }

                fileCount += 1

                require(
                    fileCount <= MAX_SITE_FILES
                ) {
                    "Windows projesi 10000 dosya sınırını aşıyor."
                }

                val length = canonical.length()

                require(length >= 0L) {
                    "Windows proje dosya boyutu geçersiz."
                }

                totalBytes += length

                require(
                    totalBytes <= MAX_SITE_BYTES
                ) {
                    "Windows projesi 500 MB sınırını aşıyor."
                }

                val relative =
                    normalizeRelativePath(
                        candidate
                            .relativeTo(root)
                            .invariantSeparatorsPath
                    )

                val entry =
                    ZipEntry(relative).apply {
                        time = 0L
                    }

                zip.putNextEntry(entry)

                canonical
                    .inputStream()
                    .buffered(COPY_BUFFER)
                    .use { input ->
                        input.copyTo(
                            zip,
                            COPY_BUFFER
                        )
                    }

                zip.closeEntry()
            }
        }

        require(fileCount > 0) {
            "Windows proje klasörü boş."
        }

        require(
            target.isFile &&
                target.length() > 0L
        ) {
            "Windows project.zip oluşturulamadı."
        }
    }

    private fun copyHost(
        source: File,
        target: File
    ) {
        target.parentFile?.mkdirs()

        source
            .inputStream()
            .buffered(COPY_BUFFER)
            .use { input ->
                FileOutputStream(
                    target,
                    false
                ).use { output ->
                    input.copyTo(
                        output,
                        COPY_BUFFER
                    )
                    output.fd.sync()
                }
            }
    }

    private fun appendPayload(
        target: File,
        manifest: ByteArray,
        projectZip: File?,
        payloadLength: Long
    ) {
        FileOutputStream(
            target,
            true
        ).use { raw ->
            val output =
                DataOutputStream(
                    BufferedOutputStream(
                        raw,
                        COPY_BUFFER
                    )
                )

            output.write(PAYLOAD_MAGIC)
            output.writeInt(manifest.size)
            output.write(manifest)

            projectZip
                ?.inputStream()
                ?.buffered(COPY_BUFFER)
                ?.use { input ->
                    input.copyTo(
                        output,
                        COPY_BUFFER
                    )
                }

            output.writeLong(payloadLength)
            output.write(FOOTER_MAGIC)
            output.flush()
            raw.fd.sync()
        }
    }

    private fun verifyPackagedExe(
        file: File,
        expectedPayloadLength: Long
    ) {
        RandomAccessFile(file, "r").use { raf ->
            require(
                raf.length() >
                    WindowsPortableHostStore.HOST_BYTES
            ) {
                "Windows EXE çıktısı host boyutunu aşmıyor."
            }

            raf.seek(0L)

            require(
                raf.read() == 'M'.code &&
                    raf.read() == 'Z'.code
            ) {
                "Windows EXE MZ başlığı geçersiz."
            }

            val footerBytes =
                8L +
                    FOOTER_MAGIC.size.toLong()

            require(
                raf.length() > footerBytes
            ) {
                "Windows EXE footer eksik."
            }

            raf.seek(
                raf.length() -
                    footerBytes
            )

            val payloadLength =
                raf.readLong()

            require(
                payloadLength ==
                    expectedPayloadLength
            ) {
                "Windows EXE payload uzunluğu doğrulanamadı."
            }

            val magic =
                ByteArray(
                    FOOTER_MAGIC.size
                )

            raf.readFully(magic)

            require(
                magic.contentEquals(
                    FOOTER_MAGIC
                )
            ) {
                "Windows EXE AppForge footer imzası geçersiz."
            }

            val payloadOffset =
                raf.length() -
                    footerBytes -
                    payloadLength

            require(
                payloadOffset >=
                    WindowsPortableHostStore.HOST_BYTES
            ) {
                "Windows EXE payload konumu geçersiz."
            }

            raf.seek(payloadOffset)

            val payloadMagic =
                ByteArray(
                    PAYLOAD_MAGIC.size
                )

            raf.readFully(payloadMagic)

            require(
                payloadMagic.contentEquals(
                    PAYLOAD_MAGIC
                )
            ) {
                "Windows EXE payload başlığı doğrulanamadı."
            }
        }
    }

    private fun normalizeRelativePath(
        value: String
    ): String {
        val normalized =
            value
                .replace('\\', '/')
                .trim()

        require(
            normalized.isNotBlank() &&
                !normalized.startsWith("/") &&
                !Regex(
                    """^[A-Za-z]:"""
                ).containsMatchIn(normalized)
        ) {
            "Windows proje yolu geçersiz."
        }

        val parts =
            normalized
                .split('/')
                .filter { it.isNotBlank() }

        require(
            parts.isNotEmpty() &&
                parts.none {
                    it == "." ||
                        it == ".."
                }
        ) {
            "Windows proje path traversal engellendi."
        }

        return parts.joinToString("/")
    }

    private fun isInside(
        root: File,
        file: File
    ): Boolean {
        val rootPath = root.canonicalPath
        val filePath = file.canonicalPath

        return filePath == rootPath ||
            filePath.startsWith(
                rootPath +
                    File.separator
            )
    }
}
