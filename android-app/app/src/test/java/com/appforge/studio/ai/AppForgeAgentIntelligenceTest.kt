package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppForgeAgentIntelligenceTest {
    private val validBlueprintJson = """
        {
          "schemaVersion":1,
          "appName":"TaskFlow",
          "prompt":"Görev uygulaması",
          "platform":"ANDROID",
          "startRoute":"/home",
          "screens":[{
            "id":"home",
            "title":"Ana Sayfa",
            "route":"/home",
            "purpose":"Görevleri gösterir",
            "components":["TaskList"],
            "actions":[]
          }],
          "maxRepairAttempts":2
        }
    """.trimIndent()

    @Test
    fun secondBrainSnapshotParsesKnownSchema() {
        val snapshot = AppForgeAgentSecondBrainSnapshotParser.parse(
            """{"schemaVersion":2,"project":"AppForge Studio","authority":"REPOSITORY_DERIVED","sourceBasisSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sourceBasisFileCount":578,"buildArchitecture":"DEVICE_LOCAL","publisherAuthorization":"STAGING_DEVICE_ACCEPTED","d1Ledger":"RECONCILED_0001_0005","d1Migrations":5,"wikiPages":39,"qualityContractFiles":224,"androidUnitTestFiles":52,"releaseGate":"REVIEW_REQUIRED","productionPublisherEndpoint":"DISABLED","liveState":"NOT_LIVE_QUERY"}"""
        )

        assertEquals(2, snapshot.schemaVersion)
        assertEquals("REPOSITORY_DERIVED", snapshot.authority)
        assertEquals("DEVICE_LOCAL", snapshot.buildArchitecture)
        assertEquals(
            "STAGING_DEVICE_ACCEPTED",
            snapshot.publisherAuthorization
        )
        assertEquals(
            "RECONCILED_0001_0005",
            snapshot.d1Ledger
        )
        assertEquals(5, snapshot.d1Migrations)
        assertEquals(
            "NOT_LIVE_QUERY",
            snapshot.liveState
        )
    }

    @Test
    fun sensitiveContextNeverEntersPacket() {
        val packet = AppForgeAgentIntelligenceEngine.buildContextPacket(
            listOf(
                AppForgeAgentContextFact("safe", "ok"),
                AppForgeAgentContextFact("secret", "ghp_123456789012345678901234", sensitive = true)
            )
        )
        assertTrue(packet.contains("safe"))
        assertFalse(packet.contains("ghp_"))
        assertFalse(packet.contains("secret:"))
    }

    @Test
    fun secretLikeValuesAreRedactedEvenWhenFactIsNotMarkedSensitive() {
        val packet = AppForgeAgentIntelligenceEngine.buildContextPacket(
            listOf(AppForgeAgentContextFact("token", "sk-abcdefghijklmnopqrstuvwxyz123456"))
        )
        assertTrue(packet.contains("[REDACTED]"))
        assertFalse(packet.contains("sk-abcdefghijklmnopqrstuvwxyz123456"))
    }

    @Test
    fun contextBudgetIsBounded() {
        val facts = (1..80).map {
            AppForgeAgentContextFact("fact$it", "x".repeat(400), priority = it.coerceAtMost(100))
        }
        val packet = AppForgeAgentIntelligenceEngine.buildContextPacket(
            facts,
            AppForgeAgentIntelligencePolicy(maxContextChars = 2_048, maxFacts = 64)
        )
        assertTrue(packet.length <= 2_048)
    }

    @Test
    fun architectureAwarenessDetectsFullStackAreas() {
        val areas = AppForgeAgentIntelligenceEngine.inferArchitecture(
            "OAuth login, PostgreSQL database, REST API, worker queue and production deploy"
        )
        assertTrue(AppForgeAgentArchitectureArea.AUTH in areas)
        assertTrue(AppForgeAgentArchitectureArea.DATABASE in areas)
        assertTrue(AppForgeAgentArchitectureArea.API in areas)
        assertTrue(AppForgeAgentArchitectureArea.WORKER in areas)
        assertTrue(AppForgeAgentArchitectureArea.DEPLOYMENT in areas)
    }

    @Test
    fun criticalSecretRiskNeverRoutesToCloud() {
        val plan = AppForgeAgentIntelligenceEngine.plan(
            prompt = "Use ghp_123456789012345678901234 for production deploy",
            facts = emptyList(),
            localAvailable = true,
            cloudAvailable = true
        )
        assertEquals(AppForgeAgentRiskLevel.CRITICAL, plan.risk.level)
        assertEquals(AppForgeAgentAiRoute.LOCAL, plan.route)
        assertTrue(plan.manualReviewRequired)
    }

    @Test
    fun missingProvidersBlocksGeneration() {
        val plan = AppForgeAgentIntelligenceEngine.plan(
            prompt = "Create a simple notes app",
            facts = emptyList(),
            localAvailable = false,
            cloudAvailable = false
        )
        assertEquals(AppForgeAgentAiRoute.BLOCKED, plan.route)
    }

    @Test
    fun cloudOnlyProviderCanBeSelectedForLowRiskPrompt() {
        val plan = AppForgeAgentIntelligenceEngine.plan(
            prompt = "Create a simple calculator screen",
            facts = emptyList(),
            localAvailable = false,
            cloudAvailable = true
        )
        assertEquals(AppForgeAgentAiRoute.CLOUD, plan.route)
    }

    @Test
    fun hybridUsesLocalFirstAndDoesNotCallCloudOnValidOutput() {
        var cloudCalls = 0
        val provider = AppForgeAgentIntelligentBlueprintProvider(
            localProvider = AppForgeAgentRawAiProviderV9 { validBlueprintJson },
            cloudProvider = AppForgeAgentRawAiProviderV9 {
                cloudCalls += 1
                validBlueprintJson
            }
        )
        val output = provider.createBlueprint("Build API backed notes app")
        assertEquals(validBlueprintJson, output)
        assertEquals(0, cloudCalls)
        assertEquals(AppForgeAgentAiRoute.HYBRID, provider.lastPlan?.route)
    }

    @Test
    fun hybridFallsBackToCloudWhenLocalOutputIsInvalid() {
        var cloudCalls = 0
        val provider = AppForgeAgentIntelligentBlueprintProvider(
            localProvider = AppForgeAgentRawAiProviderV9 { "not-json" },
            cloudProvider = AppForgeAgentRawAiProviderV9 {
                cloudCalls += 1
                validBlueprintJson
            }
        )
        val output = provider.createBlueprint("Build database backed notes app")
        assertEquals(validBlueprintJson, output)
        assertEquals(1, cloudCalls)
    }

    @Test
    fun cloudCanBeDisabledByPolicy() {
        val plan = AppForgeAgentIntelligenceEngine.plan(
            prompt = "Create a simple notes app",
            facts = emptyList(),
            localAvailable = false,
            cloudAvailable = true,
            policy = AppForgeAgentIntelligencePolicy(allowCloud = false)
        )
        assertEquals(AppForgeAgentAiRoute.BLOCKED, plan.route)
    }
}
