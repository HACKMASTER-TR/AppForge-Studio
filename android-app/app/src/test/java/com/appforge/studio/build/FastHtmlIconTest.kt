package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

class FastHtmlIconTest {
    private fun png(width: Int=2,height: Int=2,raw: ByteArray=ByteArray(18),interlace: Int=0): ByteArray {
        val out=ByteArrayOutputStream();val sink=DataOutputStream(out)
        sink.write(byteArrayOf(-119,80,78,71,13,10,26,10))
        fun chunk(type: String, bytes: ByteArray) {
            val t=type.toByteArray();sink.writeInt(bytes.size);sink.write(t);sink.write(bytes)
            sink.writeInt(CRC32().apply{update(t);update(bytes)}.value.toInt())
        }
        val header=ByteArrayOutputStream();DataOutputStream(header).apply{writeInt(width);writeInt(height);write(byteArrayOf(8,6,0,0,interlace.toByte()))}
        val compressed=ByteArrayOutputStream();DeflaterOutputStream(compressed).use{it.write(raw)}
        chunk("IHDR",header.toByteArray());chunk("IDAT",compressed.toByteArray());chunk("IEND",byteArrayOf())
        return out.toByteArray()
    }
    @Test fun actualStaticPngAndMalformedPayloads() {
        val valid=png();assertArrayEquals(valid,FastHtmlIcon.requirePng(valid))
        for(bytes in listOf(valid.copyOf(30), valid+byteArrayOf(0),valid.clone().apply{this[40]=(this[40].toInt() xor 1).toByte()},
            png(width=0),png(width=1025),png(raw=ByteArray(17)),png(raw=ByteArray(19)),
            png(raw=ByteArray(18).apply{this[0]=5}),png(interlace=1),ByteArray(1024*1024+1))) {
            assertThrows(Exception::class.java){FastHtmlIcon.requirePng(bytes)}
        }
    }
}
