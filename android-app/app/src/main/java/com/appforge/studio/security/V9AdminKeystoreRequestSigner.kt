package com.appforge.studio.security

import java.security.KeyStore

/** Android-only, NOT CONNECTED to navigation or the production Google admin login.
 * Requires an ALREADY-PROVISIONED AndroidKeyStore EC key alias. No auto-key creation,
 * self-enrollment, key deletion, device approval or token caching is permitted here.
 */
internal class V9AdminKeystoreRequestSigner(
    alias: String,
    now: () -> Long = System::currentTimeMillis
) : V9AdminRequestProofSigner {
    private val delegate: V9AdminP256SigningCore

    init {
        require(Regex("^appforge-v9-admin-proof-[a-z0-9_-]{8,48}$").matches(alias)) {
            "V9 unapproved Keystore alias format"
        }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: error("V9 admin P-256 key not provisioned")
        // AndroidKeyStore normally supplies non-exportable private keys. Refuse exportable keys.
        check(entry.privateKey.encoded == null) { "V9 exportable private key denied" }
        delegate = V9AdminP256SigningCore(entry.privateKey, entry.certificate.publicKey.encoded, now)
    }

    override fun sign(method: String, origin: String, path: String, body: ByteArray): V9AdminProofWire.SignedProof =
        delegate.sign(method, origin, path, body)
}
