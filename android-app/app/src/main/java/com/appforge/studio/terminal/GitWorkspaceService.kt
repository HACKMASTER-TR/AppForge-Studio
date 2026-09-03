package com.appforge.studio.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.net.URI
import java.text.DateFormat
import java.util.Date

data class GitCredentials(
    val username: String = "",
    val token: String = "",
    val allowedHosts: Set<String> = emptySet()
) {
    fun provider(
        remoteUrl: String?
    ): CredentialsProvider? {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return null
        }

        require(
            cleanToken.length <= 32 * 1_024 &&
                cleanToken.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                } &&
                username.length <= 512 &&
                username.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "Git kimlik bilgisi geçersiz."
        }

        if (!remoteAllowed(remoteUrl)) {
            return null
        }

        return UsernamePasswordCredentialsProvider(
            username.trim().ifBlank {
                "oauth2"
            },
            cleanToken
        )
    }

    private fun remoteAllowed(
        remoteUrl: String?
    ): Boolean {
        val remoteUri =
            runCatching {
                URI(remoteUrl.orEmpty())
            }.getOrNull()
                ?: return false

        if (!remoteUri.scheme.equals(
                "https",
                ignoreCase = true
            )
        ) {
            return false
        }

        if (allowedHosts.isEmpty()) {
            return true
        }

        val host =
            remoteUri.host
                ?.lowercase()
                ?: return false

        return allowedHosts.any { allowed ->
            val normalized = allowed.lowercase()

            host == normalized
        }
    }
}
object GitWorkspaceService {
    suspend fun status(
        workspace: File
    ): String =
        withRepository(workspace) { git, repository ->
            val status =
                git.status()
                    .call()

            buildStatusText(
                repository,
                status
            )
        }

    suspend fun init(
        workspace: File
    ): String =
        withContext(Dispatchers.IO) {
            val safeWorkspace =
                requireWorkspace(workspace)

            Git.init()
                .setDirectory(safeWorkspace)
                .call()
                .use { git ->
                    "Git deposu hazır: ${git.repository.directory.absolutePath}"
                }
        }

    suspend fun stageAll(
        workspace: File
    ): String =
        withRepository(workspace) { git, _ ->
            git.add()
                .addFilepattern(".")
                .call()

            git.add()
                .setUpdate(true)
                .addFilepattern(".")
                .call()

            "Tüm değişiklikler hazırlama alanına eklendi."
        }

    suspend fun commit(
        workspace: File,
        message: String,
        authorName: String,
        authorEmail: String
    ): String =
        withRepository(workspace) { git, _ ->
            require(message.isNotBlank()) {
                "Commit mesajı boş olamaz."
            }

            val commit =
                git.commit()
                    .setMessage(message.trim())
                    .setAuthor(
                        authorName.ifBlank {
                            "AppForge User"
                        },
                        authorEmail.ifBlank {
                            "appforge@local"
                        }
                    )
                    .call()

            "Commit oluşturuldu: ${commit.name.take(8)} • ${commit.shortMessage}"
        }

