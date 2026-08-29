package com.appforge.studio.net

import android.content.Context
import com.appforge.studio.security.StudioDeviceIdentity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Session(
    val token: String,
    val userId: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val twoFactorEnabled: Boolean
)

sealed class LoginResult {
    data class Success(val session: Session) : LoginResult()
    data class TwoFactorRequired(val challengeToken: String) : LoginResult()
}

class AppForgeAccountClient(
    context: Context,
    private val baseUrl: String
) {
    private val deviceId =
        StudioDeviceIdentity.value(
            context.applicationContext
        )

    fun register(
        email: String,
        password: String,
        displayName: String
    ): Session {
        val json = request(
            "/api/auth/register",
            JSONObject().apply {
                put("email", email)
                put("password", password)
                put("displayName", displayName)
            }
        )
        return session(json)
    }

    fun login(email: String, password: String): LoginResult {
        val json = request(
            "/api/auth/login",
            JSONObject().apply {
                put("email", email)
                put("password", password)
            }
        )

        if (json.optBoolean("requiresTwoFactor", false)) {
            return LoginResult.TwoFactorRequired(
                json.getString("challengeToken")
            )
        }

        return LoginResult.Success(session(json))
    }

    fun verifyTwoFactor(
        challengeToken: String,
        code: String
    ): Session {
        val json = request(
            "/api/auth/2fa/verify-login",
            JSONObject().apply {
                put("challengeToken", challengeToken)
                put("code", code)
            }
        )
        return session(json)
    }

    fun transferDevice(
        email: String,
        password: String,
        twoFactorCode: String = ""
    ): Session {
        return session(
            request(
                "/api/auth/device-transfer",
                JSONObject().apply {
                    put("email", email)
                    put("password", password)
                    if (twoFactorCode.isNotBlank()) {
                        put("twoFactorCode", twoFactorCode)
                    }
                }
            )
        )
    }


    fun createBuildApiToken(
        sessionToken: String,
        name: String = "Android App"
    ): String {
        val conn =
            (
                URL(
                    baseUrl.trimEnd('/') +
                        "/api/auth/api-tokens"
                ).openConnection()
                as HttpURLConnection
            ).apply {
                requestMethod = "POST"
                doOutput = true

                connectTimeout =
                    15_000

                readTimeout =
                    20_000

                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                setRequestProperty(
                    "Authorization",
                    "Bearer $sessionToken"
                )

                setRequestProperty(
                    "X-AppForge-Device-ID",
                    deviceId
                )
            }

        val body =
            JSONObject()
                .put(
                    "name",
                    name
                )

        conn.outputStream.use {
            it.write(
                body.toString()
                    .toByteArray()
            )
        }

        val text =
            if (
                conn.responseCode in
                200..299
            ) {
                conn.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }
            } else {
                conn.errorStream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()
            }

        if (
            conn.responseCode !in
            200..299
        ) {
            throw IllegalStateException(
                runCatching {
                    JSONObject(text)
                        .optString(
                            "error",
                            "API token oluşturulamadı."
                        )
                }.getOrDefault(
                    "API token oluşturulamadı."
                )
            )
        }

        return JSONObject(text)
            .getString(
                "token"
            )
    }

    fun deleteAccount(
        email: String,
        password: String,
        twoFactorCode: String = ""
    ): JSONObject {
        return request(
            "/api/auth/delete-account",
            JSONObject().apply {
                put(
                    "email",
                    email
                )
                put(
                    "password",
                    password
                )

                if (
                    twoFactorCode
                        .isNotBlank()
                ) {
                    put(
                        "twoFactorCode",
                        twoFactorCode
                    )
                }
            }
        )
    }


    private fun session(json: JSONObject): Session {
        val user = json.getJSONObject("user")

        return Session(
            token = json.getString("token"),
            userId = user.getString("id"),
            email = user.getString("email"),
            displayName = user.optString("displayName"),
            emailVerified = user.optBoolean("emailVerified", false),
            twoFactorEnabled = user.optBoolean("twoFactorEnabled", false)
        )
    }

    private fun request(
        path: String,
        body: JSONObject
    ): JSONObject {
        val conn =
            (URL(baseUrl.trimEnd('/') + path).openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                setRequestProperty(
                    "X-AppForge-Device-ID",
                    deviceId
                )
            }

        conn.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        val text =
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
            }

        if (conn.responseCode !in 200..299) {
            throw IllegalStateException(
                runCatching {
                    JSONObject(text).optString(
                        "error",
                        "İstek başarısız."
                    )
                }.getOrDefault("İstek başarısız.")
            )
        }

        return JSONObject(text)
    }
}
