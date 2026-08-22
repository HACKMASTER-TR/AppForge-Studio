package com.appforge.studio.security

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SecurityConfig(
    val integrityEnabled: Boolean,
    val cloudProjectNumber: Long,
    val proProductId: String,
    val proMonthlyProductId: String,
    val strictProIntegrity: Boolean
)

data class ProStatus(
    val active: Boolean,
    val source: String?,
    val productId: String?,
    val expiresAt: String?,
    val integrityRequired: Boolean
)

class StudioSecurityClient(
    context: Context,
    private val baseUrl: String,
    private val accessToken: String
) {
    private val appContext =
        context.applicationContext

    private val integrityManager =
        IntegrityManagerFactory
            .createStandard(appContext)

    private var tokenProvider:
        StandardIntegrityManager.StandardIntegrityTokenProvider? =
        null

    suspend fun config(): SecurityConfig {
        val json =
            request(
                path = "/api/security/config",
                method = "GET",
                body = null,
                integritySession = null
            )

        return SecurityConfig(
            integrityEnabled =
                json.optBoolean(
                    "integrityEnabled",
                    false
                ),
            cloudProjectNumber =
                json.optLong(
                    "cloudProjectNumber",
                    0L
                ),
            proProductId =
                json.optString(
                    "proProductId",
                    ""
                ),
            proMonthlyProductId =
                json.optString(
                    "proMonthlyProductId",
                    ""
                ),
            strictProIntegrity =
                json.optBoolean(
                    "strictProIntegrity",
                    true
                )
        )
    }

    suspend fun attest(
        userId: String,
        action: String = "pro_status"
    ): String {
        val cfg = config()

        if (!cfg.integrityEnabled) {
            error(
                "Play Integrity sunucuda etkin değil."
            )
        }

        require(
            cfg.cloudProjectNumber > 0
        ) {
            "Play Integrity Cloud project number eksik."
        }

        if (tokenProvider == null) {
            tokenProvider =
                integrityManager
                    .prepareIntegrityToken(
                        StandardIntegrityManager
                            .PrepareIntegrityTokenRequest
                            .builder()
                            .setCloudProjectNumber(
                                cfg.cloudProjectNumber
                            )
                            .build()
                    )
                    .await()
        }

        val nonce =
            ByteArray(24)
                .also {
                    SecureRandom()
                        .nextBytes(it)
                }
                .let {
                    Base64.encodeToString(
                        it,
                        Base64.URL_SAFE or
                            Base64.NO_WRAP or
                            Base64.NO_PADDING
                    )
                }

        val timestamp =
            System.currentTimeMillis()

        val material =
            "$userId|$action|$nonce|$timestamp"

        val requestHash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    material.toByteArray()
                )
                .let {
                    Base64.encodeToString(
                        it,
                        Base64.URL_SAFE or
                            Base64.NO_WRAP or
                            Base64.NO_PADDING
                    )
                }

        val token =
            tokenProvider
                ?.request(
                    StandardIntegrityManager
                        .StandardIntegrityTokenRequest
                        .builder()
                        .setRequestHash(
                            requestHash
                        )
                        .build()
                )
                ?.await()
                ?.token()
                ?: error(
                    "Play Integrity token alınamadı."
                )

        val localSignature =
            AppSignatureVerifier
                .check(appContext)

        val response =
            request(
                path = "/api/security/attest",
                method = "POST",
                body =
                    JSONObject()
                        .put(
                            "integrityToken",
                            token
                        )
                        .put(
                            "requestHash",
                            requestHash
                        )
                        .put(
                            "action",
                            action
                        )
                        .put(
                            "nonce",
                            nonce
                        )
                        .put(
                            "timestamp",
                            timestamp
                        )
                        .put(
                            "localCertificateSha256",
                            localSignature
                                .detectedSha256
                                .firstOrNull()
                                .orEmpty()
                        ),
                integritySession = null
            )

        return response.getString(
            "integritySession"
        )
    }


    suspend fun activatePro(
        userId: String,
        purchaseToken: String,
        plan: String
    ): ProStatus {
        val cfg =
            config()

        val integritySession =
            if (
                cfg.integrityEnabled &&
                cfg.strictProIntegrity
            ) {
                attest(
                    userId,
                    "pro_activate"
                )
            } else {
                null
            }

        val json =
            request(
                path =
                    "/api/pro/activate",
                method =
                    "POST",
                body =
                    JSONObject()
                        .put(
                            "purchaseToken",
                            purchaseToken
                        )
                        .put(
                            "plan",
                            if (
                                plan ==
                                "monthly"
                            ) {
                                "monthly"
                            } else {
                                "lifetime"
                            }
                        ),
                integritySession =
                    integritySession
            )

        return ProStatus(
            active =
                json.optBoolean(
                    "active",
                    false
                ),
            source =
                json.optString(
                    "source"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            productId =
                json.optString(
                    "productId"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            expiresAt =
                json.optString(
                    "expiresAt"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            integrityRequired =
                cfg.strictProIntegrity
        )
    }

    suspend fun proStatus(
        userId: String
    ): ProStatus {
        val cfg = config()

        val integritySession =
            if (
                cfg.integrityEnabled &&
                cfg.strictProIntegrity
            ) {
                attest(
                    userId,
                    "pro_status"
                )
            } else {
                null
            }

        val json =
            request(
                path = "/api/pro/status",
                method = "GET",
                body = null,
                integritySession =
                    integritySession
            )

        return ProStatus(
            active =
                json.optBoolean(
                    "active",
                    false
                ),
            source =
                json.optString(
                    "source"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            productId =
                json.optString(
                    "productId"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            expiresAt =
                json.optString(
                    "expiresAt"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            integrityRequired =
                json.optBoolean(
                    "integrityRequired",
                    true
                )
        )
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject?,
        integritySession: String?
    ): JSONObject {
        if (
            !baseUrl.startsWith(
                "https://",
                ignoreCase = true
            ) &&
            !baseUrl.startsWith(
                "http://10.0.2.2",
                ignoreCase = true
            )
        ) {
            error(
                "Güvenlik / Pro API'si üretimde HTTPS gerektirir."
            )
        }

        val conn =
            (
                URL(
                    baseUrl.trimEnd('/') +
                        path
                ).openConnection()
                    as HttpURLConnection
            ).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty(
                    "Accept",
                    "application/json"
                )
                setRequestProperty(
                    "Authorization",
                    "Bearer $accessToken"
                )

                if (
                    !integritySession
                        .isNullOrBlank()
                ) {
                    setRequestProperty(
                        "X-AppForge-Integrity",
                        integritySession
                    )
                }

                if (body != null) {
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                    )
                }
            }

        if (body != null) {
            conn.outputStream.use {
                it.write(
                    body.toString()
                        .toByteArray()
                )
            }
        }

        val text =
            (
                if (
                    conn.responseCode in
                    200..299
                ) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }
            )
                ?.bufferedReader()
                ?.use {
                    it.readText()
                }
                .orEmpty()

        if (
            conn.responseCode !in
            200..299
        ) {
            val message =
                runCatching {
                    JSONObject(text)
                        .optString(
                            "error",
                            "Güvenlik doğrulaması başarısız."
                        )
                }.getOrDefault(
                    "Güvenlik doğrulaması başarısız."
                )

            throw IllegalStateException(
                message
            )
        }

        return JSONObject(text)
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine {
        continuation ->
        addOnSuccessListener {
            value ->
            if (
                continuation.isActive
            ) {
                continuation.resume(
                    value
                )
            }
        }

        addOnFailureListener {
            error ->
            if (
                continuation.isActive
            ) {
                continuation
                    .resumeWithException(
                        error
                    )
            }
        }

        addOnCanceledListener {
            continuation.cancel()
        }
    }
