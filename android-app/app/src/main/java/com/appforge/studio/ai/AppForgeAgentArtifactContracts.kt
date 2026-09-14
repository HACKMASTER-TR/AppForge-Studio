package com.appforge.studio.ai

internal data class AppForgeAgentArtifactReport(
    val kind: String,
    val fileSizeBytes: Long,
    val uncompressedBytes: Long,
    val entryCount: Int
)

internal data class AppForgeAgentSecurityFinding(
    val severity: String,
    val title: String,
    val detail: String
)

internal data class AppForgeAgentArtifactState(
    val busy: Boolean = false,
    val message: String = "",
    val buildId: String? = null,
    val logsLoaded: Boolean = false,
    val testLabAvailable: Boolean = false,
    val logs: List<String> = emptyList(),
    val apk: AppForgeAgentArtifactReport? = null,
    val aab: AppForgeAgentArtifactReport? = null,
    val security: List<AppForgeAgentSecurityFinding> = emptyList(),
    val lastDownloadId: Long? = null
)

internal object AppForgeAgentArtifactSafety {
    private val secretPatterns = listOf(
        Regex("ghp_[A-Za-z0-9]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b"),
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{12,}"),
        Regex(
            "(?i)(password|token|secret|api[_ -]?key)" +
                "\\s*[:=]\\s*[^\\s,;]{4,}"
        )
    )

    fun sanitize(
        value: String,
        maxChars: Int = 12_000
    ): String {
        var text = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        secretPatterns.forEach { pattern ->
            text = text.replace(pattern, "[REDACTED]")
        }

        return text.takeLast(maxChars.coerceIn(1, 64 * 1024))
    }

    fun safeKind(raw: String): String =
        when (raw.trim().lowercase()) {
            "apk" -> "apk"
            "aab" -> "aab"
            "exe" -> "exe"
            else -> error("Desteklenmeyen artifact türü.")
        }

    fun fileName(
        buildId: String,
        kind: String
    ): String {
        val safeKind = safeKind(kind)
        val id = buildId
            .filter { it.isLetterOrDigit() || it in "-_" }
            .take(48)
            .ifBlank { "build" }

        return "AppForge-$id.$safeKind"
    }

    fun sourceZipName(
        appName: String,
        platform: AppForgeAgentPlatform
    ): String {
        val safe = appName
            .map { ch ->
                if (ch.code < 128 && ch.isLetterOrDigit()) {
                    ch
                } else {
                    '-'
                }
            }
            .joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")
            .take(48)
            .ifBlank { "app" }

        return "AppForge-$safe-${platform.name.lowercase()}-source.zip"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "—"
        if (bytes < 1024L) return "$bytes B"

        val kib = bytes / 1024.0
        if (kib < 1024.0) {
            return String.format(java.util.Locale.US, "%.1f KiB", kib)
        }

        val mib = kib / 1024.0
        if (mib < 1024.0) {
            return String.format(java.util.Locale.US, "%.1f MiB", mib)
        }

        val gib = mib / 1024.0
        return String.format(java.util.Locale.US, "%.2f GiB", gib)
    }

    fun formatBytesSigned(bytes: Long): String {
        if (bytes == 0L) return "0 B"

        val prefix =
            if (bytes > 0L) "+" else "-"

        val absolute =
            if (bytes == Long.MIN_VALUE) {
                Long.MAX_VALUE
            } else {
                kotlin.math.abs(bytes)
            }

        return prefix + formatBytes(absolute)
    }
}
