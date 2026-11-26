package com.appforge.studio.security

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.widget.TextView
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Separate sandbox application UID, NO NETWORK. Never provisions an AppForge admin key.
 * Uses the same pinned Stage 5E.4/5E.5 Kotlin signer source in a diagnostic-only process.
 * Observations here DO NOT prove production app UID, attestation or enrollment.
 */
class V9S5E6KeystoreProbeActivity : Activity() {
    private val alias = "appforge-v9-admin-proof-s5e6probe2026"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = TextView(this).apply {
            text = "S5E6_PHYSICAL_KEYSTORE=RUNNING\nNo network access; disposable test key only."
            textSize = 15f
            setPadding(24, 36, 24, 24)
        }
        setContentView(report)
        Thread {
            val result = runCatching { runProbe() }.getOrElse { err ->
                "S5E6_PHYSICAL_KEYSTORE=FAIL\n" + err.javaClass.simpleName + ": " +
                    (err.message ?: "unknown diagnostic failure").take(200)
            }
            runOnUiThread { report.text = result }
        }.start()
    }

    private fun runProbe(): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // Alias belongs ONLY to this isolated application's UID. No admin enrollment.
        if (store.containsAlias(alias)) store.deleteEntry(alias)
        try {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false) // diagnostic ONLY; NOT a production policy
                    .build()
            )
            generator.generateKeyPair()
            val entry = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                ?: error("AndroidKeyStore key not found")
            check(entry.privateKey.encoded == null) { "Private key export unexpectedly available" }

            val info = KeyFactory.getInstance("EC", "AndroidKeyStore")
                .getKeySpec(entry.privateKey, KeyInfo::class.java) as KeyInfo
            check(info.keySize == 256 && info.origin == KeyProperties.ORIGIN_GENERATED) {
                "Not a freshly generated P-256 AndroidKeyStore key"
            }
            val publicSpki = entry.certificate.publicKey.encoded
            val spkiDigest = MessageDigest.getInstance("SHA-256").digest(publicSpki)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val signer = V9AdminKeystoreRequestSigner(alias)
            val path = "/v9/private/admin/session/begin"
            val origin = "https://keystore-probe.invalid"
            val original = "{\"probe\":1}".toByteArray(Charsets.UTF_8)
            val sigA = signer.sign("POST", origin, path, original)
            val sigB = signer.sign("POST", origin, path, original)
            check(sigA.principalId == spkiDigest) { "Wrong public-key pin" }
            check(sigA.proofId != sigB.proofId) { "Per-request proof ID reused" }

            fun verify(payload: ByteArray, proofId: String, b64: String): Boolean {
                val canonical = V9AdminProofWire.canonical("POST", origin, path, payload, proofId, sigA.issuedAtMs)
                return Signature.getInstance("SHA256withECDSA").run {
                    initVerify(entry.certificate.publicKey)
                    update(canonical)
                    verify(Base64.getUrlDecoder().decode(b64))
                }
            }
            check(verify(original, sigA.proofId, sigA.signatureDerB64Url)) { "Actual AndroidKeyStore signature failed" }
            check(!verify("{\"probe\":2}".toByteArray(Charsets.UTF_8), sigA.proofId, sigA.signatureDerB64Url)) {
                "Tampered body accepted"
            }
            check(!verify(original, sigB.proofId, sigA.signatureDerB64Url)) { "Changed proof ID accepted" }
            val hardware = if (Build.VERSION.SDK_INT >= 31) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                    else -> "SOFTWARE_OR_UNKNOWN"
                }
            } else if (info.isInsideSecureHardware) "HARDWARE_REPORTED" else "SOFTWARE_OR_UNKNOWN"
            return "S5E6_PHYSICAL_KEYSTORE=PASS\n" +
                "S5E6_P256=PASS;NON_EXPORTABLE=PASS;SIGNATURE=PASS\n" +
                "S5E6_BODY_TAMPER=DENIED;PROOF_ID_TAMPER=DENIED\n" +
                "S5E6_PROOF_ID_UNIQUE=PASS;SPKI_PIN=PASS\n" +
                "S5E6_SECURITY_LEVEL=$hardware\n" +
                "S5E6_ADMIN_ENROLLMENT=NO;APPFORGE_APP_UID_TESTED=NO\n" +
                "S5E6_KEY_ALIAS_CLEANUP=PASS"
        } finally {
            // No permanent test key or enrollment; cleanup even if an assertion fails.
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }
}
