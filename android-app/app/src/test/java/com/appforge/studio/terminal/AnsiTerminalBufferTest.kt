package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiTerminalBufferTest {
    @Test
    fun interpretsSgrWithoutLeakingEscapeCodes() {
        val buffer =
            AnsiTerminalBuffer(
                initialRows = 4,
                initialColumns = 40
            )

        buffer.feed(
            "normal \u001b[31mred\u001b[0m done"
        )

        val snapshot =
            buffer.snapshot(
                includeScrollback = false
            )

        assertEquals(
            "normal red done",
            snapshot.plainText()
        )

        val redCell =
            snapshot.lines[0][7]

        assertEquals(
            0xCD3131,
            redCell.style.foregroundRgb
        )

        assertFalse(
            snapshot.plainText()
                .contains('\u001b')
        )
    }

    @Test
    fun supportsCursorMovementAndLineErase() {
        val buffer =
            AnsiTerminalBuffer(
                initialRows = 4,
                initialColumns = 20
            )

        buffer.feed("hello")
        buffer.feed("\u001b[2D")
        buffer.feed("XY")
        buffer.feed("\u001b[K")

        assertEquals(
            "helXY",
            buffer
                .snapshot(false)
                .plainText()
        )
    }

    @Test
    fun resizesAndKeepsCursorInsideBounds() {
        val buffer =
            AnsiTerminalBuffer(
                initialRows = 24,
                initialColumns = 80
            )

        buffer.feed("appforge")
        buffer.resize(
            newRows = 10,
            newColumns = 32
        )

        val snapshot =
            buffer.snapshot(false)

        assertEquals(10, snapshot.rows)
        assertEquals(32, snapshot.columns)
        assertTrue(
            snapshot.cursorLine in
                0 until snapshot.rows
        )
        assertTrue(
            snapshot.cursorColumn in
                0 until snapshot.columns
        )
    }
}
