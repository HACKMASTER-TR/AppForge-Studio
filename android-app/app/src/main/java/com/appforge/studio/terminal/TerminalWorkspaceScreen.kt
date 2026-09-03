package com.appforge.studio.terminal

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.io.ProjectLibrary
import com.appforge.studio.io.SavedProject
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.security.SecureAccountStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private enum class TerminalWorkspaceTab(
    val title: String,
    val icon: String
) {
    TERMINAL("Terminal", ">_"),
    FILES("Dosyalar", "▤"),
    GIT("Git", "⑂"),
    CONNECTIONS("Bağlantılar", "◎"),
    SSH("SSH", "⌁"),
    TOOLS("Araçlar", "◆")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalWorkspaceScreen(
    activeProjectId: String?,
    activeDraft: ProjectDraft,
    railwayAuthorizationUri: Uri?,
    railwayAuthorizationSequence: Int,
    onRailwayAuthorizationConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenBuilder: (String?) -> Unit,
    onOpenAi: (String?) -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val projects =
        remember {
            ProjectLibrary.load(context)
        }

    var selectedProjectId by
        rememberSaveable {
            mutableStateOf(
                activeProjectId
                    ?: if (
                        activeDraft.appName.isNotBlank() ||
                        !activeDraft.importedFolder.isNullOrBlank()
                    ) {
                        null
                    } else {
                        projects
                            .firstOrNull()
                            ?.id
                    }
            )
        }

    val selectedProject =
        projects.firstOrNull {
            it.id == selectedProjectId
        }

    val selectedDraft =
        remember(
            selectedProjectId,
            activeProjectId,
            activeDraft.importedFolder,
            activeDraft.packageName
        ) {
            when {
                selectedProjectId != null ->
                    ProjectLibrary.restore(
                        context,
                        requireNotNull(
                            selectedProjectId
                        )
                    )

                activeProjectId == null &&
                    (
                        activeDraft.importedFolder != null ||
                            activeDraft.appName.isNotBlank()
                    ) ->
                    activeDraft

                else ->
                    null
            }
        }

    val workspace =
        remember(
            selectedProjectId,
            selectedDraft?.importedFolder
        ) {
            TerminalWorkspaceResolver.resolve(
                context = context,
                projectId = selectedProjectId,
                draft = selectedDraft
            )
        }

    val workspaceHistoryKey =
        selectedProjectId
            ?: "scratch"

    val localEngine =
        remember {
            LocalTerminalEngine(
                context.filesDir
            )
        }

    val sshClient =
        remember {
            SshTerminalClient(
                context.applicationContext
            )
        }

    var sessionNumber by
        remember(workspace.absolutePath) {
            mutableIntStateOf(1)
        }

    fun initialSession(
        number: Int
    ) =
        TerminalSessionState(
            id =
                UUID.randomUUID()
                    .toString(),
            title =
                "Terminal $number",
            workingDirectory =
                workspace,
            lines =
                listOf(
                    TerminalLine(
                        "AppForge Terminal hazır.",
                        TerminalLineKind.SUCCESS
                    ),
                    TerminalLine(
                        "Komutları görmek için appforge help yazın.",
                        TerminalLineKind.INFO
                    )
                ),
            history =
                TerminalHistoryStore.load(
                    context,
                    workspaceHistoryKey
                )
        )

    var sessions by
        remember(workspace.absolutePath) {
            mutableStateOf(
                listOf(
                    initialSession(1)
                )
            )
        }

    var activeSessionId by
        remember(workspace.absolutePath) {
            mutableStateOf(
                sessions.first().id
            )
        }

    val activeSession =
        sessions.firstOrNull {
            it.id == activeSessionId
        } ?: sessions.first()

    var selectedTab by
        rememberSaveable {
            mutableStateOf(
                TerminalWorkspaceTab.TERMINAL
            )
        }

    LaunchedEffect(
        railwayAuthorizationSequence
    ) {
        if (railwayAuthorizationUri != null) {
            selectedTab =
                TerminalWorkspaceTab.CONNECTIONS
        }
    }

    var pendingDangerousCommand by
        remember {
            mutableStateOf<Pair<String, String>?>(
                null
            )
        }

    val commandJobs =
        remember {
            mutableMapOf<String, Job>()
        }

    fun updateSession(
        sessionId: String,
        update: (TerminalSessionState) -> TerminalSessionState
    ) {
        sessions =
            sessions.map { session ->
                if (session.id == sessionId) {
                    update(session)
                } else {
                    session
                }
            }
    }

    fun appendLines(
        sessionId: String,
        text: String,
        kind: TerminalLineKind
    ) {
        val clean =
            TerminalTextSanitizer.clean(text)

        val lines =
            clean
                .split('\n')
                .map {
                    TerminalLine(
                        it,
                        kind
                    )
                }

        updateSession(sessionId) { session ->
            session.copy(
                lines =
                    session.lines
                        .plus(lines)
                        .takeLast(
                            MAX_TERMINAL_LINES
                        )
            )
        }
    }

    fun appForgeHelp(): String =
        """
        AppForge özel komutları
        • appforge status     Proje ve çalışma alanı bilgisi
        • appforge projects   Kayıtlı projeleri listele
        • appforge build      Builder / APK-AAB ekranını aç
        • appforge ai         Projeye bağlı AI Asistanı aç
        • appforge connect    GitHub / Railway bağlantılarını aç
        • runtime             Yerel araç durumunu göster

        Yerel kabuk örnekleri
        • pwd, ls -la, find ., cat dosya.txt
        • mkdir klasor, touch dosya.txt

        Git komutları
        • git init, git status, git add ., git log
        • Commit, pull, push ve clone için Git sekmesini kullanın.
        """.trimIndent()

    fun runCommand(
        sessionId: String,
        rawCommand: String,
        confirmed: Boolean = false
    ) {
        val command =
            rawCommand.trim()

        val session =
            sessions.firstOrNull {
                it.id == sessionId
            } ?: return

        if (session.running) {
            appendLines(
                sessionId,
                "Bu oturumda bir komut zaten çalışıyor.",
                TerminalLineKind.WARNING
            )

            return
        }

        val review =
            TerminalCommandPolicy.review(
                command
            )

        if (!review.allowed) {
            appendLines(
                sessionId,
                review.message,
                TerminalLineKind.ERROR
            )

            return
        }

        if (
            review.requiresConfirmation &&
            !confirmed
        ) {
            pendingDangerousCommand =
                sessionId to command

            return
        }

        val history =
            TerminalHistoryStore.add(
                context,
                workspaceHistoryKey,
                command
            )

        updateSession(sessionId) {
            it.copy(
                running = true,
                history = history,
                historyIndex = history.size,
                lines =
                    it.lines
                        .plus(
                            TerminalLine(
                                "\$ $command",
                                TerminalLineKind.PROMPT
                            )
                        )
                        .takeLast(
                            MAX_TERMINAL_LINES
                        )
            )
        }

        val job =
            scope.launch {
                try {
                    when {
                        command == "clear" ->
                            updateSession(sessionId) {
                                it.copy(
                                    lines = emptyList()
                                )
                            }

                        command == "appforge help" ||
                            command == "help" ->
                            appendLines(
                                sessionId,
                                appForgeHelp(),
                                TerminalLineKind.INFO
                            )

                        command == "appforge status" -> {
                            val draft =
                                selectedDraft

                            appendLines(
                                sessionId,
                                buildString {
                                    append("Proje: ")
                                    append(
                                        draft
                                            ?.appName
                                            ?.ifBlank {
                                                selectedProject
                                                    ?.name
                                                    ?: "Genel çalışma alanı"
                                            }
                                            ?: "Genel çalışma alanı"
                                    )
                                    append("\nPaket: ")
                                    append(
                                        draft
                                            ?.packageName
                                            ?: "—"
                                    )
                                    append("\nTeknoloji: ")
                                    append(
                                        draft
                                            ?.sourceTechnologyLabel
                                            ?: "—"
                                    )
                                    append("\nKlasör: ")
                                    append(
                                        workspace.absolutePath
                                    )
                                },
                                TerminalLineKind.INFO
                            )
                        }

                        command == "appforge projects" ->
                            appendLines(
                                sessionId,
                                if (projects.isEmpty()) {
                                    "Kayıtlı proje yok."
                                } else {
                                    projects.joinToString("\n") {
                                        "• ${it.name}  (${it.packageName})"
                                    }
                                },
                                TerminalLineKind.INFO
                            )

                        command == "appforge build" -> {
                            appendLines(
                                sessionId,
                                "Builder ekranı açılıyor…",
                                TerminalLineKind.SUCCESS
                            )

                            onOpenBuilder(
                                selectedProjectId
                            )
                        }

                        command == "appforge ai" -> {
                            appendLines(
                                sessionId,
                                "AppForge AI açılıyor…",
                                TerminalLineKind.SUCCESS
                            )

                            onOpenAi(
                                selectedProjectId
                            )
                        }

                        command == "runtime" -> {
                            appendLines(
                                sessionId,
                                "Araç durumu ekranı açıldı.",
                                TerminalLineKind.INFO
                            )

                            selectedTab =
                                TerminalWorkspaceTab.TOOLS
                        }

                        command == "appforge connect" ||
                            command == "connections" -> {
                            appendLines(
                                sessionId,
                                "Hesap bağlantıları açıldı.",
                                TerminalLineKind.INFO
                            )

                            selectedTab =
                                TerminalWorkspaceTab.CONNECTIONS
                        }

                        command.startsWith("git ") ||
                            command == "git" -> {
                            val commitMatch =
                                Regex(
                                    "^git\\s+commit\\s+-m\\s+(['\"])(.*?)\\1$",
                                    setOf(
                                        RegexOption.IGNORE_CASE,
                                        RegexOption.DOT_MATCHES_ALL
                                    )
                                ).matchEntire(command)

                            val gitResult =
                                when {
                                    commitMatch != null ->
                                        GitWorkspaceService.commit(
                                            workspace = workspace,
                                            message =
                                                commitMatch
                                                    .groupValues[2],
                                            authorName =
                                                "AppForge User",
                                            authorEmail =
                                                "appforge@local"
                                        )

                                    else ->
                                        GitWorkspaceService
                                            .executeTerminalCommand(
                                                workspace,
                                                command,
                                                SecureAccountStore
                                                    .loadExternalConnection(
                                                        context,
                                                        "github"
                                                    )
                                                    ?.let {
                                                        GitCredentials(
                                                            username =
                                                                it.accountLabel
                                                                    .substringBefore(
                                                                        " • "
                                                                    ),
                                                            token =
                                                                it.accessToken,
                                                            allowedHosts =
                                                                setOf(
                                                                    "github.com"
                                                                )
                                                        )
                                                    }
                                                    ?: GitCredentials()
                                            )
                                }

                            if (gitResult == null) {
                                appendLines(
                                    sessionId,
                                    "Bu Git komutu için Git sekmesini kullanın.",
                                    TerminalLineKind.WARNING
                                )
                            } else {
                                appendLines(
                                    sessionId,
                                    gitResult,
                                    TerminalLineKind.SUCCESS
                                )
                            }
                        }

                        else -> {
                            val current =
                                sessions.firstOrNull {
                                    it.id == sessionId
                                } ?: session

                            val result =
                                localEngine.execute(
                                    sessionId = sessionId,
                                    command = command,
                                    workingDirectory =
                                        current.workingDirectory
                                )

                            if (result.output.isNotBlank()) {
                                appendLines(
                                    sessionId,
                                    result.output,
                                    if (result.exitCode == 0) {
                                        TerminalLineKind.OUTPUT
                                    } else {
                                        TerminalLineKind.ERROR
                                    }
                                )
                            }

                            if (result.timedOut) {
                                appendLines(
                                    sessionId,
                                    "Komut 120 saniye sınırını aştığı için durduruldu.",
                                    TerminalLineKind.WARNING
                                )
                            } else if (
                                result.exitCode != 0 &&
                                result.output.isBlank()
                            ) {
                                appendLines(
                                    sessionId,
                                    "Komut ${result.exitCode} koduyla tamamlandı.",
                                    TerminalLineKind.ERROR
                                )
                            }

                            updateSession(sessionId) {
                                it.copy(
                                    workingDirectory =
                                        result.workingDirectory
                                )
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    appendLines(
                        sessionId,
                        error.message
                            ?: "Komut çalıştırılamadı.",
                        TerminalLineKind.ERROR
                    )
                } finally {
                    updateSession(sessionId) {
                        it.copy(
                            running = false
                        )
                    }

                    commandJobs.remove(sessionId)
                }
            }

        commandJobs[sessionId] =
            job
    }

    fun cancelCommand(
        sessionId: String
    ) {
        localEngine.cancel(sessionId)
        commandJobs.remove(sessionId)
            ?.cancel()

        updateSession(sessionId) {
            it.copy(
                running = false,
                lines =
                    it.lines
                        .plus(
                            TerminalLine(
                                "^C  Komut durduruldu.",
                                TerminalLineKind.WARNING
                            )
                        )
                        .takeLast(
                            MAX_TERMINAL_LINES
                        )
            )
        }
    }

    DisposableEffect(workspace.absolutePath) {
        onDispose {
            commandJobs.keys
                .toList()
                .forEach { sessionId ->
                    localEngine.cancel(
                        sessionId
                    )
                }

            commandJobs.values.forEach {
                it.cancel()
            }

            commandJobs.clear()
        }
    }

    BackHandler(onBack = onBack)

    pendingDangerousCommand
        ?.let { pending ->
            AlertDialog(
                onDismissRequest = {
                    pendingDangerousCommand =
                        null
                },
                title = {
                    Text(
                        "Komutu onayla"
                    )
                },
                text = {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            )
                    ) {
                        Text(
                            "Bu komut dosyaları değiştirebilir veya silebilir. Devam etmek istiyor musun?"
                        )

                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        TerminalBackground
                                )
                        ) {
                            Text(
                                pending.second,
                                color =
                                    TerminalWarning,
                                modifier =
                                    Modifier.padding(
                                        12.dp
                                    )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingDangerousCommand =
                                null

                            runCommand(
                                pending.first,
                                pending.second,
                                confirmed = true
                            )
                        }
                    ) {
                        Text("Çalıştır")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingDangerousCommand =
                                null
                        }
                    ) {
                        Text("Vazgeç")
                    }
                }
            )
        }

    Scaffold(
        containerColor =
            TerminalBackground,
        topBar = {
            TopAppBar(
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            TerminalSurface,
                        titleContentColor =
                            TerminalText
                    ),
                navigationIcon = {
                    TextButton(
                        onClick = onBack
                    ) {
                        Text(
                            "‹",
                            color =
                                TerminalPrimary,
                            fontSize =
                                30.sp
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            "AppForge Terminal",
                            fontWeight =
                                FontWeight.Black,
                            fontSize =
                                19.sp
                        )

                        Text(
                            "Proje komut merkezi",
                            color =
                                TerminalMuted,
                            fontSize =
                                10.sp
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
        ) {
            ProjectWorkspaceSelector(
                projects = projects,
                selectedProject = selectedProject,
                scratchSelected =
                    selectedProjectId == null,
                workspace = workspace,
                onSelect = {
                    selectedProjectId =
                        it
                }
            )

            ScrollableTabRow(
                selectedTabIndex =
                    selectedTab.ordinal,
                containerColor =
                    TerminalSurface,
                contentColor =
                    TerminalPrimary,
                edgePadding =
                    8.dp,
                divider = {
                    HorizontalDivider(
                        color =
                            TerminalSurfaceRaised
                    )
                }
            ) {
                TerminalWorkspaceTab.entries.forEach { tab ->
                    Tab(
                        selected =
                            selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                        },
                        text = {
                            Text(
                                "${tab.icon}  ${tab.title}",
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            TerminalBackground
                        )
            ) {
                when (selectedTab) {
                    TerminalWorkspaceTab.TERMINAL ->
                        LocalTerminalPanel(
                            sessions = sessions,
                            activeSession =
                                activeSession,
                            workspaceRoot =
                                workspace,
                            onSelectSession = {
                                activeSessionId =
                                    it
                            },
                            onNewSession = {
                                if (
                                    sessions.size <
                                    MAX_TERMINAL_SESSIONS
                                ) {
                                    sessionNumber +=
                                        1

                                    val added =
                                        initialSession(
                                            sessionNumber
                                        )

                                    sessions =
                                        sessions.plus(
                                            added
                                        )

                                    activeSessionId =
                                        added.id
                                } else {
                                    appendLines(
                                        activeSessionId,
                                        "En fazla $MAX_TERMINAL_SESSIONS terminal oturumu açılabilir.",
                                        TerminalLineKind.WARNING
                                    )
                                }
                            },
                            onCloseSession = { id ->
                                if (sessions.size > 1) {
                                    cancelCommand(id)

                                    sessions =
                                        sessions.filterNot {
                                            it.id == id
                                        }

                                    if (activeSessionId == id) {
                                        activeSessionId =
                                            sessions.first().id
                                    }
                                }
                            },
                            onRunCommand = { command ->
                                runCommand(
                                    activeSessionId,
                                    command
                                )
                            },
                            onCancel = {
                                cancelCommand(
                                    activeSessionId
                                )
                            },
                            onHistoryIndex = { index ->
                                updateSession(
                                    activeSessionId
                                ) {
                                    it.copy(
                                        historyIndex =
                                            index
                                    )
                                }
                            }
                        )

                    TerminalWorkspaceTab.FILES ->
                        WorkspaceFilesPanel(
                            workspace = workspace
                        )

                    TerminalWorkspaceTab.GIT ->
                        GitWorkspacePanel(
                            workspace = workspace
                        )

                    TerminalWorkspaceTab.CONNECTIONS ->
                        ConnectionsPanel(
                            onOpenGit = {
                                selectedTab =
                                    TerminalWorkspaceTab.GIT
                            },
                            railwayAuthorizationUri =
                                railwayAuthorizationUri,
                            railwayAuthorizationSequence =
                                railwayAuthorizationSequence,
                            onRailwayAuthorizationConsumed =
                                onRailwayAuthorizationConsumed
                        )

                    TerminalWorkspaceTab.SSH ->
                        SshWorkspacePanel(
                            sshClient = sshClient
                        )

                    TerminalWorkspaceTab.TOOLS ->
                        TerminalToolsPanel(
                            workspace = workspace,
                            onRunCommand = { command ->
                                selectedTab =
                                    TerminalWorkspaceTab.TERMINAL

                                runCommand(
                                    activeSessionId,
                                    command
                                )
                            },
                            onOpenBuilder = {
                                onOpenBuilder(
                                    selectedProjectId
                                )
                            },
                            onOpenAi = {
                                onOpenAi(
                                    selectedProjectId
                                )
                            },
                            onOpenFiles = {
                                selectedTab =
                                    TerminalWorkspaceTab.FILES
                            },
                            onOpenGit = {
                                selectedTab =
                                    TerminalWorkspaceTab.GIT
                            },
                            onOpenConnections = {
                                selectedTab =
                                    TerminalWorkspaceTab.CONNECTIONS
                            },
                            onOpenSsh = {
                                selectedTab =
                                    TerminalWorkspaceTab.SSH
                            }
                        )
                }
            }
        }
    }
}

@Composable
private fun ProjectWorkspaceSelector(
    projects: List<SavedProject>,
    selectedProject: SavedProject?,
    scratchSelected: Boolean,
    workspace: File,
    onSelect: (String?) -> Unit
) {
    var expanded by
        remember {
            mutableStateOf(false)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 9.dp
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    TerminalSurfaceRaised
            ),
        shape =
            RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .background(
                            TerminalPrimary,
                            RoundedCornerShape(
                                11.dp
                            )
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    ">_",
                    color =
                        TerminalBackground,
                    fontWeight =
                        FontWeight.Black
                )
            }

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    selectedProject
                        ?.name
                        ?: "Genel çalışma alanı",
                    color =
                        TerminalText,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    workspace.absolutePath,
                    color =
                        TerminalMuted,
                    fontSize =
                        9.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
            }

            Box {
                TextButton(
                    onClick = {
                        expanded =
                            true
                    }
                ) {
                    Text(
                        "Değiştir",
                        color =
                            TerminalPrimary
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded =
                            false
                    }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Genel çalışma alanı"
                            )
                        },
                        trailingIcon = {
                            if (scratchSelected) {
                                Text("✓")
                            }
                        },
                        onClick = {
                            expanded =
                                false

                            onSelect(null)
                        }
                    )

                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(project.name)
                                    Text(
                                        project.packageName,
                                        fontSize = 10.sp
                                    )
                                }
                            },
                            trailingIcon = {
                                if (
                                    project.id ==
                                    selectedProject?.id
                                ) {
                                    Text("✓")
                                }
                            },
                            onClick = {
                                expanded =
                                    false

                                onSelect(
                                    project.id
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun terminalRelativePath(
    root: File,
    directory: File
): String =
    runCatching {
        val relative =
            directory
                .canonicalFile
                .relativeTo(
                    root.canonicalFile
                )
                .invariantSeparatorsPath

        if (relative.isBlank()) {
            "~"
        } else {
            "~/$relative"
        }
    }.getOrDefault(
        directory.name
    )

private const val MAX_TERMINAL_LINES =
    1_500

private const val MAX_TERMINAL_SESSIONS =
    5
