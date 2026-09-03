package com.appforge.studio.terminal

data class TerminalCommandReview(
    val allowed: Boolean,
    val requiresConfirmation: Boolean = false,
    val message: String = ""
)

object TerminalCommandPolicy {
    private const val MAX_COMMAND_LENGTH = 8_192

    private val blockedPatterns =
        listOf(
            Regex("(^|[\\n;&|]\\s*)(su|reboot|shutdown|halt|setenforce)(\\s|$)", RegexOption.IGNORE_CASE),
            Regex("(^|[\\n;&|]\\s*)pm\\s+uninstall(\\s|$)", RegexOption.IGNORE_CASE),
            Regex("(^|[\\n;&|]\\s*)(mkfs(?:\\.[A-Za-z0-9_+-]+)?|mkswap|fdisk|parted)(\\s|$)", RegexOption.IGNORE_CASE),
            Regex("(^|[\\n;&|]\\s*)dd\\s+.*\\bof=/dev/", RegexOption.IGNORE_CASE)
        )

    private val confirmationPatterns =
        listOf(
            Regex("(^|[\\n;&|]\\s*)rm\\s+[^\\n]*(--recursive|-[^\\s]*r)", RegexOption.IGNORE_CASE),
            Regex("(^|[\\n;&|]\\s*)find\\s+[^\\n]*\\s-delete(\\s|$)", RegexOption.IGNORE_CASE),
            Regex("(^|[\\n;&|]\\s*)git\\s+reset\\s+--hard(\\s|$)", RegexOption.IGNORE_CASE),
            Regex("(^|[\\n;&|]\\s*)git\\s+clean\\s+-[^\\s]*f", RegexOption.IGNORE_CASE),
            Regex("(^|[\\n;&|]\\s*)truncate\\s+", RegexOption.IGNORE_CASE),
            Regex("(^|[\\n;&|]\\s*)chmod\\s+[^\\n]*-[^\\s]*R", RegexOption.IGNORE_CASE)
        )

    fun review(command: String): TerminalCommandReview {
        val normalized = command.trim()

        if (normalized.isBlank()) {
            return TerminalCommandReview(
                allowed = false,
                message = "Komut boş."
            )
        }

        if (normalized.length > MAX_COMMAND_LENGTH) {
            return TerminalCommandReview(
                allowed = false,
                message = "Komut çok uzun; en fazla $MAX_COMMAND_LENGTH karakter kullanılabilir."
            )
        }

        if ('\u0000' in normalized) {
            return TerminalCommandReview(
                allowed = false,
                message = "Komut geçersiz bir kontrol karakteri içeriyor."
            )
        }

        if (blockedPatterns.any { it.containsMatchIn(normalized) }) {
            return TerminalCommandReview(
                allowed = false,
                message = "Bu cihaz/yönetici komutu AppForge güvenlik alanında çalıştırılamaz."
            )
        }

        if (confirmationPatterns.any { it.containsMatchIn(normalized) }) {
            return TerminalCommandReview(
                allowed = true,
                requiresConfirmation = true,
                message = "Bu komut dosyaları kalıcı olarak değiştirebilir veya silebilir."
            )
        }

        return TerminalCommandReview(allowed = true)
    }
}
object ShellEscaper {
    fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}

object TerminalTextSanitizer {
    private val ansiCsi =
        Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")

    private val ansiOsc =
        Regex("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)")

    private val unsafeControl =
        Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001A\\u001C-\\u001F\\u007F]")

    fun clean(value: String): String =
        value
            .replace(ansiOsc, "")
            .replace(ansiCsi, "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(unsafeControl, "")
}
