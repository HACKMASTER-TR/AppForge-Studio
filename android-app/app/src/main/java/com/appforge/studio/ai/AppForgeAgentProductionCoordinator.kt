package com.appforge.studio.ai

internal enum class AppForgeAgentProductionStatusV10 {
    SUCCESS,
    QUEUED,
    BLOCKED
}

internal data class AppForgeAgentProductionResultV10(
    val status: AppForgeAgentProductionStatusV10,
    val admission: AppForgeAgentAdmissionDecisionV10,
    val autonomousResult: AppForgeAgentAutonomousResult? = null
)

internal class AppForgeAgentProductionCoordinatorV10(
    private val pipeline: AppForgeAgentAutonomousPipeline,
    private val policy: AppForgeAgentScalePolicyV10 = AppForgeAgentScalePolicyV10(),
    private val telemetry: AppForgeAgentTelemetrySinkV10 = AppForgeAgentTelemetrySinkV10 { }
) {
    fun run(
        envelope: AppForgeAgentProductionEnvelopeV10,
        load: AppForgeAgentLoadSnapshotV10,
        usage: AppForgeAgentQuotaUsageV10
    ): AppForgeAgentProductionResultV10 {
        val admission = AppForgeAgentScaleGuardrailsV10.evaluate(
            envelope = envelope,
            load = load,
            usage = usage,
            policy = policy
        )

        emit(admission, AppForgeAgentTelemetryKindV10.ADMISSION, mapOf("reason" to admission.reason))

        when (admission.disposition) {
            AppForgeAgentProductionDisposition.BLOCKED -> {
                emit(admission, AppForgeAgentTelemetryKindV10.BLOCKED, mapOf("reason" to admission.reason))
                return AppForgeAgentProductionResultV10(
                    status = AppForgeAgentProductionStatusV10.BLOCKED,
                    admission = admission
                )
            }

            AppForgeAgentProductionDisposition.QUEUED -> {
                emit(admission, AppForgeAgentTelemetryKindV10.QUEUED, mapOf("reason" to admission.reason))
                return AppForgeAgentProductionResultV10(
                    status = AppForgeAgentProductionStatusV10.QUEUED,
                    admission = admission
                )
            }

            AppForgeAgentProductionDisposition.RUN_NOW -> Unit
        }

        emit(admission, AppForgeAgentTelemetryKindV10.STARTED)
        val result = pipeline.run(envelope.autonomousRequest)
        val success = result.status == AppForgeAgentAutonomousStatus.SUCCESS

        emit(
            admission,
            if (success) AppForgeAgentTelemetryKindV10.FINISHED else AppForgeAgentTelemetryKindV10.BLOCKED,
            mapOf(
                "agentStatus" to result.status.name,
                "deployGate" to result.deployGate.name,
                "manualReview" to admission.manualReviewRequired.toString()
            )
        )

        return AppForgeAgentProductionResultV10(
            status = if (success) {
                AppForgeAgentProductionStatusV10.SUCCESS
            } else {
                AppForgeAgentProductionStatusV10.BLOCKED
            },
            admission = admission,
            autonomousResult = result
        )
    }

    private fun emit(
        admission: AppForgeAgentAdmissionDecisionV10,
        kind: AppForgeAgentTelemetryKindV10,
        attributes: Map<String, String> = emptyMap()
    ) {
        telemetry.emit(
            AppForgeAgentTelemetryEventV10(
                kind = kind,
                tenantScope = admission.tenantScope,
                jobScope = admission.jobScope,
                attributes = attributes
            )
        )
    }
}
