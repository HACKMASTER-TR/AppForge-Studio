package com.hackmaster.videoforge

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

object UrlVideoImporter {
    fun download(
        context: Context,
        address: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File {
        val parsed = URI(address.trim())
        require(parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) {
            "URL http:// veya https:// ile başlamalı."
        }
        require(parsed.userInfo == null) { "Kullanıcı adı/parola içeren URL desteklenmiyor." }
        val host = parsed.host.orEmpty().lowercase(Locale.US)
        require(host.isNotBlank() && host != "localhost" && host != "127.0.0.1" && host != "::1") {
            "Yerel ağ/localhost URL'si desteklenmiyor."
        }

        val dir = File(context.cacheDir, "url-import").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 6 * 60 * 60 * 1000L }
            ?.forEach { runCatching { it.delete() } }
        val out = File(dir, "video-${System.currentTimeMillis()}.bin")

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 30_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "VideoForge/4.0.3 Android")
                setRequestProperty("Accept", "video/*,application/octet-stream;q=0.9,*/*;q=0.1")
            }
            val code = connection.responseCode
            require(code in 200..299) { "Video indirilemedi (HTTP $code)." }

            val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.US)
            val looksLikeVideo = contentType.startsWith("video/") || contentType == "application/octet-stream" ||
                parsed.path.orEmpty().lowercase(Locale.US).let { p ->
                    p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".mov") ||
                        p.endsWith(".mkv") || p.endsWith(".m4v") || p.endsWith(".3gp")
                }
            require(looksLikeVideo) {
                "Bu URL doğrudan video dosyasına gitmiyor. Doğrudan MP4/WebM/MOV bağlantısı kullan."
            }

            val total = connection.contentLengthLong.coerceAtLeast(-1L)
            if (total > 0L) {
                val usable = context.cacheDir.usableSpace
                require(usable > total + 256L * 1024L * 1024L) {
                    "Telefonda bu videoyu işlemek için yeterli boş alan yok."
                }
            }

            connection.inputStream.use { input ->
                FileOutputStream(out).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        output.write(buffer, 0, n)
                        done += n
                        if (done % (1024 * 1024) < buffer.size) onProgress(done, total)
                    }
                    output.fd.sync()
                    onProgress(done, total)
                }
            }
            require(out.length() > 0L) { "URL'den boş dosya geldi." }
            return out
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            connection?.disconnect()
        }
    }
}
