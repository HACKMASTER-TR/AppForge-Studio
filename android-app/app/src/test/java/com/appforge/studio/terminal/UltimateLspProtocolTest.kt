package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateLspProtocolTest {
    @Test
    fun parsesChunkedContentLengthFrames() {
        val first = "{\"jsonrpc\":\"2.0\",\"id\":1}"
        val second = "{\"method\":\"initialized\"}"

        val payload =
            frame(first) + frame(second)

        val bytes =
            payload.toByteArray(
                Charsets.UTF_8
            )

        val framer =
            LspContentLengthFramer()

        val messages =
            buildList {
                addAll(
                    framer.feed(
                        bytes.copyOfRange(0, 11)
                    )
                )
                addAll(
                    framer.feed(
                        bytes.copyOfRange(
                            11,
                            bytes.size
                        )
                    )
                )
            }

        assertEquals(
            listOf(first, second),
            messages
        )
    }

    @Test
    fun workspaceUriNeverAcceptsParentTraversal() {
        assertEquals(
            "file:///workspace/src/main.ts",
            LspDocumentPath.toWorkspaceUri(
                "src/main.ts"
            )
        )

        assertEquals(
            "src/main.ts",
            LspDocumentPath.fromWorkspaceUri(
                "file:///workspace/src/main.ts"
            )
        )

        assertNull(
            LspDocumentPath.fromWorkspaceUri(
                "file:///etc/passwd"
            )
        )

        val failed =
            runCatching {
                LspDocumentPath.toWorkspaceUri(
                    "../secret.txt"
                )
            }

        assertTrue(failed.isFailure)
    }

    @Test
    fun convertsLspPositionToEditorOffset() {
        val text = "one\ntwo\nthree"

        assertEquals(
            5,
            LspDocumentPath.offsetFor(
                text,
                LspPosition(
                    line = 1,
                    character = 1
                )
            )
        )
    }

    private fun frame(
        body: String
    ): String {
        val size =
            body.toByteArray(
                Charsets.UTF_8
            ).size

        return "Content-Length: $size\r\n\r\n$body"
    }
}
