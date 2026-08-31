package com.appforge.studio.io

import android.content.Context
import android.net.Uri
import org.json.JSONObject

data class FirebaseConfigInspection(
    val projectId: String,
    val packageNames: Set<String>,
    val packageMatches: Boolean
)

object FirebaseConfigInspector {
    fun inspect(context: Context, uri: Uri, expectedPackage: String): FirebaseConfigInspection {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length + count <= 2 * 1024 * 1024) {
                    "Firebase dosyası beklenenden büyük."
                }
                result.append(buffer, 0, count)
            }
            result.toString()
        } ?: error("Firebase dosyası okunamadı.")

        val json = JSONObject(raw)
        val projectId = json.optJSONObject("project_info")?.optString("project_id").orEmpty()
        require(projectId.isNotBlank()) { "Geçerli project_info.project_id bulunamadı." }

        val packageNames = buildSet {
            val clients = json.optJSONArray("client") ?: return@buildSet
            for (index in 0 until clients.length()) {
                clients.optJSONObject(index)
                    ?.optJSONObject("client_info")
                    ?.optJSONObject("android_client_info")
                    ?.optString("package_name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }
        require(packageNames.isNotEmpty()) { "Firebase Android package kaydı bulunamadı." }

        return FirebaseConfigInspection(
            projectId = projectId,
            packageNames = packageNames,
            packageMatches = expectedPackage in packageNames
        )
    }
}
