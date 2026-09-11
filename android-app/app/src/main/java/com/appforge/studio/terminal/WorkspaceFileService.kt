package com.appforge.studio.terminal

import android.content.Context
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.security.OwnerAccessPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

object TerminalWorkspaceResolver {
    fun resolve(
        context: Context,
        projectId: String?,
        draft: ProjectDraft?,
        accountEmail: String
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

        val accountScope =
            accountScope(
                accountEmail
            )

        val root =
            File(
                context.filesDir,
                "terminal/workspaces/accounts/$accountScope"
            ).apply {
                mkdirs()
            }.canonicalFile

        val target =
            File(
                root,
                safeId
            )

        /*
         * Eski sürümlerde workspace hesaplar arasında ortaktı:
         *
         * terminal/workspaces/scratch
         *
         * Güvenlik nedeniyle bu legacy alanı SADECE owner hesabına
         * aktar. Normal hesaplara hiçbir eski dosya kopyalanmaz.
         */
        if (
            !target.exists() &&
            OwnerAccessPolicy.isActiveOwner(
                context,
                accountEmail
            )
        ) {
            migrateLegacyWorkspaceForOwner(
                context = context,
                safeId = safeId,
                target = target
            )
        }

        return target
            .apply {
                mkdirs()
            }
            .canonicalFile
    }


    private fun accountScope(
        accountEmail: String
    ): String {
        val normalized =
            accountEmail
                .trim()
                .lowercase()

        require(
            normalized.isNotBlank()
        ) {
            "Aktif hesap bulunamadı."
        }

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )
                .digest(
                    normalized
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )

        return digest
            .take(16)
            .joinToString("") {
                "%02x".format(
                    it.toInt() and 0xff
                )
            }
    }


    private fun migrateLegacyWorkspaceForOwner(
        context: Context,
        safeId: String,
        target: File
    ) {
        val legacy =
            File(
                context.filesDir,
                "terminal/workspaces/$safeId"
            )

        if (
            !legacy.isDirectory
        ) {
            return
        }

        val canonicalLegacy =
            runCatching {
                legacy.canonicalFile
            }.getOrNull()
                ?: return

        val canonicalTarget =
            runCatching {
                target.canonicalFile
            }.getOrNull()
                ?: return

        canonicalTarget
            .parentFile
            ?.mkdirs()

        /*
         * Önce rename: aynı filesystem'de atomik.
         */
        if (
            canonicalLegacy.renameTo(
                canonicalTarget
            )
        ) {
            return
        }

        /*
         * Rename olmazsa copy + doğrulama + delete.
         */
        runCatching {
            canonicalLegacy.copyRecursively(
                canonicalTarget,
                overwrite = false
            )

            if (
                canonicalTarget.exists()
            ) {
                canonicalLegacy
                    .deleteRecursively()
            }
        }
    }
}

data class WorkspaceEntry(
    val file: File,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long
)


data class WorkspacePage(
    val entries: List<WorkspaceEntry>,
    val totalCount: Int,
    val pageIndex: Int,
    val pageSize: Int,
    val pageCount: Int
) {
    val hasPrevious: Boolean
        get() =
            pageIndex > 0

    val hasNext: Boolean
        get() =
            pageIndex + 1 <
                pageCount
}


object WorkspaceFileService {
    const val DEFAULT_PAGE_SIZE =
        200

    private const val CANCELLATION_CHECK_INTERVAL =
        64

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

    /*
     * Legacy full-list API is retained for compatibility with any
     * existing callers. The Files UI uses listPage() below.
     */
    suspend fun list(
        root: File,
        directory: File
    ): List<WorkspaceEntry> =
        withContext(
            Dispatchers.IO
        ) {
            val safeRoot =
                root.canonicalFile

            val safeDirectory =
                requireInside(
                    safeRoot,
                    directory
                )

            sortedChildren(
                safeDirectory
            )
                .map { file ->
                    workspaceEntry(
                        safeRoot,
                        file
                    )
                }
        }


