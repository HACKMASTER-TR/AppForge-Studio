package com.appforge.studio.io

import android.content.Context
import org.json.JSONObject
import java.io.File

data class StudioPreferences(
    val languageCode: String = "system"
)

object AppSettingsStore {
    private fun file(context: Context) =
        File(context.filesDir, "studio_preferences.json")

    fun load(context: Context): StudioPreferences {
        val f = file(context)
        if (!f.exists()) {
            return StudioPreferences()
        }

        return runCatching {
            val o = JSONObject(f.readText())
            StudioPreferences(
                languageCode = o.optString("languageCode", "system")
            )
        }.getOrDefault(StudioPreferences())
    }

    fun save(context: Context, prefs: StudioPreferences) {
        val o = JSONObject().apply {
            put("languageCode", prefs.languageCode)
        }
        file(context).writeText(o.toString(2))
    }

    fun updateLanguage(context: Context, languageCode: String): StudioPreferences {
        val prefs = load(context).copy(languageCode = languageCode)
        save(context, prefs)
        return prefs
    }
}
