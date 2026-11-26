package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ModuleIsolationV1Test {
    @Test fun windowsNativeDoesNotPullExpoRustOrAndroidSdk() {
        val plan = ModuleIsolationV1.request(OfflineModuleId.WINDOWS_NATIVE)
        assertEquals(listOf(OfflineModuleId.LINUX_RUNTIME, OfflineModuleId.WINDOWS_NATIVE), plan.dependencyOrder)
        assertFalse(plan.requiresSdkLicense)
    }

    @Test fun expoPullsOnlyItsExplicitDependencyClosure() {
        val plan = ModuleIsolationV1.request(OfflineModuleId.EXPO_REACT_NATIVE)
        assertTrue(plan.dependencyOrder.containsAll(setOf(OfflineModuleId.LINUX_RUNTIME, OfflineModuleId.ANDROID_CORE, OfflineModuleId.NODE_WEB)))
        assertFalse(plan.dependencyOrder.contains(OfflineModuleId.RUST))
        assertFalse(plan.dependencyOrder.contains(OfflineModuleId.WINDOWS_NATIVE))
        assertTrue(plan.requiresSdkLicense)
    }

    @Test fun rustAndroidDoesNotInstallExpoOrWindowsNative() {
        val order = ModuleIsolationV1.request(OfflineModuleId.RUST_ANDROID_NDK).dependencyOrder
        assertTrue(order.containsAll(setOf(OfflineModuleId.RUST, OfflineModuleId.ANDROID_CORE)))
        assertFalse(order.contains(OfflineModuleId.EXPO_REACT_NATIVE))
        assertFalse(order.contains(OfflineModuleId.NODE_WEB))
    }

    @Test fun legacyDependencyOrderMatchesTheExistingInstallerForMultipleSelections() {
        val selected = linkedSetOf(OfflineModuleId.EXPO_REACT_NATIVE, OfflineModuleId.RUST_ANDROID_NDK)
        assertEquals(OfflineModules.resolve(selected), ModuleIsolationV1.legacyDependencyOrder(selected))
        val reversed = linkedSetOf(OfflineModuleId.RUST_ANDROID_NDK, OfflineModuleId.EXPO_REACT_NATIVE)
        assertEquals(OfflineModules.resolve(reversed), ModuleIsolationV1.legacyDependencyOrder(reversed))
    }

    @Test fun bundledFastHtmlDoesNotAllocateLinuxOrDownloadFolders() {
        val base = Files.createTempDirectory("appforge-bundled").toFile()
        try {
            assertTrue(ModuleIsolationV1.request(OfflineModuleId.FAST_HTML).bundledOnly)
            assertThrows(IllegalArgumentException::class.java) {
                ModuleIsolationV1.prepare(base, OfflineModuleId.FAST_HTML)
            }
            assertTrue(base.listFiles().isNullOrEmpty())
        } finally { base.deleteRecursively() }
    }

    @Test fun moduleLocationsAreDisjointAndDoNotTouchExistingUserData() {
        val base = Files.createTempDirectory("appforge-isolated").toFile()
        try {
            val project = File(base, "project.txt").apply { writeText("keep project") }
            val legacy = File(base, "legacy-rootfs").apply { mkdir(); File(this, "tool").writeText("keep legacy") }
            val rust = ModuleIsolationV1.prepare(base, OfflineModuleId.RUST_ANDROID_NDK)
            val expo = ModuleIsolationV1.prepare(base, OfflineModuleId.EXPO_REACT_NATIVE)
            assertNotEquals(rust.base.canonicalPath, expo.base.canonicalPath)
            assertNotEquals(rust.rootfs.canonicalPath, expo.rootfs.canonicalPath)
            assertNotEquals(rust.downloads.canonicalPath, expo.downloads.canonicalPath)
            assertNotEquals(rust.apt.canonicalPath, expo.apt.canonicalPath)
            assertEquals("keep project", project.readText())
            assertEquals("keep legacy", File(legacy, "tool").readText())
        } finally { base.deleteRecursively() }
    }

    @Test fun signedArchiveCopyIsIndependentAndNeverHardlinked() {
        val base = Files.createTempDirectory("appforge-archive-copy").toFile()
        try {
            val source = File(base, "signed-packet.deb").apply { writeText("verified bytes") }
            val digest = sha(source)
            val expo = ModuleIsolationV1.prepare(base, OfflineModuleId.EXPO_REACT_NATIVE)
            val rust = ModuleIsolationV1.prepare(base, OfflineModuleId.RUST_ANDROID_NDK)
            val first = ModuleIsolationV1.copyVerifiedArchive(expo, source, digest, source.length())
            val second = ModuleIsolationV1.copyVerifiedArchive(rust, source, digest, source.length())
            assertFalse(Files.isSameFile(first.toPath(), second.toPath()))
            assertFalse(Files.isSameFile(first.toPath(), source.toPath()))
            assertEquals(digest, sha(first))
            assertEquals(digest, sha(second))
            source.writeText("changed outside verified store")
            assertEquals(digest, sha(first))
            assertEquals(digest, sha(second))
        } finally { base.deleteRecursively() }
    }

    @Test fun damagedAndSymlinkedArchivesAreRejectedWithoutChangingTargets() {
        val base = Files.createTempDirectory("appforge-reject-archive").toFile()
        try {
            val original = File(base, "source").apply { writeText("verified") }
            val expo = ModuleIsolationV1.prepare(base, OfflineModuleId.EXPO_REACT_NATIVE)
            assertThrows(IllegalArgumentException::class.java) {
                ModuleIsolationV1.copyVerifiedArchive(expo, original, "f".repeat(64), original.length())
            }
            val link = File(base, "source-link")
            Files.createSymbolicLink(link.toPath(), original.toPath())
            assertThrows(IllegalArgumentException::class.java) {
                ModuleIsolationV1.copyVerifiedArchive(expo, link, sha(original), original.length())
            }
            assertTrue(expo.downloads.listFiles().isNullOrEmpty())
        } finally { base.deleteRecursively() }
    }

    @Test fun symlinkedModuleNamespaceCannotBeEntered() {
        val base = Files.createTempDirectory("appforge-link").toFile()
        try {
            val foreign = File(base, "foreign").apply { mkdir() }
            val top = File(base, "isolated-modules-v1").apply { mkdir() }
            Files.createSymbolicLink(File(top, "node_web").toPath(), foreign.toPath())
            assertThrows(IllegalArgumentException::class.java) {
                ModuleIsolationV1.prepare(base, OfflineModuleId.NODE_WEB)
            }
            assertTrue(foreign.listFiles().isNullOrEmpty())
        } finally { base.deleteRecursively() }
    }

    private fun sha(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
}
