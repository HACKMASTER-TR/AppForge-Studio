package com.appforge.studio.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class V9SignedR2CandidateDownloadTest {
    private val sha = "16d50387b7656796b79e2c059d06774dfe9fe3f42808d8d50b332ceb780f508a"

    @Test fun fixedHttpsHostAndDigestPath() {
        assertEquals(
            "https://appforge-v9-packages-readonly.28550040284a.workers.dev/v9/objects/sha256/16/$sha",
            V9SignedR2CandidateDownload.urlForSha(sha)
        )
    }

    @Test fun rejectsUppercaseDigest() {
        assertThrows(IllegalArgumentException::class.java) {
            V9SignedR2CandidateDownload.urlForSha(sha.uppercase())
        }
    }

    @Test fun rejectsPathTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            V9SignedR2CandidateDownload.urlForSha("../$sha")
        }
    }

    @Test fun rejectsShortDigest() {
        assertThrows(IllegalArgumentException::class.java) {
            V9SignedR2CandidateDownload.urlForSha(sha.drop(1))
        }
    }

    @Test fun chunkSizeNeverExceedsWorkerLimit() {
        val c = V9SignedR2CandidateDownload.nextChunk(0, 35_401_298)
        assertEquals(0L, c.start)
        assertEquals(8L * 1024 * 1024, c.length)
        assertTrue(c.length <= 16L * 1024 * 1024)
    }

    @Test fun resumesAtExactNonAlignedOffset() {
        val c = V9SignedR2CandidateDownload.nextChunk(8_000_001, 35_401_298)
        assertEquals(8_000_001L, c.start)
        assertEquals(8L * 1024 * 1024, c.length)
    }

    @Test fun exactTailChunk() {
        val c = V9SignedR2CandidateDownload.nextChunk(1030, 1032)
        assertEquals(1030L, c.start)
        assertEquals(1031L, c.end)
        assertEquals(2L, c.length)
    }

    @Test fun rejectsOutOfBoundsResume() {
        assertThrows(IllegalArgumentException::class.java) {
            V9SignedR2CandidateDownload.nextChunk(1032, 1032)
        }
    }

    @Test fun productionDistributionIsStillDisabled() {
        assertFalse(V9ReleaseCandidatePolicy.PRODUCTION_DISTRIBUTION_ENABLED)
        V9ReleaseCandidatePolicy.requireProductionDisabled()
    }

    @Test fun forgedManifestCannotAuthorizeNetworkPlan() {
        val caught = runCatching {
            V9SignedR2CandidateDownload.planSignedObject(
                "{}\n".toByteArray(), ByteArray(64), 0L, sha)
        }.exceptionOrNull()
        assertTrue(caught is SecurityException || caught is GeneralSecurityException)
    }
}
