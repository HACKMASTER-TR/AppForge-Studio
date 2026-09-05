package com.appforge.studio.terminal

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class InteractiveLinuxPtySession(
    context: Context
) : AutoCloseable {
    private val appContext =
        context.applicationContext

    private val packagedEngine =
        PackagedLinuxEngine(
            appContext
        )

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val running =
        AtomicBoolean(false)

    private var processId: Int? =
        null

    private var inputDescriptor:
        ParcelFileDescriptor? =
        null

    private var outputDescriptor:
        ParcelFileDescriptor? =
        null

    private var controlDescriptor:
        ParcelFileDescriptor? =
        null

    private var inputStream:
        FileInputStream? =
        null

    private var outputStream:
        FileOutputStream? =
        null

    private var readerJob: Job? =
        null

    private var waiterJob: Job? =
        null

    val isRunning: Boolean
        get() = running.get()

    suspend fun start(
        rootfs: File,
        workspace: File,
        rows: Int,
        columns: Int,
        onOutput: (String) -> Unit,
        onExit: (Int) -> Unit
    ) {
        check(
            running.compareAndSet(
                false,
                true
            )
        ) {
            "Linux PTY oturumu zaten çalışıyor."
        }

        try {
            withContext(
                Dispatchers.IO
            ) {
                val launcher =
                    packagedEngine
                        .requireLauncher()

                val arguments =
                    ProrootPinnedRuntime
                        .buildInteractiveShellArguments(
                            rootfs = rootfs,
                            workspace = workspace
                        )

                val runtimeTemp =
                    File(
                        appContext.filesDir,
                        "terminal/linux/proroot-tmp"
                    ).apply {
                        mkdirs()
                    }.canonicalFile

                val spawned =
                    AppForgePtyBridge.spawn(
                        executable =
                            launcher.absolutePath,
                        arguments =
                            arguments,
                        environment =
                            mapOf(
                                "PROROOT_TMP_DIR" to
                                    runtimeTemp.absolutePath,
                                "HOME" to "/root",
                                "TERM" to
                                    "xterm-256color",
                                "COLORTERM" to
                                    "truecolor",
                                "LANG" to
                                    "C.UTF-8",
                                "PATH" to
                                    "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                            ),
                        workingDirectory =
                            appContext.filesDir
                                .canonicalPath,
                        rows = rows,
                        columns = columns
                    )

                processId =
                    spawned.processId

                inputDescriptor =
                    ParcelFileDescriptor
                        .adoptFd(
                            spawned.inputFd
                        )

                outputDescriptor =
                    ParcelFileDescriptor
                        .adoptFd(
                            spawned.outputFd
                        )

                controlDescriptor =
                    ParcelFileDescriptor
                        .adoptFd(
                            spawned.controlFd
                        )

                inputStream =
                    ParcelFileDescriptor
                        .AutoCloseInputStream(
                            requireNotNull(
                                inputDescriptor
                            )
                        )
                inputDescriptor = null

                outputStream =
                    ParcelFileDescriptor
                        .AutoCloseOutputStream(
                            requireNotNull(
                                outputDescriptor
                            )
                        )
                outputDescriptor = null

                val input =
                    requireNotNull(
                        inputStream
                    )

                readerJob =
                    scope.launch {
                        runCatching {
                            InputStreamReader(
                                input,
                                Charsets.UTF_8
                            ).use { reader ->
                                val buffer =
                                    CharArray(
                                        2_048
                                    )

                                while (
                                    running.get()
                                ) {
                                    val count =
                                        reader.read(
                                            buffer
                                        )

                                    if (count < 0) {
                                        break
                                    }

                                    if (count > 0) {
                                        onOutput(
                                            String(
                                                buffer,
                                                0,
                                                count
                                            )
                                        )
                                    }
                                }
                            }
                        }.onFailure {
                            if (running.get()) {
                                terminate()
                            }
                        }
                    }

                waiterJob =
                    scope.launch {
                        val exitCode =
                            AppForgePtyBridge
                                .waitFor(
                                    spawned.processId
                                )

                        val shouldNotifyExit =
                            running.getAndSet(false)

                        closeDescriptors()

                        if (shouldNotifyExit) {
                            onExit(exitCode)
                        }
                    }
            }
        } catch (error: Throwable) {
            running.set(false)
            closeDescriptors()
            throw error
        }
    }

    suspend fun write(
        text: String
    ) {
        if (
            text.isEmpty() ||
            !running.get()
        ) {
            return
        }

        withContext(
            Dispatchers.IO
        ) {
            synchronized(this@InteractiveLinuxPtySession) {
                val stream =
                    outputStream
                        ?: return@synchronized

                stream.write(
                    text.toByteArray(
                        Charsets.UTF_8
                    )
                )
                stream.flush()
            }
        }
    }

    suspend fun sendControlC() {
        write("\u0003")
    }

    suspend fun resize(
        rows: Int,
        columns: Int
    ): Boolean =
        withContext(
            Dispatchers.IO
        ) {
            val descriptor =
                controlDescriptor
                    ?: return@withContext false

            AppForgePtyBridge.resize(
                controlFd = descriptor.fd,
                rows = rows,
                columns = columns
            )
        }

    fun terminate() {
        val pid =
            processId
                ?: return

        AppForgePtyBridge.terminate(
            pid
        )
    }

    override fun close() {
        if (running.getAndSet(false)) {
            terminate()
        }

        readerJob?.cancel()
        waiterJob?.cancel()
        readerJob = null
        waiterJob = null

        closeDescriptors()
        scope.cancel()
    }

    @Synchronized
    private fun closeDescriptors() {
        runCatching {
            inputStream?.close()
        }
        inputStream = null

        runCatching {
            outputStream?.close()
        }
        outputStream = null

        runCatching {
            inputDescriptor?.close()
        }
        inputDescriptor = null

        runCatching {
            outputDescriptor?.close()
        }
        outputDescriptor = null

        runCatching {
            controlDescriptor?.close()
        }
        controlDescriptor = null

        processId = null
    }
}
