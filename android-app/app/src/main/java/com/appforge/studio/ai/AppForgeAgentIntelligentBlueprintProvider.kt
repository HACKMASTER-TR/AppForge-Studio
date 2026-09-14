package com.appforge.studio.ai

internal fun interface AppForgeAgentRawAiProviderV9 {
    fun generateStructuredJson(prompt: String): String
}

internal class AppForgeAgentIntelligentBlueprintProvider(
    private val localProvider: AppForgeAgentRawAiProviderV9? = null,
    private val cloudProvider: AppForgeAgentRawAiProviderV9? = null,
    private val contextSource: AppForgeAgentContextSourceV9 = AppForgeAgentContextSourceV9 { emptyList() },
    private val policy: AppForgeAgentIntelligencePolicy = AppForgeAgentIntelligencePolicy()
) : AppForgeAgentBlueprintProvider {
    var lastPlan: AppForgeAgentIntelligencePlan? = null
        private set

    override fun createBlueprint(promptContract: String): String {
        val plan = AppForgeAgentIntelligenceEngine.plan(
            prompt = promptContract,
            facts = contextSource.load(),
            localAvailable = localProvider != null,
            cloudAvailable = cloudProvider != null,
            policy = policy
        )
        lastPlan = plan

        require(plan.route != AppForgeAgentAiRoute.BLOCKED) {
            "AI provider routing BLOCKED: ${plan.reasons.joinToString(" | ")}"
        }

        val providerPrompt = AppForgeAgentIntelligenceEngine.buildProviderPrompt(
            basePrompt = promptContract,
            plan = plan
        )

        return when (plan.route) {
            AppForgeAgentAiRoute.LOCAL -> validateOutput(
                requireNotNull(localProvider) { "Local AI provider bağlı değil." }
                    .generateStructuredJson(providerPrompt)
            )

            AppForgeAgentAiRoute.CLOUD -> validateOutput(
                requireNotNull(cloudProvider) { "Cloud AI provider bağlı değil." }
                    .generateStructuredJson(providerPrompt)
            )

            AppForgeAgentAiRoute.HYBRID -> {
                val local = requireNotNull(localProvider) { "Hybrid için local provider gerekli." }
                val cloud = requireNotNull(cloudProvider) { "Hybrid için cloud provider gerekli." }

                runCatching {
                    validateOutput(local.generateStructuredJson(providerPrompt))
                }.getOrElse {
                    validateOutput(cloud.generateStructuredJson(providerPrompt))
                }
            }

            AppForgeAgentAiRoute.BLOCKED -> error("Unreachable blocked route")
        }
    }

    private fun validateOutput(raw: String): String {
        require(raw.length <= MAX_PROVIDER_OUTPUT_CHARS) {
            "AI blueprint yanıtı güvenli boyut sınırını aşıyor."
        }
        AppForgeAgentBlueprintJson.parse(raw)
        return raw
    }

    private companion object {
        const val MAX_PROVIDER_OUTPUT_CHARS = 32 * 1024
    }
}
