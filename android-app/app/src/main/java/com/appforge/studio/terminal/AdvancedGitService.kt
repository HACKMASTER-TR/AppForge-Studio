package com.appforge.studio.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File

internal data class AdvancedGitSnapshot(
    val branch: String,
    val branches: List<String>,
    val staged: List<String>,
    val unstaged: List<String>,
    val conflicts: List<String>,
    val originUrl: String?
)

internal enum class GitConflictChoice {
    OURS,
    THEIRS,
    MARK_RESOLVED
}

internal object AdvancedGitService {
    private const val MAX_DIFF_BYTES =
        512 * 1024

    suspend fun inspect(
        workspace: File
    ): AdvancedGitSnapshot =
        withRepository(workspace) { git, repository ->
            val status =
                git.status()
                    .call()

            AdvancedGitSnapshot(
                branch =
                    runCatching {
                        repository.branch
                    }.getOrDefault("HEAD"),
                branches =
                    git.branchList()
                        .call()
                        .map {
                            Repository.shortenRefName(
                                it.name
                            )
                        }
                        .distinct()
                        .sorted(),
                staged =
                    (
                        status.added +
                            status.changed +
                            status.removed
                        )
                        .toList()
                        .distinct()
                        .sorted(),
                unstaged =
                    (
                        status.modified +
                            status.missing +
                            status.untracked
                        )
                        .toList()
                        .distinct()
                        .sorted(),
                conflicts =
                    status.conflicting
                        .toList()
                        .distinct()
                        .sorted(),
                originUrl =
                    repository.config
                        .getString(
                            "remote",
                            "origin",
                            "url"
                        )
                        ?.takeIf {
                            it.isNotBlank()
                        }
            )
        }

    suspend fun stagePath(
        workspace: File,
        path: String
    ): AdvancedGitSnapshot =
        withRepository(workspace) { git, _ ->
            val safePath =
                requireRelativePath(
                    workspace,
                    path
                )

            git.add()
                .addFilepattern(safePath)
                .call()

            git.add()
                .setUpdate(true)
                .addFilepattern(safePath)
                .call()

            inspectBlocking(
                git,
                workspace
            )
        }

    suspend fun unstagePath(
        workspace: File,
        path: String
    ): AdvancedGitSnapshot =
        withRepository(workspace) { git, _ ->
            val safePath =
                requireRelativePath(
                    workspace,
                    path
                )

            git.reset()
                .addPath(safePath)
                .call()

            inspectBlocking(
                git,
                workspace
            )
        }

    suspend fun createBranch(
        workspace: File,
        branchName: String
    ): AdvancedGitSnapshot =
        withRepository(workspace) { git, _ ->
            val safeName =
                requireBranchName(
                    branchName
                )

            git.branchCreate()
                .setName(safeName)
                .call()

            inspectBlocking(
                git,
                workspace
            )
        }

    suspend fun checkoutBranch(
        workspace: File,
        branchName: String
    ): AdvancedGitSnapshot =
        withRepository(workspace) { git, _ ->
            val safeName =
                requireBranchName(
                    branchName
                )

            git.checkout()
                .setName(safeName)
                .call()

            inspectBlocking(
                git,
                workspace
            )
        }

    suspend fun resolveConflict(
        workspace: File,
        path: String,
        choice: GitConflictChoice
    ): AdvancedGitSnapshot =
        withRepository(workspace) { git, _ ->
            val safePath =
                requireRelativePath(
                    workspace,
                    path
                )

            when (choice) {
                GitConflictChoice.OURS -> {
                    git.checkout()
                        .setStage(
                            CheckoutCommand.Stage.OURS
                        )
                        .addPath(safePath)
                        .call()

                    git.add()
                        .addFilepattern(safePath)
                        .call()
                }

                GitConflictChoice.THEIRS -> {
                    git.checkout()
                        .setStage(
                            CheckoutCommand.Stage.THEIRS
                        )
                        .addPath(safePath)
                        .call()

                    git.add()
                        .addFilepattern(safePath)
                        .call()
                }

                GitConflictChoice.MARK_RESOLVED -> {
                    git.add()
                        .addFilepattern(safePath)
                        .call()
                }
            }

            inspectBlocking(
                git,
                workspace
            )
        }

    suspend fun diff(
        workspace: File,
        staged: Boolean,
        path: String? = null
    ): String =
        withRepository(workspace) { git, repository ->
            val command =
                git.diff()
                    .setCached(staged)

            val safePath =
                path
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        requireRelativePath(
                            workspace,
                            it
                        )
                    }

            if (safePath != null) {
                command.setPathFilter(
                    PathFilter.create(
                        safePath
                    )
                )
            }

            val entries =
                command.call()
                    .take(100)

            if (entries.isEmpty()) {
                return@withRepository "Diff boş."
            }

