package com.appforge.studio.terminal

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalUltimateFoundationTest {
    @Test
    fun detectsReactAndBuildsSafeActions() {
        withTempProject { root ->
            File(
                root,
                "package.json"
            ).writeText(
                """{"dependencies":{"react":"19.0.0"}}"""
            )

            val result =
                AppForgeProjectDetector.detect(
                    root
                )

            assertEquals(
                AppForgeProjectKind.REACT,
                result.kind
            )

            assertTrue(
                result.actions.any {
                    it.command ==
                        "npm install"
                }
            )
        }
    }

    @Test
    fun detectsFlutterBeforeGenericAndroidMarkers() {
        withTempProject { root ->
            File(
                root,
                "pubspec.yaml"
            ).writeText(
                "name: appforge_test"
            )

            File(
                root,
                "android"
            ).mkdirs()

            val result =
                AppForgeProjectDetector.detect(
                    root
                )

            assertEquals(
                AppForgeProjectKind.FLUTTER,
                result.kind
            )
        }
    }

    @Test
    fun advisorKeepsExistingDangerousCommandPolicy() {
        val dangerous =
            TerminalCommandAdvisor.explain(
                "rm -rf build"
            )

        assertTrue(
            dangerous.allowed
        )

        assertTrue(
            dangerous.requiresConfirmation
        )

        val blocked =
            TerminalCommandAdvisor.explain(
                "reboot"
            )

        assertFalse(
            blocked.allowed
        )
    }

    @Test
    fun masksCommonSecretsBeforeTerminalDisplay() {
        val masked =
            TerminalSecretMasker.redact(
                "GITHUB_TOKEN=ghp_1234567890abcdefghijklmnop Authorization: Bearer abcdefghijklmnop"
            )

        assertFalse(
            masked.contains(
                "ghp_"
            )
        )

        assertFalse(
            masked.contains(
                "abcdefghijklmnop"
            )
        )

        assertTrue(
            masked.contains(
                "••••••"
            )
        )
    }

    @Test
    fun healthInspectorWarnsAboutEnvFile() {
        withTempProject { root ->
            File(
                root,
                "requirements.txt"
            ).writeText(
                "pytest\n"
            )

            File(
                root,
                ".env"
            ).writeText(
                "TOKEN=secret\n"
            )

            val result =
                ProjectHealthInspector.inspect(
                    root
                )

            assertTrue(
                result.any {
                    it.title ==
                        ".env dosyası bulundu"
                }
            )
        }
    }

    private fun withTempProject(
        block: (File) -> Unit
    ) {
        val root =
            Files
                .createTempDirectory(
                    "appforge-ultimate"
                )
                .toFile()

        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
