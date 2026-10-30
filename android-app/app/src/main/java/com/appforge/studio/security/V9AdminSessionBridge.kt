package com.appforge.studio.security

import java.util.concurrent.atomic.AtomicBoolean

/** V9-only offline contract; intentionally not connected to any Activity, URL or owner gate.
 * Session source and submitter MUST be trusted, bound to a server-authenticated endpoint.
 * Neither JWT claims nor client-side state ever grant administrator privileges.
 */
internal fun interface V9AdminSessionSource {
    suspend fun begin(): V9AdminNonceWire.ServerChallenge
}

/** Request exclusively from Credential Manager with the exact server nonce. */
internal fun interface V9GoogleTokenSource {
    suspend fun requestIdToken(serverNonce: String): String
}

internal data class V9AdminDecisionRequest(
    val sessionId: String,
    val deviceId: String,
    val action: String,
    val idToken: String
)

internal data class V9AdminDecisionResult(
    val deviceId: String,
    val status: String
)

internal fun interface V9AdminDecisionSink {
    suspend fun submit(request: V9AdminDecisionRequest): V9AdminDecisionResult
}

/** No local token generation, storage, retries, bypass, network endpoint or permission grant. */
internal class V9AdminSessionBridge(
    private val sessions: V9AdminSessionSource,
    private val google: V9GoogleTokenSource,
    private val decisions: V9AdminDecisionSink,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val inFlight = AtomicBoolean(false)
    private val compactJwt = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")

    suspend fun decide(deviceId: String, action: String): V9AdminDecisionResult {
        V9AdminNonceWire.checkDecision(deviceId, action)
        check(inFlight.compareAndSet(false, true)) { "V9 admin request already in progress" }
        try {
            val challenge = V9AdminNonceWire.validate(sessions.begin(), now())
            // Never allow a caller to substitute a local nonce or a JWT-provided nonce.
            val idToken = google.requestIdToken(challenge.nonce)
            require(idToken.length in 50..8192 && compactJwt.matches(idToken)) {
                "V9 invalid Google ID token format"
            }
            // Credential Manager is interactive. Recheck after it returns.
            check(now() < challenge.expiresAt) { "V9 admin challenge expired" }
            val reply = decisions.submit(
                V9AdminDecisionRequest(challenge.sessionId, deviceId, action, idToken)
            )
            val requiredStatus = when (action) {
                "approve" -> "active"
                "reject" -> "rejected"
                else -> "revoked"
            }
            check(reply.deviceId == deviceId && reply.status == requiredStatus) {
                "V9 server decision response mismatch"
            }
            // This result is NOT an admin session grant; the server remains authoritative.
            return reply
        } finally {
            inFlight.set(false)
        }
    }
}
