package com.appforge.studio.terminal

import java.io.File

internal const val APPFORGE_CWD_PROMPT_COMMAND =
    "printf '\\033]777;appforge-cwd=%s\\007' \"\$PWD\""

private const val APPFORGE_CWD_OSC_PREFIX =
    "\u001b]777;appforge-cwd="

private const val APPFORGE_CWD_MAX_PATH =
    4_096


internal class TerminalWorkingDirectoryTracker {
    private val pending =
        StringBuilder()

    fun reset() {
        pending.clear()
    }

    fun feed(
        chunk: String
    ): String? {
        if (chunk.isEmpty()) {
            return null
        }

        pending.append(
            chunk
        )

        var latest:
            String? = null

        while (true) {
            val text =
                pending.toString()

            val start =
                text.indexOf(
                    APPFORGE_CWD_OSC_PREFIX
                )

            if (start < 0) {
                keepPrefixTail()
                break
            }

            if (start > 0) {
                pending.delete(
                    0,
                    start
                )
            }

            val current =
                pending.toString()

            val end =
                current.indexOf(
                    '\u0007',
                    APPFORGE_CWD_OSC_PREFIX.length
                )

            if (end < 0) {
                if (
                    pending.length >
                        APPFORGE_CWD_OSC_PREFIX.length +
                        APPFORGE_CWD_MAX_PATH +
                        1
                ) {
                    pending.clear()
                }

                break
            }

            val path =
                current.substring(
                    APPFORGE_CWD_OSC_PREFIX.length,
                    end
                )

            pending.delete(
                0,
                end + 1
            )

            if (
                path.startsWith("/") &&
                path.length <=
                    APPFORGE_CWD_MAX_PATH &&
                path.none {
                    it.code < 0x20 ||
                        it == '\u007f'
                }
            ) {
                latest =
                    path
            }
        }

        return latest
    }

    private fun keepPrefixTail() {
        val keep =
            (
                APPFORGE_CWD_OSC_PREFIX.length -
                    1
                )
                .coerceAtLeast(
                    0
                )

        if (
            pending.length >
                keep
        ) {
            pending.delete(
                0,
                pending.length -
                    keep
            )
        }
    }
}


internal object TerminalWorkingDirectoryResolver {

    fun resolveLinuxGuestDirectory(
        rootfs: File,
        workspace: File,
        guestPath: String
    ): File? {
        if (
            guestPath.isBlank() ||
            !guestPath.startsWith("/")
        ) {
            return null
        }

        val safeRootfs =
            runCatching {
                rootfs.canonicalFile
            }.getOrNull()
                ?: return null

        val safeWorkspace =
            runCatching {
                workspace.canonicalFile
            }.getOrNull()
                ?: return null

        val candidate =
            when {
                guestPath ==
                    "/workspace" ->
                    safeWorkspace

                guestPath.startsWith(
                    "/workspace/"
                ) -> {
                    val relative =
                        guestPath.removePrefix(
                            "/workspace/"
                        )

                    runCatching {
                        File(
                            safeWorkspace,
                            relative
                        ).canonicalFile
                    }.getOrNull()
                        ?.takeIf {
                            inside(
                                safeWorkspace,
                                it
                            )
                        }
                }

                else -> {
                    val relative =
                        guestPath.removePrefix(
                            "/"
                        )

                    runCatching {
                        File(
                            safeRootfs,
                            relative
                        ).canonicalFile
                    }.getOrNull()
                        ?.takeIf {
                            inside(
                                safeRootfs,
                                it
                            )
                        }
                }
            }
                ?: return null

        return candidate.takeIf {
            it.isDirectory &&
                it.canRead()
        }
    }

    fun findGitRoot(
        start: File
    ): File? {
        var current =
            runCatching {
                start.canonicalFile
            }.getOrNull()
                ?: return null

        repeat(64) {
            if (
                File(
                    current,
                    ".git"
                ).exists()
            ) {
                return current
            }

            current =
                current.parentFile
                    ?: return null
        }

        return null
    }

    private fun inside(
        root: File,
        candidate: File
    ): Boolean =
        candidate ==
            root ||
            candidate.path.startsWith(
                root.path +
                    File.separator
            )
}
