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
                .open(ASSET_NAME)
                .bufferedReader()
                .use { reader ->
                    JSONObject(
                        reader.readText()
                    )
                }
        }.getOrNull()

    fun summary(
        context: Context
    ): String? {
        val data =
            snapshot(context)
                ?: return null

        return buildString {
            append("v")
            append(
                data.optString(
                    "version",
                    "?"
                )
            )
            append(" | risk=")
            append(
                data.optString(
                    "risk",
                    "UNKNOWN"
                )
            )
            append(" ")
            append(
                data.optInt(
                    "riskScore",
                    -1
                )
            )
            append("/100")
            append(" | security=")
            append(
                data.optString(
                    "security",
                    "UNKNOWN"
                )
            )
            append(" | release=")
            append(
                data.optString(
                    "release",
                    "UNKNOWN"
                )
            )
            append(" | API=")
            append(
                data.optInt(
                    "apiRoutes",
                    0
                )
            )
            append(" | DB=")
            append(
                data.optInt(
                    "databaseTables",
                    0
                )
            )
            append(" | tests=")
            append(
                data.optInt(
                    "tests",
                    0
                )
            )
        }
    }
}
