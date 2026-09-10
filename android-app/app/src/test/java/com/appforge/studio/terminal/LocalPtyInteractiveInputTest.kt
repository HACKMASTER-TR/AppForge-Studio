package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class LocalPtyInteractiveInputTest {

    @Test
    fun standaloneEnterAlwaysSubmitsInBracketedPasteMode() {
        val result =
            localPtyBracketedPasteDispatch(
                delta =
                    "\n",
                pendingPaste =
                    null,
                bracketedPasteEnabled =
                    true
            )

        assertEquals(
            "\r",
            result.ptyText
        )

        assertNull(
            result.pendingPaste
        )

        assertTrue(
            result.resetIme
        )
    }


    @Test
    fun standaloneEnterClearsStalePasteState() {
        val result =
            localPtyBracketedPasteDispatch(
                delta =
                    "\n",
                pendingPaste =
                    "gh auth login",
                bracketedPasteEnabled =
                    false
            )

        assertEquals(
            "\r",
            result.ptyText
        )

        assertNull(
            result.pendingPaste
        )

        assertTrue(
            result.resetIme
        )
    }


    @Test
    fun promptShortcutSubmitsAnswerAtomically() {
        assertEquals(
            "y\r",
            localPtyPromptAnswer(
                'Y'
            )
        )

        assertEquals(
            "n\r",
            localPtyPromptAnswer(
                'n'
            )
        )
    }


    @Test
    fun imeCompositionIsNotWrappedAsBracketedPaste() {
        val result =
            localPtyBracketedPasteDispatch(
                delta = "ab",
                pendingPaste = null,
                bracketedPasteEnabled = true,
                imeComposing = true
            )

        assertEquals(
            "ab",
            result.ptyText
        )

        assertNull(
            result.pendingPaste
        )

        assertFalse(
            result.resetIme
        )
    }


    @Test
    fun singleLineBurstIsNotWrappedAsBracketedPaste() {
        val result =
            localPtyBracketedPasteDispatch(
                delta = "ab",
                pendingPaste = null,
                bracketedPasteEnabled = true,
                imeComposing = false
            )

        assertEquals(
            "ab",
            result.ptyText
        )

        assertNull(
            result.pendingPaste
        )

        assertFalse(
            result.resetIme
        )
    }


    @Test
    fun multiLinePasteStillUsesBracketedPaste() {
        val result =
            localPtyBracketedPasteDispatch(
                delta = "echo one\necho two",
                pendingPaste = null,
                bracketedPasteEnabled = true,
                imeComposing = false
            )

        assertEquals(
            "\u001b[200~echo one\necho two\u001b[201~",
            result.ptyText
        )

        assertEquals(
            "echo one\necho two",
            result.pendingPaste
        )

        assertTrue(
            result.resetIme
        )
    }




    @Test
    fun twentyThousandLinePasteIsPreserved() {
        val payload =
            (1..20_000)
                .joinToString(
                    "\n"
                ) { index ->
                    "echo line-$index"
                }

        assertEquals(
            20_000,
            payload.count {
                it == '\n'
            } + 1
        )

        val dispatch =
            localPtyBracketedPasteDispatch(
                delta =
                    payload,
                pendingPaste =
                    null,
                bracketedPasteEnabled =
                    true,
                imeComposing =
                    false
            )

        assertEquals(
            "\u001b[200~" +
                payload +
                "\u001b[201~",
            dispatch.ptyText
        )

        assertEquals(
            payload,
            dispatch.pendingPaste
        )

        assertTrue(
            dispatch.resetIme
        )

        val chunks =
            localPtyInputWriteChunks(
                dispatch.ptyText
            ).toList()

        assertTrue(
            chunks.size > 1
        )

        assertTrue(
            chunks.all {
                it.length <= 16_384
            }
        )

        assertEquals(
            dispatch.ptyText,
            chunks.joinToString("")
        )
    }


    @Test
    fun chunkingDoesNotSplitUnicodeSurrogatePairs() {
        val text =
            "1234567😀ABCDEFG"

        val chunks =
            localPtyInputWriteChunks(
                text = text,
                maxChunkChars = 8
            ).toList()

        assertEquals(
            text,
            chunks.joinToString("")
        )

        chunks.forEach { chunk ->
            if (
                chunk.isNotEmpty()
            ) {
                assertFalse(
                    chunk.last()
                        .isHighSurrogate()
                )
            }
        }
    }


    @Test
    fun rawYesNoPromptKeysPassThroughUnchanged() {
        listOf(
            "y",
            "n"
        ).forEach {
            key ->

            val result =
                localPtyBracketedPasteDispatch(
                    delta =
                        key,
                    pendingPaste =
                        null,
                    bracketedPasteEnabled =
                        false
                )

            assertEquals(
                key,
                result.ptyText
            )

            assertNull(
                result.pendingPaste
            )

            assertFalse(
                result.resetIme
            )
        }
    }
}
