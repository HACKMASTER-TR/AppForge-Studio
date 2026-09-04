package com.appforge.studio.terminal

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.ArrayDeque

internal data class EditorFileEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val sensitive: Boolean
)

internal data class EditorSearchMatch(
    val line: Int,
    val column: Int,
    val preview: String
)

internal enum class EditorDiffKind {
    SAME,
    ADDED,
    REMOVED
}

internal data class EditorDiffLine(
    val kind: EditorDiffKind,
    val text: String
)

internal data class EditorSaveResult(
    val file: File,
    val restorePoint: File?
)

internal data class LspLanguageProfile(
    val id: String,
    val title: String,
    val extensions: Set<String>,
    val serverCommand: String,
    val installCommand: String
)

internal object UltimateEditorWorkspace {
    private const val MAX_FILE_BYTES =
        2L * 1_024L * 1_024L

    private const val MAX_LISTED_FILES =
        500

    private const val MAX_DEPTH =
        6

    private val ignoredDirectoryNames =
        setOf(
            ".git",
            ".gradle",
            ".idea",
            ".appforge-editor-history",
            "node_modules",
            "build",
            "dist",
            "out",
            "target"
        )

    private val blockedExtensions =
        setOf(
            "apk",
            "aab",
            "jar",
            "class",
            "dex",
            "so",
            "png",
            "jpg",
            "jpeg",
            "gif",
            "webp",
            "pdf",
            "zip",
            "gz",
            "7z",
            "jks",
            "keystore",
            "p12",
            "pfx",
            "pem",
            "der"
        )

    private val textExtensions =
        setOf(
            "",
            "txt",
            "md",
            "markdown",
            "json",
            "jsonc",
            "yaml",
            "yml",
            "toml",
            "xml",
            "html",
            "htm",
            "css",
            "scss",
            "sass",
            "less",
            "js",
            "jsx",
            "mjs",
            "cjs",
            "ts",
            "tsx",
            "vue",
            "svelte",
            "py",
            "java",
            "kt",
            "kts",
            "gradle",
            "properties",
            "sh",
            "bash",
            "zsh",
            "fish",
            "php",
            "go",
            "rs",
            "c",
            "h",
            "cc",
            "cpp",
            "cxx",
            "hpp",
            "cmake",
            "sql",
            "graphql",
            "gql",
            "dart",
            "swift",
            "rb",
            "pl",
            "ini",
            "conf",
            "env",
            "gitignore",
            "dockerfile"
        )

    fun listFiles(root: File): List<EditorFileEntry> {
        val safeRoot = canonicalRoot(root)
        val result = mutableListOf<EditorFileEntry>()

        fun walk(directory: File, depth: Int) {
            if (
                depth > MAX_DEPTH ||
                result.size >= MAX_LISTED_FILES
            ) {
                return
            }

            directory
                .listFiles()
                .orEmpty()
                .sortedBy { it.name.lowercase() }
                .forEach { child ->
                    if (result.size >= MAX_LISTED_FILES) {
                        return
                    }

                    val safeChild =
                        runCatching {
                            child.canonicalFile
                        }.getOrNull()
                            ?: return@forEach

                    if (!isInside(safeRoot, safeChild)) {
                        return@forEach
                    }

                    if (safeChild.isDirectory) {
                        if (
                            safeChild.name !in
                            ignoredDirectoryNames
                        ) {
                            walk(
                                safeChild,
                                depth + 1
                            )
                        }
                    } else if (
                        safeChild.isFile &&
                        safeChild.length() <= MAX_FILE_BYTES &&
                        isSupportedTextFile(safeChild)
                    ) {
                        result +=
                            EditorFileEntry(
                                relativePath =
                                    safeRoot
                                        .toPath()
                                        .relativize(
                                            safeChild.toPath()
                                        )
                                        .toString(),
                                sizeBytes =
                                    safeChild.length(),
                                sensitive =
                                    isSensitive(
                                        safeChild.name
                                    )
                            )
                    }
                }
        }

        walk(safeRoot, 0)
        return result
    }

    fun readText(
        root: File,
        relativePath: String
    ): String {
        val file =
            resolveTextFile(
                root,
                relativePath,
                requireExisting = true
            )

        require(
            file.length() <= MAX_FILE_BYTES
        ) {
            "Dosya editör sınırını aşıyor."
        }

        return file.readText(
            Charsets.UTF_8
        )
    }

