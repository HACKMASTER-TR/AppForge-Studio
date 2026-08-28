package com.appforge.studio.io

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.Deflater

object ZipUtils {
    data class CachedZipResult(
        val file: File,
        val cacheHit: Boolean
    )

    fun cachedZipDirectory(
        sourceDir: File,
        cacheDir: File
    ): CachedZipResult {
        require(sourceDir.isDirectory) { "Kaynak klasör bulunamadı." }

        val fingerprint = sourceFingerprint(sourceDir)
        val safeProject = sha256(sourceDir.canonicalPath).take(16)
        val targetDir = File(cacheDir, safeProject).apply { mkdirs() }
        val outFile = File(targetDir, "$fingerprint.zip")

        if (outFile.isFile && outFile.length() > 0L) {
            return CachedZipResult(outFile, true)
        }

        targetDir.listFiles()
            ?.filter { it.isFile && it != outFile }
            ?.forEach { it.delete() }

        return CachedZipResult(
            file = zipDirectory(sourceDir, outFile),
            cacheHit = false
        )
    }

    private fun sourceFingerprint(sourceDir: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        sourceDir.walkTopDown()
            .filter { it.isFile }
            .map {
                val relative = it.relativeTo(sourceDir).invariantSeparatorsPath
                "$relative\u0000${it.length()}\u0000${it.lastModified()}\n"
            }
            .sorted()
            .forEach { digest.update(it.toByteArray(Charsets.UTF_8)) }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun zipDirectory(sourceDir: File, outFile: File): File {
        require(sourceDir.isDirectory) { "Kaynak klasör bulunamadı." }
        outFile.parentFile?.mkdirs()

        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            // Kaynak paketleri zaten çoğunlukla sıkıştırılmış medya içeriyor.
            // BEST_SPEED, upload boyutunu makul tutarken büyük projelerde hazırlık
            // süresini belirgin biçimde azaltır.
            zip.setLevel(Deflater.BEST_SPEED)
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val rel = file.relativeTo(sourceDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(rel))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return outFile
    }
}
