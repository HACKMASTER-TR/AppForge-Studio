package com.appforge.studio.ai

import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentAutonomousPipelineTest {
    @Test
    fun successfulRunStopsAtReviewRequiredDeployGate() {
        val workspace = tempWorkspace()
        val runnerStages = mutableListOf<AppForgeAgentExecutionStage>()
        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = validBlueprintProvider(),
            stageRunner = AppForgeAgentStageRunner { stage, _ ->
                runnerStages += stage
                AppForgeAgentExecutionResult(success = true)
            }
        )

        val result = pipeline.run(request(workspace))

        assertEquals(AppForgeAgentAutonomousStatus.SUCCESS, result.status)
        assertEquals(AppForgeAgentDeployGateV8.REVIEW_REQUIRED, result.deployGate)
        assertEquals(
            listOf(AppForgeAgentExecutionStage.TEST, AppForgeAgentExecutionStage.BUILD),
            runnerStages
        )
        assertTrue(result.generatedProject!!.files.all { File(workspace, it.path).isFile })
    }

    @Test
    fun invalidBlueprintBlocksBeforeWorkspaceMutation() {
        val workspace = tempWorkspace()
        var runnerCalled = false
        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = AppForgeAgentBlueprintProvider { "not-json" },
            stageRunner = AppForgeAgentStageRunner { _, _ ->
                runnerCalled = true
                AppForgeAgentExecutionResult(success = true)
            }
        )

        val result = pipeline.run(request(workspace))

        assertEquals(AppForgeAgentAutonomousStatus.BLOCKED, result.status)
        assertFalse(runnerCalled)
        assertTrue(workspace.walkTopDown().filter { it.isFile }.none())
    }

    @Test
    fun structuredPatchRestartsFromTestThenBuild() {
        val workspace = tempWorkspace()
        val scripted = ArrayDeque(
            listOf(
                AppForgeAgentExecutionResult(false, 1, "compile error"),
                AppForgeAgentExecutionResult(true),
                AppForgeAgentExecutionResult(true)
            )
        )
        val stages = mutableListOf<AppForgeAgentExecutionStage>()

        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = validBlueprintProvider(),
            stageRunner = AppForgeAgentStageRunner { stage, _ ->
                stages += stage
                scripted.removeFirst()
            },
            patchProvider = AppForgeAgentPatchProviderV8 { patchRequest ->
                val target = workspace.walkTopDown()
                    .first { it.isFile && ".appforge-agent-v4" !in it.path }
                AppForgeAgentPatchPlan(
                    failureFingerprint = patchRequest.failureFingerprint,
                    operations = listOf(
                        AppForgeAgentPatchOperation(
                            path = target.relativeTo(workspace).invariantSeparatorsPath,
                            baseSha256 = sha256(target.readBytes()),
                            replacementContent = target.readText() + "\n// repaired\n"
                        )
                    )
                )
            }
        )

        val result = pipeline.run(request(workspace))

        assertEquals(AppForgeAgentAutonomousStatus.SUCCESS, result.status)
        assertEquals(
            listOf(
                AppForgeAgentExecutionStage.TEST,
                AppForgeAgentExecutionStage.TEST,
                AppForgeAgentExecutionStage.BUILD
            ),
            stages
        )
        assertEquals(2, result.checkpoints.size)
    }

    @Test
    fun repeatedFailureBlocksAndRollsBackGeneratedFiles() {
        val workspace = tempWorkspace()
        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = validBlueprintProvider(),
            stageRunner = AppForgeAgentStageRunner { _, _ ->
                AppForgeAgentExecutionResult(false, 1, "same compile error")
            },
            patchProvider = AppForgeAgentPatchProviderV8 { request ->
                val target = workspace.walkTopDown()
                    .first { it.isFile && ".appforge-agent-v4" !in it.path }
                AppForgeAgentPatchPlan(
                    failureFingerprint = request.failureFingerprint,
                    operations = listOf(
                        AppForgeAgentPatchOperation(
                            target.relativeTo(workspace).invariantSeparatorsPath,
                            sha256(target.readBytes()),
                            target.readText() + "\n// attempted repair\n"
                        )
                    )
                )
            }
        )

        val result = pipeline.run(request(workspace))

        assertEquals(AppForgeAgentAutonomousStatus.BLOCKED, result.status)
        assertEquals(AppForgeAgentDeployGateV8.NOT_REACHED, result.deployGate)
        assertTrue(
            workspace.walkTopDown()
                .filter { it.isFile && ".appforge-agent-v4" !in it.path }
                .none()
        )
    }

    @Test
    fun stalePatchFingerprintIsRejectedAndRolledBack() {
        val workspace = tempWorkspace()
        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = validBlueprintProvider(),
            stageRunner = AppForgeAgentStageRunner { _, _ ->
                AppForgeAgentExecutionResult(false, 1, "compile error")
            },
            patchProvider = AppForgeAgentPatchProviderV8 {
                AppForgeAgentPatchPlan(
                    failureFingerprint = "0".repeat(64),
                    operations = listOf(
                        AppForgeAgentPatchOperation(
                            path = "android/new.kt",
                            baseSha256 = null,
                            replacementContent = "package sample"
                        )
                    )
                )
            }
        )

        val result = pipeline.run(request(workspace))

        assertEquals(AppForgeAgentAutonomousStatus.BLOCKED, result.status)
        assertTrue(result.message.contains("fingerprint", ignoreCase = true))
    }

    @Test
    fun patchOutsideGeneratedRootWhitelistIsRejected() {
        val workspace = tempWorkspace()
        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = validBlueprintProvider(),
            stageRunner = AppForgeAgentStageRunner { _, _ ->
                AppForgeAgentExecutionResult(false, 1, "compile error")
            },
            patchProvider = AppForgeAgentPatchProviderV8 { request ->
                AppForgeAgentPatchPlan(
                    failureFingerprint = request.failureFingerprint,
                    operations = listOf(
                        AppForgeAgentPatchOperation(
                            path = "docs/escape.md",
                            baseSha256 = null,
                            replacementContent = "blocked"
                        )
                    )
                )
            }
        )

        val result = pipeline.run(request(workspace))

        assertEquals(AppForgeAgentAutonomousStatus.BLOCKED, result.status)
        assertTrue(result.message.contains("whitelist", ignoreCase = true))
        assertFalse(File(workspace, "docs/escape.md").exists())
    }

    @Test
    fun failureOutputGivenToPatchProviderIsRedacted() {
        val workspace = tempWorkspace()
        var captured: AppForgeAgentPatchRequestV8? = null
        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = validBlueprintProvider(),
            stageRunner = AppForgeAgentStageRunner { _, _ ->
                AppForgeAgentExecutionResult(
                    false,
                    1,
                    "token=ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890 compile error"
                )
            },
            patchProvider = AppForgeAgentPatchProviderV8 { request ->
                captured = request
                AppForgeAgentPatchPlan(
                    failureFingerprint = "f".repeat(64),
                    operations = listOf(
                        AppForgeAgentPatchOperation("android/x.kt", null, "package x")
                    )
                )
            }
        )

        pipeline.run(request(workspace))

        assertNotNull(captured)
        assertFalse(captured!!.normalizedFailureOutput.contains("ghp_"))
        assertTrue(captured!!.normalizedFailureOutput.contains("[REDACTED]"))
    }

    private fun request(workspace: File) = AppForgeAgentAutonomousRequest(
        userPrompt = "Basit görev takip uygulaması oluştur.",
        preferredPlatform = AppForgeAgentPlatform.ANDROID,
        workspace = workspace,
        maxRepairAttempts = 2
    )

    private fun validBlueprintProvider() = AppForgeAgentBlueprintProvider {
        AppForgeAgentBlueprintJson.encode(
            AppForgeAgentBlueprint(
                appName = "TaskFlow",
                prompt = "Görev takip uygulaması.",
                platform = AppForgeAgentPlatform.ANDROID,
                startRoute = "/home",
                screens = listOf(
                    AppForgeAgentScreenSpec(
                        id = "home",
                        title = "Ana Sayfa",
                        route = "/home",
                        purpose = "Görevleri gösterir.",
                        components = listOf("TaskList")
                    )
                ),
                maxRepairAttempts = 2
            )
        )
    }

    private fun tempWorkspace(): File =
        kotlin.io.path.createTempDirectory("appforge-v8-").toFile().canonicalFile

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
