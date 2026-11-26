package com.appforge.studio.security

import java.security.MessageDigest

/** Stage 5E.4: signed-request bytes for a future trusted Android Keystore signer.
 * Does NOT implement authentication, store keys or confer administrator access.
 * Server must independently verify the device SPKI pin, signature and anti-replay.
 */
internal object V9AdminProofWire {
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val HEX32 = Regex("^[0-9a-f]{32}$")
    private val PATH = setOf(
        "/v9/private/admin/session/begin",
        "/v9/private/admin/device/decide"
    )
    fun canonical(method: String, origin: String, path: String, body: ByteArray, proofId: String, issuedAtMs: Long): ByteArray {
        require(method == "POST" && path in PATH &&
            Regex("^https://[a-z0-9.-]+$").matches(origin)) { "V9 forbidden request target" }
        require(body.size <= 12_000) { "V9 oversized admin body" }
        require(HEX32.matches(proofId) && issuedAtMs > 0) { "V9 invalid proof metadata" }
        val digest = MessageDigest.getInstance("SHA-256").digest(body)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return ("APPFORGE-V9-STAGE5E4-HTTP\u0000" + method + "\n" + origin + "\n" + path + "\n" +
            digest + "\n" + proofId + "\n" + issuedAtMs + "\n").toByteArray(Charsets.UTF_8)
    }
    data class SignedProof(
        val principalId: String,
        val proofId: String,
        val issuedAtMs: Long,
        val signatureDerB64Url: String
    ) {
        fun validate(nowMs: Long): SignedProof {
            require(HEX64.matches(principalId) && HEX32.matches(proofId)) { "V9 invalid proof IDs" }
            require(issuedAtMs > 0 && kotlin.math.abs(nowMs - issuedAtMs) <= 30_000) {
                "V9 expired signed request proof"
            }
            require(Regex("^[A-Za-z0-9_-]{64,200}$").matches(signatureDerB64Url)) {
                "V9 invalid signature format"
            }
            return this
        }
    }
}

/** Provider must sign V9AdminProofWire.canonical with a device-bound nonexportable P-256 key.
 * Must issue a cryptographically random unique proof ID for EVERY HTTP request.
 * This interface is intentionally not implemented or wired into a deployed app.
 */
internal fun interface V9AdminRequestProofSigner {
    fun sign(method: String, origin: String, path: String, body: ByteArray): V9AdminProofWire.SignedProof
}
