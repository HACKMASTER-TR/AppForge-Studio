package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Mandatory real AGP outputs: no synthetic manifest can authorize this acceptance. */
class CompiledHtmlTemplateIdentityTest {
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun compiledTemplatesHaveIsolatedProviderIdentitiesAndExactPins() {
        val build = File(requireNotNull(System.getProperty("appforge.templateAssets")))
        val metadata = File(build, "generated/html-integrity/assets/html-template-integrity.txt")
            .readLines().associate { val parts=it.split(' '); parts[0] to parts[1] }
        val apk = File(build, "generated/html-template/assets/html-apk-template.apk")
        val aab = File(build, "generated/html-aab-template/assets/html-aab-template.aab")
        assertEquals(metadata[apk.name], hash(apk.readBytes()))
        assertEquals(metadata[aab.name], hash(aab.readBytes()))
        val binary = ZipFile(apk).use { it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes() }
        val proto = ZipFile(aab).use { it.getInputStream(it.getEntry("base/manifest/AndroidManifest.xml")).readBytes() }
        val pin = requireNotNull(metadata["html-aab-manifest"])
        assertEquals(pin, hash(proto))
        val outputs = listOf("com.example.first", "com.example.second").map { pkg ->
            val draft=ProjectDraft(packageName=pkg, appName="Provider", versionCode=47)
            val b=HtmlBinaryManifest.rewrite(binary,pkg,draft.appName,47,"1.0.0",26,37)
            val p=FastHtmlAabProtoManifest.rewrite(proto,draft,pin)
            // Independent string decoding below checks exact rewritten binary authority.
            assertTrue(binaryStrings(b).contains("$pkg.appforge.documents"))
            assertFalse(binaryStrings(b).contains("com.appforge.htmlstub.appforge.documents"))
            assertTrue(p.toString(Charsets.UTF_8).contains("$pkg.appforge.documents"))
            b to p
        }
        assertFalse(outputs[0].first.contentEquals(outputs[1].first))
        assertFalse(outputs[0].second.contentEquals(outputs[1].second))
        assertThrows(IllegalArgumentException::class.java) {
            HtmlBinaryManifest.rewrite(binary,"com.appforge.htmlstub","Invalid",1,"1",26,37)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabProtoManifest.rewrite(proto,ProjectDraft(appName="Invalid"),"0".repeat(64))
        }
        val corrupt=proto.clone();corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabProtoManifest.rewrite(corrupt,ProjectDraft(appName="Invalid"),pin)
        }
    }
    private fun binaryStrings(b: ByteArray): List<String> {
        fun n(i: Int)=java.nio.ByteBuffer.wrap(b,i,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        var at=8
        while ((b[at].toInt() and 255) != 1 || b[at+1].toInt()!=0) at+=n(at+4)
        val count=n(at+8); val utf8=n(at+16) and 256 != 0; val start=at+n(at+20)
        return (0 until count).map {
            var p=start+n(at+28+4*it)
            if(utf8) {
                fun len(): Int {val first=b[p++].toInt() and 255;return if(first and 128==0)first else ((first and 127) shl 8) or (b[p++].toInt() and 255)}
                len();val size=len();String(b,p,size,Charsets.UTF_8)
            } else {
                var size=(b[p++].toInt() and 255) or ((b[p++].toInt() and 255) shl 8)
                if(size and 32768!=0) {size=((size and 32767) shl 16) or (b[p++].toInt() and 255) or ((b[p++].toInt() and 255) shl 8)}
                String(b,p,size*2,Charsets.UTF_16LE)
            }
        }
    }
}
