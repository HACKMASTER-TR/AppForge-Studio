package com.appforge.studio.terminal

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class LocalPtyTerminalState(
    val id: String,
    val title: String,
    val workspacePath: String,
    val running: Boolean,
    val starting: Boolean,
    val exitCode: Int?,
    val rows: Int,
    val columns: Int,
    val lastActivatedAt: Long,
    val restored: Boolean,
    val outputRevision: Long,
    val snapshot: AnsiTerminalSnapshot
)

internal object LocalPtySessionRegistry {
    private data class Record(
        val id: String,
        val title: String,
        val workspace: File,
        var workingDirectory: File,
        val session: LocalInteractivePtySession,
        val buffer: AnsiTerminalBuffer,
        var running: Boolean,
        var starting: Boolean,
        var exitCode: Int?,
        var rows: Int,
        var columns: Int,
        var lastActivatedAt: Long,
        var restored: Boolean,
        var outputRevision: Long
    )

    private val lock = Any()

    /*
     * PTY output can arrive in many small chunks during paste/build output.
     * Publishing a full terminal snapshot for every chunk makes Compose
     * rebuild scrollback repeatedly. Coalesce output into short UI frames.
     */
    private val outputPublishScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default
        )

    private val pendingOutputPublishes =
        HashSet<String>()

    private val records =
        LinkedHashMap<String, Record>()
    private val mutableStates =
        MutableStateFlow<List<LocalPtyTerminalState>>(
            emptyList()
        )

    val states:
        StateFlow<List<LocalPtyTerminalState>> =
        mutableStates.asStateFlow()

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (initialized) {
            return
        }

        synchronized(lock) {
            if (initialized) {
                return
            }

            appContext =
                context.applicationContext

            TerminalGitCredentialBridge
                .clearStale(
                    appContext
                )

            restoreLocked()
            initialized = true
            publishLocked()
        }
    }

    fun matching(workspace: File):
        List<LocalPtyTerminalState> {
        val path =
            workspace.canonicalFile.absolutePath

        return states.value
            .filter {
                it.workspacePath == path
            }
    }

    fun ensureSession(
        context: Context,
        workspace: File
    ): String {
        initialize(context)

        val safeWorkspace =
            workspace.canonicalFile

        synchronized(lock) {
            records.values
                .firstOrNull {
                    it.workspace ==
                        safeWorkspace
                }
                ?.let {
                    return it.id
                }
        }

        return createSession(
            context = context,
            workspace = safeWorkspace
        )
    }

    fun createSession(
        context: Context,
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
            "PTY çalışma alanı geçersiz."
        }

        synchronized(lock) {
            check(
                records.values.count {
                    it.workspace ==
                        safeWorkspace
                } < MAX_LOCAL_PTY_SESSIONS
            ) {
                "En fazla $MAX_LOCAL_PTY_SESSIONS PTY oturumu açılabilir."
            }

            val id =
                UUID.randomUUID()
                    .toString()

            val index =
                nextTerminalIndexLocked(
                    safeWorkspace
                )

            records[id] =
                Record(
                    id = id,
                    title =
                        title
                            ?.trim()
                            ?.take(40)
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: "Terminal $index",
                    workspace =
                        safeWorkspace,
                    workingDirectory =
                        safeWorkspace,
                    session =
                        LocalInteractivePtySession(
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
                    lastActivatedAt =
                        System.currentTimeMillis(),
                    restored = false,
                    outputRevision = 0L
                )

            persistLocked()
            publishLocked()
            return id
        }
    }

    suspend fun ensureStarted(
        context: Context,
        workspace: File
    ): String {
        /*
         * Registry restore reads persisted PTY snapshots and filesystem
         * metadata. Never perform that work on the Compose/UI dispatcher.
         */
        val id =
            withContext(
                Dispatchers.IO
            ) {
                ensureSession(
                    context,
                    workspace
                )
            }

        val shouldStart =
            synchronized(lock) {
                records[id]
                    ?.let {
                        !it.running &&
                            !it.starting
                    }
                    ?: false
            }

        if (shouldStart) {
            start(id)
        }

        withContext(
            Dispatchers.IO
        ) {
            markActivated(id)
        }

        return id
    }

    suspend fun start(id: String) {
        val record =
            synchronized(lock) {
                val current =
                    records[id]
                        ?: error(
                            "PTY oturumu bulunamadı."
                        )

                check(
                    !current.running &&
                        !current.starting
                ) {
                    "PTY oturumu zaten çalışıyor."
                }

                current.starting = true
                current.exitCode = null
                if (!current.restored) {
                    current.buffer.reset()
                } else {
                    current.buffer.feed(
                        "\n[AppForge] PTY geri yüklendi • yeni shell başlatılıyor.\n"
                    )
                }
                current.outputRevision += 1L
                publishLocked()
                current
            }

        try {
            record.session.start(
                workspace =
                    record.workingDirectory
                        .takeIf {
                            it.isDirectory &&
                                it.canRead()
                        }
                        ?: record.workspace,
                rows = record.rows,
                columns = record.columns,
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
                        current.outputRevision += 1L
                        current.session
                            .currentWorkingDirectory()
                            ?.takeIf {
                                it.isDirectory &&
                                    it.canRead()
                            }
                            ?.let {
                                current.workingDirectory = it
                            }

                        scheduleOutputPublishLocked(
                            id
                        )
                    }
                },
                onExit = { exitCode ->
                    synchronized(lock) {
                        records[id]?.let {
                            it.running = false
                            it.starting = false
                            it.exitCode =
                                exitCode
                            it.restored = false
                            persistLocked()
                            publishLocked()
                        }
                    }
                }
            )

            synchronized(lock) {
                records[id]?.let {
                    it.starting = false
                    it.running =
                        it.exitCode == null
                    it.restored = false
                    persistLocked()
                    publishLocked()
                }
            }
        } catch (error: Throwable) {
            synchronized(lock) {
                records[id]?.let {
                    it.running = false
                    it.starting = false
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

    suspend fun sendControlC(id: String) {
        write(id, "\u0003")
    }

    suspend fun resize(
        id: String,
        rows: Int,
        columns: Int
    ) {
        val safeRows =
            rows.coerceIn(8, 80)
        val safeColumns =
            columns.coerceIn(20, 240)

        val session =
            synchronized(lock) {
                val current =
                    records[id]
                        ?: return

                if (
                    current.rows ==
                        safeRows &&
                    current.columns ==
                        safeColumns
                ) {
                    return
                }

                current.rows =
                    safeRows
                current.columns =
                    safeColumns
                current.buffer.resize(
                    safeRows,
                    safeColumns
                )
                persistLocked()
                publishLocked()

                current.session
                    .takeIf {
                        current.running
                    }
            }

        session?.resize(
            safeRows,
            safeColumns
        )
    }

    fun markActivated(id: String) {
        synchronized(lock) {
            records[id]?.let {
                it.lastActivatedAt =
                    System.currentTimeMillis()
                it.session
                    .currentWorkingDirectory()
                    ?.takeIf { cwd ->
                        cwd.isDirectory && cwd.canRead()
                    }
                    ?.let { cwd ->
                        it.workingDirectory = cwd
                    }
                persistLocked()
                publishLocked()
            }
        }
    }

    fun terminate(id: String) {
        synchronized(lock) {
            records[id]
                ?.session
                ?.terminate()
        }
    }

    fun closeSession(id: String) {
        val removed =
            synchronized(lock) {
                records.remove(id)
                    .also {
                        persistLocked()
                        publishLocked()
                    }
            }

        removed?.session?.close()
    }

    suspend fun sendCommand(
        context: Context,
        workspace: File,
        command: String
    ) {
        val id =
            ensureStarted(
                context,
                workspace
            )

        write(
            id,
            command
                .trimEnd('\r', '\n') +
                "\r"
        )
    }

    fun clearBuffer(id: String) {
        synchronized(lock) {
            records[id]?.let { record ->
                record.buffer.clear()
                record.outputRevision += 1L
                persistLocked()
                publishLocked()
            }
        }
    }

    fun persistNow() {
        if (!initialized) return

        synchronized(lock) {
            records.values.forEach { record ->
                record.session
                    .currentWorkingDirectory()
                    ?.takeIf {
                        it.isDirectory && it.canRead()
                    }
                    ?.let {
                        record.workingDirectory = it
                    }
            }
            persistLocked()
        }
    }

    private fun restoreLocked() {
        val raw =
            appContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(KEY_SESSIONS, null)
                ?: return

        val array =
            runCatching { JSONArray(raw) }
                .getOrNull()
                ?: return

        for (index in 0 until minOf(array.length(), MAX_LOCAL_PTY_SESSIONS)) {
            val item =
                array.optJSONObject(index)
                    ?: continue

            val id =
                item.optString("id")
                    .takeIf {
                        runCatching { UUID.fromString(it) }.isSuccess
                    }
                    ?: continue

            val workspace =
                runCatching {
                    File(item.optString("workspace"))
                        .canonicalFile
                }.getOrNull()
                    ?.takeIf {
                        it.isDirectory && it.canRead()
                    }
                    ?: continue

            val cwd =
                runCatching {
                    File(item.optString("cwd", workspace.absolutePath))
                        .canonicalFile
                }.getOrNull()
                    ?.takeIf {
                        it.isDirectory && it.canRead()
                    }
                    ?: workspace

            val rows =
                item.optInt("rows", 24)
                    .coerceIn(8, 80)
            val columns =
                item.optInt("columns", 80)
                    .coerceIn(20, 240)
            val buffer =
                AnsiTerminalBuffer(
                    initialRows = rows,
                    initialColumns = columns
                )

            item.optString("snapshot")
                .takeIf { it.isNotBlank() }
                ?.let { buffer.feed(it) }

            records[id] =
                Record(
                    id = id,
                    title =
                        item.optString("title", "Terminal 1")
                            .trim()
                            .take(40)
                            .ifBlank { "Terminal 1" },
                    workspace = workspace,
                    workingDirectory = cwd,
                    session =
                        LocalInteractivePtySession(appContext),
                    buffer = buffer,
                    running = false,
                    starting = false,
                    exitCode = null,
                    rows = rows,
                    columns = columns,
                    lastActivatedAt =
                        item.optLong("lastActive", 0L)
                            .coerceAtLeast(0L),
                    restored = true,
                    outputRevision = 1L
                )
        }
    }

    private fun persistLocked() {
        if (!::appContext.isInitialized) return

        val array = JSONArray()
        records.values
            .take(MAX_LOCAL_PTY_SESSIONS)
            .forEach { record ->
                val snapshot =
                    record.buffer
                        .snapshot()
                        .plainText()
                        .takeLast(MAX_PERSISTED_SNAPSHOT_CHARS)

                array.put(
                    JSONObject()
                        .put("id", record.id)
                        .put("title", record.title)
                        .put("workspace", record.workspace.absolutePath)
                        .put("cwd", record.workingDirectory.absolutePath)
                        .put("rows", record.rows)
                        .put("columns", record.columns)
                        .put("lastActive", record.lastActivatedAt)
                        .put("snapshot", snapshot)
                )
            }

        appContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(KEY_SESSIONS, array.toString())
            .apply()
    }

    private fun nextTerminalIndexLocked(
        workspace: File
    ): Int {
        val used =
            records.values
                .filter { it.workspace == workspace }
                .mapNotNull { record ->
                    Regex("^Terminal (\\d+)$")
                        .matchEntire(record.title)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                }
                .toSet()

        return (used.maxOrNull() ?: 0) + 1
    }

    private fun scheduleOutputPublishLocked(
        id: String
    ) {
        if (
            !pendingOutputPublishes.add(
                id
            )
        ) {
            return
        }

        outputPublishScope.launch {
            delay(
                OUTPUT_PUBLISH_INTERVAL_MS
            )

            synchronized(lock) {
                pendingOutputPublishes.remove(
                    id
                )

                if (
                    records.containsKey(
                        id
                    )
                ) {
                    publishLocked()
                }
            }
        }
    }

    private fun publishLocked() {
        mutableStates.value =
            records.values
                .map {
                    LocalPtyTerminalState(
                        id = it.id,
                        title = it.title,
                        workspacePath =
                            it.workspace.absolutePath,
                        running = it.running,
                        starting = it.starting,
                        exitCode = it.exitCode,
                        rows = it.rows,
                        columns = it.columns,
                        lastActivatedAt =
                            it.lastActivatedAt,
                        restored = it.restored,
                        outputRevision = it.outputRevision,
                        snapshot =
                            it.buffer.snapshot()
                    )
                }
    }

    private const val MAX_LOCAL_PTY_SESSIONS = 6

    /*
     * ~31 fps is enough for terminal output while avoiding hundreds of
     * Compose snapshots during large paste/build bursts.
     */
    private const val OUTPUT_PUBLISH_INTERVAL_MS =
        32L

    private const val MAX_PERSISTED_SNAPSHOT_CHARS = 32_768
    private const val PREFS_NAME = "appforge_local_pty_sessions"
    private const val KEY_SESSIONS = "session_descriptors_v2"
}

private class LocalInteractivePtySession(
    context: Context
) : AutoCloseable {
    private val appContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val running =
        AtomicBoolean(false)

    private var processId: Int? =
        null

    @Volatile
    private var linuxMode =
        false

    private var gitCredentialLease:
        TerminalGitCredentialLease? =
        null

    private var inputDescriptor:
        ParcelFileDescriptor? =
        null

    private var outputDescriptor:
        ParcelFileDescriptor? =
        null

    private var controlDescriptor:
        ParcelFileDescriptor? =
        null

    private var inputStream:
        FileInputStream? =
        null

    private var outputStream:
        FileOutputStream? =
        null

    private var readerJob: Job? =
        null

    private var waiterJob: Job? =
        null

    suspend fun start(
        workspace: File,
        rows: Int,
        columns: Int,
        onOutput: (String) -> Unit,
        onExit: (Int) -> Unit
    ) {
        check(
            running.compareAndSet(
                false,
                true
            )
        ) {
            "PTY zaten çalışıyor."
        }

        try {
            withContext(Dispatchers.IO) {
                val runtimeManager =
                    AndroidLinuxRuntimeManager(
                        appContext
                    )

                val runtimeStatus =
                    runtimeManager.inspect(
                        LinuxDistribution.UBUNTU
                    )

                val linuxRootfs =
                    if (runtimeStatus.ready) {
                        runtimeManager
                            .requireReadyRootfs(
                                LinuxDistribution.UBUNTU
                            )
                    } else {
                        null
                    }

                val spawned =
                    if (linuxRootfs != null) {
                        linuxMode = true

                        TerminalStandaloneDeveloperBootstrap
                            .install(
                                appContext,
                                linuxRootfs
                            )

                        closeGitCredentialLease()

                        val credentialLease =
                            TerminalGitCredentialBridge
                                .prepare(
                                    appContext
                                )

                        gitCredentialLease =
                            credentialLease

                        val launcher =
                            PackagedLinuxEngine(
                                appContext
                            ).requireLauncher()

                        val runtimeTemp =
                            File(
                                appContext.filesDir,
                                "terminal/linux/proroot-tmp"
                            ).apply {
                                mkdirs()
                            }

                        val environment =
                            mutableMapOf(
                                "PROROOT_TMP_DIR" to
                                    runtimeTemp.absolutePath,
                                "HOME" to "/root",
                                "TERM" to
                                    "xterm-256color",
                                "COLORTERM" to
                                    "truecolor",
                                "LANG" to
                                    "C.UTF-8",
                                "PATH" to
                                    "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                            ).apply {
                                if (
                                    credentialLease !=
                                        null
                                ) {
                                    put(
                                        "GIT_ASKPASS",
                                        APPFORGE_GIT_ASKPASS_GUEST_PATH
                                    )
                                    put(
                                        "GIT_TERMINAL_PROMPT",
                                        "0"
                                    )

                                    /*
                                     * Never persist plaintext Git credentials
                                     * through a configured credential helper.
                                     * The AppForge Keystore-backed bridge owns
                                     * this PTY session's temporary credential.
                                     */
                                    put(
                                        "GIT_CONFIG_COUNT",
                                        "1"
                                    )
                                    put(
                                        "GIT_CONFIG_KEY_0",
                                        "credential.helper"
                                    )
                                    put(
                                        "GIT_CONFIG_VALUE_0",
                                        ""
                                    )
                                }
                            }

                        AppForgePtyBridge.spawn(
                            executable =
                                launcher.absolutePath,
                            arguments =
                                ProrootPinnedRuntime
                                    .buildInteractiveShellArguments(
                                        rootfs =
                                            linuxRootfs,
                                        workspace =
                                            workspace,
                                        githubCredentialFile =
                                            credentialLease
                                                ?.credentialFile
                                    ),
                            environment =
                                environment,
                            workingDirectory =
                                appContext.filesDir
                                    .canonicalPath,
                            rows = rows,
                            columns = columns
                        )
                    } else {
                        linuxMode = false
                        closeGitCredentialLease()

                        val tmp =
                            File(
                                appContext.filesDir,
                                "terminal/pty-tmp"
                            ).apply {
                                mkdirs()
                            }

                        AppForgePtyBridge.spawn(
                            executable =
                                "/system/bin/sh",
                            arguments =
                                listOf("-i"),
                            environment =
                                mapOf(
                                    "HOME" to
                                        workspace.absolutePath,
                                    "TMPDIR" to
                                        tmp.absolutePath,
                                    "PATH" to
                                        "/system/bin:/system/xbin:/product/bin:/vendor/bin",
                                    "TERM" to
                                        "xterm-256color",
                                    "COLORTERM" to
                                        "truecolor",
                                    "LANG" to
                                        "C.UTF-8",
                                    "PS1" to
                                        "\$ "
                                ),
                            workingDirectory =
                                workspace.absolutePath,
                            rows = rows,
                            columns = columns
                        )
                    }

                processId =
                    spawned.processId

                inputDescriptor =
                    ParcelFileDescriptor
                        .adoptFd(
                            spawned.inputFd
                        )

                outputDescriptor =
                    ParcelFileDescriptor
                        .adoptFd(
                            spawned.outputFd
                        )

                controlDescriptor =
                    ParcelFileDescriptor
                        .adoptFd(
                            spawned.controlFd
                        )

                inputStream =
                    ParcelFileDescriptor
                        .AutoCloseInputStream(
                            requireNotNull(
                                inputDescriptor
                            )
                        )
                inputDescriptor = null

                outputStream =
                    ParcelFileDescriptor
                        .AutoCloseOutputStream(
                            requireNotNull(
                                outputDescriptor
                            )
                        )
                outputDescriptor = null

                val input =
                    requireNotNull(
                        inputStream
                    )

                readerJob =
                    scope.launch {
                        runCatching {
                            InputStreamReader(
                                input,
                                Charsets.UTF_8
                            ).use { reader ->
                                val buffer =
                                    CharArray(8_192)

                                while (running.get()) {
                                    val count =
                                        reader.read(buffer)

                                    if (count < 0) break

                                    if (count > 0) {
                                        onOutput(
                                            String(
                                                buffer,
                                                0,
                                                count
                                            )
                                        )
                                    }
                                }
                            }
                        }.onFailure {
                            /*
                             * close() first marks the session inactive, so descriptor-close
                             * errors during normal shutdown are ignored. An unexpected reader
                             * failure while active terminates the PTY and lets waiterJob own
                             * the final cleanup/onExit path.
                             */
                            if (running.get()) {
                                terminate()
                            }
                        }
                    }

                waiterJob =
                    scope.launch {
                        val exitCode =
                            AppForgePtyBridge
                                .waitFor(
                                    spawned.processId
                                )

                        val shouldNotifyExit =
                            running.getAndSet(false)

                        closeDescriptors()

                        if (shouldNotifyExit) {
                            onExit(exitCode)
                        }

                        closeGitCredentialLease()
                    }
            }
        } catch (error: Throwable) {
            running.set(false)
            closeDescriptors()
            closeGitCredentialLease()
            throw error
        }
    }

    suspend fun write(text: String) {
        if (
            text.isEmpty() ||
            !running.get()
        ) {
            return
        }

        withContext(Dispatchers.IO) {
            synchronized(this@LocalInteractivePtySession) {
                val stream =
                    outputStream
                        ?: return@synchronized

                stream.write(
                    text.toByteArray(
                        Charsets.UTF_8
                    )
                )
                stream.flush()
            }
        }
    }

    suspend fun resize(
        rows: Int,
        columns: Int
    ): Boolean =
        withContext(Dispatchers.IO) {
            val descriptor =
                controlDescriptor
                    ?: return@withContext false

            AppForgePtyBridge.resize(
                controlFd = descriptor.fd,
                rows = rows,
                columns = columns
            )
        }

    fun currentWorkingDirectory(): File? {
        if (linuxMode) {
            return null
        }

        val pid = processId
            ?: return null

        return runCatching {
            File("/proc/$pid/cwd")
                .canonicalFile
        }.getOrNull()
    }

    fun terminate() {
        processId?.let {
            AppForgePtyBridge
                .terminate(it)
        }
    }

    override fun close() {
        if (running.getAndSet(false)) {
            terminate()
        }

        readerJob?.cancel()
        waiterJob?.cancel()
        readerJob = null
        waiterJob = null

        closeDescriptors()
        closeGitCredentialLease()
        scope.cancel()
    }

    @Synchronized
    private fun closeGitCredentialLease() {
        runCatching {
            gitCredentialLease
                ?.close()
        }

        gitCredentialLease =
            null
    }

    @Synchronized
    private fun closeDescriptors() {
        runCatching {
            inputStream?.close()
        }
        inputStream = null

        runCatching {
            outputStream?.close()
        }
        outputStream = null

        runCatching {
            inputDescriptor?.close()
        }
        inputDescriptor = null

        runCatching {
            outputDescriptor?.close()
        }
        outputDescriptor = null

        runCatching {
            controlDescriptor?.close()
        }
        controlDescriptor = null

        processId = null
        linuxMode = false
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun LocalPtyTerminalPanel(
    sessions: List<TerminalSessionState>,
    activeSession: TerminalSessionState,
    workspaceRoot: File,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    onCancel: () -> Unit,
    onHistoryIndex: (Int) -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val allStates by
        LocalPtySessionRegistry.states
            .collectAsState()

    var activePtyId by
        remember(
            workspaceRoot.absolutePath
        ) {
            mutableStateOf<String?>(null)
        }

    var message by
        remember(
            workspaceRoot.absolutePath
        ) {
            mutableStateOf<String?>(null)
        }

    val initialUxPreferences =
        remember(context.applicationContext) {
            TerminalUxPreferences.load(
                context.applicationContext
            )
        }

    var terminalFontSizeSp by
        remember(workspaceRoot.absolutePath) {
            mutableStateOf(
                initialUxPreferences.fontSizeSp
            )
        }

    val environmentState by
        TerminalDevelopmentEnvironmentCoordinator
            .state
            .collectAsState()

    LaunchedEffect(
        context.applicationContext,
        workspaceRoot.absolutePath
    ) {
        TerminalDevelopmentEnvironmentCoordinator
            .ensure(
                context.applicationContext,
                workspaceRoot
            )
    }

    LaunchedEffect(
        environmentState.phase,
        workspaceRoot.absolutePath
    ) {
        if (
            environmentState.phase ==
                TerminalEnvironmentPhase.READY
        ) {
            runCatching {
                LocalPtySessionRegistry
                    .ensureStarted(
                        context.applicationContext,
                        workspaceRoot
                    )
            }.onSuccess {
                activePtyId = it
                message = null
            }.onFailure {
                message =
                    "Terminal başlatılamadı. Tekrar deneyin."
            }
        }
    }

    LaunchedEffect(
        terminalFontSizeSp
    ) {
        delay(
            FONT_PERSIST_DEBOUNCE_MS
        )

        TerminalUxPreferences
            .saveFontSize(
                context.applicationContext,
                terminalFontSizeSp
            )
    }

    if (
        environmentState.phase !=
            TerminalEnvironmentPhase.READY
    ) {
        TermuxEnvironmentGate(
            state = environmentState,
            onRetry = {
                TerminalDevelopmentEnvironmentCoordinator
                    .retry(
                        context.applicationContext,
                        workspaceRoot
                    )
            }
        )
        return
    }

    val workspacePath =
        remember(
            workspaceRoot.absolutePath
        ) {
            workspaceRoot
                .canonicalFile
                .absolutePath
        }

    val ptySessions =
        allStates
            .filter {
                it.workspacePath ==
                    workspacePath
            }

    LaunchedEffect(
        ptySessions.map {
            it.id
        }
    ) {
        if (
            activePtyId == null ||
            ptySessions.none {
                it.id == activePtyId
            }
        ) {
            activePtyId =
                ptySessions
                    .firstOrNull()
                    ?.id
        }
    }

    val active =
        ptySessions
            .firstOrNull {
                it.id == activePtyId
            }

    /*
     * Keep the terminal viewport measured independently from the IME.
     * Only the extra-key accessory bar follows the keyboard.
     */
    val imeInsets =
        WindowInsets.ime

    val accessoryDensity =
        LocalDensity.current

    /*
     * The terminal viewport deliberately stays full-size while Android's
     * keyboard overlays its lower portion. The shortcut row is translated
     * upward by this exact IME distance, so the scroll content must reserve
     * the same occluded distance.
     *
     * This does NOT participate in PTY viewport measurement/resizing.
     */
    val imeOcclusionPx =
        imeInsets.getBottom(
            accessoryDensity
        )

    val accessoryReservePx =
        if (
            active?.running == true
        ) {
            imeOcclusionPx
        } else {
            0
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(10.dp),
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState()
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            ptySessions.forEach { session ->
                val selected =
                    session.id ==
                        activePtyId

                val closeThisSession: () -> Unit = {
                    val closing =
                        session.id

                    LocalPtySessionRegistry
                        .closeSession(
                            closing
                        )

                    if (
                        activePtyId ==
                            closing
                    ) {
                        activePtyId =
                            ptySessions
                                .firstOrNull {
                                    it.id != closing
                                }
                                ?.id
                    }
                }

                if (selected) {
                    Button(
                        onClick = {
                            activePtyId =
                                session.id
                            LocalPtySessionRegistry
                                .markActivated(
                                    session.id
                                )
                        }
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                session.title,
                                fontSize = 12.sp
                            )

                            if (ptySessions.size > 1) {
                                Text(
                                    "×",
                                    modifier =
                                        Modifier
                                            .padding(start = 4.dp)
                                            .clickable {
                                                closeThisSession()
                                            }
                                            .padding(
                                                horizontal = 8.dp,
                                                vertical = 6.dp
                                            ),
                                    fontSize = 18.sp,
                                    fontWeight =
                                        FontWeight.Black
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            activePtyId =
                                session.id
                            LocalPtySessionRegistry
                                .markActivated(
                                    session.id
                                )
                        }
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                session.title,
                                fontSize = 12.sp
                            )

                            if (ptySessions.size > 1) {
                                Text(
                                    "×",
                                    modifier =
                                        Modifier
                                            .padding(start = 4.dp)
                                            .clickable {
                                                closeThisSession()
                                            }
                                            .padding(
                                                horizontal = 8.dp,
                                                vertical = 6.dp
                                            ),
                                    fontSize = 18.sp,
                                    fontWeight =
                                        FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            val id =
                                LocalPtySessionRegistry
                                    .createSession(
                                        context =
                                            context.applicationContext,
                                        workspace =
                                            workspaceRoot
                                    )

                            LocalPtySessionRegistry
                                .start(id)

                            id
                        }.onSuccess {
                            activePtyId = it
                            LocalPtySessionRegistry
                                .markActivated(it)
                            message = null
                        }.onFailure {
                            message =
                                it.message
                                    ?: "Yeni PTY oturumu açılamadı."
                        }
                    }
                }
            ) {
                Text(
                    "+ Oturum",
                    fontSize = 11.sp
                )
            }
        }

        message?.let {
            Text(
                TerminalSecretMasker
                    .redact(it),
                color =
                    TerminalWarning,
                fontSize = 9.sp
            )
        }

        if (active == null) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            Color(0xFF030609),
                            RoundedCornerShape(16.dp)
                        )
            ) {
                Text(
                    "Gerçek PTY hazırlanıyor…",
                    color =
                        TerminalMuted,
                    modifier =
                        Modifier.padding(12.dp),
                    fontFamily =
                        FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        } else {
            LocalPtySurface(
                state = active,
                fontSizeSp = terminalFontSizeSp,
                onFontSizeSpChange = { next ->
                    terminalFontSizeSp =
                        next.coerceIn(
                            8f,
                            18f
                        )
                },
                bottomContentPaddingPx =
                    accessoryReservePx,
                modifier =
                    Modifier.weight(1f)
            )
        }

        active?.let { state ->
            if (state.running) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = -imeInsets.getBottom(this)
                                )
                            }
                            .horizontalScroll(
                                rememberScrollState()
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(4.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    PtyKey("ESC", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u001b"
                            )
                        }
                    }
                    PtyKey("TAB", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\t"
                            )
                        }
                    }
                    PtyKey("CTRL+C", true) {
                        scope.launch {
                            LocalPtySessionRegistry
                                .sendControlC(
                                    state.id
                                )
                        }
                    }
                    PtyKey("CTRL+L", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u000c"
                            )
                        }
                    }
                    PtyKey("CTRL+A", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u0001"
                            )
                        }
                    }
                    PtyKey("CTRL+E", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u0005"
                            )
                        }
                    }
                    PtyKey("CTRL+R", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u0012"
                            )
                        }
                    }
                    PtyKey("CTRL+U", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u0015"
                            )
                        }
                    }
                    PtyKey("CTRL+W", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u0017"
                            )
                        }
                    }
                    PtyKey("⌫", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u007f"
                            )
                        }
                    }
                    PtyKey("↵", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\r"
                            )
                        }
                    }
                    PtyKey("←", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u001b[D"
                            )
                        }
                    }
                    PtyKey("↑", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u001b[A"
                            )
                        }
                    }
                    PtyKey("↓", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u001b[B"
                            )
                        }
                    }
                    PtyKey("→", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u001b[C"
                            )
                        }
                    }
                    PtyKey("pwd", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "pwd\r"
                            )
                        }
                    }
                    PtyKey("ls", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "ls -la\r"
                            )
                        }
                    }
                    PtyKey("clear", true) {
                        LocalPtySessionRegistry
                            .clearBuffer(
                                state.id
                            )
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "\u000c"
                            )
                        }
                    }
                    PtyKey(
                        "A−",
                        terminalFontSizeSp > 8f
                    ) {
                        terminalFontSizeSp =
                            (terminalFontSizeSp - 1f)
                                .coerceAtLeast(8f)
                    }
                    PtyKey(
                        "A+",
                        terminalFontSizeSp < 18f
                    ) {
                        terminalFontSizeSp =
                            (terminalFontSizeSp + 1f)
                                .coerceAtMost(18f)
                    }
                    PtyKey("Tanıla", true) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                "printf '=== APPFORGE PTY SELF TEST ===\\n'; " +
                                    "printf 'TERM=%s\\n' \"\$TERM\"; stty size; pwd; " +
                                    "for c in sh curl unzip tar gzip sed awk grep find xargs git ssh python3 node npm java gradle clang cmake; do " +
                                    "command -v \"\$c\" >/dev/null 2>&1 && " +
                                    "printf '%-8s PASS\\n' \"\$c\" || " +
                                    "printf '%-8s MISS\\n' \"\$c\"; done\r"
                            )
                        }
                    }
                }
            } else {
                PtyKey(
                    "Yeniden Başlat",
                    !state.starting,
                    Modifier.fillMaxWidth()
                ) {
                    scope.launch {
                        runCatching {
                            LocalPtySessionRegistry
                                .start(
                                    state.id
                                )
                        }.onFailure {
                            message =
                                "Terminal yeniden başlatılamadı."
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun TermuxEnvironmentGate(
    state: TerminalEnvironmentState,
    onRetry: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(10.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        Color(0xFF030609),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(18.dp),
            contentAlignment =
                Alignment.Center
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "AppForge Terminal",
                    color = TerminalText,
                    fontFamily =
                        FontFamily.Monospace,
                    fontWeight =
                        FontWeight.Black,
                    fontSize = 16.sp
                )

                Text(
                    state.detail,
                    color =
                        if (
                            state.phase ==
                                TerminalEnvironmentPhase.ERROR
                        ) {
                            TerminalWarning
                        } else {
                            TerminalSecondary
                        },
                    fontSize = 11.sp
                )

                if (
                    state.phase ==
                        TerminalEnvironmentPhase.IDLE ||
                    state.phase ==
                        TerminalEnvironmentPhase.PREPARING
                ) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    state.percent?.let { percent ->
                        Text(
                            "%$percent",
                            color =
                                TerminalPrimary,
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }

                if (
                    state.phase ==
                        TerminalEnvironmentPhase.ERROR
                ) {
                    Button(
                        onClick = onRetry
                    ) {
                        Text("Tekrar Dene")
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPtySurface(
    state: LocalPtyTerminalState,
    fontSizeSp: Float,
    onFontSizeSpChange: (Float) -> Unit,
    bottomContentPaddingPx: Int = 0,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember(state.id) { FocusRequester() }
    val tapInteraction = remember(state.id) { MutableInteractionSource() }
    val outputScroll = rememberScrollState()

    var selectionEpoch by remember(state.id) { mutableStateOf(0) }
    var imeValue by
        remember(state.id) {
            mutableStateOf(
                TextFieldValue(
                    text =
                        LOCAL_PTY_IME_SENTINEL,
                    selection =
                        TextRange(
                            LOCAL_PTY_IME_SENTINEL.length
                        )
                )
            )
        }
    var pinchFontSizeSp by remember(state.id) { mutableStateOf(fontSizeSp) }
    var surfaceSize by remember(state.id) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(fontSizeSp) {
        pinchFontSizeSp = fontSizeSp
    }

    val fontSize = fontSizeSp.sp
    val lineHeight = (fontSizeSp * 1.4f).sp
    val charWidthPx = with(density) { fontSize.toPx() * 0.72f }
    val lineHeightPx = with(density) { lineHeight.toPx() }

    val bottomContentPadding =
        12.dp +
            with(density) {
                bottomContentPaddingPx
                    .coerceAtLeast(0)
                    .toDp()
            }

    val rendered =
        remember(state.snapshot, state.running) {
            renderLocalPtySnapshot(
                state.snapshot,
                state.running
            )
        }

    /*
     * Follow the active terminal cursor.
     *
     * outputRevision arrives before Compose necessarily recalculates the
     * scroll range. Give layout one frame, then move to the real bottom.
     * Changing the accessory reserve must also reveal the prompt above
     * the keyboard shortcut row.
     */
    LaunchedEffect(
        state.outputRevision,
        bottomContentPaddingPx
    ) {
        delay(16L)
        outputScroll.scrollTo(
            outputScroll.maxValue
        )
    }

    /*
     * IME/layout changes can update maxValue after the output effect above.
     * Follow that final range as well so the cursor cannot remain inside
     * the keyboard-occluded portion of the full-size terminal viewport.
     */
    LaunchedEffect(
        outputScroll.maxValue
    ) {
        if (
            outputScroll.maxValue > 0
        ) {
            outputScroll.scrollTo(
                outputScroll.maxValue
            )
        }
    }

    LaunchedEffect(
        surfaceSize,
        fontSizeSp,
        state.id
    ) {
        if (
            surfaceSize.width <= 0 ||
            surfaceSize.height <= 0
        ) {
            return@LaunchedEffect
        }

        delay(
            RESIZE_DEBOUNCE_MS
        )

        val horizontalPaddingPx =
            with(density) {
                28.dp.toPx()
            }

        val verticalPaddingPx =
            with(density) {
                24.dp.toPx()
            }

        val availableWidthPx =
            (
                surfaceSize.width -
                    horizontalPaddingPx
                )
                .coerceAtLeast(
                    charWidthPx
                )

        val availableHeightPx =
            (
                surfaceSize.height -
                    verticalPaddingPx
                )
                .coerceAtLeast(
                    lineHeightPx
                )

        val columns =
            (availableWidthPx / charWidthPx)
                .toInt()
                .coerceIn(20, 240)

        val rows =
            (availableHeightPx / lineHeightPx)
                .toInt()
                .coerceIn(8, 80)

        LocalPtySessionRegistry.resize(
            state.id,
            rows,
            columns
        )
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .background(
                    Color(0xFF030609),
                    RoundedCornerShape(16.dp)
                )
                .onSizeChanged { size ->
                    surfaceSize = size
                }
                .pointerInput(state.id) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            val next =
                                (pinchFontSizeSp * zoom)
                                    .coerceIn(8f, 18f)
                            if (next != pinchFontSizeSp) {
                                pinchFontSizeSp = next
                                onFontSizeSpChange(next)
                            }
                        }
                    }
                }
                .clickable(
                    interactionSource = tapInteraction,
                    indication = null
                ) {
                    selectionEpoch += 1
                    inputFocusRequester.requestFocus()
                    keyboardController?.show()
                }
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides
                TextSelectionColors(
                    handleColor = TerminalPrimary,
                    backgroundColor = TerminalPrimary.copy(alpha = 0.28f)
                )
        ) {
            key(selectionEpoch) {
                SelectionContainer {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    outputScroll
                                )
                                .padding(
                                    start = 16.dp,
                                    top = 12.dp,
                                    end = 12.dp
                                )
                    ) {
                        Text(
                            rendered,
                            color = TerminalText,
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier =
                                Modifier.fillMaxWidth()
                        )

                        /*
                         * This is real scrollable content beneath the prompt.
                         * When the IME accessory floats upward, the prompt can
                         * therefore scroll completely above the shortcut row.
                         */
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        bottomContentPadding
                                    )
                        )
                    }
                }
            }
        }

        /* 1dp IME capture cannot steal long-press output selection. */
        CompositionLocalProvider(
            LocalTextSelectionColors provides
                TextSelectionColors(
                    handleColor = Color.Transparent,
                    backgroundColor = Color.Transparent
                )
        ) {
            BasicTextField(
                value = imeValue,
                onValueChange = { next ->
                    if (!state.running) {
                        imeValue =
                            TextFieldValue(
                                text =
                                    LOCAL_PTY_IME_SENTINEL,
                                selection =
                                    TextRange(
                                        LOCAL_PTY_IME_SENTINEL.length
                                    )
                            )
                        return@BasicTextField
                    }

                    val delta =
                        localPtyImeDeltaWithSentinel(
                            previous =
                                imeValue.text,
                            next =
                                next.text
                        )

                    imeValue =
                        localPtyImeValue(
                            next
                        )

                    if (delta.isNotEmpty()) {
                        scope.launch {
                            LocalPtySessionRegistry.write(
                                state.id,
                                delta
                            )
                        }
                    }
                },
                enabled = state.running,
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType =
                            KeyboardType.Text
                    ),
                textStyle =
                    TextStyle(
                        color = Color.Transparent,
                        fontSize = 1.sp
                    ),
                cursorBrush = SolidColor(Color.Transparent),
                modifier =
                    Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(
                            inputFocusRequester
                        )
            )
        }
    }
}

