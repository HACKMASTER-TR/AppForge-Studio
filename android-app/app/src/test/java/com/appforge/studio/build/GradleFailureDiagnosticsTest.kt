package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

internal class GradleFailureDiagnosticsTest {
    @Test fun classifiesNetworkAndSecurityErrors() {
        assertEquals("NETWORK_TIMEOUT",
            GradleFailureDiagnostics.classify("Read timed out"))
        assertEquals("TLS_TRUST",
            GradleFailureDiagnostics.classify("PKIX path building failed"))
        assertEquals("HTTP_AUTH",
            GradleFailureDiagnostics.classify("403 Forbidden"))
        assertEquals("RESOURCE_READ",
            GradleFailureDiagnostics.classify("Could not GET resource"))
    }

    @Test fun classifiesAgpAndChaquopyPluginFailures() {
        val agp = """Plugin [id: 'com.android.application', version: '9.1.1', apply: false] was not found in any of the following sources"""
        val python = """Plugin [id: 'com.chaquo.python', version: '17.0.0', apply: false] was not found"""
        assertEquals("AGP_PLUGIN_UNRESOLVED", GradleFailureDiagnostics.classify(agp))
        assertEquals("CHAQUOPY_PLUGIN_UNRESOLVED", GradleFailureDiagnostics.classify(python))
        assertEquals("TLS_TRUST", GradleFailureDiagnostics.classify("PKIX " + agp))
        assertEquals("AGP_JVM_TLS",
            GradleFailureDiagnostics.classify("$agp\nAPPFORGE_AGP_JVM_MARKER=TLS"))
        assertEquals("AGP_MAVEN_TLS",
            GradleFailureDiagnostics.classify("$agp\nAPPFORGE_AGP_MAVEN_PROBE=TLS"))
        assertEquals("AGP_PLUGIN_UNRESOLVED",
            GradleFailureDiagnostics.classify("$agp\nAPPFORGE_AGP_MAVEN_PROBE=REACHABLE"))
        assertEquals("TLS_TRUST",
            GradleFailureDiagnostics.classify("PKIX $agp\nAPPFORGE_AGP_MAVEN_PROBE=DNS"))
    }

    @Test fun persistedResultNeverContainsRawUrl() {
        val base=Files.createTempDirectory("gradle-diag").toFile()
        try {
            val privateRoot=File(base,"private").apply{mkdirs()}
            val workspace=File(base,"workspace").apply{mkdirs()}
            val log=File(workspace,
                ".appforge-offline-prewarm/android-gradle.log")
            log.parentFile.mkdirs()
            log.writeText(
                "Read timed out\nhttps://example.invalid/secret"
            )

            assertEquals("NETWORK_TIMEOUT",
                GradleFailureDiagnostics.capture(
                    privateRoot,workspace,OfflineModuleId.ANDROID_CORE
                ))

            val saved=File(privateRoot,
                "offline-module-v3/gradle-error-v1.json")
            assertTrue(saved.isFile)
            assertFalse(saved.readText().contains("example.invalid"))
            assertFalse(saved.readText().contains("secret"))
        } finally {
            base.deleteRecursively()
        }
    }
}
