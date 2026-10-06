package com.appforge.studio.build

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.appforge.studio.model.ProjectDraft
import java.io.File

/**
 * APPFORGE_LAUNCHER_FULL_BLEED_V1_5
 *
 * Custom icons receive density mipmaps and a full-bleed adaptive icon.
 * Only the disposable generated/native Android project is modified.
 */
internal object DeviceProjectIcon {
    private val densities =
        linkedMapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192
        )

    fun install(
        context: Context,
        draft: ProjectDraft,
        project: File
    ) {
        val uri =
            draft.iconUri
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return

        val bitmap =
            context.contentResolver
                .openInputStream(
                    Uri.parse(
                        uri
                    )
                )
                ?.use {
                    BitmapFactory
                        .decodeStream(
                            it
                        )
                }
                ?: error(
                    "Seçilen uygulama ikonu okunamadı; varsayılan ikonla devam edilmedi."
                )

        try {
            require(
                bitmap.width in
                    1..4096 &&
                    bitmap.height in
                    1..4096
            ) {
                "Seçilen uygulama ikonunun boyutu geçersiz."
            }

            val res =
                File(
                    project,
                    "app/src/main/res"
                ).apply {
                    mkdirs()
                }

            val drawable =
                File(
                    res,
                    "drawable-nodpi"
                ).apply {
                    mkdirs()
                }

            val master =
                File(
                    drawable,
                    "appforge_user_icon_fullbleed.png"
                )

            val masterBitmap =
                Bitmap.createScaledBitmap(
                    bitmap,
                    1024,
                    1024,
                    true
                )

            try {
                master.outputStream()
                    .use {
                        out ->

                        check(
                            masterBitmap.compress(
                                Bitmap.CompressFormat.PNG,
                                100,
                                out
                            )
                        ) {
                            "Uygulama ikon master PNG oluşturulamadı."
                        }
                    }
            } finally {
                if (
                    masterBitmap !==
                        bitmap
                ) {
                    masterBitmap.recycle()
                }
            }

            densities.forEach {
                (density, size) ->

                val dir =
                    File(
                        res,
                        "mipmap-$density"
                    ).apply {
                        mkdirs()
                    }

                val scaled =
                    Bitmap.createScaledBitmap(
                        bitmap,
                        size,
                        size,
                        true
                    )

                try {
                    listOf(
                        "appforge_user_icon.png",
                        "appforge_user_icon_round.png"
                    ).forEach {
                        name ->

                        File(
                            dir,
                            name
                        )
                            .outputStream()
                            .use {
                                out ->

                                check(
                                    scaled.compress(
                                        Bitmap.CompressFormat.PNG,
                                        100,
                                        out
                                    )
                                ) {
                                    "Launcher mipmap PNG oluşturulamadı."
                                }
                            }
                    }
                } finally {
                    if (
                        scaled !==
                            bitmap
                    ) {
                        scaled.recycle()
                    }
                }
            }

            val transparentDir =
                File(
                    res,
                    "drawable"
                ).apply {
                    mkdirs()
                }

            File(
                transparentDir,
                "appforge_icon_transparent.xml"
            ).writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <shape xmlns:android="http://schemas.android.com/apk/res/android"
                    android:shape="rectangle">
                    <solid android:color="#00000000" />
                </shape>
                """.trimIndent()
            )

            val adaptiveDir =
                File(
                    res,
                    "mipmap-anydpi-v26"
                ).apply {
                    mkdirs()
                }

            val adaptive =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@drawable/appforge_user_icon_fullbleed" />
                    <foreground android:drawable="@drawable/appforge_icon_transparent" />
                </adaptive-icon>
                """.trimIndent()

            File(
                adaptiveDir,
                "appforge_user_icon.xml"
            ).writeText(
                adaptive
            )

            File(
                adaptiveDir,
                "appforge_user_icon_round.xml"
            ).writeText(
                adaptive
            )

        } finally {
            bitmap.recycle()
        }

        val manifest =
            File(
                project,
                "app/src/main/AndroidManifest.xml"
            )

        require(
            manifest.isFile
        ) {
            "İkon için AndroidManifest.xml bulunamadı."
        }

        val source =
            manifest.readText()

        val app =
            Regex(
                "<application\\b[^>]*>"
            )
                .find(
                    source
                )
                ?: error(
                    "Uygulama ikonu için application etiketi bulunamadı."
                )

        val cleaned =
            app.value
                .replace(
                    Regex(
                        "\\sandroid:(?:icon|roundIcon)\\s*=\\s*\\\"[^\\\"]*\\\""
                    ),
                    ""
                )

        val decorated =
            cleaned.replaceFirst(
                "<application",
                "<application " +
                    "android:icon=\"@mipmap/appforge_user_icon\" " +
                    "android:roundIcon=\"@mipmap/appforge_user_icon_round\""
            )

        manifest.writeText(
            source.replaceRange(
                app.range,
                decorated
            )
        )

        val rendered =
            manifest.readText()

        check(
            rendered.contains(
                "android:icon=\"@mipmap/appforge_user_icon\""
            ) &&
                rendered.contains(
                    "android:roundIcon=\"@mipmap/appforge_user_icon_round\""
                )
        ) {
            "Seçilen ikon manifestte doğrulanamadı."
        }
    }
}
