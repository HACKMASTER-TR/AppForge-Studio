package com.appforge.studio.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import com.appforge.studio.model.DEFAULT_CONTROL_PLANE_URL
import com.appforge.studio.security.OwnerAccessPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Fail-closed clipboard boundary for short-lived, server-verified Google administrators.
 * Legacy account tokens, emails, device IDs and workspace URLs confer no privileges.
 */
internal class TerminalSafeAutoCopy(
    private val context: Context,
    private val currentTerminalSession: () -> String?,
    private val lifecycleActive: () -> Boolean,
    private val clipboardDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val policy = TerminalSafeAutoCopyPolicy()
    private var enabledGoogleIdToken: String? = null
    val enabled: Boolean get() = policy.enabled

    fun disable() {
        policy.setEnabled(false, false)
        enabledGoogleIdToken = null
    }

    fun begin(command: String, sessionId: String): TerminalCopyTicket? =
        if (lifecycleActive() && currentTerminalSession() == sessionId)
            policy.begin(command, sessionId) else null

    /** The in-memory token is a candidate, never the authorization itself. */
    private fun activeGoogleAdminToken(): String? {
        if (!OwnerAccessPolicy.isActiveOwner(context)) return null
        return OwnerAccessPolicy.currentGoogleIdToken()
            ?.takeIf { it.length in 50..12000 }
    }

    suspend fun enable(): Boolean {
        disable()
        val revision = policy.revision
        val terminalSession = currentTerminalSession() ?: return false
        if (!lifecycleActive()) return false
        val token = activeGoogleAdminToken() ?: return false
        val verificationStarted = SystemClock.elapsedRealtime()
        val authorized = freshAdmin(token)
        return withContext(clipboardDispatcher) {
            currentCoroutineContext().ensureActive()
            if (revision != policy.revision || activeGoogleAdminToken() != token ||
                !lifecycleActive() || currentTerminalSession() != terminalSession ||
                SystemClock.elapsedRealtime() - verificationStarted !in 0L..6_000L)
                return@withContext false
            if (policy.setEnabled(true, authorized)) {
                enabledGoogleIdToken = token
                true
            } else false
        }
    }

    suspend fun copyCompleted(ticket: TerminalCopyTicket, result: TerminalCopyResult): Boolean {
        val revision = policy.revision
        if (!lifecycleActive() || !policy.claim(ticket, result, currentTerminalSession().orEmpty()))
            return false
        try {
            val token = enabledGoogleIdToken ?: return false
            if (activeGoogleAdminToken() != token) {
                disable()
                return false
            }
            val verificationStarted = SystemClock.elapsedRealtime()
            val authorized = freshAdmin(token)
            return withContext(clipboardDispatcher) {
                currentCoroutineContext().ensureActive()
                if (!authorized || activeGoogleAdminToken() != token || !lifecycleActive()) {
                    if (revision == policy.revision) disable()
                    return@withContext false
                }
                val text = policy.consume(ticket, result, authorized,
                    currentTerminalSession().orEmpty()) ?: return@withContext false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@withContext false
                try {
                    currentCoroutineContext().ensureActive()
                    if (revision != policy.revision || activeGoogleAdminToken() != token ||
                        !lifecycleActive() || currentTerminalSession() != ticket.sessionId ||
                        SystemClock.elapsedRealtime() - verificationStarted !in 0L..6_000L)
                        return@withContext false
                    clipboard.setPrimaryClip(ClipData.newPlainText("AppForge result", text))
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: RuntimeException) {
                    false
                }
            }
        } catch (cancelled: CancellationException) {
            if (revision == policy.revision) disable()
            throw cancelled
        }
    }

    /** V3: only complete Bash/PTY messages may be copied; never a raw PTY frame. */
    suspend fun copyPtyCompleted(
        event: TerminalPtyCopyEvent,
        stillCurrent: () -> Boolean
    ): Boolean {
        if (!policy.enabled || !lifecycleActive() || !stillCurrent() || event.reason != null) return false
        if (event.output.isBlank() ||
            event.output.toByteArray(Charsets.UTF_8).size > TerminalPtyCopyCapture.MAX_COPY_BYTES ||
            event.output.any { it == '\u0000' || it == '\u001b' }) return false
        val revision = policy.revision
        val logicalSession = currentTerminalSession() ?: return false
        val token = enabledGoogleIdToken ?: return false
        if (activeGoogleAdminToken() != token) {
            disable()
            return false
        }
        val verificationStarted = SystemClock.elapsedRealtime()
        val authorized = freshAdmin(token)
        return withContext(clipboardDispatcher) {
            currentCoroutineContext().ensureActive()
            if (!authorized || activeGoogleAdminToken() != token || !lifecycleActive()) {
                if (revision == policy.revision) disable()
                return@withContext false
            }
            if (!policy.enabled || revision != policy.revision ||
                enabledGoogleIdToken != token || currentTerminalSession() != logicalSession ||
                !stillCurrent() ||
                SystemClock.elapsedRealtime() - verificationStarted !in 0L..6_000L)
                return@withContext false
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@withContext false
            try {
                currentCoroutineContext().ensureActive()
                if (!policy.enabled || revision != policy.revision ||
                    activeGoogleAdminToken() != token || !lifecycleActive() || !stillCurrent())
                    return@withContext false
                clipboard.setPrimaryClip(ClipData.newPlainText("AppForge PTY result", event.output))
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                false
            }
        }
    }

    private suspend fun freshAdmin(token: String): Boolean {
        val started = SystemClock.elapsedRealtime()
        val allowed = withTimeoutOrNull(6_000L) { verifyGoogleAdmin(token) } ?: false
        currentCoroutineContext().ensureActive()
        return allowed && SystemClock.elapsedRealtime() - started in 0L..6_000L
    }

    private suspend fun verifyGoogleAdmin(token: String): Boolean = withContext(Dispatchers.IO) {
        if (token.length !in 50..12000) return@withContext false
        try {
            // Match the current AdminOpsScreen / GoogleAdminIdentityClient authority.
            // Never use the device://local build-service authority or user workspaces.
            val url = URL(DEFAULT_CONTROL_PLANE_URL.trimEnd('/') + "/api/admin/system-status")
            if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null)
                return@withContext false
            val connection = url.openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (connection.responseCode != 200) return@withContext false
                val text = connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(128 * 1024 + 1)
                    var length = 0
                    while (length < buffer.size) {
                        val count = reader.read(buffer, length, buffer.size - length)
                        if (count < 0) break
                        length += count
                    }
                    if (length > 128 * 1024) return@withContext false
                    String(buffer, 0, length)
                }
                val response = JSONObject(text)
                TerminalAdminCopyDecision.permits(
                    ok = response.optBoolean("ok", false),
                    adminVerified = response.optBoolean("adminVerified", false),
                    expiresAtSeconds = response.optLong("expiresAt", 0L),
                    nowSeconds = System.currentTimeMillis() / 1000L
                )
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Fail closed. No token, HTTP response body or owner identity is logged.
            false
        }
    }
}
