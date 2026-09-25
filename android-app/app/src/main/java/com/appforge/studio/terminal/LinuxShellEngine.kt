package com.appforge.studio.terminal

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    private data class ActiveProcess(
        val process: Process,
        val closeExpected: AtomicBoolean =
            AtomicBoolean(false)
    )

    private val activeProcesses =
        ConcurrentHashMap<String, ActiveProcess>()

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
                    1_000L..1_800_000L
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

            val activeProcess =
                ActiveProcess(
                    process = process
                )

            activeProcesses[
                sessionId
            ] =
                activeProcess

            try {
                val output =
                    StringBuilder()

                val readerFailure =
                    AtomicReference<IOException?>(
                        null
                    )

                val readerThread =
                    Thread {
                        try {
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
                        } catch (
                            io: IOException
                        ) {
                            /*
                             * A normal cancel/timeout closes the process pipe
                             * from another thread on Android. libcore reports
                             * that expected close as InterruptedIOException.
                             *
                             * Expected teardown must never escape this reader
                             * thread. Unexpected reader failures are retained
                             * and rethrown on the build thread below.
                             */
                            if (
                                !activeProcess
                                    .closeExpected
                                    .get()
                            ) {
                                readerFailure
                                    .compareAndSet(
                                        null,
                                        io
                                    )
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
                    activeProcess
                        .closeExpected
                        .set(true)

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

                readerFailure
                    .get()
                    ?.let {
                        throw it
                    }

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
                activeProcess
                    .closeExpected
                    .set(true)

                process
                    .destroyForcibly()

                throw cancelled
            } finally {
                activeProcesses
                    .remove(
                        sessionId,
                        activeProcess
                    )
            }
        }

    fun cancel(
        sessionId: String
    ): Boolean {
        val activeProcess =
            activeProcesses
                .remove(
                    sessionId
                )
                ?: return false

        activeProcess
            .closeExpected
            .set(true)

        activeProcess
            .process
            .destroy()

        return true
    }

    companion object {
        private const val
            MAX_CAPTURE_CHARS =
                512 * 1_024
    }
}
