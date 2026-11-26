package com.appforge.studio.security

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Executable pure-JVM offline acceptance without Android/Gradle/network/Google credentials. */
private fun <T> runNow(block: suspend () -> T): T {
    var output: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { output = result }
    })
    return checkNotNull(output) { "Unexpected suspension in isolated fixture" }.getOrThrow()
}
private inline fun rejects(block: () -> Unit) {
    var denied = false
    try { block() } catch (_: IllegalArgumentException) { denied = true }
    catch (_: IllegalStateException) { denied = true }
    check(denied) { "Expected fail-closed denial" }
}
private const val NOW = 1_800_000_000_000L
private val DEVICE = "a".repeat(64)
private val TOKEN = "a".repeat(30) + "." + "b".repeat(35) + "." + "c".repeat(70)
private fun challenge(t: Long = NOW + 150_000L) =
    V9AdminNonceWire.ServerChallenge("b".repeat(32), "N".repeat(43), t)

private class Fixture {
    var at = NOW
    var serverCalls = 0
    var googleCalls = 0
    var submitCalls = 0
    var supplied = challenge()
    var token = TOKEN
    var result = V9AdminDecisionResult(DEVICE, "active")
    var lastNonce = ""
    var last: V9AdminDecisionRequest? = null
    var onGoogle: (() -> Unit)? = null
    var onSubmit: (() -> Unit)? = null
    fun bridge(): V9AdminSessionBridge = V9AdminSessionBridge(
        V9AdminSessionSource { serverCalls++; supplied },
        V9GoogleTokenSource { nonce ->
            googleCalls++; lastNonce = nonce; onGoogle?.invoke(); token
        },
        V9AdminDecisionSink { req ->
            submitCalls++; last = req; onSubmit?.invoke(); result
        },
        { at }
    )
}

fun main() {
    var passed = 0
    fun test(name: String, block: () -> Unit) { block(); passed++; println("S5E2_CASE_${passed}=PASS") }
    test("happy approve") {
        val x = Fixture(); val response = runNow { x.bridge().decide(DEVICE, "approve") }
        check(response.status == "active" && x.serverCalls == 1 && x.googleCalls == 1 && x.submitCalls == 1)
        check(x.lastNonce == x.supplied.nonce && x.last!!.sessionId == x.supplied.sessionId)
        check(x.last!!.deviceId == DEVICE && x.last!!.action == "approve" && x.last!!.idToken == TOKEN)
    }
    test("reject binds expected status") {
        val x = Fixture(); x.result = V9AdminDecisionResult(DEVICE, "rejected")
        check(runNow { x.bridge().decide(DEVICE, "reject") }.status == "rejected")
    }
    test("revoke binds expected status") {
        val x = Fixture(); x.result = V9AdminDecisionResult(DEVICE, "revoked")
        check(runNow { x.bridge().decide(DEVICE, "revoke") }.status == "revoked")
    }
    test("bad device fails before server") {
        val x = Fixture(); rejects { runNow { x.bridge().decide("BAD", "approve") } }
        check(x.serverCalls == 0)
    }
    test("bad action fails before server") {
        val x = Fixture(); rejects { runNow { x.bridge().decide(DEVICE, "grantOwner") } }
        check(x.serverCalls == 0)
    }
    test("expired challenge rejects before Google") {
        val x = Fixture(); x.supplied = challenge(NOW - 1)
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
        check(x.googleCalls == 0 && x.submitCalls == 0)
    }
    test("overlong challenge rejects") {
        val x = Fixture(); x.supplied = challenge(NOW + 180_001)
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
        check(x.googleCalls == 0)
    }
    test("malformed session rejects") {
        val x = Fixture(); x.supplied = challenge().copy(sessionId = "not-a-session")
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
        check(x.googleCalls == 0)
    }
    test("malformed nonce rejects") {
        val x = Fixture(); x.supplied = challenge().copy(nonce = "LOCAL")
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
        check(x.googleCalls == 0)
    }
    test("expired during Credential Manager rejects before submit") {
        val x = Fixture(); x.onGoogle = { x.at = NOW + 150_000 }
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
        check(x.submitCalls == 0)
    }
    test("malformed JWT denied") {
        val x = Fixture(); x.token = "raw.email.or.claims"
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
        check(x.submitCalls == 0)
    }
    test("oversize JWT denied") {
        val x = Fixture(); x.token = "a".repeat(8193)
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
        check(x.submitCalls == 0)
    }
    test("server result cannot select other device") {
        val x = Fixture(); x.result = V9AdminDecisionResult("d".repeat(64), "active")
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
        check(x.submitCalls == 1)
    }
    test("server status mismatch denied") {
        val x = Fixture(); x.result = V9AdminDecisionResult(DEVICE, "pending")
        rejects { runNow { x.bridge().decide(DEVICE, "approve") } }
    }
    test("same bridge rejects nested request") {
        val x = Fixture(); val bridge = x.bridge()
        x.onSubmit = { rejects { runNow { bridge.decide(DEVICE, "approve") } } }
        runNow { bridge.decide(DEVICE, "approve") }
        check(x.serverCalls == 1 && x.submitCalls == 1)
    }
    test("failed attempt releases in-flight guard") {
        val x = Fixture(); val bridge = x.bridge(); x.token = "bad"
        rejects { runNow { bridge.decide(DEVICE, "approve") } }
        x.token = TOKEN
        check(runNow { bridge.decide(DEVICE, "approve") }.status == "active")
        check(x.serverCalls == 2 && x.submitCalls == 1)
    }
    test("Google token never appears in result") {
        val x = Fixture()
        val response = runNow { x.bridge().decide(DEVICE, "approve") }
        check(response.toString().contains(TOKEN).not())
    }
    check(passed == 17)
    println("S5E2_KOTLIN_JVM=PASS;TESTS=$passed;FAIL=0")
}
