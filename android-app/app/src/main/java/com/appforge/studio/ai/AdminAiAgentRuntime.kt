package com.appforge.studio.ai

import android.content.Context
import com.appforge.studio.build.BuildApiClient
import com.appforge.studio.build.DeviceBuildEngine
import com.appforge.studio.build.DeviceBuildRuntimeV3
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode
import com.appforge.studio.security.OwnerAccessPolicy
import com.appforge.studio.terminal.LinuxShellEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Reuses the packaged Linux engine and device build gates. Cloudflare never executes tools. */
internal class AdminAiAgentRuntime(private val context: Context, private val session: AdminAiAgentSession,
    private val draft: ProjectDraft, private val mayBuild: () -> Boolean) {
    private val shell = LinuxShellEngine(context)
    private val shellId = "admin-ai-${UUID.randomUUID()}"
    private val builds = BuildApiClient(context, "device://local", "")
    @Volatile private var buildId: String? = null
    @Volatile private var cancelled = false
    fun beginTask() {
        check(session.stage == AdminAiStage.IDLE)
        cancelled = false; buildId = null
    }
    fun stop() { cancelled = true; session.stop(); shell.cancel(shellId); buildId?.let { builds.cancelBuild(it) } }
    suspend fun execute(op: AdminAiOperation, approved: Boolean): AdminAiEvidence = withContext(Dispatchers.IO) {
        check(OwnerAccessPolicy.isActiveOwner(context)) { "Administrator verification required." }
        session.authorize(op, approved)
        try {
            currentCoroutineContext().ensureActive(); check(!cancelled)
            when (op.tool) {
                AdminAiTool.TERMINAL -> {
                    // No project scripts, shell syntax, or arbitrary Git configuration execution.
                    session.transition(AdminAiStage.INSPECTING)
                    require(op.input == "pwd") { "Only pwd is safe in this non-sandboxed runtime." }
                    val rootfs = DeviceBuildRuntimeV3.ensureReady(context) { }
                    currentCoroutineContext().ensureActive(); check(!cancelled)
                    val result = shell.execute(shellId, rootfs, session.root, op.input, timeoutMs = 10000)
                    currentCoroutineContext().ensureActive(); check(!cancelled)
                    session.record(AdminAiEvidence(op, result.exitCode == 0 && !result.timedOut,
                        "exit=${result.exitCode}; timeout=${result.timedOut}\n${result.output}"))
                }
                AdminAiTool.BUILD -> {
                    require(mayBuild()) { "Studio has an active build." }
                    require(draft.sourceMode == SourceMode.LOCAL && draft.signingMode == SigningMode.DEBUG &&
                        draft.sourceBuildEngine != "windows-native") { "AI build requires local sources and debug signing; native publisher flow denied." }
                    require(op.input in setOf("apk", "aab", "both", "exe"))
                    require(op.input != "exe" || !com.appforge.studio.build.WindowsPublisherSigningPolicy.signingRequested(context)) { "Publisher signing must remain outside AI authority." }
                    // Fail closed if workspace contains symlinks: existing build copies project sources.
                    rejectSymlinks(session.root)
                    session.transition(AdminAiStage.BUILDING)
                    val started = builds.createBuild(draft.copy(importedFolder = session.root.path, buildOutput = op.input), null)
                    buildId = started.buildId
                    if (cancelled) { builds.cancelBuild(started.buildId); throw CancellationException() }
                    withTimeout(20 * 60 * 1000L) {
                        while (true) {
                            currentCoroutineContext().ensureActive(); check(!cancelled)
                            val state = builds.getBuild(started.buildId)
                            require(state.buildId == started.buildId && state.buildNo == started.buildNo)
                            if (state.status in setOf("success", "failed", "cancelled", "canceled")) {
                                val kinds = if (op.input == "both") listOf("apk", "aab") else listOf(op.input)
                                val artifacts = if (state.status == "success") kinds.map { kind ->
                                    val file = DeviceBuildEngine.artifact(started.buildId, kind) ?: error("Requested artifact missing: $kind")
                                    val root = File(context.filesDir, "device-build/artifacts/${started.buildId}").canonicalFile
                                    require(file.canonicalPath.startsWith(root.path + File.separator) && file.isFile && file.length() > 0)
                                    val digest = MessageDigest.getInstance("SHA-256")
                                    file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n < 0) break; currentCoroutineContext().ensureActive(); digest.update(buffer, 0, n) } }
                                    AdminAiArtifactProof(kind, file.length(), digest.digest().joinToString("") { "%02x".format(it) })
                                } else emptyList()
                                currentCoroutineContext().ensureActive(); check(!cancelled)
                                return@withTimeout session.record(AdminAiEvidence(op, state.status == "success" && artifacts.size == kinds.size,
                                    "project=${session.projectId}; build=${state.buildId}; number=${state.buildNo}; status=${state.status}\n${artifacts.joinToString("\n") { "${it.kind} bytes=${it.bytes} sha256=${it.sha256}" }}\n${state.logs.takeLast(40).joinToString("\n")}",
                                    buildProof = AdminAiBuildProof(session.projectId, state.buildId, state.buildNo, state.status, artifacts)))
                            }
                            delay(500)
                        }
                        @Suppress("UNREACHABLE_CODE") error("Build did not finish")
                    }
                }
                AdminAiTool.BUILD_RESULT -> {
                    val id = buildId ?: error("No build in this session.")
                    val state = builds.getBuild(id)
                    session.record(AdminAiEvidence(op, true, "build=$id; status=${state.status}; progress=${state.progress}"))
                }
                AdminAiTool.GIT_STATUS, AdminAiTool.GIT_DIFF -> {
                    val output = com.appforge.studio.terminal.GitWorkspaceService.agentInspect(session.root,
                        op.tool == AdminAiTool.GIT_DIFF, session::resolve)
                    currentCoroutineContext().ensureActive(); check(!cancelled)
                    session.record(AdminAiEvidence(op, true, output))
                }
                else -> session.executeLocal(op, approved)
            }
        } catch (e: CancellationException) {
            buildId?.let { builds.cancelBuild(it) }; shell.cancel(shellId); session.stop(); throw e
        } catch (e: Exception) {
            buildId?.let { builds.cancelBuild(it) }
            if (session.stage == AdminAiStage.CANCELLED) throw CancellationException()
            session.record(AdminAiEvidence(op, false, e.message.orEmpty()))
        }
    }
    private fun rejectSymlinks(root: File) {
        var count = 0
        fun visit(dir: File, depth: Int) {
            require(depth <= 32)
            java.nio.file.Files.newDirectoryStream(dir.toPath()).use { stream ->
                for (path in stream) {
                    require(++count <= 20000 && !java.nio.file.Files.isSymbolicLink(path)) { "Build source unsafe or scan limit exceeded." }
                    if (java.nio.file.Files.isDirectory(path)) visit(path.toFile(), depth + 1)
                }
            }
        }
        visit(root, 0)
    }
}