private fun renderLocalPtySnapshot(
    snapshot: AnsiTerminalSnapshot,
    showCursor: Boolean
): AnnotatedString =
    buildAnnotatedString {
        snapshot.lines.forEachIndexed { lineIndex, line ->
            val cursorColumn =
                if (
                    showCursor &&
                    snapshot.cursorVisible &&
                    lineIndex == snapshot.cursorLine
                ) {
                    snapshot.cursorColumn.coerceAtLeast(0)
                } else {
                    -1
                }

            val lastContentColumn =
                line.indexOfLast {
                    it.character != ' '
                }

            val lastColumn =
                maxOf(
                    lastContentColumn,
                    cursorColumn
                )

            if (lastColumn >= 0) {
                for (column in 0..lastColumn) {
                    if (column == cursorColumn) {
                        append('▌')
                    }

                    if (column < line.size) {
                        val cell = line[column]
                        val style = cell.style

                        val defaultFg = TerminalText
                        val defaultBg = Color(0xFF030609)
                        val explicitFg =
                            style.foregroundRgb
                                ?.let(::localAnsiColor)
                        val explicitBg =
                            style.backgroundRgb
                                ?.let(::localAnsiColor)

                        val fg =
                            if (style.inverse) {
                                explicitBg ?: defaultBg
                            } else {
                                explicitFg ?: defaultFg
                            }

                        val bg =
                            if (style.inverse) {
                                explicitFg ?: defaultFg
                            } else {
                                explicitBg
                            }

                        withStyle(
                            SpanStyle(
                                color = fg,
                                background =
                                    bg ?: Color.Unspecified,
                                fontWeight =
                                    if (style.bold) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                textDecoration =
                                    if (style.underline) {
                                        TextDecoration.Underline
                                    } else {
                                        TextDecoration.None
                                    }
                            )
                        ) {
                            append(cell.character)
                        }
                    }
                }
            }

            if (lineIndex < snapshot.lines.lastIndex) {
                append('\n')
            }
        }
    }

