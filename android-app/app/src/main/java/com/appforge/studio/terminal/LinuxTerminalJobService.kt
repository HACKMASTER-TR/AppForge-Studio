package com.appforge.studio.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.appforge.studio.MainActivity
import com.appforge.studio.R
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal object LinuxBackgroundJobCoordinator {
    fun protect(
        context: Context,
        state: LinuxManagedPtySessionState
    ) {
        require(
            state.running &&
                !state.restored &&
                UUID_PATTERN.matches(state.id)
        ) {
            "Yalnız canlı Linux PTY oturumu arka planda korunabilir."
        }

        val appContext = context.applicationContext
        LinuxBackgroundJobStore.initialize(appContext)

        ContextCompat.startForegroundService(
            appContext,
            Intent(
                appContext,
                LinuxTerminalJobService::class.java
            ).apply {
                action = LinuxTerminalJobService.ACTION_PROTECT
                putExtra(
                    LinuxTerminalJobService.EXTRA_SESSION_ID,
                    state.id
                )
            }
        )
    }

    private val UUID_PATTERN = Regex("^[0-9a-fA-F-]{36}$")
}

class LinuxTerminalJobService : Service() {
    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate
        )

    private val protectedSessions = LinkedHashSet<String>()
    private val cancelRequested = HashSet<String>()

    private lateinit var notificationManager: NotificationManager
    private var foregroundSessionId: String? = null

    override fun onCreate() {
        super.onCreate()

        notificationManager =
            requireNotNull(
                getSystemService(NotificationManager::class.java)
            )

        ensureChannel()
        LinuxPtySessionRegistry.initialize(applicationContext)
        LinuxBackgroundJobStore.initialize(applicationContext)

        serviceScope.launch {
            LinuxPtySessionRegistry.states
                .collectLatest { states ->
                    val byId = states.associateBy { it.id }
                    protectedSessions
                        .toList()
                        .forEach { sessionId ->
                            val state = byId[sessionId]

                            when {
                                state == null ->
                                    finishProtectedSession(
                                        sessionId = sessionId,
                                        exitCode = null,
                                        forcedStatus =
                                            LinuxBackgroundJobStatus.FAILED
                                    )

                                !state.running &&
                                    !state.starting &&
                                    state.exitCode != null ->
                                    finishProtectedSession(
                                        sessionId = sessionId,
                                        exitCode = state.exitCode,
                                        forcedStatus = null
                                    )
                            }
                        }
                }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val sessionId =
            intent
                ?.getStringExtra(EXTRA_SESSION_ID)
                ?.takeIf { UUID_PATTERN.matches(it) }

        when (intent?.action) {
            ACTION_PROTECT -> {
                if (sessionId == null) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                beginProtection(
                    sessionId = sessionId,
                    startId = startId
                )
            }

            ACTION_CANCEL -> {
                if (sessionId != null) {
                    requestCancel(sessionId)
                }
            }

            else -> {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(
        startId: Int,
        fgsType: Int
    ) {
        val timedOutSessions =
            protectedSessions.toList()

        protectedSessions.clear()
        cancelRequested.clear()
        foregroundSessionId = null

        timedOutSessions.forEach { sessionId ->
            val title = sessionTitle(sessionId)

            LinuxBackgroundJobStore.markFinished(
                applicationContext,
                sessionId,
                LinuxBackgroundJobStatus.TIMED_OUT
            )
            notificationManager.cancel(
                notificationId(sessionId)
            )
            LinuxPtySessionRegistry.terminate(sessionId)
            postFinalNotification(
                sessionId = sessionId,
                title = title,
                status = LinuxBackgroundJobStatus.TIMED_OUT,
                exitCode = null
            )
        }

        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE
        )

        // If the platform invokes an FGS timeout callback, immediately
        // stop protected PTYs so no process is left running unprotected.
        stopSelf()
    }

    override fun onDestroy() {
        val orphanedSessions =
            protectedSessions.toList()

        protectedSessions.clear()
        cancelRequested.clear()
        foregroundSessionId = null

        orphanedSessions.forEach { sessionId ->
            LinuxBackgroundJobStore.markFinished(
                applicationContext,
                sessionId,
                LinuxBackgroundJobStatus.PROCESS_LOST
            )
            notificationManager.cancel(
                notificationId(sessionId)
            )
            LinuxPtySessionRegistry.terminate(sessionId)
        }

        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE
        )
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginProtection(
        sessionId: String,
        startId: Int
    ) {
        val state =
            LinuxPtySessionRegistry.states.value
                .firstOrNull { it.id == sessionId }

        if (
            state == null ||
            !state.running ||
            state.restored
        ) {
            stopSelf(startId)
            return
        }

        LinuxBackgroundJobStore.markActive(
            applicationContext,
            state
        )

        protectedSessions += sessionId
        cancelRequested -= sessionId

        runCatching {
            showRunningNotifications()
        }.onFailure {
            rollbackForegroundStart(
                sessionId = sessionId,
                startId = startId
            )
        }
    }

    private fun requestCancel(sessionId: String) {
        if (sessionId !in protectedSessions) return

        cancelRequested += sessionId
        LinuxBackgroundJobStore.markCancelling(
            applicationContext,
            sessionId
        )
        LinuxPtySessionRegistry.terminate(sessionId)
    }

    private fun finishProtectedSession(
        sessionId: String,
        exitCode: Int?,
        forcedStatus: LinuxBackgroundJobStatus?
    ) {
        if (sessionId !in protectedSessions) return

        val status =
            forcedStatus
                ?: when {
                    sessionId in cancelRequested ->
                        LinuxBackgroundJobStatus.CANCELLED
                    exitCode == 0 ->
                        LinuxBackgroundJobStatus.COMPLETED
                    else ->
                        LinuxBackgroundJobStatus.FAILED
                }

        val title = sessionTitle(sessionId)

        LinuxBackgroundJobStore.markFinished(
            applicationContext,
            sessionId,
            status
        )

        protectedSessions -= sessionId
        cancelRequested -= sessionId
        notificationManager.cancel(notificationId(sessionId))

        if (foregroundSessionId == sessionId) {
            foregroundSessionId = null
        }

        if (protectedSessions.isEmpty()) {
            ServiceCompat.stopForeground(
                this,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            stopSelf()
        } else {
            runCatching {
                showRunningNotifications()
            }.onFailure {
                abortRemainingProtection()
            }
        }

        postFinalNotification(
            sessionId = sessionId,
            title = title,
            status = status,
            exitCode = exitCode
        )
    }

    private fun rollbackForegroundStart(
        sessionId: String,
        startId: Int
    ) {
        val title = sessionTitle(sessionId)

        protectedSessions -= sessionId
        cancelRequested -= sessionId
        notificationManager.cancel(
            notificationId(sessionId)
        )

        LinuxBackgroundJobStore.markFinished(
            applicationContext,
            sessionId,
            LinuxBackgroundJobStatus.FAILED
        )
        LinuxPtySessionRegistry.terminate(sessionId)
        postFinalNotification(
            sessionId = sessionId,
            title = title,
            status = LinuxBackgroundJobStatus.FAILED,
            exitCode = null
        )

        if (protectedSessions.isEmpty()) {
            foregroundSessionId = null
            ServiceCompat.stopForeground(
                this,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            stopSelf(startId)
        }
    }

    private fun abortRemainingProtection() {
        val remaining = protectedSessions.toList()

        protectedSessions.clear()
        cancelRequested.clear()
        foregroundSessionId = null

        remaining.forEach { sessionId ->
            val title = sessionTitle(sessionId)

            LinuxBackgroundJobStore.markFinished(
                applicationContext,
                sessionId,
                LinuxBackgroundJobStatus.FAILED
            )
            notificationManager.cancel(
                notificationId(sessionId)
            )
            LinuxPtySessionRegistry.terminate(sessionId)
            postFinalNotification(
                sessionId = sessionId,
                title = title,
                status = LinuxBackgroundJobStatus.FAILED,
                exitCode = null
            )
        }

        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE
        )
        stopSelf()
    }

    private fun showRunningNotifications() {
        val first = protectedSessions.firstOrNull()
            ?: return

        val firstState =
            LinuxPtySessionRegistry.states.value
                .firstOrNull { it.id == first }

        val foregroundNotification =
            runningNotification(
                sessionId = first,
                title = firstState?.title ?: "Linux"
            )

        val foregroundType =
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }

        ServiceCompat.startForeground(
            this,
            notificationId(first),
            foregroundNotification,
            foregroundType
        )
        foregroundSessionId = first

        protectedSessions
            .filterNot { it == first }
            .forEach { sessionId ->
                notificationManager.notify(
                    notificationId(sessionId),
                    runningNotification(
                        sessionId = sessionId,
                        title = sessionTitle(sessionId)
                    )
                )
            }
    }

    private fun runningNotification(
        sessionId: String,
        title: String
    ): Notification =
        NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$title arka planda çalışıyor")
            .setContentText(
                "AppForge Linux PTY oturumu foreground service ile korunuyor."
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(sessionId))
            .addAction(
                0,
                "İptal",
                cancelIntent(sessionId)
            )
            .build()

    private fun postFinalNotification(
        sessionId: String,
        title: String,
        status: LinuxBackgroundJobStatus,
        exitCode: Int?
    ) {
        if (!TerminalNotificationPermission.isGranted(this)) {
            return
        }

        val text =
            when (status) {
                LinuxBackgroundJobStatus.COMPLETED ->
                    "Arka plan Linux işi başarıyla tamamlandı."
                LinuxBackgroundJobStatus.CANCELLED ->
                    "Arka plan Linux işi iptal edildi."
                LinuxBackgroundJobStatus.TIMED_OUT ->
                    "Foreground service zaman aşımı nedeniyle durduruldu."
                LinuxBackgroundJobStatus.PROCESS_LOST ->
                    "Uygulama süreci sonlandığı için iş devam ediyor kabul edilmedi."
                else ->
                    if (exitCode != null) {
                        "Linux işi $exitCode çıkış koduyla başarısız oldu."
                    } else {
                        "Linux işi beklenmedik şekilde sonlandı."
                    }
            }

        notificationManager.notify(
            resultNotificationId(sessionId),
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("$title • ${statusLabel(status)}")
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppIntent(sessionId))
                .build()
        )
    }

    private fun openAppIntent(sessionId: String): PendingIntent =
        PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelIntent(sessionId: String): PendingIntent =
        PendingIntent.getService(
            this,
            sessionId.hashCode() xor 0x7b00,
            Intent(
                this,
                LinuxTerminalJobService::class.java
            ).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

    private fun sessionTitle(sessionId: String): String =
        LinuxPtySessionRegistry.states.value
            .firstOrNull { it.id == sessionId }
            ?.title
            ?.let { TerminalTextSanitizer.clean(it).take(40) }
            ?.ifBlank { "Linux" }
            ?: LinuxBackgroundJobStore.recent(applicationContext)
                .firstOrNull { it.sessionId == sessionId }
                ?.title
                ?: "Linux"

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Linux arka plan işleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Kullanıcının başlattığı uzun süreli Linux PTY işlerini ve sonuçlarını gösterir."
            }
        )
    }

    private fun notificationId(sessionId: String): Int =
        NOTIFICATION_BASE +
            (sessionId.hashCode() and 0x1fff)

    private fun resultNotificationId(sessionId: String): Int =
        RESULT_NOTIFICATION_BASE +
            (sessionId.hashCode() and 0x1fff)

    private fun statusLabel(status: LinuxBackgroundJobStatus): String =
        when (status) {
            LinuxBackgroundJobStatus.COMPLETED -> "Tamamlandı"
            LinuxBackgroundJobStatus.CANCELLED -> "İptal edildi"
            LinuxBackgroundJobStatus.TIMED_OUT -> "Zaman aşımı"
            LinuxBackgroundJobStatus.PROCESS_LOST -> "Süreç sonlandı"
            LinuxBackgroundJobStatus.FAILED -> "Başarısız"
            LinuxBackgroundJobStatus.ACTIVE -> "Çalışıyor"
            LinuxBackgroundJobStatus.CANCELLING -> "İptal ediliyor"
        }

    companion object {
        internal const val ACTION_PROTECT =
            "com.appforge.studio.terminal.PROTECT_LINUX_JOB"
        internal const val ACTION_CANCEL =
            "com.appforge.studio.terminal.CANCEL_LINUX_JOB"
        internal const val EXTRA_SESSION_ID =
            "session_id"

        private const val CHANNEL_ID =
            "appforge-linux-background-jobs"
        private const val NOTIFICATION_BASE = 53_000
        private const val RESULT_NOTIFICATION_BASE = 63_000
        private val UUID_PATTERN = Regex("^[0-9a-fA-F-]{36}$")
    }
}
