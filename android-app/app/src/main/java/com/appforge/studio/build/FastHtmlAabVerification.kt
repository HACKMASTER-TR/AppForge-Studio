package com.appforge.studio.build

import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarFile

/** Internal acceptance prerequisite only. Does not produce or authorize an AAB. */
internal object FastHtmlAabVerification {
    private const val MAX_ARCHIVE_BYTES = 40L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 24L * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 40L * 1024 * 1024
    private const val MAX_ENTRIES = 2048
    private val required = setOf(
        "BundleConfig.pb", "base/manifest/AndroidManifest.xml",
        "base/dex/classes.dex", "base/resources.pb",
        "base/assets/appforge-html-config.json"
    )

    /** Reads every entry to EOF: JAR verification is lazy until the payload is consumed.
     * Expected identity must come from the approved signer, never from this archive.
     * A successful return proves neither manifest correctness nor Play upload eligibility.
     */
    fun requireSignedPayload(file: File, expectedCertificate: X509Certificate) {
        require(file.isFile && file.length() in 1024..MAX_ARCHIVE_BYTES)
        val expected = MessageDigest.getInstance("SHA-256").digest(expectedCertificate.encoded)
        JarFile(file, true).use { jar ->
            val names = HashSet<String>()
            var expanded = 0L
            var payloads = 0
            val entries = jar.entries()
            val buffer = ByteArray(8192)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                requireSafeName(entry.name, entry.isDirectory)
                require(names.add(entry.name) && names.size <= MAX_ENTRIES) {
                    "Duplicate or excessive AAB entries."
                }
                require(entry.size in 0..MAX_ENTRY_BYTES) { "Invalid AAB entry size." }
                var consumed = 0L
                jar.getInputStream(entry).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        consumed += count
                        expanded += count
                        require(consumed <= MAX_ENTRY_BYTES && expanded <= MAX_EXPANDED_BYTES) {
                            "AAB expanded size limit exceeded."
                        }
                    }
                }
                require(consumed == entry.size)
                if (entry.isDirectory) {
                    require(consumed == 0L)
                } else if (!entry.name.startsWith("META-INF/")) {
                    requireExpectedSigner(entry.certificates, expected)
                    payloads++
                } else {
                    // Only JAR signature bookkeeping may be unsigned; no hidden payload.
                    require(entry.name == "META-INF/MANIFEST.MF" ||
                        entry.name.matches(Regex("META-INF/[A-Z0-9_-]{1,8}\\.(SF|RSA|DSA|EC)"))) {
                        "Unsupported AAB signature metadata."
                    }
                }
            }
            require(payloads > 0 && names.containsAll(required)) { "Incomplete signed HTML AAB." }
        }
    }

    internal fun requireExpectedSigner(
        certificates: Array<java.security.cert.Certificate>?, expectedSha256: ByteArray
    ) {
        require(expectedSha256.size == 32)
        // The local signing contract uses one self-signed persistent certificate.
        // Chains/multiple signers need a separately reviewed identity policy.
        val chain = requireNotNull(certificates) { "Unsigned AAB entry." }
        require(chain.size == 1 && chain[0] is X509Certificate) {
            "Unsigned entry or unsupported signer chain."
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
        requireCertificateDigest(actual, expectedSha256)
    }

    internal fun requireCertificateDigest(actual: ByteArray, expected: ByteArray) {
        require(actual.size == 32 && expected.size == 32 &&
            MessageDigest.isEqual(expected, actual)) { "AAB signer identity mismatch." }
    }

    internal fun requireSafeName(name: String, directory: Boolean) {
        require(name.isNotEmpty() && name.length <= 1024 && !name.startsWith('/') &&
            '\\' !in name && ':' !in name && name.none { it.code < 32 || it.code == 127 }) {
            "Unsafe AAB entry name."
        }
        val components = (if (directory) name.removeSuffix("/") else name).split('/')
        require(components.none { it.isEmpty() || it == "." || it == ".." }) {
            "Unsafe AAB entry path."
        }
    }
}
