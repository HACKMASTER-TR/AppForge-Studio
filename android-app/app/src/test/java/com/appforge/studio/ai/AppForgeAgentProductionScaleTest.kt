package com.appforge.studio.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentProductionScaleTest {
    private fun autonomousRequest() = AppForgeAgentAutonomousRequest(
        userPrompt = "Basit görev uygulaması üret",
        preferredPlatform = AppForgeAgentPlatform.ANDROID,
        workspace = File(".")
    )

    private fun envelope(
        tenant: String = "tenant-a",
        job: String = "job-1",
        bytes: Long = 1024
    ) = AppForgeAgentProductionEnvelopeV10(
        tenantId = tenant,
        jobId = job,
        estimatedGeneratedBytes = bytes,
        autonomousRequest = autonomousRequest()
    )

    @Test
    fun capacityAvailableRunsNow() {
        val decision = AppForgeAgentScaleGuardrailsV10.evaluate(
            envelope(), AppForgeAgentLoadSnapshotV10(), AppForgeAgentQuotaUsageV10()
        )
        assertEquals(AppForgeAgentProductionDisposition.RUN_NOW, decision.disposition)
    }

    @Test
    fun tenantConcurrencyQueuesInsteadOfOverrunning() {
        val decision = AppForgeAgentScaleGuardrailsV10.evaluate(
            envelope(),
            AppForgeAgentLoadSnapshotV10(runningTenant = 4),
            AppForgeAgentQuotaUsageV10()
        )
        assertEquals(AppForgeAgentProductionDisposition.QUEUED, decision.disposition)
    }

    @Test
    fun dailyBuildQuotaBlocks() {
        val decision = AppForgeAgentScaleGuardrailsV10.evaluate(
            envelope(),
            AppForgeAgentLoadSnapshotV10(),
            AppForgeAgentQuotaUsageV10(dailyBuilds = 250)
        )
        assertEquals(AppForgeAgentProductionDisposition.BLOCKED, decision.disposition)
        assertEquals("DAILY_BUILD_QUOTA", decision.reason)
    }

    @Test
    fun oversizedJobBlocks() {
        val policy = AppForgeAgentScalePolicyV10(maxGeneratedBytesPerJob = 64 * 1024L)
        val decision = AppForgeAgentScaleGuardrailsV10.evaluate(
            envelope(bytes = 64 * 1024L + 1),
            AppForgeAgentLoadSnapshotV10(),
            AppForgeAgentQuotaUsageV10(),
            policy
        )
        assertEquals(AppForgeAgentProductionDisposition.BLOCKED, decision.disposition)
    }

    @Test
    fun tenantScopedCacheKeysCannotCollideAcrossTenants() {
        val digest = "a".repeat(64)
        val first = AppForgeAgentScaleGuardrailsV10.cacheKey("tenant-a", digest, "BUILD", "same")
        val second = AppForgeAgentScaleGuardrailsV10.cacheKey("tenant-b", digest, "BUILD", "same")
        assertNotEquals(first, second)
    }

    @Test
    fun telemetryRedactsSecrets() {
        val result = AppForgeAgentTelemetrySanitizerV10.sanitize(
            mapOf("message" to "token ghp_123456789012345678901234567890")
        )
        assertFalse(result.getValue("message").contains("ghp_"))
        assertTrue(result.getValue("message").contains("[REDACTED]"))
    }

    @Test
    fun boundedTelemetryDropsOldestEvent() {
        val sink = AppForgeAgentBoundedTelemetrySinkV10(maxEvents = 2)
        repeat(3) { index ->
            sink.emit(
                AppForgeAgentTelemetryEventV10(
                    kind = AppForgeAgentTelemetryKindV10.ADMISSION,
                    tenantScope = "t",
                    jobScope = "j$index"
                )
            )
        }
        assertEquals(listOf("j1", "j2"), sink.snapshot().map { it.jobScope })
    }

    @Test
    fun fairQueueRotatesTenants() {
        val queue = AppForgeAgentFairQueueV10()
        assertTrue(queue.offer(envelope("tenant-a", "a1")))
        assertTrue(queue.offer(envelope("tenant-a", "a2")))
        assertTrue(queue.offer(envelope("tenant-b", "b1")))
        assertEquals("a1", queue.poll()?.jobId)
        assertEquals("b1", queue.poll()?.jobId)
        assertEquals("a2", queue.poll()?.jobId)
        assertNull(queue.poll())
    }

    @Test
    fun fairQueueEnforcesPerTenantDepth() {
        val queue = AppForgeAgentFairQueueV10(
            AppForgeAgentScalePolicyV10(maxTenantQueueDepth = 1)
        )
        assertTrue(queue.offer(envelope("tenant-a", "a1")))
        assertFalse(queue.offer(envelope("tenant-a", "a2")))
    }

    @Test
    fun rawTenantIdIsNotUsedAsTelemetryScope() {
        val decision = AppForgeAgentScaleGuardrailsV10.evaluate(
            envelope(tenant = "customer-secret-name"),
            AppForgeAgentLoadSnapshotV10(),
            AppForgeAgentQuotaUsageV10()
        )
        assertNotEquals("customer-secret-name", decision.tenantScope)
        assertEquals(20, decision.tenantScope.length)
    }
}
