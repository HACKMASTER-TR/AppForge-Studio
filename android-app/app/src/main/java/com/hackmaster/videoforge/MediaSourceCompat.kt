package com.hackmaster.videoforge

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.IOException

/**
 * Opens Android media sources defensively.
 *
 * Some Storage Access Framework/content providers expose content://
 * URIs which fail through the framework's direct URI data-source path
 * but work correctly through an AssetFileDescriptor.
 *
 * Always try the standard Android URI path first. If that fails,
 * retry using the provider's real file descriptor + offset/length.
 */
internal object MediaSourceCompat {

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

            val fallback =
                MediaExtractor()

            try {
                context.contentResolver
                    .openAssetFileDescriptor(
                        uri,
                        "r"
                    )
                    ?.use {
                        afd ->

                        val length =
                            afd.declaredLength

                        if (
                            length >=
                            0L
                        ) {
                            fallback.setDataSource(
                                afd.fileDescriptor,
                                afd.startOffset,
                                length
                            )
                        } else {
                            fallback.setDataSource(
                                afd.fileDescriptor
                            )
                        }
                    }
                    ?: throw IOException(
                        "Seçilen video için dosya tanımlayıcısı açılamadı."
                    )

                return fallback

            } catch (
                fallbackError: Throwable
            ) {
                runCatching {
                    fallback.release()
                }

                fallbackError.addSuppressed(
                    directError
                )

                throw IOException(
                    "Seçilen video Android medya motoru tarafından açılamadı. " +
                        "Dosya bozuk, eksik veya desteklenmeyen bir medya biçiminde olabilir.",
                    fallbackError
                )
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

            val fallback =
                MediaMetadataRetriever()

            try {
                context.contentResolver
                    .openAssetFileDescriptor(
                        uri,
                        "r"
                    )
                    ?.use {
                        afd ->

                        val length =
                            afd.declaredLength

                        if (
                            length >=
                            0L
                        ) {
                            fallback.setDataSource(
                                afd.fileDescriptor,
                                afd.startOffset,
                                length
                            )
                        } else {
                            fallback.setDataSource(
                                afd.fileDescriptor
                            )
                        }
                    }
                    ?: throw IOException(
                        "Seçilen video için dosya tanımlayıcısı açılamadı."
                    )

                return fallback

            } catch (
                fallbackError: Throwable
            ) {
                runCatching {
                    fallback.release()
                }

                fallbackError.addSuppressed(
                    directError
                )

                throw IOException(
                    "Seçilen video Android medya motoru tarafından açılamadı. " +
                        "Dosya bozuk, eksik veya desteklenmeyen bir medya biçiminde olabilir.",
                    fallbackError
                )
            }
        }
    }
}
