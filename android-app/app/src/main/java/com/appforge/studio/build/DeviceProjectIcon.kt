package com.appforge.studio.build

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.appforge.studio.model.ProjectDraft
import java.io.File

/** Writes only to the disposable project copy; never touches imported source or app storage. */
internal object DeviceProjectIcon {
    fun install(context: Context, draft: ProjectDraft, project: File) {
        val uri = draft.iconUri?.takeIf { it.isNotBlank() } ?: return
        val bitmap = context.contentResolver.openInputStream(Uri.parse(uri))?.use {
            BitmapFactory.decodeStream(it)
        } ?: error("Seçilen uygulama ikonu okunamadı; varsayılan ikonla devam edilmedi.")
        try {
            require(bitmap.width in 1..4096 && bitmap.height in 1..4096) {
                "Seçilen uygulama ikonunun boyutu geçersiz."
            }
            val dir = File(project, "app/src/main/res/drawable-nodpi").apply { mkdirs() }
            val target = File(dir, "appforge_user_icon.png")
            val scaled = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
            try {
                target.outputStream().use { out ->
                    check(scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        "Uygulama ikonu PNG olarak oluşturulamadı."
                    }
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
            check(target.isFile && target.length() > 0) {
                "Android uygulama ikonu kaynak dosyasına gömülemedi."
            }
        } finally {
            bitmap.recycle()
        }
        val manifest = File(project, "app/src/main/AndroidManifest.xml")
        require(manifest.isFile) { "İkon için AndroidManifest.xml bulunamadı." }
        val source = manifest.readText()
        val app = Regex("<application\\b[^>]*>").find(source)
            ?: error("Uygulama ikonu için application etiketi bulunamadı.")
        // Never duplicate the attributes of an imported Android project.
        val cleaned = app.value.replace(Regex("\\sandroid:(?:icon|roundIcon)\\s*=\\s*\\\"[^\\\"]*\\\""), "")
        val decorated = cleaned.replaceFirst(
            "<application",
            "<application android:icon=\"@drawable/appforge_user_icon\" " +
                "android:roundIcon=\"@drawable/appforge_user_icon\""
        )
        manifest.writeText(source.replaceRange(app.range, decorated))
        check(manifest.readText().contains("android:icon=\"@drawable/appforge_user_icon\"")) {
            "Seçilen ikon manifestte doğrulanamadı."
        }
    }
}
