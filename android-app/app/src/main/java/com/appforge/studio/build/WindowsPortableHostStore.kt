package com.appforge.studio.build

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object WindowsPortableHostStore {

    const val REVISION =
        "windows-host-v1-a8c5323"

    const val HOST_URL =
        "https://github.com/HACKMASTER-TR/AppForge-Studio/" +
            "releases/download/windows-host-v1-a8c5323/" +
            "AppForge-Windows-Host-v1.exe"

    const val HOST_SHA256 =
        "699e5e13a157b9e436f8c19d0d6bca6264b510a71939a741d756078148d56c03"

    const val HOST_BYTES =
        375_025_483L

    private const val DIRECTORY =
        "offline-build-pack/windows-host-v1"

    private const val HOST_NAME =
        "AppForge-Windows-Host-v1.exe"

    private const val PART_NAME =
        "AppForge-Windows-Host-v1.exe.part"

    private const val MARKER_NAME =
        "windows-host.ready"


    fun isInstalled(
        context: Context
    ): Boolean {

        val directory =
            hostDirectory(
                context.applicationContext
            )

        val host =
            File(
                directory,
                HOST_NAME
            )

        val marker =
            File(
                directory,
                MARKER_NAME
            )

        if (
            !host.isFile ||
            host.length() !=
                HOST_BYTES ||
            !marker.isFile ||
            marker.length() >
                512L
        ) {
            return false
        }

        return runCatching {
            marker.readText(
                Charsets.UTF_8
            )
        }.getOrNull() ==
            expectedMarker()
    }


    fun requireVerifiedHost(
        context: Context
    ): File {

        val appContext =
            context.applicationContext

        val host =
            File(
                hostDirectory(
                    appContext
                ),
                HOST_NAME
            )

        require(
            isInstalled(
                appContext
            )
        ) {
            "Windows Portable Host cihazda kurulu değil."
        }

        require(
            sha256(
                host
            ) ==
                HOST_SHA256
        ) {
            "Windows Portable Host SHA-256 doğrulaması başarısız."
        }

        return host
    }


    suspend fun install(
        context: Context,
        onProgress: (String) -> Unit = {}
    ): File =
        withContext(
            Dispatchers.IO
        ) {
            val appContext =
                context.applicationContext

            val directory =
                hostDirectory(
                    appContext
                ).apply {
                    mkdirs()
                }

            val host =
                File(
                    directory,
                    HOST_NAME
                )

            val part =
                File(
                    directory,
                    PART_NAME
                )

            val marker =
                File(
                    directory,
                    MARKER_NAME
                )

            if (
                isInstalled(
                    appContext
                )
            ) {
                onProgress(
                    "4/4 • Windows Portable Host zaten doğrulanmış."
                )

                return@withContext host
            }

            /*
             * Uygulama daha önce final host'u yazmış ancak marker
             * yazılmadan kapanmış olabilir. Büyük dosyayı tekrar
             * indirmeden önce SHA-256 ile kurtarmayı dene.
             */
            if (
                host.isFile &&
                host.length() ==
                    HOST_BYTES
            ) {
                onProgress(
                    "4/4 • Mevcut Windows Host doğrulanıyor..."
                )

                if (
                    sha256(
                        host
                    ) ==
                        HOST_SHA256
                ) {
                    writeMarker(
                        marker
                    )

                    return@withContext host
                }

                host.delete()
                marker.delete()
            } else if (
                host.exists()
            ) {
                host.delete()
                marker.delete()
            }

            if (
                part.exists() &&
                part.length() >
                    HOST_BYTES
            ) {
                part.delete()
            }

            /*
             * İndirme tamamlanmış fakat promote aşamasında uygulama
             * kapanmışsa .part dosyasını da SHA-256 ile kurtar.
             */
            if (
                part.isFile &&
                part.length() ==
                    HOST_BYTES
            ) {
                onProgress(
                    "4/4 • İndirilen Windows Host doğrulanıyor..."
                )

                if (
                    sha256(
                        part
                    ) ==
                        HOST_SHA256
                ) {
                    promoteVerifiedPart(
                        part,
                        host,
                        marker
                    )

                    return@withContext host
                }

                part.delete()
            }

            download(
                part = part,
                onProgress = onProgress
            )

            require(
                part.length() ==
                    HOST_BYTES
            ) {
                "Windows Portable Host boyutu beklenen değerle eşleşmiyor: " +
                    "${part.length()} / $HOST_BYTES"
            }

            onProgress(
                "4/4 • Windows Host SHA-256 doğrulanıyor..."
            )

            val digest =
                sha256(
                    part
                )

            if (
                digest !=
                HOST_SHA256
            ) {
                part.delete()

                error(
                    "Windows Portable Host SHA-256 doğrulaması başarısız. " +
                        "Beklenen: $HOST_SHA256 • Gelen: $digest"
                )
            }

            promoteVerifiedPart(
                part,
                host,
                marker
            )

            require(
                isInstalled(
                    appContext
                )
            ) {
                "Windows Portable Host kurulum işareti doğrulanamadı."
            }

            onProgress(
                "4/4 • Windows Portable Host cihazda doğrulandı."
            )

            host
        }


    private fun download(
        part: File,
        onProgress: (String) -> Unit
    ) {
        val source =
            URL(
                HOST_URL
            )

        require(
            source.protocol
                .equals(
                    "https",
                    ignoreCase =
                        true
                )
        ) {
            "Windows Host yalnız HTTPS üzerinden indirilebilir."
        }

        var resumeBytes =
            part
                .takeIf {
                    it.isFile
                }
                ?.length()
                ?: 0L

        if (
            resumeBytes <
                0L ||
            resumeBytes >
                HOST_BYTES
        ) {
            part.delete()
            resumeBytes =
                0L
        }

        onProgress(
            progressText(
                resumeBytes
            )
        )

        val connection =
            (
                source
                    .openConnection() as
                    HttpURLConnection
                ).apply {

                    instanceFollowRedirects =
                        true

                    connectTimeout =
                        30_000

                    readTimeout =
                        120_000

                    requestMethod =
                        "GET"

                    setRequestProperty(
                        "Accept",
                        "application/octet-stream"
                    )

                    setRequestProperty(
                        "User-Agent",
                        "AppForge-Studio-WindowsHost/1"
                    )

                    if (
                        resumeBytes >
                            0L
                    ) {
                        setRequestProperty(
                            "Range",
                            "bytes=$resumeBytes-"
                        )
                    }
                }

        try {
            val response =
                connection
                    .responseCode

            val append =
                when {
                    resumeBytes >
                        0L &&
                    response ==
                        HttpURLConnection.HTTP_PARTIAL ->
                        true

                    response ==
                        HttpURLConnection.HTTP_OK ->
                        false

                    else ->
                        error(
                            "Windows Host indirme HTTP hatası: $response"
                        )
                }

            if (
                !append
            ) {
                resumeBytes =
                    0L
            }

            part.parentFile
                ?.mkdirs()

            connection
                .inputStream
                .buffered(
                    256 * 1024
                )
                .use {
                    input ->

                    FileOutputStream(
                        part,
                        append
                    ).use {
                        output ->

                        val buffer =
                            ByteArray(
                                256 * 1024
                            )

                        var total =
                            resumeBytes

                        var lastReported =
                            total

                        while (
                            true
                        ) {
                            val read =
                                input.read(
                                    buffer
                                )

                            if (
                                read <
                                    0
                            ) {
                                break
                            }

                            if (
                                read ==
                                    0
                            ) {
                                continue
                            }

                            total +=
                                read.toLong()

                            require(
                                total <=
                                    HOST_BYTES
                            ) {
                                "Windows Host beklenen boyutu aştı."
                            }

                            output.write(
                                buffer,
                                0,
                                read
                            )

                            if (
                                total -
                                    lastReported >=
                                    8L *
                                    1024L *
                                    1024L
                            ) {
                                onProgress(
                                    progressText(
                                        total
                                    )
                                )

                                lastReported =
                                    total
                            }
                        }

                        output.fd.sync()
                    }
                }

            onProgress(
                progressText(
                    part.length()
                )
            )

        } finally {
            connection.disconnect()
        }
    }


    private fun promoteVerifiedPart(
        part: File,
        host: File,
        marker: File
    ) {
        marker.delete()

        if (
            host.exists()
        ) {
            require(
                host.delete()
            ) {
                "Eski Windows Host silinemedi."
            }
        }

        require(
            part.renameTo(
                host
            )
        ) {
            "Doğrulanmış Windows Host final konuma taşınamadı."
        }

        require(
            host.isFile &&
                host.length() ==
                HOST_BYTES
        ) {
            "Windows Host final dosyası geçersiz."
        }

        writeMarker(
            marker
        )
    }


    private fun writeMarker(
        marker: File
    ) {
        marker
            .parentFile
            ?.mkdirs()

        marker.writeText(
            expectedMarker(),
            Charsets.UTF_8
        )
    }


    private fun expectedMarker(): String =
        buildString {
            append(
                REVISION
            )

            append(
                '\n'
            )

            append(
                HOST_SHA256
            )

            append(
                '\n'
            )

            append(
                HOST_BYTES
            )

            append(
                '\n'
            )
        }


    private fun progressText(
        bytes: Long
    ): String {

        val safe =
            bytes
                .coerceIn(
                    0L,
                    HOST_BYTES
                )

        val percent =
            (
                safe *
                    100L /
                    HOST_BYTES
                )
                .coerceIn(
                    0L,
                    100L
                )

        val downloadedMb =
            safe /
                1_000_000L

        val totalMb =
            HOST_BYTES /
                1_000_000L

        return "4/4 • Windows Portable Host indiriliyor... " +
            "$percent% • $downloadedMb / $totalMb MB"
    }


    private fun hostDirectory(
        context: Context
    ): File =
        File(
            context.noBackupFilesDir,
            DIRECTORY
        )


    private fun sha256(
        file: File
    ): String {

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )

        file
            .inputStream()
            .buffered(
                256 * 1024
            )
            .use {
                input ->

                val buffer =
                    ByteArray(
                        256 * 1024
                    )

                while (
                    true
                ) {
                    val read =
                        input.read(
                            buffer
                        )

                    if (
                        read <
                            0
                    ) {
                        break
                    }

                    if (
                        read >
                            0
                    ) {
                        digest.update(
                            buffer,
                            0,
                            read
                        )
                    }
                }
            }

        return digest
            .digest()
            .joinToString(
                separator =
                    ""
            ) {
                byte ->
                "%02x".format(
                    byte
                )
            }
    }
}
