package com.appforge.studio.ai

internal data class AppForgeAgentSecondBrainSnapshot(
    val version: String,
    val head: String,
    val branch: String,
    val risk: String,
    val riskScore: Int,
    val security: String,
    val apiRoutes: Int,
    val databaseTables: Int,
    val migrations: Int,
    val tests: Int,
    val release: String,
    val liveGithub: String,
    val liveRailway: String
) {
    fun toContextFacts(): List<AppForgeAgentContextFact> = listOf(
        AppForgeAgentContextFact("secondBrain.version", version, "secondbrain", 100),
        AppForgeAgentContextFact("secondBrain.head", head, "secondbrain", 95),
        AppForgeAgentContextFact("secondBrain.branch", branch, "secondbrain", 95),
        AppForgeAgentContextFact("secondBrain.risk", "$risk ($riskScore/100)", "secondbrain", 100),
        AppForgeAgentContextFact("secondBrain.security", security, "secondbrain", 100),
        AppForgeAgentContextFact("architecture.apiRoutes", apiRoutes.toString(), "secondbrain", 80),
        AppForgeAgentContextFact("architecture.databaseTables", databaseTables.toString(), "secondbrain", 80),
        AppForgeAgentContextFact("architecture.migrations", migrations.toString(), "secondbrain", 80),
        AppForgeAgentContextFact("quality.tests", tests.toString(), "secondbrain", 85),
        AppForgeAgentContextFact("release.gate", release, "secondbrain", 100),
        AppForgeAgentContextFact("live.github", liveGithub, "secondbrain", 70),
        AppForgeAgentContextFact("live.railway", liveRailway, "secondbrain", 70)
    )
}

internal object AppForgeAgentSecondBrainSnapshotParser {
    private const val MAX_JSON_CHARS = 64 * 1024

    fun parse(raw: String): AppForgeAgentSecondBrainSnapshot {
        require(raw.length <= MAX_JSON_CHARS) { "SecondBrain snapshot çok büyük." }
        require(raw.trim().startsWith('{') && raw.trim().endsWith('}')) {
            "SecondBrain snapshot JSON nesnesi olmalı."
        }

        fun string(key: String): String {
            val pattern = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
            return pattern.find(raw)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("SecondBrain alanı eksik: $key")
        }

        fun int(key: String): Int {
            val pattern = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(-?\\d+)")
            return pattern.find(raw)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw IllegalArgumentException("SecondBrain sayısal alanı eksik: $key")
        }

        val riskScore = int("riskScore")
        require(riskScore in 0..100) { "SecondBrain riskScore 0..100 olmalı." }

        return AppForgeAgentSecondBrainSnapshot(
            version = string("version").take(32),
            head = string("head").take(64),
            branch = string("branch").take(120),
            risk = string("risk").take(40),
            riskScore = riskScore,
            security = string("security").take(40),
            apiRoutes = int("apiRoutes").coerceAtLeast(0),
            databaseTables = int("databaseTables").coerceAtLeast(0),
            migrations = int("migrations").coerceAtLeast(0),
            tests = int("tests").coerceAtLeast(0),
            release = string("release").take(60),
            liveGithub = string("liveGithub").take(60),
            liveRailway = string("liveRailway").take(60)
        )
    }
}

internal fun interface AppForgeAgentContextSourceV9 {
    fun load(): List<AppForgeAgentContextFact>
}

internal class AppForgeAgentSecondBrainContextSource(
    private val snapshot: AppForgeAgentSecondBrainSnapshot,
    private val extraFacts: List<AppForgeAgentContextFact> = emptyList()
) : AppForgeAgentContextSourceV9 {
    override fun load(): List<AppForgeAgentContextFact> =
        snapshot.toContextFacts() + extraFacts
}