    /*
     * Large folders publish only one page of WorkspaceEntry objects
     * into Compose state.
     *
     * Directory ordering remains exactly the same:
     * directories first, then case-insensitive name order.
     */
    suspend fun listPage(
        root: File,
        directory: File,
        pageIndex: Int,
        pageSize: Int =
            DEFAULT_PAGE_SIZE
    ): WorkspacePage =
        withContext(
            Dispatchers.IO
        ) {
            val safeRoot =
                root.canonicalFile

            val safeDirectory =
                requireInside(
                    safeRoot,
                    directory
                )

            val children =
                sortedChildren(
                    safeDirectory
                )

            val window =
                WorkspacePagination
                    .window(
                        totalCount =
                            children.size,
                        requestedPage =
                            pageIndex,
                        requestedPageSize =
                            pageSize
                    )

            val pageEntries =
                if (
                    window.fromIndex >=
                        window.toIndex
                ) {
                    emptyList()
                } else {
                    children
                        .subList(
                            window.fromIndex,
                            window.toIndex
                        )
                        .map { file ->
                            workspaceEntry(
                                safeRoot,
                                file
                            )
                        }
                }

            WorkspacePage(
                entries =
                    pageEntries,
                totalCount =
                    children.size,
                pageIndex =
                    window.pageIndex,
                pageSize =
                    window.pageSize,
                pageCount =
                    window.pageCount
            )
        }


    private suspend fun sortedChildren(
        directory: File
    ): List<File> {
        WorkspaceDirectoryIndexes
            .get(
                directory
            )
            ?.let {
                return it
            }

        val children =
            scanDirectoryChildren(
                directory
            )

        WorkspaceDirectoryIndexes
            .put(
                directory,
                children
            )

        return children
    }


    /*
     * DirectoryStream avoids allocating the complete listFiles()
     * array before AppForge has a chance to observe coroutine
     * cancellation.
     */
    private suspend fun scanDirectoryChildren(
        directory: File
    ): List<File> {
        val children =
            ArrayList<File>()

        Files
            .newDirectoryStream(
                directory.toPath()
            )
            .use { stream ->
                var scanned =
                    0

                for (
                    path in stream
                ) {
                    /*
                     * Cancellation check is batched to keep the hot
                     * loop cheap while still allowing abandoned
                     * directory loads to stop quickly.
                     */
                    if (
                        scanned %
                            CANCELLATION_CHECK_INTERVAL ==
                            0
                    ) {
                        currentCoroutineContext()
                            .ensureActive()
                    }

                    scanned +=
                        1

                    val file =
                        path.toFile()

                    if (
                        file.name !=
                            ".appforge-trash"
                    ) {
                        children.add(
                            file
                        )
                    }
                }
            }

        currentCoroutineContext()
            .ensureActive()

        children.sortWith(
            compareBy<File> {
                !it.isDirectory
            }.thenBy {
                it.name.lowercase()
            }
        )

        currentCoroutineContext()
            .ensureActive()

        return children
    }


    fun invalidateDirectory(
        directory: File
    ) {
        WorkspaceDirectoryIndexes
            .invalidate(
                directory
            )
    }


    private fun workspaceEntry(
        root: File,
        file: File
    ): WorkspaceEntry =
        WorkspaceEntry(
            file =
                file,
            relativePath =
                file
                    .relativeTo(root)
                    .invariantSeparatorsPath,
            isDirectory =
                file.isDirectory,
            sizeBytes =
                if (
                    file.isFile
                ) {
                    file.length()
                } else {
                    0L
                },
            modifiedAt =
                file.lastModified()
        )


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

            WorkspaceDirectoryIndexes
                .invalidate(
                    safeParent
                )

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

            val originalParent =
                safeTarget.parentFile

            require(
                safeTarget.renameTo(destination)
            ) {
                "Öğe geri dönüşüm alanına taşınamadı."
            }

            originalParent
                ?.let {
                    WorkspaceDirectoryIndexes
                        .invalidate(
                            it
                        )
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
