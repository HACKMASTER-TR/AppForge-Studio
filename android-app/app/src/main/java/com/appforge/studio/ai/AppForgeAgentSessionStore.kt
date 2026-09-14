package com.appforge.studio.ai

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

internal class AppForgeAgentSessionStore(
    private val rootDir: File,
    private val clock: () -> Long =
        System::currentTimeMillis
) {
    private val lock = Any()

    private val sessionFile =
        File(
            rootDir,
            "unified-agent-session.json"
        )

    private val tempFile =
        File(
            rootDir,
            "unified-agent-session.tmp"
        )

    private val historyDir =
        File(
            rootDir,
            "unified-agent-session-history"
        )

    private val quarantineDir =
        File(
            rootDir,
            "unified-agent-session-quarantine"
        )

    fun save(
        session: AppForgeAgentPersistentSession
    ) = synchronized(lock) {
        rootDir.mkdirs()
        historyDir.mkdirs()

        val clean =
            AppForgeAgentSessionCodec.sanitize(
                session.copy(
                    updatedAtEpochMs =
                        clock()
                )
            )

        val envelope =
            encodeEnvelope(
                clean
            )

        atomicWrite(
            target =
                sessionFile,
            bytes =
                envelope,
            temp =
                tempFile
        )

        atomicWrite(
            target =
                historyFile(
                    clean.sessionId
                ),
            bytes =
                envelope
        )

        pruneHistory()

        Unit
    }

    fun load():
        AppForgeAgentSessionLoadResult =
        synchronized(lock) {
            if (
                !sessionFile.isFile
            ) {
                tempFile.delete()

                return@synchronized
                    AppForgeAgentSessionLoadResult.Empty
            }

            loadFile(
                file =
                    sessionFile,
                quarantineOnFailure =
                    true
            )
        }

    fun loadById(
        sessionId: String
    ): AppForgeAgentSessionLoadResult =
        synchronized(lock) {
            val file =
                historyFile(
                    requireSafeSessionId(
                        sessionId
                    )
                )

            if (
                !file.isFile
            ) {
                return@synchronized
                    AppForgeAgentSessionLoadResult.Empty
            }

            loadFile(
                file =
                    file,
                quarantineOnFailure =
                    true
            )
        }

    fun listRecent(
        limit: Int = 8
    ): List<AppForgeAgentPersistentSession> =
        synchronized(lock) {
            requireHistoryLimit(
                limit
            )

            historySessions()
                .filter {
                    !it.archived
                }
                .sortedWith(
                    compareByDescending<AppForgeAgentPersistentSession> {
                        it.pinned
                    }
                        .thenByDescending {
                            it.updatedAtEpochMs
                        }
                )
                .take(
                    limit
                )
        }

    fun listArchived(
        limit: Int = 8
    ): List<AppForgeAgentPersistentSession> =
        synchronized(lock) {
            requireHistoryLimit(
                limit
            )

            historySessions()
                .filter {
                    it.archived
                }
                .sortedByDescending {
                    it.updatedAtEpochMs
                }
                .take(
                    limit
                )
        }

    fun renameSession(
        sessionId: String,
        name: String
    ) = synchronized(lock) {
        mutateSession(
            sessionId
        ) {
            it.copy(
                customName =
                    AppForgeAgentArtifactSafety
                        .sanitize(
                            name.trim(),
                            80
                        )
                        .takeIf {
                            value ->
                                value.isNotBlank()
                        }
            )
        }
    }

    fun setPinned(
        sessionId: String,
        pinned: Boolean
    ) = synchronized(lock) {
        mutateSession(
            sessionId
        ) {
            it.copy(
                pinned =
                    pinned
            )
        }
    }

    fun setArchived(
        sessionId: String,
        archived: Boolean
    ) = synchronized(lock) {
        mutateSession(
            sessionId
        ) {
            it.copy(
                archived =
                    archived,
                pinned =
                    if (
                        archived
                    ) {
                        false
                    } else {
                        it.pinned
                    }
            )
        }
    }

    fun cleanupStorage(
        filesDir: File,
        nowEpochMs: Long =
            clock(),
        orphanGraceMs: Long =
            DEFAULT_ORPHAN_GRACE_MS
    ): AppForgeAgentSessionCleanupReport =
        synchronized(lock) {
            require(
                orphanGraceMs >=
                    0L
            ) {
                "Workspace cleanup grace negatif olamaz."
            }

            var deletedTempFiles =
                0

            rootDir
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile &&
                        it.name.endsWith(
                            ".tmp"
                        )
                }
                .forEach {
                    if (
                        it.delete()
                    ) {
                        deletedTempFiles +=
                            1
                    }
                }

            val workspaceRoot =
                File(
                    filesDir,
                    "unified-agent-workspaces"
                )
                    .canonicalFile

            if (
                !workspaceRoot.isDirectory
            ) {
                return@synchronized
                    AppForgeAgentSessionCleanupReport(
                        deletedWorkspaceCount =
                            0,
                        deletedTempFileCount =
                            deletedTempFiles
                    )
            }

            val referenced =
                referencedWorkspacePaths(
                    filesDir =
                        filesDir
                )

            val cutoff =
                nowEpochMs -
                    orphanGraceMs

            var deletedWorkspaces =
                0

            workspaceRoot
                .listFiles()
                .orEmpty()
                .filter {
                    it.isDirectory
                }
                .forEach {
                    workspace ->
                        val canonical =
                            runCatching {
                                workspace.canonicalFile
                            }.getOrNull()
                                ?: return@forEach

                        val insideRoot =
                            canonical.parentFile ==
                                workspaceRoot

                        val oldEnough =
                            canonical.lastModified() <=
                                cutoff

                        if (
                            insideRoot &&
                            canonical.path !in
                                referenced &&
                            oldEnough &&
                            canonical.deleteRecursively()
                        ) {
                            deletedWorkspaces +=
                                1
                        }
                }

            AppForgeAgentSessionCleanupReport(
                deletedWorkspaceCount =
                    deletedWorkspaces,
                deletedTempFileCount =
                    deletedTempFiles
            )
        }

    private fun requireHistoryLimit(
        limit: Int
    ) {
        require(
            limit in 1..MAX_HISTORY_FILES
        ) {
            "Session geçmişi limiti geçersiz."
        }
    }

    private fun historySessions():
        List<AppForgeAgentPersistentSession> {
        if (
            !historyDir.isDirectory
        ) {
            return emptyList()
        }

        return historyDir
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.name.endsWith(
                        ".json"
                    )
            }
            .sortedByDescending {
                it.lastModified()
            }
            .mapNotNull {
                file ->
                    when (
                        val loaded =
                            loadFile(
                                file =
                                    file,
                                quarantineOnFailure =
                                    true
                            )
                    ) {
                        is AppForgeAgentSessionLoadResult.Loaded ->
                            loaded.session

                        AppForgeAgentSessionLoadResult.Empty,
                        is AppForgeAgentSessionLoadResult.Quarantined ->
                            null
                    }
            }
            .distinctBy {
                it.sessionId
            }
    }

    private fun mutateSession(
        sessionId: String,
        transform: (
            AppForgeAgentPersistentSession
        ) ->
            AppForgeAgentPersistentSession
    ) {
        val safe =
            requireSafeSessionId(
                sessionId
            )

        val file =
            historyFile(
                safe
            )

        val loaded =
            when (
                val result =
                    loadFile(
                        file =
                            file,
                        quarantineOnFailure =
                            true
                    )
            ) {
                is AppForgeAgentSessionLoadResult.Loaded ->
                    result.session

                AppForgeAgentSessionLoadResult.Empty ->
                    error(
                        "Unified Agent session bulunamadı."
                    )

                is AppForgeAgentSessionLoadResult.Quarantined ->
                    error(
                        result.reason
                    )
            }

        val updated =
            AppForgeAgentSessionCodec.sanitize(
                transform(
                    loaded
                ).copy(
                    updatedAtEpochMs =
                        clock()
                )
            )

        val envelope =
            encodeEnvelope(
                updated
            )

        atomicWrite(
            target =
                file,
            bytes =
                envelope
        )

        val active =
            if (
                sessionFile.isFile
            ) {
                when (
                    val result =
                        loadFile(
                            file =
                                sessionFile,
                            quarantineOnFailure =
                                true
                        )
                ) {
                    is AppForgeAgentSessionLoadResult.Loaded ->
                        result.session

                    AppForgeAgentSessionLoadResult.Empty,
                    is AppForgeAgentSessionLoadResult.Quarantined ->
                        null
                }
            } else {
                null
            }

        if (
            active?.sessionId ==
                safe
        ) {
            atomicWrite(
                target =
                    sessionFile,
                bytes =
                    envelope,
                temp =
                    tempFile
            )
        }
    }

    private fun referencedWorkspacePaths(
        filesDir: File
    ): Set<String> {
        val workspaceRoot =
            File(
                filesDir,
                "unified-agent-workspaces"
            )
                .canonicalFile

        val candidates =
            buildList {
                addAll(
                    historySessions()
                )

                if (
                    sessionFile.isFile
                ) {
                    when (
                        val active =
                            loadFile(
                                file =
                                    sessionFile,
                                quarantineOnFailure =
                                    true
                            )
                    ) {
                        is AppForgeAgentSessionLoadResult.Loaded ->
                            add(
                                active.session
                            )

                        AppForgeAgentSessionLoadResult.Empty,
                        is AppForgeAgentSessionLoadResult.Quarantined ->
                            Unit
                    }
                }
            }

        return candidates
            .mapNotNull {
                AppForgeAgentSessionRuntimePolicy
                    .restoreWorkspacePath(
                        filesDir =
                            filesDir,
                        savedPath =
                            it.workspacePath
                    )
            }
            .mapNotNull {
                path ->
                    runCatching {
                        File(
                            path
                        ).canonicalFile
                    }.getOrNull()
            }
            .filter {
                it.parentFile ==
                    workspaceRoot
            }
            .map {
                it.path
            }
            .toSet()
    }

    fun deleteSession(
        sessionId: String
    ) = synchronized(lock) {
        val safe =
            requireSafeSessionId(
                sessionId
            )

        historyFile(
            safe
        ).delete()

        val active =
            if (
                sessionFile.isFile
            ) {
                when (
                    val result =
                        loadFile(
                            file =
                                sessionFile,
                            quarantineOnFailure =
                                true
                        )
                ) {
                    is AppForgeAgentSessionLoadResult.Loaded ->
                        result.session

                    AppForgeAgentSessionLoadResult.Empty,
                    is AppForgeAgentSessionLoadResult.Quarantined ->
                        null
                }
            } else {
                null
            }

        if (
            active?.sessionId ==
                safe
        ) {
            sessionFile.delete()
            tempFile.delete()
        }
    }

    fun clear() =
        synchronized(lock) {
            sessionFile.delete()
            tempFile.delete()
        }

    fun clearAll() =
        synchronized(lock) {
            sessionFile.delete()
            tempFile.delete()
            historyDir.deleteRecursively()
        }

    fun exists(): Boolean =
        synchronized(lock) {
            sessionFile.isFile
        }

    fun quarantineCount(): Int =
        synchronized(lock) {
            quarantineDir
                .listFiles()
                .orEmpty()
                .count {
                    it.isFile
                }
        }

    private fun loadFile(
        file: File,
        quarantineOnFailure: Boolean
    ): AppForgeAgentSessionLoadResult {
        if (
            !file.isFile
        ) {
            return AppForgeAgentSessionLoadResult.Empty
        }

        return runCatching {
            decodeEnvelope(
                file
            )
        }.fold(
            onSuccess = {
                session ->
                    AppForgeAgentSessionLoadResult.Loaded(
                        session
                    )
            },
            onFailure = {
                if (
                    quarantineOnFailure
                ) {
                    quarantine(
                        file
                    )
                }

                AppForgeAgentSessionLoadResult.Quarantined(
                    reason =
                        "Kaydedilmiş Unified Agent session'ı bozuk veya uyumsuzdu ve karantinaya alındı."
                )
            }
        )
    }

    private fun encodeEnvelope(
        session: AppForgeAgentPersistentSession
    ): ByteArray {
        val payload =
            AppForgeAgentSessionCodec.encode(
                session
            )

        require(
            payload.toByteArray(
                Charsets.UTF_8
            ).size <=
                MAX_SESSION_BYTES
        ) {
            "Unified Agent session dosyası sınırı aşıldı."
        }

        val envelope =
            JSONObject().apply {
                put(
                    "format",
                    STORE_FORMAT
                )
                put(
                    "schemaVersion",
                    session.schemaVersion
                )
                put(
                    "checksumSha256",
                    sha256(
                        payload.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                )
                put(
                    "payload",
                    payload
                )
            }
                .toString()
                .toByteArray(
                    Charsets.UTF_8
                )

        require(
            envelope.size <=
                MAX_ENVELOPE_BYTES
        ) {
            "Unified Agent session envelope sınırı aşıldı."
        }

        return envelope
    }

    private fun decodeEnvelope(
        file: File
    ): AppForgeAgentPersistentSession {
        require(
            file.length() in
                1..MAX_ENVELOPE_BYTES.toLong()
        ) {
            "Session envelope boyutu geçersiz."
        }

        val envelope =
            JSONObject(
                file.readText(
                    Charsets.UTF_8
                )
            )

        require(
            envelope.getString(
                "format"
            ) ==
                STORE_FORMAT
        ) {
            "Session store formatı geçersiz."
        }

        val schema =
            envelope.getInt(
                "schemaVersion"
            )

        require(
            schema ==
                AppForgeAgentPersistentSession
                    .CURRENT_SCHEMA_VERSION
        ) {
            "Session schema desteklenmiyor."
        }

        val payload =
            envelope.getString(
                "payload"
            )

        require(
            payload.toByteArray(
                Charsets.UTF_8
            ).size <=
                MAX_SESSION_BYTES
        ) {
            "Session payload boyutu geçersiz."
        }

        val expected =
            envelope.getString(
                "checksumSha256"
            )

        require(
            constantTimeEquals(
                expected,
                sha256(
                    payload.toByteArray(
                        Charsets.UTF_8
                    )
                )
            )
        ) {
            "Session checksum uyuşmuyor."
        }

        return AppForgeAgentSessionCodec.decode(
            payload
        )
    }

    private fun historyFile(
        sessionId: String
    ): File =
        File(
            historyDir,
            "session-${
                requireSafeSessionId(
                    sessionId
                )
            }.json"
        )

    private fun requireSafeSessionId(
        value: String
    ): String {
        val clean =
            value.trim()

        require(
            clean.length in 8..80 &&
                clean.all {
                    it.isLetterOrDigit() ||
                        it == '-' ||
                        it == '_'
                }
        ) {
            "Unified Agent sessionId geçersiz."
        }

        return clean
    }

    private fun atomicWrite(
        target: File,
        bytes: ByteArray,
        temp: File =
            File(
                target.parentFile,
                "${target.name}.tmp"
            )
    ) {
        target.parentFile?.mkdirs()
        temp.delete()

        FileOutputStream(
            temp
        ).use {
            output ->
                output.write(
                    bytes
                )
                output.flush()
                output.fd.sync()
        }

        atomicReplace(
            temp,
            target
        )
    }

    private fun quarantine(
        source: File
    ) {
        if (
            !source.isFile
        ) {
            return
        }

        quarantineDir.mkdirs()

        val safeName =
            source.name
                .filter {
                    it.isLetterOrDigit() ||
                        it in ".-_"
                }
                .take(96)
                .ifBlank {
                    "session"
                }

        val target =
            File(
                quarantineDir,
                "${clock()}-$safeName.corrupt"
            )

        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            runCatching {
                source.copyTo(
                    target,
                    overwrite = true
                )
                source.delete()
            }
        }

        if (
            source == sessionFile
        ) {
            tempFile.delete()
        }

        pruneQuarantine()
    }

    private fun pruneHistory() {
        historyDir
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.name.endsWith(
                        ".json"
                    )
            }
            .sortedByDescending {
                it.lastModified()
            }
            .drop(
                MAX_HISTORY_FILES
            )
            .forEach {
                it.delete()
            }
    }

    private fun pruneQuarantine() {
        quarantineDir
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile
            }
            .sortedByDescending {
                it.lastModified()
            }
            .drop(
                MAX_QUARANTINE_FILES
            )
            .forEach {
                it.delete()
            }
    }

    private fun atomicReplace(
        source: File,
        target: File
    ) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (
            _: AtomicMoveNotSupportedException
        ) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun sha256(
        bytes: ByteArray
    ): String =
        MessageDigest.getInstance(
            "SHA-256"
        )
            .digest(
                bytes
            )
            .joinToString("") {
                byte ->
                    "%02x".format(
                        byte
                    )
            }

    private fun constantTimeEquals(
        left: String,
        right: String
    ): Boolean =
        MessageDigest.isEqual(
            left.toByteArray(
                Charsets.US_ASCII
            ),
            right.toByteArray(
                Charsets.US_ASCII
            )
        )

    private companion object {
        const val STORE_FORMAT =
            "appforge-unified-agent-session"

        const val MAX_SESSION_BYTES =
            384 * 1024

        const val MAX_ENVELOPE_BYTES =
            512 * 1024

        const val MAX_HISTORY_FILES =
            12

        const val MAX_QUARANTINE_FILES =
            3

        const val DEFAULT_ORPHAN_GRACE_MS =
            48L * 60L * 60L * 1000L
    }
}

internal data class AppForgeAgentSessionCleanupReport(
    val deletedWorkspaceCount: Int,
    val deletedTempFileCount: Int
)
