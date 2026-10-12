package com.appforge.studio.build

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

/** Developer-only JAR/CMS v1 AAB signing. This AndroidKeyStore identity is not
 *  exportable. Never claim Play upload-key portability or Play readiness.
 */
internal object FastHtmlAabJarSigner {
    private const val SIGNING_ALIAS = "appforge-html-aab-dev-v1"
    private const val MAX_AAB_BYTES = 40L * 1024L * 1024L
    private const val MAX_ENTRIES = 2048
    private const val MAX_ENTRY_BYTES = 24L * 1024L * 1024L

    fun signDevelopment(unsigned: File, workspace: File): File {
        require(workspace.isDirectory && workspace.canonicalFile == unsigned.parentFile.canonicalFile)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(SIGNING_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                SIGNING_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=AppForge Local AAB Development"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(0L))
                .setCertificateNotAfter(Date(4_102_444_800_000L))
                .build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
                .apply { initialize(spec) }.generateKeyPair()
        }
        val key = store.getKey(SIGNING_ALIAS, null) as? PrivateKey
            ?: error("AAB development key unavailable")
        val cert = store.getCertificate(SIGNING_ALIAS) as? X509Certificate
            ?: error("AAB development certificate unavailable")
        val candidate = signWithIdentity(unsigned, workspace, key, cert)
        try {
            FastHtmlAabVerification.requireSignedPayload(candidate, cert)
            return candidate
        } catch (problem: Throwable) {
            candidate.delete()
            throw problem
        }
    }

    /** Testable without AndroidKeyStore by passing a temporary test identity. */
    internal fun signWithIdentity(
        unsigned: File, workspace: File, key: PrivateKey, cert: X509Certificate
    ): File {
        require(unsigned.isFile && unsigned.length() in 1024..MAX_AAB_BYTES)
        require(workspace.isDirectory && workspace.canonicalFile == unsigned.parentFile.canonicalFile)
        require(key.algorithm.equals("RSA", true)) { "Only RSA signing identity is supported" }
        require(cert.publicKey.algorithm.equals("RSA", true))
        val payload = linkedMapOf<String, ByteArray>()
        ZipFile(unsigned).use { input ->
            val entries = input.entries()
            var total = 0L
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                FastHtmlAabVerification.requireSafeName(entry.name, entry.isDirectory)
                require(!entry.isDirectory && !entry.name.startsWith("META-INF/", true))
                require(entry.size in 0..MAX_ENTRY_BYTES && payload.size < MAX_ENTRIES)
                require(!payload.containsKey(entry.name)) { "Duplicate AAB ZIP entry" }
                val bytes = input.getInputStream(entry).use { stream ->
                    val buffer = ByteArray(8192)
                    val out = ByteArrayOutputStream()
                    while (true) {
                        val n = stream.read(buffer)
                        if (n == -1) break
                        out.write(buffer, 0, n)
                        require(out.size().toLong() <= MAX_ENTRY_BYTES)
                    }
                    out.toByteArray()
                }
                require(entry.size == bytes.size.toLong())
                total += bytes.size
                require(total <= MAX_AAB_BYTES)
                payload[entry.name] = bytes
            }
        }
        require(payload.keys.containsAll(setOf("BundleConfig.pb", "base/manifest/AndroidManifest.xml",
            "base/dex/classes.dex", "base/resources.pb", "base/assets/appforge-html-config.json")))

