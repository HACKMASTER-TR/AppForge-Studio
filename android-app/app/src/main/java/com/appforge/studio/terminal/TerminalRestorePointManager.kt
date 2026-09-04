package com.appforge.studio.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class TerminalRestorePoint(
    val archive: File,
    val createdAt: Long,
    val sizeBytes: Long
)

internal data class TerminalRestoreCreateResult(
    val point: TerminalRestorePoint,
    val archivedFiles: Int,
    val skippedSensitive: Int,
    val skippedLarge: Int
)

internal data class TerminalRestoreResult(
    val restoredFiles: Int,
    val safetyBackup: TerminalRestorePoint?
)

internal object TerminalRestorePointManager {
    private const val MAX_ARCHIVE_BYTES =
        64L * 1024L * 1024L

    private const val MAX_SINGLE_FILE_BYTES =
        8L * 1024L * 1024L

    private const val MAX_FILE_COUNT =
        10_000

    private const val MAX_RESTORE_POINTS =
        5

    private val excludedDirectories =
        setOf(
            ".git",
            ".gradle",
            ".idea",
            ".next",
            ".dart_tool",
            "node_modules",
            "build",
            "dist",
            "target"
        )

    private val sensitiveNames =
        setOf(
            ".env",
            "id_rsa",
            "id_ed25519",
            "credentials.json",
            "service-account.json",
            "google-services.json",
            "google-services-info.plist"
        )

    private val sensitiveExtensions =
        setOf(
            "jks",
            "keystore",
            "p12",
            "pfx",
            "pem",
            "key"
        )

