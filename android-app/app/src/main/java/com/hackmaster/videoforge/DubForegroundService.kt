package com.hackmaster.videoforge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class DubForegroundService : Service(), AppVisibility.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = AtomicBoolean(false)
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAt = 0L
    private var lastProgress = 0
    private var lastMessage = "VideoForge Studio hazırlanıyor…"
    private var foregroundShown = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "VideoForge Studio", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Cihaz içi video dublaj ve çeviri ilerlemesi"
            }
        )
        AppVisibility.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (!running.compareAndSet(false, true)) {
            broadcast(0, "Zaten devam eden bir VideoForge işlemi var.", STATE_RUNNING)
            return START_REDELIVER_INTENT
        }

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_DUB
        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val queue = intent.getStringArrayListExtra(EXTRA_VIDEO_URIS).orEmpty()
        val sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL) ?: "Video"
        val options = StudioOptions.fromIntent(intent)

        startedAt = System.currentTimeMillis()
        lastProgress = 0
        lastMessage = "VideoForge Studio hazırlanıyor…"
        if (!AppVisibility.isForeground) showProgressNotification()
        acquireWakeLock()

        scope.launch {
            try {
                val models = ModelManager(this@DubForegroundService)
                if (!models.isReady()) {
                    models.ensureReady { pct, msg ->
                        val mapped = if (mode == MODE_MODELS) pct else (pct * 0.12).toInt()
                        update(mapped, msg)
                    }
                }

                if (mode == MODE_MODELS) {
                    update(100, "AI modelleri hazır.", STATE_MODELS_READY)
                    return@launch
                }

                val items = mutableListOf<Pair<Uri, String>>()
                var importedFile: java.io.File? = null

                if (mode == MODE_URL_DUB) {
                    require(!videoUrl.isNullOrBlank()) { "Video URL'si boş." }
                    update(13, "Video URL'den telefona alınıyor…")
                    val imported = UrlVideoImporter.download(this@DubForegroundService, videoUrl) { done, total ->
                        val p = if (total > 0L) (13 + (done * 10L / total)).toInt() else 16
                        update(p.coerceIn(13, 23), "Video URL'den telefona alınıyor…")
                    }
                    importedFile = imported
                    items += Uri.fromFile(imported) to (imported.name.ifBlank { "URL videosu" })
                } else if (mode == MODE_QUEUE) {
                    require(queue.isNotEmpty()) { "İşlem kuyruğu boş." }
                    queue.forEachIndexed { index, raw -> items += Uri.parse(raw) to "Kuyruk ${index + 1}" }
                } else {
                    require(!videoUri.isNullOrBlank()) { "Video seçilmedi." }
                    items += Uri.parse(videoUri) to sourceLabel
                }

                try {
                    for ((itemIndex, item) in items.withIndex()) {
                        val (uri, label) = item
                        val perItemOptions = options.copy(previewOnly = options.previewOnly && items.size == 1)
                        val engine = OfflineDubEngine(this@DubForegroundService, models)
                        val result = engine.run(
                            input = uri,
                            options = perItemOptions,
                            checkpointKey = uri.toString()
                        ) { pct, msg ->
                            val baseStart = if (mode == MODE_URL_DUB) 24 else 13
                            val usable = 86
                            val queueBase = itemIndex.toDouble() / items.size
                            val queuePart = pct.toDouble() / 100.0 / items.size
                            val mapped = baseStart + ((queueBase + queuePart) * usable).toInt()
                            val prefix = if (items.size > 1) "[${itemIndex + 1}/${items.size}] " else ""
                            update(mapped.coerceAtMost(99), prefix + msg)
                        }

                        HistoryStore(this@DubForegroundService).add(
                            HistoryEntry(
                                timestamp = System.currentTimeMillis(),
                                sourceLabel = label,
                                inputUri = uri.toString(),
                                outputUri = result.videoUri.toString(),
                                subtitleUri = result.subtitleUri?.toString(),
                                targetLanguage = perItemOptions.targetLanguage,
                                preview = result.preview,
                                turns = result.turns,
                                speakers = result.speakers
                            )
                        )

                        val summary = if (result.preview) {
                            "Önizleme hazır • ${result.turns} konuşma bölümü"
                        } else {
                            "Tamamlandı • ${result.speakers} konuşmacı • ${result.turns} konuşma bölümü"
                        }
                        broadcastResult(
                            pct = if (itemIndex == items.lastIndex) 100 else ((itemIndex + 1) * 100 / items.size),
                            message = summary,
                            state = if (itemIndex == items.lastIndex) STATE_DONE else STATE_RUNNING,
                            outputUri = result.videoUri.toString(),
                            subtitleUri = result.subtitleUri?.toString(),
                            inputUri = uri.toString()
                        )
                    }
                    update(100, if (items.size > 1) "Kuyruktaki ${items.size} video tamamlandı." else "VideoForge işlemi tamamlandı.", STATE_DONE)
                } finally {
                    importedFile?.delete()
                }
            } catch (t: Throwable) {
                update(0, friendlyError(t), STATE_ERROR)
            } finally {
                running.set(false)
                releaseWakeLock()
                removeProgressNotification()
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onAppForegroundChanged(isForeground: Boolean) {
        if (isForeground) {
            removeProgressNotification()
            notificationManager.cancel(COMPLETION_NOTIFICATION_ID)
        } else if (running.get()) {
            showProgressNotification()
        }
    }

    override fun onDestroy() {
        AppVisibility.removeListener(this)
        releaseWakeLock()
        removeProgressNotification()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun update(pct: Int, message: String, state: String = STATE_RUNNING) {
        val normalized = if (state == STATE_RUNNING) maxOf(lastProgress, pct.coerceIn(0, 99)) else pct.coerceIn(0, 100)
        if (state == STATE_RUNNING) lastProgress = normalized
        val withEta = if (state == STATE_RUNNING) "$message${etaText(normalized)}" else message
        lastProgress = normalized
        lastMessage = withEta

        if (state == STATE_RUNNING) {
            if (!AppVisibility.isForeground) showProgressNotification()
        } else {
            removeProgressNotification()
            if (!AppVisibility.isForeground) showCompletionNotification(withEta, state != STATE_ERROR)
        }
        broadcast(normalized, withEta, state)
    }

    private fun showProgressNotification() {
        if (!running.get()) return
        val n = progressNotification(lastProgress, lastMessage)
        if (!foregroundShown) {
            startForeground(NOTIFICATION_ID, n)
            foregroundShown = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, n)
        }
    }

    private fun removeProgressNotification() {
        if (foregroundShown) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            foregroundShown = false
        }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun showCompletionNotification(message: String, success: Boolean) {
        notificationManager.notify(COMPLETION_NOTIFICATION_ID, terminalNotification(message, success))
    }

    private fun etaText(pct: Int): String {
        if (pct < 5 || pct >= 100) return ""
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < 5_000) return ""
        val remaining = (elapsed * (100 - pct).toDouble() / pct).toLong().coerceAtLeast(0L)
        val min = remaining / 60_000
        val sec = (remaining % 60_000) / 1000
        return " • tahmini ${min}d ${sec}s"
    }

    private fun broadcast(pct: Int, message: String, state: String) {
        sendBroadcast(Intent(ACTION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_PROGRESS, pct)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_STATE, state)
        })
    }

    private fun broadcastResult(
        pct: Int,
        message: String,
        state: String,
        outputUri: String?,
        subtitleUri: String?,
        inputUri: String?
    ) {
        sendBroadcast(Intent(ACTION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_PROGRESS, pct)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_STATE, state)
            if (outputUri != null) putExtra(EXTRA_OUTPUT_URI, outputUri)
            if (subtitleUri != null) putExtra(EXTRA_SUBTITLE_URI, subtitleUri)
            if (inputUri != null) putExtra(EXTRA_INPUT_URI, inputUri)
        })
    }

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        1,
        Intent(this, VideoForgeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun progressNotification(pct: Int, message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VideoForge arka planda çalışıyor")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setProgress(100, pct.coerceIn(0, 99), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent())
            .build()

    private fun terminalNotification(message: String, success: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (success) "VideoForge tamamlandı" else "VideoForge işlemi durdu")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openIntent())
            .build()

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoForge:Studio").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    private fun friendlyError(t: Throwable): String {
        val raw = t.message.orEmpty()
        return when {
            raw.contains("network", true) || raw.contains("HTTP", true) ->
                "Ağ işlemi başarısız. Bağlantıyı veya video URL'sini kontrol et. ($raw)"
            raw.contains("TTS", true) || raw.contains("metin-okuma", true) -> raw
            raw.contains("memory", true) || raw.contains("OutOfMemory", true) ->
                "Telefon belleği yetersiz kaldı. Kaliteyi 'Hızlı' seç veya başka uygulamaları kapat."
            else -> raw.ifBlank { t::class.java.simpleName }
        }
    }

    companion object {
        const val ACTION_START = "com.hackmaster.videoforge.START"
        const val ACTION_UPDATE = "com.hackmaster.videoforge.UPDATE"

        const val EXTRA_MODE = "mode"
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_URIS = "video_uris"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_SOURCE_LABEL = "source_label"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_STATE = "state"
        const val EXTRA_OUTPUT_URI = "output_uri"
        const val EXTRA_SUBTITLE_URI = "subtitle_uri"
        const val EXTRA_INPUT_URI = "input_uri"

        const val MODE_MODELS = "models"
        const val MODE_DUB = "dub"
        const val MODE_URL_DUB = "url_dub"
        const val MODE_QUEUE = "queue"

        const val STATE_RUNNING = "running"
        const val STATE_MODELS_READY = "models_ready"
        const val STATE_DONE = "done"
        const val STATE_ERROR = "error"

        private const val CHANNEL_ID = "videoforge_studio_v412"
        private const val NOTIFICATION_ID = 4420
        private const val COMPLETION_NOTIFICATION_ID = 4421

        fun clearVisibleNotifications(context: android.content.Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIFICATION_ID)
            nm.cancel(COMPLETION_NOTIFICATION_ID)
        }
    }
}
