package com.appforge.studio.terminal

import android.content.Context
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object TerminalStartupLockPreferences {
    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    private const val PREFS_NAME =
        "appforge_terminal_startup_lock"
    private const val KEY_ENABLED =
        "biometric_enabled_v1"
}

@Composable
internal fun TerminalStartupBiometricGate(
    onUnlocked: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var message by remember {
        mutableStateOf("Terminal kilitli. Biyometrik doğrulama gerekiyor.")
    }
    var authenticating by remember {
        mutableStateOf(false)
    }
    var cancellation by remember {
        mutableStateOf<CancellationSignal?>(null)
    }

    val available =
        remember {
            TerminalBiometricGuard.isAvailable(context)
        }

    fun authenticate() {
        if (authenticating || !available) return

        authenticating = true
        cancellation =
            TerminalBiometricGuard.authenticate(
                context = context,
                title = "AppForge Terminal kilidi",
                onSuccess = {
                    authenticating = false
                    cancellation = null
                    onUnlocked()
                },
                onError = {
                    authenticating = false
                    cancellation = null
                    message = it
                }
            )
    }

    LaunchedEffect(available) {
        if (available) authenticate()
    }

    DisposableEffect(Unit) {
        onDispose {
            cancellation?.cancel()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = TerminalSurface
                )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Terminal Güvenlik Kilidi",
                    color = TerminalText,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )

                Text(
                    if (available) {
                        message
                    } else {
                        "Kilit açık fakat kullanılabilir platform biyometrisi bulunamadı. Terminal güvenlik nedeniyle açılmadı."
                    },
                    color = TerminalSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Button(
                    enabled = available && !authenticating,
                    onClick = { authenticate() }
                ) {
                    Text(
                        if (authenticating) {
                            "Doğrulanıyor…"
                        } else {
                            "Biyometrik Doğrula"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onBack
                ) {
                    Text("Terminalden Çık")
                }
            }
        }
    }
}

@Composable
internal fun TerminalStartupLockSettingsPanel() {
    val context = LocalContext.current

    var enabled by remember {
        mutableStateOf(
            TerminalStartupLockPreferences.isEnabled(context)
        )
    }
    var message by remember {
        mutableStateOf(
            if (enabled) {
                "Terminal açılış biyometrik kilidi etkin."
            } else {
                "Terminal açılış biyometrik kilidi kapalı."
            }
        )
    }
    var cancellation by remember {
        mutableStateOf<CancellationSignal?>(null)
    }

    val available =
        remember {
            TerminalBiometricGuard.isAvailable(context)
        }

    DisposableEffect(Unit) {
        onDispose {
            cancellation?.cancel()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = TerminalBackground
            )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "Terminal Açılış Kilidi",
                color = TerminalText,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Text(
                message,
                color = TerminalSecondary,
                fontSize = 10.sp
            )

            Button(
                enabled = available,
                onClick = {
                    cancellation?.cancel()
                    cancellation =
                        TerminalBiometricGuard.authenticate(
                            context = context,
                            title =
                                if (enabled) {
                                    "Terminal kilidini kapat"
                                } else {
                                    "Terminal kilidini aç"
                                },
                            onSuccess = {
                                val next = !enabled
                                TerminalStartupLockPreferences.setEnabled(
                                    context,
                                    next
                                )
                                enabled = next
                                cancellation = null
                                message =
                                    if (next) {
                                        "Terminal açılış biyometrik kilidi etkin. Sonraki Terminal açılışında doğrulama istenecek."
                                    } else {
                                        "Terminal açılış biyometrik kilidi kapatıldı."
                                    }
                            },
                            onError = {
                                cancellation = null
                                message = it
                            }
                        )
                }
            ) {
                Text(
                    if (enabled) {
                        "Kilidi Kapat"
                    } else {
                        "Kilidi Aç"
                    }
                )
            }
        }
    }
}
