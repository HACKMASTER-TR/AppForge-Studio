package com.appforge.studio.terminal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal object TerminalNotificationPermission {
    fun isGranted(context: Context): Boolean {
        val appContext = context.applicationContext

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return NotificationManagerCompat
            .from(appContext)
            .areNotificationsEnabled()
    }
}

@Composable
internal fun TerminalNotificationPermissionControl(
    onMessage: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT < 33) {
        return
    }

    val context = LocalContext.current

    var granted by
        remember(context.applicationContext) {
            mutableStateOf(
                TerminalNotificationPermission
                    .isGranted(context)
            )
        }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permissionGranted ->
            granted =
                permissionGranted &&
                    TerminalNotificationPermission
                        .isGranted(context)

            onMessage(
                if (granted) {
                    "Terminal bildirim izni etkin."
                } else {
                    "Bildirim izni verilmedi. Foreground Linux işi çalışabilir ancak sonuç bildirimi gösterilmeyebilir."
                }
            )
        }

    OutlinedButton(
        enabled = !granted,
        onClick = {
            launcher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    ) {
        Text(
            if (granted) {
                "Bildirim İzni ✓"
            } else {
                "Bildirim İzni"
            }
        )
    }
}
