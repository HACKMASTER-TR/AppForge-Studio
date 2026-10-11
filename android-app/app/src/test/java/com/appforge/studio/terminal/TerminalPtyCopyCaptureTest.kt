package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPtyCopyCaptureTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val begin = "\u001b]777;afcopy-begin=$token\u0007"
    private fun end(code: Int) = "\u001b]777;afcopy-end=$token;$code\u0007"

    @Test fun bashCommandCompletionAcrossArbitraryChunks() {
        val parser = TerminalPtyCopyCapture(token)
        assertTrue(parser.feed("pwd\r\n" + begin.substring(0, 12)).isEmpty())
        assertTrue(parser.feed(begin.substring(12) + "/work").isEmpty())
        val results = parser.feed("space\r\n" + end(0))
        assertEquals(1, results.size)
        assertEquals("/workspace", results.single().output)
        assertEquals(0, results.single().exitCode)
    }

    @Test fun oneCopyPerCompletedCommandAndMultipleCommands() {
        val parser = TerminalPtyCopyCapture(token)
        val one = parser.feed(begin + "one\r\n" + end(0))
        val two = parser.feed(begin + "two\r\n" + end(1))
        assertEquals("one", one.single().output)
        assertEquals("two", two.single().output)
        assertTrue(two.single().sequence > one.single().sequence)
        assertTrue(parser.feed(end(0)).isEmpty())
    }

    @Test fun refusesMissingEndOrUnknownSessionMarker() {
        val parser = TerminalPtyCopyCapture(token)
        assertTrue(parser.feed("hello\r\n").isEmpty())
        assertTrue(parser.feed("\u001b]777;afcopy-begin=00000000000000000000000000000000\u0007secret\r\n").isEmpty())
        assertTrue(parser.feed(begin + "partial\r\n").isEmpty())
        parser.reset()
        assertTrue(parser.feed(end(0)).isEmpty())
    }

    @Test fun interruptedAndOversizeOutputsNeverCopy() {
        val interrupted = TerminalPtyCopyCapture(token)
        interrupted.feed(begin + "partial\r\n")
        val beforeCancel = interrupted.latestSequence
        interrupted.cancel()
        assertTrue(interrupted.latestSequence > beforeCancel)
        assertTrue(interrupted.feed(end(130)).isEmpty())
        val large = TerminalPtyCopyCapture(token)
        large.feed(begin)
        large.feed("A".repeat(TerminalPtyCopyCapture.MAX_COPY_BYTES + 5))
        val oversize = large.feed(end(0)).single()
        assertTrue(oversize.output.isEmpty())
        assertEquals("OUTPUT_LIMIT", oversize.reason)
    }

    @Test fun ansiColorsAreRenderedAsReadableText() {
        val parser = TerminalPtyCopyCapture(token)
        val result = parser.feed(begin + "\u001b[32mgreen\u001b[0m\r\n" + end(0))
        assertEquals("green", result.single().output)
        assertFalse(result.single().output.contains('\u001b'))
    }

    @Test fun sensitiveTokensAndAlternateScreensDoNotCopy() {
        val p = TerminalPtyCopyCapture(token)
        val sensitive = p.feed(begin + "password=do-not-copy\r\n" + end(0)).single()
        assertEquals("SENSITIVE_OUTPUT", sensitive.reason)
        assertTrue(sensitive.output.isEmpty())
        val fullScreen = TerminalPtyCopyCapture(token)
        assertTrue(fullScreen.feed(begin + "\u001b[?1049hvim\u001b[?1049l" + end(0)).isEmpty())
    }

    @Test fun bareEndNeverCopiesAndEmptyIsSkipped() {
        val parser = TerminalPtyCopyCapture(token)
        assertTrue(parser.feed(end(0)).isEmpty())
        assertTrue(parser.feed(begin + end(0)).isEmpty())
    }
}
