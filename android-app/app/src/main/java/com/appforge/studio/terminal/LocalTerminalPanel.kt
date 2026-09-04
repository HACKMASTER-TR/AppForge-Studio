package com.appforge.studio.terminal

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun LocalTerminalPanel(
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
    var command by
        rememberSaveable(
            activeSession.id
        ) {
            mutableStateOf("")
        }

    fun submitCommand() {
        if (
            !activeSession.running &&
            command.isNotBlank()
        ) {
            val submitted =
                command

            command =
                ""

            onRunCommand(submitted)
        }
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
            sessions.forEach { session ->
                val selected =
                    session.id ==
                        activeSession.id

                Card(
                    onClick = {
                        onSelectSession(
                            session.id
                        )
                    },
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (selected) {
                                    TerminalPrimary
                                } else {
                                    TerminalSurfaceRaised
                                }
                        ),
                    shape =
                        RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier =
                            Modifier.padding(
                                start = 11.dp,
                                end = 4.dp,
                                top = 5.dp,
                                bottom = 5.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        if (session.running) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier
                                        .padding(end = 6.dp)
                                        .widthIn(
                                            max = 12.dp
                                        ),
                                color =
                                    if (selected) {
                                        TerminalBackground
                                    } else {
                                        TerminalPrimary
                                    },
                                strokeWidth = 2.dp
                            )
                        }

                        Text(
                            session.title,
                            color =
                                if (selected) {
                                    TerminalBackground
                                } else {
                                    TerminalText
                                },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                11.sp
                        )

                        if (sessions.size > 1) {
                            TextButton(
                                onClick = {
                                    onCloseSession(
                                        session.id
                                    )
                                }
                            ) {
                                Text(
                                    "×",
                                    color =
                                        if (selected) {
                                            TerminalBackground
                                        } else {
                                            TerminalMuted
                                        }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onNewSession
            ) {
                Text(
                    "+ Oturum",
                    fontSize =
                        11.sp
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
                terminalRelativePath(
                    workspaceRoot,
                    activeSession.workingDirectory
                ),
                modifier =
                    Modifier.weight(1f),
                color =
                    TerminalPrimary,
                fontFamily =
                    FontFamily.Monospace,
                fontSize =
                    11.sp,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )

            Text(
                if (activeSession.running) {
                    "Çalışıyor"
                } else {
                    "Hazır"
                },
                color =
                    if (activeSession.running) {
                        TerminalWarning
                    } else {
                        TerminalPrimary
                    },
                fontSize =
                    10.sp
            )
        }

        TerminalOutput(
            session = activeSession,
            command = command,
            inputEnabled =
                !activeSession.running,
            onCommandChange = {
                command =
                    it
                        .replace("\n", "")
                        .replace("\r", "")
                        .take(16 * 1_024)
            },
            onSubmit = {
                submitCommand()
            },
            modifier =
                Modifier.weight(1f)
        )

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
            ExtraKey("ESC") {
                if (!activeSession.running) {
                    command += "\u001B"
                }
            }

            ExtraKey("TAB") {
                if (!activeSession.running) {
                    command += "\t"
                }
            }

            ExtraKey("CTRL+C") {
                if (activeSession.running) {
                    onCancel()
                }
            }

            ExtraKey("↵") {
                submitCommand()
            }

            ExtraKey("↑") {
                val history =
                    activeSession.history

                if (history.isNotEmpty()) {
                    val index =
                        (
                            activeSession.historyIndex -
                                1
                        ).coerceIn(
                            0,
                            history.lastIndex
                        )

                    command =
                        history[index]

                    onHistoryIndex(index)
                }
            }

            ExtraKey("↓") {
                val history =
                    activeSession.history

                if (history.isNotEmpty()) {
                    val index =
                        (
                            activeSession.historyIndex +
                                1
                        ).coerceAtMost(
                            history.size
                        )

                    command =
                        if (index == history.size) {
                            ""
                        } else {
                            history[index]
                        }

                    onHistoryIndex(index)
                }
            }

            ExtraKey("pwd") {
                onRunCommand("pwd")
            }

            ExtraKey("ls") {
                onRunCommand("ls -la")
            }

            ExtraKey("clear") {
                onRunCommand("clear")
            }
        }
    }
}
@Composable
private fun TerminalOutput(
    session: TerminalSessionState,
    command: String,
    inputEnabled: Boolean,
    onCommandChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState =
        rememberLazyListState()

    LaunchedEffect(
        session.id,
        session.lines.size
    ) {
        if (session.lines.isNotEmpty()) {
            listState.scrollToItem(
                session.lines.lastIndex
            )
        }
    }

    BasicTextField(
        value = command,
        onValueChange =
            onCommandChange,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .background(
                    Color(0xFF030609),
                    RoundedCornerShape(16.dp)
                ),
        enabled = inputEnabled,
        singleLine = true,
        textStyle =
            TextStyle(
                color = TerminalText,
                fontFamily =
                    FontFamily.Monospace,
                fontSize = 12.sp
            ),
        keyboardOptions =
            KeyboardOptions(
                imeAction =
                    ImeAction.Send
            ),
        keyboardActions =
            KeyboardActions(
                onSend = {
                    onSubmit()
                }
            ),
        decorationBox = { innerTextField ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 12.dp
                            ),
                    verticalArrangement =
                        Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        session.lines,
                        key = { index, _ ->
                            "${session.id}-$index"
                        }
                    ) { _, line ->
                        Text(
                            line.text.ifEmpty {
                                " "
                            },
                            color =
                                when (line.kind) {
                                    TerminalLineKind.PROMPT ->
                                        TerminalPrimary

                                    TerminalLineKind.INFO ->
                                        TerminalSecondary

                                    TerminalLineKind.SUCCESS ->
                                        TerminalPrimary

                                    TerminalLineKind.WARNING ->
                                        TerminalWarning

                                    TerminalLineKind.ERROR ->
                                        TerminalError

                                    TerminalLineKind.OUTPUT ->
                                        TerminalText
                                },
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize =
                                12.sp,
                            lineHeight =
                                16.sp
                        )
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 12.dp,
                                vertical = 10.dp
                            ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        "$ ",
                        color = TerminalPrimary,
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize = 12.sp
                    )

                    Box(
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        if (command.isEmpty()) {
                            Text(
                                if (inputEnabled) {
                                    "Terminale dokun ve yaz…"
                                } else {
                                    "Komut çalışıyor…"
                                },
                                color = TerminalMuted,
                                fontFamily =
                                    FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }

                        innerTextField()
                    }
                }
            }
        }
    )
}

@Composable
private fun ExtraKey(
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier =
            Modifier
                .background(
                    TerminalSurfaceRaised,
                    RoundedCornerShape(10.dp)
                )
                .padding(
                    horizontal = 2.dp
                )
    ) {
        Text(
            label,
            color =
                TerminalSecondary,
            fontFamily =
                FontFamily.Monospace,
            fontSize =
                11.sp
        )
    }
}
