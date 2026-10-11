package com.appforge.studio.ai

import android.content.Context
import org.json.JSONObject

object SecondBrainBridge {

    private const val ASSET_NAME =
        "second_brain_snapshot.json"

    private fun snapshot(
        context: Context
    ): JSONObject? =
        runCatching {
            context.assets
                .open(
                    ASSET_NAME
                )
                .bufferedReader()
                .use {
                    reader ->

                    JSONObject(
                        reader.readText()
                    )
                }
                .takeIf {
                    it.optInt(
                        "schemaVersion",
                        -1
                    ) == 2
                }
        }.getOrNull()

    fun summary(
        context: Context
    ): String? {
        val data =
            snapshot(
                context
            )
                ?: return null

        val basis =
            data
                .optString(
                    "sourceBasisSha256",
                    "?"
                )
                .take(
                    12
                )

        return buildString {
            append(
                "SecondBrain V"
            )

            append(
                data.optInt(
                    "schemaVersion",
                    -1
                )
            )

            append(
                " | authority="
            )

            append(
                data.optString(
                    "authority",
                    "UNKNOWN"
                )
            )

            append(
                " | basis="
            )

            append(
                basis
            )

            append(
                " | build="
            )

            append(
                data.optString(
                    "buildArchitecture",
                    "UNKNOWN"
                )
            )

            append(
                " | publisher="
            )

            append(
                data.optString(
                    "publisherAuthorization",
                    "UNKNOWN"
                )
            )

            append(
                " | d1="
            )

            append(
                data.optString(
                    "d1Ledger",
                    "UNKNOWN"
                )
            )

            append(
                " | release="
            )

            append(
                data.optString(
                    "releaseGate",
                    "UNKNOWN"
                )
            )

            append(
                " | live="
            )

            append(
                data.optString(
                    "liveState",
                    "UNKNOWN"
                )
            )
        }
    }
}
