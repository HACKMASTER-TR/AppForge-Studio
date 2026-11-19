package com.appforge.studio.build

import com.appforge.studio.terminal.LinuxArchitecture
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Non-network unit tests: no user data, no Android device needed. */
class IsolatedModuleInstallerV3Test {
    @Test fun windowsNativeOnlyRequestsOwnLinuxAndCrossCompiler() {
        val request = ModuleIsolationV1.request(OfflineModuleId.WINDOWS_NATIVE)
        assertEquals(listOf(OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.WINDOWS_NATIVE),
            request.dependencyOrder)
        assertFalse(request.requiresSdkLicense)
    }

    @Test fun expoAndRustNdkHaveDisjointAptAndDpkgTrees() {
        val root = Files.createTempDirectory("appforge-iso-all").toFile()
        try {
            val expo = IsolatedModuleRuntimeV2.layout(root,OfflineModuleId.EXPO_REACT_NATIVE,
                LinuxArchitecture.ARM64)
            val ndk = IsolatedModuleRuntimeV2.layout(root,OfflineModuleId.RUST_ANDROID_NDK,
                LinuxArchitecture.ARM64)
            IsolatedModuleRuntimeV2.requireSeparated(expo,ndk)
            assertNotEquals(expo.dpkgStatus.absolutePath,ndk.dpkgStatus.absolutePath)
            assertNotEquals(expo.aptLists.absolutePath,ndk.aptLists.absolutePath)
            assertNotEquals(expo.namespace.downloads.absolutePath,ndk.namespace.downloads.absolutePath)
        } finally { root.deleteRecursively() }
    }

    @Test fun noInstallReceiptOrStateAppearsFromPathPreparation() {
        val home = Files.createTempDirectory("appforge-iso-receipt").toFile()
        try {
            val userProject = File(home,"project-source.txt").apply {writeText("untouched")}
            val currentRuntime = File(home,"device-build").apply {mkdir()}
            File(currentRuntime,"marker").writeText("legacy")
            val target = IsolatedModuleRuntimeV2.layout(home,OfflineModuleId.WINDOWS_NATIVE,
                LinuxArchitecture.ARM64)
            assertFalse(File(target.linux.rootfsDirectory,
                "opt/appforge-device/isolated-v3/receipt.json").exists())
            assertEquals("untouched",userProject.readText())
            assertEquals("legacy",File(currentRuntime,"marker").readText())
        } finally { home.deleteRecursively() }
    }
}