        val main = "Manifest-Version: 1.0\r\n\r\n".toByteArray(StandardCharsets.UTF_8)
        val sections = linkedMapOf<String, ByteArray>()
        for ((name, bytes) in payload) {
            sections[name] = foldJarHeader("Name", name) +
                foldJarHeader("SHA-256-Digest", b64(sha(bytes))) + crlf()
        }
        val manifest = join(main, *sections.values.toTypedArray())
        val sfMain = "Signature-Version: 1.0\r\n".toByteArray(StandardCharsets.UTF_8) +
            foldJarHeader("SHA-256-Digest-Manifest", b64(sha(manifest))) +
            foldJarHeader("SHA-256-Digest-Manifest-Main-Attributes", b64(sha(main))) + crlf()
        val sfSections = sections.map { (name, bytes) ->
            foldJarHeader("Name", name) + foldJarHeader("SHA-256-Digest", b64(sha(bytes))) + crlf()
        }
        val sf = join(sfMain, *sfSections.toTypedArray())
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(key)
            update(sf)
        }.sign()
        val block = cmsSignedData(sf, signature, cert)
        val signed = File.createTempFile("appforge-signed-aab-", ".aab", workspace)
        var success = false
        try {
            ZipOutputStream(signed.outputStream().buffered()).use { zip ->
                val members = linkedMapOf(
                    "META-INF/MANIFEST.MF" to manifest,
                    "META-INF/APPFORGE.SF" to sf,
                    "META-INF/APPFORGE.RSA" to block
                )
                members.putAll(payload)
                members.forEach { (name, bytes) ->
                    val entry = ZipEntry(name).apply { time = 315532800000L }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            require(signed.length() in 1024..MAX_AAB_BYTES)
            FastHtmlAabVerification.requireSignedPayload(signed, cert)
            success = true
            return signed
        } finally {
            if (!success) signed.delete()
        }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun b64(data: ByteArray) = Base64.getEncoder().encodeToString(data)
    private fun crlf() = "\r\n".toByteArray(StandardCharsets.US_ASCII)
    private fun join(vararg values: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        values.forEach { out.write(it) }
    }.toByteArray()

    /** Manifest continuation lines are limited to 72 UTF-8 bytes, including the space. */
    private fun foldJarHeader(name: String, value: String): ByteArray {
        val out = ByteArrayOutputStream()
        var lineSize = 0
        val content = "$name: $value"
        for (point in content.codePoints().toArray()) {
            val chunk = String(Character.toChars(point)).toByteArray(StandardCharsets.UTF_8)
            require(chunk.size <= 4)
            if (lineSize + chunk.size > 70) {
                out.write(crlf()); out.write(' '.code); lineSize = 1
            }
            out.write(chunk)
            lineSize += chunk.size
        }
        out.write(crlf())
        return out.toByteArray()
    }

    /** Minimal detached PKCS#7 SignedData accepted by JVM JAR verifier. No signed attributes. */
    private fun cmsSignedData(sf: ByteArray, signature: ByteArray, cert: X509Certificate): ByteArray {
        val sha256 = algorithm("608648016503040201")
        val rsa = algorithm("2a864886f70d010101")
        val issuerAndSerial = der(0x30,
            cert.issuerX500Principal.encoded + der(0x02, cert.serialNumber.toByteArray()))
        val signer = seq(derInt(1), issuerAndSerial, sha256, rsa, der(0x04, signature))
        val inner = seq(derInt(1), der(0x31, sha256),
            seq(der(0x06, hex("2a864886f70d010701"))),
            der(0xa0, cert.encoded), der(0x31, signer))
        return seq(der(0x06, hex("2a864886f70d010702")), der(0xa0, inner))
    }
    private fun algorithm(oid: String) = seq(der(0x06, hex(oid)), der(0x05, byteArrayOf()))
    private fun hex(h: String) = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun seq(vararg b: ByteArray) = der(0x30, join(*b))
    private fun derInt(i: Int) = der(0x02, byteArrayOf(i.toByte()))
    private fun der(tag: Int, bytes: ByteArray): ByteArray {
        val length = bytes.size
        val prefix = when {
            length < 128 -> byteArrayOf(length.toByte())
            length < 256 -> byteArrayOf(0x81.toByte(), length.toByte())
            length < 65536 -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), length.toByte())
            else -> byteArrayOf(0x83.toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte())
        }
        return byteArrayOf(tag.toByte()) + prefix + bytes
    }
}
