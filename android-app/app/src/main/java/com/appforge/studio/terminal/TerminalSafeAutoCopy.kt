package com.appforge.studio.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.appforge.studio.model.DEFAULT_BUILD_SERVICE_URL
import com.appforge.studio.net.Session
import com.appforge.studio.security.SecureAccountStore
import com.appforge.studio.security.StudioDeviceIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import android.os.SystemClock
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Clipboard boundary. UI state, owner email checks and shell markers grant no authority. */
internal class TerminalSafeAutoCopy(
    private val context: Context,
    private val currentTerminalSession: () -> String?,
    private val lifecycleActive: () -> Boolean,
    private val clipboardDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val policy = TerminalSafeAutoCopyPolicy()
    private var enabledSession: Session? = null
    val enabled: Boolean get() = policy.enabled

    fun disable() {
        policy.setEnabled(false, false)
        enabledSession = null
    }

    fun begin(command: String, sessionId: String): TerminalCopyTicket? =
        if (lifecycleActive() && currentTerminalSession() == sessionId)
            policy.begin(command, sessionId) else null

    suspend fun enable(): Boolean {
        disable()
        val revision = policy.revision
        val terminalSession = currentTerminalSession() ?: return false
        if (!lifecycleActive()) return false
        val session = SecureAccountStore.loadSession(context) ?: return false
        val verificationStarted = SystemClock.elapsedRealtime()
        val authorized = freshAdmin(session)
        return withContext(clipboardDispatcher) {
            currentCoroutineContext().ensureActive()
            if (revision != policy.revision || !sameSession(session) || !lifecycleActive() ||
                currentTerminalSession() != terminalSession ||
                SystemClock.elapsedRealtime() - verificationStarted !in 0L..6_000L) return@withContext false
            if (policy.setEnabled(true, authorized)) {
                enabledSession = session
                true
            } else false
        }
    }

    suspend fun copyCompleted(ticket: TerminalCopyTicket, result: TerminalCopyResult): Boolean {
        val revision = policy.revision
        if (!lifecycleActive() || !policy.claim(ticket, result, currentTerminalSession().orEmpty()))
            return false
        try {
            val session = enabledSession ?: return false
            if (!sameSession(session)) {
                disable()
                return false
            }
            val verificationStarted = SystemClock.elapsedRealtime()
            val authorized = freshAdmin(session)
            return withContext(clipboardDispatcher) {
                currentCoroutineContext().ensureActive()
                if (!authorized || !sameSession(session) || !lifecycleActive()) {
                    if (revision == policy.revision) disable()
                    return@withContext false
                }
                val text = policy.consume(ticket, result, authorized, currentTerminalSession().orEmpty())
                    ?: return@withContext false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@withContext false
                try {
                    currentCoroutineContext().ensureActive()
                    if (revision != policy.revision || !sameSession(session) || !lifecycleActive() ||
                        currentTerminalSession() != ticket.sessionId ||
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

    private suspend fun freshAdmin(session: Session): Boolean {
        val started = SystemClock.elapsedRealtime()
        val allowed = withTimeoutOrNull(6_000L) { verifyAdmin(session) } ?: false
        currentCoroutineContext().ensureActive()
        return allowed && SystemClock.elapsedRealtime() - started in 0L..6_000L
    }

    private fun sameSession(expected: Session): Boolean {
        val current = SecureAccountStore.loadSession(context) ?: return false
        return current.userId == expected.userId && current.token == expected.token &&
            current.email == expected.email
    }

    private suspend fun verifyAdmin(session: Session): Boolean = withContext(Dispatchers.IO) {
        if (session.token.isBlank() || session.userId.isBlank()) return@withContext false
        try {
            // Fixed app authority, not a workspace/user-configured server. No credential redirects.
            val connection = URL(DEFAULT_BUILD_SERVICE_URL.trimEnd('/') + "/api/admin/system-status")
                .openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${session.token}")
                connection.setRequestProperty("X-AppForge-Device-ID", StudioDeviceIdentity.value(context))
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
                val account = response.optJSONObject("account") ?: return@withContext false
                TerminalAdminCopyDecision.permits(
                    ok = response.optBoolean("ok", false),
                    role = account.optString("role"),
                    fullAccess = account.optBoolean("fullAccess", false),
                    email = account.optString("email"),
                    sessionEmail = session.email
                )
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Fail closed without logging credentials, response bodies or terminal text.
            false
        }
    }
}
