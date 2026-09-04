package com.appforge.studio.terminal

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateProjectAutomationTest {
    @Test
    fun nodeLockfileUsesNpmCiAndNodeToolchain() {
        val root =
            Files.createTempDirectory(
                "appforge-node-plan"
            ).toFile()

        try {
            root.resolve(
                "package.json"
            ).writeText(
                "{}"
            )

            root.resolve(
                "package-lock.json"
            ).writeText(
                "{}"
            )

            val plan =
                UltimateProjectAutomationPlanner
                    .plan(
                        root,
                        AppForgeProjectKind.NODE
                    )

            assertTrue(
                LinuxToolchainId.NODE in
                    plan.recommendedToolchains
            )

            assertEquals(
                "npm ci",
                plan.steps
                    .first {
                        it.kind ==
                            ProjectAutomationStepKind.INSTALL
                    }
                    .command
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deployFilesProduceProviderHintsWithoutDeploying() {
        val root =
            Files.createTempDirectory(
                "appforge-deploy-plan"
            ).toFile()

        try {
            root.resolve(
                "vercel.json"
            ).writeText(
                "{}"
            )

            root.resolve(
                "wrangler.toml"
            ).writeText(
                "name = \"demo\""
            )

            val plan =
                UltimateProjectAutomationPlanner
                    .plan(
                        root,
                        AppForgeProjectKind.REACT
                    )

            assertTrue(
                DeploymentProvider.VERCEL in
                    plan.deployHints
            )

            assertTrue(
                DeploymentProvider.CLOUDFLARE in
                    plan.deployHints
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun packageStoreCommandUsesPinnedCatalog() {
        val command =
            LinuxToolchainCatalog
                .installCommand(
                    setOf(
                        LinuxToolchainId.BASE,
                        LinuxToolchainId.PYTHON
                    )
                )

        assertTrue(
            command.startsWith(
                "apt-get update && "
            )
        )

        assertTrue(
            command.contains(
                "python3-pip"
            )
        )
    }
}
