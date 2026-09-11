package com.appforge.studio.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.appforge.studio.net.Session
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ExternalServiceConnection(
    val provider: String,
    val accessToken: String,
    val refreshToken: String = "",
    val tokenType: String = "oauth",
    val accountLabel: String = "",
    val scopes: String = "",
    val expiresAt: Long = 0L,
    val connectedAt: Long =
        System.currentTimeMillis()
)

data class PendingExternalAuthorization(
    val provider: String,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val expiresAt: Long
)

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

    private const val TERMINAL_SESSIONS_DATA =
        "terminal_sessions_data"

    private const val TERMINAL_SESSIONS_IV =
        "terminal_sessions_iv"

    private const val ACCOUNT_SCOPE_PREFIX =
        "account_"

    private const val GUEST_ACCOUNT_SCOPE =
        "guest"

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
            ).also {
                migrateLegacyAccountCredentials(
                    context,
                    it
                )
            }
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

        val scope =
            accountScope(context)

        writeEncrypted(
            context = context,
            dataKey =
                scopedKey(
                    scope,
                    API_DATA
                ),
            ivKey =
                scopedKey(
                    scope,
                    API_IV
                ),
            plaintext = clean
        )
    }

    fun loadBuildApiKey(
        context: Context
    ): String? {
        val scope =
            accountScope(context)

        return readEncrypted(
            context = context,
            dataKey =
                scopedKey(
                    scope,
                    API_DATA
                ),
            ivKey =
                scopedKey(
                    scope,
                    API_IV
                )
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

    fun saveTerminalSessionState(
        context: Context,
        state: String
    ) {
        val clean =
            state.trim()

        if (clean.isBlank()) {
            clearTerminalSessionState(
                context
            )
            return
        }

        val scope =
            accountScope(
                context
            )

        writeEncrypted(
            context = context,
            dataKey =
                scopedKey(
                    scope,
                    TERMINAL_SESSIONS_DATA
                ),
            ivKey =
                scopedKey(
                    scope,
                    TERMINAL_SESSIONS_IV
                ),
            plaintext =
                clean
        )
    }

    fun loadTerminalSessionState(
        context: Context
    ): String? {
        val scope =
            accountScope(
                context
            )

        return readEncrypted(
            context = context,
            dataKey =
                scopedKey(
                    scope,
                    TERMINAL_SESSIONS_DATA
                ),
            ivKey =
                scopedKey(
                    scope,
                    TERMINAL_SESSIONS_IV
                )
        )
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
    }

    fun clearTerminalSessionState(
        context: Context
    ) {
        val scope =
            accountScope(
                context
            )

        prefs(context)
            .edit()
            .remove(
                scopedKey(
                    scope,
                    TERMINAL_SESSIONS_DATA
                )
            )
            .remove(
                scopedKey(
                    scope,
                    TERMINAL_SESSIONS_IV
                )
            )
            .apply()
    }

    fun clearBuildApiKey(
        context: Context
    ) {
        val scope =
            accountScope(context)

        prefs(context)
            .edit()
            .remove(
                scopedKey(
                    scope,
                    API_DATA
                )
            )
            .remove(
                scopedKey(
                    scope,
                    API_IV
                )
            )
            .apply()
    }

    fun saveExternalConnection(
        context: Context,
        connection: ExternalServiceConnection
    ) {
        val provider =
            normalizeProvider(
                connection.provider
            )

        val accessToken =
            validateToken(
                connection.accessToken,
                required = true
            )

        val refreshToken =
            validateToken(
                connection.refreshToken,
                required = false
            )

        val json =
            JSONObject().apply {
                put(
                    "provider",
                    provider
                )
                put(
                    "accessToken",
                    accessToken
                )
                put(
                    "refreshToken",
                    refreshToken
                )
                put(
                    "tokenType",
                    connection.tokenType
                )
                put(
                    "accountLabel",
                    connection.accountLabel
                )
                put(
                    "scopes",
                    connection.scopes
                )
                put(
                    "expiresAt",
                    connection.expiresAt
                )
                put(
                    "connectedAt",
                    connection.connectedAt
                )
            }

        val scope =
            accountScope(context)

        writeEncrypted(
            context = context,
            dataKey =
                scopedKey(
                    scope,
                    externalDataKey(
                        provider
                    )
                ),
            ivKey =
                scopedKey(
                    scope,
                    externalIvKey(
                        provider
                    )
                ),
            plaintext =
                json.toString()
        )
    }

    fun loadExternalConnection(
        context: Context,
        provider: String
    ): ExternalServiceConnection? {
        val safeProvider =
            normalizeProvider(provider)

        val scope =
            accountScope(context)

        val raw =
            readEncrypted(
                context = context,
                dataKey =
                    scopedKey(
                        scope,
                        externalDataKey(
                            safeProvider
                        )
                    ),
                ivKey =
                    scopedKey(
                        scope,
                        externalIvKey(
                            safeProvider
                        )
                    )
            ) ?: return null

        return runCatching {
            val json =
                JSONObject(raw)

            require(
                json.getString("provider") ==
                    safeProvider
            ) {
                "Bağlantı sağlayıcısı eşleşmiyor."
            }

            ExternalServiceConnection(
                provider =
                    safeProvider,

                accessToken =
                    validateToken(
                        json.getString(
                            "accessToken"
                        ),
                        required = true
                    ),

                refreshToken =
                    validateToken(
                        json.optString(
                            "refreshToken"
                        ),
                        required = false
                    ),

                tokenType =
                    json.optString(
                        "tokenType",
                        "oauth"
                    ),

                accountLabel =
                    json.optString(
                        "accountLabel"
                    ),

                scopes =
                    json.optString(
                        "scopes"
                    ),

                expiresAt =
                    json.optLong(
                        "expiresAt",
                        0L
                    ),

                connectedAt =
                    json.optLong(
                        "connectedAt",
                        0L
                    )
            )
        }.getOrElse {
            clearExternalConnection(
                context,
                safeProvider
            )

            null
        }
    }

    fun clearExternalConnection(
        context: Context,
        provider: String
    ) {
        val safeProvider =
            normalizeProvider(provider)

        val scope =
            accountScope(context)

        prefs(context)
            .edit()
            .remove(
                scopedKey(
                    scope,
                    externalDataKey(
                        safeProvider
                    )
                )
            )
            .remove(
                scopedKey(
                    scope,
                    externalIvKey(
                        safeProvider
                    )
                )
            )
            .apply()
    }

    fun savePendingExternalAuthorization(
        context: Context,
        authorization: PendingExternalAuthorization
    ) {
        val provider =
            normalizeProvider(
                authorization.provider
            )

        require(
            provider == "railway"
        ) {
            "Desteklenmeyen OAuth dönüş sağlayıcısı."
        }

        validatePendingAuthorization(
            authorization
        )

        val json =
            JSONObject().apply {
                put(
                    "provider",
                    provider
                )
                put(
                    "state",
                    authorization.state
                )
                put(
                    "codeVerifier",
                    authorization.codeVerifier
                )
                put(
                    "redirectUri",
                    authorization.redirectUri
                )
                put(
                    "expiresAt",
                    authorization.expiresAt
                )
            }

        val scope =
            accountScope(context)

        writeEncrypted(
            context = context,
            dataKey =
                scopedKey(
                    scope,
                    pendingDataKey(
                        provider
                    )
                ),
            ivKey =
                scopedKey(
                    scope,
                    pendingIvKey(
                        provider
                    )
                ),
            plaintext =
                json.toString()
        )
    }

    fun loadPendingExternalAuthorization(
        context: Context,
        provider: String
    ): PendingExternalAuthorization? {
        val safeProvider =
            normalizeProvider(provider)

        val scope =
            accountScope(context)

        val raw =
            readEncrypted(
                context = context,
                dataKey =
                    scopedKey(
                        scope,
                        pendingDataKey(
                            safeProvider
                        )
                    ),
                ivKey =
                    scopedKey(
                        scope,
                        pendingIvKey(
                            safeProvider
                        )
                    )
            ) ?: return null

        return runCatching {
            val json =
                JSONObject(raw)

            require(
                json.getString(
                    "provider"
                ) ==
                    safeProvider
            ) {
                "OAuth sağlayıcısı eşleşmiyor."
            }

            PendingExternalAuthorization(
                provider =
                    safeProvider,

                state =
                    json.getString(
                        "state"
                    ),

                codeVerifier =
                    json.getString(
                        "codeVerifier"
                    ),

                redirectUri =
                    json.getString(
                        "redirectUri"
                    ),

                expiresAt =
                    json.getLong(
                        "expiresAt"
                    )
            ).also {
                validatePendingAuthorization(
                    it
                )
            }
        }.getOrElse {
            clearPendingExternalAuthorization(
                context,
                safeProvider
            )

            null
        }
    }

    fun clearPendingExternalAuthorization(
        context: Context,
        provider: String
    ) {
        val safeProvider =
            normalizeProvider(provider)

        val scope =
            accountScope(context)

        prefs(context)
            .edit()
            .remove(
                scopedKey(
                    scope,
                    pendingDataKey(
                        safeProvider
                    )
                )
            )
            .remove(
                scopedKey(
                    scope,
                    pendingIvKey(
                        safeProvider
                    )
                )
            )
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

    fun activeAccountScope(
        context: Context
    ): String =
        accountScope(
            context
        )

    private fun accountScope(
        context: Context
    ): String =
        accountScope(
            loadSession(
                context
            )
        )

    private fun accountScope(
        session: Session?
    ): String {
        val identity =
            session
                ?.userId
                ?.trim()
                .orEmpty()
                .ifBlank {
                    session
                        ?.email
                        ?.trim()
                        ?.lowercase()
                        .orEmpty()
                }

        if (identity.isBlank()) {
            return GUEST_ACCOUNT_SCOPE
        }

        return MessageDigest
            .getInstance(
                "SHA-256"
            )
            .digest(
                identity.toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString("") {
                byte ->
                "%02x".format(
                    byte.toInt() and
                        0xff
                )
            }
    }

    private fun scopedKey(
        scope: String,
        baseKey: String
    ): String =
        "$ACCOUNT_SCOPE_PREFIX${scope}_$baseKey"

    /*
     * v1 sürümünde build API, GitHub ve Railway bilgileri
     * global preference anahtarlarında tutuluyordu.
     *
     * Güncelleme sonrası mevcut oturum ilk kez okunurken
     * bu kayıtları o hesabın SHA-256 namespace'ine taşırız.
     * Böylece eski kullanıcının tokenı sonraki hesaba
     * yanlışlıkla migrate edilemez.
     */
    private fun migrateLegacyAccountCredentials(
        context: Context,
        session: Session
    ) {
        val scope =
            accountScope(
                session
            )

        if (
            scope ==
                GUEST_ACCOUNT_SCOPE
        ) {
            return
        }

        listOf(
            API_DATA to
                API_IV,

            externalDataKey(
                "github"
            ) to
                externalIvKey(
                    "github"
                ),

            externalDataKey(
                "railway"
            ) to
                externalIvKey(
                    "railway"
                ),

            pendingDataKey(
                "railway"
            ) to
                pendingIvKey(
                    "railway"
                )
        ).forEach {
            pair ->

            migrateLegacyEncryptedPair(
                context =
                    context,
                scope =
                    scope,
                legacyDataKey =
                    pair.first,
                legacyIvKey =
                    pair.second
            )
        }
    }

    private fun migrateLegacyEncryptedPair(
        context: Context,
        scope: String,
        legacyDataKey: String,
        legacyIvKey: String
    ) {
        val scopedDataKey =
            scopedKey(
                scope,
                legacyDataKey
            )

        val scopedIvKey =
            scopedKey(
                scope,
                legacyIvKey
            )

        /*
         * Hesap kasasında zaten geçerli bir değer varsa
         * eski global kopya kullanılmaz.
         */
        val scoped =
            readEncrypted(
                context =
                    context,
                dataKey =
                    scopedDataKey,
                ivKey =
                    scopedIvKey
            )

        if (scoped != null) {
            prefs(context)
                .edit()
                .remove(
                    legacyDataKey
                )
                .remove(
                    legacyIvKey
                )
                .apply()

            return
        }

        val legacy =
            readEncrypted(
                context =
                    context,
                dataKey =
                    legacyDataKey,
                ivKey =
                    legacyIvKey
            ) ?: return

        writeEncrypted(
            context =
                context,
            dataKey =
                scopedDataKey,
            ivKey =
                scopedIvKey,
            plaintext =
                legacy
        )

        prefs(context)
            .edit()
            .remove(
                legacyDataKey
            )
            .remove(
                legacyIvKey
            )
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

    private fun normalizeProvider(
        provider: String
    ): String {
        val normalized =
            provider
                .trim()
                .lowercase()

        require(
            normalized in
                setOf(
                    "github",
                    "railway"
                )
        ) {
            "Desteklenmeyen bağlantı sağlayıcısı."
        }

        return normalized
    }

    private fun externalDataKey(
        provider: String
    ) =
        "external_${provider}_data"

    private fun externalIvKey(
        provider: String
    ) =
        "external_${provider}_iv"

    private fun pendingDataKey(
        provider: String
    ) =
        "pending_${provider}_data"

    private fun pendingIvKey(
        provider: String
    ) =
        "pending_${provider}_iv"

    private fun validatePendingAuthorization(
        authorization: PendingExternalAuthorization
    ) {
        require(
            authorization.provider == "railway" &&
                authorization.redirectUri ==
                "appforge-studio://auth/railway" &&
                authorization.state.length in 32..256 &&
                authorization.codeVerifier.length in 43..128 &&
                authorization.state.all {
                    it.isLetterOrDigit() ||
                        it == '-' ||
                        it == '_'
                } &&
                authorization.codeVerifier.all {
                    it.isLetterOrDigit() ||
                        it == '-' ||
                        it == '_' ||
                        it == '.' ||
                        it == '~'
                } &&
                authorization.expiresAt >=
                System.currentTimeMillis() &&
                authorization.expiresAt <=
                System.currentTimeMillis() +
                    MAX_PENDING_AUTH_LIFETIME_MS
        ) {
            "Geçersiz OAuth güvenlik durumu."
        }
    }

    private fun validateToken(
        value: String,
        required: Boolean
    ): String {
        val clean = value.trim()

        require(!required || clean.isNotBlank()) {
            "Bağlantı anahtarı boş olamaz."
        }

        require(
            clean.length <= MAX_EXTERNAL_TOKEN_LENGTH &&
                clean.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "Bağlantı anahtarı geçersiz."
        }

        return clean
    }

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

    private const val MAX_EXTERNAL_TOKEN_LENGTH =
        32 * 1_024

    private const val MAX_PENDING_AUTH_LIFETIME_MS =
        15 * 60 * 1_000L
}
