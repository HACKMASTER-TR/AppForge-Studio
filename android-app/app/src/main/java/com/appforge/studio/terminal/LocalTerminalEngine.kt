package com.appforge.studio.terminal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class LocalTerminalEngine(
    private val appFilesDirectory: File
) {
    private val activeProcesses =
        ConcurrentHashMap<String, Process>()

    suspend fun execute(
        sessionId: String,
        command: String,
        workingDirectory: File,
        timeoutMs: Long = 120_000L
    ): TerminalCommandResult =
        withContext(Dispatchers.IO) {
            require(
                command.isNotBlank() &&
                    command.length <= 16 * 1_024 &&
                    '\u0000' !in command &&
                    timeoutMs in 1_000L..600_000L
            ) {
                "Komut geçersiz veya çok uzun."
            }

            val safeWorkingDirectory =
                prepareWorkingDirectory(workingDirectory)

            val marker =
                "__APPFORGE_TERMINAL_${UUID.randomUUID().toString().replace("-", "")}__"

            val script =
                buildString {
                    append(command)
                    append("\n__appforge_exit=$?\n")
                    append("printf '\\n")
                    append(marker)
                    append("%s\\t%s\\n' \"\$__appforge_exit\" \"\$PWD\"\n")
                }

            val process =
                ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    script
                )
                    .directory(safeWorkingDirectory)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["HOME"] =
                            safeWorkingDirectory.absolutePath

                        environment()["TMPDIR"] =
                            File(
                                appFilesDirectory,
                                "terminal/tmp"
                            ).apply {
                                mkdirs()
                            }.absolutePath

                        environment()["PATH"] =
                            listOf(
                                "/system/bin",
                                "/system/xbin",
                                "/product/bin",
                                "/vendor/bin"
                            ).joinToString(":")

                        environment()["TERM"] =
                            "xterm-256color"

                        environment()["LANG"] =
                            "C.UTF-8"
                    }
                    .start()

            activeProcesses[sessionId] =
                process

            try {
                val output =
                    StringBuilder()

                val readerThread =
                    Thread {
                        process
                            .inputStream
                            .reader(Charsets.UTF_8)
                            .use { reader ->
                                val buffer =
                                    CharArray(2_048)

                                while (true) {
                                    val count =
                                        reader.read(buffer)

                                    if (count < 0) {
                                        break
                                    }

                                    synchronized(output) {
                                        if (output.length < MAX_CAPTURE_CHARS) {
                                            val remaining =
                                                MAX_CAPTURE_CHARS -
                                                    output.length

                                            output.append(
                                                buffer,
                                                0,
                                                minOf(
                                                    count,
                                                    remaining
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                    }.apply {
                        name =
                            "AppForgeTerminal-$sessionId"

                        isDaemon =
                            true

                        start()
                    }

                val completed =
                    process.waitFor(
                        timeoutMs,
                        TimeUnit.MILLISECONDS
                    )

                if (!completed) {
                    process.destroy()

                    if (
                        !process.waitFor(
                            500L,
                            TimeUnit.MILLISECONDS
                        )
                    ) {
                        process.destroyForcibly()
                    }
                }

                readerThread.join(1_000L)

                val captured =
                    synchronized(output) {
                        output.toString()
                    }

                val parsed =
                    parseResult(
                        captured,
                        marker,
                        safeWorkingDirectory
                    )

                parsed.copy(
                    exitCode =
                        if (completed) {
                            parsed.exitCode
                        } else {
                            124
                        },
                    timedOut =
                        !completed
                )
            } catch (cancelled: CancellationException) {
                process.destroyForcibly()
                throw cancelled
            } finally {
                activeProcesses.remove(
                    sessionId,
                    process
                )
            }
        }

    fun cancel(sessionId: String): Boolean {
        val process =
            activeProcesses.remove(sessionId)
                ?: return false

        process.destroy()

        return true
    }

    private fun prepareWorkingDirectory(
        requested: File
    ): File {
        val fallback =
            File(
                appFilesDirectory,
                "terminal/workspaces/scratch"
            ).apply {
                mkdirs()
            }

        val candidate =
            runCatching {
                requested.canonicalFile
            }.getOrNull()

        return candidate
            ?.takeIf {
                it.isDirectory &&
                    it.canRead()
            }
            ?: fallback.canonicalFile
    }

    private fun parseResult(
        captured: String,
        marker: String,
        fallbackDirectory: File
    ): TerminalCommandResult {
        val clean =
            TerminalTextSanitizer.clean(captured)

        val markerIndex =
            clean.lastIndexOf(marker)

        if (markerIndex < 0) {
            return TerminalCommandResult(
                exitCode = 1,
                output = clean.trimEnd(),
                workingDirectory = fallbackDirectory
            )
        }

        val visible =
            clean.substring(
                0,
                markerIndex
            ).trimEnd()

        val metadata =
            clean.substring(
                markerIndex +
                    marker.length
            ).lineSequence()
                .firstOrNull()
                .orEmpty()

        val exitCode =
            metadata
                .substringBefore('\t')
                .trim()
                .toIntOrNull()
                ?: 1

        val reportedDirectory =
            metadata
                .substringAfter(
                    '\t',
                    ""
                )
                .trim()
                .takeIf {
                    it.isNotBlank()
                }
                ?.let(::File)
                ?.takeIf {
                    it.isDirectory &&
                        it.canRead()
                }
                ?: fallbackDirectory

        return TerminalCommandResult(
            exitCode = exitCode,
            output = visible,
            workingDirectory = reportedDirectory
        )
    }

    companion object {
        private const val MAX_CAPTURE_CHARS =
            512 * 1_024
    }
}
