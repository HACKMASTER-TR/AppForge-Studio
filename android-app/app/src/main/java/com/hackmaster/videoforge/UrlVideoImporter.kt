package com.hackmaster.videoforge

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

object UrlVideoImporter {

    data class ValidatedDownload(
        val file: File,
        val extension: String,
        val mimeType: String
    )
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

    fun downloadValidated(
        context: Context,
        address: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): ValidatedDownload {

        val adaptive =
            AdaptiveStreamDownloader.downloadIfAdaptive(
                context = context,
                address = address,
                onProgress = onProgress
            )

        if (
            adaptive !=
            null
        ) {
            try {
                validateVideoTrack(
                    adaptive
                )

                return ValidatedDownload(
                    file = adaptive,
                    extension = "mp4",
                    mimeType = "video/mp4"
                )

            } catch (
                t: Throwable
            ) {
                runCatching {
                    adaptive.delete()
                }

                throw t
            }
        }

        val downloaded =
            download(
                context,
                address,
                onProgress
            )

        try {
            rejectNonVideoPayload(
                downloaded
            )

            validateVideoTrack(
                downloaded
            )

            val extension =
                detectExtension(
                    downloaded,
                    address
                )

            val mimeType =
                when (
                    extension
                ) {
                    "webm" ->
                        "video/webm"

                    "mkv" ->
                        "video/x-matroska"

                    "mov" ->
                        "video/quicktime"

                    "3gp" ->
                        "video/3gpp"

                    else ->
                        "video/mp4"
                }

            val validated =
                File(
                    downloaded.parentFile,
                    "${downloaded.nameWithoutExtension}.$extension"
                )

            if (
                validated.absolutePath !=
                downloaded.absolutePath
            ) {
                runCatching {
                    validated.delete()
                }

                if (
                    !downloaded.renameTo(
                        validated
                    )
                ) {
                    downloaded.copyTo(
                        validated,
                        overwrite = true
                    )

                    require(
                        downloaded.delete()
                    ) {
                        "Geçici indirme dosyası temizlenemedi."
                    }
                }
            }

            require(
                validated.isFile &&
                    validated.length() >
                    0L
            ) {
                "Doğrulanmış video dosyası oluşturulamadı."
            }

            return ValidatedDownload(
                file = validated,
                extension = extension,
                mimeType = mimeType
            )

        } catch (
            t: Throwable
        ) {
            runCatching {
                downloaded.delete()
            }

            throw t
        }
    }

