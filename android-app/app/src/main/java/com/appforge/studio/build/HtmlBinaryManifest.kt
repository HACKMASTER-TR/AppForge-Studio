package com.appforge.studio.build

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** Strict Android binary XML string-pool writer for the pinned, generated HTML shell. */
internal object HtmlBinaryManifest {
    private const val XML = 0x0003
    private const val STRINGS = 0x0001
    private const val START_ELEMENT = 0x0102
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val UTF8_FLAG = 0x100
    private const val TEMPLATE_PACKAGE = "com.appforge.htmlstub"
    private const val TEMPLATE_LABEL = "APPFORGE_HTML_APP"

    fun rewrite(
        input: ByteArray,
        packageName: String,
        appName: String,
        versionCode: Int,
        versionName: String,
        minSdk: Int,
        targetSdk: Int
    ): ByteArray {
        require(input.size in 64..1_048_576) { "Geçersiz AndroidManifest.xml boyutu." }
        require(u16(input, 0) == XML && u16(input, 2) == 8 && u32(input, 4) == input.size) {
            "Android binary manifest başlığı hatalı."
        }
        require(versionCode > 0 && minSdk in 26..37 && targetSdk in minSdk..37)
        require(packageName.matches(Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")))
        require(appName.isNotBlank() && appName.length <= 100 && versionName.length in 1..64)
        require(appName.none { it == '\u0000' } && versionName.none { it == '\u0000' })

        val source = input.clone()
        var poolStart = -1
        var poolEnd = -1
        var strings = emptyList<String>()
        var isUtf8 = false
        var offset = 8
        var manifestSeen = 0
        var versionCodeSeen = 0
        var versionNameIndex = -1
        var minSdkSeen = 0
        var targetSdkSeen = 0
        var providers = 0
        while (offset + 8 <= source.size) {
            val kind = u16(source, offset)
            val header = u16(source, offset + 2)
            val length = u32(source, offset + 4)
            require(header >= 8 && length >= header && offset + length <= source.size) {
                "Android manifest chunk sınırı geçersiz."
            }
            if (kind == STRINGS) {
                require(poolStart == -1 && header == 28) { "Beklenmeyen string pool yapısı." }
                val count = u32(source, offset + 8)
                val styleCount = u32(source, offset + 12)
                val flags = u32(source, offset + 16)
                val first = u32(source, offset + 20)
                require(styleCount == 0 && count in 1..4096 && first >= 28 + 4 * count)
                require(offset + first < offset + length)
                isUtf8 = flags and UTF8_FLAG != 0
                strings = (0 until count).map { i ->
                    val relative = u32(source, offset + 28 + i * 4)
                    val at = offset + first + relative
                    require(at >= offset + first && at < offset + length)
                    readText(source, at, isUtf8)
                }
                poolStart = offset
                poolEnd = offset + length
            } else if (kind == START_ELEMENT) {
                require(poolStart >= 0 && header == 16 && length >= 36)
                val elementName = strings.getOrNull(u32(source, offset + 20))
                if (elementName == "manifest") manifestSeen++
                if (elementName == "provider") providers++
                val providerAttributes = mutableMapOf<String, Int>()
                val attrsStart = u16(source, offset + 24) + offset + 16
                val attrSize = u16(source, offset + 26)
                val attrCount = u16(source, offset + 28)
                require(attrSize == 20 && attrsStart >= offset + 36 && attrsStart + attrCount * 20 <= offset + length)
                repeat(attrCount) { i ->
                    val at = attrsStart + i * 20
                    val attributeName = strings.getOrNull(u32(source, at + 4))
                    if (elementName == "provider") {
                        require(attributeName != null && providerAttributes.put(attributeName, at) == null)
                    }
                    when (attributeName) {
                        "versionCode" -> if (elementName == "manifest") {
                            require(source[at + 15].toInt() and 255 == TYPE_INT_DEC)
                            put32(source, at + 16, versionCode)
                            versionCodeSeen++
                        }
                        "versionName" -> if (elementName == "manifest") {
                            require(source[at + 15].toInt() and 255 == TYPE_STRING)
                            versionNameIndex = u32(source, at + 16)
                        }
                        "minSdkVersion" -> if (elementName == "uses-sdk") {
                            require(source[at + 15].toInt() and 255 == TYPE_INT_DEC)
                            put32(source, at + 16, minSdk)
                            minSdkSeen++
                        }
                        "targetSdkVersion" -> if (elementName == "uses-sdk") {
                            require(source[at + 15].toInt() and 255 == TYPE_INT_DEC)
                            put32(source, at + 16, targetSdk)
                            targetSdkSeen++
                        }
                    }
                }
                if (elementName == "provider") {
                    fun stringAttribute(name: String): String? {
                        val at = providerAttributes[name] ?: error("Missing provider $name")
                        require(source[at + 15].toInt() and 255 == TYPE_STRING)
                        return strings.getOrNull(u32(source, at + 16))
                    }
                    require(stringAttribute("name") == "$TEMPLATE_PACKAGE.AppForgeShareProvider")
                    require(stringAttribute("authorities") == "$TEMPLATE_PACKAGE.appforge.documents")
                    for ((name, enabled) in listOf("exported" to false, "grantUriPermissions" to true)) {
                        val at = providerAttributes[name] ?: error("Missing provider $name")
                        require(source[at + 15].toInt() and 255 == 0x12)
                        require((0..3).any { source[at + 16 + it].toInt() != 0 } == enabled)
                    }
                }
            }
            offset += length
        }
        require(offset == source.size && poolStart >= 0 && manifestSeen == 1 && providers == 1)
        require(versionCodeSeen == 1 && versionNameIndex in strings.indices)
        require(minSdkSeen == 1 && targetSdkSeen == 1)
        val mutable = strings.toMutableList()
        require(mutable.count { it == TEMPLATE_PACKAGE } == 1) { "Şablon package işareti bulunamadı." }
        require(mutable.count { it == TEMPLATE_LABEL } == 1) { "Şablon label işareti bulunamadı." }
        val authority = "$TEMPLATE_PACKAGE.appforge.documents"
        require(mutable.count { it == authority } == 1) { "Missing unique share authority." }
        require(packageName != TEMPLATE_PACKAGE) { "Shell package must be replaced." }
        mutable[mutable.indexOf(authority)] = "$packageName.appforge.documents"
        mutable[mutable.indexOf(TEMPLATE_PACKAGE)] = packageName
        mutable[mutable.indexOf(TEMPLATE_LABEL)] = appName
        mutable[versionNameIndex] = versionName

        val pool = encodePool(mutable, isUtf8)
        val out = ByteArrayOutputStream(source.size + pool.size)
        out.write(source, 0, poolStart)
        out.write(pool)
        out.write(source, poolEnd, source.size - poolEnd)
        val result = out.toByteArray()
        put32(result, 4, result.size)
        return result
    }

    private fun encodePool(strings: List<String>, utf8: Boolean): ByteArray {
        val bytes = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        for (value in strings) {
            offsets += bytes.size()
            if (utf8) {
                val raw = value.toByteArray(Charsets.UTF_8)
                writeLen8(bytes, value.length)
                writeLen8(bytes, raw.size)
                bytes.write(raw)
                bytes.write(0)
            } else {
                val raw = value.toByteArray(Charsets.UTF_16LE)
                writeLen16(bytes, value.length)
                bytes.write(raw)
                bytes.write(0); bytes.write(0)
            }
        }
        while (bytes.size() % 4 != 0) bytes.write(0)
        val payload = bytes.toByteArray()
        val start = 28 + strings.size * 4
        val out = ByteArray(start + payload.size)
        put16(out, 0, STRINGS)
        put16(out, 2, 28)
        put32(out, 4, out.size)
        put32(out, 8, strings.size)
        put32(out, 12, 0)
        put32(out, 16, if (utf8) UTF8_FLAG else 0)
        put32(out, 20, start)
        put32(out, 24, 0)
        offsets.forEachIndexed { i, value -> put32(out, 28 + i * 4, value) }
        payload.copyInto(out, start)
        return out
    }

    private fun readText(raw: ByteArray, from: Int, utf8: Boolean): String {
        var at = from
        if (utf8) {
            val (_, skip) = readLen8(raw, at); at += skip
            val (length, width) = readLen8(raw, at); at += width
            require(length >= 0 && length <= 65536 && at + length < raw.size && raw[at + length] == 0.toByte())
            return String(raw, at, length, Charsets.UTF_8)
        }
        val (chars, width) = readLen16(raw, at); at += width
        val count = chars * 2
        require(chars in 0..65536 && at + count + 1 < raw.size)
        return String(raw, at, count, Charsets.UTF_16LE)
    }

    private fun readLen8(b: ByteArray, i: Int): Pair<Int, Int> {
        require(i in b.indices)
        val first = b[i].toInt() and 255
        if (first and 128 == 0) return first to 1
        require(i + 1 < b.size)
        return (((first and 127) shl 8) or (b[i + 1].toInt() and 255)) to 2
    }

    private fun readLen16(b: ByteArray, i: Int): Pair<Int, Int> {
        val first = u16(b, i)
        if (first and 32768 == 0) return first to 2
        return (((first and 32767) shl 16) or u16(b, i + 2)) to 4
    }

    private fun writeLen8(out: ByteArrayOutputStream, length: Int) {
        require(length in 0..32767)
        if (length < 128) out.write(length)
        else { out.write((length ushr 8) or 128); out.write(length and 255) }
    }
    private fun writeLen16(out: ByteArrayOutputStream, length: Int) {
        if (length < 32768) { out.write(length and 255); out.write(length ushr 8) }
        else {
            val hi = (length ushr 16) or 32768
            out.write(hi and 255); out.write(hi ushr 8)
            out.write(length and 255); out.write(length ushr 8)
        }
    }
    private fun u16(b: ByteArray, i: Int): Int {
        require(i >= 0 && i + 1 < b.size)
        return (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)
    }
    private fun u32(b: ByteArray, i: Int): Int {
        require(i >= 0 && i + 3 < b.size)
        val value = (b[i].toInt() and 255) or ((b[i+1].toInt() and 255) shl 8) or
            ((b[i+2].toInt() and 255) shl 16) or ((b[i+3].toInt() and 255) shl 24)
        require(value >= 0) { "Android binary XML boyutu/indeksi geçersiz." }
        return value
    }
    private fun put16(b: ByteArray, i: Int, n: Int) {
        b[i] = n.toByte(); b[i + 1] = (n ushr 8).toByte()
    }
    private fun put32(b: ByteArray, i: Int, n: Int) {
        b[i] = n.toByte(); b[i+1] = (n ushr 8).toByte()
        b[i+2] = (n ushr 16).toByte(); b[i+3] = (n ushr 24).toByte()
    }
}
