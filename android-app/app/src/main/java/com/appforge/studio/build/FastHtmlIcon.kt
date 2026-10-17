package com.appforge.studio.build

import android.content.Context
import android.net.Uri
import com.appforge.studio.model.ProjectDraft
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Replace one pinned drawable, never resource IDs or arbitrary archive paths. */
internal object FastHtmlIcon {
    const val AAB_PATH = "base/res/drawable-nodpi-v4/appforge_icon.png"
    private const val LIMIT = 1024 * 1024
    private val signature = byteArrayOf(-119,80,78,71,13,10,26,10)

    fun read(context: Context, draft: ProjectDraft): ByteArray? {
        val address = draft.iconUri?.takeIf { it.isNotBlank() } ?: return null
        val uri = Uri.parse(address)
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank() && uri.fragment == null) {
            "Özel ikon için kullanıcı tarafından seçilmiş content URI gerekir."
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= LIMIT) { "PNG ikon en çok 1 MiB olabilir." }
                output.write(buffer,0,count)
            }
            output.toByteArray()
        } ?: error("Seçilen PNG ikon okunamadı; varsayılan ikonla devam edilmedi.")
        return requirePng(bytes)
    }

    // Static PNG only; reject APNG, malformed CRC/length/order and oversized decoder allocations.
    internal fun requirePng(bytes: ByteArray): ByteArray {
        require(bytes.size in 57..LIMIT && bytes.copyOfRange(0,8).contentEquals(signature)) { "Geçerli PNG ikon gerekir." }
        fun int(at: Int) = ByteBuffer.wrap(bytes,at,4).order(ByteOrder.BIG_ENDIAN).int
        var at=8; var header=false; var data=false; var ended=false; var palette=false; var dataEnded=false
        var indexed=false
        while(at < bytes.size) {
            require(at + 12 <= bytes.size)
            val size=int(at)
            require(size >= 0 && size <= bytes.size-at-12)
            val type=bytes.copyOfRange(at+4,at+8).toString(Charsets.US_ASCII)
            require(type.matches(Regex("[A-Za-z]{4}")))
            val crc=CRC32().apply { update(bytes,at+4,size+4) }.value
            require(crc == (int(at+8+size).toLong() and 0xffffffffL)) { "PNG CRC doğrulanamadı." }
            require(header || type=="IHDR")
            when(type) {
                "IHDR" -> {
                    require(!header && at==8 && size==13)
                    require(int(at+8) in 1..1024 && int(at+12) in 1..1024) { "PNG ikon boyutu 1–1024 piksel olmalı." }
                    val depth=bytes[at+16].toInt() and 255; val color=bytes[at+17].toInt() and 255
                    require(when(color) {0 -> depth in setOf(1,2,4,8,16); 2,4,6 -> depth in setOf(8,16); 3 -> depth in setOf(1,2,4,8); else -> false})
                    require(bytes[at+18].toInt()==0 && bytes[at+19].toInt()==0 && bytes[at+20].toInt() in 0..1)
                    indexed=color==3; header=true
                }
                "PLTE" -> { require(!data && !palette && size in 3..768 && size%3==0); palette=true }
                "IDAT" -> { require(!dataEnded && (!indexed || palette)); data=true }
                "IEND" -> { require(data && size==0 && at+12==bytes.size); ended=true }
                "acTL", "fcTL", "fdAT" -> error("Animasyonlu PNG ikon desteklenmiyor.")
                else -> { require(type[0].isLowerCase()) { "Bilinmeyen kritik PNG bölümü." }; if(data)dataEnded=true }
            }
            at+=size+12
        }
        require(ended)
        // Decode the compressed stream independently of Android: verify bounded scanline size.
        validatePixels(bytes)
        return bytes
    }

    private fun validatePixels(bytes: ByteArray) {
        fun int(at: Int)=ByteBuffer.wrap(bytes,at,4).order(ByteOrder.BIG_ENDIAN).int
        val width=int(16); val height=int(20); val depth=bytes[24].toInt() and 255
        val channels=when(bytes[25].toInt()) {0,3 -> 1; 2 -> 3; 4 -> 2; 6 -> 4; else -> error("PNG color")}
        // Keep the supported icon format predictable; interlaced PNG can be exported as a normal PNG.
        require(bytes[28].toInt()==0) { "Interlace PNG yerine normal PNG seçin." }
        val row=(width.toLong()*depth*channels+7)/8
        val expected=(row+1)*height
        val compressed=ByteArrayOutputStream()
        var at=8
        while(at<bytes.size) {val n=int(at);if(bytes.copyOfRange(at+4,at+8).toString(Charsets.US_ASCII)=="IDAT")compressed.write(bytes,at+8,n);at+=n+12}
        val inflater=java.util.zip.Inflater()
        try {
            inflater.setInput(compressed.toByteArray())
            val buffer=ByteArray(8192);var total=0L
            while(!inflater.finished()) {
                val n=inflater.inflate(buffer)
                require(n>0) { "PNG sıkıştırılmış görüntüsü bozuk." }
                for(i in 0 until n) if((total+i)%(row+1)==0L)require((buffer[i].toInt() and 255)<=4)
                total+=n;require(total<=expected)
            }
            require(total==expected && inflater.remaining==0) { "PNG piksel uzunluğu geçersiz." }
        } finally {inflater.end()}
    }
}