            val output =
                LimitedDiffOutputStream(
                    MAX_DIFF_BYTES
                )

            DiffFormatter(output)
                .use { formatter ->
                    formatter.setRepository(
                        repository
                    )
                    formatter.setDiffComparator(
                        RawTextComparator.DEFAULT
                    )
                    formatter.isDetectRenames =
                        true

                    entries.forEach {
                        formatter.format(it)
                    }
                }

            val text =
                output.toString(
                    Charsets.UTF_8.name()
                )

            if (output.truncated) {
                "$text\n\n[Diff ${MAX_DIFF_BYTES / 1024} KiB sınırında kesildi.]"
            } else {
                text
            }
        }

    internal fun repositorySlug(
        originUrl: String
    ): String =
        GitHubDevOpsClient.repositorySlug(
            originUrl
        )

    private fun inspectBlocking(
        git: Git,
        workspace: File
    ): AdvancedGitSnapshot {
        val repository =
            git.repository

        val status =
            git.status()
                .call()

        return AdvancedGitSnapshot(
            branch =
                runCatching {
                    repository.branch
                }.getOrDefault("HEAD"),
            branches =
                git.branchList()
                    .call()
                    .map {
                        Repository.shortenRefName(
                            it.name
                        )
                    }
                    .distinct()
                    .sorted(),
            staged =
                (
                    status.added +
                        status.changed +
                        status.removed
                    )
                    .toList()
                    .distinct()
                    .sorted(),
            unstaged =
                (
                    status.modified +
                        status.missing +
                        status.untracked
                    )
                    .toList()
                    .distinct()
                    .sorted(),
            conflicts =
                status.conflicting
                    .toList()
                    .distinct()
                    .sorted(),
            originUrl =
                repository.config
                    .getString(
                        "remote",
                        "origin",
                        "url"
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    }
        )
    }

    private suspend fun <T> withRepository(
        workspace: File,
        block: (Git, Repository) -> T
    ): T =
        withContext(Dispatchers.IO) {
            val safeWorkspace =
                requireWorkspace(
                    workspace
                )

            val repository =
                FileRepositoryBuilder()
                    .findGitDir(
                        safeWorkspace
                    )
                    .readEnvironment()
                    .build()

            repository.use { safeRepository ->
                require(
                    safeRepository.directory != null &&
                        safeRepository.directory.isDirectory
                ) {
                    "Bu çalışma alanında Git deposu yok."
                }

                val repositoryRoot =
                    runCatching {
                        safeRepository
                            .workTree
                            .canonicalFile
                    }.getOrNull()

                require(
                    repositoryRoot ==
                        safeWorkspace
                ) {
                    "Git deposu seçilen proje kökünde değil."
                }

                Git(safeRepository).use { git ->
                    block(
                        git,
                        safeRepository
                    )
                }
            }
        }

    private fun requireWorkspace(
        workspace: File
    ): File {
        val safe =
            workspace.canonicalFile

        require(
            safe.isDirectory &&
                safe.canRead() &&
                safe.canWrite()
        ) {
            "Proje çalışma alanına erişilemiyor."
        }

        return safe
    }

    private fun requireRelativePath(
        workspace: File,
        value: String
    ): String {
        val clean =
            value.trim()
                .replace(
                    '\\',
                    '/'
                )

        require(
            clean.isNotBlank() &&
                clean.length <= 2_048 &&
                clean.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                } &&
                !clean.startsWith("/") &&
                !clean.startsWith("../") &&
                !clean.contains("/../")
        ) {
            "Geçersiz Git dosya yolu."
        }

        val root =
            workspace.canonicalFile

        val target =
            File(
                root,
                clean
            ).canonicalFile

        require(
            target == root ||
                target.path.startsWith(
                    root.path +
                        File.separator
                )
        ) {
            "Git dosya yolu çalışma alanı dışında."
        }

        return clean
    }

    private fun requireBranchName(
        value: String
    ): String {
        val clean =
            value.trim()

        require(
            clean.length in 1..120 &&
                clean.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                } &&
                Repository.isValidRefName(
                    "refs/heads/$clean"
                )
        ) {
            "Geçersiz Git dal adı."
        }

        return clean
    }

    private class LimitedDiffOutputStream(
        private val maxBytes: Int
    ) : ByteArrayOutputStream() {
        var truncated: Boolean =
            false
            private set

        override fun write(
            b: Int
        ) {
            if (count >= maxBytes) {
                truncated =
                    true
                return
            }

            super.write(b)
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int
        ) {
            val remaining =
                maxBytes - count

            if (remaining <= 0) {
                truncated =
                    true
                return
            }

            val allowed =
                len.coerceAtMost(
                    remaining
                )

            super.write(
                b,
                off,
                allowed
            )

            if (allowed < len) {
                truncated =
                    true
            }
        }
    }
}
