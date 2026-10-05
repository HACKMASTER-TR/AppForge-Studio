package com.hackmaster.videoforge

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaFormat
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
        "videoforge-media-source-v3"

    private const val CACHE_MAX_AGE_MS =
        24L * 60L * 60L * 1000L

    internal data class ProcessingProbe(
        val durationSeconds: Double,
        val hasAudio: Boolean,
        val hasVideo: Boolean
    )

    /*
     * VIDEOFORGE_EXTRACTOR_PREFLIGHT_V1_2
     *
     * MediaExtractor is the parser used by the real decode/mux pipeline.
     * MediaMetadataRetriever is optional metadata tooling and must not be a
     * hard preflight dependency.
     */
    fun probeForProcessing(
        context: Context,
        uri: Uri
    ): ProcessingProbe {
        val extractor =
            openExtractor(
                context,
                uri
            )

        try {
            var hasAudio =
                false

            var hasVideo =
                false

            var durationUs =
                0L

            for (
                index in 0 until
                    extractor.trackCount
            ) {
                val format =
                    extractor.getTrackFormat(
                        index
                    )

                val mime =
                    format.getString(
                        MediaFormat.KEY_MIME
                    )
                        .orEmpty()

                if (
                    mime.startsWith(
                        "audio/"
                    )
                ) {
                    hasAudio =
                        true
                }

                if (
                    mime.startsWith(
                        "video/"
                    )
                ) {
                    hasVideo =
                        true
                }

                if (
                    format.containsKey(
                        MediaFormat.KEY_DURATION
                    )
                ) {
                    val trackDuration =
                        runCatching {
                            format.getLong(
                                MediaFormat.KEY_DURATION
                            )
                        }
                            .getOrDefault(
                                0L
                            )

                    durationUs =
                        maxOf(
                            durationUs,
                            trackDuration
                        )
                }
            }

            require(
                hasVideo
            ) {
                "Videoda görüntü parçası bulunamadı."
            }

            require(
                hasAudio
            ) {
                "Videoda ses parçası bulunamadı."
            }

            return ProcessingProbe(
                durationSeconds =
                    durationUs
                        .coerceAtLeast(
                            0L
                        ) /
                        1_000_000.0,
                hasAudio =
                    true,
                hasVideo =
                    true
            )
        } finally {
            extractor.release()
        }
    }

    fun openExtractor(
        context: Context,
        uri: Uri
    ): MediaExtractor {

        localFile(
            uri
        )?.let {
            file ->

            val local =
                MediaExtractor()

            try {
                local.setDataSource(
                    file.absolutePath
                )

                return local
            } catch (
                error: Throwable
            ) {
                runCatching {
                    local.release()
                }

                throw IOException(
                    "Yerel video MediaExtractor tarafından açılamadı. " +
                        "Alt neden: ${safeMediaError(error)}",
                    error
                )
            }
        }

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
                            "yerel güvenli kopyadan açılamadı. " +
                            "Alt neden: ${safeMediaError(localError)}",
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

        localFile(
            uri
        )?.let {
            file ->

            val local =
                MediaMetadataRetriever()

            try {
                local.setDataSource(
                    file.absolutePath
                )

                return local
            } catch (
                error: Throwable
            ) {
                runCatching {
                    local.release()
                }

                throw IOException(
                    "Yerel video MediaMetadataRetriever tarafından açılamadı. " +
                        "Alt neden: ${safeMediaError(error)}",
                    error
                )
            }
        }

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
                            "yerel güvenli kopyadan açılamadı. " +
                            "Alt neden: ${safeMediaError(localError)}",
                        localError
                    )
                }
            }
        }
    }

    /*
     * VIDEOFORGE_STABLE_LOCAL_SOURCE_V1_1
     *
     * Used for one automatic retry when a provider URI cannot be consumed
     * by Android's native media layer. The copy remains inside app-private
     * cache and is returned as file:// for the existing media pipeline.
     */
    fun materializeForProcessing(
        context: Context,
        uri: Uri
    ): Uri {
        if (
            uri.scheme.equals(
                "file",
                ignoreCase = true
            )
        ) {
            val existing =
                uri.path
                    ?.let(
                        ::File
                    )

            if (
                existing != null &&
                existing.isFile &&
                existing.length() > 0L
            ) {
                return uri
            }
        }

        val file =
            materializeLocalCopy(
                context,
                uri
            )

        if (
            !file.isFile ||
            file.length() <= 0L
        ) {
            throw IOException(
                "VideoForge yerel medya kopyası doğrulanamadı."
            )
        }

        return Uri.fromFile(
            file
        )
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

        localFile(
            uri
        )?.let {
            return it
        }

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

        val part =
            File(
                directory,
                "$key.part"
            )

        runCatching {
            part.delete()
        }

        /*
         * VIDEOFORGE_LOCAL_COPY_INTEGRITY_V1_2
         *
         * Hash the exact bytes read from the selected provider while writing
         * the .part file, then hash the final app-private file again. A local
         * processing source is accepted only when byte count and SHA-256
         * match the stream that was actually read.
         */
        try {
            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            var copiedBytes =
                0L

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

                            val buffer =
                                ByteArray(
                                    1024 * 1024
                                )

                            while (
                                true
                            ) {
                                val read =
                                    source.read(
                                        buffer
                                    )

                                if (
                                    read < 0
                                ) {
                                    break
                                }

                                if (
                                    read == 0
                                ) {
                                    continue
                                }

                                digest.update(
                                    buffer,
                                    0,
                                    read
                                )

                                output.write(
                                    buffer,
                                    0,
                                    read
                                )

                                copiedBytes +=
                                    read
                            }

                            output.flush()
                        }
                }

            if (
                !part.isFile ||
                copiedBytes <= 0L ||
                part.length() !=
                    copiedBytes
            ) {
                throw IOException(
                    "Seçilen videonun yerel kopyası eksik veya boş."
                )
            }

            val sourceStreamHash =
                hex(
                    digest.digest()
                )

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
                target.length() !=
                    copiedBytes
            ) {
                throw IOException(
                    "Yerel VideoForge medya kopyası byte doğrulamasını geçemedi."
                )
            }

            val localHash =
                sha256(
                    target
                )

            if (
                localHash !=
                    sourceStreamHash
            ) {
                runCatching {
                    target.delete()
                }

                throw IOException(
                    "Yerel VideoForge medya kopyası SHA-256 doğrulamasını geçemedi."
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

    private fun safeMediaError(
        error: Throwable
    ): String {
        val messages =
            mutableListOf<String>()

        var current:
            Throwable? =
            error

        var depth =
            0

        while (
            current != null &&
            depth < 4
        ) {
            current.message
                ?.trim()
                ?.replace(
                    '\n',
                    ' '
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    if (
                        it !in messages
                    ) {
                        messages +=
                            it
                    }
                }

            current =
                current.cause

            depth +=
                1
        }

        return messages
            .take(
                3
            )
            .joinToString(
                " | "
            )
            .take(
                420
            )
            .ifBlank {
                "medya kaynağı okunamadı"
            }
    }

    private fun localFile(
        uri: Uri
    ): File? {
        if (
            !uri.scheme.equals(
                "file",
                ignoreCase = true
            )
        ) {
            return null
        }

        return uri.path
            ?.let(
                ::File
            )
            ?.takeIf {
                it.isFile &&
                    it.length() >
                    0L
            }
    }

    private fun sha256(
        file: File
    ): String {
        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        file.inputStream()
            .buffered(
                1024 * 1024
            )
            .use {
                input ->

                val buffer =
                    ByteArray(
                        1024 * 1024
                    )

                while (
                    true
                ) {
                    val read =
                        input.read(
                            buffer
                        )

                    if (
                        read < 0
                    ) {
                        break
                    }

                    if (
                        read > 0
                    ) {
                        digest.update(
                            buffer,
                            0,
                            read
                        )
                    }
                }
            }

        return hex(
            digest.digest()
        )
    }

    private fun hex(
        bytes: ByteArray
    ): String =
        bytes.joinToString(
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
