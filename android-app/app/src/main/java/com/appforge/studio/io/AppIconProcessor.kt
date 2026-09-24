package com.appforge.studio.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class PreparedAppIcon(
    val uri: String,
    val name: String,
    val sourceWidth: Int,
    val sourceHeight: Int
)

object AppIconProcessor {
    private const val OUTPUT_SIZE = 1024
    // Full-width, aspect-preserving master; no synthetic cyan outline.
    private const val SAFE_CONTENT_SIZE = OUTPUT_SIZE
    private const val MAX_DECODE_SIZE = 2048

    fun prepare(
        context: Context,
        source: Uri,
        backgroundColor: String
    ): PreparedAppIcon {
        val decoded =
            decode(
                context,
                source
            )

        require(
            decoded.width > 0 &&
                decoded.height > 0
        ) {
            "Seçilen ikon resmi okunamadı."
        }

        val sourceWidth =
            decoded.width

        val sourceHeight =
            decoded.height

        val output =
            Bitmap.createBitmap(
                OUTPUT_SIZE,
                OUTPUT_SIZE,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(output)

        canvas.drawColor(
            masterBackgroundColor(decoded, backgroundColor)
        )

        val scale =
            minOf(
                SAFE_CONTENT_SIZE.toFloat() /
                    decoded.width,
                SAFE_CONTENT_SIZE.toFloat() /
                    decoded.height
            )

        val targetWidth =
            max(
                1,
                (
                    decoded.width *
                        scale
                ).roundToInt()
            )

        val targetHeight =
            max(
                1,
                (
                    decoded.height *
                        scale
                ).roundToInt()
            )

        val left =
            (
                OUTPUT_SIZE -
                    targetWidth
            ) / 2f

        val top =
            (
                OUTPUT_SIZE -
                    targetHeight
            ) / 2f

        canvas.drawBitmap(
            decoded,
            null,
            android.graphics.RectF(
                left,
                top,
                left + targetWidth,
                top + targetHeight
            ),
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG or
                    Paint.DITHER_FLAG
            )
        )

        val iconDir =
            File(
                context.filesDir,
                "prepared-icons"
            ).apply {
                mkdirs()
            }

        val outputFile =
            File(
                iconDir,
                "app-icon-${UUID.randomUUID()}.png"
            )

        FileOutputStream(
            outputFile
        ).use {
            stream ->

            check(
                output.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    stream
                )
            ) {
                "İkon PNG olarak hazırlanamadı."
            }
        }

        output.recycle()
        decoded.recycle()

        return PreparedAppIcon(
            uri =
                Uri.fromFile(
                    outputFile
                ).toString(),
            name =
                outputFile.name,
            sourceWidth =
                sourceWidth,
            sourceHeight =
                sourceHeight
        )
    }

    private fun decode(
        context: Context,
        uri: Uri
    ): Bitmap {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {
            val source =
                ImageDecoder.createSource(
                    context.contentResolver,
                    uri
                )

            return ImageDecoder.decodeBitmap(
                source
            ) {
                decoder,
                info,
                _ ->

                val longest =
                    max(
                        info.size.width,
                        info.size.height
                    )

                if (
                    longest > MAX_DECODE_SIZE
                ) {
                    val ratio =
                        MAX_DECODE_SIZE.toFloat() /
                            longest

                    decoder.setTargetSize(
                        max(
                            1,
                            (
                                info.size.width *
                                    ratio
                            ).roundToInt()
                        ),
                        max(
                            1,
                            (
                                info.size.height *
                                    ratio
                            ).roundToInt()
                        )
                    )
                }

                decoder.allocator =
                    ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        context.contentResolver
            .openInputStream(uri)
            ?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    bounds
                )
            }

        require(
            bounds.outWidth > 0 &&
                bounds.outHeight > 0
        ) {
            "Seçilen ikon resmi okunamadı."
        }

        var sample =
            1

        while (
            max(
                bounds.outWidth / sample,
                bounds.outHeight / sample
            ) > MAX_DECODE_SIZE
        ) {
            sample *=
                2
        }

        val bitmap =
            context.contentResolver
                .openInputStream(uri)
                ?.use {
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        BitmapFactory.Options().apply {
                            inSampleSize = sample
                        }
                    )
                }
                ?: error(
                    "Seçilen ikon resmi okunamadı."
                )

        val orientation =
            context.contentResolver
                .openInputStream(uri)
                ?.use {
                    ExifInterface(it)
                        .getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                }
                ?: ExifInterface.ORIENTATION_NORMAL

        val rotation =
            when (
                orientation
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

        if (
            rotation == 0f
        ) {
            return bitmap
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply {
                postRotate(
                    rotation
                )
            },
            true
        ).also {
            bitmap.recycle()
        }
    }

    /**
     * An opaque wide logo already has a background in its source artwork.
     * Match its corner colour so the square master's remaining bars do not
     * become the unrelated UI primary colour (seen as a cyan/white frame).
     * Transparent artwork continues to use the explicitly selected colour.
     * Preserve aspect ratio and ALL of the original design; do not crop text.
     */
    private fun masterBackgroundColor(bitmap: Bitmap, fallback: String): Int {
        val corners = listOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(bitmap.width - 1, 0),
            bitmap.getPixel(0, bitmap.height - 1),
            bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)
        )
        if (corners.count { Color.alpha(it) >= 240 } < 3) {
            return parseColor(fallback)
        }
        fun median(channel: (Int) -> Int): Int =
            corners.map(channel).sorted()[corners.size / 2]
        return Color.rgb(
            median(Color::red),
            median(Color::green),
            median(Color::blue)
        )
    }

    private fun parseColor(
        value: String
    ): Int =
        runCatching {
            Color.parseColor(
                value
            )
        }.getOrDefault(
            Color.rgb(
                7,
                16,
                31
            )
        )
}
