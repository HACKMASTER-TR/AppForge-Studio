package com.appforge.studio.terminal

import android.content.Context
import com.appforge.studio.model.ProjectDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object TerminalWorkspaceResolver {
    fun resolve(
        context: Context,
        projectId: String?,
        draft: ProjectDraft?
    ): File {
        val imported =
            draft
                ?.importedFolder
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let(::File)
                ?.takeIf {
                    it.isDirectory &&
                        it.canRead() &&
                        it.canWrite()
                }

        if (imported != null) {
            return imported.canonicalFile
        }

        val safeId =
            projectId
                ?.replace(
                    Regex("[^A-Za-z0-9._-]"),
                    "_"
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "scratch"

        return File(
            context.filesDir,
            "terminal/workspaces/$safeId"
        ).apply {
            mkdirs()
        }.canonicalFile
    }
}
data class WorkspaceEntry(
    val file: File,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long
)

object WorkspaceFileService {
    private const val MAX_EDITOR_BYTES =
        2L * 1_024L * 1_024L

    private val textExtensions =
        setOf(
            "txt",
            "md",
            "json",
            "xml",
            "html",
            "htm",
            "css",
            "js",
            "mjs",
            "cjs",
            "ts",
            "tsx",
            "jsx",
            "kt",
            "kts",
            "java",
            "py",
            "php",
            "rb",
            "go",
            "rs",
            "c",
            "h",
            "cpp",
            "hpp",
            "cs",
            "dart",
            "gradle",
            "properties",
            "yaml",
            "yml",
            "toml",
            "ini",
            "cfg",
            "conf",
            "sh",
            "sql",
            "env",
            "gitignore"
        )

    suspend fun list(
        root: File,
        directory: File
    ): List<WorkspaceEntry> =
        withContext(Dispatchers.IO) {
            val safeRoot =
                root.canonicalFile

            val safeDirectory =
                requireInside(
                    safeRoot,
                    directory
                )

            safeDirectory
                .listFiles()
                .orEmpty()
                .filterNot {
                    it.name ==
                        ".appforge-trash"
                }
                .sortedWith(
                    compareBy<File> {
                        !it.isDirectory
                    }.thenBy {
                        it.name.lowercase()
                    }
                )
                .map { file ->
                    WorkspaceEntry(
                        file = file,
                        relativePath =
                            file
                                .relativeTo(safeRoot)
                                .invariantSeparatorsPath,
                        isDirectory =
                            file.isDirectory,
                        sizeBytes =
                            if (file.isFile) {
                                file.length()
                            } else {
                                0L
                            },
                        modifiedAt =
                            file.lastModified()
                    )
                }
        }

    suspend fun readText(
        root: File,
        file: File
    ): String =
        withContext(Dispatchers.IO) {
            val safeFile =
                requireInside(
                    root.canonicalFile,
                    file
                )

            require(safeFile.isFile) {
                "Dosya bulunamadı."
            }

            require(safeFile.length() <= MAX_EDITOR_BYTES) {
                "Dosya düzenleyici için çok büyük (en fazla 2 MB)."
            }

            require(isTextFile(safeFile)) {
                "Bu dosya metin olarak düzenlenemez."
            }

            safeFile.readText(Charsets.UTF_8)
        }

    suspend fun writeText(
        root: File,
        file: File,
        content: String
    ) =
        withContext(Dispatchers.IO) {
            require(
                content.toByteArray(
                    Charsets.UTF_8
                ).size <=
                    MAX_EDITOR_BYTES
            ) {
                "Dosya düzenleyici için çok büyük (en fazla 2 MB)."
            }

            val safeFile =
                requireInside(
                    root.canonicalFile,
                    file
                )

            safeFile.parentFile
                ?.mkdirs()

            safeFile.writeText(
                content,
                Charsets.UTF_8
            )
        }

    suspend fun create(
        root: File,
        parent: File,
        name: String,
        directory: Boolean
    ): File =
        withContext(Dispatchers.IO) {
            val safeName =
                validateName(name)

            val safeParent =
                requireInside(
                    root.canonicalFile,
                    parent
                )

            val target =
                requireInside(
                    root.canonicalFile,
                    File(
                        safeParent,
                        safeName
                    )
                )

            require(!target.exists()) {
                "Aynı adda bir dosya veya klasör zaten var."
            }

            val created =
                if (directory) {
                    target.mkdirs()
                } else {
                    target.parentFile
                        ?.mkdirs()

                    target.createNewFile()
                }

            require(created) {
                "Dosya veya klasör oluşturulamadı."
            }

            target
        }

    suspend fun moveToTrash(
        root: File,
        target: File
    ): File =
        withContext(Dispatchers.IO) {
            val safeRoot =
                root.canonicalFile

            val safeTarget =
                requireInside(
                    safeRoot,
                    target
                )

            require(safeTarget != safeRoot) {
                "Çalışma alanının tamamı silinemez."
            }

            val trash =
                File(
                    safeRoot,
                    ".appforge-trash"
                ).apply {
                    mkdirs()
                }

            val destination =
                File(
                    trash,
                    "${System.currentTimeMillis()}_${safeTarget.name}"
                )

            require(
                safeTarget.renameTo(destination)
            ) {
                "Öğe geri dönüşüm alanına taşınamadı."
            }

            destination
        }

    fun isTextFile(file: File): Boolean {
        if (!file.isFile) {
            return false
        }

        if (file.length() > MAX_EDITOR_BYTES) {
            return false
        }

        val extension =
            file.extension
                .lowercase()

        if (extension in textExtensions) {
            return true
        }

        return file.name in
            setOf(
                "Dockerfile",
                "Makefile",
                "LICENSE",
                "README"
            )
    }

    private fun validateName(value: String): String {
        val clean =
            value.trim()

        require(clean.isNotBlank()) {
            "Ad boş olamaz."
        }

        require(
            clean != "." &&
                clean != ".." &&
                '/' !in clean &&
                '\\' !in clean &&
                '\u0000' !in clean
        ) {
            "Geçersiz dosya veya klasör adı."
        }

        return clean
    }

    private fun requireInside(
        root: File,
        candidate: File
    ): File {
        val safeRoot =
            root.canonicalFile

        val safeCandidate =
            candidate.canonicalFile

        require(
            safeCandidate == safeRoot ||
                safeCandidate.path.startsWith(
                    safeRoot.path +
                        File.separator
                )
        ) {
            "Çalışma alanı dışındaki dosyalara erişilemez."
        }

        return safeCandidate
    }
}
