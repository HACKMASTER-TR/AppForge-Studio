package com.appforge.studio.terminal

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateProjectPipelineTest {
    @Test
    fun parsesRuntimeProbeAndFindsMissingTools() {
        val root =
            Files.createTempDirectory(
                "appforge-pipeline"
            ).toFile()

        try {
            File(root, "package.json")
                .writeText("{}")

            val plan =
                UltimateProjectAutomationPlanner
                    .plan(
                        root,
                        AppForgeProjectKind.NODE
                    )

            val report =
                UltimateProjectHealthChecker
                    .report(
                        workspace = root,
                        plan = plan,
                        selectedToolchains =
                            setOf(
                                LinuxToolchainId.BASE,
                                LinuxToolchainId.NODE
                            ),
                        runtimeOutput =
                            "git\tOK\t/usr/bin/git\nnode\tOK\t/usr/bin/node\nnpm\tYOK\n"
                    )

            assertEquals(
                setOf("npm"),
                report.missingCommands
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nodeMultipleLockFilesProduceWarning() {
        val root =
            Files.createTempDirectory(
                "appforge-locks"
            ).toFile()

        try {
            File(root, "package.json")
                .writeText("{}")
            File(root, "package-lock.json")
                .writeText("{}")
            File(root, "yarn.lock")
                .writeText("")

            val plan =
                UltimateProjectAutomationPlanner
                    .plan(
                        root,
                        AppForgeProjectKind.NODE
                    )

            val issues =
                UltimateProjectHealthChecker
                    .staticIssues(
                        root,
                        plan
                    )

            assertTrue(
                issues.any {
                    it.title.contains(
                        "lock",
                        ignoreCase = true
                    )
                }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aiPacketMasksCommonSecrets() {
        val health =
            ProjectHealthReport(
                issues = emptyList(),
                runtime = emptyMap(),
                requiredCommands = emptySet(),
                missingCommands = emptySet()
            )

        val packet =
            UltimatePipelineAiContextBuilder
                .build(
                    projectKind =
                        AppForgeProjectKind.NODE,
                    failedTitle =
                        "test",
                    failedCommand =
                        "npm test",
                    exitCode = 1,
                    output =
                        "Authorization: Bearer ghp_abcdefghijklmnopqrstuvwxyz1234567890",
                    health = health
                )

        assertFalse(
            packet.contains(
                "ghp_abcdefghijklmnopqrstuvwxyz1234567890"
            )
        )
        assertTrue(
            packet.length <=
                24 * 1024
        )
    }
}
