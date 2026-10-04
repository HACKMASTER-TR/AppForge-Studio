package com.hackmaster.videoforge

import android.text.Html
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

object PublicMediaPageResolver {

    private const val PROBE_LIMIT =
        32 * 1024

    private const val HTML_LIMIT =
        2 * 1024 * 1024

    private const val MAX_CANDIDATES =
        24

    private data class Probe(
        val contentType: String,
        val bytes: ByteArray
    ) {
        val text: String
            get() =
                bytes.toString(
                    Charsets.UTF_8
                )
    }

    fun resolve(
        address: String
    ): String {

        val page =
            parseHttpUri(
                address
            )

        val initialProbe =
            probe(
                page
            )

        if (
            !isHtml(
                initialProbe
            )
        ) {
            return page.toString()
        }

        val html =
            fetchHtml(
                page
            )

        val candidates =
            extractCandidates(
                page,
                html
            )
                .take(
                    MAX_CANDIDATES
                )

        for (
            candidate in candidates
        ) {
            val candidateProbe =
                runCatching {
                    probe(
                        candidate
                    )
                }
                    .getOrNull()
                    ?: continue

            if (
                isPublicMedia(
                    candidate,
                    candidateProbe
                )
            ) {
                return candidate.toString()
            }
        }

        error(
            "Bu web sayfasında açık ve indirilebilir bir video kaynağı bulunamadı."
        )
    }

    private fun extractCandidates(
        base: URI,
        html: String
    ): List<URI> {

        val values =
            mutableListOf<String>()

        Regex(
            """(?is)<(?:video|source)\b[^>]*>"""
        )
            .findAll(
                html
            )
            .forEach {
                match ->

                val attrs =
                    attributes(
                        match.value
                    )

                attrs["src"]
                    ?.let(
                        values::add
                    )

                attrs["data-src"]
                    ?.let(
                        values::add
                    )
            }

        Regex(
            """(?is)<meta\b[^>]*>"""
        )
            .findAll(
                html
            )
            .forEach {
                match ->

                val attrs =
                    attributes(
                        match.value
                    )

                val key =
                    (
                        attrs["property"]
                            ?: attrs["name"]
                            ?: attrs["itemprop"]
                            ?: ""
                    )
                        .trim()
                        .lowercase(
                            Locale.US
                        )

                if (
                    key in setOf(
                        "og:video",
                        "og:video:url",
                        "og:video:secure_url",
                        "twitter:player:stream",
                        "contenturl"
                    )
                ) {
                    attrs["content"]
                        ?.let(
                            values::add
                        )
                }
            }

        Regex(
            """(?is)<link\b[^>]*>"""
        )
            .findAll(
                html
            )
            .forEach {
                match ->

                val attrs =
                    attributes(
                        match.value
                    )

                val href =
                    attrs["href"]
                        .orEmpty()

                val asValue =
                    attrs["as"]
                        .orEmpty()
                        .lowercase(
                            Locale.US
                        )

                val type =
                    attrs["type"]
                        .orEmpty()
                        .lowercase(
                            Locale.US
                        )

                if (
                    href.isNotBlank() &&
                    (
                        asValue ==
                            "video" ||
                        type.startsWith(
                            "video/"
                        ) ||
                        looksLikeMediaPath(
                            href
                        )
                    )
                ) {
                    values += href
                }
            }

        Regex(
            """(?is)<a\b[^>]*>"""
        )
            .findAll(
                html
            )
            .forEach {
                match ->

                val href =
                    attributes(
                        match.value
                    )["href"]
                        .orEmpty()

                if (
                    looksLikeMediaPath(
                        href
                    )
                ) {
                    values += href
                }
            }

        return values
            .mapNotNull {
                value ->

                resolveCandidate(
                    base,
                    value
                )
            }
            .distinctBy {
                it.normalize()
                    .toString()
            }
    }

    private fun attributes(
        tag: String
    ): Map<String, String> {

        val result =
            linkedMapOf<String, String>()

        Regex(
            """(?is)([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>]+))"""
        )
            .findAll(
                tag
            )
            .forEach {
                match ->

                val name =
                    match.groupValues[1]
                        .lowercase(
                            Locale.US
                        )

                val raw =
                    sequenceOf(
                        match.groupValues[2],
                        match.groupValues[3],
                        match.groupValues[4]
                    )
                        .firstOrNull {
                            it.isNotEmpty()
                        }
                        .orEmpty()

                result[name] =
                    Html.fromHtml(
                        raw,
                        Html.FROM_HTML_MODE_LEGACY
                    )
                        .toString()
                        .trim()
            }

        return result
    }

    private fun resolveCandidate(
        base: URI,
        raw: String
    ): URI? {

        val value =
            raw.trim()

        if (
            value.isBlank() ||
            value.startsWith(
                "data:",
                true
            ) ||
            value.startsWith(
                "blob:",
                true
            ) ||
            value.startsWith(
                "javascript:",
                true
            )
        ) {
            return null
        }

        return runCatching {
            val resolved =
                base.resolve(
                    value
                )

            parseHttpUri(
                resolved.toString()
            )
        }
            .getOrNull()
    }

