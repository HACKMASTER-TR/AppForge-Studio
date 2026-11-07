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
