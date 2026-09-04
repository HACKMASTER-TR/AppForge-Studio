package com.appforge.studio.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.security.SecureAccountStore
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun GitWorkspacePanel(
    workspace: File
) {
    val context = LocalContext.current

    val scope =
        rememberCoroutineScope()

    val githubConnection =
        remember {
            SecureAccountStore.loadExternalConnection(
                context,
                "github"
            )
        }

    var output by
        remember(workspace.absolutePath) {
            mutableStateOf(
                "Durumu görmek için Kontrol Et'e dokunun."
            )
        }

    var busy by
        remember {
            mutableStateOf(false)
        }

    var remoteUrl by
        remember(workspace.absolutePath) {
            mutableStateOf("")
        }

    var username by
        remember {
            mutableStateOf("")
        }

    var token by
        remember {
            mutableStateOf("")
        }

    var commitMessage by
        remember {
            mutableStateOf("")
        }

    var authorName by
        remember {
            mutableStateOf("AppForge User")
        }

    var authorEmail by
        remember {
            mutableStateOf("appforge@local")
        }

    fun credentials() =
        GitCredentials(
            username =
                username.ifBlank {
                    githubConnection
                        ?.accountLabel
                        ?.substringBefore(" • ")
                        .orEmpty()
                },
            token =
                token.ifBlank {
                    githubConnection
                        ?.accessToken
                        .orEmpty()
                },
            allowedHosts =
                if (
                    token.isBlank() &&
                    githubConnection != null
                ) {
                    setOf("github.com")
                } else {
                    emptySet()
                }
        )

    fun runAction(
        action: suspend () -> String
    ) {
        if (busy) {
            return
        }

        busy =
            true

        scope.launch {
            output =
                runCatching {
                    action()
                }.getOrElse {
                    "Git işlemi başarısız: ${it.message ?: "Bilinmeyen hata"}"
                }

            busy =
                false
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        item {
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
                        Modifier.padding(15.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                "Gömülü Git motoru",
                                color =
                                    TerminalText,
                                fontWeight =
                                    FontWeight.Black,
                                fontSize =
                                    18.sp
                            )

                            Text(
                                "Telefonda ayrıca git kurulumu gerektirmez.",
                                color =
                                    TerminalMuted,
                                fontSize =
                                    11.sp
                            )
                        }

                        if (busy) {
                            CircularProgressIndicator(
                                color =
                                    TerminalPrimary,
                                strokeWidth =
                                    2.dp
                            )
                        }
                    }

                    Text(
                        terminalRelativePath(
                            workspace,
                            workspace
                        ),
                        color =
                            TerminalPrimary,
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            11.sp
                    )
                }
            }
        }

        item {
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
                        runAction {
                            GitWorkspaceService.status(
                                workspace
                            )
                        }
                    },
                    enabled =
                        !busy
                ) {
                    Text("Kontrol Et")
                }

                OutlinedButton(
                    onClick = {
                        runAction {
                            GitWorkspaceService.init(
                                workspace
                            )
                        }
                    },
                    enabled =
                        !busy
                ) {
                    Text("Git Başlat")
                }

                OutlinedButton(
                    onClick = {
                        runAction {
                            GitWorkspaceService.stageAll(
                                workspace
                            )
                        }
                    },
                    enabled =
                        !busy
                ) {
                    Text("Tümünü Ekle")
                }

                OutlinedButton(
                    onClick = {
                        runAction {
                            GitWorkspaceService.log(
                                workspace
                            )
                        }
                    },
                    enabled =
                        !busy
                ) {
                    Text("Geçmiş")
                }
            }
        }

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalSurface
                    )
            ) {
                SelectionContainer {
                    Text(
                        output,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(13.dp),
                        color =
                            if (
                                output.contains(
                                    "başarısız",
                                    ignoreCase = true
                                )
                            ) {
                                TerminalError
                            } else {
                                TerminalText
                            },
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            11.sp,
                        lineHeight =
                            15.sp
                    )
                }
            }
        }

        item {
            TerminalPanelTitle(
                "Commit oluştur",
                "Değişiklikleri önce Tümünü Ekle ile hazırlayın."
            )
        }

        item {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = {
                        commitMessage = it.take(500)
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("Commit mesajı")
                    },
                    singleLine = true
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedTextField(
                        value = authorName,
                        onValueChange = {
                            authorName = it.take(160)
                        },
                        modifier =
                            Modifier.weight(1f),
                        label = {
                            Text("Ad")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = authorEmail,
                        onValueChange = {
                            authorEmail = it.take(254)
                        },
                        modifier =
                            Modifier.weight(1f),
                        label = {
                            Text("E-posta")
                        },
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        runAction {
                            GitWorkspaceService.commit(
                                workspace = workspace,
                                message = commitMessage,
                                authorName = authorName,
                                authorEmail = authorEmail
                            ).also {
                                commitMessage =
                                    ""
                            }
                        }
                    },
                    enabled =
                        !busy &&
                            commitMessage.isNotBlank()
                ) {
                    Text("Commit Oluştur")
                }
            }
        }

        item {
            TerminalPanelTitle(
                "GitHub / uzak depo",
                if (githubConnection == null) {
                    "HTTPS adresi kullanılır. Bağlantılar sekmesinden GitHub hesabını yetkilendirebilir veya geçici token girebilirsin."
                } else {
                    "Bağlı GitHub hesabı: ${githubConnection.accountLabel.ifBlank { "GitHub" }}. Şifreli token uzak adrese yazılmadan kullanılır."
                }
            )
        }

        item {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = {
                        remoteUrl = it.take(2_048)
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("Depo adresi")
                    },
                    placeholder = {
                        Text(
                            "https://github.com/kullanici/proje.git"
                        )
                    },
                    singleLine = true
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it.take(512)
                        },
                        modifier =
                            Modifier.weight(1f),
                        label = {
                            Text("Kullanıcı adı")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it.take(32 * 1_024)
                        },
                        modifier =
                            Modifier.weight(1f),
                        label = {
                            Text(
                                if (githubConnection == null) {
                                    "Token"
                                } else {
                                    "Geçici token (isteğe bağlı)"
                                }
                            )
                        },
                        visualTransformation =
                            PasswordVisualTransformation(),
                        singleLine = true
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
                            runAction {
                                GitWorkspaceService.setRemote(
                                    workspace,
                                    remoteUrl
                                )
                            }
                        },
                        enabled =
                            !busy &&
                                remoteUrl.isNotBlank()
                    ) {
                        Text("origin Ayarla")
                    }

                    Button(
                        onClick = {
                            runAction {
                                GitWorkspaceService.pull(
                                    workspace,
                                    credentials()
                                )
                            }
                        },
                        enabled =
                            !busy
                    ) {
                        Text("Pull")
                    }

                    Button(
                        onClick = {
                            runAction {
                                GitWorkspaceService.push(
                                    workspace,
                                    credentials()
                                )
                            }
                        },
                        enabled =
                            !busy
                    ) {
                        Text("Push")
                    }

                    OutlinedButton(
                        onClick = {
                            runAction {
                                val folder =
                                    GitWorkspaceService.clone(
                                        workspace,
                                        remoteUrl,
                                        credentials()
                                    )

                                "Depo indirildi: ${folder.absolutePath}"
                            }
                        },
                        enabled =
                            !busy &&
                                remoteUrl.isNotBlank()
                    ) {
                        Text("Clone")
                    }

                    OutlinedButton(
                        onClick = {
                            token =
                                ""
                        },
                        enabled =
                            token.isNotEmpty()
                    ) {
                        Text("Tokenı Temizle")
                    }
                }
            }
        }

        item {
            AdvancedGitPanel(
                workspace = workspace,
                githubToken =
                    token.ifBlank {
                        githubConnection?.accessToken.orEmpty()
                    }
            )
        }
    }
}

@Composable
internal fun TerminalPanelTitle(
    title: String,
    detail: String
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(3.dp)
    ) {
        Text(
            title,
            color =
                TerminalText,
            fontWeight =
                FontWeight.Black,
            fontSize =
                16.sp
        )

        Text(
            detail,
            color =
                TerminalMuted,
            fontSize =
                10.sp
        )
    }
}
