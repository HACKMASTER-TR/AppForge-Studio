package com.appforge.studio.terminal

import android.util.Base64
import com.appforge.studio.security.ExternalServiceConnection
import com.appforge.studio.security.PendingExternalAuthorization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection

internal enum class ExternalProvider(
    val key: String,
    val title: String,
    val tokenEndpoint: String,
    val scope: String
) {
    GITHUB(
        key = "github",
        title = "GitHub",
        tokenEndpoint =
            "https://github.com/login/oauth/access_token",
        scope =
            "repo read:user user:email"
    ),
    RAILWAY(
        key = "railway",
        title = "Railway",
        tokenEndpoint =
            "https://backboard.railway.com/oauth/token",
        scope =
            "openid profile email offline_access workspace:viewer project:member"
    )
}

internal data class DeviceAuthorization(
    val provider: ExternalProvider,
    val clientId: String,
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresAt: Long,
    val intervalSeconds: Long
)

internal data class ExternalIdentity(
    val label: String,
    val detail: String = ""
)

internal data class OAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val scopes: String,
    val expiresAt: Long
)

internal data class BrowserAuthorization(
    val authorizationUri: String,
    val pending: PendingExternalAuthorization
)

internal sealed interface DevicePollResult {
    data class Pending(
        val intervalSeconds: Long,
        val message: String
    ) : DevicePollResult

    data class Authorized(
        val token: OAuthToken
    ) : DevicePollResult

    data class Failed(
        val message: String
    ) : DevicePollResult
}

internal object ExternalConnectionsClient {
    suspend fun startDeviceAuthorization(
        provider: ExternalProvider,
        clientId: String
    ): DeviceAuthorization =
        withContext(Dispatchers.IO) {
            require(provider == ExternalProvider.GITHUB) {
                "Cihaz kodu akışı yalnızca GitHub için desteklenir."
            }
            require(clientId.isNotBlank()) {
                "${provider.title} OAuth istemci kimliği yapılandırılmamış."
            }

            val response =
                postForm(
                    GITHUB_DEVICE_ENDPOINT,
                    mapOf(
                        "client_id" to clientId.trim(),
                        "scope" to provider.scope
                    )
                )

            ensureSuccess(response)

            val json = JSONObject(response.body)
            val expiresIn =
                json.optLong("expires_in", 900L)
                    .coerceIn(60L, 3_600L)

            val verificationUri =
                json.optString("verification_uri")
                    .ifBlank {
                        json.optString(
                            "verification_url"
                        )
                    }

            require(verificationUri.isNotBlank()) {
                "Yetkilendirme adresi alınamadı."
            }

            val verificationUriComplete =
                json.optString(
                    "verification_uri_complete"
                ).ifBlank {
                    verificationUri
                }

            validateVerificationUri(
                provider,
                verificationUri
            )
            validateVerificationUri(
                provider,
                verificationUriComplete
            )

            DeviceAuthorization(
                provider = provider,
                clientId = clientId.trim(),
                deviceCode =
                    json.getString("device_code"),
                userCode =
                    json.getString("user_code"),
                verificationUri = verificationUri,
                verificationUriComplete =
                    verificationUriComplete,
                expiresAt =
                    System.currentTimeMillis() +
                        expiresIn * 1_000L,
                intervalSeconds =
                    json.optLong("interval", 5L)
                        .coerceIn(5L, 30L)
            )
        }

    fun startRailwayAuthorization(
        clientId: String
    ): BrowserAuthorization {
        val cleanClientId = validateClientId(clientId)
        val verifier = randomBase64Url(32)
        val state = randomBase64Url(32)
        val challenge =
            base64Url(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                        verifier.toByteArray(
                            Charsets.US_ASCII
                        )
                    )
            )

        val pending =
            PendingExternalAuthorization(
                provider =
                    ExternalProvider.RAILWAY.key,
                state = state,
                codeVerifier = verifier,
                redirectUri =
                    RAILWAY_REDIRECT_URI,
                expiresAt =
                    System.currentTimeMillis() +
                        RAILWAY_AUTH_LIFETIME_MS
            )

        val authorizationUri =
            buildString {
                append(RAILWAY_AUTH_ENDPOINT)
                append("?response_type=code")
                append("&client_id=")
                append(encode(cleanClientId))
                append("&redirect_uri=")
                append(encode(RAILWAY_REDIRECT_URI))
                append("&scope=")
                append(
                    encode(
                        ExternalProvider.RAILWAY.scope
                    )
                )
                append("&state=")
                append(encode(state))
                append("&code_challenge=")
                append(encode(challenge))
                append("&code_challenge_method=S256")
                append("&prompt=consent")
            }

