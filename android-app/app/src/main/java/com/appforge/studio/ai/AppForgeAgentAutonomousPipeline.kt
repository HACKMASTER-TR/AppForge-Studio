package com.appforge.studio.ai

internal class AppForgeAgentAutonomousPipeline(
    private val blueprintProvider: AppForgeAgentBlueprintProvider,
    private val stageRunner: AppForgeAgentStageRunner,
    private val patchProvider: AppForgeAgentPatchProviderV8? = null
) {
    fun run(request: AppForgeAgentAutonomousRequest): AppForgeAgentAutonomousResult {
        val events = mutableListOf<AppForgeAgentAutonomousEvent>()
        val checkpoints = mutableListOf<AppForgeWorkspaceCheckpoint>()
        var blueprint: AppForgeAgentBlueprint? = null
        var project: AppForgeGeneratedProject? = null

        fun event(
            stage: AppForgeAgentAutonomousStage,
            message: String,
            attempt: Int = 0,
            fingerprint: String? = null
        ) {
            events += AppForgeAgentAutonomousEvent(
                stage = stage,
                message = message,
                attempt = attempt,
                fingerprint = fingerprint
            )
        }

        fun rollbackAll() {
            if (!request.rollbackOnFailure) return
            checkpoints.asReversed().forEach { checkpoint ->
                runCatching {
                    AppForgeAgentWorkspaceTransaction.rollback(checkpoint)
                }.onFailure { error ->
                    event(
                        AppForgeAgentAutonomousStage.BLOCKED,
                        "Rollback warning: ${safeMessage(error)}"
                    )
                }
            }
        }

        fun blocked(reason: String): AppForgeAgentAutonomousResult {
            rollbackAll()
            event(AppForgeAgentAutonomousStage.BLOCKED, reason)
            return AppForgeAgentAutonomousResult(
                status = AppForgeAgentAutonomousStatus.BLOCKED,
                message = reason,
                blueprint = blueprint,
                generatedProject = project,
                checkpoints = checkpoints.toList(),
                events = events.toList(),
                deployGate = AppForgeAgentDeployGateV8.NOT_REACHED
            )
        }

        try {
            event(AppForgeAgentAutonomousStage.BLUEPRINT, "Structured blueprint isteniyor.")
            val promptContract = AppForgeAgentBlueprintPrompt.build(
                userPrompt = request.userPrompt,
                preferredPlatform = request.preferredPlatform
            )
            val rawBlueprint = blueprintProvider.createBlueprint(promptContract)
            blueprint = AppForgeAgentBlueprintJson.parse(rawBlueprint)

            require(blueprint!!.platform == request.preferredPlatform) {
                "AI blueprint platformu istenen platform ile eşleşmiyor."
            }

            request.fullStackContract?.let { contract ->
                require(contract.frontendPlatform == request.preferredPlatform) {
                    "Full-stack contract frontendPlatform ile Autonomous Agent platformu eşleşmeli."
                }
                require(contract.appName.trim() == blueprint!!.appName.trim()) {
                    "Full-stack contract appName ile blueprint appName eşleşmeli."
                }
            }

            event(AppForgeAgentAutonomousStage.GENERATE, "Deterministik kaynak üretimi başladı.")
            project = request.fullStackContract?.let { contract ->
                AppForgeAgentFullStackCodegen.generate(blueprint!!, contract)
            } ?: AppForgeAgentCodegen.generate(blueprint!!)

            event(AppForgeAgentAutonomousStage.APPLY, "Workspace transaction uygulanıyor.")
            val initialApply = AppForgeAgentWorkspaceTransaction.apply(
                workspace = request.workspace,
                project = project!!
            )
            checkpoints += initialApply.checkpoint

            val allowedRoots = project!!.files
                .map { it.path.substringBefore('/') }
                .filter { it.isNotBlank() }
                .toSet()

            val repairLoop = AppForgeAgentRepairLoop(
                AppForgeAgentRepairLoopConfig(
                    maxRepairAttempts = minOf(
                        request.maxRepairAttempts,
                        blueprint!!.maxRepairAttempts
                    ),
                    maxSameFingerprint = 1
                )
            )

            var stageIndex = 0
            val executionStages = listOf(
                AppForgeAgentExecutionStage.TEST,
                AppForgeAgentExecutionStage.BUILD
            )

            while (stageIndex < executionStages.size) {
                val executionStage = executionStages[stageIndex]
                event(
                    stage = when (executionStage) {
                        AppForgeAgentExecutionStage.TEST -> AppForgeAgentAutonomousStage.TEST
                        AppForgeAgentExecutionStage.BUILD -> AppForgeAgentAutonomousStage.BUILD
                    },
                    message = "${executionStage.name} gate çalıştırılıyor."
                )

                val execution = stageRunner.run(executionStage, request.workspace)
                if (execution.success) {
                    stageIndex += 1
                    continue
                }

                val failure = AppForgeAgentFailure(
                    phase = when (executionStage) {
                        AppForgeAgentExecutionStage.TEST -> AppForgeAgentFailurePhase.TEST
                        AppForgeAgentExecutionStage.BUILD -> AppForgeAgentFailurePhase.BUILD
                    },
                    exitCode = execution.exitCode,
                    output = execution.output.takeLast(MAX_FAILURE_OUTPUT_CHARS),
                    command = null
                )

                val decision = repairLoop.decide(failure)
                event(
                    stage = AppForgeAgentAutonomousStage.REPAIR,
                    message = decision.reason,
                    attempt = decision.attempt,
                    fingerprint = decision.fingerprint
                )

                when (decision.action) {
                    AppForgeAgentRepairAction.RETRY_PIPELINE -> {
                        continue
                    }

                    AppForgeAgentRepairAction.REQUEST_STRUCTURED_PATCH -> {
                        val provider = patchProvider
                            ?: return blocked("Structured patch gerekiyor ancak patch provider bağlı değil.")

                        val patch = provider.createPatch(
                            AppForgeAgentPatchRequestV8(
                                failureFingerprint = decision.fingerprint,
                                attempt = decision.attempt,
                                phase = failure.phase,
                                normalizedFailureOutput = AppForgeAgentFailureFingerprint
                                    .normalize(failure.output),
                                allowedRootPrefixes = allowedRoots,
                                generatedProjectDigestSha256 = project!!.digestSha256
                            )
                        )

                        require(patch.failureFingerprint == decision.fingerprint) {
                            "AI patch fingerprint güncel hatayla eşleşmiyor."
                        }
                        validatePatchRoots(patch, allowedRoots)

                        val patchApply = AppForgeAgentStructuredPatch.apply(
                            workspace = request.workspace,
                            plan = patch
                        )
                        checkpoints += patchApply.checkpoint

                        // Kaynak değiştiyse her zaman en baştan TEST -> BUILD doğrulaması yapılır.
                        stageIndex = 0
                    }

                    AppForgeAgentRepairAction.STOP_MANUAL -> {
                        return blocked(decision.reason)
                    }
                }
            }

            event(
                AppForgeAgentAutonomousStage.COMPLETE,
                "Test ve build gate'leri geçti; deploy için insan onayı gerekiyor."
            )

            return AppForgeAgentAutonomousResult(
                status = AppForgeAgentAutonomousStatus.SUCCESS,
                message = "Autonomous build tamamlandı. Deploy otomatik yapılmadı.",
                blueprint = blueprint,
                generatedProject = project,
                checkpoints = checkpoints.toList(),
                events = events.toList(),
                deployGate = AppForgeAgentDeployGateV8.REVIEW_REQUIRED
            )
        } catch (error: Throwable) {
            return blocked(safeMessage(error))
        }
    }

    private fun validatePatchRoots(
        plan: AppForgeAgentPatchPlan,
        allowedRoots: Set<String>
    ) {
        require(allowedRoots.isNotEmpty()) { "Patch root whitelist boş olamaz." }
        plan.operations.forEach { operation ->
            val normalized = operation.path.trim().replace('\\', '/')
            val root = normalized.substringBefore('/')
            require(root in allowedRoots) {
                "AI patch üretilen proje root whitelist'i dışında: ${operation.path}"
            }
        }
    }

    private fun safeMessage(error: Throwable): String =
        (error.message ?: error::class.simpleName ?: "Autonomous Agent blocked")
            .replace(Regex("ghp_[A-Za-z0-9]{20,}"), "[REDACTED]")
            .replace(Regex("github_pat_[A-Za-z0-9_]{20,}"), "[REDACTED]")
            .replace(Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b"), "[REDACTED]")
            .take(2_000)

    private companion object {
        const val MAX_FAILURE_OUTPUT_CHARS = 64 * 1024
    }
}
