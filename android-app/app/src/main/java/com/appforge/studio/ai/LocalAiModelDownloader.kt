package com.appforge.studio.ai

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object LocalAiModelDownloader {

    const val MODEL_TITLE =
        "Qwen3-0.6B"

    const val MODEL_FILE =
        "Qwen3-0.6B.litertlm"

    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true"

    private const val EXPECTED_SIZE =
        614236160L

    private const val EXPECTED_SHA256 =
        "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4"

    suspend fun install(
        context: Context,
        onProgress: (Int) -> Unit
    ): LocalAiModelInfo =
        withContext(Dispatchers.IO) {

            val available =
                StatFs(
                    context.filesDir.absolutePath
                ).availableBytes

            require(
                available >
                    EXPECTED_SIZE +
                    700L * 1024L * 1024L
            ) {
                "Yerel AI kurulumu için yeterli boş alan yok. En az yaklaşık 1.3 GB boş alan bırak."
            }

            val downloadDir =
                File(
                    context.cacheDir,
                    "local-ai-download"
                ).apply {
                    mkdirs()
                }

            val temp =
                File(
                    downloadDir,
                    "$MODEL_FILE.part"
                )

            var existing =
                temp
                    .takeIf {
                        it.exists()
                    }
                    ?.length()
                    ?: 0L

            if (
                existing < 0L ||
                existing >= EXPECTED_SIZE
            ) {
                temp.delete()
                existing = 0L
            }

            val connection =
                URL(MODEL_URL)
                    .openConnection() as
                    HttpURLConnection

            connection.instanceFollowRedirects =
                true

            connection.connectTimeout =
                30_000

            connection.readTimeout =
                60_000

            connection.setRequestProperty(
                "User-Agent",
                "AppForge-Studio/3.0"
            )

            connection.setRequestProperty(
                "Accept",
                "application/octet-stream"
            )

            if (existing > 0L) {
                connection.setRequestProperty(
                    "Range",
                    "bytes=$existing-"
                )
            }

            try {
                val responseCode =
                    connection.responseCode

                require(
                    responseCode in
                        200..299
                ) {
                    "Model sunucusu HTTP $responseCode döndürdü."
                }

                val resume =
                    responseCode ==
                        HttpURLConnection.HTTP_PARTIAL &&
                    existing > 0L

                if (!resume) {
                    temp.delete()
                    existing = 0L
                }

                var downloaded =
                    existing

                var lastProgress =
                    -1

                connection.inputStream
                    .buffered(
                        1024 * 1024
                    )
                    .use { input ->

                        FileOutputStream(
                            temp,
                            resume
                        )
                            .buffered(
                                1024 * 1024
                            )
                            .use { output ->

                                val buffer =
                                    ByteArray(
                                        1024 * 1024
                                    )

                                while (true) {
                                    val count =
                                        input.read(
                                            buffer
                                        )

                                    if (count < 0) {
                                        break
                                    }

                                    output.write(
                                        buffer,
                                        0,
                                        count
                                    )

                                    downloaded +=
                                        count

                                    val progress =
                                        (
                                            downloaded *
                                                100L /
                                                EXPECTED_SIZE
                                            )
                                            .toInt()
                                            .coerceIn(
                                                0,
                                                100
                                            )

                                    if (
                                        progress !=
                                        lastProgress
                                    ) {
                                        lastProgress =
                                            progress

                                        withContext(
                                            Dispatchers.Main
                                        ) {
                                            onProgress(
                                                progress
                                            )
                                        }
                                    }
                                }

                                output.flush()
                            }
                    }
            } finally {
                connection.disconnect()
            }

            require(
                temp.length() ==
                    EXPECTED_SIZE
            ) {
                "Model indirmesi tamamlanmadı. Beklenen ${EXPECTED_SIZE} byte, gelen ${temp.length()} byte."
            }

            withContext(
                Dispatchers.Main
            ) {
                onProgress(100)
            }

            val sha =
                sha256(
                    temp
                )

            require(
                sha.equals(
                    EXPECTED_SHA256,
                    ignoreCase = true
                )
            ) {
                temp.delete()

                "Model doğrulaması başarısız. Dosya güvenlik nedeniyle silindi."
            }

            LocalAiModelStore
                .installDownloadedModel(
                    context = context,
                    source = temp,
                    displayName =
                        MODEL_TITLE,
                    sha256 = sha
                )
        }

    private fun sha256(
        file: File
    ): String {

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )

        FileInputStream(
            file
        )
            .buffered(
                1024 * 1024
            )
            .use { input ->

                val buffer =
                    ByteArray(
                        1024 * 1024
                    )

                while (true) {
                    val read =
                        input.read(
                            buffer
                        )

                    if (read < 0) {
                        break
                    }

                    digest.update(
                        buffer,
                        0,
                        read
                    )
                }
            }

        return digest
            .digest()
            .joinToString(
                ""
            ) {
                "%02x".format(
                    it
                )
            }
    }
}
