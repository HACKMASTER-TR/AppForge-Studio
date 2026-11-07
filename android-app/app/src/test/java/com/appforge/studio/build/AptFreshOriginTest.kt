package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal class AptFreshOriginTest {
    private val archive = "a".repeat(64)

    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("fresh-origin-").toFile()
        try {
            fun create(name: String, contents: String): File {
                return File(root, name).apply {
                    parentFile.mkdirs()
                    writeText(contents)
                }
            }
            val status = create(
                "var/lib/dpkg/status",
                "Package: base\nVersion: 1\nArchitecture: arm64\n" +
                    "Status: install ok installed\n\n"
            )
            create(
                "opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json",
                JSONObject()
                    .put("schema", 1)
                    .put("release", "24.04.4")
                    .put("architecture", "arm64")
                    .put("rootfsSha256", archive)
                    .put("statusSha256", VerifiedModuleDownload.sha(status))
                    .put("packages", JSONObject().put("base", "1 (arm64)"))
                    .toString()
            )
            create(
                BaseRuntimeOwnership.FILE,
                JSONObject().put("schema", 1)
                    .put("paths", org.json.JSONArray().put("etc"))
                    .toString()
            )
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun freshVendorRootCanRecordOriginBeforeNodeReceipt() = fixture {
        AptSnapshotMigration.captureFreshRoot(it, "arm64", archive)
        val receipt = File(
            it, "opt/appforge-device/modules-v3/NODE_WEB.json"
        )
        receipt.parentFile.mkdirs()
        receipt.writeText("{}")
        assertNull(AptSnapshotMigration.blockedReason(it))
    }

    @Test fun legacyNodeReceiptCannotBeRetrospectivelyTrusted() = fixture {
        val receipt = File(
            it, "opt/appforge-device/modules-v3/NODE_WEB.json"
        )
        receipt.parentFile.mkdirs()
        receipt.writeText("{}")
        assertNotNull(AptSnapshotMigration.blockedReason(it))
        assertTrue(runCatching {
            AptSnapshotMigration.captureFreshRoot(it, "arm64", archive)
        }.isFailure)
    }

    @Test fun changedDpkgStateRejectsFreshOrigin() = fixture {
        File(it, "var/lib/dpkg/status").appendText("Changed: yes\n")
        assertTrue(runCatching {
            AptSnapshotMigration.captureFreshRoot(it, "arm64", archive)
        }.isFailure)
    }

    @Test fun wrongVendorRootHashRejectsFreshOrigin() = fixture {
        assertTrue(runCatching {
            AptSnapshotMigration.captureFreshRoot(it, "arm64", "b".repeat(64))
        }.isFailure)
    }

    @Test fun secondOriginCaptureIsNotAllowed() = fixture {
        AptSnapshotMigration.captureFreshRoot(it, "arm64", archive)
        assertTrue(runCatching {
            AptSnapshotMigration.captureFreshRoot(it, "arm64", archive)
        }.isFailure)
    }
}
