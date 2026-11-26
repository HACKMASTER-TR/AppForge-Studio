package com.appforge.studio.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class V9PinnedReleaseVerifierUnitTest {
    @Test fun compiledPublicKeyHashMatchesPin() {
        val bytes=Base64.getDecoder().decode(V9ReleaseSignatureVerifier.TRUSTED_SPKI_B64)
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes)
        assertEquals(V9ReleaseSignatureVerifier.TRUSTED_SPKI_SHA256,
            digest.joinToString(""){ "%02x".format(it.toInt() and 255) })
    }

    @Test fun rejectsForgedSignature() {
        assertThrows(SecurityException::class.java) {
            V9ReleaseSignatureVerifier.verifyPinned("{}\n".toByteArray(),ByteArray(64))
        }
    }

    @Test fun rejectsRollback() {
        assertThrows(SecurityException::class.java) {
            V9ReleaseSignatureVerifier.requireNoRollback(2026101801L, 2026101800L)
        }
    }
}
