package com.hackmaster.videoforge

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(UnstableApi::class)
object AdaptiveStreamDownloader {

    private const val PREFIX_LIMIT =
        64 * 1024

    private const val MANIFEST_LIMIT =
        2 * 1024 * 1024

    private const val MAX_HLS_PLAYLISTS =
        32

    private const val EXPORT_TIMEOUT_MINUTES =
        120L

    private enum class SourceKind {
        DIRECT,
        HLS,
        DASH,
        HTML,
        JSON
    }

    private data class Probe(
        val contentType: String,
        val prefix: String
    )

    fun downloadIfAdaptive(
        context: Context,
        address: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? {

        val uri =
            parseAndValidateHttpUri(
                address
            )

        val probe =
            probe(
                uri
            )

        return when (
            classify(
                uri,
                probe
            )
        ) {
            SourceKind.DIRECT ->
                null

            SourceKind.HTML ->
                error(
                    "Bu bağlantı video dosyası yerine bir web sayfası döndürdü."
                )

            SourceKind.JSON ->
                error(
                    "Bu bağlantı video yerine JSON/metin yanıtı döndürdü."
                )

            SourceKind.HLS -> {
                validateClearVodHls(
                    uri
                )

                exportAdaptiveToMp4(
                    context = context,
                    uri = uri,
                    mimeType = MimeTypes.APPLICATION_M3U8,
                    onProgress = onProgress
                )
            }

            SourceKind.DASH -> {
                validateClearVodDash(
                    uri
                )

                exportAdaptiveToMp4(
                    context = context,
                    uri = uri,
                    mimeType = MimeTypes.APPLICATION_MPD,
                    onProgress = onProgress
                )
            }
        }
    }

    private fun parseAndValidateHttpUri(
        address: String
    ): URI {

        val uri =
            URI(
                address.trim()
            )

        require(
            uri.scheme.equals(
                "https",
                true
            ) ||
                uri.scheme.equals(
                    "http",
                    true
                )
        ) {
            "URL http:// veya https:// ile başlamalı."
        }

        require(
            uri.userInfo ==
                null
        ) {
            "Kullanıcı adı/parola içeren URL desteklenmiyor."
        }

        val host =
            uri.host
                .orEmpty()
                .lowercase(
                    Locale.US
                )

        require(
            host.isNotBlank() &&
                host != "localhost" &&
                host != "127.0.0.1" &&
                host != "::1"
        ) {
            "Yerel cihaz URL'si desteklenmiyor."
        }

        return uri
    }

    private fun probe(
        uri: URI
    ): Probe {

        var connection:
            HttpURLConnection? =
            null

        try {
            connection =
                openConnection(
                    uri
                ).apply {
                    setRequestProperty(
                        "Range",
                        "bytes=0-${PREFIX_LIMIT - 1}"
                    )
                }

            val code =
                connection.responseCode

            require(
                code in 200..299
            ) {
                "Video kaynağı HTTP $code döndürdü."
            }

            val contentType =
                connection
                    .contentType
                    .orEmpty()
                    .substringBefore(
                        ';'
                    )
                    .trim()
                    .lowercase(
                        Locale.US
                    )

            val bytes =
                connection
                    .inputStream
                    .use {
                        input ->
                        readLimited(
                            input,
                            PREFIX_LIMIT
                        )
                    }

            return Probe(
                contentType =
                    contentType,
                prefix =
                    bytes.toString(
                        Charsets.UTF_8
                    )
            )

        } finally {
            connection
                ?.disconnect()
        }
    }

    private fun classify(
        uri: URI,
        probe: Probe
    ): SourceKind {

        val path =
            uri.path
                .orEmpty()
                .lowercase(
                    Locale.US
                )

        val text =
            probe.prefix
                .trimStart()
                .lowercase(
                    Locale.US
                )

        val contentType =
            probe.contentType

        if (
            text.startsWith(
                "<!doctype html"
            ) ||
            text.startsWith(
                "<html"
            ) ||
            text.contains(
                "<html"
            ) ||
            contentType ==
                "text/html"
        ) {
            return SourceKind.HTML
        }

        if (
            text.startsWith(
                "#extm3u"
            ) ||
            contentType.contains(
                "mpegurl"
            ) ||
            path.endsWith(
                ".m3u8"
            )
        ) {
            return SourceKind.HLS
        }

        if (
            text.contains(
                "<mpd"
            ) ||
            contentType ==
                "application/dash+xml" ||
            path.endsWith(
                ".mpd"
            )
        ) {
            return SourceKind.DASH
        }

        if (
            text.startsWith(
                "{"
            ) ||
            text.startsWith(
                "["
            ) ||
            contentType.contains(
                "json"
            )
        ) {
            return SourceKind.JSON
        }

        return SourceKind.DIRECT
    }

    private fun validateClearVodHls(
        root: URI
    ) {
        val queue =
            ArrayDeque<URI>()

        val visited =
            linkedSetOf<String>()

        queue.add(
            root
        )

        var mediaPlaylistCount =
            0

        while (
            queue.isNotEmpty()
        ) {
            require(
                visited.size <
                    MAX_HLS_PLAYLISTS
            ) {
                "HLS manifest zinciri güvenli doğrulama sınırını aştı."
            }

            val uri =
                queue.removeFirst()

            val key =
                uri.normalize()
                    .toString()

            if (
                !visited.add(
                    key
                )
            ) {
                continue
            }

            val body =
                fetchManifestText(
                    uri
                )

            val normalized =
                body
                    .trimStart()

            require(
                normalized.startsWith(
                    "#EXTM3U",
                    ignoreCase = true
                )
            ) {
                "HLS oynatma listesi geçersiz."
            }

            val lines =
                body
                    .lineSequence()
                    .map {
                        it.trim()
                    }
                    .filter {
                        it.isNotEmpty()
                    }
                    .toList()

            val encrypted =
                lines.any {
                    line ->

                    (
                        line.startsWith(
                            "#EXT-X-KEY:",
                            ignoreCase = true
                        ) ||
                            line.startsWith(
                                "#EXT-X-SESSION-KEY:",
                                ignoreCase = true
                            )
                    ) &&
                        !line.contains(
                            "METHOD=NONE",
                            ignoreCase = true
                        )
                }

            require(
                !encrypted
            ) {
                "Şifreli/DRM korumalı HLS akışları indirilmiyor."
            }

            val isMediaPlaylist =
                lines.any {
                    it.startsWith(
                        "#EXTINF:",
                        ignoreCase = true
                    )
                }

            if (
                isMediaPlaylist
            ) {
                mediaPlaylistCount++

                require(
                    lines.any {
                        it.equals(
                            "#EXT-X-ENDLIST",
                            ignoreCase = true
                        )
                    }
                ) {
                    "Canlı HLS akışları V5.2 indirme kapsamında değil."
                }
            }

            val children =
                linkedSetOf<URI>()

            lines.forEachIndexed {
                index,
                line ->

                if (
                    line.startsWith(
                        "#EXT-X-STREAM-INF:",
                        ignoreCase = true
                    )
                ) {
                    val next =
                        lines
                            .drop(
                                index + 1
                            )
                            .firstOrNull {
                                !it.startsWith(
                                    "#"
                                )
                            }

                    if (
                        !next.isNullOrBlank()
                    ) {
                        children.add(
                            resolveHttpChild(
                                uri,
                                next
                            )
                        )
                    }
                }

                if (
                    line.startsWith(
                        "#EXT-X-MEDIA:",
                        ignoreCase = true
                    ) ||
                    line.startsWith(
                        "#EXT-X-I-FRAME-STREAM-INF:",
                        ignoreCase = true
                    )
                ) {
                    val match =
                        Regex(
                            """URI\s*=\s*"([^"]+)"""",
                            RegexOption.IGNORE_CASE
                        )
                            .find(
                                line
                            )

                    val child =
                        match
                            ?.groupValues
                            ?.getOrNull(
                                1
                            )

                    if (
                        !child.isNullOrBlank()
                    ) {
                        children.add(
                            resolveHttpChild(
                                uri,
                                child
                            )
                        )
                    }
                }
            }

            require(
                visited.size +
                    queue.size +
                    children.size <=
                    MAX_HLS_PLAYLISTS
            ) {
                "HLS manifest zinciri çok fazla alt oynatma listesi içeriyor."
            }

            children.forEach {
                child ->

                if (
                    !visited.contains(
                        child.normalize()
                            .toString()
                    )
                ) {
                    queue.add(
                        child
                    )
                }
            }
        }

        require(
            mediaPlaylistCount >
                0
        ) {
            "HLS içinde indirilebilir VOD medya oynatma listesi bulunamadı."
        }
    }

    private fun validateClearVodDash(
        uri: URI
    ) {
        val body =
            fetchManifestText(
                uri
            )

        val lower =
            body.lowercase(
                Locale.US
            )

        require(
            lower.contains(
                "<mpd"
            )
        ) {
            "DASH manifesti geçersiz."
        }

        require(
            !Regex(
                """\btype\s*=\s*["']dynamic["']""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(
                body
            )
        ) {
            "Canlı DASH akışları V5.2 indirme kapsamında değil."
        }

        require(
            !lower.contains(
                "<contentprotection"
            )
        ) {
            "Şifreli/DRM korumalı DASH akışları indirilmiyor."
        }
    }

    private fun resolveHttpChild(
        parent: URI,
        child: String
    ): URI {

        val resolved =
            parent.resolve(
                child
            )

        require(
            resolved.scheme.equals(
                "https",
                true
            ) ||
                resolved.scheme.equals(
                    "http",
                    true
                )
        ) {
            "HLS alt kaynak protokolü desteklenmiyor."
        }

        return resolved
    }

    private fun fetchManifestText(
        uri: URI
    ): String {

        var connection:
            HttpURLConnection? =
            null

        try {
            connection =
                openConnection(
                    uri
                )

            val code =
                connection.responseCode

            require(
                code in 200..299
            ) {
                "Manifest HTTP $code döndürdü."
            }

            val announced =
                connection
                    .contentLengthLong

            require(
                announced <=
                    MANIFEST_LIMIT ||
                    announced <
                    0L
            ) {
                "Manifest beklenenden büyük."
            }

            val bytes =
                connection
                    .inputStream
                    .use {
                        input ->
                        readLimitedOrFail(
                            input,
                            MANIFEST_LIMIT
                        )
                    }

            return bytes.toString(
                Charsets.UTF_8
            )

        } finally {
            connection
                ?.disconnect()
        }
    }

    private fun openConnection(
        uri: URI
    ): HttpURLConnection =
        (
            URL(
                uri.toString()
            ).openConnection() as
                HttpURLConnection
        ).apply {
            instanceFollowRedirects =
                true

            connectTimeout =
                20_000

            readTimeout =
                30_000

            requestMethod =
                "GET"

            setRequestProperty(
                "User-Agent",
                "VideoForge/5.2 Android"
            )

            setRequestProperty(
                "Accept",
                "video/*,application/vnd.apple.mpegurl,application/x-mpegurl,application/dash+xml,application/octet-stream;q=0.9,*/*;q=0.1"
            )
        }

    private fun readLimited(
        input: InputStream,
        limit: Int
    ): ByteArray {

        val output =
            ByteArrayOutputStream()

        val buffer =
            ByteArray(
                16 * 1024
            )

        while (
            output.size() <
            limit
        ) {
            val remaining =
                limit -
                    output.size()

            val count =
                input.read(
                    buffer,
                    0,
                    minOf(
                        buffer.size,
                        remaining
                    )
                )

            if (
                count <
                0
            ) {
                break
            }

            if (
                count >
                0
            ) {
                output.write(
                    buffer,
                    0,
                    count
                )
            }
        }

        return output.toByteArray()
    }

    private fun readLimitedOrFail(
        input: InputStream,
        limit: Int
    ): ByteArray {

        val output =
            ByteArrayOutputStream()

        val buffer =
            ByteArray(
                16 * 1024
            )

        var total =
            0

        while (
            true
        ) {
            val count =
                input.read(
                    buffer
                )

            if (
                count <
                0
            ) {
                break
            }

            if (
                count ==
                0
            ) {
                continue
            }

            total +=
                count

            require(
                total <=
                    limit
            ) {
                "Manifest güvenli boyut sınırını aştı."
            }

            output.write(
                buffer,
                0,
                count
            )
        }

        return output.toByteArray()
    }

    private fun exportAdaptiveToMp4(
        context: Context,
        uri: URI,
        mimeType: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File {

        val directory =
            File(
                context.cacheDir,
                "url-import"
            ).apply {
                if (
                    !exists() &&
                    !mkdirs()
                ) {
                    error(
                        "VideoForge geçici alanı oluşturulamadı."
                    )
                }
            }

        val output =
            File(
                directory,
                "adaptive-${System.currentTimeMillis()}.mp4"
            )

        runCatching {
            output.delete()
        }

        val latch =
            CountDownLatch(
                1
            )

        val failure =
            AtomicReference<Throwable?>(
                null
            )

        val transformerReference =
            AtomicReference<Transformer?>(
                null
            )

        onProgress(
            0L,
            -1L
        )

        Handler(
            Looper.getMainLooper()
        ).post {
            try {
                val item =
                    MediaItem.Builder()
                        .setUri(
                            uri.toString()
                        )
                        .setMimeType(
                            mimeType
                        )
                        .build()

                val transformer =
                    Transformer.Builder(
                        context.applicationContext
                    )
                        .addListener(
                            object :
                                Transformer.Listener {

                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult
                                ) {
                                    latch.countDown()
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    failure.set(
                                        exportException
                                    )

                                    latch.countDown()
                                }
                            }
                        )
                        .build()

                transformerReference.set(
                    transformer
                )

                val editedItem =
                    EditedMediaItem.Builder(
                        item
                    )
                        .build()

                transformer.start(
                    editedItem,
                    output.absolutePath
                )

            } catch (
                t: Throwable
            ) {
                failure.set(
                    t
                )

                latch.countDown()
            }
        }

        val completed =
            latch.await(
                EXPORT_TIMEOUT_MINUTES,
                TimeUnit.MINUTES
            )

        if (
            !completed
        ) {
            Handler(
                Looper.getMainLooper()
            ).post {
                runCatching {
                    transformerReference
                        .get()
                        ?.cancel()
                }
            }

            runCatching {
                output.delete()
            }

            throw IOException(
                "Adaptif video dışa aktarma zaman aşımına uğradı."
            )
        }

        val error =
            failure.get()

        if (
            error !=
            null
        ) {
            runCatching {
                output.delete()
            }

            throw IOException(
                "Adaptif video MP4 olarak oluşturulamadı: " +
                    (
                        error.message
                            ?: error.javaClass.simpleName
                    ),
                error
            )
        }

        require(
            output.isFile &&
                output.length() >
                0L
        ) {
            "Adaptif video çıktısı oluşturulamadı."
        }

        onProgress(
            output.length(),
            output.length()
        )

        return output
    }
}
