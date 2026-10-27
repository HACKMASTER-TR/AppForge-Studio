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
    @Test fun updateRetryCountAndNetworkTimeoutsAreBounded() {
        val text=shell()
        assertTrue(text.contains("""appforge_apt_get_legacy "$@" -o Acquire::Retries=2"""))
        assertTrue(text.contains("-o Acquire::https::Timeout=20"))
        assertTrue(text.contains("-o Acquire::http::Timeout=20"))
        assertFalse(text.contains("Acquire::Retries=999"))
    }
    @Test fun failedSnapshotCannotSilentlyFallbackToLiveArchive() {
        val text=shell()
        assertTrue(text.contains("as_stamp=20261018T000000Z"))
        assertTrue(text.contains("https://snapshot.ubuntu.com/ubuntu/"))
        assertTrue(text.contains("snapshot index acquisition failed"))
        assertFalse(text.contains("as_repo=\"https://archive.ubuntu.com"))
    }
    @Test fun signatureHashAndExpiryValidationStayRequired() {
        val text=shell()
        assertTrue(text.contains("as_verify_indices || return 60"))
        assertTrue(text.contains("Acquire::Check-Valid-Until=true"))
        assertTrue(text.contains("Acquire::AllowDowngradeToInsecureRepositories=false"))
        assertTrue(text.contains("gpgv --status-fd=1"))
    }
}
