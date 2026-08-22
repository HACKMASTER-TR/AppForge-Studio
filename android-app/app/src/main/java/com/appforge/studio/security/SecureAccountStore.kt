package com.appforge.studio.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.appforge.studio.net.Session
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureAccountStore {

    private const val PREFS_NAME =
        "appforge_secure_account_v1"

    private const val KEY_ALIAS =
        "appforge_secure_account_key_v1"

    private const val SESSION_DATA =
        "session_data"

    private const val SESSION_IV =
        "session_iv"

    private const val API_DATA =
        "build_api_data"

    private const val API_IV =
        "build_api_iv"

    private const val TRANSFORMATION =
        "AES/GCM/NoPadding"

    fun saveSession(
        context: Context,
        session: Session
    ) {
        val json =
            JSONObject().apply {
                put("token", session.token)
                put("userId", session.userId)
                put("email", session.email)
                put(
                    "displayName",
                    session.displayName
                )
                put(
                    "emailVerified",
                    session.emailVerified
                )
                put(
                    "twoFactorEnabled",
                    session.twoFactorEnabled
                )
            }

        writeEncrypted(
            context = context,
            dataKey = SESSION_DATA,
            ivKey = SESSION_IV,
            plaintext = json.toString()
        )
    }

    fun loadSession(
        context: Context
    ): Session? {
        val raw =
            readEncrypted(
                context = context,
                dataKey = SESSION_DATA,
                ivKey = SESSION_IV
            ) ?: return null

        return runCatching {
            val json =
                JSONObject(raw)

            Session(
                token =
                    json.getString("token"),

                userId =
                    json.getString("userId"),

                email =
                    json.getString("email"),

                displayName =
                    json.optString(
                        "displayName"
                    ),

                emailVerified =
                    json.optBoolean(
                        "emailVerified",
                        false
                    ),

                twoFactorEnabled =
                    json.optBoolean(
                        "twoFactorEnabled",
                        false
                    )
            )
        }.getOrElse {
            clearSession(context)
            null
        }
    }

    fun saveBuildApiKey(
        context: Context,
        apiKey: String
    ) {
        val clean =
            apiKey.trim()

        if (clean.isBlank()) {
            clearBuildApiKey(context)
            return
        }

        writeEncrypted(
            context = context,
            dataKey = API_DATA,
            ivKey = API_IV,
            plaintext = clean
        )
    }

    fun loadBuildApiKey(
        context: Context
    ): String? {
        return readEncrypted(
            context = context,
            dataKey = API_DATA,
            ivKey = API_IV
        )
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
    }

    fun clearSession(
        context: Context
    ) {
        prefs(context)
            .edit()
            .remove(SESSION_DATA)
            .remove(SESSION_IV)
            .apply()
    }

    fun clearBuildApiKey(
        context: Context
    ) {
        prefs(context)
            .edit()
            .remove(API_DATA)
            .remove(API_IV)
            .apply()
    }

    fun clearAll(
        context: Context
    ) {
        prefs(context)
            .edit()
            .clear()
            .apply()
    }

    private fun writeEncrypted(
        context: Context,
        dataKey: String,
        ivKey: String,
        plaintext: String
    ) {
        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey()
        )

        val encrypted =
            cipher.doFinal(
                plaintext.toByteArray(
                    Charsets.UTF_8
                )
            )

        val encodedData =
            Base64.encodeToString(
                encrypted,
                Base64.NO_WRAP
            )

        val encodedIv =
            Base64.encodeToString(
                cipher.iv,
                Base64.NO_WRAP
            )

        prefs(context)
            .edit()
            .putString(
                dataKey,
                encodedData
            )
            .putString(
                ivKey,
                encodedIv
            )
            .apply()
    }

    private fun readEncrypted(
        context: Context,
        dataKey: String,
        ivKey: String
    ): String? {
        val preferences =
            prefs(context)

        val encodedData =
            preferences.getString(
                dataKey,
                null
            ) ?: return null

        val encodedIv =
            preferences.getString(
                ivKey,
                null
            ) ?: return null

        return runCatching {
            val encrypted =
                Base64.decode(
                    encodedData,
                    Base64.NO_WRAP
                )

            val iv =
                Base64.decode(
                    encodedIv,
                    Base64.NO_WRAP
                )

            val cipher =
                Cipher.getInstance(
                    TRANSFORMATION
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    128,
                    iv
                )
            )

            String(
                cipher.doFinal(
                    encrypted
                ),
                Charsets.UTF_8
            )
        }.getOrElse {

            preferences
                .edit()
                .remove(dataKey)
                .remove(ivKey)
                .apply()

            null
        }
    }

    private fun prefs(
        context: Context
    ) =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private fun getOrCreateKey():
        SecretKey {

        val keyStore =
            KeyStore.getInstance(
                "AndroidKeyStore"
            ).apply {
                load(null)
            }

        val existing =
            keyStore.getKey(
                KEY_ALIAS,
                null
            ) as? SecretKey

        if (existing != null) {
            return existing
        }

        val generator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

        val spec =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setKeySize(256)
                .setRandomizedEncryptionRequired(
                    true
                )
                .build()

        generator.init(spec)

        return generator.generateKey()
    }
}
