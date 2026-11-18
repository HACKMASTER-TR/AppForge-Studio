package com.appforge.studio.build

import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRuntimeManifestRegistry
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class IsolatedModuleRuntimeV2Test {
    @Test fun windowsNativeHasOwnUbuntuDpkgAndNeverAddsExpoOrAndroid() = fixture { home ->
        val win = IsolatedModuleRuntimeV2.layout(home, OfflineModuleId.WINDOWS_NATIVE, LinuxArchitecture.ARM64)
        assertEquals(listOf(OfflineModuleId.LINUX_RUNTIME, OfflineModuleId.WINDOWS_NATIVE), win.request.dependencyOrder)
        assertFalse(win.request.requiresSdkLicense)
        assertEquals("windows_native", win.namespace.base.name)
        assertTrue(win.dpkgStatus.path.contains("windows_native"))
        assertEquals("status", win.dpkgStatus.name)
    }

    @Test fun eachModuleHasDistinctRuntimeAptDpkgAndDownloads() = fixture { home ->
        val windows = IsolatedModuleRuntimeV2.layout(home, OfflineModuleId.WINDOWS_NATIVE, LinuxArchitecture.ARM64)
        val expo = IsolatedModuleRuntimeV2.layout(home, OfflineModuleId.EXPO_REACT_NATIVE, LinuxArchitecture.ARM64)
        val ndk = IsolatedModuleRuntimeV2.layout(home, OfflineModuleId.RUST_ANDROID_NDK, LinuxArchitecture.ARM64)
        IsolatedModuleRuntimeV2.requireSeparated(windows, expo)
        IsolatedModuleRuntimeV2.requireSeparated(windows, ndk)
        IsolatedModuleRuntimeV2.requireSeparated(expo, ndk)
        for (a in listOf(windows, expo, ndk)) {
            assertTrue(a.linux.rootfsDirectory.path.contains(a.request.target.name.lowercase()))
            assertTrue(a.dpkgStatus.path.contains("var/lib/dpkg/status"))
            assertTrue(a.aptLists.path.contains("var/lib/apt/lists"))
            assertTrue(a.dpkgInfo.path.contains("var/lib/dpkg/info"))
            assertTrue(a.namespace.downloads.isDirectory)
        }
    }

    @Test fun existingUserProjectsAndLegacyRootfsAreNotReadOrModified() = fixture { home ->
        val user = File(home, "user-project.txt").apply { writeText("private source") }
        val legacy = File(home, "device-build").apply { mkdir() }
        val file = File(legacy, "var-lib-dpkg-status").apply { writeText("existing database") }
        val plan = IsolatedModuleRuntimeV2.layout(home, OfflineModuleId.RUST_ANDROID_NDK, LinuxArchitecture.ARM64)
        assertEquals("private source", user.readText())
        assertEquals("existing database", file.readText())
        assertFalse(plan.linux.rootfsDirectory.exists())
        assertFalse(plan.seal.exists())
    }

    @Test fun cannotDeclareBaselineVerifiedWithoutSignedRootfsDpkgAndSeal() = fixture { home ->
        val layout = IsolatedModuleRuntimeV2.layout(home, OfflineModuleId.EXPO_REACT_NATIVE, LinuxArchitecture.ARM64)
        val manifest = requireNotNull(LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU, LinuxArchitecture.ARM64))
        assertFalse(IsolatedModuleRuntimeV2.baselineVerified(layout, manifest))
        assertFalse(layout.seal.exists())
    }

    @Test fun malformedOrSymlinkedModuleRuntimePathIsRejected() = fixture { home ->
        val ns = ModuleIsolationV1.prepare(home, OfflineModuleId.WINDOWS_NATIVE)
        val foreign = File(home, "external-root").apply { mkdir() }
        val ubuntu = File(ns.rootfs, "ubuntu")
        Files.createSymbolicLink(ubuntu.toPath(), foreign.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            IsolatedModuleRuntimeV2.layout(home, OfflineModuleId.WINDOWS_NATIVE, LinuxArchitecture.ARM64)
        }
        assertTrue(foreign.listFiles().isNullOrEmpty())
    }

    @Test fun portableAndBundledModulesCannotAllocateLinuxRootfs() = fixture { home ->
        for (id in listOf(OfflineModuleId.WINDOWS_PORTABLE, OfflineModuleId.FAST_HTML)) {
            assertThrows(IllegalArgumentException::class.java) {
                IsolatedModuleRuntimeV2.layout(home, id, LinuxArchitecture.ARM64)
            }
        }
    }

    private fun fixture(block:(File)->Unit) {
        val home = Files.createTempDirectory("appforge-v541-isolated-rootfs").toFile()
        try { block(home) } finally { home.deleteRecursively() }
    }
}