    fun saveValidatedToDownloads(
        context: Context,
        validated: ValidatedDownload,
        displayNameBase: String
    ): Uri {

        require(
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
        ) {
            "Doğrulanmış video indirme Android 10 veya üzerini gerektirir."
        }

        val safeBase =
            displayNameBase
                .replace(
                    Regex(
                        "[^A-Za-z0-9._-]"
                    ),
                    "_"
                )
                .trim(
                    '.',
                    '_'
                )
                .ifBlank {
                    "VideoForge"
                }

        val values =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    "$safeBase.${validated.extension}"
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    validated.mimeType
                )

                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/VideoForge"
                )

                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    1
                )
            }

        val resolver =
            context.contentResolver

        val uri =
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            )
                ?: error(
                    "İndirilenler/VideoForge kaydı oluşturulamadı."
                )

        try {
            resolver
                .openOutputStream(
                    uri,
                    "w"
                )
                ?.use {
                    output ->

                    validated
                        .file
                        .inputStream()
                        .buffered(
                            256 * 1024
                        )
                        .use {
                            input ->

                            input.copyTo(
                                output,
                                256 * 1024
                            )
                        }
                }
                ?: error(
                    "Doğrulanmış video İndirilenler'e yazılamadı."
                )

            values.clear()

            values.put(
                MediaStore.MediaColumns.IS_PENDING,
                0
            )

            resolver.update(
                uri,
                values,
                null,
                null
            )

            return uri

        } catch (
            t: Throwable
        ) {
            runCatching {
                resolver.delete(
                    uri,
                    null,
                    null
                )
            }

            throw t
        }
    }

    private fun rejectNonVideoPayload(
        file: File
    ) {
        val prefix =
            file.inputStream()
                .use {
                    input ->

                    val buffer =
                        ByteArray(
                            16 * 1024
                        )

                    val count =
                        input.read(
                            buffer
                        )

                    if (
                        count <=
                        0
                    ) {
                        ByteArray(
                            0
                        )
                    } else {
                        buffer.copyOf(
                            count
                        )
                    }
                }

        val text =
            prefix
                .toString(
                    Charsets.UTF_8
                )
                .trimStart()
                .lowercase(
                    Locale.US
                )

        require(
            !text.startsWith(
                "<!doctype html"
            ) &&
                !text.startsWith(
                    "<html"
                ) &&
                !text.contains(
                    "<html"
                )
        ) {
            "Bu bağlantı video yerine bir web sayfası döndürdü."
        }

        require(
            !text.startsWith(
                "#extm3u"
            )
        ) {
            "Bu bağlantı doğrudan video yerine HLS oynatma listesi döndürdü."
        }

        require(
            !text.startsWith(
                "<mpd"
            ) &&
                !text.contains(
                    "<mpd "
                )
        ) {
            "Bu bağlantı doğrudan video yerine DASH manifesti döndürdü."
        }

        require(
            !text.startsWith(
                "{"
            ) &&
                !text.startsWith(
                    "["
                )
        ) {
            "Bu bağlantı video yerine JSON/metin yanıtı döndürdü."
        }
    }

    private fun validateVideoTrack(
        file: File
    ) {
        val extractor =
            MediaExtractor()

        try {
            FileInputStream(
                file
            ).use {
                input ->

                extractor.setDataSource(
                    input.fd
                )

                var videoTrackFound =
                    false

                for (
                    index in
                    0 until extractor.trackCount
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
                            "video/"
                        )
                    ) {
                        videoTrackFound =
                            true

                        break
                    }
                }

                require(
                    videoTrackFound
                ) {
                    "İndirilen içerikte geçerli video parçası bulunamadı."
                }
            }

        } catch (
            t: Throwable
        ) {
            throw IllegalArgumentException(
                "İndirilen içerik Android tarafından geçerli video olarak doğrulanamadı.",
                t
            )

        } finally {
            runCatching {
                extractor.release()
            }
        }
    }

    private fun detectExtension(
        file: File,
        address: String
    ): String {

        val allowed =
            setOf(
                "mp4",
                "webm",
                "mov",
                "mkv",
                "m4v",
                "3gp"
            )

        val pathExtension =
            runCatching {
                URI(
                    address.trim()
                )
                    .path
                    .orEmpty()
                    .substringAfterLast(
                        '.',
                        ""
                    )
                    .lowercase(
                        Locale.US
                    )
            }
                .getOrNull()
                ?.takeIf {
                    it in
                        allowed
                }

        if (
            pathExtension !=
            null
        ) {
            return pathExtension
        }

        val header =
            file.inputStream()
                .use {
                    input ->

                    val buffer =
                        ByteArray(
                            16
                        )

                    val count =
                        input.read(
                            buffer
                        )

                    if (
                        count <=
                        0
                    ) {
                        ByteArray(
                            0
                        )
                    } else {
                        buffer.copyOf(
                            count
                        )
                    }
                }

        if (
            header.size >=
            8 &&
            header[4] ==
            'f'.code.toByte() &&
            header[5] ==
            't'.code.toByte() &&
            header[6] ==
            'y'.code.toByte() &&
            header[7] ==
            'p'.code.toByte()
        ) {
            return "mp4"
        }

        if (
            header.size >=
            4 &&
            header[0] ==
            0x1a.toByte() &&
            header[1] ==
            0x45.toByte() &&
            header[2] ==
            0xdf.toByte() &&
            header[3] ==
            0xa3.toByte()
        ) {
            return "webm"
        }

        error(
            "Video parçası bulundu ancak container türü güvenli şekilde belirlenemedi."
        )
    }

}
