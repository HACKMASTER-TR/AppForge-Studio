package com.appforge.studio.security

/** V9's server-issued nonce is distinct from the existing GoogleAdminIdentityClient nonce.
 * No network I/O, UI integration, token caching or production APK activation here. */
internal object V9AdminNonceWire {
    private val SESSION = Regex("^[0-9a-f]{32}$")
    private val NONCE = Regex("^[A-Za-z0-9_-]{22,128}$")
    private val DEVICE = Regex("^[0-9a-f]{64}$")
    private val ACTIONS = setOf("approve", "reject", "revoke")

    data class ServerChallenge(val sessionId: String, val nonce: String, val expiresAt: Long)
    fun validate(challenge: ServerChallenge, now: Long): ServerChallenge {
        require(SESSION.matches(challenge.sessionId)) { "V9 invalid admin session" }
        require(NONCE.matches(challenge.nonce)) { "V9 invalid server nonce" }
        require(challenge.expiresAt > now && challenge.expiresAt - now <= 180_000L) {
            "V9 admin challenge expired or too long"
        }
        return challenge
    }
    fun checkDecision(deviceId: String, action: String) {
        require(DEVICE.matches(deviceId) && action in ACTIONS) { "V9 invalid decision" }
    }
}
