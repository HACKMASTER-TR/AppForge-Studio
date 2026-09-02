package com.appforge.studio.tools.excel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class ExcelHistoryItem(
    val id: String,
    val name: String,
    val uri: String,
    val createdAt: Long,
    val trashed: Boolean = false
)

internal object ExcelToolsHistoryStore {
    private const val PREFS =
        "appforge_excel_tools_history"

    private const val KEY_ITEMS =
        "items"

    fun load(
        context: Context
    ): List<ExcelHistoryItem> {
        val raw =
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_ITEMS,
                    "[]"
                )
                .orEmpty()

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (
                    i in 0 until array.length()
                ) {
                    val item =
                        array.getJSONObject(i)

                    add(
                        ExcelHistoryItem(
                            id =
                                item.optString("id"),
                            name =
                                item.optString("name"),
                            uri =
                                item.optString("uri"),
                            createdAt =
                                item.optLong("createdAt"),
                            trashed =
                                item.optBoolean(
                                    "trashed",
                                    false
                                )
                        )
                    )
                }
            }
        }.getOrDefault(
            emptyList()
        ).sortedByDescending {
            it.createdAt
        }
    }

    fun add(
        context: Context,
        item: ExcelHistoryItem
    ) {
        val items =
            load(context)
                .filterNot {
                    it.id ==
                        item.id
                }
                .toMutableList()

        items.add(
            0,
            item
        )

        save(
            context,
            items.take(100)
        )
    }

    fun setTrashed(
        context: Context,
        id: String,
        trashed: Boolean
    ) {
        save(
            context,
            load(context).map {
                if (
                    it.id ==
                    id
                ) {
                    it.copy(
                        trashed =
                            trashed
                    )
                } else {
                    it
                }
            }
        )
    }

    fun remove(
        context: Context,
        id: String
    ) {
        save(
            context,
            load(context)
                .filterNot {
                    it.id ==
                        id
                }
        )
    }

    private fun save(
        context: Context,
        items: List<ExcelHistoryItem>
    ) {
        val array =
            JSONArray()

        items.forEach {
            item ->

            array.put(
                JSONObject().apply {
                    put(
                        "id",
                        item.id
                    )
                    put(
                        "name",
                        item.name
                    )
                    put(
                        "uri",
                        item.uri
                    )
                    put(
                        "createdAt",
                        item.createdAt
                    )
                    put(
                        "trashed",
                        item.trashed
                    )
                }
            )
        }

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_ITEMS,
                array.toString()
            )
            .apply()
    }
}
