package com.appforge.studio.build

import org.junit.Assert.assertThrows
import org.junit.Test

class FastHtmlAabVerificationTest {
    @Test fun certificateMismatchCannotPass() {
        val expected = ByteArray(32) { 1 }
        FastHtmlAabVerification.requireCertificateDigest(expected.clone(), expected)
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabVerification.requireCertificateDigest(ByteArray(32) { 2 }, expected)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabVerification.requireCertificateDigest(ByteArray(0), ByteArray(0))
        }
    }

    @Test fun rejectsZipSlipAndAmbiguousNames() {
        listOf("../x", "/x", "base/../x", "base/./x", "base//x", "base\\x",
            "C:/x", "base/x\u0000", "base/x/").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                FastHtmlAabVerification.requireSafeName(name, false)
            }
        }
    }

    @Test fun acceptsCanonicalBundleNames() {
        FastHtmlAabVerification.requireSafeName("base/manifest/AndroidManifest.xml", false)
        FastHtmlAabVerification.requireSafeName("base/assets/site/style.css", false)
        FastHtmlAabVerification.requireSafeName("base/", true)
    }

    @Test fun unsignedPayloadCannotPassIdentityGate() {
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabVerification.requireExpectedSigner(null, ByteArray(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabVerification.requireExpectedSigner(emptyArray(), ByteArray(32))
        }
    }
}
