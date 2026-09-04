package com.appforge.studio.terminal

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal data class LinuxManagedPtySessionState(
    val id: String,
    val title: String,
    val distribution: LinuxDistribution,
    val workspacePath: String,
    val running: Boolean,
    val starting: Boolean,
    val exitCode: Int?,
    val rows: Int,
    val columns: Int,
    val notifyOnCompletion: Boolean,
    val favorite: Boolean,
    val lastActivatedAt: Long,
    val restored: Boolean,
    val snapshot: AnsiTerminalSnapshot
)

internal object LinuxPtySessionRegistry {
    private data class Record(
        val id: String,
        var title: String,
        val distribution: LinuxDistribution,
        val workspace: File,
        val session: InteractiveLinuxPtySession,
        val buffer: AnsiTerminalBuffer,
        var running: Boolean,
        var starting: Boolean,
        var exitCode: Int?,
        var rows: Int,
        var columns: Int,
        var notifyOnCompletion: Boolean,
        var favorite: Boolean,
        var lastActivatedAt: Long,
        var restored: Boolean
    )

    private val lock = Any()

    private val records =
        LinkedHashMap<String, Record>()

    private val mutableStates =
        MutableStateFlow<
            List<LinuxManagedPtySessionState>
        >(emptyList())

    val states:
        StateFlow<List<LinuxManagedPtySessionState>> =
        mutableStates.asStateFlow()

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    fun initialize(
        context: Context
    ) {
        if (initialized) {
            return
        }

        synchronized(lock) {
            if (initialized) {
                return
            }

            appContext =
                context.applicationContext

            restoreDescriptorsLocked()
            initialized = true
            publishLocked()
        }
    }

    fun ensureSession(
        context: Context,
        distribution: LinuxDistribution,
        workspace: File
    ): String {
        initialize(context)

        val safeWorkspace =
            workspace.canonicalFile

        synchronized(lock) {
            val existing =
                records.values
                    .firstOrNull {
                        it.distribution == distribution &&
                            it.workspace == safeWorkspace
                    }

            if (existing != null) {
                return existing.id
            }
        }

        return createSession(
            context = context,
            distribution = distribution,
            workspace = safeWorkspace
        )
    }

    fun createSession(
        context: Context,
        distribution: LinuxDistribution,
        workspace: File,
        title: String? = null
    ): String {
        initialize(context)

        val safeWorkspace =
            workspace.canonicalFile

        require(
            safeWorkspace.isDirectory &&
                safeWorkspace.canRead()
        ) {
            "Linux PTY çalışma alanı geçersiz."
        }

        synchronized(lock) {
            check(
                records.size <
                    MAX_SESSIONS
            ) {
                "En fazla $MAX_SESSIONS Linux PTY oturumu açılabilir."
            }

            val id =
                UUID.randomUUID()
                    .toString()

            val index =
                records.size + 1

            records[id] =
                Record(
                    id = id,
                    title =
                        sanitizeTitle(
                            title ?: "Linux $index"
                        ),
                    distribution =
                        distribution,
                    workspace =
                        safeWorkspace,
                    session =
                        InteractiveLinuxPtySession(
                            appContext
                        ),
                    buffer =
                        AnsiTerminalBuffer(
                            initialRows = 24,
                            initialColumns = 80
                        ),
                    running = false,
                    starting = false,
                    exitCode = null,
                    rows = 24,
                    columns = 80,
                    notifyOnCompletion = true,
                    favorite = false,
                    lastActivatedAt = System.currentTimeMillis(),
                    restored = false
                )

            persistLocked()
            publishLocked()
            return id
        }
    }

    fun rename(
        id: String,
        title: String
    ) {
        synchronized(lock) {
            val record =
                records[id]
                    ?: return

            record.title =
                sanitizeTitle(title)

            persistLocked()
            publishLocked()
        }
    }

    fun setNotifyOnCompletion(
        id: String,
        enabled: Boolean
    ) {
        synchronized(lock) {
            val record =
                records[id]
                    ?: return

            record.notifyOnCompletion =
                enabled

            persistLocked()
            publishLocked()
        }
    }

    fun setFavorite(
        id: String,
        favorite: Boolean
    ) {
        synchronized(lock) {
            val record =
                records[id]
                    ?: return

            record.favorite =
                favorite

            persistLocked()
            publishLocked()
        }
    }

    fun markActivated(
        id: String
    ) {
        synchronized(lock) {
            val record =
                records[id]
                    ?: return

            record.lastActivatedAt =
                System.currentTimeMillis()

            persistLocked()
            publishLocked()
        }
    }

