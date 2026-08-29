package com.appforge.studio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.appforge.studio.build.BuildApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps a remote build visible after the Studio UI is backgrounded. */
class BuildProgressService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val buildId = intent?.getStringExtra(EXTRA_BUILD_ID) ?: return START_NOT_STICKY
        val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: return START_NOT_STICKY
        val apiKey = intent.getStringExtra(EXTRA_API_KEY) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "AppForge uygulaması"
        createChannel()
        startForeground(NOTIFICATION_ID, notification(appName, "Derleme arka planda hazırlanıyor", 0, true))
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val client = BuildApiClient(this@BuildProgressService, serverUrl, apiKey)
            while (true) {
                try {
                    val status = client.getBuild(buildId)
                    val active = status.status == "queued" || status.status == "building"
                    val text = if (active) "Derleme devam ediyor • %${status.progress}" else when (status.status) {
                        "success" -> "Derleme hazır. AppForge Studio'dan çıktıyı indirebilirsin."
                        "cancelled" -> "Derleme iptal edildi."
                        else -> "Derleme tamamlanamadı. Ayrıntılar için AppForge Studio'yu aç."
                    }
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification(appName, text, status.progress, active))
                    if (!active) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf(startId)
                        return@launch
                    }
                } catch (_: Throwable) {
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_ID,
                        notification(appName, "Derleme sunucuda devam ediyor • bağlantı yeniden denenecek", 0, true)
                    )
                }
                delay(5_000)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AppForge derlemeleri", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(appName: String, text: String, progress: Int, ongoing: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(appName)
            .setContentText(text)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0 && ongoing)
            .build()

    companion object {
        private const val CHANNEL_ID = "appforge_build_progress"
        private const val NOTIFICATION_ID = 7412
        private const val EXTRA_BUILD_ID = "build_id"
        private const val EXTRA_SERVER_URL = "server_url"
        private const val EXTRA_API_KEY = "api_key"
        private const val EXTRA_APP_NAME = "app_name"

        fun start(context: Context, buildId: String, serverUrl: String, apiKey: String, appName: String) {
            ContextCompat.startForegroundService(context, Intent(context, BuildProgressService::class.java).apply {
                putExtra(EXTRA_BUILD_ID, buildId)
                putExtra(EXTRA_SERVER_URL, serverUrl)
                putExtra(EXTRA_API_KEY, apiKey)
                putExtra(EXTRA_APP_NAME, appName)
            })
        }
    }
}
