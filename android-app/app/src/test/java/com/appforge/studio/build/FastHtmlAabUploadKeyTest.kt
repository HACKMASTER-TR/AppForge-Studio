package com.appforge.studio.build

import java.security.KeyPairGenerator
import org.junit.Assert.assertThrows
import org.junit.Test

class FastHtmlAabUploadKeyTest {
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
