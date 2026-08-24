package com.appforge.studio.io

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

data class AppForgeExeInfo(
    val cachedExe: File,
    val payloadOffset: Long,
    val payloadLength: Long
)

object AppForgeExeConversion {

    /*
     * Gelecekte AppForge Windows EXE'lerine
     * eklenecek güvenli dönüşüm footer'ı:
     *
     * [AppForge payload ZIP]
     * [8 byte payload length]
     * [16 byte magic]
     */
    private const val MAGIC_TEXT =
        "APPFORGE-EXE-V1!"

    private val MAGIC =
        MAGIC_TEXT.toByteArray(
            Charsets.US_ASCII
        )

    private const val FOOTER_BYTES =
        24L

    private const val MAX_EXE_BYTES =
        1_073_741_824L

    private const val MAX_PAYLOAD_BYTES =
        536_870_912L

    fun inspect(
        context: Context,
        uri: Uri
    ): AppForgeExeInfo {

        val cacheRoot =
            File(
                context.cacheDir,
                "appforge-exe-conversion"
            )

        cacheRoot.mkdirs()

        /*
         * Önceki geçici dönüşüm dosyalarını temizle.
         */
        cacheRoot
            .listFiles()
            ?.forEach {
                runCatching {
                    it.delete()
                }
            }

        val cachedExe =
            File(
                cacheRoot,
                "selected-${System.currentTimeMillis()}.exe"
            )

        try {
            copyExe(
                context,
                uri,
                cachedExe
            )

            return inspectCachedExe(
                cachedExe
            )
        } catch (
            t: Throwable
        ) {
            runCatching {
                cachedExe.delete()
            }

            throw t
        }
    }

    private fun copyExe(
        context: Context,
        uri: Uri,
        target: File
    ) {
        val input =
            context.contentResolver
                .openInputStream(
                    uri
                )
                ?: error(
                    "EXE dosyası açılamadı."
                )

        input.use { source ->
            FileOutputStream(
                target
            ).use { output ->

                val buffer =
                    ByteArray(
                        128 * 1024
                    )

                var total =
                    0L

                while (true) {
                    val read =
                        source.read(
                            buffer
                        )

                    if (
                        read < 0
                    ) {
                        break
                    }

                    total +=
                        read.toLong()

                    if (
                        total >
                        MAX_EXE_BYTES
                    ) {
                        error(
                            "EXE dosyası 1 GB sınırını aşıyor."
                        )
                    }

                    output.write(
                        buffer,
                        0,
                        read
                    )
                }
            }
        }
    }

    private fun inspectCachedExe(
        exe: File
    ): AppForgeExeInfo {

        RandomAccessFile(
            exe,
            "r"
        ).use { raf ->

            val fileLength =
                raf.length()

            if (
                fileLength < 2L
            ) {
                error(
                    "Geçerli bir Windows EXE dosyası değil."
                )
            }

            /*
             * Windows PE / EXE temel MZ kontrolü.
             */
            raf.seek(
                0L
            )

            val m =
                raf.read()

            val z =
                raf.read()

            if (
                m != 'M'.code ||
                z != 'Z'.code
            ) {
                error(
                    "Geçerli bir Windows EXE dosyası değil."
                )
            }

            if (
                fileLength <
                FOOTER_BYTES
            ) {
                unsupported()
            }

            /*
             * Son 16 byte AppForge magic olmalı.
             */
            raf.seek(
                fileLength -
                MAGIC.size
            )

            val foundMagic =
                ByteArray(
                    MAGIC.size
                )

            raf.readFully(
                foundMagic
            )

            if (
                !foundMagic.contentEquals(
                    MAGIC
                )
            ) {
                unsupported()
            }

            /*
             * Magic'ten hemen önce payload boyutu var.
             * Java/Kotlin readLong big-endian okur.
             */
            raf.seek(
                fileLength -
                FOOTER_BYTES
            )

            val payloadLength =
                raf.readLong()

            if (
                payloadLength <= 0L ||
                payloadLength >
                MAX_PAYLOAD_BYTES
            ) {
                error(
                    "AppForge EXE dönüşüm verisi bozuk veya desteklenmiyor."
                )
            }

            val payloadOffset =
                fileLength -
                FOOTER_BYTES -
                payloadLength

            if (
                payloadOffset < 2L
            ) {
                error(
                    "AppForge EXE dönüşüm verisi geçersiz."
                )
            }

            return AppForgeExeInfo(
                cachedExe =
                    exe,
                payloadOffset =
                    payloadOffset,
                payloadLength =
                    payloadLength
            )
        }
    }

    private fun unsupported(): Nothing {
        error(
            "Bu EXE AppForge projesi değil. Otomatik EXE → APK dönüşümü desteklenmiyor."
        )
    }
}
