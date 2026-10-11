package com.appforge.studio.ai

import com.appforge.studio.terminal.TerminalCommandPolicy
import com.appforge.studio.terminal.TerminalTextSanitizer
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

internal enum class AdminAiStage {
    IDLE, UNDERSTANDING, SEARCHING, INSPECTING, PLANNING, WAITING_APPROVAL,
    EDITING, TESTING, BUILDING, OBSERVING, REPAIRING, VERIFYING, COMPLETED, FAILED, CANCELLED
}
internal enum class AdminAiTool { TREE, READ_FILE, SEARCH, METADATA, GIT_STATUS, GIT_DIFF,
    EDIT, CREATE, DELETE, UNDO, TERMINAL, LINT, TEST, TYPECHECK, BUILD, BUILD_RESULT }
internal data class AdminAiOperation(
    val id: String, val projectId: String, val workspace: String, val tool: AdminAiTool,
    val path: String = "", val input: String = "", val beforeHash: String? = null
)
internal data class AdminAiArtifactProof(val kind: String, val bytes: Long, val sha256: String)
internal data class AdminAiBuildProof(val projectId: String, val buildId: String, val buildNo: Long?,
    val status: String, val artifacts: List<AdminAiArtifactProof>)
internal data class AdminAiEvidence(
    val operation: AdminAiOperation, val success: Boolean, val output: String,
    val beforeHash: String? = null, val afterHash: String? = null,
    val checkpointId: String? = null, val buildProof: AdminAiBuildProof? = null, val state: String = if (success) "SUCCEEDED" else "FAILED"
)

/** Model requests are data. Only the local session creates approval authority. No persisted logs. */
internal class AdminAiAgentSession(val projectId: String, workspace: File) {
    val root: File = workspace.canonicalFile
    private val mutableStage = kotlinx.coroutines.flow.MutableStateFlow(AdminAiStage.IDLE)
    val stages: kotlinx.coroutines.flow.StateFlow<AdminAiStage> get() = mutableStage
    var stage: AdminAiStage
        get() = mutableStage.value
        private set(value) { mutableStage.value = value }
    var pending: AdminAiOperation? = null
        private set
    val evidence = mutableListOf<AdminAiEvidence>()
    private val usedIds = mutableSetOf<String>()
    private val checkpoints = mutableListOf<Pair<AppForgeWorkspaceCheckpoint, Map<String, String?>>>()
    private val repair = AppForgeAgentRepairLoop(AppForgeAgentRepairLoopConfig(maxRepairAttempts = 2))
    private var mutationUnverified = false
    val active: Boolean get() = stage !in setOf(AdminAiStage.IDLE, AdminAiStage.COMPLETED, AdminAiStage.FAILED, AdminAiStage.CANCELLED)