    fun duplicateSession(
        context: Context,
        sourceId: String
    ): String {
        initialize(context)

        val source =
            synchronized(lock) {
                records[sourceId]
                    ?.let { record ->
                        Triple(
                            record.distribution,
                            record.workspace,
                            record.title
                        )
                    }
            }
                ?: error(
                    "Kopyalanacak Linux PTY oturumu bulunamadı."
                )

        return createSession(
            context = context,
            distribution = source.first,
            workspace = source.second,
            title =
                sanitizeTitle(
                    "${source.third} Kopya"
                )
        )
    }

    suspend fun start(
        id: String,
        manager: AndroidLinuxRuntimeManager
    ) {
        val record =
            synchronized(lock) {
                val current =
                    records[id]
                        ?: error(
                            "Linux PTY oturumu bulunamadı."
                        )

                check(
                    !current.running &&
                        !current.starting
                ) {
                    "Linux PTY oturumu zaten çalışıyor."
                }

                current.starting = true
                current.exitCode = null
                current.restored = false
                current.buffer.reset()
                persistLocked()
                publishLocked()
                current
            }

        try {
            val rootfs =
                manager.requireReadyRootfs(
                    record.distribution
                )

            record.session.start(
                rootfs = rootfs,
                workspace =
                    record.workspace,
                rows = record.rows,
                columns =
                    record.columns,
                onOutput = { chunk ->
                    synchronized(lock) {
                        val current =
                            records[id]
                                ?: return@synchronized

                        current.buffer.feed(
                            TerminalSecretMasker.redact(
                                chunk
                            )
                        )
                        publishLocked()
                    }
                },
                onExit = { exitCode ->
                    val notify =
                        synchronized(lock) {
                            val current =
                                records[id]
                                    ?: return@synchronized false

                            current.running = false
                            current.starting = false
                            current.exitCode =
                                exitCode
                            persistLocked()
                            publishLocked()
                            current.notifyOnCompletion &&
                                !LinuxBackgroundJobStore
                                    .isActiveSession(
                                        appContext,
                                        id
                                    )
                        }

                    if (notify) {
                        LinuxSessionNotifier.notifyCompleted(
                            context = appContext,
                            title = record.title,
                            exitCode = exitCode
                        )
                    }
                }
            )

            synchronized(lock) {
                val current =
                    records[id]
                        ?: return

                current.starting = false

                // An extremely fast PTY exit may race with start().
                // Never overwrite an already-recorded exit with running=true.
                current.running =
                    current.exitCode == null

                persistLocked()
                publishLocked()
            }
        } catch (error: Throwable) {
            synchronized(lock) {
                records[id]?.let {
                    it.running = false
                    it.starting = false
                    persistLocked()
                    publishLocked()
                }
            }
            throw error
        }
    }

    suspend fun write(
        id: String,
        text: String
    ) {
        val session =
            synchronized(lock) {
                records[id]
                    ?.takeIf {
                        it.running
                    }
                    ?.session
            }
                ?: return

        session.write(text)
    }

    suspend fun sendControlC(
        id: String
    ) {
        val session =
            synchronized(lock) {
                records[id]
                    ?.takeIf {
                        it.running
                    }
                    ?.session
            }
                ?: return

        session.sendControlC()
    }

    suspend fun resize(
        id: String,
        rows: Int,
        columns: Int
    ) {
        val safeRows =
            rows.coerceIn(
                2,
                1_000
            )

        val safeColumns =
            columns.coerceIn(
                10,
                2_000
            )

        val session =
            synchronized(lock) {
                val record =
                    records[id]
                        ?: return

                record.rows =
                    safeRows
                record.columns =
                    safeColumns
                record.buffer.resize(
                    safeRows,
                    safeColumns
                )
                persistLocked()
                publishLocked()

                if (record.running) {
                    record.session
                } else {
                    null
                }
            }

        session?.resize(
            safeRows,
            safeColumns
        )
    }

    fun terminate(
        id: String
    ) {
        val session =
            synchronized(lock) {
                records[id]
                    ?.takeIf {
                        it.running ||
                            it.starting
                    }
                    ?.session
            }

        // Native/process shutdown can block or synchronously trigger onExit.
        // Never hold the registry lock while terminating the PTY.
        session?.terminate()
    }

    fun closeSession(
        id: String
    ) {
        val removed =
            synchronized(lock) {
                val record =
                    records.remove(id)
                        ?: return

                persistLocked()
                publishLocked()
                record
            }

        removed.session.close()
    }

    fun closeAll() {
        val removed =
            synchronized(lock) {
                val all =
                    records.values
                        .toList()

                records.clear()
                persistLocked()
                publishLocked()
                all
            }

        removed.forEach {
            it.session.close()
        }
    }