private fun localAnsiColor(rgb: Int): Color =
    Color(
        0xFF000000L or
            (rgb.toLong() and 0x00FFFFFFL)
    )

private fun localPtyImeDeltaWithSentinel(
    previous: String,
    next: String
): String {
    if (
        previous ==
            LOCAL_PTY_IME_SENTINEL &&
        next.isEmpty()
    ) {
        return "\u007f"
    }

    val previousPayload =
        previous.removePrefix(
            LOCAL_PTY_IME_SENTINEL
        )

    val nextPayload =
        next.removePrefix(
            LOCAL_PTY_IME_SENTINEL
        )

    if (
        nextPayload.lastOrNull()
            ?.isHighSurrogate() ==
            true
    ) {
        return ""
    }

    if (
        previousPayload.lastOrNull()
            ?.isHighSurrogate() ==
            true
    ) {
        val pending =
            previousPayload.last()

        if (
            nextPayload.length ==
                previousPayload.length + 1 &&
            nextPayload.startsWith(
                previousPayload
            ) &&
            nextPayload.lastOrNull()
                ?.isLowSurrogate() ==
                true
        ) {
            return buildString {
                append(pending)
                append(
                    nextPayload.last()
                )
            }
        }

        return localPtyImeDelta(
            previous =
                previousPayload.dropLast(1),
            next =
                nextPayload
                    .removeSuffix(
                        pending.toString()
                    )
        )
    }

    return localPtyImeDelta(
        previous = previousPayload,
        next = nextPayload
    )
}

