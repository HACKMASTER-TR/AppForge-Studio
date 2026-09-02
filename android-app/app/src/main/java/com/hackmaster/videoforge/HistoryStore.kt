package com.hackmaster.videoforge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val timestamp: Long,
    val sourceLabel: String,
    val inputUri: String,
    val outputUri: String,
    val subtitleUri: String?,
    val targetLanguage: String,
    val preview: Boolean,
    val turns: Int,
    val speakers: Int
)

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("videoforge_history", Context.MODE_PRIVATE)

    fun add(entry: HistoryEntry) {
        val all = read().toMutableList()
        all.add(0, entry)
        val trimmed = all.take(30)
        val array = JSONArray()
        trimmed.forEach { item ->
            array.put(JSONObject().apply {
                put("timestamp", item.timestamp)
                put("sourceLabel", item.sourceLabel)
                put("inputUri", item.inputUri)
                put("outputUri", item.outputUri)
                put("subtitleUri", item.subtitleUri ?: JSONObject.NULL)
                put("targetLanguage", item.targetLanguage)
                put("preview", item.preview)
                put("turns", item.turns)
                put("speakers", item.speakers)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    fun read(): List<HistoryEntry> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        HistoryEntry(
                            timestamp = o.optLong("timestamp"),
                            sourceLabel = o.optString("sourceLabel", "Video"),
                            inputUri = o.optString("inputUri"),
                            outputUri = o.optString("outputUri"),
                            subtitleUri = if (o.isNull("subtitleUri")) null else o.optString("subtitleUri"),
                            targetLanguage = o.optString("targetLanguage", "tr"),
                            preview = o.optBoolean("preview", false),
                            turns = o.optInt("turns", 0),
                            speakers = o.optInt("speakers", 0)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun remove(
        entry: HistoryEntry
    ) {
        val remaining =
            read().filterNot {
                it == entry
            }

        val array =
            JSONArray()

        remaining.forEach { item ->
            array.put(
                JSONObject().apply {
                    put(
                        "timestamp",
                        item.timestamp
                    )
                    put(
                        "sourceLabel",
                        item.sourceLabel
                    )
                    put(
                        "inputUri",
                        item.inputUri
                    )
                    put(
                        "outputUri",
                        item.outputUri
                    )
                    put(
                        "subtitleUri",
                        item.subtitleUri
                            ?: JSONObject.NULL
                    )
                    put(
                        "targetLanguage",
                        item.targetLanguage
                    )
                    put(
                        "preview",
                        item.preview
                    )
                    put(
                        "turns",
                        item.turns
                    )
                    put(
                        "speakers",
                        item.speakers
                    )
                }
            )
        }

        prefs
            .edit()
            .putString(
                "items",
                array.toString()
            )
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
