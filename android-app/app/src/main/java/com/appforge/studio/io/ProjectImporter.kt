package com.appforge.studio.io

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

data class ImportResult(
    val projectDir: File,
    val startPage: File
)

object ProjectImporter {
    private const val MAX_ENTRIES = 5_000
    private const val MAX_TOTAL_BYTES = 250L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 50L * 1024L * 1024L
    private const val MAX_PATH_LENGTH = 240
    private const val MAX_DEPTH = 20

    fun importLocalSource(
        context: Context,
        sourceUri: Uri,
        projectKey: String
    ): ImportResult {
        val finalDir =
            File(
                context.filesDir,
                "projects/$projectKey"
            )

        val tempDir =
            File(
                context.cacheDir,
                "appforge-import/${projectKey}-${System.nanoTime()}"
            )

        tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            val name =
                queryDisplayName(
                    context,
                    sourceUri
                ).lowercase()

            val input =
                context.contentResolver
                    .openInputStream(sourceUri)
                    ?: error("Dosya açılamadı.")

            input.use { stream ->
                if (name.endsWith(".zip")) {
                    extractZip(
                        stream,
                        tempDir
                    )
                } else {
                    val target =
                        File(
                            tempDir,
                            "index.html"
                        )

                    target.outputStream().use { out ->
                        copyLimited(
                            stream,
                            out,
                            MAX_ENTRY_BYTES
                        )
                    }
                }
            }

            val start =
                findStartPage(tempDir)
                    ?: error(
                        "Kaynak içinde index.html bulunamadı."
                    )

            finalDir.parentFile?.mkdirs()
            finalDir.deleteRecursively()

            if (!tempDir.renameTo(finalDir)) {
                tempDir.copyRecursively(
                    finalDir,
                    overwrite = true
                )
                tempDir.deleteRecursively()
            }

            val relative =
                start.relativeTo(tempDir).path

            return ImportResult(
                projectDir = finalDir,
                startPage =
                    File(
                        finalDir,
                        relative
                    )
            )
        } catch (t: Throwable) {
            tempDir.deleteRecursively()
            throw t
        }
    }

    private fun extractZip(
        input: InputStream,
        projectDir: File
    ) {
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry =
                    zip.nextEntry
                        ?: break

                entryCount++

                require(
                    entryCount <=
                        MAX_ENTRIES
                ) {
                    "ZIP çok fazla dosya içeriyor."
                }

                val normalized =
                    entry.name
                        .replace('\\', '/')
                        .trimStart('/')

                require(
                    normalized.length <=
                        MAX_PATH_LENGTH
                ) {
                    "ZIP içindeki dosya yolu çok uzun."
                }

                val depth =
                    normalized
                        .split('/')
                        .count {
                            it.isNotBlank()
                        }

                require(
                    depth <= MAX_DEPTH
                ) {
                    "ZIP klasör derinliği sınırı aşıldı."
                }

                val outFile =
                    safeChild(
                        projectDir,
                        normalized
                    )

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()

                    val written =
                        outFile
                            .outputStream()
                            .use { out ->
                                copyLimited(
                                    zip,
                                    out,
                                    MAX_ENTRY_BYTES
                                )
                            }

                    totalBytes += written

                    require(
                        totalBytes <=
                            MAX_TOTAL_BYTES
                    ) {
                        "ZIP açılmış boyut sınırını aşıyor."
                    }
                }

                zip.closeEntry()
            }
        }
    }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        limit: Long
    ): Long {
        val buffer =
            ByteArray(64 * 1024)

        var total = 0L

        while (true) {
            val read =
                input.read(buffer)

            if (read < 0) break

            total += read

            require(
                total <= limit
            ) {
                "Dosya boyutu güvenlik sınırını aşıyor."
            }

            output.write(
                buffer,
                0,
                read
            )
        }

        return total
    }

    private fun safeChild(
        parent: File,
        child: String
    ): File {
        val target =
            File(
                parent,
                child
            )

        val p =
            parent.canonicalFile

        val t =
            target.canonicalFile

        require(
            t.path == p.path ||
            t.path.startsWith(
                p.path +
                    File.separator
            )
        ) {
            "Güvensiz ZIP yolu engellendi."
        }

        return t
    }

    private fun findStartPage(
        dir: File
    ): File? =
        File(
            dir,
            "index.html"
        ).takeIf {
            it.isFile
        } ?: dir
            .walkTopDown()
            .firstOrNull {
                it.isFile &&
                it.name.equals(
                    "index.html",
                    true
                )
            }

    private fun queryDisplayName(
        context: Context,
        uri: Uri
    ): String {
        val projection =
            arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME
            )

        context.contentResolver
            .query(
                uri,
                projection,
                null,
                null,
                null
            )
            ?.use { c ->
                val idx =
                    c.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )

                if (
                    idx >= 0 &&
                    c.moveToFirst()
                ) {
                    return c.getString(idx)
                        ?: "source"
                }
            }

        return uri.lastPathSegment
            ?: "source"
    }
}
