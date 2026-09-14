package com.appforge.studio.ai

import android.content.Context
import com.appforge.studio.build.BuildApiClient

internal class AppForgeAgentReleaseReviewClient(
    context: Context,
    buildServiceUrl: String,
    buildApiKey: String
) {
    private val client =
        BuildApiClient(
            context = context.applicationContext,
            baseUrl =
                buildServiceUrl.trim().ifBlank {
                    "https://api.appforgecloud.com"
                },
            apiKey = buildApiKey
        )

    fun load(
        remote: AppForgeAgentRemoteBuildInfo,
        blueprint: AppForgeAgentBlueprint,
        artifacts: AppForgeAgentArtifactState
    ): AppForgeAgentReleaseReviewState {
        val packageName =
            AppForgeAgentBuildProjectPreparer.safeApplicationId(
                blueprint.appName
            )

        val historyResult = runCatching {
            client.history()
                .filter {
                    it.packageName == packageName
                }
                .sortedByDescending {
                    it.createdAt
                }
                .take(MAX_HISTORY)
                .map {
                    AppForgeAgentBuildHistoryItem(
                        buildId = it.buildId,
                        buildNo = it.buildNo,
                        status =
                            AppForgeAgentArtifactSafety.sanitize(
                                it.status,
                                80
                            ),
                        createdAt = it.createdAt
                    )
                }
        }

        val history =
            historyResult.getOrDefault(
                emptyList()
            )

        val previous =
            history.firstOrNull {
                it.buildId != remote.buildId &&
                    it.status.equals(
                        "success",
                        ignoreCase = true
                    )
            }

        val comparisonResult =
            previous?.let { previousBuild ->
                runCatching {
                    val compare =
                        client.compareBuilds(
                            leftBuildId =
                                previousBuild.buildId,
                            rightBuildId =
                                remote.buildId
                        )

                    AppForgeAgentBuildComparison(
                        previousBuildId =
                            previousBuild.buildId,
                        currentBuildId =
                            remote.buildId,
                        apkDeltaBytes =
                            compare.apkDeltaBytes,
                        aabDeltaBytes =
                            compare.aabDeltaBytes,
                        changeCount =
                            compare.changeCount,
                        changes =
                            compare.changes
                                .take(MAX_CHANGES)
                                .map {
                                    AppForgeAgentArtifactSafety.sanitize(
                                        it,
                                        1_200
                                    )
                                }
                    )
                }
            }

        val releaseNotesResult =
            runCatching {
                client.releaseNotes(
                    remote.buildId
                )
                    .take(MAX_RELEASE_NOTES)
                    .map {
                        AppForgeAgentArtifactSafety.sanitize(
                            it,
                            1_000
                        )
                    }
            }

        val readiness =
            AppForgeAgentReleaseReadinessEvaluator.evaluate(
                remote = remote,
                artifacts = artifacts
            )

        val message = buildString {
            if (historyResult.isSuccess) {
                append(
                    "${history.size} ilgili build geçmişi bulundu."
                )
            } else {
                append(
                    "Build geçmişi alınamadı."
                )
            }

            if (previous != null) {
                append(
                    if (
                        comparisonResult?.isSuccess == true
                    ) {
                        " Önceki SUCCESS build ile karşılaştırma hazır."
                    } else {
                        " Önceki build bulundu fakat karşılaştırma alınamadı."
                    }
                )
            }

            append(
                if (readiness.ready) {
                    " Teknik release kontrolü PASS."
                } else {
                    " Teknik release kontrolünde blocker var."
                }
            )
        }

        return AppForgeAgentReleaseReviewState(
            busy = false,
            message = message,
            history = history,
            comparison =
                comparisonResult?.getOrNull(),
            releaseNotes =
                releaseNotesResult.getOrDefault(
                    emptyList()
                ),
            readiness = readiness
        )
    }

    private companion object {
        const val MAX_HISTORY = 10
        const val MAX_CHANGES = 40
        const val MAX_RELEASE_NOTES = 30
    }
}
