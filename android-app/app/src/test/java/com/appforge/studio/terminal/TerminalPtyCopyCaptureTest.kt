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


    @Test fun distinctPtySessionsNeverShareCapturedOutput() {
        val tokenTwo = "fedcba9876543210fedcba9876543210"
        val otherBegin = "\u001b]777;afcopy-begin=$tokenTwo\u0007"
        val otherEnd = "\u001b]777;afcopy-end=$tokenTwo;0\u0007"
        val sessionOne = TerminalPtyCopyCapture(token)
        val sessionTwo = TerminalPtyCopyCapture(tokenTwo)
        assertTrue(sessionOne.feed(begin + "SESSION_1_PARTIAL\r\n").isEmpty())
        assertTrue(sessionTwo.feed(otherBegin + "SESSION_2_ONLY\r\n" + otherEnd)
            .single().output == "SESSION_2_ONLY")
        assertTrue(sessionOne.feed(end(0)).single().output == "SESSION_1_PARTIAL")
        assertTrue(sessionTwo.feed(begin + "FOREIGN\r\n" + end(0)).isEmpty())
    }

    @Test fun partialAndBadExitMarkersFailClosed() {
        for (status in listOf(-1, 130, 143, 256, 999)) {
            val parser = TerminalPtyCopyCapture(token)
            assertTrue(parser.feed(begin + "UNSAFE_TO_COPY\r\n" + end(status)).isEmpty())
        }
        val dangling = TerminalPtyCopyCapture(token)
        assertTrue(dangling.feed(begin + "NOT_FINISHED\r\n").isEmpty())
        assertTrue(dangling.feed(end(0).dropLast(1)).isEmpty())
        assertEquals("NOT_FINISHED", dangling.feed("\u0007").single().output)
    }

    @Test fun alternateScreenMarkerSplitAcrossChunksRemainsUnsupported() {
        val parser = TerminalPtyCopyCapture(token)
        assertTrue(parser.feed(begin + "\u001b[?10").isEmpty())
        assertTrue(parser.feed("49hfullscreen\r\n\u001b[?1049l" + end(0)).isEmpty())
    }

    @Test fun fakeCredentialsBlockAutomaticCopy() {
        val prefixes = listOf(
            "password=INERT_TEST_VALUE", "api_key: INERT_TEST_VALUE",
            "access_token=INERT_TEST_VALUE", "Authorization: Bearer INERT_TEST_VALUE",
            "-----BEGIN PRIVATE KEY-----\r\nINERT_TEST_VALUE"
        )
        for (text in prefixes) {
            val parser = TerminalPtyCopyCapture(token)
            val result = parser.feed(begin + text + "\r\n" + end(0)).single()
            assertEquals("SENSITIVE_OUTPUT", result.reason)
            assertTrue(result.output.isEmpty())
        }
    }

    @Test fun overflowOnExactByteBudgetAndSplitChunks() {
        val limit = TerminalPtyCopyCapture.MAX_COPY_BYTES
        val parser = TerminalPtyCopyCapture(token)
        assertTrue(parser.feed(begin).isEmpty())
        repeat(65) { assertTrue(parser.feed("x".repeat(8192)).isEmpty()) }
        val result = parser.feed(end(0)).single()
        assertEquals("OUTPUT_LIMIT", result.reason)
        assertTrue(result.output.isEmpty())
        assertTrue(limit < 65 * 8192)
    }

    @Test fun lostScrollbackFailsClosedInsteadOfSilentlyTruncating() {
        // Tiny injected scrollback budget avoids allocating a 20K-line test fixture.
        val parser = TerminalPtyCopyCapture(token, historyLimit = 3)
        val lines = (1..40).joinToString("\r\n") { "line-$it" }
        val result = parser.feed(begin + lines + "\r\n" + end(0)).single()
        assertEquals("OUTPUT_INCOMPLETE", result.reason)
        assertTrue(result.output.isEmpty())
    }

    @Test fun clearedTerminalHistoryDoesNotSilentlyCopyOnlySuffix() {
        val parser = TerminalPtyCopyCapture(token)
        val result = parser.feed(begin + "BEFORE_CLEAR\r\n\u000cAFTER_CLEAR\r\n" + end(0)).single()
        assertEquals("OUTPUT_INCOMPLETE", result.reason)
        assertTrue(result.output.isEmpty())
    }

    @Test fun resetMakesNewCommandsIndependentOfEarlierOverflow() {
        val parser = TerminalPtyCopyCapture(token, historyLimit = 4)
        val longOutput = (1..50).joinToString("\r\n") { "before-$it" }
        assertEquals("OUTPUT_INCOMPLETE", parser.feed(begin + longOutput + end(0)).single().reason)
        parser.reset()
        val valid = parser.feed(begin + "AFTER_RESET\r\n" + end(0)).single()
        assertEquals("AFTER_RESET", valid.output)
        assertEquals(null, valid.reason)
    }

    @Test fun bareEndNeverCopiesAndEmptyIsSkipped() {
        val parser = TerminalPtyCopyCapture(token)
        assertTrue(parser.feed(end(0)).isEmpty())
        assertTrue(parser.feed(begin + end(0)).isEmpty())
    }
}
