package com.appforge.studio.build

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max

/**
 * Replaces existing RT_ICON/RT_GROUP_ICON in the per-project EXE copy in place.
 * Never modifies the verified generic Host, the PE section layout or NSIS overlay.
 * If selected icon resources cannot be embedded, output fails closed.
 */
internal object WindowsPeIconPatcher {
    private data class Section(val va: Long, val virtualSize: Long, val raw: Long, val rawSize: Long)
    private data class Resource(val id: Int, val lang: Int, val data: Long, val size: Long,
                                val entry: Long)

    fun install(context: Context, uriText: String, target: File): Int {
        val bitmap = context.contentResolver.openInputStream(Uri.parse(uriText))?.use {
            BitmapFactory.decodeStream(it)
        } ?: error("Windows EXE için seçilen uygulama ikonu okunamadı.")
        try {
            require(bitmap.width in 1..4096 && bitmap.height in 1..4096) {
                "Windows EXE için ikon boyutu geçersiz."
            }
            return RandomAccessFile(target, "rw").use { pe ->
                val originalLength = pe.length()
                require(pe.u16(0) == 0x5a4d) { "Windows EXE MZ başlığı geçersiz." }
                val nt = pe.u32(0x3c)
                require(nt in 64..(originalLength - 256)) { "Windows PE başlığı geçersiz." }
                require(pe.u32(nt) == 0x4550L) { "Windows PE imzası geçersiz." }
                val count = pe.u16(nt + 6)
                val optionalSize = pe.u16(nt + 20)
                val optional = nt + 24
                val magic = pe.u16(optional)
                val dataDirectory = when (magic) {
                    0x10b -> optional + 96
                    0x20b -> optional + 112
                    else -> error("Windows EXE PE formatı desteklenmiyor.")
                }
                require(count in 1..96 && optionalSize >= dataDirectory - optional + 24) {
                    "Windows PE bölüm tablosu geçersiz."
                }
                val resourceRva = pe.u32(dataDirectory + 16)
                require(resourceRva > 0) { "Windows Host ikon kaynakları bulunamadı." }
                val sections = (0 until count).map { index ->
                    val at = optional + optionalSize + index * 40L
                    Section(pe.u32(at + 12), pe.u32(at + 8),
                            pe.u32(at + 20), pe.u32(at + 16))
                }
                fun fileOffset(rva: Long): Long {
                    val section = sections.firstOrNull {
                        rva >= it.va && rva - it.va < max(it.virtualSize, it.rawSize)
                    } ?: error("Windows ikon kaynağı PE bölüm tablosunda bulunamadı.")
                    val delta = rva - section.va
                    require(delta < section.rawSize && section.raw + delta < originalLength) {
                        "Windows ikon kaynağı bölüm dışına çıkıyor."
                    }
                    return section.raw + delta
                }
                val resourceRoot = fileOffset(resourceRva)
                fun resourceOffset(relative: Long): Long {
                    require(relative in 0..16_777_216L) { "Windows kaynak dizini boyutu geçersiz." }
                    val result = resourceRoot + relative
                    require(result in 0..(originalLength - 24)) { "Windows kaynak dizini eksik." }
                    return result
                }
                fun children(directory: Long): List<Pair<Int,Long>> {
                    require(directory in 0..(originalLength - 16)) { "Windows kaynak dizini geçersiz." }
                    val countEntries = pe.u16(directory + 12) + pe.u16(directory + 14)
                    require(countEntries in 0..4096 && directory + 16 + countEntries * 8L < originalLength) {
                        "Windows kaynak dizini girdi sınırı aşıldı."
                    }
                    return (0 until countEntries).map { index ->
                        val at = directory + 16 + index * 8L
                        val id = pe.u32(at).toInt() and 0xffff
                        id to pe.u32(at + 4)
                    }
                }
                fun resources(type: Int): List<Resource> {
                    val typeEntry = children(resourceRoot).firstOrNull { it.first == type }
                        ?: return emptyList()
                    require(typeEntry.second and 0x8000_0000L != 0L) { "Windows kaynak tipi dizin değil." }
                    val typeDir = resourceOffset(typeEntry.second and 0x7fff_ffffL)
                    return children(typeDir).flatMap { (id, nameEntry) ->
                        require(nameEntry and 0x8000_0000L != 0L) { "Windows kaynak kimliği dizin değil." }
                        val nameDir = resourceOffset(nameEntry and 0x7fff_ffffL)
                        children(nameDir).map { (lang, languageEntry) ->
                            require(languageEntry and 0x8000_0000L == 0L) { "Windows kaynak dil girdisi geçersiz." }
                            val dataEntry = resourceOffset(languageEntry)
                            val data = fileOffset(pe.u32(dataEntry))
                            val size = pe.u32(dataEntry + 4)
                            require(size in 1..16_777_216L && data + size <= originalLength) {
                                "Windows ikon kaynak verisi geçersiz."
                            }
                            Resource(id,lang,data,size,dataEntry)
                        }
                    }
                }
                val icons = resources(3)
                val groups = resources(14)
                require(icons.isNotEmpty() && groups.isNotEmpty()) {
                    "Windows Host içinde değiştirilebilir ikon kaynakları bulunamadı."
                }
                val replaced = mutableMapOf<Pair<Int,Int>,Int>()
                var groupCount = 0
                for (group in groups) {
                    require(group.size in 20..65536) { "Windows ikon grup verisi geçersiz." }
                    val countIcons = pe.u16(group.data + 4)
                    require(pe.u16(group.data) == 0 && pe.u16(group.data + 2) == 1 &&
                            countIcons in 1..256 && 6L + countIcons * 14L <= group.size) {
                        "Windows ikon grup biçimi geçersiz."
                    }
                    val collected = ArrayList<ByteArray>()
                    for (index in 0 until countIcons) {
                        val at = group.data + 6 + index * 14L
                        val width = pe.u8(at).let { if (it == 0) 256 else it }
                        val height = pe.u8(at + 1).let { if (it == 0) 256 else it }
                        val id = pe.u16(at + 12)
                        val slot = icons.firstOrNull { it.id == id && it.lang == group.lang }
                            ?: icons.firstOrNull { it.id == id }
                            ?: continue
                        val key = slot.id to slot.lang
                        val size = replaced[key] ?: run {
                            val encoded = encode(bitmap,width,height,slot.size.toInt()) ?: return@run null
                            pe.seek(slot.data)
                            pe.write(encoded)
                            pe.put32(slot.entry + 4,encoded.size.toLong())
                            replaced[key] = encoded.size
                            encoded.size
                        } ?: continue
                        val entry = ByteArray(14)
                        pe.seek(at)
                        pe.readFully(entry)
                        entry[8] = size.toByte()
                        entry[9] = (size ushr 8).toByte()
                        entry[10] = (size ushr 16).toByte()
                        entry[11] = (size ushr 24).toByte()
                        collected.add(entry)
                    }
                    require(collected.isNotEmpty()) {
                        "Windows Host ikon yuvalarına seçilen görsel sığmadı."
                    }
                    require(6 + collected.size * 14 <= group.size) {
                        "Windows ikon grup kaynağı kapasiteyi aştı."
                    }
                    pe.put16(group.data + 4,collected.size)
                    collected.forEachIndexed { index, bytes ->
                        pe.seek(group.data + 6 + index * 14L)
                        pe.write(bytes)
                    }
                    pe.put32(group.entry + 4,(6 + collected.size * 14).toLong())
                    groupCount++
                }
                require(pe.length() == originalLength && groupCount == groups.size && replaced.isNotEmpty()) {
                    "Windows EXE özel ikon doğrulaması başarısız."
                }
                pe.fd.sync()
                replaced.size
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun encode(bitmap: Bitmap, w: Int, h: Int, capacity: Int): ByteArray? {
        if (w !in 1..256 || h !in 1..256) return null
        val scaled = Bitmap.createScaledBitmap(bitmap,w,h,true)
        try {
            val png = ByteArrayOutputStream().also {
                check(scaled.compress(Bitmap.CompressFormat.PNG,100,it)) {
                    "Windows ikon PNG'ye çevrilemedi."
                }
            }.toByteArray()
            if (png.size <= capacity) return png
            // DIB + AND mask for original small RT_ICON slots that cannot fit PNG.
            val maskRow = ((w + 31) / 32) * 4
            val dibSize = 40 + w * h * 4 + maskRow * h
            if (dibSize > capacity) return null
            val data = ByteArray(dibSize)
            fun put16(o: Int, v: Int) { data[o] = v.toByte(); data[o+1] = (v ushr 8).toByte() }
            fun put32(o: Int, v: Int) { put16(o,v);put16(o+2,v ushr 16) }
            put32(0,40);put32(4,w);put32(8,h*2);put16(12,1);put16(14,32)
            put32(20,w*h*4 + maskRow*h)
            val pixels = IntArray(w*h)
            scaled.getPixels(pixels,0,w,0,0,w,h)
            val maskBase = 40+w*h*4
            for(y in 0 until h) for(x in 0 until w) {
                val pixel = pixels[(h-1-y)*w+x]
                val at = 40+(y*w+x)*4
                data[at] = pixel.toByte()
                data[at+1] = (pixel ushr 8).toByte()
                data[at+2] = (pixel ushr 16).toByte()
                data[at+3] = (pixel ushr 24).toByte()
                if ((pixel ushr 24) < 128) {
                    val m = maskBase + y*maskRow + x/8
                    data[m] = (data[m].toInt() or (0x80 ushr (x%8))).toByte()
                }
            }
            return data
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun RandomAccessFile.u8(at:Long):Int { seek(at);return readUnsignedByte() }
    private fun RandomAccessFile.u16(at:Long):Int = u8(at) or (u8(at+1) shl 8)
    private fun RandomAccessFile.u32(at:Long):Long =
        (u16(at).toLong() or (u16(at+2).toLong() shl 16)) and 0xffff_ffffL
    private fun RandomAccessFile.put16(at:Long,v:Int) {
        seek(at);write(v and 255);write((v ushr 8) and 255)
    }
    private fun RandomAccessFile.put32(at:Long,v:Long) {
        put16(at,v.toInt());put16(at+2,(v ushr 16).toInt())
    }
}
