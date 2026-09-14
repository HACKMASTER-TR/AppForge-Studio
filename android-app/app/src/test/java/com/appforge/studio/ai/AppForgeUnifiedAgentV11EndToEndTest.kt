package com.appforge.studio.ai

import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeUnifiedAgentV11EndToEndTest {
    @Test
    fun promptToBlueprintDesignerRepairBuildEndsAtReviewRequired() {
        val workspace = kotlin.io.path.createTempDirectory("appforge-v11-e2e-").toFile().canonicalFile
        val blueprint = validBlueprint()
        val provider = AppForgeAgentBlueprintProvider {
            AppForgeAgentBlueprintJson.encode(blueprint)
        }
        val scripted = ArrayDeque(
            listOf(
                AppForgeAgentExecutionResult(false, 1, "compile error"),
                AppForgeAgentExecutionResult(true, output = "TEST PASS"),
                AppForgeAgentExecutionResult(true, output = "BUILD PASS")
            )
        )
        val stages = mutableListOf<AppForgeAgentExecutionStage>()

        val patchProvider = AppForgeAgentPatchProviderV8 { request ->
            val target = workspace.walkTopDown().first {
                it.isFile && ".appforge-agent-v4" !in it.invariantSeparatorsPath
            }
            AppForgeAgentPatchPlan(
                failureFingerprint = request.failureFingerprint,
                operations = listOf(
                    AppForgeAgentPatchOperation(
                        path = target.relativeTo(workspace).invariantSeparatorsPath,
                        baseSha256 = sha256(target.readBytes()),
                        replacementContent = target.readText() + "\n// v11-e2e-repair\n"
                    )
                )
            )
        }

        val orchestrator = AppForgeAgentStudioOrchestrator(
            blueprintProvider = provider,
            stageRunner = AppForgeAgentStageRunner { stage, _ ->
                stages += stage
                scripted.removeFirst()
            },
            patchProvider = patchProvider
        )

        val prompted = orchestrator.updatePrompt(
            orchestrator.initialState(),
            "Görev takip uygulaması oluştur."
        )
        val reviewed = orchestrator.generateBlueprint(prompted)

        assertEquals(AppForgeAgentStudioStep.BLUEPRINT_REVIEW, reviewed.step)
        assertTrue(reviewed.validation?.valid == true)

        val designer = orchestrator.openDesigner(reviewed)
        assertEquals(AppForgeAgentStudioStep.DESIGN, designer.step)

        val current = requireNotNull(designer.blueprint)
        val edited = orchestrator.updateBlueprint(
            designer,
            current.copy(
                tokens = current.tokens.copy(primary = "#0057B8")
            )
        )

        assertTrue(edited.canBuild)

        val result = orchestrator.build(
            state = edited,
            request = AppForgeAgentStudioBuildRequest(
                workspace = workspace,
                maxRepairAttempts = 2,
                rollbackOnFailure = true
            )
        )

        assertEquals(AppForgeAgentStudioStep.RESULT, result.step)
        assertEquals(AppForgeAgentAutonomousStatus.SUCCESS, result.autonomousResult?.status)
        assertEquals(
            AppForgeAgentDeployGateV8.REVIEW_REQUIRED,
            result.autonomousResult?.deployGate
        )
        assertEquals(
            listOf(
                AppForgeAgentExecutionStage.TEST,
                AppForgeAgentExecutionStage.TEST,
                AppForgeAgentExecutionStage.BUILD
            ),
            stages
        )
        assertEquals(2, result.autonomousResult?.checkpoints?.size)
        assertTrue(
            workspace.walkTopDown().any {
                it.isFile && ".appforge-agent-v4" !in it.invariantSeparatorsPath
            }
        )

        workspace.deleteRecursively()
    }

    @Test
    fun successfulArtifactReviewCanPassTechnicalGateButStillRequiresReview() {
        val remote = AppForgeAgentRemoteBuildInfo(
            buildId = "build-v11-1",
            buildNo = 101,
            status = "success",
            progress = 100,
            apkAvailable = true,
            aabAvailable = true,
            exeAvailable = false
        )
        val artifacts = AppForgeAgentArtifactState(
            buildId = remote.buildId,
            logsLoaded = true,
            testLabAvailable = true
        )

        val readiness = AppForgeAgentReleaseReadinessEvaluator.evaluate(
            remote = remote,
            artifacts = artifacts
        )

        assertTrue(readiness.ready)
        assertTrue(readiness.reviewRequired)
        assertTrue(readiness.blockers.isEmpty())
    }

    @Test
    fun failedBuildNeverBecomesReleaseReady() {
        val remote = AppForgeAgentRemoteBuildInfo(
            buildId = "build-v11-fail",
            buildNo = 102,
            status = "failed",
            progress = 72,
            apkAvailable = false,
            aabAvailable = false,
            exeAvailable = false
        )

        val readiness = AppForgeAgentReleaseReadinessEvaluator.evaluate(
            remote = remote,
            artifacts = AppForgeAgentArtifactState(
                buildId = remote.buildId,
                logsLoaded = true,
                testLabAvailable = false
            )
        )

        assertFalse(readiness.ready)
        assertTrue(readiness.reviewRequired)
        assertTrue(readiness.blockers.isNotEmpty())
    }

    @Test
    fun resultStateCanRebuildSameBlueprint() {
        val blueprint = validBlueprint()
        val state = AppForgeAgentStudioState(
            step = AppForgeAgentStudioStep.RESULT,
            prompt = "Görev takip uygulaması oluştur.",
            platform = AppForgeAgentPlatform.ANDROID,
            blueprint = blueprint,
            validation = AppForgeAgentBlueprintValidator.validate(blueprint),
            busy = false
        )

        assertTrue(state.canRebuild)
        assertFalse(state.copy(busy = true).canRebuild)
    }

    @Test
    fun highSecurityFindingBlocksReleaseReady() {
        val remote = AppForgeAgentRemoteBuildInfo(
            buildId = "build-v11-risk",
            buildNo = 103,
            status = "success",
            progress = 100,
            apkAvailable = true,
            aabAvailable = false,
            exeAvailable = false
        )

        val readiness = AppForgeAgentReleaseReadinessEvaluator.evaluate(
            remote = remote,
            artifacts = AppForgeAgentArtifactState(
                buildId = remote.buildId,
                testLabAvailable = true,
                security = listOf(
                    AppForgeAgentSecurityFinding(
                        severity = "CRITICAL",
                        title = "Credential exposure",
                        detail = "Synthetic test finding"
                    )
                )
            )
        )

        assertFalse(readiness.ready)
        assertTrue(readiness.blockers.any { it.contains("HIGH/CRITICAL") })
    }

    @Test
    fun e2eFailureOutputSentToRepairIsRedacted() {
        val workspace = kotlin.io.path.createTempDirectory("appforge-v11-redaction-").toFile().canonicalFile
        var captured: AppForgeAgentPatchRequestV8? = null

        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = AppForgeAgentBlueprintProvider {
                AppForgeAgentBlueprintJson.encode(validBlueprint())
            },
            stageRunner = AppForgeAgentStageRunner { _, _ ->
                AppForgeAgentExecutionResult(
                    success = false,
                    exitCode = 1,
                    output = "token=ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890 compile error"
                )
            },
            patchProvider = AppForgeAgentPatchProviderV8 { request ->
                captured = request
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

        pipeline.run(
            AppForgeAgentAutonomousRequest(
                userPrompt = "Görev takip uygulaması oluştur.",
                preferredPlatform = AppForgeAgentPlatform.ANDROID,
                workspace = workspace,
                maxRepairAttempts = 1
            )
        )

        assertTrue(captured != null)
        assertFalse(captured!!.normalizedFailureOutput.contains("ghp_"))
        assertTrue(captured!!.normalizedFailureOutput.contains("[REDACTED]"))
        workspace.deleteRecursively()
    }

    private fun validBlueprint() = AppForgeAgentBlueprint(
        appName = "TaskFlow",
        prompt = "Görev takip uygulaması.",
        platform = AppForgeAgentPlatform.ANDROID,
        tokens = AppForgeAgentDesignTokens(
            primary = "#6750A4",
            secondary = "#625B71",
            background = "#FFFBFE",
            surface = "#FFFBFE",
            text = "#1D1B20",
            spacingUnitDp = 8,
            cornerRadiusDp = 16
        ),
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
