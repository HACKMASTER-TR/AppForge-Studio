package com.hackmaster.videoforge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class JobCheckpointStore(private val context: Context, inputKey: String, source: String, target: String) {
    val jobId: String = sha1("$inputKey|$source|$target|engine-v412").take(16)
    val dir = File(context.filesDir, "videoforge-jobs/$jobId").apply { mkdirs() }
    private val stateFile = File(dir, "state.json")

    data class Segment(val start: Double, val end: Double, val speaker: Int)
    data class SavedTurn(
        val start: Double,
        val end: Double,
        val speaker: Int,
        val sourceText: String,
        val sourceLanguage: String,
        val translatedText: String
    )

    data class State(
        val nextSegmentIndex: Int = 0,
        val segments: List<Segment> = emptyList(),
        val turns: List<SavedTurn> = emptyList()
    )

    fun load(): State? {
        if (!stateFile.isFile) return null
        return runCatching {
            val root = JSONObject(stateFile.readText())
            val segmentsJson = root.optJSONArray("segments") ?: JSONArray()
            val turnsJson = root.optJSONArray("turns") ?: JSONArray()
            val segments = buildList {
                for (i in 0 until segmentsJson.length()) {
                    val o = segmentsJson.getJSONObject(i)
                    add(Segment(o.getDouble("start"), o.getDouble("end"), o.getInt("speaker")))
                }
            }
            val turns = buildList {
                for (i in 0 until turnsJson.length()) {
                    val o = turnsJson.getJSONObject(i)
                    add(
                        SavedTurn(
                            start = o.getDouble("start"),
                            end = o.getDouble("end"),
                            speaker = o.getInt("speaker"),
                            sourceText = o.getString("sourceText"),
                            sourceLanguage = o.getString("sourceLanguage"),
                            translatedText = o.getString("translatedText")
                        )
                    )
                }
            }
            State(root.optInt("nextSegmentIndex", 0), segments, turns)
        }.getOrNull()
    }

    fun save(state: State) {
        val root = JSONObject().apply {
            put("nextSegmentIndex", state.nextSegmentIndex)
            put("segments", JSONArray().apply {
                state.segments.forEach { s ->
                    put(JSONObject().apply {
                        put("start", s.start)
                        put("end", s.end)
                        put("speaker", s.speaker)
                    })
                }
            })
            put("turns", JSONArray().apply {
                state.turns.forEach { t ->
                    put(JSONObject().apply {
                        put("start", t.start)
                        put("end", t.end)
                        put("speaker", t.speaker)
                        put("sourceText", t.sourceText)
                        put("sourceLanguage", t.sourceLanguage)
                        put("translatedText", t.translatedText)
                    })
                }
            })
        }
        val temp = File(dir, "state.json.tmp")
        temp.writeText(root.toString())
        if (stateFile.exists()) stateFile.delete()
        temp.renameTo(stateFile)
    }

    fun ttsFile(index: Int): File = File(dir, "tts-${index.toString().padStart(5, '0')}.wav")

    fun clear() = dir.deleteRecursively()

    companion object {
        private fun sha1(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
