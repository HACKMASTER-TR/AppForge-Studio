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
import org.json.JSONArray

/**
 * Normal build ve 5 Build Testi'ni uygulama arka plandayken
 * foreground service olarak izler.
 */
class BuildProgressService : Service() {

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private var monitorJob: Job? =
        null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val prefs =
            getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val mode =
            intent
                ?.getStringExtra(
                    EXTRA_MODE
                )
                ?: prefs.getString(
                    EXTRA_MODE,
                    MODE_SINGLE
                )
                ?: MODE_SINGLE

        val serverUrl =
            intent
                ?.getStringExtra(
                    EXTRA_SERVER_URL
                )
                ?: prefs.getString(
                    EXTRA_SERVER_URL,
                    ""
                )
                .orEmpty()

        val apiKey =
            intent
                ?.getStringExtra(
                    EXTRA_API_KEY
                )
                ?: prefs.getString(
                    EXTRA_API_KEY,
                    ""
                )
                .orEmpty()

        val appName =
            intent
                ?.getStringExtra(
                    EXTRA_APP_NAME
                )
                ?: prefs.getString(
                    EXTRA_APP_NAME,
                    "AppForge uygulaması"
                )
                .orEmpty()

        if (serverUrl.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createChannel()

        if (mode == MODE_BATCH) {
            startForeground(
                NOTIFICATION_ID,
                notification(
                    appName =
                        "AppForge Studio",
                    text =
                        "5 Build Testi arka planda hazırlanıyor",
                    progress = 0,
                    ongoing = true,
                    buildId = null,
                    serverUrl = serverUrl
                )
            )
        } else {
            val buildId =
                intent
                    ?.getStringExtra(
                        EXTRA_BUILD_ID
                    )
                    ?: prefs.getString(
                        EXTRA_BUILD_ID,
                        null
                    )
                    ?: run {
                        stopSelf(startId)
                        return START_NOT_STICKY
                    }

            startForeground(
                NOTIFICATION_ID,
                notification(
                    appName = appName,
                    text =
                        "Derleme arka planda hazırlanıyor",
                    progress = 0,
                    ongoing = true,
                    buildId = buildId,
                    serverUrl = serverUrl
                )
            )
        }

        monitorJob?.cancel()

        monitorJob =
            serviceScope.launch {

                val client =
                    BuildApiClient(
                        this@BuildProgressService,
                        serverUrl,
                        apiKey
                    )

                if (mode == MODE_BATCH) {
                    monitorBatch(
                        client = client,
                        startId = startId,
                        serverUrl = serverUrl
                    )
                } else {
                    val buildId =
                        prefs.getString(
                            EXTRA_BUILD_ID,
                            null
                        )
                            ?: return@launch

                    monitorSingle(
                        client = client,
                        startId = startId,
                        appName = appName,
                        buildId = buildId,
                        serverUrl = serverUrl
                    )
                }
            }

        /*
         * Sistem servisi bellek baskısı nedeniyle kapatırsa
         * SharedPreferences'taki aktif build bilgisiyle
         * yeniden başlatabilsin.
         */
        return START_STICKY
    }

    private suspend fun monitorSingle(
        client: BuildApiClient,
        startId: Int,
        appName: String,
        buildId: String,
        serverUrl: String
    ) {
        while (true) {
            try {
                val status =
                    client.getBuild(
                        buildId
                    )

                val normalized =
                    status.status
                        .trim()
                        .lowercase()

                val active =
                    normalized !in
                        TERMINAL_STATES

                val text =
                    if (active) {
                        "Derleme devam ediyor • %${status.progress}"
                    } else {
                        when (normalized) {
                            "success" ->
                                "Derleme hazır. Çıktıyı AppForge Studio'dan indirebilirsin."

                            "cancelled",
                            "canceled" ->
                                "Derleme iptal edildi."

                            else ->
                                "Derleme tamamlanamadı. Ayrıntılar için AppForge Studio'yu aç."
                        }
                    }

                showNotification(
                    appName = appName,
                    text = text,
                    progress =
                        if (
                            normalized ==
                            "success"
                        ) {
                            100
                        } else {
                            status.progress
                        },
                    ongoing = active,
                    buildId = buildId,
                    serverUrl = serverUrl
                )

                if (!active) {
                    clear(
                        this@BuildProgressService
                    )

                    stopForeground(
                        STOP_FOREGROUND_DETACH
                    )

                    stopSelf(
                        startId
                    )

                    return
                }
            } catch (_: Throwable) {
                showNotification(
                    appName = appName,
                    text =
                        "Derleme sunucuda devam ediyor • bağlantı yeniden denenecek",
                    progress = 0,
                    ongoing = true,
                    buildId = buildId,
                    serverUrl = serverUrl
                )
            }

            delay(
                5_000L
            )
        }
    }

