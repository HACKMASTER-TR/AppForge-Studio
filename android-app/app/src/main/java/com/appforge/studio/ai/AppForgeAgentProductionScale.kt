package com.appforge.studio.ai

import java.security.MessageDigest
import java.util.ArrayDeque

internal enum class AppForgeAgentProductionDisposition {
    RUN_NOW,
    QUEUED,
    BLOCKED
}

internal data class AppForgeAgentScalePolicyV10(
    val maxGlobalQueueDepth: Int = 2_000,
    val maxTenantQueueDepth: Int = 64,
    val maxConcurrentGlobal: Int = 128,
    val maxConcurrentPerTenant: Int = 4,
    val maxDailyBuildsPerTenant: Int = 250,
    val maxDailyAiRequestsPerTenant: Int = 2_000,
    val maxGeneratedBytesPerJob: Long = 64L * 1024 * 1024
) {
    init {
        require(maxGlobalQueueDepth in 1..100_000)
        require(maxTenantQueueDepth in 1..10_000)
        require(maxConcurrentGlobal in 1..4_096)
        require(maxConcurrentPerTenant in 1..256)
        require(maxConcurrentPerTenant <= maxConcurrentGlobal)
        require(maxDailyBuildsPerTenant in 1..1_000_000)
        require(maxDailyAiRequestsPerTenant in 1..10_000_000)
        require(maxGeneratedBytesPerJob in 64 * 1024L..2L * 1024 * 1024 * 1024)
    }
}

internal data class AppForgeAgentQuotaUsageV10(
    val dailyBuilds: Int = 0,
    val dailyAiRequests: Int = 0
) {
    init {
        require(dailyBuilds >= 0)
        require(dailyAiRequests >= 0)
    }
}

internal data class AppForgeAgentLoadSnapshotV10(
    val globalQueueDepth: Int = 0,
    val tenantQueueDepth: Int = 0,
    val runningGlobal: Int = 0,
    val runningTenant: Int = 0
) {
    init {
        require(globalQueueDepth >= 0)
        require(tenantQueueDepth >= 0)
        require(runningGlobal >= 0)
        require(runningTenant >= 0)
    }
}

internal data class AppForgeAgentProductionEnvelopeV10(
    val tenantId: String,
    val jobId: String,
    val estimatedGeneratedBytes: Long,
    val autonomousRequest: AppForgeAgentAutonomousRequest,
    val intelligencePlan: AppForgeAgentIntelligencePlan? = null
) {
    init {
        require(AppForgeAgentScaleGuardrailsV10.isValidScopeId(tenantId)) {
            "tenantId güvenli scope biçiminde olmalı."
        }
        require(AppForgeAgentScaleGuardrailsV10.isValidScopeId(jobId)) {
            "jobId güvenli scope biçiminde olmalı."
        }
        require(estimatedGeneratedBytes >= 0) { "Tahmini çıktı boyutu negatif olamaz." }
    }
}

internal data class AppForgeAgentAdmissionDecisionV10(
    val disposition: AppForgeAgentProductionDisposition,
    val reason: String,
    val tenantScope: String,
    val jobScope: String,
    val manualReviewRequired: Boolean
) {
    val accepted: Boolean
        get() = disposition != AppForgeAgentProductionDisposition.BLOCKED
}