    fun matching(
        distribution: LinuxDistribution,
        workspace: File
    ): List<LinuxManagedPtySessionState> {
        val safePath =
            runCatching {
                workspace.canonicalPath
            }.getOrElse {
                workspace.absolutePath
            }

        return states.value.filter {
            it.distribution == distribution &&
                it.workspacePath == safePath
        }
    }

    private fun restoreDescriptorsLocked() {
        val raw =
            appContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_SESSIONS,
                    null
                )
                ?: return

        val array =
            runCatching {
                JSONArray(raw)
            }.getOrNull()
                ?: return

        for (
            index in
            0 until minOf(
                array.length(),
                MAX_SESSIONS
            )
        ) {
            val item =
                array.optJSONObject(index)
                    ?: continue

            val id =
                item.optString("id")
                    .takeIf {
                        UUID_PATTERN.matches(it)
                    }
                    ?: continue

            val distribution =
                runCatching {
                    LinuxDistribution.valueOf(
                        item.optString(
                            "distribution"
                        )
                    )
                }.getOrNull()
                    ?: continue

            val workspace =
                runCatching {
                    File(
                        item.optString(
                            "workspace"
                        )
                    ).canonicalFile
                }.getOrNull()
                    ?.takeIf {
                        it.isDirectory &&
                            it.canRead()
                    }
                    ?: continue

            val rows =
                item.optInt(
                    "rows",
                    24
                ).coerceIn(
                    2,
                    1_000
                )

            val columns =
                item.optInt(
                    "columns",
                    80
                ).coerceIn(
                    10,
                    2_000
                )

            records[id] =
                Record(
                    id = id,
                    title =
                        sanitizeTitle(
                            item.optString(
                                "title",
                                "Linux"
                            )
                        ),
                    distribution =
                        distribution,
                    workspace =
                        workspace,
                    session =
                        InteractiveLinuxPtySession(
                            appContext
                        ),
                    buffer =
                        AnsiTerminalBuffer(
                            initialRows = rows,
                            initialColumns =
                                columns
                        ),
                    running = false,
                    starting = false,
                    exitCode = null,
                    rows = rows,
                    columns = columns,
                    notifyOnCompletion =
                        item.optBoolean(
                            "notify",
                            true
                        ),
                    favorite =
                        item.optBoolean(
                            "favorite",
                            false
                        ),
                    lastActivatedAt =
                        item.optLong(
                            "lastActive",
                            0L
                        ).coerceAtLeast(0L),
                    restored = true
                )
        }
    }

    private fun persistLocked() {
        if (!::appContext.isInitialized) {
            return
        }

        val array =
            JSONArray()

        records.values
            .take(MAX_SESSIONS)
            .forEach { record ->
                array.put(
                    JSONObject()
                        .put(
                            "id",
                            record.id
                        )
                        .put(
                            "title",
                            record.title
                        )
                        .put(
                            "distribution",
                            record.distribution.name
                        )
                        .put(
                            "workspace",
                            record.workspace.absolutePath
                        )
                        .put(
                            "rows",
                            record.rows
                        )
                        .put(
                            "columns",
                            record.columns
                        )
                        .put(
                            "notify",
                            record.notifyOnCompletion
                        )
                        .put(
                            "favorite",
                            record.favorite
                        )
                        .put(
                            "lastActive",
                            record.lastActivatedAt
                        )
                )
            }

        appContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_SESSIONS,
                array.toString()
            )
            .apply()
    }

    private fun publishLocked() {
        mutableStates.value =
            records.values.map { record ->
                LinuxManagedPtySessionState(
                    id = record.id,
                    title = record.title,
                    distribution =
                        record.distribution,
                    workspacePath =
                        record.workspace.absolutePath,
                    running = record.running,
                    starting = record.starting,
                    exitCode = record.exitCode,
                    rows = record.rows,
                    columns = record.columns,
                    notifyOnCompletion =
                        record.notifyOnCompletion,
                    favorite = record.favorite,
                    lastActivatedAt =
                        record.lastActivatedAt,
                    restored = record.restored,
                    snapshot =
                        record.buffer.snapshot()
                )
            }
    }

    private fun sanitizeTitle(
        value: String
    ): String =
        TerminalTextSanitizer.clean(
            value
        )
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .take(MAX_TITLE_LENGTH)
            .ifBlank {
                "Linux"
            }

    private const val MAX_SESSIONS = 6
    private const val MAX_TITLE_LENGTH = 40
    private const val PREFS_NAME =
        "appforge_linux_pty_sessions"
    private const val KEY_SESSIONS =
        "session_descriptors_v1"

    private val UUID_PATTERN =
        Regex(
            "^[0-9a-fA-F-]{36}$"
        )
}
