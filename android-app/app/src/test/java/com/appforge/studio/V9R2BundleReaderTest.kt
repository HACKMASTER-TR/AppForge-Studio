package com.appforge.studio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class V9R2BundleReaderTest {
    @Test fun acceptsBoundedManifest() {
        val bytes = "{\"schema\":\"candidate\"}".toByteArray()
        assertArrayEquals(bytes, V9R2BundleReader.bounded(ByteArrayInputStream(bytes), 128))
    }
    @Test fun rejectsEmptyInput() {
        assertThrows(IllegalArgumentException::class.java) {
            V9R2BundleReader.bounded(ByteArrayInputStream(byteArrayOf()), 64)
        }
    }
    @Test fun rejectsOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            V9R2BundleReader.bounded(ByteArrayInputStream(ByteArray(65)), 64)
        }
    }
    @Test fun refusesStalledStream() {
        val stalled = object: InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertThrows(IllegalArgumentException::class.java) {
            V9R2BundleReader.bounded(stalled, 64)
        }
    }
}