    suspend fun create(
        context: Context,
        workspace: File,
        label: String = "manual"
    ): TerminalRestoreCreateResult =
        withContext(Dispatchers.IO) {
            val root =
                requireWorkspace(
                    workspace
                )

            val directory =
                restoreDirectory(
                    context,
                    root
                ).apply {
                    mkdirs()
                }

            val cleanLabel =
                label
                    .lowercase(Locale.ROOT)
                    .replace(
                        Regex(
                            "[^a-z0-9._-]+"
                        ),
                        "-"
                    )
                    .trim('-')
                    .take(32)
                    .ifBlank {
                        "restore"
                    }

            val timestamp =
                System.currentTimeMillis()

            val temp =
                File(
                    directory,
                    "$timestamp-$cleanLabel.zip.part"
                )

            val target =
                File(
                    directory,
                    "$timestamp-$cleanLabel.zip"
                )

            var archived =
                0

            var skippedSensitive =
                0

            var skippedLarge =
                0

            var totalInputBytes =
                0L

            try {
                ZipOutputStream(
                    BufferedOutputStream(
                        FileOutputStream(
                            temp
                        )
                    )
                ).use { zip ->
                    root.walkTopDown()
                        .onEnter { directoryEntry ->
                            if (
                                directoryEntry ==
                                root
                            ) {
                                true
                            } else {
                                directoryEntry
                                    .name
                                    .lowercase(
                                        Locale.ROOT
                                    ) !in
                                    excludedDirectories
                            }
                        }
                        .filter {
                            it.isFile
                        }
                        .forEach { file ->
                            if (
                                archived >=
                                MAX_FILE_COUNT
                            ) {
                                error(
                                    "Restore point dosya sayısı güvenlik sınırını aştı."
                                )
                            }

                            val relative =
                                relativePath(
                                    root,
                                    file
                                )

                            if (
                                isSensitive(
                                    relative
                                )
                            ) {
                                skippedSensitive +=
                                    1
                                return@forEach
                            }

                            val length =
                                file.length()

                            if (
                                length >
                                MAX_SINGLE_FILE_BYTES
                            ) {
                                skippedLarge +=
                                    1
                                return@forEach
                            }

                            require(
                                totalInputBytes +
                                    length <=
                                    MAX_ARCHIVE_BYTES
                            ) {
                                "Restore point 64 MiB güvenlik sınırını aştı."
                            }

                            zip.putNextEntry(
                                ZipEntry(
                                    relative
                                )
                            )

                            FileInputStream(
                                file
                            ).use { input ->
                                input.copyTo(
                                    zip,
                                    bufferSize =
                                        32 * 1024
                                )
                            }

                            zip.closeEntry()

                            totalInputBytes +=
                                length

                            archived +=
                                1
                        }
                }

                require(
                    archived > 0
                ) {
                    "Restore point içine alınabilecek güvenli dosya bulunamadı."
                }

                require(
                    temp.length() <=
                    MAX_ARCHIVE_BYTES
                ) {
                    "Restore point arşivi güvenlik sınırını aştı."
                }

                require(
                    temp.renameTo(
                        target
                    )
                ) {
                    "Restore point atomik olarak etkinleştirilemedi."
                }

                prune(
                    directory
                )

                TerminalRestoreCreateResult(
                    point =
                        toRestorePoint(
                            target
                        ),
                    archivedFiles =
                        archived,
                    skippedSensitive =
                        skippedSensitive,
                    skippedLarge =
                        skippedLarge
                )
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        }

    suspend fun list(
        context: Context,
        workspace: File
    ): List<TerminalRestorePoint> =
        withContext(Dispatchers.IO) {
            val root =
                requireWorkspace(
                    workspace
                )

            restoreDirectory(
                context,
                root
            )
                .listFiles { file ->
                    file.isFile &&
                        file.name.endsWith(
                            ".zip"
                        )
                }
                .orEmpty()
                .map(
                    ::toRestorePoint
                )
                .sortedByDescending {
                    it.createdAt
                }
                .take(
                    MAX_RESTORE_POINTS
                )
        }

    suspend fun restoreOverlay(
        context: Context,
        workspace: File,
        point: TerminalRestorePoint
    ): TerminalRestoreResult =
        withContext(Dispatchers.IO) {
            val root =
                requireWorkspace(
                    workspace
                )

            val directory =
                restoreDirectory(
                    context,
                    root
                )

            val archive =
                point.archive
                    .canonicalFile

            require(
                archive.isFile &&
                    archive.path.startsWith(
                        directory
                            .canonicalFile
                            .path +
                            File.separator
                    )
            ) {
                "Restore point çalışma alanına ait değil."
            }

            val safetyBackup =
                runCatching {
                    create(
                        context,
                        root,
                        "before-restore"
                    ).point
                }.getOrNull()

            val staging =
                File(
                    context.cacheDir,
                    "terminal-restore-${
                        System.nanoTime()
                    }"
                )

            require(
                staging.mkdirs()
            ) {
                "Restore staging klasörü oluşturulamadı."
            }

            var extractedFiles =
                0

            var extractedBytes =
                0L

            try {
                ZipInputStream(
                    BufferedInputStream(
                        FileInputStream(
                            archive
                        )
                    )
                ).use { zip ->
                    while (true) {
                        val entry =
                            zip.nextEntry
                                ?: break

                        val cleanName =
                            validateEntryName(
                                entry.name
                            )

                        require(
                            !entry.isDirectory
                        ) {
                            "Beklenmeyen klasör girdisi."
                        }

                        require(
                            !isSensitive(
                                cleanName
                            )
                        ) {
                            "Restore point hassas dosya içeriyor."
                        }

                        extractedFiles +=
                            1

                        require(
                            extractedFiles <=
                                MAX_FILE_COUNT
                        ) {
                            "Restore point dosya sayısı güvenlik sınırını aştı."
                        }

                        val output =
                            File(
                                staging,
                                cleanName
                            )
                                .canonicalFile

                        requireInside(
                            staging,
                            output
                        )

                        output.parentFile
                            ?.mkdirs()

                        FileOutputStream(
                            output
                        ).use { fileOutput ->
                            val buffer =
                                ByteArray(
                                    32 * 1024
                                )

                            var entryBytes =
                                0L

                            while (true) {
                                val read =
                                    zip.read(
                                        buffer
                                    )

                                if (read < 0) {
                                    break
                                }

                                entryBytes +=
                                    read

                                extractedBytes +=
                                    read

                                require(
                                    entryBytes <=
                                        MAX_SINGLE_FILE_BYTES &&
                                        extractedBytes <=
                                        MAX_ARCHIVE_BYTES
                                ) {
                                    "Restore point çıkarma güvenlik sınırını aştı."
                                }

                                fileOutput.write(
                                    buffer,
                                    0,
                                    read
                                )
                            }
                        }

                        zip.closeEntry()
                    }
                }

                var restored =
                    0

                staging.walkTopDown()
                    .filter {
                        it.isFile
                    }
                    .forEach { source ->
                        val relative =
                            relativePath(
                                staging,
                                source
                            )

                        require(
                            !isSensitive(
                                relative
                            )
                        ) {
                            "Hassas dosya geri yükleme engellendi."
                        }

                        val target =
                            File(
                                root,
                                relative
                            )
                                .canonicalFile

                        requireInside(
                            root,
                            target
                        )

                        target.parentFile
                            ?.mkdirs()

                        source.copyTo(
                            target,
                            overwrite =
                                true
                        )

                        restored +=
                            1
                    }

                TerminalRestoreResult(
                    restoredFiles =
                        restored,
                    safetyBackup =
                        safetyBackup
                )
            } finally {
                staging.deleteRecursively()
            }
        }

