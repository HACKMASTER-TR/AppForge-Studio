package com.appforge.studio.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch

@Composable
internal fun LinuxRuntimePanel(
    workspace: File,
    onOpenSsh: () -> Unit
) {
    val context =
        LocalContext.current

    val manager =
        remember(context.applicationContext) {
            AndroidLinuxRuntimeManager(
                context.applicationContext
            )
        }

    val scope =
        rememberCoroutineScope()

    var refreshNonce by
        remember {
            mutableIntStateOf(0)
        }

    var installProgress by
        remember {
            mutableStateOf<LinuxInstallProgress?>(
                null
            )
        }

    var installError by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    var installing by
        remember {
            mutableStateOf(false)
        }

    var distribution by
        remember {
            mutableStateOf(
                LinuxDistribution.UBUNTU
            )
        }

    val status =
        remember(
            distribution,
            workspace.absolutePath,
            refreshNonce
        ) {
            manager.inspect(distribution)
        }

    val installableManifest =
        remember(
            distribution,
            status.architecture
        ) {
            manager.installableManifest(
                distribution
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
                "Rootless Linux Runtime",
                color =
                    TerminalText,
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    17.sp
            )

            Text(
                "Aşama 2D çoklu PTY katmanı. Doğrulanmış Linux üzerinde çoklu/bölünmüş oturum, sekmeler arasında arka plan devamı, tamamlanma bildirimi ve güvenli oturum profili geri yükleme.",
                color =
                    TerminalMuted,
                fontSize =
                    11.sp,
                lineHeight =
                    16.sp
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
                LinuxDistribution.entries.forEach { item ->
                    if (item == distribution) {
                        Button(
                            onClick = {
                                distribution = item
                            }
                        ) {
                            Text(item.title)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                distribution = item
                            }
                        ) {
                            Text(item.title)
                        }
                    }
                }
            }

            LinuxStatusLine(
                title = "Dağıtım",
                value = status.distribution.title
            )

            LinuxStatusLine(
                title = "Mimari",
                value =
                    status.architecture
                        ?.let {
                            "${it.id} / ${it.distroArchitecture}"
                        }
                        ?: "Desteklenmiyor"
            )

            LinuxStatusLine(
                title = "Native engine",
                value =
                    if (status.engineBundled) {
                        "APK içinde bulundu"
                    } else {
                        "Henüz paketlenmedi"
                    }
            )

            LinuxStatusLine(
                title = "Rootfs",
                value =
                    if (status.rootfsInstalled) {
                        if (status.rootfsTrusted) {
                            "Kurulu ve doğrulanmış"
                        } else {
                            "Kurulu fakat güven doğrulaması tamamlanmadı"
                        }
                    } else {
                        "Kurulu değil"
                    }
            )

            Text(
                status.detail,
                color =
                    if (status.ready) {
                        TerminalPrimary
                    } else {
                        TerminalWarning
                    },
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    11.sp,
                lineHeight =
                    16.sp
            )

            Text(
                "Proje bağı: ${workspace.absolutePath} → /workspace",
                color =
                    TerminalSecondary,
                fontFamily =
                    FontFamily.Monospace,
                fontSize =
                    9.sp,
                lineHeight =
                    14.sp
            )

            if (status.ready) {
                LinuxMultiSessionTerminalPanel(
                    manager = manager,
                    distribution = distribution,
                    workspace = workspace
                )
            } else {
                Text(
                    "Gerçek PTY terminali yalnız native engine ve doğrulanmış rootfs birlikte Hazır olduğunda açılır.",
                    color = TerminalWarning,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }

            Text(
                "APT araç zincirleri",
                color =
                    TerminalText,
                fontWeight =
                    FontWeight.Black
            )

            LinuxToolchainCatalog.specs.forEach { spec ->
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        spec.title,
                        color =
                            TerminalPrimary,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            11.sp
                    )

                    Text(
                        spec.description,
                        color =
                            TerminalMuted,
                        fontSize =
                            9.sp
                    )

                    Text(
                        spec.packages.joinToString(" "),
                        color =
                            TerminalSecondary,
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            8.sp
                    )
                }
            }

            Text(
                "Örnek güvenli kurulum planı:",
                color =
                    TerminalText,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    10.sp
            )

            Text(
                TerminalSecretMasker.redact(
                    manager.toolchainCommand(
                        listOf(
                            LinuxToolchainId.BASE,
                            LinuxToolchainId.PYTHON,
                            LinuxToolchainId.NODE
                        )
                    )
                ),
                color =
                    TerminalPrimary,
                fontFamily =
                    FontFamily.Monospace,
                fontSize =
                    8.sp,
                lineHeight =
                    13.sp
            )

            Text(
                "Doğrulanmış rootfs kurulumu",
                color =
                    TerminalText,
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    11.sp
            )

            if (installableManifest == null) {
                Text(
                    if (distribution == LinuxDistribution.DEBIAN) {
                        "Debian profili hazır; ancak resmi, sabit SHA-256 ile pinlenmiş rootfs artefaktı henüz onaylanmadığı için kurulum bilerek kapalı."
                    } else {
                        "Bu mimari için doğrulanmış Ubuntu Base manifesti bulunmuyor."
                    },
                    color =
                        TerminalWarning,
                    fontSize =
                        10.sp,
                    lineHeight =
                        15.sp
                )
            } else {
                Text(
                    "${installableManifest.distribution.title} ${installableManifest.release} • ${installableManifest.architecture.distroArchitecture}",
                    color =
                        TerminalPrimary,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        10.sp
                )

                Text(
                    "Kaynak: ${installableManifest.sourceUri.host} • SHA-256: ${installableManifest.archiveSha256.take(12)}…",
                    color =
                        TerminalSecondary,
                    fontFamily =
                        FontFamily.Monospace,
                    fontSize =
                        8.sp
                )

                Button(
                    enabled = !installing,
                    onClick = {
                        installing = true
                        installError = null
                        installProgress = null

                        scope.launch {
                            runCatching {
                                manager.installVerifiedRootfs(
                                    distribution
                                ) { progress ->
                                    scope.launch {
                                        installProgress =
                                            progress
                                    }
                                }
                            }.onSuccess {
                                refreshNonce += 1
                            }.onFailure { error ->
                                installError =
                                    error.message
                                        ?: "Rootfs kurulumu başarısız."
                            }

                            installing = false
                        }
                    }
                ) {
                    Text(
                        if (status.rootfsTrusted) {
                            "Rootfs'i Yeniden Doğrula / Kur"
                        } else {
                            "Doğrulanmış Rootfs Kur"
                        }
                    )
                }
            }

            installProgress?.let { progress ->
                Text(
                    buildString {
                        append(progress.detail)
                        progress.percent?.let {
                            append(" • %")
                            append(it)
                        }
                    },
                    color =
                        if (progress.stage == LinuxInstallStage.COMPLETE) {
                            TerminalPrimary
                        } else {
                            TerminalSecondary
                        },
                    fontSize =
                        9.sp,
                    lineHeight =
                        14.sp
                )
            }

            installError?.let { message ->
                Text(
                    TerminalSecretMasker.redact(
                        message
                    ),
                    color =
                        TerminalWarning,
                    fontSize =
                        9.sp,
                    lineHeight =
                        14.sp
                )
            }

            Text(
                "Kurucu hiçbir executable indirmez veya çalıştırmaz. PTY yalnız APK ile paketlenmiş, SHA-256 doğrulanmış Proroot launcher'ını forkpty üzerinden başlatır.",
                color =
                    TerminalMuted,
                fontSize =
                    10.sp,
                lineHeight =
                    15.sp
            )

            OutlinedButton(
                onClick = onOpenSsh
            ) {
                Text("Şimdi Uzak Linux / SSH Aç")
            }
        }
    }
}

@Composable
private fun LinuxStatusLine(
    title: String,
    value: String
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$title:",
            color =
                TerminalMuted,
            fontSize =
                10.sp,
            modifier =
                Modifier.weight(0.36f)
        )

        Text(
            value,
            color =
                TerminalText,
            fontWeight =
                FontWeight.Bold,
            fontSize =
                10.sp,
            modifier =
                Modifier.weight(0.64f)
        )
    }
}
