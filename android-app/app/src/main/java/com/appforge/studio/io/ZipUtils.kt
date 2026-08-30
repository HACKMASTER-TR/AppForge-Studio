package com.appforge.studio.io

import java.io.File
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    data class CachedZipResult(
        val file: File,
        val cacheHit: Boolean
    )

    fun cachedZipDirectory(
        sourceDir: File,
        cacheDir: File
    ): CachedZipResult {
        require(
            sourceDir.isDirectory
        ) {
            "Kaynak klasör bulunamadı."
        }

        /*
         * Fingerprint artık mtime yerine gerçek dosya
         * içeriğinden oluşturulur.
         *
         * Böylece:
         * - timestamp değişikliği gereksiz cache MISS oluşturmaz
         * - aynı boyutta değişmiş dosya yanlışlıkla eski ZIP'i kullanmaz
         */
        val fingerprint =
            sourceFingerprint(
                sourceDir
            )

        val safeProject =
            sha256(
                sourceDir.canonicalPath
            ).take(16)

        val targetDir =
            File(
                cacheDir,
                safeProject
            ).apply {
                mkdirs()
            }

        val outFile =
            File(
                targetDir,
                "$fingerprint.zip"
            )

        if (
            outFile.isFile &&
            outFile.length() > 0L
        ) {
            return CachedZipResult(
                outFile,
                true
            )
        }

        targetDir
            .listFiles()
            ?.filter {
                it.isFile &&
                    it != outFile
            }
            ?.forEach {
                it.delete()
            }

        return CachedZipResult(
            file =
                zipDirectory(
                    sourceDir,
                    outFile
                ),
            cacheHit = false
        )
    }

    private fun projectFiles(
        sourceDir: File
    ): List<File> =
        sourceDir
            .walkTopDown()
            .filter {
                it.isFile
            }
            .sortedBy {
                it.relativeTo(
                    sourceDir
                ).invariantSeparatorsPath
            }
            .toList()

    private fun sourceFingerprint(
        sourceDir: File
    ): String {
        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )

        val separator =
            byteArrayOf(0)

        val newline =
            "\n".toByteArray(
                Charsets.UTF_8
            )

        val buffer =
            ByteArray(
                1024 * 1024
            )

        projectFiles(
            sourceDir
        ).forEach { file ->
            val relative =
                file.relativeTo(
                    sourceDir
                ).invariantSeparatorsPath

            digest.update(
                relative.toByteArray(
                    Charsets.UTF_8
                )
            )

            digest.update(
                separator
            )

            digest.update(
                file.length()
                    .toString()
                    .toByteArray(
                        Charsets.UTF_8
                    )
            )

            digest.update(
                separator
            )

            file.inputStream()
                .buffered()
                .use { input ->
                    while (true) {
                        val count =
                            input.read(
                                buffer
                            )

                        if (
                            count < 0
                        ) {
                            break
                        }

                        if (
                            count > 0
                        ) {
                            digest.update(
                                buffer,
                                0,
                                count
                            )
                        }
                    }
                }

            digest.update(
                newline
            )
        }

        return digest
            .digest()
            .joinToString(
                ""
            ) {
                "%02x".format(it)
            }
    }

    private fun sha256(
        value: String
    ): String =
        MessageDigest
            .getInstance(
                "SHA-256"
            )
            .digest(
                value.toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString(
                ""
            ) {
                "%02x".format(it)
            }

    fun zipDirectory(
        sourceDir: File,
        outFile: File
    ): File {
        require(
            sourceDir.isDirectory
        ) {
            "Kaynak klasör bulunamadı."
        }

        outFile
            .parentFile
            ?.mkdirs()

        ZipOutputStream(
            outFile
                .outputStream()
                .buffered()
        ).use { zip ->
            /*
             * Kaynak paketleri çoğunlukla zaten sıkıştırılmış
             * medya / binary dosyaları içeriyor.
             */
            zip.setLevel(
                Deflater.BEST_SPEED
            )

            /*
             * Dosyalar her zaman aynı sırada yazılır.
             * ZIP entry zamanı sabitlenir.
             *
             * Aynı kaynak klasörü farklı zamanda tekrar
             * paketlense bile aynı ZIP byte dizisi üretilir.
             */
            projectFiles(
                sourceDir
            ).forEach { file ->
                val rel =
                    file.relativeTo(
                        sourceDir
                    ).invariantSeparatorsPath

                val entry =
                    ZipEntry(
                        rel
                    ).apply {
                        time = 0L
                    }

                zip.putNextEntry(
                    entry
                )

                file.inputStream()
                    .buffered()
                    .use {
                        it.copyTo(
                            zip
                        )
                    }

                zip.closeEntry()
            }
        }

        return outFile
    }
}
