package com.appforge.studio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.ai.AiProjectContextCollector
import com.appforge.studio.ai.AiProjectContextSnapshot
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.security.OwnerAccessPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class AdminAiResult(
    val provider: String,
    val model: String,
    val content: String,
    val fallbackUsed: Boolean,
    val projectContextUsed: Boolean,
    val contextFileCount: Int
)

private class AdminAiAuthorizationDenied(
    message: String
) : IllegalStateException(message)

@Composable
fun AdminAiRouterScreen(
    serverUrl: String,
    projectId: String?,
    draft: ProjectDraft,
    onAuthorizationLost: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(serverUrl) { AdminAiRouterApiClient(serverUrl) }

    var provider by remember { mutableStateOf("auto") }
    var prompt by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AdminAiResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    val providers = listOf(
        "auto" to "AUTO",
        "groq" to "GROQ",
        "gemini" to "GEMINI",
        "workers_ai" to "WORKERS AI",
        "openrouter" to "OPENROUTER"
    )

    val activeProjectLabel = draft.appName
        .ifBlank { projectId?.takeIf { it.isNotBlank() } ?: "Aktif proje yok" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("AppForge AI", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Yalnız doğrulanmış yönetici",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = onBack) { Text("GERİ") }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Project-aware AI Router",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Aktif proje: $activeProjectLabel",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Bu ilk güvenli aşamada yalnız sınırlı ve secret filtreli read-only proje bağlamı gönderilir. Dosya düzenleme, terminal ve build tool çağrısı henüz açık değildir.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "AUTO modunda AppForge göreve göre sağlayıcı seçer ve gerektiğinde diğer sağlayıcılara geçer.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        providers.forEach { choice ->
                            val id = choice.first
                            val label = choice.second
                            if (provider == id) {
                                Button(onClick = { provider = id }) { Text(label) }
                            } else {
                                OutlinedButton(onClick = { provider = id }) { Text(label) }
                            }
                        }
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = prompt,
                        enabled = !sending,
                        minLines = 5,
                        maxLines = 12,
                        label = { Text("Yönetici istemi") },
                        onValueChange = { if (it.length <= 12000) prompt = it }
                    )

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sending && prompt.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val token = OwnerAccessPolicy.currentGoogleIdToken()
                                if (token == null) {
                                    result = null
                                    error = "Google yönetici doğrulaması gerekli."
                                    onAuthorizationLost()
                                    return@launch
                                }

                                sending = true
                                result = null
                                error = null

                                try {
                                    val projectContext = withContext(Dispatchers.IO) {
                                        AiProjectContextCollector.collect(
                                            projectId = projectId,
                                            draft = draft
                                        )
                                    }

                                    result = withContext(Dispatchers.IO) {
                                        client.chat(
                                            token = token,
                                            prompt = prompt,
                                            provider = provider,
                                            projectContext = projectContext
                                        )
                                    }
                                } catch (_: AdminAiAuthorizationDenied) {
                                    OwnerAccessPolicy.clearVerifiedGoogleAdmin(context)
                                    error = "Yönetici oturumu sona erdi."
                                    onAuthorizationLost()
                                } catch (e: Exception) {
                                    error = e.message?.take(300)
                                        ?: "AppForge AI isteği başarısız."
                                } finally {
                                    sending = false
                                }
                            }
                        }
                    ) {
                        Text(
                            if (sending) "PROJE BAĞLAMI HAZIRLANIYOR..."
                            else "APPFORGE AI'YA SOR"
                        )
                    }
                }
            }
        }

        if (!error.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        error.orEmpty(),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        result?.let { answer ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Yanıt", style = MaterialTheme.typography.titleMedium)
                        Text("Sağlayıcı: ${answer.provider}")
                        Text(
                            "Model: ${answer.model}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (answer.fallbackUsed) {
                            Text(
                                "Fallback kullanıldı",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            if (answer.projectContextUsed) {
                                "Proje bağlamı: ${answer.contextFileCount} kaynak dosyası + proje ağacı"
                            } else {
                                "Proje bağlamı: aktif kaynak klasörü bulunamadı"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(answer.content)
                    }
                }
            }
        }
    }
}

