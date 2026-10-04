package com.hackmaster.videoforge

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Defensive VideoForge media-source opener.
 *
 * Resolution order:
 *
 * 1. Android Context + Uri API
 * 2. AssetFileDescriptor / real file descriptor
 * 3. App-private cache copy + absolute local file path
 *
 * The final cache fallback is required for content providers which
 * allow stream access but cannot be consumed directly by Android's
 * MediaExtractor / MediaMetadataRetriever native data-source layer.
 */
internal object MediaSourceCompat {

    private const val CACHE_DIRECTORY =
        "videoforge-media-source-v2"

    private const val CACHE_MAX_AGE_MS =
        24L * 60L * 60L * 1000L

    fun openExtractor(
        context: Context,
        uri: Uri
    ): MediaExtractor {

        val direct =
            MediaExtractor()

        try {
            direct.setDataSource(
                context,
                uri,
                null
            )

            return direct

        } catch (
            directError: Throwable
        ) {
            runCatching {
                direct.release()
            }

            val descriptor =
                MediaExtractor()

            try {
                setExtractorFromDescriptor(
                    context,
                    uri,
                    descriptor
                )

                return descriptor

            } catch (
                descriptorError: Throwable
            ) {
                runCatching {
                    descriptor.release()
                }

                val local =
                    MediaExtractor()

                try {
                    val file =
                        materializeLocalCopy(
                            context,
                            uri
                        )

                    local.setDataSource(
                        file.absolutePath
                    )

                    return local

                } catch (
                    localError: Throwable
                ) {
                    runCatching {
                        local.release()
                    }

                    localError.addSuppressed(
                        directError
                    )

                    localError.addSuppressed(
                        descriptorError
                    )

                    throw IOException(
                        "Seçilen video Android medya motoru tarafından " +
                            "doğrudan, dosya tanımlayıcısından veya " +
                            "yerel güvenli kopyadan açılamadı.",
                        localError
                    )
                }
            }
        }
    }

    fun openRetriever(
        context: Context,
        uri: Uri
    ): MediaMetadataRetriever {

        val direct =
            MediaMetadataRetriever()

        try {
            direct.setDataSource(
                context,
                uri
            )

            return direct

        } catch (
            directError: Throwable
        ) {
            runCatching {
                direct.release()
            }

            val descriptor =
                MediaMetadataRetriever()

            try {
                setRetrieverFromDescriptor(
                    context,
                    uri,
                    descriptor
                )

                return descriptor

            } catch (
                descriptorError: Throwable
            ) {
                runCatching {
                    descriptor.release()
                }

                val local =
                    MediaMetadataRetriever()

                try {
                    val file =
                        materializeLocalCopy(
                            context,
                            uri
                        )

                    local.setDataSource(
                        file.absolutePath
                    )

                    return local

                } catch (
                    localError: Throwable
                ) {
                    runCatching {
                        local.release()
                    }

                    localError.addSuppressed(
                        directError
                    )

                    localError.addSuppressed(
                        descriptorError
                    )

                    throw IOException(
                        "Seçilen video Android medya motoru tarafından " +
                            "doğrudan, dosya tanımlayıcısından veya " +
                            "yerel güvenli kopyadan açılamadı.",
                        localError
                    )
                }
            }
        }
    }

    private fun setExtractorFromDescriptor(
        context: Context,
        uri: Uri,
        extractor: MediaExtractor
    ) {
        context.contentResolver
            .openAssetFileDescriptor(
                uri,
                "r"
            )
            ?.use {
                afd ->

                val length =
                    afd.declaredLength

                when {
                    length >= 0L -> {
                        extractor.setDataSource(
                            afd.fileDescriptor,
                            afd.startOffset,
                            length
                        )
                    }

                    afd.startOffset == 0L -> {
                        extractor.setDataSource(
                            afd.fileDescriptor
                        )
                    }

                    else -> {
                        throw IOException(
                            "Medya dosya tanımlayıcısının uzunluğu bilinmiyor."
                        )
                    }
                }
            }
            ?: throw IOException(
                "Seçilen video için dosya tanımlayıcısı açılamadı."
            )
    }

    private fun setRetrieverFromDescriptor(
        context: Context,
        uri: Uri,
        retriever: MediaMetadataRetriever
    ) {
        context.contentResolver
            .openAssetFileDescriptor(
                uri,
                "r"
            )
            ?.use {
                afd ->

                val length =
                    afd.declaredLength

                when {
                    length >= 0L -> {
                        retriever.setDataSource(
                            afd.fileDescriptor,
                            afd.startOffset,
                            length
                        )
                    }

                    afd.startOffset == 0L -> {
                        retriever.setDataSource(
                            afd.fileDescriptor
                        )
                    }

                    else -> {
                        throw IOException(
                            "Medya dosya tanımlayıcısının uzunluğu bilinmiyor."
                        )
                    }
                }
            }
            ?: throw IOException(
                "Seçilen video için dosya tanımlayıcısı açılamadı."
            )
    }

