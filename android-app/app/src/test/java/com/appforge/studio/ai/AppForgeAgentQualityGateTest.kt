package com.appforge.studio.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentQualityGateTest {
    @Test
    fun validBlueprintPassesQualityGate() {
        val report = AppForgeAgentQualityGate.assess(
            blueprint()
        )

        assertTrue(report.pass)
        assertTrue(report.score >= 80)
    }

    @Test
    fun invalidBlueprintIsBlocked() {
        val report = AppForgeAgentQualityGate.assess(
            blueprint().copy(
                startRoute = "/missing"
            )
        )

        assertTrue(!report.pass)
        assertTrue(
            report.findings.any {
                it.level == AppForgeAgentQualityLevel.ERROR
            }
        )
    }

    @Test
    fun repairBudgetNeverExceedsBlueprintOrGlobalLimit() {
        val blueprint = blueprint().copy(
            maxRepairAttempts = 2
        )

        assertEquals(
            2,
            AppForgeAgentQualityGate.boundedRepairBudget(
                blueprint,
                3
            )
        )
    }

    @Test
    fun webValidationTargetsDoNotClaimAndroidArtifact() {
        val targets = AppForgeAgentQualityGate.validationTargets(
            AppForgeAgentPlatform.WEB
        )

        assertTrue("apk" !in targets)
        assertTrue("web-build" in targets)
    }

    @Test
    fun androidValidationTargetsRequireSecurityAndArtifacts() {
        val targets = AppForgeAgentQualityGate.validationTargets(
            AppForgeAgentPlatform.ANDROID
        )

        assertTrue("security" in targets)
        assertTrue("apk" in targets)
        assertTrue("aab" in targets)
    }

    private fun blueprint() = AppForgeAgentBlueprint(
        appName = "Demo",
        prompt = "Demo uygulaması",
        platform = AppForgeAgentPlatform.ANDROID,
        startRoute = "/home",
        screens = listOf(
            AppForgeAgentScreenSpec(
                id = "home",
                title = "Ana Sayfa",
                route = "/home",
                purpose = "Başlangıç",
                components = listOf("Text")
            )
        ),
        maxRepairAttempts = 2
    )
}
