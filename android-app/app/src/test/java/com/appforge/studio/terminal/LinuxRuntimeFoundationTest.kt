package com.appforge.studio.terminal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxRuntimeFoundationTest {
    @Test
    fun mapsAndroidAbisToDistroArchitectures() {
        assertEquals(
            LinuxArchitecture.ARM64,
            LinuxArchitecture.fromAndroidAbis(
                listOf(
                    "arm64-v8a",
                    "armeabi-v7a"
                )
            )
        )

        assertEquals(
            "arm64",
            LinuxArchitecture.ARM64
                .distroArchitecture
        )

        assertEquals(
            LinuxArchitecture.X86_64,
            LinuxArchitecture.fromAndroidAbis(
                listOf("x86_64")
            )
        )

        assertNull(
            LinuxArchitecture.fromAndroidAbis(
                listOf("mips64")
            )
        )
    }

    @Test
    fun aptToolchainPlanUsesOnlyCatalogPackages() {
        val command =
            LinuxToolchainCatalog.installCommand(
                listOf(
                    LinuxToolchainId.BASE,
                    LinuxToolchainId.PYTHON,
                    LinuxToolchainId.NODE,
                    LinuxToolchainId.C_CPP
                )
            )

        assertTrue(
            command.startsWith(
                "apt-get update && "
            )
        )
        assertTrue(command.contains("python3"))
        assertTrue(command.contains("nodejs"))
        assertTrue(command.contains("build-essential"))
        assertFalse(command.contains("curl |"))
        assertFalse(command.contains("wget |"))
    }

    @Test
    fun sha256VerificationRejectsChangedArchive() {
        val archive =
            File.createTempFile(
                "appforge-rootfs",
                ".tar"
            )

        try {
            archive.writeText(
                "verified-rootfs"
            )

            val digest =
                LinuxRuntimeIntegrity.sha256(
                    archive
                )

            assertTrue(
                LinuxRuntimeIntegrity.matches(
                    archive,
                    digest
                )
            )

            archive.appendText("-changed")

            assertFalse(
                LinuxRuntimeIntegrity.matches(
                    archive,
                    digest
                )
            )
        } finally {
            archive.delete()
        }
    }

    @Test
    fun runtimeCannotBecomeTrustedWithoutPinnedManifest() {
        assertNull(
            LinuxRuntimeManifestRegistry.find(
                LinuxDistribution.DEBIAN,
                LinuxArchitecture.ARM64
            )
        )

        val status =
            LinuxRuntimeStatus(
                distribution =
                    LinuxDistribution.DEBIAN,
                architecture =
                    LinuxArchitecture.ARM64,
                state =
                    LinuxRuntimeState.ROOTFS_UNTRUSTED,
                engineBundled = true,
                rootfsInstalled = true,
                rootfsTrusted = false,
                detail = "test"
            )

        assertFalse(status.ready)
    }
    @Test
    fun ubuntuBaseManifestIsHttpsAndPinned() {
        val manifest =
            LinuxRuntimeManifestRegistry.find(
                LinuxDistribution.UBUNTU,
                LinuxArchitecture.ARM64
            )

        assertTrue(manifest != null)

        requireNotNull(manifest)

        assertEquals(
            "https",
            manifest.sourceUri.scheme
        )
        assertEquals(
            "cdimage.ubuntu.com",
            manifest.sourceUri.host
        )
        assertEquals(
            "24.04.4",
            manifest.release
        )
        assertEquals(
            "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2",
            manifest.archiveSha256
        )
        assertTrue(
            LinuxRuntimeIntegrity.isValidSha256(
                manifest.archiveSha256
            )
        )
    }

}
