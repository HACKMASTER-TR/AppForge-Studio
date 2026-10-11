package com.appforge.studio.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSafeAutoCopyPolicyTest {
    private fun result(command: String = "appforge help") =
        TerminalCopyResult.execute(command) {}!!

    @Test fun defaultsOffEvenForVerifiedAdministrator() {
        val policy = TerminalSafeAutoCopyPolicy()
        assertFalse(policy.enabled)
        assertTrue(policy.begin("appforge help", "one") == null)
    }

    @Test fun onlyCompletedAppOwnedHelpIsAccepted() {
        val policy = TerminalSafeAutoCopyPolicy()
        assertTrue(policy.setEnabled(true, true))
        val ticket = policy.begin("appforge help", "one")!!
        assertTrue(policy.permits(ticket, result(), true, "one"))
        for (origin in TerminalCopyOrigin.values()) {
            for (completion in TerminalCopyCompletion.values()) {
                assertTrue(TerminalSafeAutoCopyPolicy.acceptsPayload("appforge help",
                    TerminalSafeHelp.text, origin, completion) ==
                    (origin == TerminalCopyOrigin.FIRST_PARTY && completion == TerminalCopyCompletion.COMPLETED))
            }
        }
    }

    @Test fun adminDenialBlocksEnableAndCopyAfterRevocation() {
        val policy = TerminalSafeAutoCopyPolicy()
        assertFalse(policy.setEnabled(true, false))
        policy.setEnabled(true, true)
        val ticket = policy.begin("appforge help", "one")!!
        assertFalse(policy.permits(ticket, result(), false, "one"))
    }

    @Test fun adminResponseMustMatchAuthenticatedAccountAndCurrentRole() {
        assertTrue(TerminalAdminCopyDecision.permits(true, "admin", true, "admin@example.invalid", "admin@example.invalid"))
        assertFalse(TerminalAdminCopyDecision.permits(false, "admin", true, "a", "a"))
        assertFalse(TerminalAdminCopyDecision.permits(true, "pro", true, "a", "a"))
        assertFalse(TerminalAdminCopyDecision.permits(true, "admin", false, "a", "a"))
        assertFalse(TerminalAdminCopyDecision.permits(true, "admin", true, "a", "b"))
        assertFalse(TerminalAdminCopyDecision.permits(true, "admin", true, "", ""))
    }

    @Test fun disablingOrReenablingInvalidatesInFlightEvents() {
        val policy = TerminalSafeAutoCopyPolicy()
        policy.setEnabled(true, true)
        val pending = policy.begin("appforge help", "one")!!
        policy.setEnabled(false, false)
        assertFalse(policy.permits(pending, result(), true, "one"))
        policy.setEnabled(true, true)
        assertFalse(policy.permits(pending, result(), true, "one"))
    }

    @Test fun safePayloadContainsOnlyStaticHelp() {
        assertTrue(TerminalSafeHelp.text.startsWith("AppForge özel komutları"))
        assertTrue(TerminalSafeHelp.text.contains("appforge build"))
    }

    @Test fun safeFirstPartyResultsPropagateActualTextAndIdentity() {
        for (command in listOf("help", "appforge help", "runtime", "connections", "appforge connect", "appforge downloads")) {
            var displayed = ""
            val result = TerminalCopyResult.execute(command) { displayed = it }!!
            val policy = TerminalSafeAutoCopyPolicy()
            policy.setEnabled(true, true)
            val ticket = policy.begin(command, "one")!!
            assertTrue(policy.claim(ticket, result, "one"))
            assertTrue(policy.consume(ticket, result, true, "one") == displayed)
            assertFalse(policy.claim(ticket, result, "one"))
            assertTrue(policy.consume(ticket, result, true, "one") == null)
            if (command == "runtime") assertTrue(displayed == "Araç durumu ekranı açıldı.")
        }
    }

    @Test fun untrustedDynamicOversizedAndMalformedPayloadsAreDenied() {
        for (command in listOf("pwd", "appforge status", "appforge projects", "appforge build", "appforge ai",
            "appforge import", "git status", "runtime; pwd", "runtime ", "RUNTIME")) {
            var invoked = false
            assertTrue(TerminalCopyResult.execute(command) { invoked = true } == null)
            assertFalse(invoked)
        }
        for (body in listOf("", "INERT_TEST_CREDENTIAL", "x".repeat(4097), "\u0000", "\u001b[31m", "\uD800",
            TerminalSafeHelp.text + "\nINERT_TEST_CREDENTIAL")) {
            assertFalse(TerminalSafeAutoCopyPolicy.acceptsPayload("appforge help", body,
                TerminalCopyOrigin.FIRST_PARTY, TerminalCopyCompletion.COMPLETED))
        }
    }

    @Test fun sessionSwitchChangedCommandAndRevisionBlockStaleResults() {
        val policy = TerminalSafeAutoCopyPolicy()
        policy.setEnabled(true, true)
        val ticket = policy.begin("appforge help", "one")!!
        assertFalse(policy.claim(ticket, result(), "two"))
        assertFalse(policy.claim(ticket, result("runtime"), "one"))
        policy.begin("pwd", "one")
        assertFalse(policy.claim(ticket, result(), "one"))
    }

    @Test fun lifecycleStopCancellationAndTimeoutCannotProduceCompletion() {
        val policy = TerminalSafeAutoCopyPolicy()
        policy.setEnabled(true, true)
        val ticket = policy.begin("runtime", "one")!!
        policy.setEnabled(false, false) // The screen's stop/cancel invalidation.
        assertFalse(policy.claim(ticket, result("runtime"), "one"))
        for (completion in listOf(TerminalCopyCompletion.CANCELLED, TerminalCopyCompletion.TIMED_OUT,
            TerminalCopyCompletion.INCOMPLETE)) {
            assertFalse(TerminalSafeAutoCopyPolicy.acceptsPayload("runtime", result("runtime").text,
                TerminalCopyOrigin.FIRST_PARTY, completion))
        }
        var returned = false
        try {
            TerminalCopyResult.execute("runtime") { throw IllegalStateException("inert failure") }
            returned = true
        } catch (_: IllegalStateException) { }
        assertFalse(returned)
    }

    @Test fun ptyAndLocalShellBodiesRemainUnsupportedEvenWhenCanonicalOrCompleted() {
        for (origin in listOf(TerminalCopyOrigin.PTY, TerminalCopyOrigin.LOCAL_SHELL, TerminalCopyOrigin.UNKNOWN)) {
            assertFalse(TerminalSafeAutoCopyPolicy.acceptsPayload("runtime", result("runtime").text,
                origin, TerminalCopyCompletion.COMPLETED))
        }
    }
}
