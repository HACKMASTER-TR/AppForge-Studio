package com.appforge.studio.terminal

import java.io.File

enum class TerminalLineKind {
    PROMPT,
    OUTPUT,
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class TerminalLine(
    val text: String,
    val kind: TerminalLineKind = TerminalLineKind.OUTPUT
)
data class TerminalSessionState(
    val id: String,
    val title: String,
    val workingDirectory: File,
    val lines: List<TerminalLine> = emptyList(),
    val history: List<String> = emptyList(),
    val historyIndex: Int = 0,
    val running: Boolean = false
)

data class TerminalCommandResult(
    val exitCode: Int,
    val output: String,
    val workingDirectory: File,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false
)

data class SshProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val workingDirectory: String = "~"
)

data class SshAuth(
    val password: String = "",
    val privateKey: ByteArray? = null,
    val privateKeyName: String = "",
    val passphrase: String = ""
)

data class SshHostProbe(
    val host: String,
    val port: Int,
    val keyType: String,
    val encodedKey: String,
    val fingerprint: String
)

data class RuntimeCapability(
    val name: String,
    val command: String,
    val available: Boolean,
    val detail: String
)
