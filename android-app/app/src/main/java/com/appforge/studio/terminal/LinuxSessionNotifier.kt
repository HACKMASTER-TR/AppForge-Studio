package com.appforge.studio.terminal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.appforge.studio.MainActivity
import com.appforge.studio.R

internal object LinuxSessionNotifier {
    private const val CHANNEL_ID =
        "appforge-linux-terminal"

    fun notifyCompleted(
        context: Context,
        title: String,
        exitCode: Int
    ) {
        val appContext =
            context.applicationContext

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager =
            appContext.getSystemService(
                NotificationManager::class.java
            )
                ?: return

        ensureChannel(manager)

        val openIntent =
            PendingIntent.getActivity(
                appContext,
                title.hashCode(),
                Intent(
                    appContext,
                    MainActivity::class.java
                ).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val successful =
            exitCode == 0

        val notification =
            NotificationCompat.Builder(
                appContext,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.mipmap.ic_launcher
                )
                .setContentTitle(
                    "$title tamamlandı"
                )
                .setContentText(
                    if (successful) {
                        "Linux oturumu başarıyla tamamlandı."
                    } else {
                        "Linux oturumu $exitCode çıkış koduyla tamamlandı."
                    }
                )
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()

        manager.notify(
            NOTIFICATION_BASE +
                (title.hashCode() and 0x0fff),
            notification
        )
    }

    private fun ensureChannel(
        manager: NotificationManager
    ) {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Linux terminal oturumları",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Arka plandaki Linux PTY oturumları tamamlandığında bildirim gösterir."
            }
        )
    }

    private const val NOTIFICATION_BASE =
        42_000
}
