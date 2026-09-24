package com.appforge.studio.ai

import android.annotation.TargetApi
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.appforge.studio.build.BuildApiClient
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class AppForgeAgentArtifactSaveResult(
    val downloadId: Long?,
    val message: String
)

internal class AppForgeAgentArtifactClient(
    context: Context,
    buildServiceUrl: String,
    buildApiKey: String
) {
    private val appContext = context.applicationContext

    private val client = BuildApiClient(
        context = appContext,
        baseUrl = buildServiceUrl.trim().ifBlank {
            "https://api.appforgecloud.com"
        },
        apiKey = buildApiKey
    )

    fun inspect(
        buildId: String
    ): AppForgeAgentArtifactState {
        require(buildId.isNotBlank()) {
            "Artifact inceleme için buildId gerekli."
        }

        val logsResult = runCatching {
            client.getLogs(buildId)
                .map { (_, line) ->
                    AppForgeAgentArtifactSafety.sanitize(
                        line,
                        maxChars = 1_200
                    )
                }
                .filter { it.isNotBlank() }
                .takeLast(MAX_VISIBLE_LOGS)
        }

        val labResult = runCatching {
            client.testLab(buildId)
        }

        val lab = labResult.getOrNull()

        val message = buildString {
            if (logsResult.isSuccess) {
                append("Build logları yüklendi.")
            } else {
                append("Build logları alınamadı: ")
                append(
                    AppForgeAgentArtifactSafety.sanitize(
                        logsResult.exceptionOrNull()
                            ?.message
                            .orEmpty(),
                        1_000
                    )
                )
            }

            append(' ')

            if (lab != null) {
                append("Test Lab raporu hazır.")
            } else {
                append("Test Lab şu an kullanılamıyor")
                val reason = labResult.exceptionOrNull()
                    ?.message
                    .orEmpty()
                    .takeIf { it.isNotBlank() }

                if (reason != null) {
                    append(": ")
                    append(
                        AppForgeAgentArtifactSafety.sanitize(
                            reason,
                            1_000
                        )
                    )
                } else {
                    append('.')
                }
            }
        }.trim()

        return AppForgeAgentArtifactState(
            busy = false,
            message = message,
            buildId = buildId,
            logsLoaded = logsResult.isSuccess,
            testLabAvailable = lab != null,
            logs = logsResult.getOrDefault(emptyList()),
            apk = lab?.apk?.let {
                AppForgeAgentArtifactReport(
                    kind = "apk",
                    fileSizeBytes = it.fileSizeBytes,
                    uncompressedBytes = it.uncompressedBytes,
                    entryCount = it.entryCount
                )
            },
            aab = lab?.aab?.let {
                AppForgeAgentArtifactReport(
                    kind = "aab",
                    fileSizeBytes = it.fileSizeBytes,
                    uncompressedBytes = it.uncompressedBytes,
                    entryCount = it.entryCount
                )
            },
            security = lab?.security
                .orEmpty()
                .take(MAX_SECURITY_FINDINGS)
                .map {
                    AppForgeAgentSecurityFinding(
                        severity =
                            AppForgeAgentArtifactSafety.sanitize(
                                it.severity,
                                80
                            ),
                        title =
                            AppForgeAgentArtifactSafety.sanitize(
                                it.title,
                                300
                            ),
                        detail =
                            AppForgeAgentArtifactSafety.sanitize(
                                it.detail,
                                1_500
                            )
                    )
                }
        )
    }

    /** Device builds return file:// tickets; Android DownloadManager only accepts HTTPS. */
    fun enqueueDownload(
        buildId: String,
        kind: String
    ): AppForgeAgentArtifactSaveResult {
        val safeKind = AppForgeAgentArtifactSafety.safeKind(kind)
        val ticket = client.createDownloadTicket(buildId = buildId, kind = safeKind)
        val uri = Uri.parse(ticket.url)
        val fileName = AppForgeAgentArtifactSafety.fileName(buildId, safeKind)

        if (uri.scheme.equals("file", ignoreCase = true)) {
            val root = File(appContext.filesDir, "device-build/artifacts").canonicalFile
            val source = File(uri.path ?: error("Yerel artifact yolu eksik.")).canonicalFile
            require(source.path.startsWith(root.path + File.separator) &&
                source.isFile && source.length() > 0L) {
                "Yerel artifact dosyası doğrulanamadı."
            }

            val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savePublicArtifact(source, fileName, safeKind)
            } else {
                saveLegacyArtifact(source, fileName)
            }
            return AppForgeAgentArtifactSaveResult(
                downloadId = null,
                message = "${safeKind.uppercase()} kaydedildi: $savedPath"
            )
        }

        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Artifact download yalnız güvenli HTTPS veya doğrulanmış yerel dosyayla yapılabilir."
        }

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE)
            as? DownloadManager ?: error("Android DownloadManager kullanılamıyor.")
        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setDescription("AppForge Unified Agent artifact")
            .setMimeType(artifactMimeType(safeKind))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
        return AppForgeAgentArtifactSaveResult(
            downloadId = manager.enqueue(request),
            message = "${safeKind.uppercase()} indirme kuyruğuna eklendi."
        )
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun savePublicArtifact(source: File, fileName: String, kind: String): String {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, artifactMimeType(kind))
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/AppForgeStudio"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("AppForgeStudio indirme kaydı oluşturulamadı.")
        try {
            val count = resolver.openOutputStream(target, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, 1024 * 1024) }
            } ?: error("Artifact kaydetme çıkışı açılamadı.")
            require(count == source.length()) { "Kaydedilen artifact boyutu eşleşmiyor." }
            val storedSize = resolver.openFileDescriptor(target, "r")?.use { it.statSize }
            require(storedSize == null || storedSize < 0L || storedSize == count) {
                "İndirilenler'deki artifact eksik kaydedildi."
            }
            val published = resolver.update(
                target,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            require(published > 0) { "Artifact kaydı yayımlanamadı." }
            val actualName = resolver.query(
                target,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.takeIf { it.isNotBlank() } ?: fileName
            return "İndirilenler/AppForgeStudio/$actualName"
        } catch (failure: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            throw failure
        }
    }

    /** API 26–28 cannot use MediaStore Downloads; never pretend this is public Downloads. */
    private fun saveLegacyArtifact(source: File, fileName: String): String {
        val base = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Uygulama dosya klasörü kullanılamıyor.")
        val dir = File(base, "AppForgeStudio")
        require(dir.isDirectory || dir.mkdirs()) { "Artifact klasörü oluşturulamadı." }
        val target = File(dir, fileName)
        val temp = File.createTempFile("appforge-", ".partial", dir)
        try {
            val count = source.inputStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
            }
            require(count == source.length() && temp.length() == count) {
                "Artifact boyutu eşleşmiyor."
            }
            require(!target.exists() || target.delete()) { "Önceki artifact değiştirilemedi." }
            require(temp.renameTo(target)) { "Artifact kaydı tamamlanamadı." }
            return target.absolutePath
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun artifactMimeType(kind: String): String = when (kind) {
        "apk" -> "application/vnd.android.package-archive"
        "exe" -> "application/vnd.microsoft.portable-executable"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MAX_VISIBLE_LOGS = 160
        const val MAX_SECURITY_FINDINGS = 40
    }
}

