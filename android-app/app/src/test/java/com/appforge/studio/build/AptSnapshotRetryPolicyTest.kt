package com.appforge.studio.build

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Transport hardening must never weaken APT snapshot reproducibility. */
class AptSnapshotRetryPolicyTest {
    private fun shell():String {
        val a=File("app/src/main/assets/device-build/apt-snapshot-lock.sh")
        val b=File("src/main/assets/device-build/apt-snapshot-lock.sh")
        return (if(a.isFile)a else b).readText()
    }
    @Test fun r2TimeoutsAndRangesAreBoundedWithoutSnapshotRetries() {
        val text=shell()
        assertFalse(text.contains("appforge_apt_get_legacy"))
        val a=File("app/src/main/java/com/appforge/studio/build/V9R2ObjectTransfer.kt")
        val b=File("src/main/java/com/appforge/studio/build/V9R2ObjectTransfer.kt")
        val transport=(if(a.isFile)a else b).readText()
        assertTrue(transport.contains("connectTimeout = 20_000"))
        assertTrue(transport.contains("readTimeout = 20_000"))
        assertTrue(transport.contains("V9SignedR2CandidateDownload.CHUNK_BYTES"))
        assertTrue(transport.contains("connection.instanceFollowRedirects = false"))
        assertEquals(8L*1024*1024,V9SignedR2CandidateDownload.CHUNK_BYTES)
    }
    @Test fun failedR2CannotSilentlyFallbackToSnapshotOrLiveArchive() {
        val text=shell()
        assertTrue(text.contains("as_stamp=20261018T000000Z"))
        assertTrue(text.contains("https://snapshot.ubuntu.com/ubuntu/")) // provenance only
        assertTrue(text.contains("snapshot fallback prohibited"))
        assertTrue(text.contains("as_prepare_r2_indices || return 60"))
        assertFalse(ModuleArtifactBroker.allowed(java.net.URL("https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/dists/noble/InRelease")))
        assertFalse(ModuleArtifactBroker.allowed(java.net.URL("https://archive.ubuntu.com/ubuntu/pool/example.deb")))
    }
    @Test fun signatureHashAndExpiryValidationStayRequired() {
        val text=shell()
        assertTrue(text.contains("as_verify_indices || return 60"))
        assertTrue(text.contains("Acquire::Check-Valid-Until=true"))
        assertTrue(text.contains("Acquire::AllowDowngradeToInsecureRepositories=false"))
        assertTrue(text.contains("gpgv --status-fd=1"))
    }
}
