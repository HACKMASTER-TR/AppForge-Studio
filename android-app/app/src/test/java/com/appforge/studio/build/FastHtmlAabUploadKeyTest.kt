package com.appforge.studio.build

import java.security.KeyPairGenerator
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