private class AdminAiRouterApiClient(
    private val baseUrl: String
) {
    fun chat(
        token: String,
        prompt: String,
        provider: String,
        projectContext: AiProjectContextSnapshot
    ): AdminAiResult {
        require(token.length in 50..12000) {
            "Google yönetici doğrulaması gerekli."
        }

        val cleanPrompt = prompt.trim()
        require(cleanPrompt.length in 1..12000) { "AI istemi geçersiz." }

        val providerId = provider.trim().lowercase()
        require(
            providerId in setOf(
                "auto", "groq", "gemini", "workers_ai", "openrouter"
            )
        ) { "AI sağlayıcısı geçersiz." }

        val connection = (
            URL(controlPlaneBaseUrl() + "/api/admin/ai/chat")
                .openConnection() as HttpURLConnection
            ).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $token")
            }

        try {
            val body = JSONObject()
                .put("prompt", cleanPrompt)
                .put("provider", providerId)
                .put("projectContext", projectContext.toJson())
                .toString()

            connection.outputStream
                .bufferedWriter(Charsets.UTF_8)
                .use { it.write(body) }

            val code = connection.responseCode
            val text = (
                if (code in 200..299) connection.inputStream
                else connection.errorStream
                )?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val errorCode = runCatching {
                    JSONObject(text).optString("error")
                }.getOrDefault("").ifBlank { "ai_request_failed" }

                if (code == 401 || code == 403) {
                    throw AdminAiAuthorizationDenied("HTTP $code • $errorCode")
                }

                throw IllegalStateException(
                    when (errorCode) {
                        "ai_providers_unavailable" ->
                            "AI sağlayıcılarının hiçbiri şu anda yanıt vermiyor."
                        "ai_provider_not_configured" ->
                            "Seçilen AI sağlayıcısı sunucuda yapılandırılmamış."
                        "invalid_ai_prompt" -> "AI istemi geçersiz."
                        "invalid_project_context" ->
                            "Proje bağlamı güvenlik doğrulamasından geçmedi."
                        "ai_request_too_large" ->
                            "Proje bağlamı AI isteği için fazla büyük."
                        else -> "AppForge AI kullanılamadı • $errorCode"
                    }
                )
            }

            val response = JSONObject(text)
            check(response.optBoolean("ok")) {
                "AppForge AI yanıtı doğrulanamadı."
            }

            val content = response.optString("content").trim()
            check(content.isNotBlank()) { "AppForge AI boş yanıt döndürdü." }

            return AdminAiResult(
                provider = response.optString("provider").ifBlank { "unknown" },
                model = response.optString("model").ifBlank { "unknown" },
                content = content,
                fallbackUsed = response.optBoolean("fallbackUsed"),
                projectContextUsed = response.optBoolean("projectContextUsed"),
                contextFileCount = response.optInt("contextFileCount", 0)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun controlPlaneBaseUrl(): String {
        val value = baseUrl.trim().trimEnd('/')
        require(
            value.startsWith("https://", true) ||
                value.startsWith("http://10.0.2.2", true)
        ) { "AppForge AI üretimde HTTPS Control Plane gerektirir." }
        return value
    }
}

private fun AiProjectContextSnapshot.toJson(): JSONObject {
    val fileArray = JSONArray()
    files.forEach { file ->
        fileArray.put(
            JSONObject()
                .put("path", file.path)
                .put("content", file.content)
                .put("truncated", file.truncated)
        )
    }

    return JSONObject()
        .put("projectId", projectId)
        .put("projectName", projectName)
        .put("packageName", packageName)
        .put("sourceTechnology", sourceTechnology)
        .put("sourceBuildEngine", sourceBuildEngine)
        .put("workspaceName", workspaceName)
        .put("tree", tree)
        .put("files", fileArray)
        .put("scannedFileCount", scannedFileCount)
        .put("truncated", truncated)
}
