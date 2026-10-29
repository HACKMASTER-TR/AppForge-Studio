package com.appforge.studio.build

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineToolCatalogTest {
    private val pack = OfflineToolPackage("fixture", "1.0", "aarch64-linux-android", "a".repeat(64),
        "https://packages.termux.dev/fixture", "MIT", "b".repeat(64), 4)

    @Test fun absentCacheStaysAbsentAndCannotBeComplete() {
        val root = kotlin.io.path.createTempDirectory("v10-catalog-test").toFile()
        try {
            val absent = File(root, "absent")
            assertFalse(OfflineToolCatalog.inspect(listOf(pack), absent, pack.abi).complete)
            assertFalse(absent.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun bionicCatalogCannotSatisfyUbuntuRuntimeAbi() {
        val root = kotlin.io.path.createTempDirectory("v10-catalog-test").toFile()
        try {
            val result = OfflineToolCatalog.inspect(listOf(pack), root, "aarch64-linux-gnu")
            assertFalse(result.complete)
            assertTrue(result.missingOrInvalid.single().endsWith(":ABI_MISMATCH"))
        } finally { root.deleteRecursively() }
    }

    @Test fun remoteMetadataCannotExpandLicensePolicy() {
        assertTrue(runCatching { OfflineToolCatalog.encode(listOf(pack.copy(license = "UNKNOWN"))) }.isFailure)
        assertTrue(runCatching { OfflineToolCatalog.encode(listOf(pack.copy(upstream = "https://user:password@example.invalid/a"))) }.isFailure)
        assertTrue(runCatching { OfflineToolCatalog.encode(listOf(pack.copy(sha256 = "../escape"))) }.isFailure)
    }
}