    fun saveText(
        root: File,
        relativePath: String,
        content: String
    ): EditorSaveResult {
        require(
            content.toByteArray(
                Charsets.UTF_8
            ).size <= MAX_FILE_BYTES
        ) {
            "İçerik editör sınırını aşıyor."
        }

        val file =
            resolveTextFile(
                root,
                relativePath,
                requireExisting = true
            )

        val previous =
            file.readText(
                Charsets.UTF_8
            )

        val restorePoint =
            if (isSensitive(file.name)) {
                null
            } else {
                createRestorePoint(
                    root = root,
                    relativePath = relativePath,
                    previous = previous
                )
            }

        val temporary =
            File.createTempFile(
                ".${file.name}.appforge-",
                ".tmp",
                file.parentFile
            )

        temporary.writeText(
            content,
            Charsets.UTF_8
        )

        try {
            runCatching {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }

        return EditorSaveResult(
            file = file,
            restorePoint = restorePoint
        )
    }

    fun isSensitive(path: String): Boolean {
        val name =
            path
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .lowercase()

        return name == ".env" ||
            name.startsWith(".env.") ||
            name.contains("secret") ||
            name.contains("credential") ||
            name.contains("private-key") ||
            name.endsWith(".pem") ||
            name.endsWith(".jks") ||
            name.endsWith(".keystore") ||
            name.endsWith(".p12") ||
            name.endsWith(".pfx")
    }

    private fun createRestorePoint(
        root: File,
        relativePath: String,
        previous: String
    ): File? {
        if (previous.isEmpty()) {
            return null
        }

        val safeRoot = canonicalRoot(root)
        val historyRoot =
            File(
                safeRoot,
                ".appforge-editor-history"
            ).apply {
                mkdirs()
            }.canonicalFile

        require(
            isInside(safeRoot, historyRoot)
        ) {
            "Geçmiş klasörü çalışma alanının dışında."
        }

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    relativePath.toByteArray(
                        Charsets.UTF_8
                    )
                )
                .joinToString("") {
                    "%02x".format(it)
                }
                .take(16)

        val file =
            File(
                historyRoot,
                "$digest-${System.currentTimeMillis()}.bak"
            )

        file.writeText(
            previous,
            Charsets.UTF_8
        )

        pruneRestorePoints(
            historyRoot,
            digest
        )

        return file
    }

    private fun pruneRestorePoints(
        historyRoot: File,
        digest: String
    ) {
        historyRoot
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.name.startsWith("$digest-")
            }
            .sortedByDescending {
                it.lastModified()
            }
            .drop(20)
            .forEach {
                it.delete()
            }
    }

    private fun resolveTextFile(
        root: File,
        relativePath: String,
        requireExisting: Boolean
    ): File {
        require(
            relativePath.isNotBlank() &&
                relativePath.length <= 2_048 &&
                '\u0000' !in relativePath
        ) {
            "Dosya yolu geçersiz."
        }

        val safeRoot = canonicalRoot(root)
        val candidate =
            File(
                safeRoot,
                relativePath
            ).canonicalFile

        require(
            isInside(safeRoot, candidate)
        ) {
            "Dosya çalışma alanının dışında."
        }

        if (requireExisting) {
            require(
                candidate.isFile
            ) {
                "Dosya bulunamadı."
            }
        }

        require(
            isSupportedTextFile(candidate)
        ) {
            "Bu dosya türü metin editöründe açılamaz."
        }

        return candidate
    }

    private fun canonicalRoot(root: File): File {
        val safeRoot = root.canonicalFile

        require(
            safeRoot.isDirectory
        ) {
            "Çalışma alanı bulunamadı."
        }

        return safeRoot
    }

    private fun isSupportedTextFile(file: File): Boolean {
        val name = file.name.lowercase()
        val extension =
            when {
                name == "dockerfile" ->
                    "dockerfile"

                name == ".gitignore" ->
                    "gitignore"

                name == ".env" ||
                    name.startsWith(".env.") ->
                    "env"

                else ->
                    file.extension.lowercase()
            }

        return extension !in blockedExtensions &&
            extension in textExtensions
    }

    private fun isInside(
        root: File,
        child: File
    ): Boolean =
        child.toPath().startsWith(
            root.toPath()
        )
}

internal object UltimateEditorSearch {
    fun find(
        text: String,
        query: String,
        caseSensitive: Boolean = false
    ): List<EditorSearchMatch> {
        if (query.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<EditorSearchMatch>()

        text.lineSequence()
            .forEachIndexed { lineIndex, line ->
                var start = 0

                while (start <= line.length) {
                    val index =
                        line.indexOf(
                            string = query,
                            startIndex = start,
                            ignoreCase =
                                !caseSensitive
                        )

                    if (index < 0) {
                        break
                    }

                    result +=
                        EditorSearchMatch(
                            line = lineIndex + 1,
                            column = index + 1,
                            preview =
                                line.take(220)
                        )

                    if (result.size >= 500) {
                        return result
                    }

                    start =
                        index +
                            maxOf(
                                query.length,
                                1
                            )
                }
            }

        return result
    }

    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        caseSensitive: Boolean = false
    ): String {
        if (query.isEmpty()) {
            return text
        }

        return if (caseSensitive) {
            text.replace(
                query,
                replacement
            )
        } else {
            Regex(
                Regex.escape(query),
                RegexOption.IGNORE_CASE
            ).replace(
                text,
                Regex.escapeReplacement(
                    replacement
                )
            )
        }
    }
}