    internal fun isSensitive(
        relativePath: String
    ): Boolean {
        val normalized =
            relativePath
                .replace(
                    '\\',
                    '/'
                )
                .lowercase(
                    Locale.ROOT
                )

        val segments =
            normalized.split('/')

        if (
            segments.any {
                it in
                    setOf(
                        ".ssh",
                        ".gnupg"
                    )
            }
        ) {
            return true
        }

        val name =
            segments.lastOrNull()
                .orEmpty()

        if (
            name in sensitiveNames ||
            name.startsWith(
                ".env."
            )
        ) {
            return true
        }

        val extension =
            name.substringAfterLast(
                '.',
                ""
            )

        return extension in
            sensitiveExtensions
    }

    internal fun validateEntryName(
        value: String
    ): String {
        val clean =
            value
                .replace(
                    '\\',
                    '/'
                )
                .trim()

        require(
            clean.isNotBlank() &&
                clean.length <=
                    2_048 &&
                !clean.startsWith("/") &&
                !clean.startsWith("../") &&
                !clean.contains("/../") &&
                !clean.contains(
                    '\u0000'
                )
        ) {
            "Geçersiz restore point girdisi."
        }

        return clean
    }

    private fun relativePath(
        root: File,
        file: File
    ): String =
        file
            .canonicalFile
            .relativeTo(
                root.canonicalFile
            )
            .invariantSeparatorsPath

    private fun requireInside(
        root: File,
        target: File
    ) {
        val safeRoot =
            root.canonicalFile

        val safeTarget =
            target.canonicalFile

        require(
            safeTarget.path.startsWith(
                safeRoot.path +
                    File.separator
            )
        ) {
            "Restore point yolu çalışma alanı dışında."
        }
    }

    private fun requireWorkspace(
        workspace: File
    ): File {
        val safe =
            workspace.canonicalFile

        require(
            safe.isDirectory &&
                safe.canRead() &&
                safe.canWrite()
        ) {
            "Çalışma alanına erişilemiyor."
        }

        return safe
    }

    private fun restoreDirectory(
        context: Context,
        workspace: File
    ): File {
        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )
                .digest(
                    workspace
                        .canonicalPath
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
                .joinToString("") {
                    "%02x".format(it)
                }
                .take(24)

        return File(
            context.filesDir,
            "terminal/restore/$digest"
        )
    }

    private fun toRestorePoint(
        file: File
    ): TerminalRestorePoint =
        TerminalRestorePoint(
            archive =
                file,
            createdAt =
                file.name
                    .substringBefore('-')
                    .toLongOrNull()
                    ?: file.lastModified(),
            sizeBytes =
                file.length()
        )

    private fun prune(
        directory: File
    ) {
        directory
            .listFiles { file ->
                file.isFile &&
                    file.name.endsWith(
                        ".zip"
                    )
            }
            .orEmpty()
            .sortedByDescending {
                it.lastModified()
            }
            .drop(
                MAX_RESTORE_POINTS
            )
            .forEach {
                it.delete()
            }
    }
}
