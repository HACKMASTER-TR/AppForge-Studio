package com.appforge.studio.terminal

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class LinuxShellResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean
)

internal class LinuxShellEngine(
    context: Context
) {
    private val applicationContext =
        context.applicationContext

    private val packagedEngine =
        PackagedLinuxEngine(
            applicationContext
        )

    private val activeProcesses =
        ConcurrentHashMap<String, Process>()

    suspend fun execute(
        sessionId: String,
        rootfs: File,
        workspace: File,
        command: String,
        confirmed: Boolean = false,
        timeoutMs: Long = 120_000L
    ): LinuxShellResult =
        withContext(
            Dispatchers.IO
        ) {
            require(
                sessionId.isNotBlank() &&
                    timeoutMs in
                    1_000L..600_000L
            ) {
                "Linux oturumu geçersiz."
            }

            val review =
                TerminalCommandPolicy.review(
                    command
                )

            require(
                review.allowed
            ) {
                review.message
            }

            require(
                confirmed ||
                    !review.requiresConfirmation
            ) {
                "Bu Linux komutu çalıştırılmadan önce kullanıcı onayı gerekiyor."
            }

            val launcher =
                packagedEngine
                    .requireLauncher()

            val arguments =
                ProrootPinnedRuntime
                    .buildShellArguments(
                        rootfs = rootfs,
                        workspace = workspace,
                        command = command
                    )

            val runtimeTemp =
                File(
                    applicationContext.filesDir,
                    "terminal/linux/proroot-tmp"
                ).apply {
                    mkdirs()
                }

            val process =
                ProcessBuilder(
                    listOf(
                        launcher.absolutePath
                    ) + arguments
                )
                    .directory(
                        applicationContext
                            .filesDir
                    )
                    .redirectErrorStream(
                        true
                    )
                    .apply {
                        environment()
                            .remove(
                                "LD_PRELOAD"
                            )

                        environment()
                            .remove(
                                "LD_LIBRARY_PATH"
                            )

                        environment()[
                            "PROROOT_TMP_DIR"
                        ] =
                            runtimeTemp
                                .absolutePath

                        environment()[
                            "HOME"
                        ] =
                            "/root"

                        environment()[
                            "TERM"
                        ] =
                            "xterm-256color"

                        environment()[
                            "LANG"
                        ] =
                            "C.UTF-8"

                        environment()[
                            "PATH"
                        ] =
                            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                    }
                    .start()

            activeProcesses[
                sessionId
            ] =
                process

            try {
                val output =
                    StringBuilder()

                val readerThread =
                    Thread {
                        process
                            .inputStream
                            .reader(
                                Charsets.UTF_8
                            )
                            .use { reader ->
                                val buffer =
                                    CharArray(
                                        2_048
                                    )

                                while (true) {
                                    val count =
                                        reader.read(
                                            buffer
                                        )

                                    if (
                                        count < 0
                                    ) {
                                        break
                                    }

                                    synchronized(
                                        output
                                    ) {
                                        if (
                                            output.length <
                                            MAX_CAPTURE_CHARS
                                        ) {
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
                            "AppForgeLinux-$sessionId"

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
                        process
                            .destroyForcibly()
                    }
                }

                readerThread.join(
                    1_000L
                )

                val captured =
                    synchronized(
                        output
                    ) {
                        output.toString()
                    }

                LinuxShellResult(
                    exitCode =
                        if (completed) {
                            process.exitValue()
                        } else {
                            124
                        },
                    output =
                        TerminalTextSanitizer
                            .clean(
                                captured
                            )
                            .trimEnd(),
                    timedOut =
                        !completed
                )
            } catch (
                cancelled:
                    CancellationException
            ) {
                process
                    .destroyForcibly()

                throw cancelled
            } finally {
                activeProcesses
                    .remove(
                        sessionId,
                        process
                    )
            }
        }

    fun cancel(
        sessionId: String
    ): Boolean {
        val process =
            activeProcesses
                .remove(
                    sessionId
                )
                ?: return false

        process.destroy()

        return true
    }

    companion object {
        private const val
            MAX_CAPTURE_CHARS =
                512 * 1_024
    }
}
