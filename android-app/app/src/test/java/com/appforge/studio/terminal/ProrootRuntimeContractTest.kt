package com.appforge.studio.terminal

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProrootRuntimeContractTest {
    @Test
    fun pinsAllPackagedRuntimeAssets() {
        assertEquals(
            "v1.2.8",
            ProrootPinnedRuntime.version
        )

        assertEquals(
            "arm64-v8a",
            ProrootPinnedRuntime.supportedAbi
        )

        assertEquals(
            5,
            ProrootPinnedRuntime.assets.size
        )

        assertTrue(
            ProrootPinnedRuntime.assets
                .all {
                    it.name.startsWith(
                        "libproroot"
                    ) &&
                        it.name.endsWith(
                            ".so"
                        ) &&
                        it.size > 0L &&
                        it.sha256.length == 64
                }
        )

        assertFalse(
            ProrootPinnedRuntime.assets
                .any {
                    it.sha256
                        .all { ch ->
                            ch == '0'
                        }
                }
        )
    }

    @Test
    fun buildsRootlessShellArgumentsWithoutHostShellInterpolation() {
        val root =
            Files
                .createTempDirectory(
                    "appforge-proroot-test"
                )
                .toFile()

        try {
            val rootfs =
                File(
                    root,
                    "rootfs"
                ).apply {
                    File(
                        this,
                        "bin"
                    ).mkdirs()

                    File(
                        this,
                        "bin/sh"
                    ).writeText(
                        "#!/bin/sh\n"
                    )
                }

            val workspace =
                File(
                    root,
                    "workspace"
                ).apply {
                    mkdirs()
                }

            val command =
                "printf '%s' hello"

            val args =
                ProrootPinnedRuntime
                    .buildShellArguments(
                        rootfs,
                        workspace,
                        command
                    )

            assertEquals(
                command,
                args.last()
            )

            assertTrue(
                "-0" in args
            )

            assertTrue(
                "--link2symlink" in args
            )

            assertTrue(
                "/dev:/dev" in args
            )

            assertTrue(
                "/proc:/proc" in args
            )

            assertFalse(
                "/dev" in args
            )

            assertFalse(
                "/proc" in args
            )

            assertTrue(
                args.contains(
                    "${workspace.canonicalPath}:/workspace"
                )
            )

            assertEquals(
                "/bin/sh",
                args[
                    args.size - 3
                ]
            )

            assertEquals(
                "-lc",
                args[
                    args.size - 2
                ]
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun buildsInteractiveShellArgumentsWithoutHostShell() {
        val root =
            Files
                .createTempDirectory(
                    "appforge-proroot-pty"
                )
                .toFile()

        try {
            val rootfs =
                File(root, "rootfs")
                    .apply {
                        File(this, "bin").mkdirs()
                        File(this, "bin/sh")
                            .writeText("#!/bin/sh\n")
                        File(this, "bin/bash")
                            .writeText("#!/bin/sh\n")
                    }

            val workspace =
                File(root, "workspace")
                    .apply {
                        mkdirs()
                    }

            val args =
                ProrootPinnedRuntime
                    .buildInteractiveShellArguments(
                        rootfs,
                        workspace
                    )

            assertTrue(
                "/dev:/dev" in args
            )

            assertTrue(
                "/proc:/proc" in args
            )

            assertFalse(
                "/dev" in args
            )

            assertFalse(
                "/proc" in args
            )

            assertEquals(
                "/bin/bash",
                args[args.size - 2]
            )
            assertEquals(
                "-l",
                args.last()
            )
            assertFalse(
                args.contains("/system/bin/sh")
            )
        } finally {
            root.deleteRecursively()
        }
    }

}
