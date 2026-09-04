package com.appforge.studio.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun UltimateCodeEditorPanel(
    workspace: File
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()

    val files =
        remember(
            workspace.absolutePath,
            workspace.lastModified()
        ) {
            UltimateEditorWorkspace.listFiles(
                workspace
            )
        }

    var fileMenuOpen by
        remember(workspace.absolutePath) {
            mutableStateOf(false)
        }

    var openTabs by
        remember(workspace.absolutePath) {
            mutableStateOf<List<String>>(
                emptyList()
            )
        }

    var activePath by
        remember(workspace.absolutePath) {
            mutableStateOf<String?>(null)
        }

    var contents by
        remember(workspace.absolutePath) {
            mutableStateOf<Map<String, String>>(
                emptyMap()
            )
        }

    var savedContents by
        remember(workspace.absolutePath) {
            mutableStateOf<Map<String, String>>(
                emptyMap()
            )
        }

    val undoBuffers =
        remember(workspace.absolutePath) {
            mutableMapOf<String, EditorUndoBuffer>()
        }

    var searchQuery by
        remember(workspace.absolutePath) {
            mutableStateOf("")
        }

    var replacement by
        remember(workspace.absolutePath) {
            mutableStateOf("")
        }

    var showDiff by
        remember(workspace.absolutePath) {
            mutableStateOf(false)
        }

    var status by
        remember(workspace.absolutePath) {
            mutableStateOf(
                "Bir dosya seçerek düzenlemeye başla."
            )
        }

    var lspSnapshot by
        remember(workspace.absolutePath) {
            mutableStateOf(
                LspSessionSnapshot(
                    state = LspConnectionState.STOPPED
                )
            )
        }

    var lspDiagnostics by
        remember(workspace.absolutePath) {
            mutableStateOf<List<LspDiagnostic>>(
                emptyList()
            )
        }

    var lspCompletions by
        remember(workspace.absolutePath) {
            mutableStateOf<List<LspCompletionItem>>(
                emptyList()
            )
        }

    var lspDefinition by
        remember(workspace.absolutePath) {
            mutableStateOf<LspLocation?>(null)
        }

    var lspLineText by
        remember(workspace.absolutePath) {
            mutableStateOf("1")
        }

    var lspColumnText by
        remember(workspace.absolutePath) {
            mutableStateOf("1")
        }

    val lspSession =
        remember(workspace.absolutePath) {
            LinuxLspSession(
                context = context,
                onState = { snapshot ->
                    uiScope.launch {
                        lspSnapshot = snapshot
                    }
                },
                onDiagnostics = { uri, diagnostics ->
                    uiScope.launch {
                        val currentPath =
                            activePath

                        if (
                            currentPath != null &&
                            LspDocumentPath.toWorkspaceUri(
                                currentPath
                            ) == uri
                        ) {
                            lspDiagnostics = diagnostics
                        }
                    }
                }
            )
        }

    DisposableEffect(lspSession) {
        onDispose {
            lspSession.close()
        }
    }

    fun openFile(path: String) {
        if (path !in contents) {
            val text =
                runCatching {
                    UltimateEditorWorkspace.readText(
                        workspace,
                        path
                    )
                }.getOrElse { error ->
                    status =
                        error.message
                            ?: "Dosya açılamadı."
                    return
                }

            contents =
                contents +
                    (path to text)

            savedContents =
                savedContents +
                    (path to text)

            undoBuffers[path] =
                EditorUndoBuffer(text)
        }

        if (path !in openTabs) {
            openTabs =
                openTabs
                    .plus(path)
                    .takeLast(MAX_EDITOR_TABS)
        }

        activePath = path
        showDiff = false
        status = "$path açıldı."
    }

    fun closeFile(path: String) {
        openTabs =
            openTabs.filterNot {
                it == path
            }

        if (activePath == path) {
            activePath =
                openTabs.lastOrNull()
        }
    }

    val activeText =
        activePath
            ?.let {
                contents[it]
            }
            .orEmpty()

    val activeSaved =
        activePath
            ?.let {
                savedContents[it]
            }
            .orEmpty()

    LaunchedEffect(
        lspSnapshot.state,
        activePath,
        activeText
    ) {
        if (
            lspSnapshot.state ==
                LspConnectionState.READY
        ) {
            val path =
                activePath
                    ?: return@LaunchedEffect

            if (
                UltimateLspCatalog.forPath(path) != null
            ) {
                delay(300L)
                runCatching {
                    lspSession.syncDocument(
                        relativePath = path,
                        text = activeText
                    )
                }.onFailure { error ->
                    status =
                        error.message
                            ?: "LSP belge eşitlemesi başarısız."
                }
            }
        }
    }

    fun lspPositionOrNull(): LspPosition? {
        val line =
            lspLineText.toIntOrNull()
        val column =
            lspColumnText.toIntOrNull()

        if (
            line == null ||
            column == null ||
            line < 1 ||
            column < 1
        ) {
            status =
                "LSP satır ve sütun değerleri 1 veya daha büyük olmalı."
            return null
        }

        return LspPosition(
            line = line - 1,
            character = column - 1
        )
    }

    val searchMatches =
        remember(
            activeText,
            searchQuery
        ) {
            UltimateEditorSearch.find(
                activeText,
                searchQuery
            )
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    TerminalSurface
            )
    ) {
        Column(
            modifier =
                Modifier.padding(14.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "AppForge Code Editor",
                color = TerminalText,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp
            )

            Text(
                "Çoklu sekme • arama/değiştir • undo/redo • diff • güvenli restore point • LSP hazırlığı",
                color = TerminalMuted,
                fontSize = 10.sp
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                Column {
                    Button(
                        onClick = {
                            fileMenuOpen = true
                        }
                    ) {
                        Text("Dosya Aç")
                    }

                    DropdownMenu(
                        expanded = fileMenuOpen,
                        onDismissRequest = {
                            fileMenuOpen = false
                        }
                    ) {
                        files
                            .take(100)
                            .forEach { entry ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (entry.sensitive) {
                                                "🔒 ${entry.relativePath}"
                                            } else {
                                                entry.relativePath
                                            },
                                            fontSize = 11.sp
                                        )
                                    },
                                    onClick = {
                                        fileMenuOpen = false
                                        openFile(
                                            entry.relativePath
                                        )
                                    }
                                )
                            }
                    }
                }

                OutlinedButton(
                    onClick = {
                        activePath
                            ?.let { path ->
                                val current =
                                    contents[path]
                                        .orEmpty()

                                val result =
                                    runCatching {
                                        UltimateEditorWorkspace.saveText(
                                            workspace,
                                            path,
                                            current
                                        )
                                    }.getOrElse { error ->
                                        status =
                                            error.message
                                                ?: "Dosya kaydedilemedi."
                                        return@OutlinedButton
                                    }

                                savedContents =
                                    savedContents +
                                        (path to current)

                                undoBuffers[path]
                                    ?.reset(current)

                                status =
                                    if (
                                        result.restorePoint != null
                                    ) {
                                        "Kaydedildi • yerel geri yükleme noktası oluşturuldu."
                                    } else if (
                                        UltimateEditorWorkspace.isSensitive(
                                            path
                                        )
                                    ) {
                                        "Kaydedildi • hassas dosya için geçmiş kopyası tutulmadı."
                                    } else {
                                        "Kaydedildi."
                                    }
                            }
                    },
                    enabled =
                        activePath != null &&
                            activeText != activeSaved
                ) {
                    Text("Kaydet")
                }
            }

            if (openTabs.isNotEmpty()) {
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
                    openTabs.forEach { path ->
                        if (activePath == path) {
                            Button(
                                onClick = {
                                    activePath = path
                                }
                            ) {
                                Text(
                                    path.substringAfterLast('/'),
                                    maxLines = 1
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    activePath = path
                                }
                            ) {
                                Text(
                                    path.substringAfterLast('/'),
                                    maxLines = 1
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                closeFile(path)
                            }
                        ) {
                            Text("×")
                        }
                    }
                }
            }

            activePath
                ?.let { path ->
                    val isSensitive =
                        UltimateEditorWorkspace.isSensitive(
                            path
                        )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            path,
                            color = TerminalPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )

                        if (isSensitive) {
                            Text(
                                "HASSAS • geçmiş yok",
                                color = TerminalWarning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = activeText,
                        onValueChange = { next ->
                            val history =
                                undoBuffers[path]
                                    ?: EditorUndoBuffer(
                                        activeText
                                    ).also {
                                        undoBuffers[path] = it
                                    }

                            contents =
                                contents +
                                    (
                                        path to
                                            history.edit(next)
                                        )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = 280.dp,
                                    max = 440.dp
                                ),
                        textStyle =
                            androidx.compose.ui.text.TextStyle(
                                fontFamily =
                                    FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TerminalText
                            ),
                        label = {
                            Text("Kod")
                        }
                    )

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val history =
                                    undoBuffers[path]
                                        ?: return@OutlinedButton

                                contents =
                                    contents +
                                        (path to history.undo())
                            },
                            enabled =
                                undoBuffers[path]
                                    ?.canUndo() == true
                        ) {
                            Text("Geri Al")
                        }

                        OutlinedButton(
                            onClick = {
                                val history =
                                    undoBuffers[path]
                                        ?: return@OutlinedButton

                                contents =
                                    contents +
                                        (path to history.redo())
                            },
                            enabled =
                                undoBuffers[path]
                                    ?.canRedo() == true
                        ) {
                            Text("Yinele")
                        }

                        OutlinedButton(
                            onClick = {
                                showDiff = !showDiff
                            },
                            enabled =
                                activeText != activeSaved
                        ) {
                            Text(
                                if (showDiff) {
                                    "Diff'i Gizle"
                                } else {
                                    "Değişiklikleri Gör"
                                }
                            )
                        }

                        Text(
                            "${activeText.lines().size} satır",
                            color = TerminalMuted,
                            fontSize = 10.sp,
                            modifier =
                                Modifier.padding(
                                    top = 12.dp
                                )
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text("Ara")
                        },
                        supportingText = {
                            Text(
                                if (searchQuery.isBlank()) {
                                    "Metin araması"
                                } else {
                                    "${searchMatches.size} eşleşme"
                                }
                            )
                        }
                    )

                    OutlinedTextField(
                        value = replacement,
                        onValueChange = {
                            replacement = it
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text("Değiştir")
                        }
                    )

                    OutlinedButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                val replaced =
                                    UltimateEditorSearch.replaceAll(
                                        activeText,
                                        searchQuery,
                                        replacement
                                    )

                                val history =
                                    undoBuffers[path]
                                        ?: EditorUndoBuffer(
                                            activeText
                                        ).also {
                                            undoBuffers[path] = it
                                        }

                                contents =
                                    contents +
                                        (
                                            path to
                                                history.edit(
                                                    replaced
                                                )
                                            )

                                status =
                                    "${searchMatches.size} eşleşme editör belleğinde değiştirildi; Kaydet ile diske yazılır."
                            }
                        },
                        enabled =
                            searchQuery.isNotBlank() &&
                                searchMatches.isNotEmpty()
                    ) {
                        Text("Tümünü Değiştir")
                    }

                    if (showDiff) {
                        val diff =
                            UltimateEditorDiff.compare(
                                activeSaved,
                                activeText
                            )

                        Card(
                            modifier =
                                Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        TerminalBackground
                                )
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(
                                            max = 260.dp
                                        )
                                        .verticalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(10.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(2.dp)
                            ) {
                                diff.take(300)
                                    .forEach { line ->
                                        Text(
                                            when (line.kind) {
                                                EditorDiffKind.SAME ->
                                                    "  ${line.text}"

                                                EditorDiffKind.ADDED ->
                                                    "+ ${line.text}"

                                                EditorDiffKind.REMOVED ->
                                                    "- ${line.text}"
                                            },
                                            color =
                                                when (line.kind) {
                                                    EditorDiffKind.SAME ->
                                                        TerminalMuted

                                                    EditorDiffKind.ADDED ->
                                                        TerminalPrimary

                                                    EditorDiffKind.REMOVED ->
                                                        TerminalWarning
                                                },
                                            fontFamily =
                                                FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }
                            }
                        }
                    }

                    UltimateLspCatalog.forPath(path)
                        ?.let { profile ->
                            Card(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor =
                                            TerminalSurfaceRaised
                                    )
                            ) {
                                Column(
                                    modifier =
                                        Modifier.padding(12.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        "LSP • ${profile.title}",
                                        color = TerminalSecondary,
                                        fontWeight = FontWeight.Black
                                    )

                                    Text(
                                        lspSnapshot.detail,
                                        color =
                                            when (lspSnapshot.state) {
                                                LspConnectionState.READY ->
                                                    TerminalPrimary
                                                LspConnectionState.ERROR ->
                                                    TerminalWarning
                                                else ->
                                                    TerminalMuted
                                            },
                                        fontSize = 10.sp
                                    )

                                    Text(
                                        "Sunucu: ${profile.serverCommand}",
                                        color = TerminalText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )

                                    Text(
                                        "Kurulum gerekiyorsa Linux terminalinde: ${profile.installCommand}",
                                        color = TerminalMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp
                                    )

                                    Text(
                                        "AppForge LSP sunucusunu otomatik kurmaz. Başlatma yalnız doğrulanmış rootless Linux ortamında ve stdio JSON-RPC üzerinden yapılır.",
                                        color = TerminalMuted,
                                        fontSize = 9.sp
                                    )

                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(
                                                    rememberScrollState()
                                                ),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(7.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                uiScope.launch {
                                                    runCatching {
                                                        val manager =
                                                            AndroidLinuxRuntimeManager(
                                                                context
                                                            )

                                                        val distribution =
                                                            LinuxDistribution.entries
                                                                .firstOrNull {
                                                                    manager.inspect(it).ready
                                                                }
                                                                ?: error(
                                                                    "Önce Ultimate Linux modunda doğrulanmış rootfs kur."
                                                                )

                                                        val rootfs =
                                                            manager.requireReadyRootfs(
                                                                distribution
                                                            )

                                                        lspSession.start(
                                                            rootfs = rootfs,
                                                            workspace = workspace,
                                                            profile = profile
                                                        )

                                                        lspSession.syncDocument(
                                                            relativePath = path,
                                                            text = activeText
                                                        )
                                                    }.onFailure { error ->
                                                        status =
                                                            error.message
                                                                ?: "LSP başlatılamadı."
                                                    }
                                                }
                                            },
                                            enabled =
                                                lspSnapshot.state !=
                                                    LspConnectionState.STARTING &&
                                                    lspSnapshot.state !=
                                                    LspConnectionState.READY
                                        ) {
                                            Text("LSP Başlat")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                uiScope.launch {
                                                    lspSession.stop()
                                                    lspDiagnostics =
                                                        emptyList()
                                                    lspCompletions =
                                                        emptyList()
                                                    lspDefinition = null
                                                }
                                            },
                                            enabled =
                                                lspSnapshot.state ==
                                                    LspConnectionState.READY ||
                                                    lspSnapshot.state ==
                                                    LspConnectionState.ERROR
                                        ) {
                                            Text("LSP Durdur")
                                        }
                                    }

                                    if (
                                        lspSnapshot.state ==
                                        LspConnectionState.READY
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                            horizontalArrangement =
                                                Arrangement.spacedBy(7.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = lspLineText,
                                                onValueChange = {
                                                    lspLineText =
                                                        it.filter(Char::isDigit)
                                                            .take(6)
                                                },
                                                modifier =
                                                    Modifier.weight(1f),
                                                singleLine = true,
                                                label = {
                                                    Text("Satır")
                                                }
                                            )

                                            OutlinedTextField(
                                                value = lspColumnText,
                                                onValueChange = {
                                                    lspColumnText =
                                                        it.filter(Char::isDigit)
                                                            .take(6)
                                                },
                                                modifier =
                                                    Modifier.weight(1f),
                                                singleLine = true,
                                                label = {
                                                    Text("Sütun")
                                                }
                                            )
                                        }

                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(
                                                        rememberScrollState()
                                                    ),
                                            horizontalArrangement =
                                                Arrangement.spacedBy(7.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val position =
                                                        lspPositionOrNull()
                                                            ?: return@OutlinedButton

                                                    uiScope.launch {
                                                        runCatching {
                                                            lspSession.completion(
                                                                relativePath = path,
                                                                position = position
                                                            )
                                                        }.onSuccess {
                                                            lspCompletions = it
                                                            status =
                                                                "${it.size} LSP tamamlama önerisi alındı."
                                                        }.onFailure { error ->
                                                            status =
                                                                error.message
                                                                    ?: "Completion isteği başarısız."
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text("Tamamlama İste")
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val position =
                                                        lspPositionOrNull()
                                                            ?: return@OutlinedButton

                                                    uiScope.launch {
                                                        runCatching {
                                                            lspSession.definition(
                                                                relativePath = path,
                                                                position = position
                                                            )
                                                        }.onSuccess {
                                                            lspDefinition = it
                                                            status =
                                                                if (it == null) {
                                                                    "Tanım bulunamadı."
                                                                } else {
                                                                    "Tanım bulundu."
                                                                }
                                                        }.onFailure { error ->
                                                            status =
                                                                error.message
                                                                    ?: "Definition isteği başarısız."
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text("Tanıma Git")
                                            }
                                        }

                                        if (lspDiagnostics.isNotEmpty()) {
                                            Text(
                                                "Tanılamalar (${lspDiagnostics.size})",
                                                color = TerminalWarning,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )

                                            lspDiagnostics
                                                .take(20)
                                                .forEach { diagnostic ->
                                                    Text(
                                                        "${diagnostic.range.start.line + 1}:${diagnostic.range.start.character + 1} • ${diagnostic.message}",
                                                        color = TerminalMuted,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                        } else {
                                            Text(
                                                "Tanılamalar: sorun bildirilmedi.",
                                                color = TerminalPrimary,
                                                fontSize = 9.sp
                                            )
                                        }

                                        if (lspCompletions.isNotEmpty()) {
                                            Text(
                                                "Otomatik tamamlama",
                                                color = TerminalSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )

                                            lspCompletions
                                                .take(12)
                                                .forEach { item ->
                                                    OutlinedButton(
                                                        onClick = {
                                                            val position =
                                                                lspPositionOrNull()
                                                                    ?: return@OutlinedButton

                                                            val offset =
                                                                LspDocumentPath.offsetFor(
                                                                    activeText,
                                                                    position
                                                                )
                                                                    ?: return@OutlinedButton

                                                            val next =
                                                                activeText.substring(
                                                                    0,
                                                                    offset
                                                                ) +
                                                                    item.insertText +
                                                                    activeText.substring(
                                                                        offset
                                                                    )

                                                            val history =
                                                                undoBuffers[path]
                                                                    ?: EditorUndoBuffer(
                                                                        activeText
                                                                    ).also {
                                                                        undoBuffers[path] = it
                                                                    }

                                                            contents =
                                                                contents +
                                                                    (path to history.edit(next))

                                                            status =
                                                                "${item.label} editör belleğine eklendi; Kaydet ile diske yazılır."
                                                        }
                                                    ) {
                                                        Text(
                                                            if (item.detail.isNullOrBlank()) {
                                                                item.label
                                                            } else {
                                                                "${item.label} • ${item.detail}"
                                                            },
                                                            maxLines = 2
                                                        )
                                                    }
                                                }
                                        }

                                        lspDefinition
                                            ?.let { location ->
                                                val targetPath =
                                                    LspDocumentPath.fromWorkspaceUri(
                                                        location.uri
                                                    )

                                                Text(
                                                    "Tanım: ${targetPath ?: location.uri}:${location.range.start.line + 1}:${location.range.start.character + 1}",
                                                    color = TerminalSecondary,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp
                                                )

                                                if (targetPath != null) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            openFile(targetPath)
                                                            lspLineText =
                                                                (location.range.start.line + 1).toString()
                                                            lspColumnText =
                                                                (location.range.start.character + 1).toString()
                                                        }
                                                    ) {
                                                        Text("Tanım Dosyasını Aç")
                                                    }
                                                }
                                            }
                                    }
                                }
                            }
                        }
                }

            Text(
                status,
                color = TerminalMuted,
                fontSize = 9.sp
            )
        }
    }
}

private const val MAX_EDITOR_TABS = 8
