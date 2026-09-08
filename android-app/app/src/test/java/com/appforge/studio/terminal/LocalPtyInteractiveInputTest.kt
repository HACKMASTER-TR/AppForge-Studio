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