    private fun isPublicMedia(
        uri: URI,
        probe: Probe
    ): Boolean {

        if (
            isHtml(
                probe
            )
        ) {
            return false
        }

        val type =
            probe.contentType

        val text =
            probe.text
                .trimStart()
                .lowercase(
                    Locale.US
                )

        return type.startsWith(
            "video/"
        ) ||
            type.contains(
                "mpegurl"
            ) ||
            type ==
                "application/dash+xml" ||
            text.startsWith(
                "#extm3u"
            ) ||
            text.contains(
                "<mpd"
            ) ||
            looksLikeMediaPath(
                uri.toString()
            ) ||
            looksLikeIsoBmff(
                probe.bytes
            ) ||
            looksLikeEbml(
                probe.bytes
            )
    }

    private fun isHtml(
        probe: Probe
    ): Boolean {

        val text =
            probe.text
                .trimStart()
                .lowercase(
                    Locale.US
                )

        return probe.contentType ==
            "text/html" ||
            probe.contentType ==
            "application/xhtml+xml" ||
            text.startsWith(
                "<!doctype html"
            ) ||
            text.startsWith(
                "<html"
            ) ||
            text.contains(
                "<html"
            )
    }

    private fun looksLikeMediaPath(
        address: String
    ): Boolean {

        val path =
            runCatching {
                URI(
                    address
                )
                    .path
                    .orEmpty()
                    .lowercase(
                        Locale.US
                    )
            }
                .getOrDefault(
                    ""
                )

        return path.endsWith(
            ".mp4"
        ) ||
            path.endsWith(
                ".webm"
            ) ||
            path.endsWith(
                ".mov"
            ) ||
            path.endsWith(
                ".mkv"
            ) ||
            path.endsWith(
                ".m4v"
            ) ||
            path.endsWith(
                ".3gp"
            ) ||
            path.endsWith(
                ".m3u8"
            ) ||
            path.endsWith(
                ".mpd"
            )
    }

    private fun looksLikeIsoBmff(
        bytes: ByteArray
    ): Boolean =
        bytes.size >=
            8 &&
            bytes[4] ==
            'f'.code.toByte() &&
            bytes[5] ==
            't'.code.toByte() &&
            bytes[6] ==
            'y'.code.toByte() &&
            bytes[7] ==
            'p'.code.toByte()

    private fun looksLikeEbml(
        bytes: ByteArray
    ): Boolean =
        bytes.size >=
            4 &&
            bytes[0] ==
            0x1a.toByte() &&
            bytes[1] ==
            0x45.toByte() &&
            bytes[2] ==
            0xdf.toByte() &&
            bytes[3] ==
            0xa3.toByte()

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
                        "bytes=0-${PROBE_LIMIT - 1}"
                    )
                }

            val code =
                connection.responseCode

            require(
                code in 200..299
            ) {
                "Bağlantı HTTP $code döndürdü."
            }

            val type =
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
                        readLimited(
                            it,
                            PROBE_LIMIT
                        )
                    }

            return Probe(
                contentType = type,
                bytes = bytes
            )

        } finally {
            connection
                ?.disconnect()
        }
    }

    private fun fetchHtml(
        uri: URI
    ): String {

        var connection:
            HttpURLConnection? =
            null

        try {
            connection =
                openConnection(
                    uri
                ).apply {
                    setRequestProperty(
                        "Accept",
                        "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1"
                    )
                }

            val code =
                connection.responseCode

            require(
                code in 200..299
            ) {
                "Web sayfası HTTP $code döndürdü."
            }

            val announced =
                connection.contentLengthLong

            require(
                announced <
                    0L ||
                    announced <=
                    HTML_LIMIT
            ) {
                "Web sayfası güvenli boyut sınırını aştı."
            }

            val bytes =
                connection
                    .inputStream
                    .use {
                        readLimitedOrFail(
                            it,
                            HTML_LIMIT
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
                "VideoForge/5.3.1 Android"
            )
        }

    private fun parseHttpUri(
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
            "Yalnızca HTTP/HTTPS medya bağlantıları destekleniyor."
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

    private fun readLimited(
        input: InputStream,
        limit: Int
    ): ByteArray {

        val out =
            ByteArrayOutputStream()

        val buffer =
            ByteArray(
                16 * 1024
            )

        while (
            out.size() <
            limit
        ) {
            val remaining =
                limit -
                    out.size()

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
                out.write(
                    buffer,
                    0,
                    count
                )
            }
        }

        return out.toByteArray()
    }

    private fun readLimitedOrFail(
        input: InputStream,
        limit: Int
    ): ByteArray {

        val out =
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
                "Web sayfası güvenli boyut sınırını aştı."
            }

            out.write(
                buffer,
                0,
                count
            )
        }

        return out.toByteArray()
    }
}
