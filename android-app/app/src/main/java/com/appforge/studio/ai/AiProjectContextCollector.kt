package com.appforge.studio.ai

import com.appforge.studio.model.ProjectDraft
import java.io.File

internal data class AiProjectContextFile(
    val path: String,
    val content: String,
    val truncated: Boolean
)

internal data class AiProjectContextSnapshot(
    val projectId: String,
    val projectName: String,
    val packageName: String,
    val sourceTechnology: String,
    val sourceBuildEngine: String,
    val workspaceName: String,
    val tree: String,
    val files: List<AiProjectContextFile>,
    val scannedFileCount: Int,
    val truncated: Boolean
)

internal object AiProjectContextCollector {
    private const val MAX_DEPTH = 6
    private const val MAX_TREE_ENTRIES = 160
    private const val MAX_TREE_CHARS = 8_000
    private const val MAX_CONTEXT_FILES = 6
    private const val MAX_FILE_BYTES = 64L * 1024L
    private const val MAX_FILE_CHARS = 5_000
    private const val MAX_TOTAL_FILE_CHARS = 30_000

    private val ignoredDirectories = setOf(
        ".git", ".gradle", ".idea", ".next", ".appforge",
        "node_modules", "build", "dist", "out", "coverage", "target"
    )

    private val secretNames = setOf(
        ".env", "id_rsa", "credentials.json", "credential.json",
        "secrets.json", "secret.json", "local.properties"
    )

    private val secretExtensions = setOf(
        "pem", "key", "p12", "pfx", "jks", "keystore"
    )

    private val contextFileNames = setOf(
        "package.json", "settings.gradle", "settings.gradle.kts",
        "build.gradle", "build.gradle.kts", "androidmanifest.xml",
        "vite.config.js", "vite.config.mjs", "vite.config.ts",
        "tsconfig.json", "jsconfig.json", "cmakelists.txt",
        "app.json", "app.config.js", "app.config.ts"
    )

    private val sourceFileExtensions = setOf(
        "html", "htm",
        "css", "scss", "sass", "less",
        "js", "mjs", "cjs", "jsx",
        "ts", "tsx",
        "kt", "java",
        "py",
        "c", "cc", "cpp", "h", "hpp",
        "cs", "dart",
        "vue", "svelte",
        "xml"
    )

    private val secretLinePattern = Regex(
        """(api[_-]?key|secret|token|password|storepassword|keypassword|authorization)["']?\s*[:=]""",
        RegexOption.IGNORE_CASE
    )

    private val privateKeyPattern = Regex(
        "-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----",
        RegexOption.IGNORE_CASE
    )

