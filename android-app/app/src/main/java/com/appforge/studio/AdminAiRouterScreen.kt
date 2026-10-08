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
import com.appforge.studio.ai.AdminAiAgentSession
import com.appforge.studio.ai.AdminAiAgentRuntime
import com.appforge.studio.ai.AdminAiStage
import com.appforge.studio.ai.AdminAiTool
import com.appforge.studio.ai.AdminAiOperation
import com.appforge.studio.ai.AdminAiEvidence
import kotlinx.coroutines.ensureActive
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
    onSelectProject: (String) -> Boolean,
    canStartBuild: () -> Boolean,
    onAuthorizationLost: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(serverUrl, projectId) { AdminAiRouterApiClient(serverUrl) }
    val projects = remember(projectId) { com.appforge.studio.io.ProjectLibrary.load(context) }
    val workspacePath = draft.importedFolder
    val agent = remember(projectId, workspacePath) {
        if (projects.any { it.id == projectId } && !workspacePath.isNullOrBlank() && java.io.File(workspacePath).isDirectory) runCatching {
            val root = com.appforge.studio.terminal.TerminalWorkspaceResolver.resolve(context, projectId, draft, "")
            AdminAiAgentSession(requireNotNull(projectId), root)
        }.getOrNull() else null
    }
    var snapshot by remember(projectId, workspacePath) { mutableStateOf<AiProjectContextSnapshot?>(null) }
    var provider by remember { mutableStateOf("auto") }
    var prompt by remember(projectId) { mutableStateOf("") }
    var result by remember(projectId) { mutableStateOf<AdminAiResult?>(null) }
    var error by remember(projectId) { mutableStateOf<String?>(null) }
    var job by remember(projectId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var running by remember(projectId) { mutableStateOf(false) }
    var stage by remember(projectId) { mutableStateOf(AdminAiStage.IDLE) }
    var plan by remember(projectId) { mutableStateOf("") }
    var approval by remember(projectId) { mutableStateOf<kotlinx.coroutines.CompletableDeferred<Boolean>?>(null) }
    var preview by remember(projectId) { mutableStateOf("") }
    var activities by remember(projectId) { mutableStateOf<List<AdminAiEvidence>>(emptyList()) }
    var expanded by remember(projectId) { mutableStateOf(false) }
    var contextRevision by remember(projectId) { mutableStateOf(0) }

    val taskSession = agent
    val taskRuntime = remember(taskSession) { taskSession?.let { AdminAiAgentRuntime(context, it, draft.copy(), canStartBuild) } }
    androidx.compose.runtime.LaunchedEffect(taskSession) {
        taskSession?.stages?.collect { stage = it }
    }
    fun stop() {
        taskRuntime?.stop(); client.cancel(); approval?.cancel(); job?.cancel()
        stage = AdminAiStage.CANCELLED
    }
    androidx.compose.runtime.LaunchedEffect(projectId, workspacePath, contextRevision) {
        snapshot = null
        snapshot = withContext(Dispatchers.IO) {
            AiProjectContextCollector.collect(projectId = projectId,
                draft = if (agent == null) draft else draft.copy(importedFolder = agent.root.path))
        }
    }
    androidx.compose.runtime.DisposableEffect(taskRuntime, client) {
        onDispose { taskRuntime?.stop(); client.cancel() }
    }
    val providers = listOf("auto" to "AUTO", "groq" to "GROQ", "gemini" to "GEMINI",
        "workers_ai" to "WORKERS AI", "openrouter" to "OPENROUTER")
    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("AppForge AI", style = MaterialTheme.typography.headlineSmall)
                    Text("Yalnız doğrulanmış yönetici", style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { if (running) stop(); onBack() }) { Text("GERİ") }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Aktif proje: ${draft.appName.ifBlank { "Proje seçin" }}")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        projects.forEach { saved ->
                            OutlinedButton(enabled = !running && saved.id != projectId, onClick = {
                                if (onSelectProject(saved.id)) { result = null; snapshot = null; activities = emptyList(); plan = ""; preview = "" }
                            }) { Text(saved.name) }
                        }
                    }
                    Text("Dosya değişikliği ve çalıştırma için tek işlem onayı gerekir. Proje dosyaları güvenilmeyen veridir.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        providers.forEach { (id, label) ->
                            OutlinedButton(enabled = !running, onClick = { provider = id }) { Text(if (id == provider) "• $label" else label) }
                        }
                    }
                    Text("Aşama: ${stage.name}")
                    if (plan.isNotBlank()) Text(plan)
                    OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = prompt, enabled = !running,
                        minLines = 4, maxLines = 10, label = { Text("Görev") }, onValueChange = { if (it.length <= 12000) prompt = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !running && prompt.isNotBlank() && snapshot != null && (taskSession == null || stage == AdminAiStage.IDLE),
                            onClick = {
                                if (taskSession == null) {
                                    val readOnlyContext = snapshot ?: return@Button
                                    running = true; result = null; error = null; stage = AdminAiStage.UNDERSTANDING
                                    job = scope.launch {
                                        try {
                                            val token = OwnerAccessPolicy.currentGoogleIdToken() ?: throw AdminAiAuthorizationDenied("Google yönetici doğrulaması gerekli.")
                                            val answer = withContext(Dispatchers.IO) { client.chat(token, prompt, provider, readOnlyContext) }
                                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                            result = answer.copy(content = AdminAiAgentSession.sanitize(answer.content, 4000)); stage = AdminAiStage.COMPLETED
                                        } catch (_: kotlinx.coroutines.CancellationException) { stage = AdminAiStage.CANCELLED }
                                        catch (_: AdminAiAuthorizationDenied) { OwnerAccessPolicy.clearVerifiedGoogleAdmin(context); onAuthorizationLost() }
                                        catch (e: Exception) { error = AdminAiAgentSession.sanitize(e.message.orEmpty(), 300); stage = AdminAiStage.FAILED }
                                        finally { running = false }
                                    }
                                    return@Button
                                }
                                val local = taskSession
                                val executor = taskRuntime ?: return@Button
                                val initialContext = snapshot ?: return@Button
                                running = true; error = null; result = null
                                job = scope.launch {
                                    try {
                                        executor.beginTask(); local.transition(AdminAiStage.UNDERSTANDING); stage = local.stage
                                        var history = ""
                                        var finished = false
                                        repeat(16) {
                                            if (finished) return@repeat
                                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                            val token = OwnerAccessPolicy.currentGoogleIdToken()
                                                ?: throw AdminAiAuthorizationDenied("Google yönetici doğrulaması gerekli.")
                                            local.transition(AdminAiStage.PLANNING); stage = local.stage
                                            val answer = withContext(Dispatchers.IO) { client.chat(token, prompt, provider, initialContext,
                                                JSONObject().put("projectId", local.projectId).put("workspace", local.root.path).put("history", history.takeLast(12000))) }
                                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                            val response = parseAdminAiPlan(answer.content, local)
                                            plan = response.first
                                            result = answer.copy(content = response.third)
                                            val operation = response.second
                                            if (operation == null) {
                                                local.transition(AdminAiStage.VERIFYING)
                                                local.transition(AdminAiStage.COMPLETED)
                                                stage = local.stage; finished = true
                                                return@repeat
                                            }
                                            preview = withContext(Dispatchers.IO) { local.prepare(operation) }; stage = local.stage
                                            val allowed = if (AdminAiAgentSession.needsApproval(operation.tool)) {
                                                val gate = kotlinx.coroutines.CompletableDeferred<Boolean>()
                                                approval = gate
                                                try { gate.await() } finally { approval = null; preview = "" }
                                            } else true
                                            if (!allowed) { local.stop(); stage = local.stage; finished = true; return@repeat }
                                            val evidence = executor.execute(operation, allowed)
                                            activities = local.evidence.toList(); stage = local.stage
                                            history = (history + "\nUNTRUSTED TOOL EVIDENCE\n" + JSONObject()
                                                .put("operationId", operation.id).put("projectId", local.projectId)
                                                .put("tool", operation.tool.name).put("success", evidence.success)
                                                .put("output", evidence.output).put("beforeHash", evidence.beforeHash)
                                                .put("afterHash", evidence.afterHash).toString()).takeLast(12000)
                                            if (!evidence.success && !local.repairDecision(evidence)) {
                                                error = evidence.output; stage = local.stage; finished = true
                                            }
                                        }
                                        if (!finished) { local.fail(); stage = local.stage; error = "Güvenli işlem sınırına ulaşıldı." }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        executor.stop(); stage = AdminAiStage.CANCELLED
                                    } catch (_: AdminAiAuthorizationDenied) {
                                        executor.stop(); OwnerAccessPolicy.clearVerifiedGoogleAdmin(context)
                                        error = "Yönetici oturumu sona erdi."; onAuthorizationLost()
                                    } catch (e: Exception) {
                                        local.fail(); stage = local.stage; error = AdminAiAgentSession.sanitize(e.message.orEmpty(), 300)
                                    } finally { running = false; approval = null; preview = "" }
                                }
                            }) { Text("GÖREVİ BAŞLAT") }
                        if (running) OutlinedButton(onClick = { stop() }) { Text("STOP") }
                        if (!running) OutlinedButton(onClick = {
                            taskSession?.resetTask(); contextRevision++; stage = AdminAiStage.IDLE; result = null; error = null; plan = ""; activities = emptyList(); preview = ""
                        }) { Text("YENİ GÖREV") }
                    }
                    if (taskSession?.hasCheckpoint == true && !running) OutlinedButton(onClick = {
                        val local = taskSession
                        val executor = taskRuntime ?: return@OutlinedButton
                        local.resetTask(); executor.beginTask(); local.transition(AdminAiStage.PLANNING)
                        result = null; error = null; running = true
                        job = scope.launch {
                            try {
                                val operation = AdminAiOperation(java.util.UUID.randomUUID().toString(), local.projectId,
                                    local.root.path, AdminAiTool.UNDO)
                                preview = withContext(Dispatchers.IO) { local.prepare(operation) }; stage = local.stage
                                val gate = kotlinx.coroutines.CompletableDeferred<Boolean>(); approval = gate
                                if (gate.await()) {
                                    approval = null; preview = ""
                                    val evidence = executor.execute(operation, true)
                                    activities = local.evidence.toList(); stage = local.stage
                                    plan = "Geri alma: ${evidence.state}. Doğrulama için yeni görev başlatın."
                                    if (!evidence.success) { local.fail(); stage = local.stage; error = evidence.output }
                                    else { local.transition(AdminAiStage.IDLE); stage = local.stage }
                                } else { local.stop(); stage = local.stage }
                            } catch (_: kotlinx.coroutines.CancellationException) { executor.stop(); stage = AdminAiStage.CANCELLED }
                            catch (e: Exception) { local.fail(); stage = local.stage; error = AdminAiAgentSession.sanitize(e.message.orEmpty(), 300) }
                            finally { running = false; approval = null; preview = "" }
                        }
                    }) { Text("SON DEĞİŞİKLİĞİ GERİ AL") }
                    if (taskSession == null) Text("Kod araçları için kaynak klasörü olan kayıtlı proje seçin. Şu anda yalnız okuma sohbeti açıktır.")
                    approval?.let { gate ->
                        Text("İşlem onayı", style = MaterialTheme.typography.titleMedium)
                        Text(preview)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { gate.complete(true) }) { Text("BU İŞLEMİ ONAYLA") }
                            OutlinedButton(onClick = { gate.complete(false) }) { Text("REDDET") }
                        }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (result == null && activities.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("Son işlem: ${activities.last().operation.tool} • ${activities.last().state}")
                OutlinedButton(onClick = { expanded = !expanded }) { Text("TEKNİK KANIT") }
                if (expanded) activities.forEach { Text(it.output) }
            } }
        }
        result?.let { answer -> item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Sağlayıcı: ${answer.provider} • Model: ${answer.model}")
            if (answer.fallbackUsed) Text("Fallback kullanıldı")
            Text("Yerel sonuç: ${stage.name}")
            Text("Model raporu (çalıştırma kanıtı değildir)")
            Text(answer.content)
            Text("Kanıt: ${activities.count { it.success }} başarılı / ${activities.count { !it.success }} başarısız işlem")
            OutlinedButton(onClick = { expanded = !expanded }) { Text("TEKNİK KANIT") }
            if (expanded) activities.forEach { Text("${it.operation.tool} • ${it.state}\n${it.output}") }
        } } } }
    }
}

