package com.appforge.studio.security

import java.security.MessageDigest
import java.util.Base64

/** Pure wire contract shared by the V9 Android Keystore client and verifier tests.
 * No network calls, no secrets and no enrollment authority. */
internal object V9DeviceProofWire {
    private const val DOMAIN = "APPFORGE-V9-DEVICE-PROOF\u0000"
    private val HEX32 = Regex("^[0-9a-f]{32}$")
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val OPERATIONS = setOf("receipt", "prepare", "finalize", "abort")

    fun deviceId(spki: ByteArray): String {
        require(spki.isNotEmpty()) { "V9 public key missing" }
        return MessageDigest.getInstance("SHA-256")
            .digest(spki).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun message(operation: String, deviceId: String, challengeId: String, nonce: String): ByteArray {
        require(operation in OPERATIONS) { "V9 unsupported operation" }
        require(HEX64.matches(deviceId)) { "V9 invalid device fingerprint" }
        require(HEX32.matches(challengeId) && HEX32.matches(nonce)) {
            "V9 invalid server challenge"
        }
        return (DOMAIN + listOf(operation, deviceId, challengeId, nonce).joinToString("\n") + "\n")
            .toByteArray(Charsets.UTF_8)
    }

    fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
