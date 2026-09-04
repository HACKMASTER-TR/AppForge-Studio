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
            "26.04",
            manifest.release
        )
        assertEquals(
            "b2b46a37324ea1954e93f293fe6d7c2241daf2fc298c4022e6e4caceeed74cab",
            manifest.archiveSha256
        )
        assertTrue(
            LinuxRuntimeIntegrity.isValidSha256(
                manifest.archiveSha256
            )
        )
    }

}
