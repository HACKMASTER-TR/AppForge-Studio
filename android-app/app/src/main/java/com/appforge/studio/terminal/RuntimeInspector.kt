package com.appforge.studio.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object RuntimeInspector {
    private val tools =
        listOf(
            "sh" to "Android Shell",
            "git" to "Git CLI",
            "python3" to "Python 3",
            "node" to "Node.js",
            "npm" to "npm",
            "java" to "Java",
            "ssh" to "OpenSSH"
        )

    suspend fun inspect(
        workingDirectory: File
    ): List<RuntimeCapability> =
        withContext(Dispatchers.IO) {
            val detected =
                tools.map { (command, label) ->
                    val path =
                        findCommand(
                            command,
                            workingDirectory
                        )

                    RuntimeCapability(
                        name = label,
                        command = command,
                        available =
                            path != null,
                        detail =
                            path
                                ?: when (command) {
                                    "git" ->
                                        "Gömülü Git ekranı hazır"

                                    "ssh" ->
                                        "Gömülü SSH ekranı hazır"

                                    "python3",
                                    "node",
                                    "npm" ->
                                        "SSH sunucusunda kullanılabilir"

                                    else ->
                                        "Bu Android ortamında bulunamadı"
                                }
                    )
                }

            detected.map { capability ->
                when (capability.command) {
                    "git" ->
                        capability.copy(
                            available = true,
                            detail =
                                if (capability.available) {
                                    capability.detail
                                } else {
                                    "AppForge gömülü Git motoru"
                                }
                        )

                    "ssh" ->
                        capability.copy(
                            available = true,
                            detail =
                                if (capability.available) {
                                    capability.detail
                                } else {
                                    "AppForge gömülü SSH motoru"
                                }
                        )

                    else ->
                        capability
                }
            }
        }

    private fun findCommand(
        command: String,
        workingDirectory: File
    ): String? =
        runCatching {
            val process =
                ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    "command -v ${ShellEscaper.quote(command)} 2>/dev/null"
                )
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .start()

            val completed =
                process.waitFor(
                    2L,
                    TimeUnit.SECONDS
                )

            if (!completed) {
                process.destroyForcibly()

                return@runCatching null
            }

            process
                .inputStream
                .bufferedReader()
                .use {
                    it.readLine()
                }
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
        }.getOrNull()
}
