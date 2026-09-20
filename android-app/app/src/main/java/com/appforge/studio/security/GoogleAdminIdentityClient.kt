package com.appforge.studio.security

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.appforge.studio.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** Admin ONLY. Google credential is not a normal AppForge account or Pro grant. */
class GoogleAdminIdentityClient(private val activity: Activity, serverUrl: String) {
    private val endpoint: String = serverUrl.trim().trimEnd('/').also {
        require(it.startsWith("https://") && URL(it).host.isNotBlank()) {
            "Google yönetici girişi HTTPS gerektirir."
        }
    }

    suspend fun signIn() {
        val clientId = BuildConfig.APPFORGE_GOOGLE_WEB_CLIENT_ID
        require(clientId.endsWith(".apps.googleusercontent.com")) {
            "Google Web OAuth istemcisi yapılandırılmamış."
        }
        val nonce = ByteArray(32).also(SecureRandom()::nextBytes).let {
            Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity)
            .getCredential(context = activity, request = request).credential
        require(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google kimlik bilgisi alınamadı."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val expiresAt = withContext(Dispatchers.IO) { verify(idToken, nonce) }
        OwnerAccessPolicy.rememberVerifiedGoogleAdmin(idToken, expiresAt)
    }

    private fun verify(idToken: String, nonce: String): Long {
        val conn = (URL("$endpoint/api/admin/google/verify").openConnection()
            as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.instanceFollowRedirects = false
            conn.outputStream.use { stream ->
                stream.write(JSONObject().put("idToken", idToken)
                    .put("nonce", nonce).toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code != 200) {
                // Never expose the ID token or arbitrary server response.
                // Only display known public control-plane error codes.
                val serverCode = runCatching {
                    val body = conn.errorStream
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText().take(2048) }
                        .orEmpty()
                    JSONObject(body).optString("error", "")
                }.getOrDefault("")

                val safeReason = when (serverCode) {
                    "admin_identity_not_configured",
                    "identity_provider_unavailable",
                    "admin_allowlist_unavailable",
                    "admin_forbidden",
                    "invalid_identity",
                    "service_unavailable" -> serverCode
                    else -> "unknown_error"
                }

                throw IllegalStateException(
                    "Google yönetici doğrulaması başarısız " +
                        "(HTTP $code • $safeReason)."
                )
            }
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8)
                .use { it.readText().take(8192) })
            check(json.optBoolean("ok") && json.optBoolean("adminVerified")) {
                "Sunucu yönetici yetkisi vermedi."
            }
            return json.getLong("expiresAt")
        } finally {
            conn.disconnect()
        }
    }
}
