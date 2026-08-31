package com.appforge.studio.net

import android.content.Context
import com.appforge.studio.security.StudioDeviceIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteTemplate(
    val slug: String,
    val name: String,
    val description: String,
    val category: String,
    val configJson: String
)

class WorkspaceClient(
    context: Context,
    private val baseUrl: String,
    private val bearerToken: String
) {
    private val deviceId =
        StudioDeviceIdentity.value(
            context.applicationContext
        )
    fun listTemplates(): List<RemoteTemplate> {
        val json = get("/api/templates")
        val arr = json.optJSONArray("templates") ?: JSONArray()

        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    RemoteTemplate(
                        slug = o.optString("slug"),
                        name = o.optString("name"),
                        description = o.optString("description"),
                        category = o.optString("category"),
                        configJson = o.optJSONObject("config")?.toString() ?: "{}"
                    )
                )
            }
        }
    }

    fun saveLocalization(
        projectId: String,
        locale: String,
        strings: Map<String, String>
    ) {
        val body = JSONObject().apply {
            put(
                "strings",
                JSONObject().apply {
                    strings.forEach { (k, v) -> put(k, v) }
                }
            )
        }

        request(
            method = "PUT",
            path = "/api/projects/$projectId/localizations/$locale",
            body = body
        )
    }

    private fun get(path: String) =
        request("GET", path, null)

    private fun request(
        method: String,
        path: String,
        body: JSONObject?
    ): JSONObject {
        val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("X-AppForge-Device-ID", deviceId)
            setRequestProperty("Accept", "application/json")

            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        if (body != null) {
            conn.outputStream.use {
                it.write(body.toString().toByteArray())
            }
        }

        val text = if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }

        if (conn.responseCode !in 200..299) {
            throw IllegalStateException(
                runCatching {
                    JSONObject(text).optString("error", "İstek başarısız.")
                }.getOrDefault("İstek başarısız.")
            )
        }

        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
