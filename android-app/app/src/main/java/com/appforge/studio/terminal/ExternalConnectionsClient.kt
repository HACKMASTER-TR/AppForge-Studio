package com.appforge.studio.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
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
            }
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

    private const val MAX_ERROR_DETAIL_LENGTH =
        512

    private const val GITHUB_DEVICE_ENDPOINT =
        "https://github.com/login/device/code"

}
