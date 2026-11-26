package com.appforge.studio.build

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

internal class V9BundledSignedReleaseTest {
    private fun assets(): File =
        listOf(
            "src/debug/assets/v9-r2",
            "app/src/debug/assets/v9-r2",
            "android-app/app/src/debug/assets/v9-r2"
        ).map(::File).first { it.isDirectory }

    private fun bundle(): Pair<ByteArray, ByteArray> =
        File(assets(), "release-v1.json").readBytes() to
        File(assets(), "release-v1.sig").readBytes()

    @Test fun originalManifestAndSignatureArePinned() {
        val (manifest, signature) = bundle()
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(manifest)
            .joinToString("") { "%02x".format(it) }

        assertEquals(
            "569f7f55cb5fa645935250acaf2e6551d697fee4cfd723799abc92f5b58f5cf5",
            sha
        )
        assertEquals(64, signature.size)

        val candidate = V9ReleaseCandidatePolicy.verify(
            manifest, signature, 2026101801L
        )
        assertEquals(2026101801L, candidate.sequence)
        assertEquals(218, candidate.objects.size)
    }

    @Test fun alteredManifestIsRejected() {
        val (manifest, signature) = bundle()
        manifest[0] = (manifest[0].toInt() xor 1).toByte()

        assertThrows(Exception::class.java) {
            V9ReleaseCandidatePolicy.verify(
                manifest, signature, 2026101801L
            )
        }
    }

    @Test fun alteredSignatureIsRejected() {
        val (manifest, signature) = bundle()
        signature[0] = (signature[0].toInt() xor 1).toByte()

        assertThrows(Exception::class.java) {
            V9ReleaseCandidatePolicy.verify(
                manifest, signature, 2026101801L
            )
        }
    }
}
