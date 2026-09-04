package com.appforge.studio.terminal

import java.io.ByteArrayOutputStream
import java.net.URI

internal data class LspPosition(
    val line: Int,
    val character: Int
) {
    init {
        require(line >= 0)
        require(character >= 0)
    }
}

internal data class LspRange(
    val start: LspPosition,
    val end: LspPosition
)

internal data class LspDiagnostic(
    val range: LspRange,
    val severity: Int?,
    val message: String,
    val source: String?
)

internal data class LspCompletionItem(
    val label: String,
    val insertText: String,
    val detail: String?
)

internal data class LspLocation(
    val uri: String,
    val range: LspRange
)

internal object LspDocumentPath {
    private const val WORKSPACE_PREFIX =
        "file:///workspace/"

    fun toWorkspaceUri(
        relativePath: String
    ): String {
        val normalized =
            normalizeRelativePath(
                relativePath
            )

        return URI(
            "file",
            "",
            "/workspace/$normalized",
            null
        ).toASCIIString()
    }

    fun fromWorkspaceUri(
        uri: String
    ): String? {
        val parsed =
            runCatching {
                URI(uri)
            }.getOrNull()
                ?: return null

        if (
            !parsed.scheme.equals(
                "file",
                ignoreCase = true
            )
        ) {
            return null
        }

        val path =
            parsed.path
                ?: return null

        if (!path.startsWith("/workspace/")) {
            return null
        }

        return runCatching {
            normalizeRelativePath(
                path.removePrefix(
                    "/workspace/"
                )
            )
        }.getOrNull()
    }

    fun offsetFor(
        text: String,
        position: LspPosition
    ): Int? {
        var line = 0
        var offset = 0

        if (position.line == 0) {
            return minOf(
                position.character,
                text.indexOf('\n')
                    .takeIf { it >= 0 }
                    ?: text.length
            )
        }

        while (offset < text.length) {
            if (text[offset] == '\n') {
                line += 1

                if (line == position.line) {
                    val lineStart =
                        offset + 1

                    val lineEnd =
                        text.indexOf(
                            '\n',
                            lineStart
                        ).takeIf {
                            it >= 0
                        } ?: text.length

                    return minOf(
                        lineStart +
                            position.character,
                        lineEnd
                    )
                }
            }

            offset += 1
        }

        return if (
            position.line == line + 1 &&
            text.endsWith("\n")
        ) {
            text.length
        } else {
            null
        }
    }

    private fun normalizeRelativePath(
        relativePath: String
    ): String {
        require(
            relativePath.isNotBlank() &&
                relativePath.length <= 2_048 &&
                '\u0000' !in relativePath
        ) {
            "LSP dosya yolu geçersiz."
        }

        val normalized =
            relativePath
                .replace('\\', '/')
                .removePrefix("/")

        val parts =
            normalized
                .split('/')

        require(
            parts.isNotEmpty() &&
                parts.none {
                    it.isBlank() ||
                        it == "." ||
                        it == ".."
                }
        ) {
            "LSP dosya yolu çalışma alanının dışında."
        }

        return parts.joinToString("/")
    }
}

internal class LspContentLengthFramer {
    private val pending =
        ByteArrayOutputStream()

    fun feed(
        bytes: ByteArray,
        count: Int = bytes.size
    ): List<String> {
        require(
            count in 0..bytes.size
        ) {
            "LSP byte sayısı geçersiz."
        }

        if (count == 0) {
            return emptyList()
        }

        pending.write(
            bytes,
            0,
            count
        )

        require(
            pending.size() <= MAX_BUFFER_BYTES
        ) {
            "LSP giriş tamponu sınırı aşıldı."
        }

        val messages =
            mutableListOf<String>()

        while (true) {
            val all =
                pending.toByteArray()

            val headerEnd =
                indexOfHeaderEnd(all)

            if (headerEnd < 0) {
                break
            }

            val header =
                String(
                    all,
                    0,
                    headerEnd,
                    Charsets.US_ASCII
                )

            val length =
                parseContentLength(
                    header
                )

            val bodyStart =
                headerEnd + 4

            val total =
                bodyStart + length

            if (all.size < total) {
                break
            }

            messages +=
                String(
                    all,
                    bodyStart,
                    length,
                    Charsets.UTF_8
                )

            pending.reset()

            if (all.size > total) {
                pending.write(
                    all,
                    total,
                    all.size - total
                )
            }
        }

        return messages
    }

    private fun parseContentLength(
        header: String
    ): Int {
        val values =
            header
                .lineSequence()
                .mapNotNull { line ->
                    val separator =
                        line.indexOf(':')

                    if (separator <= 0) {
                        return@mapNotNull null
                    }

                    val name =
                        line
                            .substring(
                                0,
                                separator
                            )
                            .trim()

                    if (
                        !name.equals(
                            "Content-Length",
                            ignoreCase = true
                        )
                    ) {
                        return@mapNotNull null
                    }

                    line
                        .substring(
                            separator + 1
                        )
                        .trim()
                }
                .toList()

        require(values.size == 1) {
            "LSP Content-Length başlığı geçersiz."
        }

        val length =
            values.single()
                .toIntOrNull()
                ?: error(
                    "LSP Content-Length sayı değil."
                )

        require(
            length in 0..MAX_MESSAGE_BYTES
        ) {
            "LSP mesajı boyut sınırını aşıyor."
        }

        return length
    }

    private fun indexOfHeaderEnd(
        bytes: ByteArray
    ): Int {
        var index = 0

        while (index + 3 < bytes.size) {
            if (
                bytes[index] == '\r'.code.toByte() &&
                bytes[index + 1] == '\n'.code.toByte() &&
                bytes[index + 2] == '\r'.code.toByte() &&
                bytes[index + 3] == '\n'.code.toByte()
            ) {
                return index
            }

            index += 1
        }

        return -1
    }

    companion object {
        private const val MAX_MESSAGE_BYTES =
            2 * 1_024 * 1_024

        private const val MAX_BUFFER_BYTES =
            MAX_MESSAGE_BYTES + 16 * 1_024
    }
}
