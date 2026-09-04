package com.appforge.studio.terminal

import android.os.CancellationSignal
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
internal fun TerminalSecurityCenterPanel(
    workspace: File
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var points by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf<
                List<TerminalRestorePoint>
                >(
                emptyList()
            )
        }

    var busy by
        remember {
            mutableStateOf(false)
        }

    var message by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf(
                "Restore point'ler yalnız uygulamanın özel depolama alanında tutulur."
            )
        }

    var pendingRestore by
        remember {
            mutableStateOf<
                TerminalRestorePoint?
                >(null)
        }

    var biometricCancellation by
        remember {
            mutableStateOf<
                CancellationSignal?
                >(null)
        }

    val biometricAvailable =
        remember {
            TerminalBiometricGuard
                .isAvailable(
                    context
                )
        }

    fun refresh() {
        scope.launch {
            points =
                runCatching {
                    TerminalRestorePointManager
                        .list(
                            context,
                            workspace
                        )
                }.getOrDefault(
                    emptyList()
                )
        }
    }

    fun restore(
        point: TerminalRestorePoint
    ) {
        if (busy) {
            return
        }

        busy =
            true

        scope.launch {
            runCatching {
                TerminalRestorePointManager
                    .restoreOverlay(
                        context,
                        workspace,
                        point
                    )
            }
                .onSuccess {
                    message =
                        "Geri yükleme tamamlandı: ${it.restoredFiles} dosya. Ekstra dosyalar güvenlik için silinmedi."

                    refresh()
                }
                .onFailure {
                    message =
                        "Geri yükleme başarısız: ${
                            it.message
                                ?: "Bilinmeyen hata"
                        }"
                }

            busy =
                false
        }
    }

    DisposableEffect(Unit) {
        refresh()

        onDispose {
            biometricCancellation
                ?.cancel()
        }
    }

    pendingRestore
        ?.let { point ->
            AlertDialog(
                onDismissRequest = {
                    if (!busy) {
                        pendingRestore =
                            null
                    }
                },
                title = {
                    Text(
                        "Restore point geri yüklensin mi?"
                    )
                },
                text = {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        Text(
                            "Mevcut dosyalar bu restore point'teki sürümlerle üzerine yazılabilir. Ekstra dosyalar silinmez."
                        )

                        Text(
                            point.archive.name,
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize =
                                10.sp
                        )

                        Text(
                            if (
                                biometricAvailable
                            ) {
                                "Devam etmek için cihaz biyometrik doğrulaması gerekir."
                            } else {
                                "Bu cihazda kullanılabilir platform biyometrisi algılanmadı; yalnız bu açık onay kullanılacak."
                            },
                            color =
                                TerminalWarning,
                            fontSize =
                                11.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val selected =
                                pendingRestore
                                    ?: return@Button

                            pendingRestore =
                                null

                            if (
                                biometricAvailable
                            ) {
                                biometricCancellation =
                                    TerminalBiometricGuard
                                        .authenticate(
                                            context =
                                                context,
                                            title =
                                                "AppForge geri yükleme",
                                            onSuccess = {
                                                biometricCancellation =
                                                    null
                                                restore(
                                                    selected
                                                )
                                            },
                                            onError = {
                                                biometricCancellation =
                                                    null
                                                message =
                                                    it
                                            }
                                        )
                            } else {
                                restore(
                                    selected
                                )
                            }
                        },
                        enabled =
                            !busy
                    ) {
                        Text(
                            if (
                                biometricAvailable
                            ) {
                                "Biyometrik Doğrula"
                            } else {
                                "Geri Yükle"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingRestore =
                                null
                        },
                        enabled =
                            !busy
                    ) {
                        Text(
                            "Vazgeç"
                        )
                    }
                }
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
                Modifier.padding(
                    14.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {
            Text(
                "Ultimate Güvenlik Merkezi",
                color =
                    TerminalText,
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    17.sp
            )

            Text(
                if (
                    biometricAvailable
                ) {
                    "✓ Biyometrik koruma kullanılabilir"
                } else {
                    "• Biyometrik koruma bu cihaz/API'de kullanılamıyor"
                },
                color =
                    if (
                        biometricAvailable
                    ) {
                        TerminalPrimary
                    } else {
                        TerminalMuted
                    },
                fontSize =
                    11.sp
            )

            TerminalStartupLockSettingsPanel()

            Text(
                "Restore point'ler .env, SSH/GPG anahtarları, keystore ve servis hesabı gibi hassas dosyaları arşive almaz.",
                color =
                    TerminalSecondary,
                fontSize =
                    10.sp,
                lineHeight =
                    15.sp
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        7.dp
                    )
            ) {
                Button(
                    onClick = {
                        if (busy) {
                            return@Button
                        }

                        busy =
                            true

                        scope.launch {
                            runCatching {
                                TerminalRestorePointManager
                                    .create(
                                        context,
                                        workspace
                                    )
                            }
                                .onSuccess {
                                    message =
                                        "Restore point hazır: ${it.archivedFiles} dosya • ${it.skippedSensitive} hassas dosya atlandı."

                                    refresh()
                                }
                                .onFailure {
                                    message =
                                        "Restore point oluşturulamadı: ${
                                            it.message
                                                ?: "Bilinmeyen hata"
                                        }"
                                }

                            busy =
                                false
                        }
                    },
                    enabled =
                        !busy
                ) {
                    Text(
                        if (busy) {
                            "İşleniyor…"
                        } else {
                            "Restore Point Oluştur"
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        refresh()
                    },
                    enabled =
                        !busy
                ) {
                    Text(
                        "Yenile"
                    )
                }
            }

            if (
                points.isEmpty()
            ) {
                Text(
                    "Henüz restore point yok.",
                    color =
                        TerminalMuted,
                    fontSize =
                        10.sp
                )
            } else {
                points.forEach { point ->
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
                                Modifier.padding(
                                    10.dp
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    5.dp
                                )
                        ) {
                            Text(
                                DateFormat
                                    .getDateTimeInstance(
                                        DateFormat.SHORT,
                                        DateFormat.SHORT
                                    )
                                    .format(
                                        Date(
                                            point.createdAt
                                        )
                                    ),
                                color =
                                    TerminalText,
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    11.sp
                            )

                            Text(
                                "${point.sizeBytes / 1024} KiB • ${point.archive.name}",
                                color =
                                    TerminalMuted,
                                fontFamily =
                                    FontFamily.Monospace,
                                fontSize =
                                    9.sp
                            )

                            OutlinedButton(
                                onClick = {
                                    pendingRestore =
                                        point
                                },
                                enabled =
                                    !busy
                            ) {
                                Text(
                                    "Geri Yükle…"
                                )
                            }
                        }
                    }
                }
            }

            Text(
                message,
                color =
                    if (
                        message.contains(
                            "başarısız",
                            ignoreCase =
                                true
                        ) ||
                        message.contains(
                            "oluşturulamadı",
                            ignoreCase =
                                true
                        )
                    ) {
                        TerminalError
                    } else {
                        TerminalMuted
                    },
                fontSize =
                    10.sp
            )

            Text(
                "Token güvenliği: bu merkez ağ isteği yapmaz, komut çalıştırmaz ve token/parola saklamaz. Mevcut bağlantı anahtarları Android Keystore AES-GCM kasasında kalır.",
                color =
                    TerminalSecondary,
                fontSize =
                    9.sp,
                lineHeight =
                    14.sp
            )
        }
    }
}
