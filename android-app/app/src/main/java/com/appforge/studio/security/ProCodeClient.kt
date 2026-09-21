package com.appforge.studio.security

import com.appforge.studio.BuildConfig

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Accountless, admin-issued Pro activation.
 *
 * Installation ID is public, never proof of Pro.
 * Private key remains in Android Keystore.
 * Only fresh HTTPS status after a signed device challenge unlocks Pro.
 */
class ProCodeClient(
    context: Context,
    serverUrl: String
) {
    private val appContext = context.applicationContext

    private val endpoint =
        serverUrl.trim().trimEnd('/').also { value ->
            val url = URL(value)

            require(
                url.protocol == "https" &&
                    url.host.isNotBlank() &&
                    url.userInfo == null
            ) {
                "Pro işlemleri HTTPS gerektirir."
            }
        }

    private val prefs by lazy {
        appContext.getSharedPreferences(
            "appforge_pro_installation_v1",
            Context.MODE_PRIVATE
        )
    }

    private fun installationId(): String? =
        prefs.getString("installation_id", null)
            ?.takeIf {
                UUID_PATTERN.matches(it)
            }

    fun hasInstallation(): Boolean =
        installationId() != null

    data class IssuedCode(
        val id: String,
        val code: String,
        val expiresAt: Long
    )

    data class CodeRow(
        val id: String,
        val state: String,
        val createdAt: Long
    )


    data class GrantRow(
        val installationId: String,
        val activationCodeId: String,
        val state: String,
        val grantedAt: Long,
        val revokedAt: Long?
    )

    private fun post(
        path: String,
        body: JSONObject,
        adminToken: String? = null
    ): JSONObject =
        request(
            path = path,
            method = "POST",
            body = body,
            adminToken = adminToken
        )

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        adminToken: String? = null
    ): JSONObject {
        require(
            path.startsWith("/") &&
                !path.startsWith("//")
        )

        if (path.startsWith("/api/admin/")) {
            require(
                adminToken != null &&
                    adminToken.length in 50..12000
            ) {
                "Google yönetici yetkisi gerekli."
            }
        }

        val connection =
            URL(endpoint + path)
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            if (adminToken != null) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $adminToken"
                )
            }

            if (body != null) {
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                connection.outputStream.use {
                    it.write(
                        body.toString()
                            .toByteArray(Charsets.UTF_8)
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

            val text =
                stream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(8192) }
                    .orEmpty()

            if (code !in 200..299) {
                val reason = runCatching {
                    JSONObject(text)
                        .optString("error")
                        .take(80)
                }.getOrDefault("")

                error(
                    "Pro sunucusu HTTP $code" +
                        if (reason.isNotBlank()) {
                            " • $reason"
                        } else {
                            ""
                        }
                )
            }

            check(text.isNotBlank()) {
                "Pro sunucu yanıtı boş."
            }

            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(32)

        SecureRandom().nextBytes(bytes)

        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or
                Base64.NO_WRAP or
                Base64.NO_PADDING
        )
    }

    fun issueAdminCode(
        adminToken: String
    ): IssuedCode {
        val result = post(
            "/api/admin/pro-codes",
            JSONObject(),
            adminToken
        )

        check(
            result.optBoolean("ok") &&
                result.optString("state") == "issued"
        ) {
            "Pro kodu oluşturulamadı."
        }

        val id = result.getString("id")
        val code = result.getString("code")

        check(
            UUID_PATTERN.matches(id) &&
                CODE_PATTERN.matches(code)
        ) {
            "Pro kodu yanıtı geçersiz."
        }

        return IssuedCode(
            id = id,
            code = code,
            expiresAt = result.getLong("expiresAt")
        )
    }

    fun listAdminCodes(
        adminToken: String
    ): List<CodeRow> {
        val result = request(
            "/api/admin/pro-codes",
            "GET",
            adminToken = adminToken
        )

        check(result.optBoolean("ok")) {
            "Pro kodları alınamadı."
        }

        val array = result.getJSONArray("codes")

        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("id")

            check(UUID_PATTERN.matches(id))

            CodeRow(
                id = id,
                state = item.getString("state"),
                createdAt = item.getLong("created_at")
            )
        }
    }

    fun revokeUnusedCode(
        id: String,
        adminToken: String
    ) {
        require(UUID_PATTERN.matches(id))

        val result = post(
            "/api/admin/pro-codes/$id/revoke",
            JSONObject(),
            adminToken
        )

        check(
            result.optBoolean("ok") &&
                result.optString("state") == "revoked"
        ) {
            "Kullanılmamış Pro kodu iptal edilemedi."
        }
    }


    fun listAdminGrants(
        adminToken: String
    ): List<GrantRow> {
        val result = request(
            "/api/admin/pro-grants",
            "GET",
            adminToken = adminToken
        )

        check(result.optBoolean("ok")) {
            "Pro yetkileri alınamadı."
        }

        val array = result.getJSONArray("grants")

        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)

            val installationId =
                item.getString("installation_id")

            val activationCodeId =
                item.getString("activation_code_id")

            val state =
                item.getString("state")

            check(
                UUID_PATTERN.matches(installationId) &&
                    UUID_PATTERN.matches(activationCodeId) &&
                    state in setOf("active", "revoked")
            ) {
                "Pro yetki kaydı geçersiz."
            }

            GrantRow(
                installationId = installationId,
                activationCodeId = activationCodeId,
                state = state,
                grantedAt = item.getLong("granted_at"),
                revokedAt =
                    if (item.isNull("revoked_at")) {
                        null
                    } else {
                        item.getLong("revoked_at")
                    }
            )
        }
    }

    fun revokeAdminGrant(
        installationId: String,
        adminToken: String
    ) {
        require(
            UUID_PATTERN.matches(installationId)
        )

        val result = post(
            "/api/admin/pro-grants/" +
                installationId +
                "/revoke",
            JSONObject(),
            adminToken
        )

        check(
            result.optBoolean("ok") &&
                result.optString("state") == "revoked" &&
                result.optString("installationId") ==
                    installationId
        ) {
            "Aktif Pro yetkisi geri alınamadı."
        }
    }

    private data class OwnershipChallenge(
        val installationId: String,
        val challengeId: String,
        val nonce: String
    )

    private fun ownershipChallenge(): OwnershipChallenge {
        val result = post(
            "/api/pro/code/ownership-challenge",
            JSONObject()
                .put("publicKey", ProInstallationProof.publicKeySpki())
                .put("requestNonce", randomNonce())
        )
        check(result.optBoolean("ok")) {
            "Kurulum sahiplik doğrulaması alınamadı."
        }
        val id = result.getString("installationId")
        val challengeId = result.getString("challengeId")
        val nonce = result.getString("nonce")
        check(
            UUID_PATTERN.matches(id) &&
                UUID_PATTERN.matches(challengeId) &&
                NONCE_PATTERN.matches(nonce)
        ) { "Sunucudan geçersiz sahiplik doğrulaması." }
        return OwnershipChallenge(id, challengeId, nonce)
    }

    private fun persistInstallation(id: String) {
        check(UUID_PATTERN.matches(id)) { "Kurulum kimliği geçersiz." }
        check(
            prefs.edit().putString("installation_id", id).commit()
        ) { "Kurulum kimliği kaydedilemedi." }
    }

    /** Recover a recorded identity using the same Keystore key, no code needed. */
    fun recoverInstallation(): ProStatus {
        val challenge = ownershipChallenge()
        val proof = ProInstallationProof.createOwnershipProof(
            operation = "recover",
            installationId = challenge.installationId,
            challengeId = challenge.challengeId,
            nonce = challenge.nonce
        )
        val result = post(
            "/api/pro/code/recover",
            JSONObject()
                .put("installationId", proof.installationId)
                .put("challengeId", proof.challengeId)
                .put("nonce", proof.nonce)
                .put("publicKey", proof.publicKey)
                .put("signature", proof.signature)
        )
        check(
            result.optBoolean("ok") &&
                result.optString("installationId") == proof.installationId &&
                result.optString("source") == "admin_code"
        ) { "Kurulum kurtarılamadı." }
        persistInstallation(proof.installationId)
        // A revoked installation may recover its ID, never Pro access.
        if (!result.optBoolean("active")) {
            error("Pro yetkisi iptal edilmiş. Yeni kodla etkinleştirebilirsin.")
        }
        return verifyStatus()
    }

    private fun reactivateAndVerify(code: String): ProStatus {
        val challenge = ownershipChallenge()
        val stored = installationId()
            ?: error("Önce mevcut kurulum kimliğini kurtar.")
        check(stored == challenge.installationId) {
            "Kurulum kimliği eşleşmiyor. Önce kurulumu kurtar."
        }
        val proof = ProInstallationProof.createOwnershipProof(
            operation = "reactivate",
            installationId = stored,
            challengeId = challenge.challengeId,
            nonce = challenge.nonce,
            code = code
        )
        val result = post(
            "/api/pro/code/reactivate",
            JSONObject()
                .put("installationId", proof.installationId)
                .put("challengeId", proof.challengeId)
                .put("nonce", proof.nonce)
                .put("publicKey", proof.publicKey)
                .put("signature", proof.signature)
                .put("code", code)
        )
        check(result.optBoolean("ok") && result.optBoolean("active") &&
            result.optString("source") == "admin_code" &&
            result.optString("installationId") == stored
        ) { "Pro yeniden etkinleştirilemedi." }
        return verifyStatus()
    }

    /**
     * A successful redeem response alone does NOT unlock Pro.
     * Persist public installation ID, then verify fresh HTTPS status.
     */
    fun redeemAndVerify(
        code: String
    ): ProStatus {
        require(CODE_PATTERN.matches(code)) {
            "Geçersiz Pro kodu."
        }

        if (installationId() != null) {
            return reactivateAndVerify(code)
        }

        val proof =
            ProInstallationProof.createRedeemProof(code)

        val result = post(
            "/api/pro/code/redeem",
            JSONObject()
                .put("code", proof.code)
                .put("publicKey", proof.publicKey)
                .put("nonce", proof.nonce)
                .put("signature", proof.signature)
        )

        val id = result.optString("installationId")

        check(
            result.optBoolean("ok") &&
                result.optBoolean("active") &&
                result.optString("source") == "admin_code" &&
                UUID_PATTERN.matches(id)
        ) {
            "Sunucu Pro aktivasyonunu doğrulamadı."
        }

        // Only the isolated opt-in debug APK simulates a
        // lost response before local persistence. The normal
        // app and every release build never enter this branch.
        if (BuildConfig.PRO_RECOVERY_TEST) {
            error(
                "STAGING_TEST_INTERRUPTED_AFTER_REDEEM_BEFORE_SAVE"
            )
        }

        persistInstallation(id)

        return verifyStatus()
    }

    /**
     * Isolated debug-only live replay test.
     * Never exports the installation private key.
     */
    fun testLiveStatusReplay(): ProStatus {
        check(
            BuildConfig.DEBUG &&
                BuildConfig.PRO_RECOVERY_TEST &&
                appContext.packageName ==
                    "com.appforge.studio.prorecovery"
        ) {
            "Replay testi yalnız izole debug paketinde çalışır."
        }

        val id = installationId()
            ?: error("Replay testi için kurulum gerekli.")

        val challenge = post(
            "/api/pro/code/challenge",
            JSONObject()
                .put("installationId", id)
                .put("requestNonce", randomNonce())
        )

        check(challenge.optBoolean("ok")) {
            "REPLAY_CHALLENGE_FAILED"
        }

        val proof =
            ProInstallationProof.createStatusProof(
                installationId = id,
                challengeId =
                    challenge.getString("challengeId"),
                nonce = challenge.getString("nonce")
            )

        val payload = JSONObject()
            .put("installationId", proof.installationId)
            .put("challengeId", proof.challengeId)
            .put("nonce", proof.nonce)
            .put("signature", proof.signature)

        val first = post(
            "/api/pro/code/status",
            payload
        )

        check(
            first.optBoolean("ok") &&
                first.optBoolean("active") &&
                first.optString("source") ==
                    "admin_code" &&
                first.optString("entitlementKind") ==
                    "admin_grant"
        ) {
            "REPLAY_FIRST_STATUS_FAILED"
        }

        val secondError = runCatching {
            post("/api/pro/code/status", payload)
        }.exceptionOrNull()

        check(
            secondError?.message ==
                "Pro sunucusu HTTP 409 • challenge_unavailable"
        ) {
            "REPLAY_SECOND_STATUS_NOT_BLOCKED"
        }

        return verifyStatus()
    }

    fun verifyStatus(): ProStatus {
        val id = installationId()
            ?: error("Bu kurulumda Pro kodu etkinleştirilmemiş.")

        val challenge = post(
            "/api/pro/code/challenge",
            JSONObject()
                .put("installationId", id)
                .put("requestNonce", randomNonce())
        )

        check(challenge.optBoolean("ok")) {
            "Pro doğrulama isteği oluşturulamadı."
        }

        val challengeId =
            challenge.getString("challengeId")

        val nonce =
            challenge.getString("nonce")

        val proof =
            ProInstallationProof.createStatusProof(
                installationId = id,
                challengeId = challengeId,
                nonce = nonce
            )

        val status = post(
            "/api/pro/code/status",
            JSONObject()
                .put(
                    "installationId",
                    proof.installationId
                )
                .put(
                    "challengeId",
                    proof.challengeId
                )
                .put("nonce", proof.nonce)
                .put("signature", proof.signature)
        )

        check(
            status.optBoolean("ok") &&
                status.optBoolean("active") &&
                status.optString("source") ==
                    "admin_code" &&
                status.optString("entitlementKind") ==
                    "admin_grant"
        ) {
            "Sunucu Pro yetkisi vermedi."
        }

        return ProStatus(
            active = true,
            source = "admin_code",
            productId = null,
            expiresAt = null,
            integrityRequired = true
        )
    }

    companion object {
        private val UUID_PATTERN =
            Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-" +
                    "[1-8][0-9a-f]{3}-" +
                    "[89ab][0-9a-f]{3}-" +
                    "[0-9a-f]{12}$"
            )

        private val NONCE_PATTERN =
            Regex("^[A-Za-z0-9_-]{22,86}$")

        private val CODE_PATTERN =
            Regex("^AFPRO-[A-Za-z0-9_-]{43}$")
    }
}