internal object AppForgeAgentScaleGuardrailsV10 {
    private val scopePattern = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,95}$")

    fun isValidScopeId(value: String): Boolean = scopePattern.matches(value.trim())

    fun evaluate(
        envelope: AppForgeAgentProductionEnvelopeV10,
        load: AppForgeAgentLoadSnapshotV10,
        usage: AppForgeAgentQuotaUsageV10,
        policy: AppForgeAgentScalePolicyV10 = AppForgeAgentScalePolicyV10()
    ): AppForgeAgentAdmissionDecisionV10 {
        val tenantScope = scopeHash(envelope.tenantId)
        val jobScope = scopeHash(envelope.jobId)
        val manualReview = envelope.intelligencePlan?.manualReviewRequired == true ||
            envelope.intelligencePlan?.risk?.level in setOf(
                AppForgeAgentRiskLevel.HIGH,
                AppForgeAgentRiskLevel.CRITICAL
            )

        fun blocked(reason: String) = AppForgeAgentAdmissionDecisionV10(
            disposition = AppForgeAgentProductionDisposition.BLOCKED,
            reason = reason,
            tenantScope = tenantScope,
            jobScope = jobScope,
            manualReviewRequired = manualReview
        )

        if (envelope.estimatedGeneratedBytes > policy.maxGeneratedBytesPerJob) {
            return blocked("JOB_OUTPUT_QUOTA")
        }
        if (usage.dailyBuilds >= policy.maxDailyBuildsPerTenant) {
            return blocked("DAILY_BUILD_QUOTA")
        }
        if (usage.dailyAiRequests >= policy.maxDailyAiRequestsPerTenant) {
            return blocked("DAILY_AI_QUOTA")
        }
        if (load.globalQueueDepth >= policy.maxGlobalQueueDepth) {
            return blocked("GLOBAL_QUEUE_FULL")
        }
        if (load.tenantQueueDepth >= policy.maxTenantQueueDepth) {
            return blocked("TENANT_QUEUE_FULL")
        }

        val queueNeeded = load.runningGlobal >= policy.maxConcurrentGlobal ||
            load.runningTenant >= policy.maxConcurrentPerTenant

        return AppForgeAgentAdmissionDecisionV10(
            disposition = if (queueNeeded) {
                AppForgeAgentProductionDisposition.QUEUED
            } else {
                AppForgeAgentProductionDisposition.RUN_NOW
            },
            reason = if (queueNeeded) "CAPACITY_QUEUE" else "CAPACITY_AVAILABLE",
            tenantScope = tenantScope,
            jobScope = jobScope,
            manualReviewRequired = manualReview
        )
    }

    fun cacheKey(
        tenantId: String,
        projectDigestSha256: String,
        stage: String,
        inputFingerprint: String
    ): String {
        require(isValidScopeId(tenantId)) { "Geçersiz tenantId." }
        require(projectDigestSha256.matches(Regex("^[a-fA-F0-9]{32,128}$"))) {
            "Project digest hex olmalı."
        }
        require(stage.matches(Regex("^[A-Z_]{2,32}$"))) { "Cache stage geçersiz." }
        require(inputFingerprint.length in 1..512) { "Fingerprint boyutu geçersiz." }

        val payload = listOf(
            "v10",
            scopeHash(tenantId),
            projectDigestSha256.lowercase(),
            stage,
            inputFingerprint
        ).joinToString("|")

        return "v10:${sha256(payload)}"
    }

    fun scopeHash(raw: String): String = sha256(raw.trim()).take(20)

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal class AppForgeAgentFairQueueV10(
    private val policy: AppForgeAgentScalePolicyV10 = AppForgeAgentScalePolicyV10()
) {
    private val queues = linkedMapOf<String, ArrayDeque<AppForgeAgentProductionEnvelopeV10>>()
    private val rotation = ArrayDeque<String>()
    private var totalDepth = 0

    @Synchronized
    fun offer(envelope: AppForgeAgentProductionEnvelopeV10): Boolean {
        if (totalDepth >= policy.maxGlobalQueueDepth) return false
        val queue = queues.getOrPut(envelope.tenantId) { ArrayDeque() }
        if (queue.size >= policy.maxTenantQueueDepth) return false

        val wasEmpty = queue.isEmpty()
        queue.addLast(envelope)
        totalDepth += 1
        if (wasEmpty) rotation.addLast(envelope.tenantId)
        return true
    }

    @Synchronized
    fun poll(): AppForgeAgentProductionEnvelopeV10? {
        while (rotation.isNotEmpty()) {
            val tenant = rotation.removeFirst()
            val queue = queues[tenant] ?: continue
            if (queue.isEmpty()) {
                queues.remove(tenant)
                continue
            }
            val item = queue.removeFirst()
            totalDepth -= 1
            if (queue.isNotEmpty()) {
                rotation.addLast(tenant)
            } else {
                queues.remove(tenant)
            }
            return item
        }
        return null
    }

    @Synchronized
    fun depth(): Int = totalDepth

    @Synchronized
    fun tenantDepth(tenantId: String): Int = queues[tenantId]?.size ?: 0
}
