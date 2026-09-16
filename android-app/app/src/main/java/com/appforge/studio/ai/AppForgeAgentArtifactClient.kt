package com.appforge.studio.ai

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

    fun enqueueDownload(
        buildId: String,
        kind: String
    ): Long {
        val safeKind =
            AppForgeAgentArtifactSafety.safeKind(kind)

        val ticket =
            client.createDownloadTicket(
                buildId = buildId,
                kind = safeKind
            )

        val uri = Uri.parse(ticket.url)

        require(
            uri.scheme.equals(
                "https",
                ignoreCase = true
            )
        ) {
            "Artifact download yalnız HTTPS ile yapılabilir."
        }

        val manager =
            appContext.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as? DownloadManager
                ?: error(
                    "Android DownloadManager kullanılamıyor."
                )

        val request =
            DownloadManager.Request(uri)
                .setTitle(
                    AppForgeAgentArtifactSafety.fileName(
                        buildId,
                        safeKind
                    )
                )
                .setDescription(
                    "AppForge Unified Agent artifact"
                )
                .setMimeType(
                    when (safeKind) {
                        "apk" ->
                            "application/vnd.android.package-archive"

                        "exe" ->
                            "application/vnd.microsoft.portable-executable"

                        else ->
                            "application/octet-stream"
                    }
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )

        return manager.enqueue(request)
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
