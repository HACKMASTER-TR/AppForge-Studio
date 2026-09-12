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
    val quota10ProductId: String,
    val quota25ProductId: String,
    val quota50ProductId: String,
    val strictProIntegrity: Boolean
)

data class QuotaAddonRedemption(
    val productId: String,
    val projectBonus: Int,
    val buildBonus: Int,
    val cycleEnd: String?,
    val testPurchase: Boolean,
    val idempotent: Boolean,
    val projectUsed: Int?,
    val projectLimit: Int?,
    val buildUsed: Int?,
    val buildLimit: Int?
)

data class ProStatus(
    val active: Boolean,
    val source: String?,
    val productId: String?,
    val expiresAt: String?,
    val integrityRequired: Boolean
)

data class QuotaStatus(
    val projectUsed: Int,
    val projectLimit: Int?,
    val projectAddonBonus: Int,
    val buildUsed: Int?,
    val buildLimit: Int?,
    val buildAddonBonus: Int,
    val periodEndsAt: String?
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
            quota10ProductId =
                json.optString(
                    "quota10ProductId",
                    ""
                ),
            quota25ProductId =
                json.optString(
                    "quota25ProductId",
                    ""
                ),
            quota50ProductId =
                json.optString(
                    "quota50ProductId",
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

    suspend fun quotaStatus(): QuotaStatus {
        val json =
            request(
                path =
                    "/api/projects/quota",
                method =
                    "GET",
                body =
                    null,
                integritySession =
                    null
            )

        val quota =
            json.optJSONObject(
                "quota"
            )
                ?: error(
                    "Kota yanıtı geçersiz."
                )

        val buildQuota =
            quota.optJSONObject(
                "buildQuota"
            )

        fun nullableInt(
            source: JSONObject?,
            key: String
        ): Int? {
            if (
                source == null ||
                !source.has(key) ||
                source.isNull(key)
            ) {
                return null
            }

            return source.optInt(
                key
            )
        }

        return QuotaStatus(
            projectUsed =
                quota.optInt(
                    "used",
                    0
                ),

            projectLimit =
                nullableInt(
                    quota,
                    "limit"
                ),

            projectAddonBonus =
                quota.optInt(
                    "addonProjectBonus",
                    0
                ),

            buildUsed =
                nullableInt(
                    buildQuota,
                    "used"
                ),

            buildLimit =
                nullableInt(
                    buildQuota,
                    "limit"
                ),

            buildAddonBonus =
                buildQuota
                    ?.optInt(
                        "addonBuildBonus",
                        0
                    )
                    ?: 0,

            periodEndsAt =
                quota.optString(
                    "periodEndsAt"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                }
        )
    }


    suspend fun redeemQuotaAddon(
        userId: String,
        productId: String,
        purchaseToken: String
    ): QuotaAddonRedemption {
        val cfg =
            config()

        val allowedProducts =
            setOf(
                cfg.quota10ProductId,
                cfg.quota25ProductId,
                cfg.quota50ProductId
            )
                .filter {
                    it.isNotBlank()
                }
                .toSet()

        require(
            productId in
                allowedProducts
        ) {
            "Geçersiz AppForge ek kota ürünü."
        }

        val integritySession =
            if (
                cfg.integrityEnabled &&
                cfg.strictProIntegrity
            ) {
                attest(
                    userId,
                    "quota_addon_redeem"
                )
            } else {
                null
            }

        val json =
            request(
                path =
                    "/api/quota/addons/redeem",
                method =
                    "POST",
                body =
                    JSONObject()
                        .put(
                            "productId",
                            productId
                        )
                        .put(
                            "purchaseToken",
                            purchaseToken
                        ),
                integritySession =
                    integritySession
            )

        val redemption =
            json.optJSONObject(
                "redemption"
            )
                ?: error(
                    "Ek kota satın alma yanıtı geçersiz."
                )

        val quota =
            json.optJSONObject(
                "quota"
            )

        val buildQuota =
            quota?.optJSONObject(
                "buildQuota"
            )

        return QuotaAddonRedemption(
            productId =
                redemption.optString(
                    "productId"
                ),
            projectBonus =
                redemption.optInt(
                    "projectBonus",
                    0
                ),
            buildBonus =
                redemption.optInt(
                    "buildBonus",
                    0
                ),
            cycleEnd =
                redemption.optString(
                    "cycleEnd"
                ).takeIf {
                    it.isNotBlank() &&
                    it != "null"
                },
            testPurchase =
                redemption.optBoolean(
                    "testPurchase",
                    false
                ),
            idempotent =
                redemption.optBoolean(
                    "idempotent",
                    false
                ),
            projectUsed =
                quota
                    ?.takeIf {
                        it.has("used")
                    }
                    ?.optInt(
                        "used"
                    ),
            projectLimit =
                quota
                    ?.takeIf {
                        it.has("limit")
                    }
                    ?.optInt(
                        "limit"
                    ),
            buildUsed =
                buildQuota
                    ?.takeIf {
                        it.has("used")
                    }
                    ?.optInt(
                        "used"
                    ),
            buildLimit =
                buildQuota
                    ?.takeIf {
                        it.has("limit")
                    }
                    ?.optInt(
                        "limit"
                    )
        )
    }


    suspend fun proStatus(
        userId: String
    ): ProStatus {

        fun parseStatus(
            json: JSONObject
        ): ProStatus {
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

        /*
         * Önce doğrudan sunucu durumunu sor.
         * admin_panel / admin_full_access gibi sunucu tarafından
         * yönetilen PRO entitlement'lar burada doğrudan doğrulanır.
         */
        var directError: Throwable? =
            null

        try {
            val direct =
                request(
                    path =
                        "/api/pro/status",
                    method =
                        "GET",
                    body =
                        null,
                    integritySession =
                        null
                )

            return parseStatus(
                direct
            )

        } catch (
            error: Throwable
        ) {
            directError =
                error
        }

        /*
         * Sunucu direct isteği kabul etmediyse Google Play PRO
         * olasılığı için mevcut Integrity akışına geç.
         */
        val cfg =
            config()

        if (
            !cfg.integrityEnabled ||
            !cfg.strictProIntegrity
        ) {
            throw (
                directError
                    ?: IllegalStateException(
                        "Pro durumu doğrulanamadı."
                    )
            )
        }

        val integritySession =
            attest(
                userId,
                "pro_status"
            )

        val verified =
            request(
                path =
                    "/api/pro/status",
                method =
                    "GET",
                body =
                    null,
                integritySession =
                    integritySession
            )

        return parseStatus(
            verified
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
                setRequestProperty(
                    "X-AppForge-Device-ID",
                    StudioDeviceIdentity.value(appContext)
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
