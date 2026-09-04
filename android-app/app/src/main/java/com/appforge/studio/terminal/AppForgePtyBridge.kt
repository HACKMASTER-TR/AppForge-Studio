package com.appforge.studio.terminal

import java.io.IOException

internal data class AppForgePtyProcess(
    val processId: Int,
    val inputFd: Int,
    val outputFd: Int,
    val controlFd: Int
)

internal object AppForgePtyBridge {
    init {
        System.loadLibrary(
            "appforge_pty"
        )
    }

    @Throws(IOException::class)
    fun spawn(
        executable: String,
        arguments: List<String>,
        environment: Map<String, String>,
        workingDirectory: String,
        rows: Int,
        columns: Int
    ): AppForgePtyProcess {
        require(
            executable.isNotBlank() &&
                workingDirectory.isNotBlank()
        ) {
            "PTY çalıştırma yolu geçersiz."
        }

        val safeRows =
            rows.coerceIn(
                2,
                1_000
            )

        val safeColumns =
            columns.coerceIn(
                10,
                2_000
            )

        val environmentEntries =
            environment
                .entries
                .sortedBy {
                    it.key
                }
                .map { entry ->
                    require(
                        SAFE_ENV_NAME.matches(
                            entry.key
                        ) &&
                            '\u0000' !in
                            entry.value &&
                            '\n' !in
                            entry.key &&
                            '\r' !in
                            entry.key
                    ) {
                        "PTY ortam değişkeni geçersiz."
                    }

                    "${entry.key}=${entry.value}"
                }
                .toTypedArray()

        val result =
            nativeSpawn(
                executable = executable,
                arguments =
                    arguments.toTypedArray(),
                environment =
                    environmentEntries,
                workingDirectory =
                    workingDirectory,
                rows = safeRows,
                columns = safeColumns
            )

        check(
            result.size == 4 &&
                result.all {
                    it >= 0
                } &&
                result[0] > 0
        ) {
            "PTY native motoru geçersiz sonuç döndürdü."
        }

        return AppForgePtyProcess(
            processId = result[0],
            inputFd = result[1],
            outputFd = result[2],
            controlFd = result[3]
        )
    }

    fun resize(
        controlFd: Int,
        rows: Int,
        columns: Int
    ): Boolean =
        nativeResize(
            controlFd = controlFd,
            rows =
                rows.coerceIn(
                    2,
                    1_000
                ),
            columns =
                columns.coerceIn(
                    10,
                    2_000
                )
        )

    fun waitFor(
        processId: Int
    ): Int =
        nativeWait(
            processId
        )

    fun terminate(
        processId: Int
    ) {
        nativeTerminate(
            processId
        )
    }

    private external fun nativeSpawn(
        executable: String,
        arguments: Array<String>,
        environment: Array<String>,
        workingDirectory: String,
        rows: Int,
        columns: Int
    ): IntArray

    private external fun nativeResize(
        controlFd: Int,
        rows: Int,
        columns: Int
    ): Boolean

    private external fun nativeWait(
        processId: Int
    ): Int

    private external fun nativeTerminate(
        processId: Int
    )

    private val SAFE_ENV_NAME =
        Regex(
            "^[A-Z_][A-Z0-9_]{0,63}$"
        )
}
