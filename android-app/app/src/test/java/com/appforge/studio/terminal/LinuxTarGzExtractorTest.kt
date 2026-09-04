package com.appforge.studio.terminal

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LinuxTarGzExtractorTest {
    @Test
    fun extractsRegularRootfsFilesInsideDestination() {
        withTempDirectory { root ->
            val archive =
                File(
                    root,
                    "rootfs.tar.gz"
                )

            writeArchive(
                archive,
                listOf(
                    "etc/os-release" to
                        "ID=ubuntu\n",
                    "usr/bin/appforge-test" to
                        "ok\n",
                    "bin/sh" to
                        "shell\n"
                )
            )

            val destination =
                File(root, "rootfs")

            LinuxTarGzExtractor.extract(
                archive,
                destination
            )

            assertEquals(
                "ID=ubuntu\n",
                File(
                    destination,
                    "etc/os-release"
                ).readText()
            )

            assertEquals(
                "ok\n",
                File(
                    destination,
                    "usr/bin/appforge-test"
                ).readText()
            )
        }
    }

    @Test
    fun rejectsPathTraversalEntry() {
        withTempDirectory { root ->
            val archive =
                File(
                    root,
                    "escape.tar.gz"
                )

            GzipCompressorOutputStream(
                archive.outputStream()
            ).use { gzip ->
                TarArchiveOutputStream(gzip).use { tar ->
                    val payload =
                        "blocked".toByteArray()

                    val entry =
                        TarArchiveEntry(
                            "../../escaped.txt"
                        ).apply {
                            size = payload.size.toLong()
                        }

                    tar.putArchiveEntry(entry)
                    tar.write(payload)
                    tar.closeArchiveEntry()
                    tar.finish()
                }
            }

            val destination =
                File(root, "rootfs")

            val result =
                runCatching {
                    LinuxTarGzExtractor.extract(
                        archive,
                        destination
                    )
                }

            assertTrue(result.isFailure)
            assertFalse(
                File(
                    root.parentFile,
                    "escaped.txt"
                ).exists()
            )
        }
    }

    private fun writeArchive(
        archive: File,
        files: List<Pair<String, String>>
    ) {
        GzipCompressorOutputStream(
            archive.outputStream()
        ).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                for ((name, content) in files) {
                    val parentParts =
                        name.substringBeforeLast(
                            '/',
                            ""
                        )

                    if (parentParts.isNotBlank()) {
                        var current = ""

                        for (part in parentParts.split('/')) {
                            current =
                                if (current.isBlank()) {
                                    part
                                } else {
                                    "$current/$part"
                                }

                            val dir =
                                TarArchiveEntry(
                                    "$current/"
                                ).apply {
                                    mode = 493 // 0755
                                }

                            runCatching {
                                tar.putArchiveEntry(dir)
                                tar.closeArchiveEntry()
                            }
                        }
                    }

                    val payload =
                        content.toByteArray()

                    val entry =
                        TarArchiveEntry(name).apply {
                            size = payload.size.toLong()
                            mode = 420 // 0644
                        }

                    tar.putArchiveEntry(entry)
                    tar.write(payload)
                    tar.closeArchiveEntry()
                }

                tar.finish()
            }
        }
    }

    private fun withTempDirectory(
        block: (File) -> Unit
    ) {
        val directory =
            Files.createTempDirectory(
                "appforge-linux-extract"
            ).toFile()

        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
