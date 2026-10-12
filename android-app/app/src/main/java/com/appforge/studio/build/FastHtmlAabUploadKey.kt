package com.appforge.studio.build

import android.content.Context
import android.net.Uri
import com.android.apksig.ApkSigner
import com.appforge.studio.io.KeystoreVault
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate

/** AAB upload signing with an explicitly selected, previously imported PKCS12.
 * Never creates a replacement key. Imported-only enforces an independent backup source.
 * Play Console upload-certificate enrollment and release approval are outside this class.
 */
internal object FastHtmlAabUploadKey {
    data class Identity(
        val key: PrivateKey,
        val certificate: X509Certificate,
        val apkSigner: ApkSigner.SignerConfig
    )

    private const val MAX_KEYSTORE_BYTES = 10L * 1024L * 1024L

    fun requireSelection(context: Context, draft: ProjectDraft) {
        require(draft.signingMode == SigningMode.CUSTOM)
        require(draft.keyAlias.isNotBlank() && draft.storePassword.isNotBlank() &&
            draft.keyPassword.isNotBlank()) { "Yükleme anahtarı, alias ve parolaları gerekli." }
        val uri = Uri.parse(draft.keystoreUri ?: error("PKCS12 yükleme anahtarı seçilmedi."))
        require(uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            "Hızlı AAB için Kasaya içe aktarılmış PKCS12 seçilmelidir."
        }
        val keyFile = File(requireNotNull(uri.path)).canonicalFile
        val vault = File(context.filesDir, "keystore_vault").canonicalFile
        require(keyFile.parentFile == vault && keyFile.isFile &&
            keyFile.length() in 256..MAX_KEYSTORE_BYTES) {
            "Kasadaki PKCS12 kayıp veya boyut sınırına uymuyor."
        }
        val managed = KeystoreVault.load(context).singleOrNull {
            File(it.savedPath).canonicalFile == keyFile
        } ?: error("Seçilen PKCS12 kasaya kayıtlı değil.")
        require(!managed.generated) {
            "Hızlı AAB üretim anahtarı dışarıda yedeği bulunan, içe aktarılmış PKCS12 olmalı."
        }
        require(managed.originalFileName.lowercase().endsWith(".p12") ||
            managed.originalFileName.lowercase().endsWith(".pfx")) {
            "Hızlı AAB için PKCS12 (.p12/.pfx) gerekli."
        }
        val stored = managed.sha256
        val actual = MessageDigest.getInstance("SHA-256").digest(keyFile.readBytes())
            .joinToString(":") { "%02X".format(it.toInt() and 255) }
        require(stored.equals(actual, true)) { "PKCS12 SHA-256 kasadaki parmak iziyle eşleşmiyor." }
    }

    fun load(context: Context, draft: ProjectDraft): Identity {
        requireSelection(context, draft)
        val path = File(requireNotNull(Uri.parse(requireNotNull(draft.keystoreUri)).path))
        val storePwd = draft.storePassword.toCharArray()
        val keyPwd = draft.keyPassword.toCharArray()
        try {
            val store = KeyStore.getInstance("PKCS12")
            path.inputStream().use { store.load(it, storePwd) }
            require(store.isKeyEntry(draft.keyAlias)) { "PKCS12 alias geçersiz veya özel anahtar yok." }
            val key = store.getKey(draft.keyAlias, keyPwd) as? PrivateKey
                ?: error("PKCS12 özel anahtarı okunamadı.")
            require(key.algorithm.equals("RSA", true)) { "Hızlı AAB RSA yükleme anahtarı gerektirir." }
            val chain = store.getCertificateChain(draft.keyAlias)
                ?.map { it as? X509Certificate ?: error("X509 sertifika gerekli.") }
                ?.takeIf { it.isNotEmpty() } ?: error("PKCS12 sertifika zinciri eksik.")
            val certificate = chain.first()
            certificate.checkValidity()
            require(certificate.publicKey.algorithm.equals("RSA", true))
            requireMatchingKey(key, certificate.publicKey)
            return Identity(
                key, certificate,
                ApkSigner.SignerConfig.Builder(draft.keyAlias, key, chain).build()
            )
        } finally {
            storePwd.fill('\u0000')
            keyPwd.fill('\u0000')
        }
    }

    internal fun requireMatchingKey(key: PrivateKey, publicKey: PublicKey) {
        val challenge = "appforge-aab-signing-key-match-v1".toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(key)
            update(challenge)
        }.sign()
        val valid = Signature.getInstance("SHA256withRSA").apply {
            initVerify(publicKey)
            update(challenge)
        }.verify(signature)
        require(valid) { "PKCS12 anahtarı ve sertifikası eşleşmiyor." }
    }
}
