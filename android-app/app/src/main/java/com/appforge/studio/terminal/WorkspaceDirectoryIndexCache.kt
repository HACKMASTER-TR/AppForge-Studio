package com.appforge.studio.terminal

import java.io.File


/*
 * Short-lived cache for sorted directory children.
 *
 * It avoids repeating the expensive directory scan + sort while the
 * user moves between pages of the same large folder.
 *
 * AppForge-owned create/delete operations explicitly invalidate the
 * affected directory. External terminal changes become visible after
 * the short TTL expires.
 */
internal object WorkspaceDirectoryIndexes {

    private val cache =
        WorkspaceDirectoryIndexCache(
            ttlMillis =
                DIRECTORY_INDEX_TTL_MS
        )


    fun get(
        directory: File
    ): List<File>? =
        cache.get(
            directory
                .canonicalFile
                .absolutePath
        )


    fun put(
        directory: File,
        children: List<File>
    ) {
        cache.put(
            directory
                .canonicalFile
                .absolutePath,
            children
        )
    }


    fun invalidate(
        directory: File
    ) {
        cache.remove(
            directory
                .canonicalFile
                .absolutePath
        )
    }


    private const val DIRECTORY_INDEX_TTL_MS =
        1_500L
}


internal class WorkspaceDirectoryIndexCache(
    ttlMillis: Long,
    private val nowNanos: () -> Long =
        System::nanoTime
) {
    private data class Entry(
        val children: List<File>,
        val expiresAtNanos: Long
    )


    private val ttlNanos =
        ttlMillis
            .coerceAtLeast(1L) *
            1_000_000L


    private val values =
        HashMap<
            String,
            Entry
            >()


    @Synchronized
    fun get(
        key: String
    ): List<File>? {
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

        return entry.children
    }


    @Synchronized
    fun put(
        key: String,
        children: List<File>
    ) {
        /*
         * Defensive copy prevents callers from mutating the cached
         * ordering after publication.
         */
        values[key] =
            Entry(
                children =
                    children.toList(),
                expiresAtNanos =
                    nowNanos() +
                        ttlNanos
            )
    }


    @Synchronized
    fun remove(
        key: String
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
