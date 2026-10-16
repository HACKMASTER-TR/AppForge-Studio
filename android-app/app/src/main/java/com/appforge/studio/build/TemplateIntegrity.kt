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