    @Synchronized
    private fun materializeLocalCopy(
        context: Context,
        uri: Uri
    ): File {

        val directory =
            File(
                context.cacheDir,
                CACHE_DIRECTORY
            ).apply {
                if (
                    !exists() &&
                    !mkdirs()
                ) {
                    throw IOException(
                        "VideoForge medya önbelleği oluşturulamadı."
                    )
                }
            }

        cleanupOldCopies(
            directory
        )

        val sourceSize =
            querySourceSize(
                context,
                uri
            )

        val extension =
            querySourceName(
                context,
                uri
            )
                ?.substringAfterLast(
                    '.',
                    ""
                )
                ?.lowercase()
                ?.takeIf {
                    it.matches(
                        Regex(
                            "[a-z0-9]{1,8}"
                        )
                    )
                }
                ?: "media"

        val key =
            sha256(
                uri.toString()
            )
                .take(
                    32
                )

        val target =
            File(
                directory,
                "$key.$extension"
            )

        if (
            target.isFile &&
            target.length() > 0L &&
            (
                sourceSize == null ||
                    sourceSize <= 0L ||
                    target.length() == sourceSize
            )
        ) {
            target.setLastModified(
                System.currentTimeMillis()
            )

            return target
        }

        val part =
            File(
                directory,
                "$key.part"
            )

        runCatching {
            part.delete()
        }

        try {
            val input =
                context.contentResolver
                    .openInputStream(
                        uri
                    )
                    ?: throw IOException(
                        "Seçilen videonun veri akışı açılamadı."
                    )

            input
                .buffered(
                    1024 * 1024
                )
                .use {
                    source ->

                    part.outputStream()
                        .buffered(
                            1024 * 1024
                        )
                        .use {
                            output ->

                            source.copyTo(
                                output,
                                1024 * 1024
                            )
                        }
                }

            if (
                !part.isFile ||
                part.length() <= 0L
            ) {
                throw IOException(
                    "Seçilen videonun yerel kopyası boş."
                )
            }

            if (
                sourceSize != null &&
                sourceSize > 0L &&
                part.length() != sourceSize
            ) {
                throw IOException(
                    "Seçilen videonun yerel kopyası eksik. " +
                        "Beklenen=$sourceSize, alınan=${part.length()}."
                )
            }

            runCatching {
                target.delete()
            }

            if (
                !part.renameTo(
                    target
                )
            ) {
                part.copyTo(
                    target,
                    overwrite = true
                )

                if (
                    !part.delete()
                ) {
                    part.deleteOnExit()
                }
            }

            if (
                !target.isFile ||
                target.length() <= 0L
            ) {
                throw IOException(
                    "Yerel VideoForge medya kopyası doğrulanamadı."
                )
            }

            target.setLastModified(
                System.currentTimeMillis()
            )

            return target

        } catch (
            t: Throwable
        ) {
            runCatching {
                part.delete()
            }

            throw IOException(
                "Video yerel güvenli medya kopyasına alınamadı.",
                t
            )
        }
    }

    private fun querySourceName(
        context: Context,
        uri: Uri
    ): String? =
        runCatching {
            context.contentResolver
                .query(
                    uri,
                    arrayOf(
                        OpenableColumns.DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )
                ?.use {
                    cursor ->

                    if (
                        !cursor.moveToFirst()
                    ) {
                        return@use null
                    }

                    val index =
                        cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        )

                    if (
                        index < 0 ||
                        cursor.isNull(
                            index
                        )
                    ) {
                        null
                    } else {
                        cursor.getString(
                            index
                        )
                    }
                }
        }.getOrNull()

    private fun querySourceSize(
        context: Context,
        uri: Uri
    ): Long? =
        runCatching {
            context.contentResolver
                .query(
                    uri,
                    arrayOf(
                        OpenableColumns.SIZE
                    ),
                    null,
                    null,
                    null
                )
                ?.use {
                    cursor ->

                    if (
                        !cursor.moveToFirst()
                    ) {
                        return@use null
                    }

                    val index =
                        cursor.getColumnIndex(
                            OpenableColumns.SIZE
                        )

                    if (
                        index < 0 ||
                        cursor.isNull(
                            index
                        )
                    ) {
                        null
                    } else {
                        cursor.getLong(
                            index
                        )
                    }
                }
        }.getOrNull()

    private fun sha256(
        value: String
    ): String =
        MessageDigest
            .getInstance(
                "SHA-256"
            )
            .digest(
                value.toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString(
                separator = ""
            ) {
                byte ->
                (
                    byte.toInt() and
                        0xff
                )
                    .toString(
                        16
                    )
                    .padStart(
                        2,
                        '0'
                    )
            }

    private fun cleanupOldCopies(
        directory: File
    ) {
        val cutoff =
            System.currentTimeMillis() -
                CACHE_MAX_AGE_MS

        directory
            .listFiles()
            ?.forEach {
                file ->

                if (
                    file.lastModified() <
                    cutoff
                ) {
                    runCatching {
                        file.delete()
                    }
                }
            }
    }
}
