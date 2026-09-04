package com.appforge.studio.terminal

import java.io.File

internal data class ProrootPackagedAsset(
    val name: String,
    val size: Long,
    val sha256: String
)

internal object ProrootPinnedRuntime {
    const val version =
        "v1.2.8"

    const val supportedAbi =
        "arm64-v8a"

    val assets =
        listOf(
            ProrootPackagedAsset(
                name = "libproroot.so",
                size = 43_624L,
                sha256 = "a4e74d75b66cdc02b080adfe863dbf9951c3b30610d77beddc95488d5fe5de01"
            ),
            ProrootPackagedAsset(
                name = "libproroot-runtime.so",
                size = 375_120L,
                sha256 = "8c47a0a7db32d84c179ebb5bf3640f655a3181860ece5886ae44d92858730c34"
            ),
            ProrootPackagedAsset(
                name = "libproroot-bridge.so",
                size = 23_624L,
                sha256 = "1c5bc9537a270e8bf8b1c70222813f57b60b828bfb5503ddf8fe37685092de2f"
            ),
            ProrootPackagedAsset(
                name = "libproroot-linker.so",
                size = 79_408L,
                sha256 = "51a0ec5bfed00e572a0de09e22d9057e2befc386b78e426613d3e0ab03f4ecee"
            ),
            ProrootPackagedAsset(
                name = "libproroot-stub-loader.so",
                size = 144_144L,
                sha256 = "06c6624db3bdc45b9ced151cd781df439a37b47731d244b93e9d6a58cd48cde0"
            )
        )

    val launcherName =
        "libproroot.so"

    fun buildShellArguments(
        rootfs: File,
        workspace: File,
        command: String
    ): List<String> {
        val safeRootfs =
            rootfs.canonicalFile

        val safeWorkspace =
            workspace.canonicalFile

        require(
            safeRootfs.isDirectory &&
                File(
                    safeRootfs,
                    "bin/sh"
                ).isFile
        ) {
            "Linux rootfs hazır değil."
        }

        require(
            safeWorkspace.isDirectory &&
                safeWorkspace.canRead()
        ) {
            "Çalışma alanı okunamıyor."
        }

        require(
            command.isNotBlank() &&
                command.length <=
                    16 * 1_024 &&
                '\u0000' !in command
        ) {
            "Linux komutu geçersiz."
        }

        return listOf(
            "-r",
            safeRootfs.absolutePath,
            "-0",
            "--link2symlink",
            "-b",
            "/dev",
            "-b",
            "/proc",
            "-b",
            "${safeWorkspace.absolutePath}:/workspace",
            "-w",
            "/workspace",
            "/bin/sh",
            "-lc",
            command
        )
    }

    fun buildInteractiveShellArguments(
        rootfs: File,
        workspace: File
    ): List<String> {
        val safeRootfs =
            rootfs.canonicalFile

        val safeWorkspace =
            workspace.canonicalFile

        require(
            safeRootfs.isDirectory &&
                File(
                    safeRootfs,
                    "bin/sh"
                ).isFile
        ) {
            "Linux rootfs hazır değil."
        }

        require(
            safeWorkspace.isDirectory &&
                safeWorkspace.canRead()
        ) {
            "Çalışma alanı okunamıyor."
        }

        val bash =
            File(
                safeRootfs,
                "bin/bash"
            )

        val shell =
            if (bash.isFile) {
                "/bin/bash"
            } else {
                "/bin/sh"
            }

        return listOf(
            "-r",
            safeRootfs.absolutePath,
            "-0",
            "--link2symlink",
            "-b",
            "/dev",
            "-b",
            "/proc",
            "-b",
            "${safeWorkspace.absolutePath}:/workspace",
            "-w",
            "/workspace",
            shell,
            "-l"
        )
    }

}
