package com.appforge.studio.build

import android.content.Context
import java.security.MessageDigest

/** Metadata generated from the exact build outputs, then protected by the Studio APK signature. */
internal object TemplateIntegrity {
    fun expected(context: Context, name: String): String {
        val rows = context.assets.open("html-template-integrity.txt").bufferedReader(Charsets.US_ASCII).use { it.readLines() }
        return parse(rows, name)
    }
    internal fun parse(rows: List<String>, name: String): String {
        require(rows.size == 3 && rows.distinct().size == 3) { "Invalid CI template metadata." }
        val values = rows.map {
            val parts = it.split(' ')
            require(parts.size == 2 && parts[1].matches(Regex("[a-f0-9]{64}"))) { "Invalid template pin." }
            parts[0] to parts[1]
        }
        require(values.map { it.first }.toSet() == setOf("html-apk-template.apk", "html-aab-template.aab", "html-aab-manifest"))
        return values.single { it.first == name }.second
    }
    /** Bounded read-only preflight; never downloads or regenerates a missing template. */
    fun requireBundled(context: Context, names: Set<String>) {
        require(names.isNotEmpty() && names.all { it in setOf("html-apk-template.apk", "html-aab-template.aab") })
        for (name in names) {
            val limit = if (name.endsWith(".apk")) 12 * 1024 * 1024 else 40 * 1024 * 1024
            val buffer = java.io.ByteArrayOutputStream()
            context.assets.open(name).use { input ->
                val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    require(buffer.size().toLong() + count <= limit) { "Bundled template exceeds size limit." }
                    buffer.write(chunk, 0, count)
                }
            }
            require(buffer.size() >= 1024) { "Bundled template missing or truncated." }
            requireAsset(context, name, buffer.toByteArray())
        }
    }

    fun requireAsset(context: Context, name: String, bytes: ByteArray) {
        requireDigest(bytes, expected(context, name))
    }
    internal fun requireDigest(bytes: ByteArray, expected: String) {
        require(expected.matches(Regex("[a-f0-9]{64}")))
        require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 255)
        } == expected) { "CI template integrity mismatch; build blocked." }
    }
}
