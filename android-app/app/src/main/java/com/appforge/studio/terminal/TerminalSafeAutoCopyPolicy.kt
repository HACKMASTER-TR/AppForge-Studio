package com.appforge.studio.terminal

/** No shell/PTY output is admitted. PTY command completion is NOT_SUPPORTED:
 * process exit, idle prompts and output frames cannot identify a completed command.
 * Fixed messages describe completed app actions, never completion of builds/imports.
 */
internal enum class TerminalCopyOrigin { FIRST_PARTY, LOCAL_SHELL, PTY, UNKNOWN }
internal enum class TerminalCopyCompletion { COMPLETED, INCOMPLETE, CANCELLED, TIMED_OUT }

internal class TerminalCopyResult private constructor(
    val command: String,
    val text: String,
    val origin: TerminalCopyOrigin,
    val completion: TerminalCopyCompletion
) {
    companion object {
        // Only this dispatcher can produce envelopes; callers cannot supply text or a safe flag.
        fun execute(command: String, action: (String) -> Unit): TerminalCopyResult? {
            val text = approvedText(command) ?: return null
            action(text) // Exceptions/cancellation never produce a completed envelope.
            return TerminalCopyResult(command, text, TerminalCopyOrigin.FIRST_PARTY,
                TerminalCopyCompletion.COMPLETED)
        }

        fun approvedText(command: String): String? = when (command) {
            "help", "appforge help" -> TerminalSafeHelp.text
            "runtime" -> "Araç durumu ekranı açıldı."
            "connections", "appforge connect" -> "Hesap bağlantıları açıldı."
            "appforge downloads" -> "İndirilenler bölümü açılıyor…"
            else -> null // Project names, paths, status, Git, shell and credentials are excluded.
        }
    }
}

internal class TerminalCopyTicket internal constructor(
    internal val command: String,
    internal val sessionId: String,
    internal val revision: Long,
    internal val sequence: Long
)

/** Main-thread confined. A command's ticket is single-use and predates execution. */
internal class TerminalSafeAutoCopyPolicy {
    var enabled = false
        private set
    var revision = 0L
        private set
    private var sequence = 0L
    private var claimed: TerminalCopyTicket? = null

    fun setEnabled(requested: Boolean, adminVerified: Boolean): Boolean {
        revision++
        claimed = null
        enabled = requested && adminVerified
        return enabled
    }

    fun begin(command: String, sessionId: String): TerminalCopyTicket? {
        sequence++ // Any later command supersedes pending results, including shell commands.
        claimed = null
        return if (enabled && sessionId.isNotBlank())
            TerminalCopyTicket(command, sessionId, revision, sequence) else null
    }

    fun claim(ticket: TerminalCopyTicket, result: TerminalCopyResult, sessionId: String): Boolean {
        if (claimed != null || !permits(ticket, result, true, sessionId)) return false
        claimed = ticket
        return true
    }

    fun permits(ticket: TerminalCopyTicket, result: TerminalCopyResult,
                adminVerified: Boolean, sessionId: String): Boolean =
        enabled && adminVerified && ticket.revision == revision && ticket.sequence == sequence &&
            ticket.sessionId == sessionId && ticket.command == result.command &&
            acceptsPayload(result.command, result.text, result.origin, result.completion)

    fun consume(ticket: TerminalCopyTicket, result: TerminalCopyResult,
                adminVerified: Boolean, sessionId: String): String? {
        if (claimed !== ticket || !permits(ticket, result, adminVerified, sessionId)) return null
        sequence++
        claimed = null
        return result.text
    }

    companion object {
        // Defense at the sink: canonical equality, bounded UTF-8, no controls/surrogates.
        fun acceptsPayload(command: String, text: String, origin: TerminalCopyOrigin,
                           completion: TerminalCopyCompletion): Boolean =
            origin == TerminalCopyOrigin.FIRST_PARTY && completion == TerminalCopyCompletion.COMPLETED &&
                text.isNotEmpty() && text.toByteArray(Charsets.UTF_8).size <= 4096 &&
                text.none { (it < ' ' && it != '\n') || it == '\u007f' || it.isSurrogate() } &&
                text == TerminalCopyResult.approvedText(command)
    }
}

/** Applied only to a fresh response from the app's authenticated HTTPS admin endpoint. */
internal object TerminalAdminCopyDecision {
    fun permits(ok: Boolean, role: String, fullAccess: Boolean, email: String, sessionEmail: String): Boolean =
        ok && role == "admin" && fullAccess && sessionEmail.isNotBlank() &&
            email.equals(sessionEmail, ignoreCase = true)
}

/** Immutable, shipped help text: no paths, project data, commands or stdout are interpolated. */
internal object TerminalSafeHelp {
    val text: String =
"""
        AppForge özel komutları
        • appforge status     Proje ve çalışma alanı bilgisi
        • appforge projects   Kayıtlı projeleri listele
        • appforge build      Builder / APK-AAB ekranını aç
        • appforge ai         Projeye bağlı AI Asistanı aç
        • appforge downloads  İndirilenler bölümünü aç
        • appforge import     Telefondan dosya seçip içe aktar
        • appforge connect    GitHub / Railway bağlantılarını aç
        • runtime             Yerel araç durumunu göster

        Yerel kabuk örnekleri
        • pwd, ls -la, find ., cat dosya.txt
        • mkdir klasor, touch dosya.txt

        Git komutları
        • git init, git status, git add ., git log
        • Commit, pull, push ve clone için Git sekmesini kullanın.
        """.trimIndent()
}