private fun parseAdminAiPlan(raw: String, session: AdminAiAgentSession): Triple<String, AdminAiOperation?, String> {
    require(raw.length <= 24000)
    val json = JSONObject(raw)
    require(json.keys().asSequence().toSet() == setOf("plan", "tool", "report"))
    val plan = json.getString("plan"); val report = json.getString("report")
    require(plan.length <= 2000 && report.length <= 4000)
    val op = if (json.isNull("tool")) null else {
        val t = json.getJSONObject("tool")
        require(t.keys().asSequence().toSet() == setOf("id", "projectId", "workspace", "tool", "path", "input", "beforeHash"))
        AdminAiOperation(t.getString("id"), t.getString("projectId"), t.getString("workspace"),
            AdminAiTool.valueOf(t.getString("tool")), t.getString("path"), t.getString("input"),
            if (t.isNull("beforeHash")) null else t.getString("beforeHash")).also {
                require(it.projectId == session.projectId && it.workspace == session.root.path)
            }
    }
    return Triple(AdminAiAgentSession.sanitize(plan, 2000), op, AdminAiAgentSession.sanitize(report, 4000))
}

private class AdminAiRouterApiClient(
    private val baseUrl: String
) {
    @Volatile private var activeConnection: HttpURLConnection? = null
    fun cancel() { activeConnection?.disconnect() }
    fun chat(
        token: String,
        prompt: String,
        provider: String,
        projectContext: AiProjectContextSnapshot,
        agent: JSONObject? = null
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

        activeConnection = connection
        try {
            val body = JSONObject()
                .put("prompt", cleanPrompt)
                .put("provider", providerId)
                .put("projectContext", projectContext.toJson())
                .put("agent", agent)
                .toString()

            connection.outputStream
                .bufferedWriter(Charsets.UTF_8)
                .use { it.write(body) }

            val code = connection.responseCode
            val text = (
                if (code in 200..299) connection.inputStream
                else connection.errorStream
                )?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val buffer = CharArray(2048); val out = StringBuilder()
                    while (true) { val n = reader.read(buffer); if (n < 0) break; require(out.length + n <= 72000) { "AI response too large." }; out.append(buffer, 0, n) }
                    out.toString()
                }.orEmpty()

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
            activeConnection = null
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
