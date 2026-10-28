package com.appforge.studio.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Independent V9 P-256 Android Keystore proof. It does not grant enrollment,
 * authorize releases, call a server, or reuse the existing Pro installation key.
 * Server-issued challenge fields must be validated by the private V9 service.
 * This class is deliberately NOT wired to production UI or APK release flows.
 */
internal object V9WitnessDeviceProof {
    private const val KEY_ALIAS = "appforge_v9_witness_p256_v1"

    data class SignedChallenge(
        val schema: Int,
        val operation: String,
        val deviceId: String,
        val challengeId: String,
        val nonce: String,
        val spkiB64Url: String,
        val signatureDerB64Url: String
    )

    private fun pair(): KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        val cert = store.getCertificate(KEY_ALIAS)
        if (cert != null) {
            val secret = store.getKey(KEY_ALIAS, null) as? PrivateKey
                ?: error("V9 Android Keystore private key unavailable")
            return KeyPair(cert.publicKey, secret)
        }
        // Never silently replace a damaged identity: re-enrollment is required.
        check(!store.containsAlias(KEY_ALIAS)) { "V9 witness key damaged" }
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKeyPair()
    }

    /** An immutable fingerprint of this keypair, not an Android-ID identifier. */
    fun deviceId(): String = V9DeviceProofWire.deviceId(pair().public.encoded)

    /** Server challenge only: no locally generated challenge is accepted. */
    fun signServerChallenge(operation: String, challengeId: String, nonce: String): SignedChallenge {
        val key = pair()
        val spki = key.public.encoded ?: error("V9 public key encoding unavailable")
        val id = V9DeviceProofWire.deviceId(spki)
        val signedBytes = V9DeviceProofWire.message(operation, id, challengeId, nonce)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(key.private)
        signer.update(signedBytes)
        val signatureDer = signer.sign()
        return SignedChallenge(
            schema = 1,
            operation = operation,
            deviceId = id,
            challengeId = challengeId,
            nonce = nonce,
            spkiB64Url = V9DeviceProofWire.base64Url(spki),
            signatureDerB64Url = V9DeviceProofWire.base64Url(signatureDer)
        )
    }
}
