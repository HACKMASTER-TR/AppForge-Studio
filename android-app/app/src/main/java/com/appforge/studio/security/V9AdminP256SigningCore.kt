package com.appforge.studio.security

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Pure JVM P-256 signing core. No enrollment or local owner permission is granted.
 * The production adapter MUST use an AndroidKeyStore non-exportable key.
 */
internal class V9AdminP256SigningCore(
    private val privateKey: PrivateKey,
    spki: ByteArray,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom()
) : V9AdminRequestProofSigner {
    private val publicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(spki.copyOf())) as ECPublicKey
    private val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(spki).joinToString("") { "%02x".format(it.toInt() and 255) }

    init {
        require(privateKey.algorithm == "EC" && spki.size in 75..120) { "V9 invalid key" }
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        val expected = params.getParameterSpec(ECParameterSpec::class.java)
        val actual = publicKey.params
        require(actual.curve == expected.curve && actual.generator == expected.generator &&
            actual.order == expected.order && actual.cofactor == expected.cofactor) {
            "V9 only P-256 is supported"
        }
    }

    override fun sign(method: String, origin: String, path: String, body: ByteArray): V9AdminProofWire.SignedProof {
        val proof = ByteArray(16).also(random::nextBytes).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
        val issuedAt = now()
        val canonical = V9AdminProofWire.canonical(method, origin, path, body, proof, issuedAt)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(canonical)
        val der = signature.sign()
        return V9AdminProofWire.SignedProof(
            principalId = fingerprint,
            proofId = proof,
            issuedAtMs = issuedAt,
            signatureDerB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(der)
        ).validate(issuedAt)
    }
}