internal object AppForgeAgentSourceExporter {
    private const val MAX_FILES = 8_000
    private const val MAX_TOTAL_BYTES =
        120L * 1024L * 1024L

    private val forbiddenLeafNames = setOf(
        ".env",
        "google-services.json",
        "id_rsa",
        "id_ed25519"
    )

    private val forbiddenExtensions = setOf(
        "jks",
        "keystore",
        "p12",
        "pfx",
        "pem",
        "key"
    )

    fun export(
        context: Context,
        workspace: File,
        blueprint: AppForgeAgentBlueprint
    ): String {
        val appRoot = File(
            context.filesDir,
            "unified-agent-workspaces"
        ).canonicalFile

        val workspaceRoot =
            workspace.canonicalFile

        require(
            workspaceRoot.isDirectory &&
                inside(appRoot, workspaceRoot)
        ) {
            "Kaynak export workspace'i güvenli değil."
        }

        val prepared =
            AppForgeAgentBuildProjectPreparer.prepare(
                cacheRoot = context.cacheDir,
                workspace = workspaceRoot,
                blueprint = blueprint
            )

        val fileName =
            AppForgeAgentArtifactSafety.sourceZipName(
                blueprint.appName,
                blueprint.platform
            )

        return try {
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                exportMediaStore(
                    context = context,
                    sourceRoot =
                        prepared.projectRoot,
                    fileName = fileName
                )
            } else {
                exportLegacy(
                    context = context,
                    sourceRoot =
                        prepared.projectRoot,
                    fileName = fileName
                )
            }
        } finally {
            prepared.cleanup()
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun exportMediaStore(
        context: Context,
        sourceRoot: File,
        fileName: String
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                fileName
            )
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                "application/zip"
            )
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/AppForge"
            )
            put(
                MediaStore.MediaColumns.IS_PENDING,
                1
            )
        }

        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: error(
            "Downloads kaydı oluşturulamadı."
        )

        try {
            resolver.openOutputStream(uri)
                ?.buffered()
                ?.use { output ->
                    zipSource(
                        sourceRoot,
                        ZipOutputStream(output)
                    )
                }
                ?: error(
                    "Kaynak ZIP çıkışı açılamadı."
                )

            val publish = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    0
                )
            }

            resolver.update(
                uri,
                publish,
                null,
                null
            )
        } catch (error: Throwable) {
            runCatching {
                resolver.delete(
                    uri,
                    null,
                    null
                )
            }
            throw error
        }

        return "Downloads/AppForge/$fileName"
    }

    private fun exportLegacy(
        context: Context,
        sourceRoot: File,
        fileName: String
    ): String {
        val directory =
            context.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ) ?: error(
                "Uygulama download klasörü kullanılamıyor."
            )

        val out = File(
            directory,
            fileName
        )

        out.outputStream()
            .buffered()
            .use { output ->
                zipSource(
                    sourceRoot,
                    ZipOutputStream(output)
                )
            }

        return out.absolutePath
    }

    private fun zipSource(
        sourceRoot: File,
        zip: ZipOutputStream
    ) {
        zip.use { output ->
            var fileCount = 0
            var totalBytes = 0L

            sourceRoot
                .walkTopDown()
                .onEnter { directory ->
                    !Files.isSymbolicLink(
                        directory.toPath()
                    ) &&
                        inside(
                            sourceRoot,
                            directory.canonicalFile
                        )
                }
                .filter {
                    it.isFile &&
                        inside(
                            sourceRoot,
                            it.canonicalFile
                        )
                }
                .forEach { file ->
                    require(
                        !Files.isSymbolicLink(
                            file.toPath()
                        )
                    ) {
                        "Kaynak ZIP symbolic link içeremez."
                    }

                    val relative =
                        file.relativeTo(sourceRoot)
                            .invariantSeparatorsPath

                    validateExportPath(relative)

                    fileCount += 1
                    require(fileCount <= MAX_FILES) {
                        "Kaynak ZIP dosya sayısı sınırı aşıldı."
                    }

                    totalBytes += file.length()
                    require(
                        totalBytes <=
                            MAX_TOTAL_BYTES
                    ) {
                        "Kaynak ZIP toplam boyut sınırı aşıldı."
                    }

                    output.putNextEntry(
                        ZipEntry(relative)
                    )

                    file.inputStream()
                        .buffered()
                        .use { input ->
                            input.copyTo(
                                output,
                                64 * 1024
                            )
                        }

                    output.closeEntry()
                }
        }
    }

    private fun validateExportPath(
        relative: String
    ) {
        val parts = relative
            .replace('\\', '/')
            .split('/')

        require(
            parts.none {
                it.isBlank() ||
                    it == "." ||
                    it == ".."
            }
        ) {
            "Güvensiz kaynak export yolu."
        }

        val leaf =
            parts.last().lowercase()

        require(
            leaf !in forbiddenLeafNames
        ) {
            "Hassas dosya export'a dahil edilemez."
        }

        val extension =
            leaf.substringAfterLast(
                '.',
                missingDelimiterValue = ""
            )

        require(
            extension !in forbiddenExtensions
        ) {
            "Hassas dosya uzantısı export'a dahil edilemez."
        }
    }

    private fun inside(
        root: File,
        candidate: File
    ): Boolean {
        val rootCanonical =
            root.canonicalFile

        val candidateCanonical =
            candidate.canonicalFile

        return (
            candidateCanonical == rootCanonical ||
                candidateCanonical.path.startsWith(
                    rootCanonical.path
                        .trimEnd(File.separatorChar) +
                        File.separator
                )
            )
    }
}
