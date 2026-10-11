package com.appforge.studio.ai

import android.content.Context
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.terminal.LinuxShellEngine
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files

private var assertions = 0
private fun verify(ok: Boolean) { check(ok); assertions++ }
private fun denied(block: () -> Unit) { verify(runCatching(block).isFailure) }
fun main() = runBlocking {
    val root = Files.createTempDirectory("admin-ai-test").toFile()
    val outside = Files.createTempDirectory("admin-ai-outside").toFile()
    try {
        val file = File(root, "src/main.js").apply { parentFile.mkdirs(); writeText("old\n") }
        File(root, ".env.local").writeText("dummy hidden value")
        File(root, "notes.txt").writeText("token = dummy-value\nokay")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
        val s = AdminAiAgentSession("A", root)
        s.transition(AdminAiStage.UNDERSTANDING)
        fun op(tool: AdminAiTool, path: String = "", input: String = "", before: String? = null) =
            AdminAiOperation("op-${assertions}", "A", root.canonicalPath, tool, path, input, before)
        for (path in listOf("../outside", "/etc/passwd", "escape/value", ".env.local", "secrets/key.txt", "x/../main", "id_ed25519", "key.pfx", ".appforge-agent-v4/state")) denied { s.resolve(path) }
        denied { s.prepare(op(AdminAiTool.TREE).copy(projectId = "B")) }
        val tree = op(AdminAiTool.TREE); s.prepare(tree)
        val t = s.executeLocal(tree, false)
        verify(t.success && !t.output.contains(".env") && !t.output.contains("escape"))
        val read = op(AdminAiTool.READ_FILE, "notes.txt"); s.prepare(read)
        val r = s.executeLocal(read, false)
        verify(r.success && !r.output.contains("dummy-value") && r.output.contains("REDACTED"))
        val search = op(AdminAiTool.SEARCH, input = "old"); s.prepare(search)
        verify(s.executeLocal(search, false).output.contains("src/main.js:1:old"))
        val edit = op(AdminAiTool.EDIT, "src/main.js", "new\n", AdminAiAgentSession.currentHash(file))
        verify(s.prepare(edit).contains("--- before"))
        denied { s.executeLocal(edit, false) }
        file.writeText("external\n")
        val conflict = s.executeLocal(edit, true)
        verify(!conflict.success && file.readText() == "external\n")
        val realEdit = op(AdminAiTool.EDIT, "src/main.js", "new\n", AdminAiAgentSession.currentHash(file))
        s.prepare(realEdit); val applied = s.executeLocal(realEdit, true)
        verify(applied.success && applied.checkpointId != null && applied.afterHash == AdminAiAgentSession.currentHash(file) && file.readText() == "new\n")
        denied { s.transition(AdminAiStage.COMPLETED) }
        val undo = op(AdminAiTool.UNDO); s.prepare(undo)
        file.writeText("new external\n")
        verify(!s.executeLocal(undo, true).success && file.readText() == "new external\n")
        file.writeText("new\n")
        val undo2 = op(AdminAiTool.UNDO); s.prepare(undo2)
        verify(s.executeLocal(undo2, true).success && file.readText() == "external\n")
        val create = op(AdminAiTool.CREATE, "src/new.js", "created\n")
        s.prepare(create); verify(s.executeLocal(create, true).success && File(root,"src/new.js").isFile)
        val delete = op(AdminAiTool.DELETE, "src/new.js", before = AdminAiAgentSession.currentHash(File(root,"src/new.js")))
        s.prepare(delete); verify(s.executeLocal(delete, true).success && !File(root,"src/new.js").exists())
        val undoDelete = op(AdminAiTool.UNDO); s.prepare(undoDelete)
        verify(s.executeLocal(undoDelete, true).success && File(root,"src/new.js").readText() == "created\n")
        val big = File(root,"big.js").apply { writeText("a".repeat(65537)) }
        val bigRead = op(AdminAiTool.READ_FILE,big.name); s.prepare(bigRead)
        verify(!s.executeLocal(bigRead,false).success)
        verify(AdminAiAgentSession.sanitize("line\n".repeat(10000)).length <= 16000)
        verify(!AdminAiAgentSession.sanitize("Bearer dummy_token_for_test\nghp_" + "a".repeat(25)).contains("dummy_token"))
        verify(AdminAiAgentSession.commandAllowed("pwd"))
        for(cmd in listOf("pwd; rm -rf /", "npm test", "curl example.com", "sudo true", "pwd &", "cat .env", "git push")) verify(!AdminAiAgentSession.commandAllowed(cmd))
        val b = op(AdminAiTool.BUILD, input="apk"); s.prepare(b)
        denied { s.record(AdminAiEvidence(b, true, "assistant says success")) }
        denied { s.record(AdminAiEvidence(b, true, "", buildProof=AdminAiBuildProof("B","local-"+"a".repeat(20),1,"success",listOf(AdminAiArtifactProof("apk",1,"a".repeat(64)))))) }
        s.stop(); denied { s.record(AdminAiEvidence(b,true,"late success")) }; denied { s.transition(AdminAiStage.COMPLETED) }
        verify(s.stage == AdminAiStage.CANCELLED)
        s.resetTask(); verify(s.stage == AdminAiStage.IDLE && s.evidence.isEmpty() && s.hasCheckpoint)
        val failed = AdminAiAgentSession("B",root); failed.transition(AdminAiStage.PLANNING); failed.fail()
        denied { failed.transition(AdminAiStage.COMPLETED) }
        val loop = AppForgeAgentRepairLoop(AppForgeAgentRepairLoopConfig(2,1))
        verify(loop.decide(AppForgeAgentFailure(AppForgeAgentFailurePhase.BUILD,1,"error one")).action == AppForgeAgentRepairAction.REQUEST_STRUCTURED_PATCH)
        verify(loop.decide(AppForgeAgentFailure(AppForgeAgentFailurePhase.BUILD,1,"error two")).action == AppForgeAgentRepairAction.REQUEST_STRUCTURED_PATCH)
        verify(loop.decide(AppForgeAgentFailure(AppForgeAgentFailurePhase.BUILD,1,"error three")).action == AppForgeAgentRepairAction.STOP_MANUAL)
        val cpRoot = Files.createTempDirectory("agent-control-escape").toFile()
        try {
            Files.createSymbolicLink(File(cpRoot,".appforge-agent-v4").toPath(),outside.toPath())
            denied { AppForgeAgentStructuredPatch.apply(cpRoot,AppForgeAgentPatchPlan("a".repeat(64),listOf(AppForgeAgentPatchOperation("a.js",null,"safe")))) }
            verify(outside.listFiles().orEmpty().isEmpty())
        } finally { cpRoot.deleteRecursively() }
        verify(AppForgeAgentStructuredPatch.normalizeContent("const escaped = \"\\r\\n\";\n") == "const escaped = \"\\r\\n\";\n")
        verify(AppForgeAgentStructuredPatch.normalizeContent("line\r\nnext") == "line\nnext\n")
        val collector = AiProjectContextCollector.collect("A",ProjectDraft(importedFolder=root.path))
        verify(collector.files.size <= 6 && !collector.tree.contains(".env") && !collector.tree.contains(".appforge-agent"))
        // These exercise the real LinuxShellEngine process/timeout/output/cancel code,
        // with only the Android launcher boundary replaced by a host test fixture.
        val shell = LinuxShellEngine(Context(root))
        val timeout = shell.execute("timeout",root,root,"exec sleep 2",timeoutMs=1000)
        verify(timeout.timedOut && timeout.exitCode == 124)
        val cap = shell.execute("cap",root,root,"head -c 700000 /dev/zero | tr '\\0' x",timeoutMs=5000)
        verify(cap.exitCode == 0 && cap.output.length <= 512*1024)
        val cancelJob = async(Dispatchers.Default) { shell.execute("cancel",root,root,"exec sleep 30",timeoutMs=60000) }
        delay(300); cancelJob.cancel(); verify(shell.cancel("cancel")); cancelJob.join(); verify(cancelJob.isCancelled)
        println("PASS: $assertions behavioral assertions (host JVM; Android launcher fixture)")
    } finally { root.deleteRecursively(); outside.deleteRecursively() }
}
