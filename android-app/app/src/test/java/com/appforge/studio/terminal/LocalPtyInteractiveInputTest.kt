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
    fun realClipboardBurstStillUsesBracketedPaste() {
        val result =
            localPtyBracketedPasteDispatch(
                delta = "ab",
                pendingPaste = null,
                bracketedPasteEnabled = true,
                imeComposing = false
            )

        assertEquals(
            "\u001b[200~ab\u001b[201~",
            result.ptyText
        )

        assertEquals(
            "ab",
            result.pendingPaste
        )

        assertTrue(
            result.resetIme
        )
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