    init { require(projectId.isNotBlank() && root.isDirectory) }
    @Synchronized fun transition(next: AdminAiStage) {
        check(stage !in setOf(AdminAiStage.CANCELLED, AdminAiStage.FAILED, AdminAiStage.COMPLETED))
        require(next != AdminAiStage.COMPLETED || !mutationUnverified) { "Edits require real verification." }
        stage = next
    }
    val hasCheckpoint: Boolean get() = checkpoints.isNotEmpty()
    @Synchronized fun resetTask() {
        check(!active)
        stage = AdminAiStage.IDLE; pending = null; evidence.clear(); usedIds.clear(); repair.reset()
    }
    @Synchronized fun stop() { stage = AdminAiStage.CANCELLED; pending = null }
    @Synchronized fun fail() { if (stage != AdminAiStage.CANCELLED) stage = AdminAiStage.FAILED; pending = null }
    @Synchronized fun prepare(op: AdminAiOperation): String {
        check(active && pending == null)
        require(op.projectId == projectId && op.workspace == root.path && op.id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(usedIds.add(op.id) && usedIds.size <= 24)
        require(op.input.length <= 16000 && op.path.length <= 320)
        val preview = when (op.tool) {
            AdminAiTool.EDIT, AdminAiTool.CREATE, AdminAiTool.DELETE -> {
                val target = resolve(op.path)
                val current = currentHash(target)
                require(current == op.beforeHash) { "Stale file conflict." }
                require(op.tool != AdminAiTool.CREATE || current == null)
                require(op.tool == AdminAiTool.CREATE || current != null)
                val replacement = if (op.tool == AdminAiTool.DELETE) "" else AppForgeAgentStructuredPatch.normalizeContent(op.input)
                require(replacement.toByteArray(Charsets.UTF_8).size <= 65536) { "Mutation byte limit exceeded." }
                val old = if (target.isFile) readRaw(target) else ""
                require(currentHash(target) == current && (current == null || hash(old.toByteArray(Charsets.UTF_8)) == current)) { "File changed during preview." }
                require(old.length <= 16000) { "Mutation preview exceeds safe bound; inspect a smaller file." }
                require(sanitize(old, 16000, 20000) == old.replace("\r\n", "\n").replace('\r', '\n') && sanitize(op.input, 16000, 20000) == op.input.replace("\r\n", "\n").replace('\r', '\n')) { "Secret-like content cannot be edited by AI." }
                if (op.tool == AdminAiTool.EDIT) require(old != op.input && changedLines(old, replacement) <= 120) { "Patch must be minimal (120 changed lines maximum)." }
                "${op.tool} • $projectId • ${op.path}\nRisk: source mutation\n--- before\n${old.take(16000)}\n+++ after\n${if (op.tool == AdminAiTool.DELETE) "[DELETE]" else replacement}"
            }
            AdminAiTool.TERMINAL -> { require(commandAllowed(op.input)); "TERMINAL • $projectId\nRisk: bounded inspection\n${op.input}" }
            AdminAiTool.BUILD -> "BUILD • $projectId\nRisk: existing device build executes project code; artifact: ${op.input}"
            AdminAiTool.UNDO -> "UNDO • $projectId\nRisk: restore last checkpoint with current hash guard"
            else -> "${op.tool} • $projectId • ${op.path}"
        }
        pending = op
        stage = if (needsApproval(op.tool)) AdminAiStage.WAITING_APPROVAL else when (op.tool) {
            AdminAiTool.SEARCH -> AdminAiStage.SEARCHING
            else -> AdminAiStage.INSPECTING
        }
        return sanitize(preview, 36000, 36000)
    }
    @Synchronized fun executeLocal(op: AdminAiOperation, approved: Boolean): AdminAiEvidence {
        authorize(op, approved)
        try {
            val result = when (op.tool) {
                AdminAiTool.TREE -> entries().joinToString("\n") { it.relativeTo(root).invariantSeparatorsPath }
                AdminAiTool.READ_FILE -> {
                    val target = resolve(op.path); val raw = readRaw(target)
                    val digest = hash(raw.toByteArray(Charsets.UTF_8))
                    require(currentHash(target) == digest) { "File changed during inspection." }
                    "sha256=$digest\n${sanitize(raw)}"
                }
                AdminAiTool.METADATA -> { val f = resolve(op.path); "file=${f.isFile}; bytes=${f.length()}; sha256=${currentHash(f)}" }
                AdminAiTool.SEARCH -> {
                    require(op.input.length in 1..200)
                    entries().filter { it.isFile && it.length() <= 65536 }.take(160).flatMap { f ->
                        runCatching { sanitize(readRaw(f)).lineSequence().mapIndexedNotNull { n, line ->
                            if (line.contains(op.input, ignoreCase = true)) "${f.relativeTo(root).invariantSeparatorsPath}:${n + 1}:$line" else null
                        }.take(40).toList() }.getOrDefault(emptyList())
                    }.take(120).joinToString("\n")
                }
                AdminAiTool.EDIT, AdminAiTool.CREATE, AdminAiTool.DELETE -> return mutate(op)
                AdminAiTool.UNDO -> {
                    stage = AdminAiStage.EDITING
                    val (cp, guards) = checkpoints.lastOrNull() ?: error("No checkpoint.")
                    guards.forEach { (path, hash) -> require(currentHash(resolve(path)) == hash) { "Undo stale conflict." } }
                    AppForgeAgentWorkspaceTransaction.rollback(cp, guards)
                    checkpoints.removeAt(checkpoints.lastIndex)
                    mutationUnverified = true
                    "checkpoint=${cp.id}; restored=${guards.keys.joinToString()}"
                }
                AdminAiTool.LINT, AdminAiTool.TEST, AdminAiTool.TYPECHECK -> error("BLOCKED: packaged Linux is not an untrusted-script sandbox; no safe project verification executor available.")
                else -> error("Requires trusted runtime adapter.")
            }
            return record(AdminAiEvidence(op, true, sanitize(result)))
        } catch (e: Exception) { return record(AdminAiEvidence(op, false, sanitize(e.message.orEmpty()))) }
    }
    @Synchronized fun authorize(op: AdminAiOperation, approved: Boolean) {
        check(active && pending == op)
        require(op.projectId == projectId && op.workspace == root.path)
        require(!needsApproval(op.tool) || approved) { "Local approval required." }
    }
    @Synchronized fun record(result: AdminAiEvidence): AdminAiEvidence {
        check(active && pending == result.operation) { "Cancelled/failed session cannot accept late success." }
        if (result.success && result.operation.tool == AdminAiTool.BUILD) {
            val proof = requireNotNull(result.buildProof) { "Real build/artifact evidence required." }
            val kinds = if (result.operation.input == "both") setOf("apk", "aab") else setOf(result.operation.input)
            require(proof.projectId == projectId && proof.buildId.matches(Regex("local-[a-z0-9]{20}")) &&
                proof.buildNo != null && proof.buildNo > 0 && proof.status == "success" &&
                proof.artifacts.map { it.kind }.toSet() == kinds && proof.artifacts.size == kinds.size &&
                proof.artifacts.all { it.bytes > 0 && it.sha256.matches(Regex("[a-f0-9]{64}")) }) { "Unverified build identity/artifact." }
        }
        val bounded = result.copy(output = sanitize(result.output))
        evidence += bounded; pending = null
        stage = AdminAiStage.OBSERVING
        if (result.success && result.operation.tool == AdminAiTool.BUILD) mutationUnverified = false
        return bounded
    }
    @Synchronized fun repairDecision(result: AdminAiEvidence): Boolean {
        require(!result.success)
        val decision = repair.decide(AppForgeAgentFailure(
            if (result.operation.tool == AdminAiTool.BUILD) AppForgeAgentFailurePhase.BUILD else AppForgeAgentFailurePhase.UNKNOWN,
            output = result.output
        ))
        if (decision.action != AppForgeAgentRepairAction.REQUEST_STRUCTURED_PATCH) { fail(); return false }
        stage = AdminAiStage.REPAIRING
        return true
    }
    private fun mutate(op: AdminAiOperation): AdminAiEvidence {
        stage = AdminAiStage.EDITING
        val target = resolve(op.path)
        require(currentHash(target) == op.beforeHash) { "Stale file conflict." }
        val applied = AppForgeAgentStructuredPatch.apply(root, AppForgeAgentPatchPlan(
            hash(op.id.toByteArray()), listOf(AppForgeAgentPatchOperation(op.path, op.beforeHash,
                if (op.tool == AdminAiTool.DELETE) "" else op.input))
        ))
        try {
            if (op.tool == AdminAiTool.DELETE) require(target.delete())
            val after = currentHash(target)
            checkpoints += applied.checkpoint to mapOf(op.path to after)
            mutationUnverified = true
            return record(AdminAiEvidence(op, true, "Written ${op.path}; checkpoint=${applied.checkpoint.id}", op.beforeHash, after, applied.checkpoint.id))
        } catch (e: Exception) {
            AppForgeAgentWorkspaceTransaction.rollback(applied.checkpoint)
            throw e
        }
    }
    fun resolve(path: String): File {
        require(path.isNotBlank() && path.length <= 320 && !path.startsWith('/') && '\\' !in path && ':' !in path && path.none { it.isISOControl() })
        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." || it.lowercase() in ignored })
        var f = root
        parts.forEach { part -> f = File(f, part); require(!Files.isSymbolicLink(f.toPath())) { "Symlinks denied." } }
        val safe = f.canonicalFile
        require(safe.path.startsWith(root.path + File.separator))
        require(!AiProjectContextCollector.isSecretPath(path, safe)) { "Protected secret path." }
        return safe
    }
    private fun entries(): List<File> {
        val found = mutableListOf<File>()
        fun visit(dir: File, depth: Int) {
            if (depth > 6 || found.size >= 160) return
            Files.newDirectoryStream(dir.toPath()).use { stream ->
                for (p in stream) {
                    if (found.size >= 160) break
                    val f = runCatching { resolve(p.toFile().relativeTo(root).invariantSeparatorsPath) }.getOrNull() ?: continue
                    found += f
                    if (f.isDirectory) visit(f, depth + 1)
                }
            }
        }
        visit(root, 0); return found
    }
    private fun readRaw(file: File): String {
        require(file.isFile && file.length() <= 65536) { "File missing or exceeds 64 KiB." }
        val bytes = file.inputStream().use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 65536); out.write(buffer, 0, n) }
            out.toByteArray()
        }
        require(bytes.size <= 65536 && 0.toByte() !in bytes)
        return bytes.toString(Charsets.UTF_8)
    }
    companion object {
        private val ignored = setOf(".git", ".appforge", ".appforge-agent-v4", ".appforge-trash", ".gradle", ".idea", "node_modules", "build", "dist", "out", "coverage", "target", ".next")
        fun needsApproval(tool: AdminAiTool) = tool in setOf(AdminAiTool.EDIT, AdminAiTool.CREATE, AdminAiTool.DELETE, AdminAiTool.UNDO, AdminAiTool.TERMINAL, AdminAiTool.LINT, AdminAiTool.TEST, AdminAiTool.TYPECHECK, AdminAiTool.BUILD)
        fun commandAllowed(command: String): Boolean = TerminalCommandPolicy.review(command).allowed && command == "pwd"
        fun sanitize(text: String, limit: Int = 16000, maxLines: Int = 240): String = AiProjectContextCollector.redact(TerminalTextSanitizer.clean(text)).lineSequence().take(maxLines).joinToString("\n").take(limit)
        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun currentHash(file: File): String? { require(!file.exists() || (file.isFile && file.length() <= 65536)); return if (file.isFile) hash(file.readBytes()) else null }
        private fun changedLines(before: String, after: String): Int {
            val a = before.lines(); val b = after.lines(); var start = 0
            while (start < minOf(a.size, b.size) && a[start] == b[start]) start++
            var end = 0
            while (end < minOf(a.size, b.size) - start && a[a.lastIndex-end] == b[b.lastIndex-end]) end++
            return a.size + b.size - 2 * (start + end)
        }
    }
}
