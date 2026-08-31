package com.appforge.studio.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredAiMessage(
    val role: String,
    val text: String
)

object LocalAiChatStore {

    private const val PREFS =
        "appforge_local_ai_history"

    private const val KEY_MESSAGES =
        "messages_v1"

    private const val MAX_MESSAGES =
        20

    private const val MAX_TEXT_LENGTH =
        8_000

    fun load(
        context: Context
    ): List<StoredAiMessage> =
        runCatching {
            val raw =
                context
                    .getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                    )
                    .getString(
                        KEY_MESSAGES,
                        null
                    )
                    ?: return emptyList()

            val array =
                JSONArray(
                    raw
                )

            buildList {
                for (
                    index in
                    0 until array.length()
                ) {
                    val item =
                        array.optJSONObject(
                            index
                        )
                            ?: continue

                    val role =
                        item
                            .optString(
                                "role"
                            )
                            .trim()

                    val text =
                        item
                            .optString(
                                "text"
                            )
                            .take(
                                MAX_TEXT_LENGTH
                            )

                    if (
                        role in
                            setOf(
                                "user",
                                "assistant"
                            ) &&
                        text.isNotBlank()
                    ) {
                        add(
                            StoredAiMessage(
                                role =
                                    role,
                                text =
                                    text
                            )
                        )
                    }
                }
            }
                .takeLast(
                    MAX_MESSAGES
                )
        }
            .getOrDefault(
                emptyList()
            )

    fun save(
        context: Context,
        messages: List<StoredAiMessage>
    ) {
        val array =
            JSONArray()

        messages
            .filter {
                it.role in
                    setOf(
                        "user",
                        "assistant"
                    ) &&
                it.text
                    .isNotBlank()
            }
            .takeLast(
                MAX_MESSAGES
            )
            .forEach {
                item ->
                array.put(
                    JSONObject()
                        .put(
                            "role",
                            item.role
                        )
                        .put(
                            "text",
                            item.text
                                .take(
                                    MAX_TEXT_LENGTH
                                )
                        )
                )
            }

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_MESSAGES,
                array.toString()
            )
            .apply()
    }

    fun clear(
        context: Context
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(
                KEY_MESSAGES
            )
            .apply()
    }
}
