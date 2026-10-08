package com.appforge.studio.ai

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class AdminAiAgentSessionTest {
    private fun rejected(block: () -> Unit) = assertTrue(runCatching(block).isFailure)
    @Test fun approvedEditHashConflictAndGuardedUndo() {
        val root = Files.createTempDirectory("admin-agent-edit").toFile()
        try {
            val file = File(root, "main.js").apply { writeText("old\n") }
            val session = AdminAiAgentSession("saved", root)
            session.transition(AdminAiStage.PLANNING)
            val op = AdminAiOperation("edit", "saved", root.canonicalPath, AdminAiTool.EDIT,
                file.name, "new\n", AdminAiAgentSession.currentHash(file))
            assertTrue(session.prepare(op).contains("--- before"))
            rejected { session.executeLocal(op, false) }
            val applied = session.executeLocal(op, true)
            assertTrue(applied.success)
            assertNotNull(applied.checkpointId)
            assertEquals("new\n", file.readText())
            rejected { session.transition(AdminAiStage.COMPLETED) }
            val undo = op.copy(id = "undo", tool = AdminAiTool.UNDO, path = "", input = "", beforeHash = null)
            session.prepare(undo)
            file.writeText("external\n")
            assertFalse(session.executeLocal(undo, true).success)
            assertEquals("external\n", file.readText())
            file.writeText("new\n")
            val retry = undo.copy(id = "undo-again")
            session.prepare(retry)
            assertTrue(session.executeLocal(retry, true).success)
            assertEquals("old\n", file.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun projectIdentityAndCancelledTaskRejectLateResults() {
        val a = Files.createTempDirectory("admin-project-a").toFile()
        val b = Files.createTempDirectory("admin-project-b").toFile()
        try {
            File(a, "a.js").writeText("project A")
            File(b, "b.js").writeText("project B")
            val first = AdminAiAgentSession("A", a)
            val second = AdminAiAgentSession("B", b)
            first.transition(AdminAiStage.PLANNING); second.transition(AdminAiStage.PLANNING)
            val old = AdminAiOperation("tree-a", "A", a.canonicalPath, AdminAiTool.TREE)
            first.prepare(old)
            rejected { second.prepare(old) }
            first.stop()
            rejected { first.record(AdminAiEvidence(old, true, "late success")) }
            rejected { first.transition(AdminAiStage.COMPLETED) }
            val next = old.copy(id = "tree-b", projectId = "B", workspace = b.canonicalPath)
            second.prepare(next)
            val evidence = second.executeLocal(next, false)
            assertTrue(evidence.output.contains("b.js"))
            assertFalse(evidence.output.contains("a.js"))
            assertEquals(1, second.evidence.size)
            assertEquals(AdminAiStage.CANCELLED, first.stage)
        } finally { a.deleteRecursively(); b.deleteRecursively() }
    }
    @Test fun secretTraversalSymlinkAndOutputBounds() {
        val root = Files.createTempDirectory("admin-agent-secret").toFile()
        val outside = Files.createTempDirectory("admin-agent-outside").toFile()
        try {
            Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
            val session = AdminAiAgentSession("saved", root)
            for (path in listOf("../file", "/etc/passwd", "escape/file", ".env.local", ".aws/credentials", "signing.jks")) rejected { session.resolve(path) }
            val text = AdminAiAgentSession.sanitize("Authorization: Bearer dummy-test\npassword = dummy-test\n" + "x".repeat(50000))
            assertFalse(text.contains("dummy-test")); assertTrue(text.length <= 16000)
            assertTrue(AdminAiAgentSession.commandAllowed("pwd"))
            for (command in listOf("pwd; rm -rf /", "npm test", "git push", "cat .env", "pwd &")) assertFalse(AdminAiAgentSession.commandAllowed(command))
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }
    @Test fun buildCannotSucceedFromAssistantProse() {
        val root = Files.createTempDirectory("admin-agent-build").toFile()
        try {
            val session = AdminAiAgentSession("saved", root)
            session.transition(AdminAiStage.PLANNING)
            val op = AdminAiOperation("build", "saved", root.canonicalPath, AdminAiTool.BUILD, input = "apk")
            session.prepare(op)
            rejected { session.record(AdminAiEvidence(op, true, "Build succeeded")) }
            val badProof = AdminAiBuildProof("other", "local-" + "a".repeat(20), 1, "success", listOf(AdminAiArtifactProof("apk", 1, "a".repeat(64))))
            rejected { session.record(AdminAiEvidence(op, true, "", buildProof = badProof)) }
            session.fail(); rejected { session.transition(AdminAiStage.COMPLETED) }
        } finally { root.deleteRecursively() }
    }
}
