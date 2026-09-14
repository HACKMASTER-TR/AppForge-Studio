package com.appforge.studio.ai

import java.io.File
import java.security.MessageDigest

internal data class AppForgeAgentPatchOperation(
    val path: String,
    val baseSha256: String?,
    val replacementContent: String
)

internal data class AppForgeAgentPatchPlan(
    val failureFingerprint: String,
    val operations: List<AppForgeAgentPatchOperation>
)

internal data class AppForgeAgentPatchApplyResult(
    val checkpoint: AppForgeWorkspaceCheckpoint,
    val writtenPaths: List<String>
)

internal object AppForgeAgentStructuredPatch {
    private const val MAX_OPERATIONS = 16
    private const val MAX_FILE_CHARS = 256 * 1024
    private const val MAX_TOTAL_CHARS = 1024 * 1024

    private val forbiddenLeafNames = setOf(
        ".env",
        "google-services.json",
        "id_rsa",
        "id_ed25519"
    )

    private val forbiddenExtensions = setOf(
        "jks",
        "keystore",
        "p12",
        "pfx",
        "pem",
        "key"
    )

    fun validate(plan: AppForgeAgentPatchPlan) {
        require(plan.failureFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "Patch failure fingerprint SHA-256 olmalı."
        }
        require(plan.operations.isNotEmpty()) {
            "Structured patch en az bir işlem içermeli."
        }
        require(plan.operations.size <= MAX_OPERATIONS) {
            "Structured patch en fazla $MAX_OPERATIONS dosya değiştirebilir."
        }

        val paths = mutableSetOf<String>()
        var totalChars = 0

        plan.operations.forEachIndexed { index, operation ->
            val path = validatePath(operation.path)
            require(paths.add(path.lowercase())) {
                "Patch yolu benzersiz olmalı: $path"
            }

            operation.baseSha256?.let { digest ->
                require(digest.matches(Regex("^[0-9a-f]{64}$"))) {
                    "operations[$index].baseSha256 geçerli SHA-256 olmalı."
                }
            }

            require(operation.replacementContent.length <= MAX_FILE_CHARS) {
                "Patch dosyası çok büyük: $path"
            }
            require('\u0000' !in operation.replacementContent) {
                "Binary/NUL içerik structured patch ile yazılamaz: $path"
            }

            totalChars += operation.replacementContent.length
            require(totalChars <= MAX_TOTAL_CHARS) {
                "Structured patch toplam içerik sınırını aşıyor."
            }
        }
    }

    fun apply(
        workspace: File,
        plan: AppForgeAgentPatchPlan
    ): AppForgeAgentPatchApplyResult {
        validate(plan)
        val root = workspace.canonicalFile
        require(root.isDirectory) { "Workspace klasörü bulunamadı." }

        val generated = plan.operations.map { operation ->
            val relative = validatePath(operation.path)
            val target = resolveInside(root, relative)
            val base = operation.baseSha256

            if (base == null) {
                require(!target.exists()) {
                    "Yeni dosya patch'i mevcut dosyanın üzerine yazamaz: $relative"
                }
            } else {
                require(target.isFile) {
                    "Patch base dosyası bulunamadı: $relative"
                }
                require(sha256(target.readBytes()) == base) {
                    "Patch base hash uyuşmazlığı; stale AI patch reddedildi: $relative"
                }
            }

            AppForgeGeneratedFile(
                path = relative,
                content = normalizeContent(operation.replacementContent)
            )
        }

        val digestInput = buildString {
            append(plan.failureFingerprint)
            append('\n')
            generated.sortedBy { it.path }.forEach { file ->
                append(file.path)
                append('\n')
                append(sha256(file.content.toByteArray(Charsets.UTF_8)))
                append('\n')
            }
        }

        val transactionProject = AppForgeGeneratedProject(
            platform = AppForgeAgentPlatform.ANDROID,
            entryPoint = generated.first().path,
            files = generated,
            digestSha256 = sha256(digestInput.toByteArray(Charsets.UTF_8))
        )

        val result = AppForgeAgentWorkspaceTransaction.apply(
            workspace = root,
            project = transactionProject
        )

        return AppForgeAgentPatchApplyResult(
            checkpoint = result.checkpoint,
            writtenPaths = result.writtenPaths
        )
    }

    private fun validatePath(raw: String): String {
        val path = raw.trim().replace('\\', '/')
        require(path.isNotBlank()) { "Patch yolu boş olamaz." }
        require(!path.startsWith('/')) { "Mutlak patch yolu yasak: $path" }
        require(!Regex("^[A-Za-z]:/").containsMatchIn(path)) {
            "Windows mutlak patch yolu yasak: $path"
        }

        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) {
            "Patch path traversal engellendi: $path"
        }
        require(parts.first().lowercase() !in setOf(".git", ".appforge-agent-v4")) {
            "Korunan klasöre patch yasak: $path"
        }

        val leaf = parts.last().lowercase()
        require(leaf !in forbiddenLeafNames) { "Hassas dosyaya patch yasak: $path" }
        val extension = leaf.substringAfterLast('.', missingDelimiterValue = "")
        require(extension !in forbiddenExtensions) { "Hassas dosya uzantısına patch yasak: $path" }

        return parts.joinToString("/")
    }

    private fun resolveInside(root: File, relative: String): File {
        val candidate = File(root, relative).canonicalFile
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        require(candidate.path.startsWith(prefix)) {
            "Workspace dışına patch girişimi engellendi: $relative"
        }
        return candidate
    }

    private fun normalizeContent(value: String): String =
        value
            .replace("\\r\\n", "\\n")
            .replace('\r', '\n')
            .let { if (it.endsWith('\n')) it else "$it\n" }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
