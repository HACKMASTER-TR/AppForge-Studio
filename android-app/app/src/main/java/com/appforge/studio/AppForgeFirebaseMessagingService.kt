package com.appforge.studio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppForgeFirebaseMessagingService :
    FirebaseMessagingService() {

    /*
     * Firebase Messaging registration API geçiş sürecinde
     * bu callback deprecated olarak işaretlenebiliyor.
     *
     * Callback hâlâ token yenilenmesini yakalamak için
     * Firebase tarafından desteklenen akışta kullanılıyor.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(
        token: String
    ) {
        getSharedPreferences(
            "appforge_fcm",
            MODE_PRIVATE
        )
            .edit()
            .putString(
                "token",
                token
            )
            .apply()
    }

    override fun onMessageReceived(
        message: RemoteMessage
    ) {
        val title =
            message.notification
                ?.title
                ?: message.data["title"]
                ?: "AppForge Studio"

        val body =
            message.notification
                ?.body
                ?: message.data["body"]
                ?: message.data["message"]
                ?: return

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channelId =
            "appforge_updates"

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "AppForge güncellemeleri",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val launchIntent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat
                .Builder(
                    this,
                    channelId
                )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    body
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(
                    true
                )
                .build()

        manager.notify(
            (
                System.currentTimeMillis() %
                    Int.MAX_VALUE
            ).toInt(),
            notification
        )
    }
}