internal object UltimateEditorDiff {
    private const val MAX_LINES =
        2_000

    fun compare(
        before: String,
        after: String
    ): List<EditorDiffLine> {
        val oldLines =
            before.lines()
                .take(MAX_LINES)

        val newLines =
            after.lines()
                .take(MAX_LINES)

        var prefix = 0
        while (
            prefix < oldLines.size &&
            prefix < newLines.size &&
            oldLines[prefix] == newLines[prefix]
        ) {
            prefix += 1
        }

        var suffix = 0
        while (
            suffix < oldLines.size - prefix &&
            suffix < newLines.size - prefix &&
            oldLines[oldLines.lastIndex - suffix] ==
            newLines[newLines.lastIndex - suffix]
        ) {
            suffix += 1
        }

        return buildList {
            oldLines
                .take(prefix)
                .forEach {
                    add(
                        EditorDiffLine(
                            EditorDiffKind.SAME,
                            it
                        )
                    )
                }

            oldLines
                .subList(
                    prefix,
                    oldLines.size - suffix
                )
                .forEach {
                    add(
                        EditorDiffLine(
                            EditorDiffKind.REMOVED,
                            it
                        )
                    )
                }

            newLines
                .subList(
                    prefix,
                    newLines.size - suffix
                )
                .forEach {
                    add(
                        EditorDiffLine(
                            EditorDiffKind.ADDED,
                            it
                        )
                    )
                }

            if (suffix > 0) {
                newLines
                    .takeLast(suffix)
                    .forEach {
                        add(
                            EditorDiffLine(
                                EditorDiffKind.SAME,
                                it
                            )
                        )
                    }
            }
        }
    }
}

internal class EditorUndoBuffer(
    initial: String,
    private val maxEntries: Int = 50
) {
    private val undo =
        ArrayDeque<String>()

    private val redo =
        ArrayDeque<String>()

    var current: String = initial
        private set

    fun edit(next: String): String {
        if (next == current) {
            return current
        }

        undo.addLast(current)
        while (undo.size > maxEntries) {
            undo.removeFirst()
        }

        redo.clear()
        current = next
        return current
    }

    fun undo(): String {
        val previous =
            undo.pollLast()
                ?: return current

        redo.addLast(current)
        current = previous
        return current
    }

    fun redo(): String {
        val next =
            redo.pollLast()
                ?: return current

        undo.addLast(current)
        current = next
        return current
    }

    fun reset(value: String) {
        undo.clear()
        redo.clear()
        current = value
    }

    fun canUndo(): Boolean =
        undo.isNotEmpty()

    fun canRedo(): Boolean =
        redo.isNotEmpty()
}

internal object UltimateLspCatalog {
    private val profiles =
        listOf(
            LspLanguageProfile(
                id = "typescript",
                title = "JavaScript / TypeScript",
                extensions =
                    setOf(
                        "js",
                        "jsx",
                        "mjs",
                        "cjs",
                        "ts",
                        "tsx"
                    ),
                serverCommand =
                    "typescript-language-server --stdio",
                installCommand =
                    "npm install -g typescript typescript-language-server"
            ),
            LspLanguageProfile(
                id = "python",
                title = "Python",
                extensions = setOf("py"),
                serverCommand =
                    "pyright-langserver --stdio",
                installCommand =
                    "npm install -g pyright"
            ),
            LspLanguageProfile(
                id = "rust",
                title = "Rust",
                extensions = setOf("rs"),
                serverCommand =
                    "rust-analyzer",
                installCommand =
                    "apt-get install -y rust-analyzer"
            ),
            LspLanguageProfile(
                id = "go",
                title = "Go",
                extensions = setOf("go"),
                serverCommand =
                    "gopls",
                installCommand =
                    "go install golang.org/x/tools/gopls@latest"
            ),
            LspLanguageProfile(
                id = "cpp",
                title = "C / C++",
                extensions =
                    setOf(
                        "c",
                        "h",
                        "cc",
                        "cpp",
                        "cxx",
                        "hpp"
                    ),
                serverCommand =
                    "clangd",
                installCommand =
                    "apt-get install -y clangd"
            ),
            LspLanguageProfile(
                id = "php",
                title = "PHP",
                extensions = setOf("php"),
                serverCommand =
                    "intelephense --stdio",
                installCommand =
                    "npm install -g intelephense"
            )
        )

    fun forPath(path: String): LspLanguageProfile? {
        val extension =
            path
                .substringAfterLast('.', "")
                .lowercase()

        return profiles.firstOrNull {
            extension in it.extensions
        }
    }

    fun isTrusted(
        profile: LspLanguageProfile
    ): Boolean =
        profiles.any {
            it == profile
        }
}