    fun collect(
        projectId: String?,
        draft: ProjectDraft
    ): AiProjectContextSnapshot {
        val root = draft.importedFolder
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?.let { runCatching { it.canonicalFile }.getOrNull() }

        if (root == null) {
            return AiProjectContextSnapshot(
                projectId = projectId.orEmpty().take(160),
                projectName = draft.appName.take(200),
                packageName = draft.packageName.take(240),
                sourceTechnology = draft.sourceTechnologyLabel.take(120),
                sourceBuildEngine = draft.sourceBuildEngine.take(120),
                workspaceName = "",
                tree = "",
                files = emptyList(),
                scannedFileCount = 0,
                truncated = false
            )
        }

        val treeEntries = mutableListOf<String>()
        val candidates = mutableListOf<File>()
        var scannedFileCount = 0
        var truncated = false

        fun visit(directory: File, depth: Int) {
            if (depth > MAX_DEPTH || treeEntries.size >= MAX_TREE_ENTRIES) {
                truncated = true
                return
            }

            val children = directory.listFiles()
                ?.sortedWith(
                    compareBy<File> { !it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
                .orEmpty()

            for (child in children) {
                if (treeEntries.size >= MAX_TREE_ENTRIES) {
                    truncated = true
                    break
                }

                val safe = runCatching { child.canonicalFile }.getOrNull()
                    ?: continue

                if (!inside(root, safe)) continue
                if (safe.isDirectory && safe.name.lowercase() in ignoredDirectories) continue

                val relative = runCatching {
                    safe.relativeTo(root).invariantSeparatorsPath
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue

                if (isSecretPath(relative, safe)) continue

                treeEntries += if (safe.isDirectory) "$relative/" else relative

                if (safe.isDirectory) {
                    visit(safe, depth + 1)
                } else {
                    scannedFileCount += 1
                    val normalizedName = safe.name.lowercase()
                    val extension = safe.extension.lowercase()

                    if (
                        safe.length() in 1..MAX_FILE_BYTES &&
                        isContextCandidate(
                            name = normalizedName,
                            extension = extension
                        )
                    ) {
                        candidates += safe
                    }
                }
            }
        }

        visit(root, 0)

        var remainingChars = MAX_TOTAL_FILE_CHARS
        val files = candidates
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedWith(
                compareBy<File> { contextPriority(root, it) }
                    .thenBy { it.absolutePath.length }
                    .thenBy { it.name.lowercase() }
            )
            .take(MAX_CONTEXT_FILES)
            .mapNotNull { file ->
                if (remainingChars <= 0) {
                    truncated = true
                    return@mapNotNull null
                }

                val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                    ?: return@mapNotNull null

                val redacted = redact(raw)
                val allowed = minOf(MAX_FILE_CHARS, remainingChars)
                val content = redacted.take(allowed)
                val wasTruncated = redacted.length > content.length
                if (wasTruncated) truncated = true
                remainingChars -= content.length

                AiProjectContextFile(
                    path = file.relativeTo(root).invariantSeparatorsPath,
                    content = content,
                    truncated = wasTruncated
                )
            }

        val rawTree = treeEntries.joinToString("\n")
        val tree = rawTree.take(MAX_TREE_CHARS)
        if (tree.length < rawTree.length) truncated = true

        return AiProjectContextSnapshot(
            projectId = projectId.orEmpty().take(160),
            projectName = draft.appName.take(200),
            packageName = draft.packageName.take(240),
            sourceTechnology = draft.sourceTechnologyLabel.take(120),
            sourceBuildEngine = draft.sourceBuildEngine.take(120),
            workspaceName = root.name.take(160),
            tree = tree,
            files = files,
            scannedFileCount = scannedFileCount,
            truncated = truncated
        )
    }

    private fun inside(root: File, candidate: File): Boolean {
        val rootPath = root.canonicalPath
        val candidatePath = candidate.canonicalPath
        return candidatePath == rootPath ||
            candidatePath.startsWith(rootPath + File.separator)
    }

    private fun isSecretPath(relative: String, file: File): Boolean {
        val normalized = relative.replace('\\', '/').lowercase()
        val name = file.name.lowercase()
        return name in secretNames ||
            name.startsWith(".env.") ||
            file.extension.lowercase() in secretExtensions ||
            normalized.split('/').any { it == ".secrets" || it == "secrets" }
    }

    private fun isContextCandidate(
        name: String,
        extension: String
    ): Boolean =
        name in contextFileNames ||
            extension in sourceFileExtensions

    private fun contextPriority(
        root: File,
        file: File
    ): Int {
        val name = file.name.lowercase()
        val relative = runCatching {
            file.relativeTo(root)
                .invariantSeparatorsPath
                .lowercase()
        }.getOrDefault(name)

        return when {
            name == "index.html" ||
                name == "index.htm" -> 0

            name == "package.json" -> 1

            name.startsWith("main.") ||
                name.startsWith("app.") ||
                name.startsWith("index.") -> 2

            name == "settings.gradle.kts" ||
                name == "settings.gradle" -> 3

            name == "build.gradle.kts" ||
                name == "build.gradle" -> 4

            name == "androidmanifest.xml" -> 5

            name == "vite.config.ts" ||
                name == "vite.config.js" ||
                name == "vite.config.mjs" -> 6

            name == "app.json" ||
                name == "app.config.ts" ||
                name == "app.config.js" -> 7

            name == "tsconfig.json" ||
                name == "jsconfig.json" -> 8

            name == "cmakelists.txt" -> 9

            relative.startsWith("src/") -> 20

            else -> 40
        }
    }

    private fun redact(value: String): String {
        if (privateKeyPattern.containsMatchIn(value)) {
            return "[REDACTED_SECRET_FILE]"
        }

        return value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .joinToString("\n") { line ->
                if (secretLinePattern.containsMatchIn(line)) {
                    "[REDACTED_SECRET-LIKE LINE]"
                } else {
                    line
                }
            }
    }
}
