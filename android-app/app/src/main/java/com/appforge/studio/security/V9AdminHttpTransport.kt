package com.appforge.studio.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Offline-reviewed Android adapter ONLY. It is NOT instantiated by current app code.
 * Backend endpoints do not exist until separate auth, rate-limit and deploy approval.
 * Never reuse GoogleAdminIdentityClient, a cached token or a local owner flag.
 */
internal class V9AdminHttpTransport(
    baseUrl: String,
    private val signer: V9AdminRequestProofSigner,
    private val now: () -> Long = System::currentTimeMillis
) : V9AdminSessionSource, V9AdminDecisionSink {
    private val origin: String
    init {
        val u = URI(baseUrl)
        require(u.scheme == "https" && !u.host.isNullOrBlank() &&
            u.rawPath in listOf("", "/") && u.rawQuery == null &&
            u.rawFragment == null && u.rawUserInfo == null &&
            (u.port == -1 || u.port == 443) &&
            Regex("^(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,63}$").matches(u.host)) {
            "V9 trusted HTTPS origin required"
        }
        origin = "https://${u.host}"
    }
    private fun request(path: String, body: JSONObject): JSONObject {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 12_000) { "V9 request too large" }
        val signed = signer.sign("POST", origin, path, bytes).validate(now())
        // Signature must cover the EXACT bytes sent below, and must be verified by
        // a server-side key pin + freshness + replay store. Header values alone NEVER authorize.
        val conn = URL(origin + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.doOutput = true
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Cache-Control", "no-store")
            conn.setRequestProperty("X-AppForge-V9-Principal", signed.principalId)
            conn.setRequestProperty("X-AppForge-V9-Proof-Id", signed.proofId)
            conn.setRequestProperty("X-AppForge-V9-Issued-At", signed.issuedAtMs.toString())
            conn.setRequestProperty("X-AppForge-V9-Signature", signed.signatureDerB64Url)
            conn.outputStream.use { it.write(bytes) }
            check(conn.responseCode == HttpURLConnection.HTTP_OK &&
                conn.contentType?.lowercase()?.startsWith("application/json") == true) {
                "V9 remote administrator request denied"
            }
            val limit = 4_096
            val result = conn.inputStream.use { input ->
                val bytesOut = java.io.ByteArrayOutputStream()
                val buf = ByteArray(1_024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    check(bytesOut.size() + n <= limit) { "V9 response too large" }
                    bytesOut.write(buf, 0, n)
                }
                bytesOut.toByteArray().toString(Charsets.UTF_8)
            }
            return JSONObject(result)
        } finally {
            conn.disconnect()
        }
    }
    override suspend fun begin(): V9AdminNonceWire.ServerChallenge = withContext(Dispatchers.IO) {
        val json = request("/v9/private/admin/session/begin", JSONObject())
        val data = V9AdminNonceWire.ServerChallenge(
            json.getString("sessionId"), json.getString("nonce"), json.getLong("expiresAt")
        )
        check(json.length() == 3) { "V9 unexpected session response" }
        V9AdminNonceWire.validate(data, now())
    }
    override suspend fun submit(request: V9AdminDecisionRequest): V9AdminDecisionResult =
        withContext(Dispatchers.IO) {
            V9AdminNonceWire.checkDecision(request.deviceId, request.action)
            val payload = JSONObject().put("sessionId", request.sessionId)
                .put("deviceId", request.deviceId).put("action", request.action)
                .put("idToken", request.idToken)
            val json = request("/v9/private/admin/device/decide", payload)
            check(json.length() == 2) { "V9 unexpected decision response" }
            V9AdminDecisionResult(json.getString("deviceId"), json.getString("status"))
        }
}
