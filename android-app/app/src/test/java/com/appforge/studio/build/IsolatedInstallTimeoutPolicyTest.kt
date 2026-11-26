package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test

class IsolatedInstallTimeoutPolicyTest {
    private val androidToolchain = "/bin/sh /workspace/runtime/install-toolchain.sh webview-static"
    private val androidPack = "/bin/sh /workspace/runtime/prepare-offline-pack.sh android"

    @Test fun onlyAndroidCoreHeavyStagesCanUseSeventyFiveMinutes() {
        assertEquals(4_500_000L, IsolatedInstallTimeoutPolicy.forStep(
            OfflineModuleId.ANDROID_CORE, androidToolchain))
        assertEquals(4_500_000L, IsolatedInstallTimeoutPolicy.forStep(
            OfflineModuleId.ANDROID_CORE, androidPack))
        assertEquals(1_800_000L, IsolatedInstallTimeoutPolicy.forStep(
            OfflineModuleId.ANDROID_CORE, "/bin/sh /workspace/runtime/module-functional-test.sh ANDROID_CORE"))
    }

    @Test fun otherModulesAndGenericShellRemainOnThirtyMinutes() {
        for (module in OfflineModuleId.entries) {
            if (module == OfflineModuleId.ANDROID_CORE) continue
            assertEquals(1_800_000L, IsolatedInstallTimeoutPolicy.forStep(module, androidToolchain))
        }
        assertEquals(1_800_000L, IsolatedInstallTimeoutPolicy.forStep(null, androidPack))
        assertEquals(1_800_000L, IsolatedInstallTimeoutPolicy.forStep(
            OfflineModuleId.RUST_ANDROID_NDK, androidPack))
    }

    @Test fun timeoutReportIsBoundedAndNeverPrintsRawShellCommand() {
        val secret = "secret-password-TEST123"
        val text = IsolatedInstallTimeoutPolicy.timeoutMessage(
            OfflineModuleId.ANDROID_CORE,
            "PASSWORD=$secret " + androidToolchain,
            ModulePhase.INSTALLING,
            4_500_000L)
        assertTrue(text.contains("APPFORGE_ISOLATED_INSTALL_TIMEOUT"))
        assertTrue(text.contains("module=ANDROID_CORE"))
        assertTrue(text.contains("step=ANDROID_TOOLCHAIN"))
        assertTrue(text.contains("lastAptPhase=INSTALLING"))
        assertTrue(text.contains("limitMinutes=75"))
        assertFalse(text.contains(secret))
        assertFalse(text.contains("/workspace/"))
    }
}
