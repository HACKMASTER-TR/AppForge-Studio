package com.appforge.studio.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun LinuxInteractiveTerminalPanel(
    manager: AndroidLinuxRuntimeManager,
    distribution: LinuxDistribution,
    workspace: File
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val density =
        LocalDensity.current

    val session =
        remember(
            context.applicationContext,
            workspace.absolutePath,
            distribution
        ) {
            InteractiveLinuxPtySession(
                context.applicationContext
            )
        }

    val terminalBuffer =
        remember(
            workspace.absolutePath,
            distribution
        ) {
            AnsiTerminalBuffer(
                initialRows = 24,
                initialColumns = 80
            )
        }

    var snapshot by
        remember(
            workspace.absolutePath,
            distribution
        ) {
            mutableStateOf(
                terminalBuffer.snapshot()
            )
        }

    var running by
        remember {
            mutableStateOf(false)
        }

    var starting by
        remember {
            mutableStateOf(false)
        }

    var input by
        remember {
            mutableStateOf("")
        }

    var message by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    var rows by
        remember {
            mutableIntStateOf(24)
        }

    var columns by
        remember {
            mutableIntStateOf(80)
        }

    var screenVersion by
        remember {
            mutableIntStateOf(0)
        }

    val outputScroll =
        rememberScrollState()

    val charWidthPx =
        with(density) {
            7.2.dp.toPx()
        }

    val lineHeightPx =
        with(density) {
            14.dp.toPx()
        }

    fun publishSnapshot() {
        snapshot =
            terminalBuffer.snapshot()
        screenVersion += 1
    }

    fun writeToTerminal(
        text: String
    ) {
        if (!running) {
            return
        }

        scope.launch {
            runCatching {
                session.write(text)
            }.onFailure { error ->
                message =
                    error.message
                        ?: "Linux terminaline yazılamadı."
            }
        }
    }

    DisposableEffect(session) {
        onDispose {
            session.close()
        }
    }

    LaunchedEffect(screenVersion) {
        outputScroll.scrollTo(
            outputScroll.maxValue
        )
    }

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
                Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        "Gerçek Linux PTY",
                        color =
                            TerminalText,
                        fontWeight =
                            FontWeight.Black,
                        fontSize =
                            14.sp
                    )

                    Text(
                        "forkpty • xterm-256color • ${columns}×${rows}",
                        color =
                            TerminalSecondary,
                        fontSize =
                            9.sp
                    )
                }

                if (running) {
                    OutlinedButton(
                        onClick = {
                            session.terminate()
                        }
                    ) {
                        Text("Durdur")
                    }
                } else {
                    Button(
                        enabled =
                            !starting,
                        onClick = {
                            starting = true
                            message = null
                            terminalBuffer.reset()
                            publishSnapshot()

                            scope.launch {
                                runCatching {
                                    val rootfs =
                                        manager.requireReadyRootfs(
                                            distribution
                                        )

                                    session.start(
                                        rootfs = rootfs,
                                        workspace = workspace,
                                        rows = rows,
                                        columns = columns,
                                        onOutput = { chunk ->
                                            scope.launch {
                                                terminalBuffer.feed(
                                                    TerminalSecretMasker.redact(
                                                        chunk
                                                    )
                                                )
                                                publishSnapshot()
                                            }
                                        },
                                        onExit = { exitCode ->
                                            scope.launch {
                                                running = false
                                                starting = false
                                                message =
                                                    "Linux PTY çıkış kodu: $exitCode"
                                                publishSnapshot()
                                            }
                                        }
                                    )

                                    running = true
                                }.onFailure { error ->
                                    running = false
                                    message =
                                        error.message
                                            ?: "Linux PTY başlatılamadı."
                                }

                                starting = false
                            }
                        }
                    ) {
                        Text(
                            if (starting) {
                                "Başlatılıyor…"
                            } else {
                                "Linux'u Başlat"
                            }
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(
                            TerminalBackground,
                            RoundedCornerShape(8.dp)
                        )
                        .onSizeChanged { size ->
                            val nextColumns =
                                (size.width /
                                    charWidthPx)
                                    .toInt()
                                    .coerceIn(
                                        20,
                                        240
                                    )

                            val nextRows =
                                (size.height /
                                    lineHeightPx)
                                    .toInt()
                                    .coerceIn(
                                        8,
                                        100
                                    )

                            if (
                                nextColumns != columns ||
                                nextRows != rows
                            ) {
                                columns = nextColumns
                                rows = nextRows
                                terminalBuffer.resize(
                                    newRows = nextRows,
                                    newColumns = nextColumns
                                )
                                publishSnapshot()

                                if (running) {
                                    scope.launch {
                                        session.resize(
                                            rows = nextRows,
                                            columns = nextColumns
                                        )
                                    }
                                }
                            }
                        }
                        .verticalScroll(
                            outputScroll
                        )
                        .padding(9.dp)
            ) {
                Text(
                    text =
                        snapshot.toAnnotatedTerminalText(),
                    color =
                        TerminalText,
                    fontFamily =
                        FontFamily.Monospace,
                    fontSize =
                        10.sp,
                    lineHeight =
                        14.sp
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = {
                    if (it.length <= 8_192) {
                        input = it
                    }
                },
                enabled = running,
                modifier =
                    Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(
                        "Linux komutu / etkileşimli giriş"
                    )
                },
                supportingText = {
                    Text(
                        "apt, python3, node, bash ve diğer TTY uygulamaları bu gerçek PTY'ye bağlıdır."
                    )
                }
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                Button(
                    enabled =
                        running &&
                            input.isNotBlank(),
                    modifier =
                        Modifier.weight(1f),
                    onClick = {
                        val value = input
                        input = ""
                        writeToTerminal(
                            "$value\r"
                        )
                    }
                ) {
                    Text("Gönder")
                }

                OutlinedButton(
                    enabled = running,
                    modifier =
                        Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            session.sendControlC()
                        }
                    }
                ) {
                    Text("Ctrl+C")
                }
            }

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
                LinuxPtyKey(
                    "Tab",
                    running
                ) {
                    writeToTerminal("\t")
                }

                LinuxPtyKey(
                    "Esc",
                    running
                ) {
                    writeToTerminal("\u001b")
                }

                LinuxPtyKey(
                    "↑",
                    running
                ) {
                    writeToTerminal("\u001b[A")
                }

                LinuxPtyKey(
                    "↓",
                    running
                ) {
                    writeToTerminal("\u001b[B")
                }

                LinuxPtyKey(
                    "←",
                    running
                ) {
                    writeToTerminal("\u001b[D")
                }

                LinuxPtyKey(
                    "→",
                    running
                ) {
                    writeToTerminal("\u001b[C")
                }

                LinuxPtyKey(
                    "Enter",
                    running
                ) {
                    writeToTerminal("\r")
                }

                LinuxPtyKey(
                    "Ctrl+D",
                    running
                ) {
                    writeToTerminal("\u0004")
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                LinuxQuickCommand(
                    title = "APT güncelle",
                    enabled = running,
                    command =
                        "apt-get update",
                    onPrepare = {
                        input = it
                    }
                )

                LinuxQuickCommand(
                    title = "Python",
                    enabled = running,
                    command =
                        "python3",
                    onPrepare = {
                        input = it
                    }
                )

                LinuxQuickCommand(
                    title = "Node",
                    enabled = running,
                    command =
                        "node",
                    onPrepare = {
                        input = it
                    }
                )

                LinuxQuickCommand(
                    title = "Araç zinciri",
                    enabled = running,
                    command =
                        manager.toolchainCommand(
                            listOf(
                                LinuxToolchainId.BASE,
                                LinuxToolchainId.PYTHON,
                                LinuxToolchainId.NODE
                            )
                        ),
                    onPrepare = {
                        input = it
                    }
                )
            }

            message?.let {
                Text(
                    TerminalSecretMasker.redact(
                        it
                    ),
                    color =
                        if (running) {
                            TerminalPrimary
                        } else {
                            TerminalMuted
                        },
                    fontSize =
                        9.sp
                )
            }
        }
    }
}