        validateRailwayAuthorizationUri(
            authorizationUri
        )

        return BrowserAuthorization(
            authorizationUri = authorizationUri,
            pending = pending
        )
    }

    suspend fun exchangeRailwayCallback(
        callbackUri: String,
        pending: PendingExternalAuthorization,
        clientId: String
    ): OAuthToken =
        withContext(Dispatchers.IO) {
            require(
                pending.provider ==
                    ExternalProvider.RAILWAY.key &&
                    pending.redirectUri ==
                    RAILWAY_REDIRECT_URI &&
                    pending.expiresAt >=
                    System.currentTimeMillis()
            ) {
                "Railway yetkilendirme isteğinin süresi dolmuş."
            }

            val callback = URI(callbackUri)
            require(
                callback.scheme.equals(
                    "appforge-studio",
                    ignoreCase = true
                ) &&
                    callback.host.equals(
                        "auth",
                        ignoreCase = true
                    ) &&
                    callback.path == "/railway" &&
                    callback.port == -1 &&
                    callback.fragment == null &&
                    callback.userInfo == null
            ) {
                "Geçersiz Railway dönüş adresi."
            }

            val parameters =
                parseQuery(callback.rawQuery.orEmpty())
            val returnedState =
                parameters["state"].orEmpty()

            require(
                returnedState.isNotBlank() &&
                    MessageDigest.isEqual(
                        pending.state.toByteArray(
                            Charsets.UTF_8
                        ),
                        returnedState.toByteArray(
                            Charsets.UTF_8
                        )
                    )
            ) {
                "Railway güvenlik doğrulaması başarısız oldu."
            }

            parameters["error"]
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    error(
                        parameters["error_description"]
                            ?.takeIf {
                                detail ->
                                detail.isNotBlank()
                            }
                            ?.take(MAX_ERROR_DETAIL_LENGTH)
                            ?: if (it == "access_denied") {
                                "Railway yetkilendirmesi reddedildi."
                            } else {
                                "Railway yetkilendirmesi tamamlanamadı: $it"
                            }
                    )
                }

            val code =
                parameters["code"]
                    ?.trim()
                    .orEmpty()

            require(
                code.isNotBlank() &&
                    code.length <= MAX_AUTH_CODE_LENGTH &&
                    code.none {
                        it == '\n' ||
                            it == '\r' ||
                            it == '\u0000'
                    }
            ) {
                "Railway yetkilendirme kodu alınamadı."
            }

            val response =
                postForm(
                    ExternalProvider.RAILWAY.tokenEndpoint,
                    mapOf(
                        "client_id" to
                            validateClientId(clientId),
                        "grant_type" to
                            "authorization_code",
                        "code" to code,
                        "redirect_uri" to
                            RAILWAY_REDIRECT_URI,
                        "code_verifier" to
                            pending.codeVerifier
                    )
                )

            ensureSuccess(response)
            oauthToken(
                JSONObject(response.body),
                ExternalProvider.RAILWAY.scope
            )
        }

    suspend fun pollDeviceAuthorization(
        authorization: DeviceAuthorization,
        currentIntervalSeconds: Long
    ): DevicePollResult =
        withContext(Dispatchers.IO) {
            require(
                authorization.provider ==
                    ExternalProvider.GITHUB
            ) {
                "Cihaz kodu yalnızca GitHub için kullanılabilir."
            }

            if (
                System.currentTimeMillis() >=
                authorization.expiresAt
            ) {
                return@withContext DevicePollResult.Failed(
                    "Yetkilendirme kodunun süresi doldu."
                )
            }

            val response =
                postForm(
                    authorization.provider.tokenEndpoint,
                    mapOf(
                        "client_id" to
                            authorization.clientId,
                        "device_code" to
                            authorization.deviceCode,
                        "grant_type" to
                            "urn:ietf:params:oauth:grant-type:device_code"
                    )
                )

            val json = parseJson(response.body)
            val error =
                json.optString("error")

            if (
                response.code == 429 ||
                response.code in 500..599
            ) {
                return@withContext DevicePollResult.Pending(
                    (currentIntervalSeconds + 2L)
                        .coerceAtMost(30L),
                    "Sağlayıcı geçici olarak yanıt vermiyor; yeniden denenecek…"
                )
            }

            if (response.code in 200..299) {
                val accessToken =
                    json.optString("access_token")

                if (accessToken.isNotBlank()) {
                    return@withContext DevicePollResult.Authorized(
                        oauthToken(
                            json,
                            authorization.provider.scope
                        )
                    )
                }
            }

            when (error) {
                "authorization_pending" ->
                    DevicePollResult.Pending(
                        currentIntervalSeconds,
                        "Tarayıcı onayı bekleniyor…"
                    )

                "slow_down" ->
                    DevicePollResult.Pending(
                        (currentIntervalSeconds + 5L)
                            .coerceAtMost(60L),
                        "Sağlayıcı bekleme süresini artırdı…"
                    )

                "access_denied" ->
                    DevicePollResult.Failed(
                        "Yetkilendirme reddedildi."
                    )

                "expired_token" ->
                    DevicePollResult.Failed(
                        "Yetkilendirme kodunun süresi doldu."
                    )

                else ->
                    DevicePollResult.Failed(
                        oauthErrorMessage(
                            response,
                            json,
                            "Yetkilendirme tamamlanamadı."
                        )
                    )
            }
        }

    suspend fun validateIdentity(
        provider: ExternalProvider,
        accessToken: String
    ): ExternalIdentity =
        withContext(Dispatchers.IO) {
            val cleanToken =
                accessToken.trim()

            require(
                cleanToken.isNotBlank() &&
                    cleanToken.length <=
                        MAX_TOKEN_LENGTH &&
                    cleanToken.none {
                        it == '\n' ||
                            it == '\r' ||
                            it == '\u0000'
                    }
            ) {
                "Token boş olamaz."
            }

            when (provider) {
                ExternalProvider.GITHUB ->
                    validateGithub(cleanToken)

                ExternalProvider.RAILWAY ->
                    validateRailway(cleanToken)
            }
        }

    suspend fun refreshRailway(
        connection: ExternalServiceConnection,
        clientId: String
    ): ExternalServiceConnection =
        withContext(Dispatchers.IO) {
            require(
                connection.provider == "railway" &&
                    connection.refreshToken.isNotBlank() &&
                    clientId.isNotBlank()
            ) {
                "Railway oturumu yenilenemiyor."
            }

            val response =
                postForm(
                    ExternalProvider.RAILWAY.tokenEndpoint,
                    mapOf(
                        "client_id" to
                            validateClientId(clientId),
                        "grant_type" to "refresh_token",
                        "refresh_token" to
                            connection.refreshToken
                    )
                )

            ensureSuccess(response)
            val json = JSONObject(response.body)
            val token =
                oauthToken(
                    json,
                    connection.scopes
                )

            connection.copy(
                accessToken = token.accessToken,
                refreshToken =
                    token.refreshToken
                        .ifBlank {
                            connection.refreshToken
                        },
                scopes =
                    token.scopes
                        .ifBlank {
                            connection.scopes
                        },
                expiresAt =
                    if (token.expiresAt > 0L) {
                        token.expiresAt
                    } else {
                        connection.expiresAt
                    }
            )
        }

    private fun validateGithub(
        accessToken: String
    ): ExternalIdentity {
        val response =
            get(
                "https://api.github.com/user",
                accessToken
            )

        ensureSuccess(response)
        val json = JSONObject(response.body)
        val login = json.getString("login")

        return ExternalIdentity(
            label = login,
            detail =
                json.optString("name")
                    .takeIf {
                        it.isNotBlank() && it != login
                    }
                    .orEmpty()
        )
    }

    private fun validateRailway(
        accessToken: String
    ): ExternalIdentity {
        val oauthResponse =
            get(
                "https://backboard.railway.com/oauth/me",
                accessToken
            )

        if (oauthResponse.code in 200..299) {
            return railwayIdentity(
                JSONObject(oauthResponse.body)
            )
        }

        val graphResponse =
            request(
                method = "POST",
                url =
                    "https://backboard.railway.com/graphql/v2",
                accessToken = accessToken,
                contentType = "application/json",
                body =
                    JSONObject()
                        .put(
                            "query",
                            "query AppForgeIdentity { me { id name email } }"
                        )
                        .toString()
            )

        ensureSuccess(graphResponse)
        val root = JSONObject(graphResponse.body)
        val errors = root.optJSONArray("errors")

        require(errors == null || errors.length() == 0) {
            errors
                ?.optJSONObject(0)
                ?.optString("message")
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Railway tokenı doğrulanamadı."
        }

        return railwayIdentity(
            root.getJSONObject("data")
                .getJSONObject("me")
        )
    }

    private fun railwayIdentity(
        json: JSONObject
    ): ExternalIdentity {
        val email =
            json.optString("email")
        val name =
            json.optString("name")
                .ifBlank {
                    json.optString("displayName")
                }
        val id =
            json.optString("sub")
                .ifBlank {
                    json.optString("id")
                }

        return ExternalIdentity(
            label =
                name.ifBlank {
                    email.ifBlank {
                        id.ifBlank {
                            "Railway hesabı"
                        }
                    }
                },
            detail =
                email.takeIf {
                    it.isNotBlank() && it != name
                }.orEmpty()
        )
    }

    private fun postForm(
        url: String,
        values: Map<String, String>
    ): HttpResponse =
        request(
            method = "POST",
            url = url,
            contentType =
                "application/x-www-form-urlencoded",
            body =
                values.entries.joinToString("&") {
                    "${encode(it.key)}=${encode(it.value)}"
                }
        )

    private fun validateVerificationUri(
        provider: ExternalProvider,
        value: String
    ) {
        val uri = URI(value)
        val host =
            uri.host
                ?.lowercase()
                .orEmpty()

        val trustedHost =
            when (provider) {
                ExternalProvider.GITHUB ->
                    host == "github.com" ||
                        host.endsWith(".github.com")

                ExternalProvider.RAILWAY ->
                    host == "railway.com" ||
                        host.endsWith(".railway.com") ||
                        host == "railway.app" ||
                        host.endsWith(".railway.app")
            }

        require(
            uri.scheme.equals(
                "https",
                ignoreCase = true
            ) && trustedHost
        ) {
            "Sağlayıcı güvenilir olmayan bir yetkilendirme adresi döndürdü."
        }
    }

    private fun validateRailwayAuthorizationUri(
        value: String
    ) {
        val uri = URI(value)
        require(
            uri.scheme == "https" &&
                uri.host == "backboard.railway.com" &&
                uri.path == "/oauth/auth" &&
                uri.userInfo == null &&
                uri.fragment == null
        ) {
            "Railway yetkilendirme adresi geçersiz."
        }
    }

    private fun oauthToken(
        json: JSONObject,
        fallbackScopes: String
    ): OAuthToken {
        val accessToken =
            validateOAuthToken(
                json.getString("access_token"),
                required = true
            )
        val refreshToken =
            validateOAuthToken(
                json.optString("refresh_token"),
                required = false
            )
        val expiresIn =
            json.optLong("expires_in", 0L)
                .coerceAtLeast(0L)

        return OAuthToken(
            accessToken = accessToken,
            refreshToken = refreshToken,
            scopes =
                json.optString("scope")
                    .ifBlank { fallbackScopes }
                    .take(512),
            expiresAt =
                if (expiresIn > 0L) {
                    System.currentTimeMillis() +
                        expiresIn
                            .coerceAtMost(86_400L) *
                        1_000L
                } else {
                    0L
                }
        )
    }

    private fun validateOAuthToken(
        value: String,
        required: Boolean
    ): String {
        val clean = value.trim()
        require(
            (!required || clean.isNotBlank()) &&
                clean.length <= MAX_TOKEN_LENGTH &&
                clean.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "Sağlayıcı geçersiz bir token döndürdü."
        }
        return clean
    }

    private fun validateClientId(
        value: String
    ): String {
        val clean = value.trim()
        require(
            clean.isNotBlank() &&
                clean.length <= 512 &&
                clean.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "Railway OAuth istemci kimliği yapılandırılmamış."
        }
        return clean
    }

    private fun parseQuery(
        rawQuery: String
    ): Map<String, String> {
        require(rawQuery.length <= MAX_CALLBACK_QUERY_LENGTH) {
            "Railway dönüş verisi çok büyük."
        }

        val result = linkedMapOf<String, String>()
        val entries =
            rawQuery
                .split('&')
                .filter { it.isNotBlank() }

        require(entries.size <= MAX_CALLBACK_PARAMETERS) {
            "Railway dönüş verisi geçersiz."
        }

        entries.forEach { entry ->
            val parts = entry.split('=', limit = 2)
            val key =
                java.net.URLDecoder.decode(
                    parts[0],
                    Charsets.UTF_8.name()
                )
            val value =
                java.net.URLDecoder.decode(
                    parts.getOrElse(1) { "" },
                    Charsets.UTF_8.name()
                )

            require(key.isNotBlank() && key !in result) {
                "Railway dönüş verisi yinelenen alan içeriyor."
            }
            result[key] = value
        }

        return result
    }

    private fun randomBase64Url(
        byteCount: Int
    ): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun base64Url(
        value: ByteArray
    ): String =
        Base64.encodeToString(
            value,
            Base64.URL_SAFE or
                Base64.NO_WRAP or
                Base64.NO_PADDING
        )

    private fun get(
        url: String,
        accessToken: String
    ): HttpResponse =
        request(
            method = "GET",
            url = url,
            accessToken = accessToken
        )

    private fun request(
        method: String,
        url: String,
        accessToken: String = "",
        contentType: String = "application/json",
        body: String = ""
    ): HttpResponse {
        require(URI(url).scheme == "https") {
            "Yalnızca HTTPS bağlantıları desteklenir."
        }

        val connection =
            URI(url)
                .toURL()
                .openConnection() as HttpsURLConnection

        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )
            connection.setRequestProperty(
                "User-Agent",
                "AppForge-Studio/5.1"
            )

            if (accessToken.isNotBlank()) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $accessToken"
                )
            }

            if (body.isNotEmpty()) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    contentType
                )
                connection.outputStream.use {
                    it.write(
                        body.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            }

            val code = connection.responseCode
            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            HttpResponse(
                code = code,
                body = readLimited(stream)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureSuccess(
        response: HttpResponse
    ) {
        if (response.code !in 200..299) {
            val json = parseJson(response.body)

            error(
                oauthErrorMessage(
                    response,
                    json,
                    "Bağlantı isteği başarısız."
                )
            )
        }
    }

    private fun oauthErrorMessage(
        response: HttpResponse,
        json: JSONObject,
        fallback: String
    ): String {
        val detail =
            json.optString("error_description")
                .ifBlank {
                    json.optString("message")
                }
                .ifBlank {
                    json.optString("error")
                }

        return if (detail.isBlank()) {
            "$fallback (HTTP ${response.code})"
        } else {
            "$fallback ${detail.take(MAX_ERROR_DETAIL_LENGTH)}"
        }
    }

    private fun parseJson(
        body: String
    ): JSONObject =
        runCatching {
            JSONObject(body)
        }.getOrDefault(
            JSONObject()
        )

    private fun readLimited(
        stream: InputStream?
    ): String {
        if (stream == null) {
            return ""
        }

        val bytes =
            stream.use {
                input ->
                val buffer = ByteArray(8_192)
                val output =
                    java.io.ByteArrayOutputStream()

                while (output.size() < MAX_RESPONSE_BYTES) {
                    val remaining =
                        minOf(
                            buffer.size,
                            MAX_RESPONSE_BYTES -
                                output.size()
                        )
                    val read =
                        input.read(
                            buffer,
                            0,
                            remaining
                        )

                    if (read <= 0) {
                        break
                    }

                    output.write(buffer, 0, read)
                }

                output.toByteArray()
            }

        return String(
            bytes,
            Charsets.UTF_8
        )
    }

    private fun encode(
        value: String
    ): String =
        URLEncoder.encode(
            value,
            Charsets.UTF_8.name()
        )

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    private const val MAX_RESPONSE_BYTES =
        256 * 1_024

    private const val MAX_TOKEN_LENGTH =
        32 * 1_024

    private const val MAX_AUTH_CODE_LENGTH =
        8 * 1_024

    private const val MAX_CALLBACK_QUERY_LENGTH =
        16 * 1_024

    private const val MAX_CALLBACK_PARAMETERS =
        16

    private const val MAX_ERROR_DETAIL_LENGTH =
        512

    private const val GITHUB_DEVICE_ENDPOINT =
        "https://github.com/login/device/code"

    private const val RAILWAY_AUTH_ENDPOINT =
        "https://backboard.railway.com/oauth/auth"

    const val RAILWAY_REDIRECT_URI =
        "appforge-studio://auth/railway"

    private const val RAILWAY_AUTH_LIFETIME_MS =
        10 * 60 * 1_000L
}