    private suspend fun monitorBatch(
        client: BuildApiClient,
        startId: Int,
        serverUrl: String
    ) {
        while (true) {

            val prefs =
                getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            val total =
                prefs.getInt(
                    EXTRA_BATCH_TOTAL,
                    5
                )
                    .coerceAtLeast(
                        1
                    )

            val ids =
                readBatchIds(
                    prefs.getString(
                        EXTRA_BATCH_IDS,
                        "[]"
                    )
                )

            /*
             * #4/#5 henüz istemci tarafındaki 3 paralel
             * slot kapısından geçmemiş olabilir.
             */
            if (ids.isEmpty()) {
                showNotification(
                    appName =
                        "AppForge Studio",
                    text =
                        "5 Build Testi • build'ler hazırlanıyor",
                    progress = 0,
                    ongoing = true,
                    buildId = null,
                    serverUrl = serverUrl
                )

                delay(
                    2_000L
                )

                continue
            }

            var success =
                0

            var failed =
                0

            var progressSum =
                0

            var networkProblem =
                false

            for (buildId in ids) {
                try {
                    val status =
                        client.getBuild(
                            buildId
                        )

                    val normalized =
                        status.status
                            .trim()
                            .lowercase()

                    progressSum +=
                        if (
                            normalized ==
                            "success"
                        ) {
                            100
                        } else {
                            status.progress
                                .coerceIn(
                                    0,
                                    100
                                )
                        }

                    when (normalized) {
                        "success" ->
                            success += 1

                        "failed",
                        "cancelled",
                        "canceled" ->
                            failed += 1
                    }
                } catch (_: Throwable) {
                    networkProblem =
                        true
                }
            }

            val finished =
                success +
                    failed

            val submitted =
                ids.size
                    .coerceAtMost(
                        total
                    )

            val averageProgress =
                (
                    progressSum /
                        total
                )
                    .coerceIn(
                        0,
                        100
                    )

            val allFinished =
                submitted >=
                    total &&
                    finished >=
                    total

            if (allFinished) {
                val text =
                    if (
                        success ==
                        total
                    ) {
                        "$success/$total build başarılı."
                    } else {
                        "$success/$total build başarılı • $failed başarısız."
                    }

                showNotification(
                    appName =
                        "5 Build Testi",
                    text = text,
                    progress = 100,
                    ongoing = false,
                    buildId =
                        ids.firstOrNull(),
                    serverUrl = serverUrl
                )

                clear(
                    this@BuildProgressService
                )

                stopForeground(
                    STOP_FOREGROUND_DETACH
                )

                stopSelf(
                    startId
                )

                return
            }

            val text =
                when {
                    networkProblem ->
                        "5 Build Testi • $finished/$total tamamlandı • bağlantı yeniden deneniyor"

                    submitted <
                        total ->
                        "5 Build Testi • $finished/$total tamamlandı • $submitted/$total gönderildi"

                    else ->
                        "5 Build Testi • $finished/$total tamamlandı"
                }

            showNotification(
                appName =
                    "AppForge Studio",
                text = text,
                progress =
                    averageProgress,
                ongoing = true,
                buildId =
                    ids.firstOrNull(),
                serverUrl = serverUrl
            )

            delay(
                3_000L
            )
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? =
        null

    private fun createChannel() {
        getSystemService(
            NotificationManager::class.java
        ).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AppForge derlemeleri",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun showNotification(
        appName: String,
        text: String,
        progress: Int,
        ongoing: Boolean,
        buildId: String?,
        serverUrl: String
    ) {
        getSystemService(
            NotificationManager::class.java
        ).notify(
            NOTIFICATION_ID,
            notification(
                appName = appName,
                text = text,
                progress = progress,
                ongoing = ongoing,
                buildId = buildId,
                serverUrl = serverUrl
            )
        )
    }

    private fun notification(
        appName: String,
        text: String,
        progress: Int,
        ongoing: Boolean,
        buildId: String?,
        serverUrl: String
    ): android.app.Notification {

        val requestCode =
            buildId
                ?.hashCode()
                ?: NOTIFICATION_ID

        val contentIntent =
            PendingIntent.getActivity(
                this,
                requestCode,
                Intent(
                    this,
                    MainActivity::class.java
                ).apply {
                    putExtra(
                        "appforge_open_builds",
                        true
                    )

                    if (
                        !buildId.isNullOrBlank()
                    ) {
                        putExtra(
                            "appforge_build_id",
                            buildId
                        )
                    }

                    putExtra(
                        "appforge_build_server_url",
                        serverUrl
                    )

                    addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setSmallIcon(
                android.R.drawable.stat_sys_upload
            )
            .setContentTitle(
                appName
            )
            .setContentText(
                text
            )
            .setContentIntent(
                contentIntent
            )
            .setOnlyAlertOnce(
                true
            )
            .setOngoing(
                ongoing
            )
            .setAutoCancel(
                !ongoing
            )
            .setProgress(
                100,
                progress.coerceIn(
                    0,
                    100
                ),
                progress <=
                    0 &&
                    ongoing
            )
            .build()
    }

    companion object {

        private const val CHANNEL_ID =
            "appforge_build_progress"

        private const val NOTIFICATION_ID =
            7412

        private const val PREFS =
            "appforge_background_build"

        private const val EXTRA_MODE =
            "mode"

        private const val MODE_SINGLE =
            "single"

        private const val MODE_BATCH =
            "batch"

        private const val EXTRA_BUILD_ID =
            "build_id"

        private const val EXTRA_SERVER_URL =
            "server_url"

        private const val EXTRA_API_KEY =
            "api_key"

        private const val EXTRA_APP_NAME =
            "app_name"

        private const val EXTRA_BATCH_IDS =
            "batch_ids"

        private const val EXTRA_BATCH_TOTAL =
            "batch_total"

        private val TERMINAL_STATES =
            setOf(
                "success",
                "failed",
                "cancelled",
                "canceled"
            )

        fun track(
            context: Context,
            buildId: String,
            serverUrl: String,
            apiKey: String,
            appName: String
        ) {
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    EXTRA_MODE,
                    MODE_SINGLE
                )
                .putString(
                    EXTRA_BUILD_ID,
                    buildId
                )
                .putString(
                    EXTRA_SERVER_URL,
                    serverUrl
                )
                .putString(
                    EXTRA_API_KEY,
                    apiKey
                )
                .putString(
                    EXTRA_APP_NAME,
                    appName
                )
                .remove(
                    EXTRA_BATCH_IDS
                )
                .remove(
                    EXTRA_BATCH_TOTAL
                )
                .apply()
        }

        fun trackBatch(
            context: Context,
            serverUrl: String,
            apiKey: String,
            totalBuilds: Int = 5
        ) {
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    EXTRA_MODE,
                    MODE_BATCH
                )
                .putString(
                    EXTRA_SERVER_URL,
                    serverUrl
                )
                .putString(
                    EXTRA_API_KEY,
                    apiKey
                )
                .putString(
                    EXTRA_APP_NAME,
                    "5 Build Testi"
                )
                .putString(
                    EXTRA_BATCH_IDS,
                    "[]"
                )
                .putInt(
                    EXTRA_BATCH_TOTAL,
                    totalBuilds
                )
                .remove(
                    EXTRA_BUILD_ID
                )
                .apply()
        }

        fun addBatchBuild(
            context: Context,
            buildId: String
        ) {
            if (
                buildId.isBlank()
            ) {
                return
            }

            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            val ids =
                readBatchIds(
                    prefs.getString(
                        EXTRA_BATCH_IDS,
                        "[]"
                    )
                )
                    .toMutableList()

            if (
                buildId !in
                ids
            ) {
                ids.add(
                    buildId
                )
            }

            prefs
                .edit()
                .putString(
                    EXTRA_BATCH_IDS,
                    JSONArray(
                        ids
                    ).toString()
                )
                .apply()
        }

        fun startPending(
            context: Context
        ) {
            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            val mode =
                prefs.getString(
                    EXTRA_MODE,
                    null
                )
                    ?: return

            if (
                mode ==
                MODE_SINGLE &&
                prefs.getString(
                    EXTRA_BUILD_ID,
                    null
                ).isNullOrBlank()
            ) {
                return
            }

            ContextCompat
                .startForegroundService(
                    context,
                    Intent(
                        context,
                        BuildProgressService::class.java
                    ).apply {
                        putExtra(
                            EXTRA_MODE,
                            mode
                        )

                        putExtra(
                            EXTRA_BUILD_ID,
                            prefs.getString(
                                EXTRA_BUILD_ID,
                                null
                            )
                        )

                        putExtra(
                            EXTRA_SERVER_URL,
                            prefs.getString(
                                EXTRA_SERVER_URL,
                                ""
                            )
                        )

                        putExtra(
                            EXTRA_API_KEY,
                            prefs.getString(
                                EXTRA_API_KEY,
                                ""
                            )
                        )

                        putExtra(
                            EXTRA_APP_NAME,
                            prefs.getString(
                                EXTRA_APP_NAME,
                                "AppForge uygulaması"
                            )
                        )
                    }
                )
        }

        fun stop(
            context: Context
        ) {
            context.stopService(
                Intent(
                    context,
                    BuildProgressService::class.java
                )
            )

            context
                .getSystemService(
                    NotificationManager::class.java
                )
                .cancel(
                    NOTIFICATION_ID
                )
        }

        fun clear(
            context: Context
        ) {
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .edit()
                .clear()
                .apply()
        }

        private fun readBatchIds(
            raw: String?
        ): List<String> {
            return try {
                val array =
                    JSONArray(
                        raw ?: "[]"
                    )

                buildList {
                    for (
                        index in
                        0 until
                            array.length()
                    ) {
                        val id =
                            array
                                .optString(
                                    index
                                )
                                .trim()

                        if (
                            id.isNotBlank()
                        ) {
                            add(
                                id
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }
}
