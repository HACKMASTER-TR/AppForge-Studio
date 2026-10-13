package com.appforge.studio.build

import java.io.IOException
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class FastHtmlAabUploadKeyTest {
    @Test fun diagnosticStageNeverExposesExceptionSecretOrCause() {
        val exception = assertThrows(IllegalStateException::class.java) {
            FastHtmlAabUploadKey.guarded(FastHtmlAabUploadKey.Stage.PKCS12_LOAD) {
                throw IllegalArgumentException("pass" + "word" + "=FAKE_TEST_VALUE")
            }
        }
        assertEquals("APPFORGE_FAST_AAB_STAGE_PKCS12_LOAD", exception.message)
        assertNull(exception.cause)
    }

    @Test fun pkcs12LoadUsesOnlyFixedExceptionCategories() {
        val failures = listOf(
            IOException("password=FAKE_TEST_VALUE", Exception("FAKE_NESTED_SECRET")) to
                "IO_EXCEPTION",
            CertificateException() to "CERTIFICATE_EXCEPTION",
            NoSuchAlgorithmException() to "NO_SUCH_ALGORITHM_EXCEPTION",
            IllegalArgumentException("FAKE_TEST_VALUE") to "RUNTIME_EXCEPTION",
            object : Exception("FAKE_TEST_VALUE") {
                override fun toString(): String = error("Must not inspect exception text")
            } to "OTHER"
        )
        for ((failure, category) in failures) {
            val exception = assertThrows(IllegalStateException::class.java) {
                FastHtmlAabUploadKey.guardedPkcs12Load { throw failure }
            }
            assertEquals("APPFORGE_FAST_AAB_STAGE_PKCS12_LOAD_$category", exception.message)
            assertNull(exception.cause)
            assertEquals(0, exception.suppressed.size)
        }
        assertEquals(42, FastHtmlAabUploadKey.guardedPkcs12Load { 42 })
    }

    @Test fun inputStagesKeepStrictRedaction() {
        for (stage in listOf(
            FastHtmlAabUploadKey.Stage.PKCS12_INPUT_OPEN,
            FastHtmlAabUploadKey.Stage.PKCS12_INPUT_READ
        )) {
            val exception = assertThrows(IllegalStateException::class.java) {
                FastHtmlAabUploadKey.guarded(stage) {
                    throw IOException("password=FAKE_TEST_VALUE")
                }
            }
            assertEquals("APPFORGE_FAST_AAB_STAGE_${stage.name}", exception.message)
            assertNull(exception.cause)
        }
    }

    @Test fun signingKeyMatchesOnlyItsPublicIdentity() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val first = generator.generateKeyPair()
        val second = generator.generateKeyPair()
        FastHtmlAabUploadKey.requireMatchingKey(first.private, first.public)
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabUploadKey.requireMatchingKey(first.private, second.public)
        }
    }
}
