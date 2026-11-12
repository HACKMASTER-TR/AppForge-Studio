package com.appforge.studio.io

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.security.MessageDigest

/** PDF source is a private, bounded document, never a URI supplied to the generated reader. */
object PdfProjectSource {
    const val MAX_BYTES = 32L * 1024 * 1024
    const val MAX_PAGES = 1000
    const val DOCUMENT = "appforge-document.pdf"
    const val MARKER = "appforge-pdf.sha256"

    fun validate(file: File): Int {
        require(!java.nio.file.Files.isSymbolicLink(file.toPath()) && file.isFile && file.length() in 5..MAX_BYTES) {
            "PDF dosyası eksik veya 32 MiB sınırını aşıyor."
        }
        require(file.inputStream().use { input ->
            val header = ByteArray(5)
            var offset = 0
            while (offset < header.size) { val count = input.read(header, offset, header.size - offset); if (count <= 0) break; offset += count }
            offset == 5 && header.contentEquals("%PDF-".toByteArray())
        }) {
            "Geçersiz PDF başlığı."
        }
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount in 1..MAX_PAGES) { "PDF 1–1000 sayfa içermeli." }
                    renderer.openPage(0).use { require(it.width > 0 && it.height > 0) }
                    return renderer.pageCount
                }
            }
        } catch (error: SecurityException) {
            throw IllegalArgumentException("Parola korumalı PDF desteklenmiyor. Kilidi kaldırılmış bir kopya seçin.", error)
        } catch (error: java.io.IOException) {
            throw IllegalArgumentException("PDF okunamadı; belge bozuk veya desteklenmeyen biçimde.", error)
        }
    }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                total += count; require(total <= MAX_BYTES) { "PDF boyut sınırı aşıldı." }
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun prepare(root: File) {
        val document = File(root, DOCUMENT)
        validate(document)
        File(root, MARKER).writeText(digest(document))
        File(root, "index.html").writeText("<!doctype html><meta charset=utf-8><title>PDF</title><p>Bu belge uygulamanın çevrimdışı PDF okuyucusunda açılır.</p>")
    }
    fun isProject(root: File): Boolean {
        val marker = File(root, MARKER)
        val document = File(root, DOCUMENT)
        return root.isDirectory && !java.nio.file.Files.isSymbolicLink(root.toPath()) && marker.isFile && !java.nio.file.Files.isSymbolicLink(marker.toPath()) && marker.length() == 64L &&
            document.isFile && !java.nio.file.Files.isSymbolicLink(document.toPath()) && document.length() in 5..MAX_BYTES &&
            marker.readText().matches(Regex("[a-f0-9]{64}")) && marker.readText() == digest(document)
    }
}
