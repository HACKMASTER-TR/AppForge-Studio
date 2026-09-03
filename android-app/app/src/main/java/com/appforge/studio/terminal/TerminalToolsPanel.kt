package com.appforge.studio.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private data class QuickTerminalAction(
    val title: String,
    val description: String,
    val command: String
)

@Composable
internal fun TerminalToolsPanel(
    workspace: File,
    onRunCommand: (String) -> Unit,
    onOpenBuilder: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenGit: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSsh: () -> Unit
) {
    var capabilities by
        remember(workspace.absolutePath) {
            mutableStateOf<List<RuntimeCapability>>(
                emptyList()
            )
        }

    var loading by
        remember(workspace.absolutePath) {
            mutableStateOf(true)
        }

    LaunchedEffect(workspace.absolutePath) {
        loading =
            true

        capabilities =
            RuntimeInspector.inspect(
                workspace
            )

        loading =
            false
    }

    val quickActions =
        remember {
            listOf(
                QuickTerminalAction(
                    "Proje durumunu göster",
                    "Paket, teknoloji ve çalışma klasörü",
                    "appforge status"
                ),
                QuickTerminalAction(
                    "Dosyaları ayrıntılı listele",
                    "Gizli dosyalar ve boyutlarla birlikte",
                    "ls -la"
                ),
                QuickTerminalAction(
                    "Proje ağacını çıkar",
                    "İlk üç klasör seviyesini gösterir",
                    "find . -maxdepth 3 -print"
                ),
                QuickTerminalAction(
                    "Android cihaz bilgisini göster",
                    "Sürüm, üretici, model ve işlemci mimarisi",
                    "getprop ro.build.version.release; getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.product.cpu.abi"
                ),
                QuickTerminalAction(
                    "Git durumunu kontrol et",
                    "Gömülü Git motorunu terminalden kullanır",
                    "git status"
                )
            )
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        item {
            TerminalPanelTitle(
                "Runtime Merkezi",
                "AppForge cihazdaki araçları otomatik algılar; Git ve SSH kendi gömülü motorlarıyla her zaman kullanılabilir."
            )
        }

        if (loading) {
            item {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(9.dp)
                ) {
                    CircularProgressIndicator(
                        color =
                            TerminalPrimary,
                        strokeWidth =
                            2.dp
                    )

                    Text(
                        "Araçlar denetleniyor…",
                        color =
                            TerminalMuted
                    )
                }
            }
        } else {
            items(
                capabilities,
                key = {
                    it.command
                }
            ) { capability ->
                RuntimeCapabilityCard(
                    capability
                )
            }
        }

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            ColorForRuntimeCard
                    )
            ) {
                Column(
                    modifier =
                        Modifier.padding(15.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Python ve Node.js nasıl kullanılır?",
                        color =
                            TerminalText,
                        fontWeight =
                            FontWeight.Black
                    )

                    Text(
                        "Cihazın Android kabuğunda Python/Node kuruluysa Terminal sekmesi doğrudan çalıştırır. Kurulu değilse SSH sekmesinden kendi Linux sunucuna bağlan; apt, dnf, apk, Python, Node.js, npm, Git ve Docker komutlarını orada tam olarak kullanabilirsin.",
                        color =
                            TerminalMuted,
                        fontSize =
                            11.sp,
                        lineHeight =
                            16.sp
                    )

                    Button(
                        onClick = onOpenSsh
                    ) {
                        Text("SSH Ortamını Aç")
                    }
                }
            }
        }

        item {
            TerminalPanelTitle(
                "Hızlı işlemler",
                "Komutu ezberlemeden güvenli kısayolu çalıştır."
            )
        }

        items(
            quickActions,
            key = {
                it.command
            }
        ) { action ->
            Card(
                onClick = {
                    onRunCommand(
                        action.command
                    )
                },
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalSurface
                    )
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        ">_",
                        color =
                            TerminalPrimary,
                        fontFamily =
                            FontFamily.Monospace,
                        fontWeight =
                            FontWeight.Black
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text(
                            action.title,
                            color =
                                TerminalText,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            action.description,
                            color =
                                TerminalMuted,
                            fontSize =
                                10.sp
                        )

                        Text(
                            action.command,
                            color =
                                TerminalSecondary,
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize =
                                9.sp
                        )
                    }

                    Text(
                        "›",
                        color =
                            TerminalPrimary,
                        fontSize =
                            25.sp
                    )
                }
            }
        }

        item {
            TerminalPanelTitle(
                "AppForge bağlantıları",
                "Kod, Git, AI ve derleme akışları aynı proje çalışma alanını kullanır."
            )
        }

        item {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    Button(
                        onClick = onOpenBuilder,
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("APK / AAB Derle")
                    }

                    Button(
                        onClick = onOpenAi,
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("AI Asistan")
                    }
                }

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenFiles,
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Dosyalar")
                    }

                    OutlinedButton(
                        onClick = onOpenGit,
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Git")
                    }

                    OutlinedButton(
                        onClick = onOpenSsh,
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("SSH")
                    }
                }

                OutlinedButton(
                    onClick = onOpenConnections,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GitHub / Railway Bağlantıları")
                }
            }
        }
    }
}

@Composable
private fun RuntimeCapabilityCard(
    capability: RuntimeCapability
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    TerminalSurface
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (capability.available) {
                    "✓"
                } else {
                    "○"
                },
                color =
                    if (capability.available) {
                        TerminalPrimary
                    } else {
                        TerminalWarning
                    },
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    18.sp
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    capability.name,
                    color =
                        TerminalText,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    capability.detail,
                    color =
                        TerminalMuted,
                    fontSize =
                        10.sp
                )
            }

            Text(
                capability.command,
                color =
                    TerminalSecondary,
                fontFamily =
                    FontFamily.Monospace,
                fontSize =
                    10.sp
            )
        }
    }
}

private val ColorForRuntimeCard =
    androidx.compose.ui.graphics.Color(
        0xFF102136
    )
