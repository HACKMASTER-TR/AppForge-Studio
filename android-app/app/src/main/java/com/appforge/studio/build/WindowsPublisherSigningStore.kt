package com.appforge.studio.build

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.appforge.studio.security.OwnerAccessPolicy
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object WindowsPublisherSigningStore {
    const val PROVIDER_ID =
        "local-pkcs12-v1"

    private const val KEY_ALIAS =
        "appforge_windows_publisher_signing_v1"

    private const val IV_BYTES =
        12

    private const val MAX_PKCS12_BYTES =
        10 * 1024 * 1024

    private fun root(
        context: Context
    ): File =
        File(
            context.noBackupFilesDir,
            "windows-publisher-signing-v1"
        )

    private fun pkcs12File(
        context: Context
    ): File =
        File(
            root(
                context
            ),
            "signer.pkcs12.enc"
        )

    private fun passwordFile(
        context: Context
    ): File =
        File(
            root(
                context
            ),
            "signer.pass.enc"
        )

    private fun metadataFile(
        context: Context
    ): File =
        File(
            root(
                context
            ),
            "metadata.txt"
        )

    private fun validatePkcs12(
        pkcs12Bytes: ByteArray,
        password: CharArray
    ) {
        val keyStore =
            KeyStore
                .getInstance(
                    "PKCS12"
                )

        ByteArrayInputStream(
            pkcs12Bytes
        ).use {
            input ->

            keyStore.load(
                input,
                password
            )
        }

        val aliases =
            keyStore.aliases()

        var signingAlias:
            String? =
            null

        while (
            aliases.hasMoreElements()
        ) {
            val alias =
                aliases.nextElement()

            if (
                keyStore.isKeyEntry(
                    alias
                )
            ) {
                signingAlias =
                    alias

                break
            }
        }

        val alias =
            signingAlias
                ?: error(
                    "PKCS#12 içinde private key bulunamadı."
                )

        val privateKey =
            keyStore.getKey(
                alias,
                password
            )

        check(
            privateKey is
                PrivateKey
        ) {
            "PKCS#12 signing private key doğrulanamadı."
        }

        val certificate =
            keyStore.getCertificate(
                alias
            ) as?
                X509Certificate
                ?: error(
                    "PKCS#12 içinde X.509 sertifikası bulunamadı."
                )

        certificate.checkValidity()

        val chain =
            keyStore.getCertificateChain(
                alias
            )

        check(
            !chain.isNullOrEmpty()
        ) {
            "PKCS#12 sertifika zinciri bulunamadı."
        }
    }

    fun isConfigured(
        context: Context
    ): Boolean =
        pkcs12File(
            context
        ).let {
            it.isFile &&
                it.length() > 32L
        } &&
            passwordFile(
                context
            ).let {
                it.isFile &&
                    it.length() > 32L
            }

    fun importPkcs12(
        context: Context,
        pkcs12Bytes: ByteArray,
        password: CharArray
    ) {
        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        require(
            pkcs12Bytes.size in
                256..MAX_PKCS12_BYTES
        ) {
            "PKCS#12 dosyası boş veya beklenmeyen boyutta."
        }

        require(
            password.size in
                1..4096
        ) {
            "PKCS#12 parolası boş veya geçersiz."
        }

        validatePkcs12(
            pkcs12Bytes =
                pkcs12Bytes,
            password =
                password
        )

        val destination =
            root(
                context
            ).apply {
                mkdirs()
            }

        check(
            destination.isDirectory
        ) {
            "Windows publisher signing private storage oluşturulamadı."
        }

        val passwordBytes =
            String(
                password
            ).toByteArray(
                Charsets.UTF_8
            )

        try {
            writeAtomic(
                pkcs12File(
                    context
                ),
                encrypt(
                    pkcs12Bytes
                )
            )

            writeAtomic(
                passwordFile(
                    context
                ),
                encrypt(
                    passwordBytes
                )
            )

            metadataFile(
                context
            ).writeText(
                "provider=$PROVIDER_ID\n" +
                    "sha256=${sha256(pkcs12Bytes)}\n",
                Charsets.UTF_8
            )
        } finally {
            passwordBytes.fill(
                0
            )
        }
    }

    fun materializeInto(
        context: Context,
        destination: File
    ) {
        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        check(
            isConfigured(
                context
            )
        ) {
            "Windows publisher signing provider yapılandırılmadı."
        }

        destination.mkdirs()

        check(
            destination.isDirectory
        ) {
            "Windows signing geçici alanı oluşturulamadı."
        }

        val pkcs12 =
            decrypt(
                pkcs12File(
                    context
                ).readBytes()
            )

        val password =
            decrypt(
                passwordFile(
                    context
                ).readBytes()
            )

        try {
            File(
                destination,
                "signer.pfx"
            ).writeBytes(
                pkcs12
            )

            File(
                destination,
                "pass.txt"
            ).writeBytes(
                password
            )
        } finally {
            pkcs12.fill(
                0
            )

            password.fill(
                0
            )
        }
    }

    fun clear(
        context: Context
    ) {
        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        root(
            context
        ).deleteRecursively()

        val keyStore =
            KeyStore
                .getInstance(
                    "AndroidKeyStore"
                )
                .apply {
                    load(
                        null
                    )
                }

        if (
            keyStore.containsAlias(
                KEY_ALIAS
            )
        ) {
            keyStore.deleteEntry(
                KEY_ALIAS
            )
        }
    }

    private fun encryptionKey(): SecretKey {
        val keyStore =
            KeyStore
                .getInstance(
                    "AndroidKeyStore"
                )
                .apply {
                    load(
                        null
                    )
                }

        val existing =
            keyStore.getKey(
                KEY_ALIAS,
                null
            ) as? SecretKey

        if (
            existing != null
        ) {
            return existing
        }

        val generator =
            KeyGenerator
                .getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )

        generator.init(
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT
                )
                .setKeySize(
                    256
                )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(
                    true
                )
                .build()
        )

        return generator
            .generateKey()
    }

    private fun encrypt(
        plain: ByteArray
    ): ByteArray {
        val cipher =
            Cipher
                .getInstance(
                    "AES/GCM/NoPadding"
                )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            encryptionKey()
        )

        return cipher.iv +
            cipher.doFinal(
                plain
            )
    }

    private fun decrypt(
        encrypted: ByteArray
    ): ByteArray {
        require(
            encrypted.size >
                IV_BYTES + 16
        ) {
            "Windows signing encrypted material bozuk."
        }

        val iv =
            encrypted.copyOfRange(
                0,
                IV_BYTES
            )

        val payload =
            encrypted.copyOfRange(
                IV_BYTES,
                encrypted.size
            )

        val cipher =
            Cipher
                .getInstance(
                    "AES/GCM/NoPadding"
                )

        cipher.init(
            Cipher.DECRYPT_MODE,
            encryptionKey(),
            GCMParameterSpec(
                128,
                iv
            )
        )

        return cipher.doFinal(
            payload
        )
    }

    private fun writeAtomic(
        target: File,
        bytes: ByteArray
    ) {
        target.parentFile
            ?.mkdirs()

        val temp =
            File(
                target.parentFile,
                target.name + ".tmp"
            )

        temp.writeBytes(
            bytes
        )

        if (
            target.exists() &&
            !target.delete()
        ) {
            temp.delete()

            error(
                "Windows signing private storage eski dosyası değiştirilemedi."
            )
        }

        if (
            !temp.renameTo(
                target
            )
        ) {
            temp.copyTo(
                target,
                overwrite = true
            )

            temp.delete()
        }
    }

    private fun sha256(
        bytes: ByteArray
    ): String =
        MessageDigest
            .getInstance(
                "SHA-256"
            )
            .digest(
                bytes
            )
            .joinToString(
                ""
            ) {
                value ->
                "%02x".format(
                    value
                )
            }
}
