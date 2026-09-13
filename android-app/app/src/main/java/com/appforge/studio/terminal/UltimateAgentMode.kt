package com.appforge.studio.terminal

import android.content.Context
import java.io.File
import kotlinx.coroutines.delay

internal enum class UltimateAgentRepairDisposition {
    RETRY_SAFE,
    AI_PATCH_REQUIRED,
    MANUAL_REQUIRED
}

internal data class UltimateAgentRepairDecision(
    val disposition: UltimateAgentRepairDisposition,
    val reason: String
)

internal data class UltimateAgentModeConfig(
    val maxRepairAttempts: Int = 2
) {
    init {
        require(maxRepairAttempts in 0..3) {
            "Agent Mode güvenli tekrar sayısı 0..3 aralığında olmalı."
        }
    }
}

internal data class UltimateAgentProgress(
    val repairAttempt: Int,
    val maxRepairAttempts: Int,
    val message: String
)

internal data class UltimateAgentModeResult(
    val success: Boolean,
    val pipeline: ProjectPipelineRunResult,
    val repairAttempts: Int,
    val lastDecision: UltimateAgentRepairDecision? = null
)

internal object UltimateAgentRepairPolicy {
    private val transientInstallSignals =
        listOf(
            "timed out",
            "timeout",
            "temporarily unavailable",
            "temporary failure",
            "connection reset",
            "econnreset",
            "eai_again",
            "too many requests",
            "http 429",
            "network is unreachable",
            "socket hang up",
            "could not resolve host"
        )

    fun decide(
        pipeline: ProjectPipelineRunResult,
        repairAttempts: Int,
        config: UltimateAgentModeConfig
    ): UltimateAgentRepairDecision {
        if (pipeline.success) {
            return UltimateAgentRepairDecision(
                disposition = UltimateAgentRepairDisposition.MANUAL_REQUIRED,
                reason = "Pipeline başarılı; düzeltme gerekmiyor."
            )
        }

        val failed =
            pipeline.steps.lastOrNull {
                it.status == ProjectPipelineStatus.FAILED
            }
                ?: return UltimateAgentRepairDecision(
                    disposition = UltimateAgentRepairDisposition.MANUAL_REQUIRED,
                    reason = "Başarısız pipeline adımı güvenilir biçimde belirlenemedi."
                )

        if (repairAttempts >= config.maxRepairAttempts) {
            return UltimateAgentRepairDecision(
                disposition = UltimateAgentRepairDisposition.MANUAL_REQUIRED,
                reason = "Agent Mode güvenli tekrar sınırına ulaştı."
            )
        }

        return when (failed.phase) {
            ProjectPipelinePhase.INSTALL -> {
                val lower = failed.output.lowercase()
                if (transientInstallSignals.any { signal -> signal in lower }) {
                    UltimateAgentRepairDecision(
                        disposition = UltimateAgentRepairDisposition.RETRY_SAFE,
                        reason =
                            "Bağımlılık kurulumunda geçici ağ/servis hatası algılandı; aynı doğrulanmış pipeline sınırlı olarak yeniden denenebilir."
                    )
                } else {
                    UltimateAgentRepairDecision(
                        disposition =
                            UltimateAgentRepairDisposition.AI_PATCH_REQUIRED,
                        reason =
                            "Bağımlılık kurulumu kalıcı bir proje/versiyon uyuşmazlığı içeriyor olabilir; rastgele shell düzeltmesi uygulanmayacak."
                    )
                }
            }

            ProjectPipelinePhase.TEST,
            ProjectPipelinePhase.BUILD ->
                UltimateAgentRepairDecision(
                    disposition = UltimateAgentRepairDisposition.AI_PATCH_REQUIRED,
                    reason =
                        "Kaynak kodu veya proje yapılandırması değişikliği gerekebilir; maskelenmiş hata paketi AI incelemesine bırakıldı."
                )

            ProjectPipelinePhase.HEALTH,
            ProjectPipelinePhase.TOOLCHAINS,
            ProjectPipelinePhase.DEPLOY_GATE ->
                UltimateAgentRepairDecision(
                    disposition = UltimateAgentRepairDisposition.MANUAL_REQUIRED,
                    reason =
                        "Bu aşama otomatik kod/shell düzeltmesine uygun değil; kullanıcı kontrolü gerekli."
                )
        }
    }
}

internal class UltimateAgentModeEngine(
    context: Context
) {
    private val pipelineEngine =
        UltimateProjectPipelineEngine(context.applicationContext)

    suspend fun run(
        workspace: File,
        distribution: LinuxDistribution,
        plan: UltimateProjectAutomationPlan,
        selectedToolchains: Collection<LinuxToolchainId>,
        confirmed: Boolean,
        config: UltimateAgentModeConfig = UltimateAgentModeConfig(),
        onProgress: (UltimateAgentProgress) -> Unit = {}
    ): UltimateAgentModeResult {
        require(confirmed) {
            "Agent Mode açık kullanıcı onayı olmadan başlatılamaz."
        }

        UltimateAiHandoffStore.clear()
        var repairAttempts = 0

        while (true) {
            onProgress(
                UltimateAgentProgress(
                    repairAttempt = repairAttempts,
                    maxRepairAttempts = config.maxRepairAttempts,
                    message =
                        if (repairAttempts == 0) {
                            "Agent Mode: sağlık → araçlar → install → test → build zinciri çalışıyor."
                        } else {
                            "Agent Mode: güvenli tekrar $repairAttempts/${config.maxRepairAttempts} çalışıyor."
                        }
                )
            )

            val pipeline =
                pipelineEngine.run(
                    workspace = workspace,
                    distribution = distribution,
                    plan = plan,
                    selectedToolchains = selectedToolchains,
                    confirmed = true
                )

            if (pipeline.success) {
                UltimateAiHandoffStore.clear()
                onProgress(
                    UltimateAgentProgress(
                        repairAttempt = repairAttempts,
                        maxRepairAttempts = config.maxRepairAttempts,
                        message =
                            "Agent Mode build zincirini tamamladı. Deploy otomatik başlatılmayacak."
                    )
                )
                return UltimateAgentModeResult(
                    success = true,
                    pipeline = pipeline,
                    repairAttempts = repairAttempts,
                    lastDecision = null
                )
            }

            val decision =
                UltimateAgentRepairPolicy.decide(
                    pipeline = pipeline,
                    repairAttempts = repairAttempts,
                    config = config
                )

            if (
                decision.disposition !=
                UltimateAgentRepairDisposition.RETRY_SAFE
            ) {
                onProgress(
                    UltimateAgentProgress(
                        repairAttempt = repairAttempts,
                        maxRepairAttempts = config.maxRepairAttempts,
                        message = decision.reason
                    )
                )
                return UltimateAgentModeResult(
                    success = false,
                    pipeline = pipeline,
                    repairAttempts = repairAttempts,
                    lastDecision = decision
                )
            }

            repairAttempts += 1
            onProgress(
                UltimateAgentProgress(
                    repairAttempt = repairAttempts,
                    maxRepairAttempts = config.maxRepairAttempts,
                    message =
                        "${decision.reason} Tekrar: $repairAttempts/${config.maxRepairAttempts}."
                )
            )
            delay(1_000L)
        }
    }
}
