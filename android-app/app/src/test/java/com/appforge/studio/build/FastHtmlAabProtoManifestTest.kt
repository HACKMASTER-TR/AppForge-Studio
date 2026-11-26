package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.security.MessageDigest

class FastHtmlAabProtoManifestTest {
    private fun draft() = ProjectDraft(appName = "Static", packageName = "com.example.static")

    /** A valid wire envelope by itself must never authorize a compiled AAPT2 manifest. */
    @Test fun framingAloneDoesNotAuthorizeSyntheticManifest() {
        val synthetic = byteArrayOf(0x12, 2, 0x18, 0)
        FastHtmlAabProtoManifest.requireWireFraming(synthetic)
        val original = synthetic.clone()
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabProtoManifest.rewrite(synthetic, draft())
        }
        assertArrayEquals(original, synthetic)
    }

    @Test fun rejectsTruncationOverflowNoncanonicalLengthsAndGroups() {
        listOf(byteArrayOf(), byteArrayOf(0x80.toByte()), byteArrayOf(0),
            byteArrayOf(0x12, 5, 1), byteArrayOf(0x12, 0x80.toByte(), 0),
            byteArrayOf(0x0b), byteArrayOf(9, 1), byteArrayOf(13, 1),
            byteArrayOf(8) + ByteArray(10) { 0xff.toByte() }).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                FastHtmlAabProtoManifest.requireWireFraming(it)
            }
        }
    }

    @Test fun boundsInputAndFieldCount() {
        listOf(ByteArray(1_048_577), ByteArray(8194) { if (it % 2 == 0) 8 else 0 }).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                FastHtmlAabProtoManifest.requireWireFraming(it)
            }
        }
    }

    @Test fun invalidIdentityVersionsAndSdkFailBeforeTemplateCheck() {
        listOf(draft().copy(packageName = "../x"), draft().copy(packageName = "a." + "b".repeat(256)),
            draft().copy(versionCode = 0), draft().copy(versionName = ""),
            draft().copy(appName = "bad\u0000label"), draft().copy(minSdk = 27),
            draft().copy(targetSdk = 36)).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                FastHtmlAabProtoManifest.rewrite(byteArrayOf(8, 0), it)
            }
        }
    }

    @Test fun evenValidWireShapesCannotBypassPinnedFixtureIdentity() {
        listOf(byteArrayOf(0x12, 0), byteArrayOf(0x12, 0, 0x12, 0),
            byteArrayOf(0x12, 4, 0x12, 2, 0x12, 0)).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                FastHtmlAabProtoManifest.rewrite(it, draft())
            }
        }
    }

    private fun currentManifest(): Pair<ByteArray, String> {
        val build = java.io.File(requireNotNull(System.getProperty("appforge.templateAssets")))
        val pin = java.io.File(build,"generated/html-integrity/assets/html-template-integrity.txt")
            .readLines().single { it.startsWith("html-aab-manifest ") }.substringAfter(' ')
        val bytes = java.util.zip.ZipFile(java.io.File(build,"generated/html-aab-template/assets/html-aab-template.aab")).use {
            it.getInputStream(it.getEntry("base/manifest/AndroidManifest.xml")).readBytes()
        }
        return bytes to pin
    }

    /** Historical providerless fixture is rejected; identity round trip uses actual new AGP output. */
    @Test fun realPinnedCompiledManifestRoundTrip() {
        val historical = Base64.getDecoder().decode(PINNED_PROTO_B64)
        assertThrows(IllegalStateException::class.java) { FastHtmlAabProtoManifest.rewrite(historical, draft()) }
        val (original, pin) = currentManifest()
        val digest = MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(pin, digest)
        val draft = draft().copy(appName = "Örnek Web Uygulaması", versionCode = 9501,
            versionName = "9.5.1", packageName = "com.example.compiled.aab")
        val edited = FastHtmlAabProtoManifest.rewrite(original, draft, pin)
        assertEquals("V14 must not rewrite fixture input in place", digest, MessageDigest.getInstance("SHA-256")
            .digest(original).joinToString("") { "%02x".format(it.toInt() and 255) })
        assertFalse(edited.contentEquals(original))
        assertTrue(edited.size in 1..1_048_576)
        // The production writer also structurally decodes and validates exact metadata.
        val bytes = edited.toString(Charsets.ISO_8859_1)
        assertTrue(bytes.contains("com.appforge.htmlstub.TinyHtmlActivity"))
        assertTrue(bytes.contains(draft.packageName))
        assertTrue(bytes.contains(draft.versionName))
        assertEquals(draft.versionCode.toLong(), parsedVersionCode(edited))
        assertEquals("com.appforge.htmlstub.TinyHtmlActivity", parsedActivity(edited))
    }

    // Separate test-only protobuf reader: never invokes production parser/rewriter.
    private fun parse(data: ByteArray): List<Pair<Int, Any>> {
        var i = 0
        fun number(): Long {
            var v = 0L
            for (shift in 0..63 step 7) {
                require(i < data.size)
                val b = data[i++].toInt() and 255
                v = v or ((b and 127).toLong() shl shift)
                if (b and 128 == 0) return v
            }
            error("varint overflow")
        }
        val out = arrayListOf<Pair<Int, Any>>()
        while (i < data.size) {
            val tag = number().toInt()
            val wire = tag and 7
            val value: Any = when (wire) {
                0 -> number()
                2 -> {
                    val n = number().toInt()
                    require(n >= 0 && n <= data.size - i)
                    data.copyOfRange(i, i + n).also { i += n }
                }
                1 -> data.copyOfRange(i, i + 8).also { i += 8 }
                5 -> data.copyOfRange(i, i + 4).also { i += 4 }
                else -> error("Invalid wire")
            }
            out += (tag ushr 3) to value
        }
        return out
    }
    private fun pbBytes(fields: List<Pair<Int, Any>>, id: Int): ByteArray =
        fields.filter { it.first == id }.single().second as ByteArray
    private fun xmlElement(fields: List<Pair<Int, Any>>, name: String): List<Pair<Int, Any>> =
        fields.filter { it.first == 5 }.mapNotNull { node ->
                val nested = parse(node.second as ByteArray).filter { it.first == 1 }
                if (nested.isEmpty()) null else parse(nested.single().second as ByteArray)
            }.single { String(pbBytes(it, 3), Charsets.UTF_8) == name }
    private fun attr(element: List<Pair<Int, Any>>, name: String): List<Pair<Int, Any>> =
        element.filter { it.first == 4 }.map { parse(it.second as ByteArray) }
            .single { String(pbBytes(it, 2), Charsets.UTF_8) == name }
    private fun manifest(bytes: ByteArray) = parse(pbBytes(parse(bytes), 1))
    private fun parsedVersionCode(bytes: ByteArray): Long {
        val packed = pbBytes(attr(manifest(bytes), "versionCode"), 6)
        val primitive = parse(pbBytes(parse(packed), 7))
        return primitive.filter { it.first == 6 }.single().second as Long
    }
    private fun parsedActivity(bytes: ByteArray): String {
        val app = xmlElement(manifest(bytes), "application")
        val activity = app.filter { it.first == 5 }.mapNotNull { node ->
            val nested = parse(node.second as ByteArray).filter { it.first == 1 }
            if (nested.isEmpty()) null else parse(nested.single().second as ByteArray)
        }.single { element -> String(pbBytes(element, 3), Charsets.UTF_8) == "activity" &&
            String(pbBytes(attr(element, "name"), 3), Charsets.UTF_8) == "com.appforge.htmlstub.TinyHtmlActivity" }
        return String(pbBytes(attr(activity, "name"), 3), Charsets.UTF_8)
    }
    @Test fun realManifestRewritesMaxVersionAndKeepsActivityIdentity() {
        val (original, pin) = currentManifest()
        val draft = draft().copy(appName = "Örnek HTML", packageName = "io.example.unstable",
            versionCode = Int.MAX_VALUE, versionName = "42.0.0")
        val rewritten = FastHtmlAabProtoManifest.rewrite(original, draft, pin)
        assertEquals(Int.MAX_VALUE.toLong(), parsedVersionCode(rewritten))
        assertEquals("com.appforge.htmlstub.TinyHtmlActivity", parsedActivity(rewritten))
        assertEquals("26", String(pbBytes(attr(xmlElement(manifest(rewritten), "uses-sdk"), "minSdkVersion"), 3)))
        assertEquals("37", String(pbBytes(attr(xmlElement(manifest(rewritten), "uses-sdk"), "targetSdkVersion"), 3)))
    }

    private val PINNED_PROTO_B64 = """
Cr0PCjkKB2FuZHJvaWQSKmh0dHA6Ly9zY2hlbWFzLmFuZHJvaWQuY29tL2Fway9yZXMvYW5kcm9p
ZBoCCAIaCG1hbmlmZXN0IiASB3BhY2thZ2UaFWNvbS5hcHBmb3JnZS5odG1sc3R1YiJLCipodHRw
Oi8vc2NoZW1hcy5hbmRyb2lkLmNvbS9hcGsvcmVzL2FuZHJvaWQSC3ZlcnNpb25Db2RlGgExIgII
AiibhIQIMgQ6AjABIkUKKmh0dHA6Ly9zY2hlbWFzLmFuZHJvaWQuY29tL2Fway9yZXMvYW5kcm9p
ZBILdmVyc2lvbk5hbWUaBTEuMC4wKJyEhAgiUgoqaHR0cDovL3NjaGVtYXMuYW5kcm9pZC5jb20v
YXBrL3Jlcy9hbmRyb2lkEhFjb21waWxlU2RrVmVyc2lvbhoCMzciAggCKPKKhAgyBDoCMCUiKBIY
cGxhdGZvcm1CdWlsZFZlcnNpb25Db2RlGgIzNyICCAIyBDoCMCUiUAoqaHR0cDovL3NjaGVtYXMu
YW5kcm9pZC5jb20vYXBrL3Jlcy9hbmRyb2lkEhljb21waWxlU2RrVmVyc2lvbkNvZGVuYW1lGgIx
NyjzioQIIigSGHBsYXRmb3JtQnVpbGRWZXJzaW9uTmFtZRoCMTciAggCMgQ6AjARKg4SBgoKICAg
IBoECAUQISq2AQqtARoIdXNlcy1zZGsiTgoqaHR0cDovL3NjaGVtYXMuYW5kcm9pZC5jb20vYXBr
L3Jlcy9hbmRyb2lkEg1taW5TZGtWZXJzaW9uGgIyNiICCAcojISECDIEOgIwGiJRCipodHRwOi8v
c2NoZW1hcy5hbmRyb2lkLmNvbS9hcGsvcmVzL2FuZHJvaWQSEHRhcmdldFNka1ZlcnNpb24aAjM3
IgIIByjwhIQIMgQ6AjAlGgQIBxAEKg4SBgoKICAgIBoECAkQKCpvCmcaD3VzZXMtcGVybWlzc2lv
biJUCipodHRwOi8vc2NoZW1hcy5hbmRyb2lkLmNvbS9hcGsvcmVzL2FuZHJvaWQSBG5hbWUaG2Fu
ZHJvaWQucGVybWlzc2lvbi5JTlRFUk5FVCiDgIQIGgQICxAEKg4SBgoKICAgIBoECAsQQirfCArW
CBoLYXBwbGljYXRpb24iTwoqaHR0cDovL3NjaGVtYXMuYW5kcm9pZC5jb20vYXBrL3Jlcy9hbmRy
b2lkEgthbGxvd0JhY2t1cBoFZmFsc2UiAggNKICFhAgyBDoCQAAiVQoqaHR0cDovL3NjaGVtYXMu
YW5kcm9pZC5jb20vYXBrL3Jlcy9hbmRyb2lkEhFleHRyYWN0TmF0aXZlTGlicxoFZmFsc2UiAggN
KOqJhAgyBDoCQAAiSwoqaHR0cDovL3NjaGVtYXMuYW5kcm9pZC5jb20vYXBrL3Jlcy9hbmRyb2lk
EgVsYWJlbBoRQVBQRk9SR0VfSFRNTF9BUFAogYCECCKoAQoqaHR0cDovL3NjaGVtYXMuYW5kcm9p
ZC5jb20vYXBrL3Jlcy9hbmRyb2lkEgV0aGVtZRovQGFuZHJvaWQ6c3R5bGUvVGhlbWUuTWF0ZXJp
YWwuTGlnaHQuTm9BY3Rpb25CYXIiAggNKICAhAgyOQo3EMGEjAgaLmFuZHJvaWQ6c3R5bGUvVGhl
bWUuTWF0ZXJpYWwuTGlnaHQuTm9BY3Rpb25CYXIwASJYCipodHRwOi8vc2NoZW1hcy5hbmRyb2lk
LmNvbS9hcGsvcmVzL2FuZHJvaWQSFHVzZXNDbGVhcnRleHRUcmFmZmljGgVmYWxzZSICCA0o7ImE
CDIEOgJAACoREgkKICAgICAgICAaBAgSEC4qqgQKoQQaCGFjdGl2aXR5IksKKmh0dHA6Ly9zY2hl
bWFzLmFuZHJvaWQuY29tL2Fway9yZXMvYW5kcm9pZBIIZXhwb3J0ZWQaBHRydWUiAggTKJCAhAgy
BDoCQAEiXwoqaHR0cDovL3NjaGVtYXMuYW5kcm9pZC5jb20vYXBrL3Jlcy9hbmRyb2lkEgRuYW1l
GiZjb20uYXBwZm9yZ2UuaHRtbHN0dWIuVGlueUh0bWxBY3Rpdml0eSiDgIQIKhUSDQogICAgICAg
ICAgICAaBAgVECUqvAIKswIaDWludGVudC1maWx0ZXIqGRIRCiAgICAgICAgICAgICAgICAaBAgW
EBsqZQpdGgZhY3Rpb24iUwoqaHR0cDovL3NjaGVtYXMuYW5kcm9pZC5jb20vYXBrL3Jlcy9hbmRy
b2lkEgRuYW1lGhphbmRyb2lkLmludGVudC5hY3Rpb24uTUFJTiiDgIQIGgQIFxAQKhoSEgoKICAg
ICAgICAgICAgICAgIBoECBcQRCptCmUaCGNhdGVnb3J5IlkKKmh0dHA6Ly9zY2hlbWFzLmFuZHJv
aWQuY29tL2Fway9yZXMvYW5kcm9pZBIEbmFtZRogYW5kcm9pZC5pbnRlbnQuY2F0ZWdvcnkuTEFV
TkNIRVIog4CECBoECBkQECoVEg0KICAgICAgICAgICAgGgQIGRBMGgQIFhAMKhESCQogICAgICAg
IBoECBoQHBoECBMQCCoNEgUKICAgIBoECBsQExoECA0QBCoKEgIKChoECBwQEhoCCAI=
    """.trimIndent().replace("\n", "")

}