    suspend fun log(
        workspace: File,
        limit: Int = 20
    ): String =
        withRepository(workspace) { git, _ ->
            val commits =
                git.log()
                    .setMaxCount(
                        limit.coerceIn(
                            1,
                            100
                        )
                    )
                    .call()
                    .toList()

            if (commits.isEmpty()) {
                "Henüz commit yok."
            } else {
                commits.joinToString("\n") { commit ->
                    val date =
                        DateFormat
                            .getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT
                            )
                            .format(
                                Date(
                                    commit.commitTime *
                                        1_000L
                                )
                            )

                    "${commit.name.take(8)}  $date  ${commit.shortMessage}"
                }
            }
        }

    suspend fun setRemote(
        workspace: File,
        remoteUrl: String
    ): String =
        withRepository(workspace) { git, repository ->
            val cleanUrl =
                validateRemoteUrl(remoteUrl)

            val existing =
                repository
                    .config
                    .getString(
                        "remote",
                        "origin",
                        "url"
                    )

            if (existing.isNullOrBlank()) {
                git.remoteAdd()
                    .setName("origin")
                    .setUri(
                        URIish(cleanUrl)
                    )
                    .call()
            } else {
                git.remoteSetUrl()
                    .setRemoteName("origin")
                    .setRemoteUri(
                        URIish(cleanUrl)
                    )
                    .call()
            }

            "origin ayarlandı: $cleanUrl"
        }

    suspend fun pull(
        workspace: File,
        credentials: GitCredentials
    ): String =
        withRepository(workspace) { git, repository ->
            val command =
                git.pull()

            credentials
                .provider(
                    originUrl(repository)
                )
                ?.let(
                    command::setCredentialsProvider
                )

            val result =
                command.call()

            val status =
                result
                    .mergeResult
                    ?.mergeStatus
                    ?.toString()
                    ?: result
                        .rebaseResult
                        ?.status
                        ?.toString()
                    ?: "Tamamlandı"

            "Pull sonucu: $status"
        }

    suspend fun push(
        workspace: File,
        credentials: GitCredentials
    ): String =
        withRepository(workspace) { git, repository ->
            val command =
                git.push()

            credentials
                .provider(
                    originUrl(repository)
                )
                ?.let(
                    command::setCredentialsProvider
                )

            val results =
                command.call()

            val messages =
                results.flatMap { result ->
                    result.remoteUpdates.map { update ->
                        "${update.remoteName}: ${update.status}"
                    }
                }

            if (messages.isEmpty()) {
                "Push tamamlandı."
            } else {
                messages.joinToString("\n")
            }
        }

    suspend fun clone(
        workspace: File,
        remoteUrl: String,
        credentials: GitCredentials
    ): File =
        withContext(Dispatchers.IO) {
            val safeWorkspace =
                requireWorkspace(workspace)

            val cleanUrl =
                validateRemoteUrl(remoteUrl)

            val repositoryName =
                cleanUrl
                    .substringAfterLast('/')
                    .removeSuffix(".git")
                    .replace(
                        Regex("[^A-Za-z0-9._-]"),
                        "_"
                    )
                    .ifBlank {
                        "repository"
                    }

            val destination =
                File(
                    safeWorkspace,
                    repositoryName
                ).canonicalFile

            require(
                destination.path.startsWith(
                    safeWorkspace.path +
                        File.separator
                )
            ) {
                "Geçersiz hedef klasör."
            }

            require(!destination.exists()) {
                "Hedef klasör zaten var: ${destination.name}"
            }

            val command =
                Git.cloneRepository()
                    .setURI(cleanUrl)
                    .setDirectory(destination)

            credentials
                .provider(cleanUrl)
                ?.let(
                    command::setCredentialsProvider
                )

            command.call()
                .close()

            destination
        }

    suspend fun executeTerminalCommand(
        workspace: File,
        command: String,
        credentials: GitCredentials =
            GitCredentials()
    ): String? {
        val clean =
            command.trim()

        return when {
            clean == "git status" ->
                status(workspace)

            clean == "git init" ->
                init(workspace)

            clean == "git add ." ||
                clean == "git add -A" ->
                stageAll(workspace)

            clean == "git log" ||
                clean == "git log --oneline" ->
                log(workspace)

            clean == "git pull" ->
                pull(
                    workspace,
                    credentials
                )

            clean == "git push" ->
                push(
                    workspace,
                    credentials
                )

            else ->
                null
        }
    }

    private suspend fun <T> withRepository(
        workspace: File,
        block: (Git, Repository) -> T
    ): T =
        withContext(Dispatchers.IO) {
            val safeWorkspace =
                requireWorkspace(workspace)

            val repository =
                FileRepositoryBuilder()
                    .findGitDir(safeWorkspace)
                    .readEnvironment()
                    .build()

            repository.use { safeRepository ->
                require(
                    safeRepository.directory != null &&
                        safeRepository.directory.isDirectory
                ) {
                    "Bu çalışma alanında Git deposu yok. Önce Git Başlat'a dokunun."
                }

                val repositoryRoot =
                    runCatching {
                        safeRepository
                            .workTree
                            .canonicalFile
                    }.getOrNull()

                require(repositoryRoot == safeWorkspace) {
                    "Git deposu seçilen proje kökünde değil. Depo kökünü çalışma alanı olarak açın."
                }

                Git(safeRepository).use { git ->
                    block(
                        git,
                        safeRepository
                    )
                }
            }
        }

    private fun buildStatusText(
        repository: Repository,
        status: Status
    ): String {
        val branch =
            runCatching {
                repository.branch
            }.getOrDefault("HEAD")

        val changes =
            buildList {
                status.added.forEach {
                    add("A  $it")
                }

                status.changed.forEach {
                    add("M  $it")
                }

                status.modified.forEach {
                    add(" M $it")
                }

                status.removed.forEach {
                    add("D  $it")
                }

                status.missing.forEach {
                    add(" D $it")
                }

                status.untracked.forEach {
                    add("?? $it")
                }

                status.conflicting.forEach {
                    add("UU $it")
                }
            }.distinct()

        return buildString {
            append("Dal: ")
            append(branch)
            append('\n')

            if (changes.isEmpty()) {
                append("Çalışma alanı temiz.")
            } else {
                append(
                    changes.joinToString("\n")
                )
            }
        }
    }

    private fun originUrl(
        repository: Repository
    ): String? =
        repository.config.getString(
            "remote",
            "origin",
            "url"
        )

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

    private fun validateRemoteUrl(
        value: String
    ): String {
        val clean =
            value.trim()

        val uri =
            runCatching {
                URI(clean)
            }.getOrElse {
                throw IllegalArgumentException(
                    "Geçersiz Git depo adresi.",
                    it
                )
            }

        require(
            uri.scheme.equals(
                "https",
                ignoreCase = true
            ) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null
        ) {
            "Kullanıcı bilgisi içermeyen bir HTTPS depo adresi kullanın."
        }

        require(
            clean.length <= 2_048 &&
                clean.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "Geçersiz Git depo adresi."
        }

        return clean
    }
}
