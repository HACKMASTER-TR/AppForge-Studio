package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentArtifactContractsTest {
    @Test
    fun sanitizerRedactsCredentialPatterns() {
        val raw =
            "Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345\n" +
                "token=super-secret-value\n" +
                "ghp_abcdefghijklmnopqrstuvwxyz123456"

        val safe =
            AppForgeAgentArtifactSafety.sanitize(raw)

        assertFalse(
            safe.contains(
                "abcdefghijklmnopqrstuvwxyz012345"
            )
        )
        assertFalse(
            safe.contains(
                "super-secret-value"
            )
        )
        assertFalse(
            safe.contains(
                "ghp_"
            )
        )
        assertTrue(
            safe.contains(
                "[REDACTED]"
            )
        )
    }

    @Test
    fun artifactFileNameUsesBoundedSafeBuildId() {
        val name =
            AppForgeAgentArtifactSafety.fileName(
                "build/../../ABC:123",
                "apk"
            )

        assertEquals(
            "AppForge-buildABC123.apk",
            name
        )
    }

    @Test(expected = IllegalStateException::class)
    fun unknownDownloadKindIsRejected() {
        AppForgeAgentArtifactSafety.safeKind(
            "zip"
        )
    }

    @Test
    fun sourceNameIsPlatformScoped() {
        val name =
            AppForgeAgentArtifactSafety.sourceZipName(
                "Görev Cep / Test",
                AppForgeAgentPlatform.WEB
            )

        assertTrue(
            name.endsWith(
                "-web-source.zip"
            )
        )
        assertFalse(
            name.contains(
                "/"
            )
        )
    }

    @Test
    fun byteFormattingIsHumanReadable() {
        assertEquals(
            "512 B",
            AppForgeAgentArtifactSafety.formatBytes(
                512
            )
        )
        assertEquals(
            "1.0 KiB",
            AppForgeAgentArtifactSafety.formatBytes(
                1024
            )
        )
        assertEquals(
            "1.0 MiB",
            AppForgeAgentArtifactSafety.formatBytes(
                1024L * 1024L
            )
        )
    }
}
