package com.appforge.studio.terminal

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import java.io.File


internal data class GitWorkingTreeStatusSnapshot(
    val branch: String,
    val added: List<String>,
    val changed: List<String>,
    val modified: List<String>,
    val removed: List<String>,
    val missing: List<String>,
    val untracked: List<String>,
    val conflicting: List<String>,
    val originUrl: String?
)


/*
 * Very short-lived Git status cache.
 *
 * Why TTL instead of a filesystem "fingerprint"?
 *
 * A cheap fingerprint based only on directory timestamps can miss
 * modifications inside nested files. A complete fingerprint requires
 * scanning the tree and would duplicate much of the work JGit already
 * performs.
 *
 * 350 ms therefore only collapses duplicate/burst status requests.
 * AppForge-owned Git mutations explicitly invalidate or refresh it.
 */
internal object GitWorkingTreeStatusCache {

    private val cache =
        TimedValueCache<
            String,
            GitWorkingTreeStatusSnapshot
            >(
            ttlMillis =
                STATUS_CACHE_TTL_MS
        )


    fun getOrLoad(
        workspace: File,
        git: Git,
        repository: Repository
    ): GitWorkingTreeStatusSnapshot {
        val key =
            cacheKey(
                workspace
            )

        cache.get(key)
            ?.let {
                return it
            }

        return capture(
            git,
            repository
        ).also {
            cache.put(
                key,
                it
            )
        }
    }


    /*
     * Used immediately after an AppForge-owned mutation.
     * This guarantees the caller receives fresh state and also seeds
     * the cache for another panel that asks for status moments later.
     */
    fun refresh(
        workspace: File,
        git: Git,
        repository: Repository
    ): GitWorkingTreeStatusSnapshot {
        val fresh =
            capture(
                git,
                repository
            )

        cache.put(
            cacheKey(
                workspace
            ),
            fresh
        )

        return fresh
    }


    fun invalidate(
        workspace: File
    ) {
        cache.remove(
            cacheKey(
                workspace
            )
        )
    }


    private fun capture(
        git: Git,
        repository: Repository
    ): GitWorkingTreeStatusSnapshot {
        val status =
            git.status()
                .call()

        return GitWorkingTreeStatusSnapshot(
            branch =
                runCatching {
                    repository.branch
                }.getOrDefault(
                    "HEAD"
                ),

            added =
                status.added
                    .toList()
                    .sorted(),

            changed =
                status.changed
                    .toList()
                    .sorted(),

            modified =
                status.modified
                    .toList()
                    .sorted(),

            removed =
                status.removed
                    .toList()
                    .sorted(),

            missing =
                status.missing
                    .toList()
                    .sorted(),

            untracked =
                status.untracked
                    .toList()
                    .sorted(),

            conflicting =
                status.conflicting
                    .toList()
                    .sorted(),

            originUrl =
                repository
                    .config
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


    private fun cacheKey(
        workspace: File
    ): String =
        workspace
            .canonicalFile
            .absolutePath


    private const val STATUS_CACHE_TTL_MS =
        350L
}


/*
 * Pure synchronized TTL cache.
 *
 * The loader itself is deliberately kept outside this class, so no
 * expensive JGit operation runs while this monitor is held.
 */
internal class TimedValueCache<K, V>(
    ttlMillis: Long,
    private val nowNanos: () -> Long =
        System::nanoTime
) {
    private data class Entry<V>(
        val value: V,
        val expiresAtNanos: Long
    )

    private val ttlNanos =
        ttlMillis
            .coerceAtLeast(1L) *
            1_000_000L

    private val values =
        HashMap<
            K,
            Entry<V>
            >()


    @Synchronized
    fun get(
        key: K
    ): V? {
        val entry =
            values[key]
                ?: return null

        if (
            nowNanos() >=
                entry.expiresAtNanos
        ) {
            values.remove(
                key
            )

            return null
        }

        return entry.value
    }


    @Synchronized
    fun put(
        key: K,
        value: V
    ) {
        values[key] =
            Entry(
                value =
                    value,
                expiresAtNanos =
                    nowNanos() +
                        ttlNanos
            )
    }


    @Synchronized
    fun remove(
        key: K
    ) {
        values.remove(
            key
        )
    }


    @Synchronized
    internal fun sizeForTests():
        Int =
        values.size
}
