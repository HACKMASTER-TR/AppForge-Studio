package com.appforge.studio.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Device-bound proof for admin-issued Pro codes.
 *
 * The private key remains in Android Keystore.
 * This class never grants Pro and never caches an entitlement.
 * Pro access requires a fresh HTTPS server verification.
 */
object ProInstallationProof {

    private const val KEY_ALIAS =
        "appforge_pro_installation_p256_v1"

    private val codeFormat =
        Regex("^AFPRO-[A-Za-z0-9_-]{43}$")

    private val nonceFormat =
        Regex("^[A-Za-z0-9_-]{22,86}$")

    private val uuidFormat =
        Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-" +
                "[1-8][0-9a-f]{3}-" +
                "[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        )

    data class RedeemProof(
        val code: String,
        val publicKey: String,
        val nonce: String,
        val signature: String
    )

    data class StatusProof(
        val installationId: String,
        val challengeId: String,
        val nonce: String,
        val signature: String
    )

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or
                Base64.NO_WRAP or
                Base64.NO_PADDING
        )

    private fun keyPair(): KeyPair {
        val store =
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }

        val certificate =
            store.getCertificate(KEY_ALIAS)

        if (certificate != null) {
            val privateKey =
                store.getKey(KEY_ALIAS, null) as? PrivateKey
                    ?: error(
                        "Pro kurulum özel anahtarı erişilemiyor."
                    )

            return KeyPair(
                certificate.publicKey,
                privateKey
            )
        }

        // Do not overwrite a partially available or damaged key.
        check(!store.containsAlias(KEY_ALIAS)) {
            "Pro kurulum anahtarı onarılamadı."
        }

        val generator =
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )

        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or
                    KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(
                    ECGenParameterSpec("secp256r1")
                )
                .setDigests(
                    KeyProperties.DIGEST_SHA256
                )
                .build()
        )

        return generator.generateKeyPair()
    }

    fun publicKeySpki(): String =
        base64Url(keyPair().public.encoded)

    private fun sign(
        message: String,
        privateKey: PrivateKey
    ): String {
        val signer =
            Signature.getInstance("SHA256withECDSA")

        signer.initSign(privateKey)

        signer.update(
            message.toByteArray(Charsets.UTF_8)
        )

        // Android returns DER ECDSA.
        // The Worker converts DER to WebCrypto P1363.
        return base64Url(signer.sign())
    }

    data class OwnershipProof(
        val installationId: String,
        val challengeId: String,
        val nonce: String,
        val publicKey: String,
        val signature: String
    )

    fun createOwnershipProof(
        operation: String,
        installationId: String,
        challengeId: String,
        nonce: String,
        code: String? = null
    ): OwnershipProof {
        require(operation == "recover" || operation == "reactivate")
        require(
            uuidFormat.matches(installationId) &&
                uuidFormat.matches(challengeId) &&
                nonceFormat.matches(nonce) &&
                (if (operation == "reactivate") {
                    code != null && codeFormat.matches(code)
                } else {
                    code == null
                })
        ) { "Geçersiz Pro sahiplik doğrulaması." }
        val pair = keyPair()
        val publicKey = base64Url(pair.public.encoded)
        val parts = mutableListOf(
            "appforge-pro-ownership-v1", operation,
            installationId, challengeId, nonce, publicKey
        )
        if (operation == "reactivate") parts.add(code!!)
        return OwnershipProof(
            installationId, challengeId, nonce, publicKey,
            sign(parts.joinToString("\n"), pair.private)
        )
    }

    fun createRedeemProof(code: String): RedeemProof {
        require(codeFormat.matches(code)) {
            "Geçersiz Pro aktivasyon kodu."
        }

        val pair = keyPair()
        val publicKey =
            base64Url(pair.public.encoded)

        val nonceBytes = ByteArray(32)
        SecureRandom().nextBytes(nonceBytes)

        val nonce = base64Url(nonceBytes)

        val message = listOf(
            "appforge-pro-redeem-v1",
            code,
            publicKey,
            nonce
        ).joinToString("\n")

        return RedeemProof(
            code = code,
            publicKey = publicKey,
            nonce = nonce,
            signature = sign(
                message,
                pair.private
            )
        )
    }

    fun createStatusProof(
        installationId: String,
        challengeId: String,
        nonce: String
    ): StatusProof {
        require(
            uuidFormat.matches(installationId) &&
                uuidFormat.matches(challengeId) &&
                nonceFormat.matches(nonce)
        ) {
            "Geçersiz Pro doğrulama isteği."
        }

        val message = listOf(
            "appforge-pro-status-v1",
            installationId,
            challengeId,
            nonce
        ).joinToString("\n")

        return StatusProof(
            installationId = installationId,
            challengeId = challengeId,
            nonce = nonce,
            signature = sign(
                message,
                keyPair().private
            )
        )
    }
}
