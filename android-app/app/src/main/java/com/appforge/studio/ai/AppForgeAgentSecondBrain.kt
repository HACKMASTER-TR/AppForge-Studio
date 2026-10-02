package com.appforge.studio.ai

internal data class AppForgeAgentSecondBrainSnapshot(
    val schemaVersion: Int,
    val project: String,
    val authority: String,
    val sourceBasisSha256: String,
    val sourceBasisFileCount: Int,
    val buildArchitecture: String,
    val publisherAuthorization: String,
    val d1Ledger: String,
    val d1Migrations: Int,
    val wikiPages: Int,
    val qualityContractFiles: Int,
    val androidUnitTestFiles: Int,
    val releaseGate: String,
    val productionPublisherEndpoint: String,
    val liveState: String
) {
    fun toContextFacts():
        List<AppForgeAgentContextFact> =
        listOf(
            AppForgeAgentContextFact(
                "secondBrain.schemaVersion",
                schemaVersion.toString(),
                "secondbrain",
                100
            ),
            AppForgeAgentContextFact(
                "secondBrain.authority",
                authority,
                "secondbrain",
                100
            ),
            AppForgeAgentContextFact(
                "secondBrain.sourceBasisSha256",
                sourceBasisSha256,
                "secondbrain",
                95
            ),
            AppForgeAgentContextFact(
                "architecture.build",
                buildArchitecture,
                "secondbrain",
                100
            ),
            AppForgeAgentContextFact(
                "security.publisherAuthorization",
                publisherAuthorization,
                "secondbrain",
                100
            ),
            AppForgeAgentContextFact(
                "database.d1Ledger",
                d1Ledger,
                "secondbrain",
                100
            ),
            AppForgeAgentContextFact(
                "database.d1Migrations",
                d1Migrations.toString(),
                "secondbrain",
                95
            ),
            AppForgeAgentContextFact(
                "knowledge.wikiPages",
                wikiPages.toString(),
                "secondbrain",
                80
            ),
            AppForgeAgentContextFact(
                "quality.contractFiles",
                qualityContractFiles.toString(),
                "secondbrain",
                85
            ),
            AppForgeAgentContextFact(
                "quality.androidUnitTestFiles",
                androidUnitTestFiles.toString(),
                "secondbrain",
                85
            ),
            AppForgeAgentContextFact(
                "release.gate",
                releaseGate,
                "secondbrain",
                100
            ),
            AppForgeAgentContextFact(
                "release.productionPublisherEndpoint",
                productionPublisherEndpoint,
                "secondbrain",
                100
            ),
            AppForgeAgentContextFact(
                "live.snapshotBoundary",
                liveState,
                "secondbrain",
                100
            )
        )
}

internal object AppForgeAgentSecondBrainSnapshotParser {

    private const val MAX_JSON_CHARS =
        64 * 1024

    fun parse(
        raw: String
    ): AppForgeAgentSecondBrainSnapshot {
        require(
            raw.length <=
                MAX_JSON_CHARS
        ) {
            "SecondBrain snapshot çok büyük."
        }

        require(
            raw.trim()
                .startsWith(
                    "{"
                ) &&
                raw.trim()
                    .endsWith(
                        "}"
                    )
        ) {
            "SecondBrain snapshot JSON nesnesi olmalı."
        }

        fun string(
            key: String
        ): String {
            val pattern =
                Regex(
                    "\\\"${Regex.escape(key)}\\\"" +
                        "\\s*:\\s*\\\"([^\\\"]*)\\\""
                )

            return pattern
                .find(
                    raw
                )
                ?.groupValues
                ?.get(
                    1
                )
                ?: throw IllegalArgumentException(
                    "SecondBrain alanı eksik: $key"
                )
        }

        fun int(
            key: String
        ): Int {
            val pattern =
                Regex(
                    "\\\"${Regex.escape(key)}\\\"" +
                        "\\s*:\\s*(-?\\d+)"
                )

            return pattern
                .find(
                    raw
                )
                ?.groupValues
                ?.get(
                    1
                )
                ?.toIntOrNull()
                ?: throw IllegalArgumentException(
                    "SecondBrain sayısal alanı eksik: $key"
                )
        }

        val schemaVersion =
            int(
                "schemaVersion"
            )

        require(
            schemaVersion ==
                2
        ) {
            "SecondBrain schema V2 gerekli."
        }

        val sourceBasisSha256 =
            string(
                "sourceBasisSha256"
            )
                .lowercase()

        require(
            sourceBasisSha256
                .matches(
                    Regex(
                        "^[0-9a-f]{64}$"
                    )
                )
        ) {
            "SecondBrain source basis hash geçersiz."
        }

        fun count(
            key: String
        ): Int =
            int(
                key
            )
                .also {
                    require(
                        it >=
                            0
                    ) {
                        "SecondBrain sayacı negatif olamaz: $key"
                    }
                }

        return AppForgeAgentSecondBrainSnapshot(
            schemaVersion =
                schemaVersion,

            project =
                string(
                    "project"
                )
                    .take(
                        80
                    ),

            authority =
                string(
                    "authority"
                )
                    .take(
                        60
                    ),

            sourceBasisSha256 =
                sourceBasisSha256,

            sourceBasisFileCount =
                count(
                    "sourceBasisFileCount"
                ),

            buildArchitecture =
                string(
                    "buildArchitecture"
                )
                    .take(
                        60
                    ),

            publisherAuthorization =
                string(
                    "publisherAuthorization"
                )
                    .take(
                        80
                    ),

            d1Ledger =
                string(
                    "d1Ledger"
                )
                    .take(
                        80
                    ),

            d1Migrations =
                count(
                    "d1Migrations"
                ),

            wikiPages =
                count(
                    "wikiPages"
                ),

            qualityContractFiles =
                count(
                    "qualityContractFiles"
                ),

            androidUnitTestFiles =
                count(
                    "androidUnitTestFiles"
                ),

            releaseGate =
                string(
                    "releaseGate"
                )
                    .take(
                        60
                    ),

            productionPublisherEndpoint =
                string(
                    "productionPublisherEndpoint"
                )
                    .take(
                        60
                    ),

            liveState =
                string(
                    "liveState"
                )
                    .take(
                        60
                    )
        )
    }
}

internal fun interface AppForgeAgentContextSourceV9 {
    fun load():
        List<AppForgeAgentContextFact>
}

internal class AppForgeAgentSecondBrainContextSource(
    private val snapshot:
        AppForgeAgentSecondBrainSnapshot,
    private val extraFacts:
        List<AppForgeAgentContextFact> =
        emptyList()
) : AppForgeAgentContextSourceV9 {

    override fun load():
        List<AppForgeAgentContextFact> =
        snapshot.toContextFacts() +
            extraFacts
}
