package com.appforge.studio.terminal

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val snapshot: AnsiTerminalSnapshot
)

internal object LocalPtySessionRegistry {
    private data class Record(
        val id: String,
        val title: String,
        val workspace: File,
        val session: LocalInteractivePtySession,
        val buffer: AnsiTerminalBuffer,
        var running: Boolean,
        var starting: Boolean,
        var exitCode: Int?,
        var rows: Int,
        var columns: Int,
        var lastActivatedAt: Long
    )

    private val lock = Any()
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
            .sortedByDescending {
                it.lastActivatedAt
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
                records.values.count {
                    it.workspace ==
                        safeWorkspace
                } + 1

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
                        System.currentTimeMillis()
                )

            publishLocked()
            return id
        }
    }

    suspend fun ensureStarted(
        context: Context,
        workspace: File
    ): String {
        val id =
            ensureSession(
                context,
                workspace
            )

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

        markActivated(id)
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
                current.buffer.reset()
                publishLocked()
                current
            }

        try {
            record.session.start(
                workspace =
                    record.workspace,
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
                        publishLocked()
                    }
                },
                onExit = { exitCode ->
                    synchronized(lock) {
                        records[id]?.let {
                            it.running = false
                            it.starting = false
                            it.exitCode =
                                exitCode
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
                        snapshot =
                            it.buffer.snapshot()
                    )
                }
    }

    private const val MAX_LOCAL_PTY_SESSIONS =
        6
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
                val tmp =
                    File(
                        appContext.filesDir,
                        "terminal/pty-tmp"
                    ).apply {
                        mkdirs()
                    }

                val spawned =
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
                        InputStreamReader(
                            input,
                            Charsets.UTF_8
                        ).use { reader ->
                            val buffer =
                                CharArray(2_048)

                            while (
                                running.get()
                            ) {
                                val count =
                                    reader.read(
                                        buffer
                                    )

                                if (count < 0) {
                                    break
                                }

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
                    }

                waiterJob =
                    scope.launch {
                        val exitCode =
                            AppForgePtyBridge
                                .waitFor(
                                    spawned.processId
                                )

                        running.set(false)
                        closeDescriptors()
                        onExit(exitCode)
                    }
            }
        } catch (error: Throwable) {
            running.set(false)
            closeDescriptors()
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
        scope.cancel()
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

    LaunchedEffect(
        context.applicationContext,
        workspaceRoot.absolutePath
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
                it.message
                    ?: "PTY başlatılamadı."
        }
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
            .sortedByDescending {
                it.lastActivatedAt
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
                if (
                    session.id ==
                    activePtyId
                ) {
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
                        Text(
                            session.title,
                            fontSize = 11.sp
                        )
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
                        Text(
                            session.title,
                            fontSize = 11.sp
                        )
                    }
                }

                if (
                    ptySessions.size > 1 &&
                    session.id ==
                        activePtyId
                ) {
                    TextButton(
                        onClick = {
                            val closing =
                                session.id

                            LocalPtySessionRegistry
                                .closeSession(
                                    closing
                                )

                            activePtyId =
                                ptySessions
                                    .firstOrNull {
                                        it.id !=
                                            closing
                                    }
                                    ?.id
                        }
                    ) {
                        Text("×")
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

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                workspaceRoot.absolutePath,
                modifier =
                    Modifier.weight(1f),
                color =
                    TerminalPrimary,
                fontFamily =
                    FontFamily.Monospace,
                fontSize = 9.sp,
                maxLines = 1
            )

            Text(
                when {
                    active?.starting == true ->
                        "Başlatılıyor"
                    active?.running == true ->
                        "PTY"
                    active?.exitCode != null ->
                        "Çıkış ${active.exitCode}"
                    else ->
                        "Hazır"
                },
                color =
                    if (
                        active?.running ==
                        true
                    ) {
                        TerminalPrimary
                    } else {
                        TerminalWarning
                    },
                fontSize = 9.sp
            )
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
                modifier =
                    Modifier.weight(1f)
            )
        }

        active?.let { state ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(5.dp)
            ) {
                PtyKey("ESC", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b"
                            )
                    }
                }

                PtyKey("TAB", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\t"
                            )
                    }
                }

                PtyKey("CTRL+C", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .sendControlC(
                                state.id
                            )
                    }
                }

                PtyKey("⌫", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\u007f"
                            )
                    }
                }

                PtyKey("↵", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\r"
                            )
                    }
                }

                PtyKey("↑", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[A"
                            )
                    }
                }

                PtyKey("↓", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[B"
                            )
                    }
                }

                PtyKey("←", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[D"
                            )
                    }
                }

                PtyKey("→", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[C"
                            )
                    }
                }

                PtyKey("pwd", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "pwd\r"
                            )
                    }
                }

                PtyKey("ls", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "ls -la\r"
                            )
                    }
                }

                PtyKey("clear", state.running) {
                    scope.launch {
                        LocalPtySessionRegistry
                            .write(
                                state.id,
                                "\u000c"
                            )
                    }
                }

                if (!state.running) {
                    PtyKey(
                        "Yeniden Başlat",
                        !state.starting
                    ) {
                        scope.launch {
                            runCatching {
                                LocalPtySessionRegistry
                                    .start(
                                        state.id
                                    )
                            }.onFailure {
                                message =
                                    it.message
                                        ?: "PTY yeniden başlatılamadı."
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPtySurface(
    state: LocalPtyTerminalState,
    modifier: Modifier = Modifier
) {
    val scope =
        rememberCoroutineScope()

    val density =
        LocalDensity.current

    val outputScroll =
        rememberScrollState()

    var imeShadow by
        remember(state.id) {
            mutableStateOf(
                LOCAL_PTY_IME_SENTINEL
            )
        }

    val fontSize =
        11.sp

    val lineHeight =
        15.sp

    val charWidthPx =
        with(density) {
            fontSize.toPx() *
                0.72f
        }

    val lineHeightPx =
        with(density) {
            lineHeight.toPx()
        }

    val rendered =
        remember(
            state.snapshot,
            state.running
        ) {
            renderLocalPtySnapshot(
                state.snapshot,
                state.running
            )
        }

    LaunchedEffect(
        rendered.length,
        outputScroll.maxValue
    ) {
        outputScroll.scrollTo(
            outputScroll.maxValue
        )
    }

    BasicTextField(
        value = imeShadow,
        onValueChange = { next ->
            if (!state.running) {
                imeShadow =
                    LOCAL_PTY_IME_SENTINEL
                return@BasicTextField
            }

            val delta =
                localPtyImeDeltaWithSentinel(
                    previous = imeShadow,
                    next = next
                )

            imeShadow =
                localPtyImeShadow(next)

            if (delta.isNotEmpty()) {
                scope.launch {
                    LocalPtySessionRegistry
                        .write(
                            state.id,
                            delta
                        )
                }
            }
        },
        enabled = state.running,
        textStyle =
            TextStyle(
                color =
                    Color.Transparent,
                fontSize = 1.sp
            ),
        cursorBrush =
            SolidColor(
                Color.Transparent
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .background(
                    Color(0xFF030609),
                    RoundedCornerShape(16.dp)
                )
                .onSizeChanged { size ->
                    val columns =
                        (
                            size.width /
                                charWidthPx
                            )
                            .toInt()
                            .coerceIn(
                                20,
                                240
                            )

                    val rows =
                        (
                            size.height /
                                lineHeightPx
                            )
                            .toInt()
                            .coerceIn(
                                8,
                                80
                            )

                    if (
                        columns !=
                            state.columns ||
                        rows !=
                            state.rows
                    ) {
                        scope.launch {
                            LocalPtySessionRegistry
                                .resize(
                                    state.id,
                                    rows,
                                    columns
                                )
                        }
                    }
                },
        decorationBox = { innerTextField ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp)
            ) {
                Text(
                    rendered,
                    color =
                        TerminalText,
                    fontFamily =
                        FontFamily.Monospace,
                    fontSize =
                        fontSize,
                    lineHeight =
                        lineHeight,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                outputScroll
                            )
                )

                Box(
                    modifier =
                        Modifier
                            .size(1.dp)
                            .alpha(0f)
                ) {
                    innerTextField()
                }
            }
        }
    )
}

private fun renderLocalPtySnapshot(
    snapshot: AnsiTerminalSnapshot,
    showCursor: Boolean
): String {
    val lines =
        snapshot.lines
            .map { line ->
                line.joinToString("") {
                    it.character
                        .toString()
                }.trimEnd()
            }
            .toMutableList()

    if (
        showCursor &&
        snapshot.cursorVisible
    ) {
        while (
            lines.size <=
            snapshot.cursorLine
        ) {
            lines.add("")
        }

        val lineIndex =
            snapshot.cursorLine
                .coerceIn(
                    0,
                    lines.lastIndex
                )

        val original =
            lines[lineIndex]

        val column =
            snapshot.cursorColumn
                .coerceAtLeast(0)

        val padded =
            original.padEnd(
                column,
                ' '
            )

        val safeColumn =
            column.coerceAtMost(
                padded.length
            )

        lines[lineIndex] =
            padded.substring(
                0,
                safeColumn
            ) +
                "▌" +
                padded.substring(
                    safeColumn
                )
    }

    return lines
        .joinToString("\n")
        .trimEnd()
        .ifEmpty {
            if (showCursor) {
                "▌"
            } else {
                " "
            }
        }
}

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

    return localPtyImeDelta(
        previous =
            previous.removePrefix(
                LOCAL_PTY_IME_SENTINEL
            ),
        next =
            next.removePrefix(
                LOCAL_PTY_IME_SENTINEL
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

    val removed =
        previous.length -
            prefix

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
    onClick: () -> Unit
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick
    ) {
        Text(
            label,
            fontFamily =
                FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}

private const val LOCAL_PTY_IME_SENTINEL =
    "\u2063"

private const val MAX_LOCAL_PTY_IME_CHARS =
    2_048
