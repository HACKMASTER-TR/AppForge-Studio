package com.appforge.studio.ai

internal class AppForgeAgentStudioOrchestrator(
    private val blueprintProvider: AppForgeAgentBlueprintProvider,
    private val stageRunner: AppForgeAgentStageRunner,
    private val patchProvider: AppForgeAgentPatchProviderV8? = null
) {
    fun initialState(
        platform: AppForgeAgentPlatform = AppForgeAgentPlatform.ANDROID
    ): AppForgeAgentStudioState = AppForgeAgentStudioState(platform = platform)

    fun updatePrompt(
        state: AppForgeAgentStudioState,
        prompt: String
    ): AppForgeAgentStudioState = state.copy(
        prompt = prompt.take(MAX_PROMPT_CHARS),
        step = if (state.step == AppForgeAgentStudioStep.BLOCKED) {
            AppForgeAgentStudioStep.PROMPT
        } else {
            state.step
        },
        message = ""
    )

    fun updatePlatform(
        state: AppForgeAgentStudioState,
        platform: AppForgeAgentPlatform
    ): AppForgeAgentStudioState = state.copy(
        platform = platform,
        blueprint = null,
        validation = null,
        autonomousResult = null,
        step = AppForgeAgentStudioStep.PROMPT,
        message = ""
    )

    fun generateBlueprint(
        state: AppForgeAgentStudioState
    ): AppForgeAgentStudioState {
        if (!state.canGenerate) {
            return blocked(
                state,
                "Uygulama açıklaması boş olamaz ve 4000 karakteri aşamaz."
            )
        }

        return runCatching {
            val promptContract = AppForgeAgentBlueprintPrompt.build(
                userPrompt = state.prompt.trim(),
                preferredPlatform = state.platform
            )
            val raw = blueprintProvider.createBlueprint(promptContract)
            val blueprint = AppForgeAgentBlueprintJson.parse(raw)

            require(blueprint.platform == state.platform) {
                "AI blueprint platformu seçilen platform ile eşleşmiyor."
            }

            val validation = AppForgeAgentBlueprintValidator.validate(blueprint)
            require(validation.valid) {
                validation.issues
                    .filter { it.level == AgentBlueprintIssueLevel.ERROR }
                    .joinToString(" | ") { "${it.field}: ${it.message}" }
            }

            state.copy(
                step = AppForgeAgentStudioStep.BLUEPRINT_REVIEW,
                blueprint = blueprint,
                validation = validation,
                autonomousResult = null,
                message = "Blueprint hazır. İncele, düzenle veya derlemeye geç."
            )
        }.getOrElse { error ->
            blocked(state, safeMessage(error))
        }
    }

    fun openDesigner(
        state: AppForgeAgentStudioState
    ): AppForgeAgentStudioState {
        if (!state.canDesign) {
            return blocked(state, "Visual Designer için geçerli blueprint gerekli.")
        }
        return state.copy(
            step = AppForgeAgentStudioStep.DESIGN,
            message = "Visual Designer aktif."
        )
    }

    fun backToReview(
        state: AppForgeAgentStudioState
    ): AppForgeAgentStudioState {
        if (state.blueprint == null) {
            return reset(state)
        }
        return state.copy(
            step = AppForgeAgentStudioStep.BLUEPRINT_REVIEW,
            message = ""
        )
    }

    fun updateBlueprint(
        state: AppForgeAgentStudioState,
        blueprint: AppForgeAgentBlueprint
    ): AppForgeAgentStudioState {
        val validation = AppForgeAgentBlueprintValidator.validate(blueprint)
        return state.copy(
            blueprint = blueprint,
            validation = validation,
            autonomousResult = null,
            message = if (validation.valid) {
                "Tasarım değişiklikleri doğrulandı."
            } else {
                "Blueprint içinde düzeltilmesi gereken alanlar var."
            }
        )
    }

    fun markBuilding(
        state: AppForgeAgentStudioState
    ): AppForgeAgentStudioState {
        if (!state.canBuild) {
            return blocked(state, "Derleme için doğrulanmış blueprint gerekli.")
        }
        return state.copy(
            step = AppForgeAgentStudioStep.BUILD,
            autonomousResult = null,
            message = "TEST → BUILD → güvenli repair pipeline hazırlanıyor."
        )
    }

    fun build(
        state: AppForgeAgentStudioState,
        request: AppForgeAgentStudioBuildRequest
    ): AppForgeAgentStudioState {
        val blueprint = state.blueprint
            ?: return blocked(state, "Derleme için blueprint bulunamadı.")

        val validation = AppForgeAgentBlueprintValidator.validate(blueprint)
        if (!validation.valid) {
            return state.copy(
                validation = validation,
                step = AppForgeAgentStudioStep.DESIGN,
                message = "Geçersiz blueprint derlemeye gönderilmedi."
            )
        }

        val fixedBlueprintJson = AppForgeAgentBlueprintJson.encode(blueprint)
        val fixedProvider = AppForgeAgentBlueprintProvider { fixedBlueprintJson }

        val pipeline = AppForgeAgentAutonomousPipeline(
            blueprintProvider = fixedProvider,
            stageRunner = stageRunner,
            patchProvider = patchProvider
        )

        val result = pipeline.run(
            AppForgeAgentAutonomousRequest(
                userPrompt = state.prompt.ifBlank { blueprint.prompt },
                preferredPlatform = state.platform,
                workspace = request.workspace,
                fullStackContract = request.fullStackContract,
                maxRepairAttempts = minOf(
                    request.maxRepairAttempts,
                    blueprint.maxRepairAttempts
                ),
                rollbackOnFailure = request.rollbackOnFailure
            )
        )

        return if (result.status == AppForgeAgentAutonomousStatus.SUCCESS) {
            state.copy(
                step = AppForgeAgentStudioStep.RESULT,
                validation = validation,
                autonomousResult = result,
                message = result.message
            )
        } else {
            state.copy(
                step = AppForgeAgentStudioStep.BLOCKED,
                validation = validation,
                autonomousResult = result,
                message = result.message
            )
        }
    }

    fun reset(
        state: AppForgeAgentStudioState
    ): AppForgeAgentStudioState = AppForgeAgentStudioState(
        platform = state.platform
    )

    private fun blocked(
        state: AppForgeAgentStudioState,
        reason: String
    ): AppForgeAgentStudioState = state.copy(
        step = AppForgeAgentStudioStep.BLOCKED,
        message = reason.take(MAX_MESSAGE_CHARS)
    )

    private fun safeMessage(error: Throwable): String =
        (error.message ?: error::class.simpleName ?: "Studio işlemi engellendi.")
            .replace(Regex("ghp_[A-Za-z0-9]{20,}"), "[REDACTED]")
            .replace(Regex("github_pat_[A-Za-z0-9_]{20,}"), "[REDACTED]")
            .replace(Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b"), "[REDACTED]")
            .take(MAX_MESSAGE_CHARS)

    private companion object {
        const val MAX_PROMPT_CHARS = 4_000
        const val MAX_MESSAGE_CHARS = 2_000
    }
}
