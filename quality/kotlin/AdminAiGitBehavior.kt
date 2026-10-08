package com.appforge.studio.ai

import com.appforge.studio.terminal.GitWorkspaceService
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

fun gitBehavior() = runBlocking {
    val root = Files.createTempDirectory("admin-ai-git").toFile()
    try {
        fun git(vararg arguments: String) { val process = ProcessBuilder(listOf("git", "-C", root.path) + arguments).redirectErrorStream(true).start(); process.inputStream.readBytes(); check(process.waitFor() == 0) }
        git("init")
        File(root,"main.js").writeText("old\n")
        File(root,".env").writeText("unlabelled_dummy_secret")
        git("add", "main.js", ".env")
        File(root,"main.js").writeText("new\n")
        File(root,".env").writeText("unlabelled_dummy_changed_secret")
        val session = AdminAiAgentSession("A",root)
        val status = GitWorkspaceService.agentInspect(root,false,session::resolve)
        check(status.contains("MODIFIED main.js") && !status.contains(".env"))
        val diff = GitWorkspaceService.agentInspect(root,true,session::resolve)
        check(diff.contains("old") && diff.contains("new") && !diff.contains("dummy"))
        File(root,".git/objects/info/alternates").writeText("/outside\n")
        check(runCatching { GitWorkspaceService.agentInspect(root,true,session::resolve) }.isFailure)
        println("PASS: 3 real JGit inspection checks")
    } finally { root.deleteRecursively() }
}