@Composable
private fun LinuxPtyKey(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick
    ) {
        Text(title)
    }
}

@Composable
private fun LinuxQuickCommand(
    title: String,
    enabled: Boolean,
    command: String,
    onPrepare: (String) -> Unit
) {
    OutlinedButton(
        enabled = enabled,
        onClick = {
            onPrepare(command)
        }
    ) {
        Text(title)
    }
}

internal fun AnsiTerminalSnapshot
    .toAnnotatedTerminalText(): AnnotatedString =
    buildAnnotatedString {
        lines.forEachIndexed { lineIndex, line ->
            val cursorOnLine =
                cursorVisible &&
                    lineIndex ==
                    cursorLine

            val lastContentIndex =
                maxOf(
                    line.indexOfLast {
                        it.character != ' '
                    },
                    if (cursorOnLine) {
                        cursorColumn
                    } else {
                        -1
                    }
                )

            val endExclusive =
                (lastContentIndex + 1)
                    .coerceIn(
                        0,
                        line.size
                    )

            for (
                column in
                0 until endExclusive
            ) {
                val cell =
                    line[column]

                val isCursor =
                    cursorOnLine &&
                        column ==
                        cursorColumn

                val foreground =
                    cell.style.foregroundRgb
                        .toComposeColor(
                            TerminalText
                        )

                val background =
                    cell.style.backgroundRgb
                        .toComposeColor(
                            TerminalBackground
                        )

                val effectiveForeground =
                    when {
                        isCursor ->
                            TerminalBackground

                        cell.style.inverse ->
                            background

                        else ->
                            foreground
                    }

                val effectiveBackground =
                    when {
                        isCursor ->
                            TerminalPrimary

                        cell.style.inverse ->
                            foreground

                        else ->
                            background
                    }

                pushStyle(
                    SpanStyle(
                        color =
                            effectiveForeground,
                        background =
                            effectiveBackground,
                        fontWeight =
                            if (cell.style.bold) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        textDecoration =
                            if (cell.style.underline) {
                                TextDecoration.Underline
                            } else {
                                TextDecoration.None
                            }
                    )
                )

                append(
                    if (
                        isCursor &&
                        cell.character == ' '
                    ) {
                        " "
                    } else {
                        cell.character
                    }
                )

                pop()
            }

            if (
                lineIndex <
                lines.lastIndex
            ) {
                append('\n')
            }
        }
    }

private fun Int?.toComposeColor(
    fallback: Color
): Color {
    val rgb =
        this
            ?: return fallback

    return Color(
        red =
            ((rgb shr 16) and 0xff) /
                255f,
        green =
            ((rgb shr 8) and 0xff) /
                255f,
        blue =
            (rgb and 0xff) /
                255f,
        alpha = 1f
    )
}