private fun localPtyImeValue(
    next: TextFieldValue
): TextFieldValue {
    val shadow =
        localPtyImeShadow(
            next.text
        )

    /*
     * Gboard composition/selection state must survive normal edits.
     * Rebuilding a String-only field here can make Backspace replay
     * the current composing character instead of deleting it.
     */
    if (
        shadow ==
            next.text
    ) {
        return next
    }

    return TextFieldValue(
        text = shadow,
        selection =
            TextRange(
                shadow.length
            )
    )
}

private fun localPtyImeShadow(
    next: String
): String {
    val payload =
        next.removePrefix(
            LOCAL_PTY_IME_SENTINEL
        )

    return if (
        payload.contains('\n') ||
        payload.contains('\r') ||
        payload.length >
            MAX_LOCAL_PTY_IME_CHARS
    ) {
        LOCAL_PTY_IME_SENTINEL
    } else {
        LOCAL_PTY_IME_SENTINEL +
            payload
    }
}

private fun localPtyImeDelta(
    previous: String,
    next: String
): String {
    var prefix = 0

    val maxPrefix =
        minOf(
            previous.length,
            next.length
        )

    while (
        prefix < maxPrefix &&
        previous[prefix] ==
            next[prefix]
    ) {
        prefix += 1
    }

    if (
        prefix > 0 &&
        prefix < previous.length &&
        previous[prefix - 1].isHighSurrogate() &&
        previous[prefix].isLowSurrogate()
    ) {
        prefix -= 1
    }

    val removedText =
        previous.substring(prefix)

    val removed =
        removedText.codePointCount(
            0,
            removedText.length
        )

    return buildString {
        repeat(removed) {
            append('\u007f')
        }

        append(
            next.substring(prefix)
                .replace(
                    "\r\n",
                    "\n"
                )
                .replace(
                    '\r',
                    '\n'
                )
        )
    }
}

@Composable
private fun PtyKey(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier =
            modifier.heightIn(min = 48.dp),
        contentPadding =
            PaddingValues(
                horizontal = 3.dp,
                vertical = 4.dp
            )
    ) {
        Text(
            label,
            fontFamily =
                FontFamily.Monospace,
            fontWeight =
                FontWeight.Bold,
            fontSize =
                when (label) {
                    "⌫",
                    "↵",
                    "↑",
                    "↓",
                    "←",
                    "→" -> 18.sp

                    "ESC",
                    "TAB",
                    "pwd",
                    "ls",
                    "A−",
                    "A+" -> 14.sp

                    else -> 13.sp
                }
        )
    }
}

private const val LOCAL_PTY_IME_SENTINEL =
    "\u2063"

private const val MAX_LOCAL_PTY_IME_CHARS =
    2_048

private const val RESIZE_DEBOUNCE_MS =
    140L

private const val FONT_PERSIST_DEBOUNCE_MS =
    250L
