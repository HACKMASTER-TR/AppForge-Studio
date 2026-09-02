package com.hackmaster.videoforge

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoForgeActivity : AppCompatActivity() {
    private var selectedVideo: Uri? = null
    private val selectedQueue = mutableListOf<Uri>()
    private var lastOutput: Uri? = null
    private var lastSubtitle: Uri? = null
    private var lastInput: Uri? = null

    private lateinit var fileText: TextView
    private lateinit var queueText: TextView
    private lateinit var modelText: TextView
    private lateinit var historyText: TextView
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var startButton: Button
    private lateinit var previewButton: Button
    private lateinit var queueStartButton: Button
    private lateinit var openButton: Button
    private lateinit var originalButton: Button
    private lateinit var shareButton: Button
    private lateinit var subtitleEditButton: Button
    private lateinit var subtitleSwitch: Switch
    private lateinit var backgroundSwitch: Switch
    private lateinit var timeSyncSwitch: Switch
    private lateinit var resumeSwitch: Switch
    private lateinit var urlInput: EditText
    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var qualitySpinner: Spinner
    private lateinit var speakerModeSpinner: Spinner
    private lateinit var ttsSeek: SeekBar
    private lateinit var bgSeek: SeekBar
    private val speakerSpinners = mutableListOf<Spinner>()

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persist(uri)
            selectedVideo = uri
            lastInput = uri
            fileText.text = "Video: ${displayName(uri)}"
            refreshButtons()
            status("Video seçildi.")
        }
    }

    private val pickQueue = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selectedQueue.clear()
        uris.forEach { uri -> persist(uri); selectedQueue += uri }
        queueText.text = if (selectedQueue.isEmpty()) "Kuyruk: boş" else "Kuyruk: ${selectedQueue.size} video hazır"
        refreshButtons()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val updates = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val pct = intent.getIntExtra(DubForegroundService.EXTRA_PROGRESS, 0).coerceIn(0, 100)
            val message = intent.getStringExtra(DubForegroundService.EXTRA_MESSAGE).orEmpty()
            val state = intent.getStringExtra(DubForegroundService.EXTRA_STATE).orEmpty()
            progress.progress = pct
            status(message.ifBlank { state })

            intent.getStringExtra(DubForegroundService.EXTRA_OUTPUT_URI)?.let {
                lastOutput = Uri.parse(it)
                openButton.visibility = View.VISIBLE
                shareButton.visibility = View.VISIBLE
            }
            intent.getStringExtra(DubForegroundService.EXTRA_SUBTITLE_URI)?.let {
                lastSubtitle = Uri.parse(it)
                subtitleEditButton.visibility = View.VISIBLE
            }
            intent.getStringExtra(DubForegroundService.EXTRA_INPUT_URI)?.let {
                lastInput = Uri.parse(it)
                originalButton.visibility = View.VISIBLE
            }

            when (state) {
                DubForegroundService.STATE_MODELS_READY -> refreshModelStatus()
                DubForegroundService.STATE_DONE, DubForegroundService.STATE_ERROR -> {
                    refreshButtons()
                    refreshHistory()
                }
                DubForegroundService.STATE_RUNNING -> setWorking(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 17, 31)
        window.navigationBarColor = Color.rgb(7, 17, 31)
        buildUi()
        refreshModelStatus()
        refreshHistory()

        ContextCompat.registerReceiver(
            this,
            updates,
            IntentFilter(DubForegroundService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.activityStarted()
    }

    override fun onStop() {
        AppVisibility.activityStopped()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        DubForegroundService.clearVisibleNotifications(this)
    }

    override fun onDestroy() {
        try { unregisterReceiver(updates) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun buildUi() {
        val bg = Color.rgb(7, 17, 31)
        val panel = Color.rgb(13, 26, 44)
        val panel2 = Color.rgb(19, 35, 58)
        val white = Color.rgb(245, 248, 255)
        val muted = Color.rgb(159, 176, 200)
        val accent = Color.rgb(103, 179, 255)
        val darkText = Color.rgb(5, 17, 28)

        val root = ScrollView(this).apply {
            setBackgroundColor(bg)
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(body)

        /*
         * Android 15+ edge-to-edge davranışında içeriğin
         * durum ve gezinme çubuklarının altında kalmasını önler.
         */
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            body.setPadding(
                dp(18) + bars.left,
                dp(18) + bars.top,
                dp(18) + bars.right,
                dp(18) + bars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(root)

        fun text(
            value: String,
            size: Float,
            color: Int = white,
            bold: Boolean = false
        ): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)

                if (bold) {
                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                }
            }

        fun section(
            title: String
        ): LinearLayout {
            body.addView(
                text(
                    title,
                    18f,
                    white,
                    true
                ).apply {
                    setPadding(
                        0,
                        dp(18),
                        0,
                        dp(8)
                    )
                }
            )

            return LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16)
                )

                background =
                    rounded(
                        panel,
                        22f
                    )

                body.addView(
                    this,
                    LinearLayout.LayoutParams(
                        -1,
                        -2
                    )
                )
            }
        }

        // -------------------------------------------------
        // BAŞLIK
        // -------------------------------------------------
        body.addView(
            text(
                "VideoForge",
                30f,
                white,
                true
            )
        )

        body.addView(
            text(
                "Videonu seç, farklı bir dile dublaj yap veya doğrudan video bağlantısını indir.",
                14f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(4)
                )
            }
        )

        body.addView(
            text(
                "Temel ayarlar önde, teknik seçenekler Gelişmiş Ayarlar bölümünde.",
                12f,
                muted
            )
        )

        // -------------------------------------------------
        // 1 - TELEFONDAN VIDEO
        // -------------------------------------------------
        val videoCard =
            section(
                "1. Video seç"
            )

        videoCard.addView(
            text(
                "Dublaj yapmak istediğin videoyu telefonundan seç.",
                12f,
                muted
            ).apply {
                setPadding(
                    0,
                    0,
                    0,
                    dp(10)
                )
            }
        )

        videoCard.addView(
            button(
                "📁 VİDEO SEÇ",
                accent,
                darkText
            ) {
                pickVideo.launch(
                    arrayOf(
                        "video/*"
                    )
                )
            }
        )

        fileText =
            text(
                "Henüz video seçilmedi.",
                13f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(10),
                    0,
                    0
                )
            }

        videoCard.addView(
            fileText
        )

        // -------------------------------------------------
        // 2 - TEMEL DUBLAJ AYARLARI
        // -------------------------------------------------
        val dubCard =
            section(
                "2. Dublaj ayarları"
            )

        dubCard.addView(
            text(
                "Hedef dil",
                13f,
                white,
                true
            )
        )

        targetSpinner =
            spinner(
                StudioOptions.TARGETS.map {
                    it.label
                }
            )

        dubCard.addView(
            targetSpinner
        )

        dubCard.addView(
            text(
                "Kalite",
                13f,
                white,
                true
            ).apply {
                setPadding(
                    0,
                    dp(12),
                    0,
                    0
                )
            }
        )

        qualitySpinner =
            spinner(
                listOf(
                    "Hızlı",
                    "Dengeli",
                    "Yüksek kalite"
                )
            )

        qualitySpinner.setSelection(
            1
        )

        dubCard.addView(
            qualitySpinner
        )

        dubCard.addView(
            text(
                "Dengeli, çoğu video için önerilen seçenektir.",
                11f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(5),
                    0,
                    dp(12)
                )
            }
        )

        modelText =
            text(
                "AI modelleri kontrol ediliyor…",
                13f,
                muted
            )

        dubCard.addView(
            modelText
        )

        dubCard.addView(
            button(
                "⬇ AI MODELLERİNİ HAZIRLA",
                panel2,
                white
            ) {
                val i =
                    baseServiceIntent(
                        DubForegroundService.MODE_MODELS,
                        StudioOptions()
                    )

                startService(
                    i
                )

                setWorking(
                    true
                )
            },
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(8)
            }
        )

        previewButton =
            button(
                "⚡ İLK 30 SANİYEYİ ÖNİZLE",
                panel2,
                white
            ) {
                val uri =
                    selectedVideo

                if (uri == null) {
                    status(
                        "Önce bir video seç."
                    )
                } else {
                    startSingle(
                        uri,
                        true
                    )
                }
            }

        dubCard.addView(
            previewButton,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(12)
            }
        )

        startButton =
            button(
                "🎙 DUBLAJ OLUŞTUR",
                accent,
                darkText
            ) {
                val uri =
                    selectedVideo

                if (uri == null) {
                    status(
                        "Önce bir video seç."
                    )
                } else {
                    startSingle(
                        uri,
                        false
                    )
                }
            }

        dubCard.addView(
            startButton,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(8)
            }
        )

        // -------------------------------------------------
        // 3 - LİNK İLE VIDEO
        // -------------------------------------------------
        val downloadCard =
            section(
                "3. Linkten video"
            )

        downloadCard.addView(
            text(
                "Doğrudan video dosyası bağlantısını yapıştır.",
                12f,
                muted
            ).apply {
                setPadding(
                    0,
                    0,
                    0,
                    dp(8)
                )
            }
        )

        urlInput =
            EditText(this).apply {
                hint =
                    "https://site.com/video.mp4"

                setHintTextColor(
                    muted
                )

                setTextColor(
                    white
                )

                setSingleLine(
                    true
                )

                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_URI

                background =
                    rounded(
                        panel2,
                        14f
                    )

                setPadding(
                    dp(12)
                )
            }

        downloadCard.addView(
            urlInput
        )

        downloadCard.addView(
            button(
                "⬇ VİDEOYU İNDİR",
                accent,
                darkText
            ) {
                val url =
                    urlInput.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                downloadVideoOnly(
                    url
                )
            },
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(8)
            }
        )

        downloadCard.addView(
            button(
                "🎙 BU LİNKİ DUBLAJ ET",
                panel2,
                white
            ) {
                val url =
                    urlInput.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                when {
                    url.isBlank() ->
                        status(
                            "Önce video bağlantısını gir."
                        )

                    !ModelManager(
                        this
                    ).isReady() ->
                        status(
                            "Dublaj için önce AI modellerini hazırla."
                        )

                    else ->
                        startUrl(
                            url,
                            false
                        )
                }
            },
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(8)
            }
        )

        downloadCard.addView(
            text(
                "İndirme için AI modeli gerekmez. DRM, üyelik veya giriş koruması aşılmaz; bağlantının doğrudan video dosyasına gitmesi gerekir.",
                11f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }
        )

        // -------------------------------------------------
        // DURUM
        // -------------------------------------------------
        val statusCard =
            section(
                "Durum"
            )

        progress =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                max =
                    100
            }

        statusCard.addView(
            progress,
            LinearLayout.LayoutParams(
                -1,
                dp(12)
            )
        )

        statusText =
            text(
                "Hazır. Bir video seçebilir veya video bağlantısı girebilirsin.",
                13f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(10),
                    0,
                    0
                )
            }

        statusCard.addView(
            statusText
        )

        openButton =
            button(
                "▶ SONUCU AÇ",
                panel2,
                white
            ) {
                lastOutput?.let {
                    openVideo(
                        it
                    )
                }
            }.apply {
                visibility =
                    View.GONE
            }

        originalButton =
            button(
                "◀ ORİJİNALİ AÇ",
                panel2,
                white
            ) {
                lastInput?.let {
                    openVideo(
                        it
                    )
                }
            }.apply {
                visibility =
                    View.GONE
            }

        shareButton =
            button(
                "↗ SONUCU PAYLAŞ",
                panel2,
                white
            ) {
                shareLast()
            }.apply {
                visibility =
                    View.GONE
            }

        subtitleEditButton =
            button(
                "✏ ALTYAZIYI DÜZENLE",
                panel2,
                white
            ) {
                editSubtitle()
            }.apply {
                visibility =
                    View.GONE
            }

        listOf(
            originalButton,
            openButton,
            shareButton,
            subtitleEditButton
        ).forEach {
            statusCard.addView(
                it,
                LinearLayout.LayoutParams(
                    -1,
                    -2
                ).apply {
                    topMargin =
                        dp(8)
                }
            )
        }

        // -------------------------------------------------
        // GELİŞMİŞ
        // -------------------------------------------------
        val advancedCard =
            section(
                "Gelişmiş ayarlar"
            )

        var advancedVisible =
            false

        val advancedContainer =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                visibility =
                    View.GONE
            }

        val advancedButton =
            button(
                "⚙ GELİŞMİŞ AYARLARI GÖSTER",
                panel2,
                white
            ) {
                advancedVisible =
                    !advancedVisible

                advancedContainer.visibility =
                    if (advancedVisible) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }

        advancedButton.setOnClickListener {
            advancedVisible =
                !advancedVisible

            advancedContainer.visibility =
                if (advancedVisible) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            advancedButton.text =
                if (advancedVisible) {
                    "▲ GELİŞMİŞ AYARLARI GİZLE"
                } else {
                    "⚙ GELİŞMİŞ AYARLARI GÖSTER"
                }
        }

        advancedCard.addView(
            advancedButton
        )

        advancedCard.addView(
            advancedContainer
        )

        advancedContainer.addView(
            text(
                "Kaynak dil",
                13f,
                white,
                true
            ).apply {
                setPadding(
                    0,
                    dp(14),
                    0,
                    0
                )
            }
        )

        sourceSpinner =
            spinner(
                StudioOptions.SOURCES.map {
                    it.label
                }
            )

        sourceSpinner.setSelection(
            0
        )

        advancedContainer.addView(
            sourceSpinner
        )

        advancedContainer.addView(
            text(
                "Otomatik algılama yanlış sonuç verirse videonun konuşma dilini buradan seç.",
                11f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(10)
                )
            }
        )

        advancedContainer.addView(
            text(
                "Konuşmacı modu",
                13f,
                white,
                true
            )
        )

        speakerModeSpinner =
            spinner(
                listOf(
                    "Otomatik",
                    "Tek konuşmacı",
                    "Çok konuşmacı"
                )
            )

        speakerModeSpinner.setSelection(
            0
        )

        advancedContainer.addView(
            speakerModeSpinner
        )

        subtitleSwitch =
            switch(
                "Altyazı (.srt) dosyası da oluştur",
                true,
                white
            )

        backgroundSwitch =
            switch(
                "Arka plan sesini korumayı dene",
                false,
                white
            )

        timeSyncSwitch =
            switch(
                "Dublajı konuşma süresine otomatik uydur",
                true,
                white
            )

        resumeSwitch =
            switch(
                "Yarım kalan işlemi devam ettir",
                true,
                white
            )

        advancedContainer.addView(
            subtitleSwitch
        )

        advancedContainer.addView(
            backgroundSwitch
        )

        advancedContainer.addView(
            timeSyncSwitch
        )

        advancedContainer.addView(
            resumeSwitch
        )

        advancedContainer.addView(
            text(
                "Dublaj sesi",
                12f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(12),
                    0,
                    0
                )
            }
        )

        ttsSeek =
            seek(
                80,
                20,
                150
            )

        advancedContainer.addView(
            ttsSeek
        )

        advancedContainer.addView(
            text(
                "Arka plan sesi",
                12f,
                muted
            )
        )

        bgSeek =
            seek(
                100,
                0,
                125
            )

        advancedContainer.addView(
            bgSeek
        )

        advancedContainer.addView(
            text(
                "Konuşmacı ses profilleri",
                13f,
                white,
                true
            ).apply {
                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(4)
                )
            }
        )

        val profiles =
            listOf(
                "Otomatik",
                "Derin",
                "Doğal",
                "Parlak"
            )

        repeat(
            4
        ) { i ->
            advancedContainer.addView(
                text(
                    "Konuşmacı ${i + 1}",
                    11f,
                    muted
                )
            )

            val sp =
                spinner(
                    profiles
                )

            sp.setSelection(
                if (i == 0) {
                    0
                } else {
                    minOf(
                        i,
                        3
                    )
                }
            )

            speakerSpinners +=
                sp

            advancedContainer.addView(
                sp
            )
        }

        advancedContainer.addView(
            text(
                "Birden fazla video",
                13f,
                white,
                true
            ).apply {
                setPadding(
                    0,
                    dp(14),
                    0,
                    dp(4)
                )
            }
        )

        advancedContainer.addView(
            button(
                "🗂 BİRDEN FAZLA VİDEO SEÇ",
                panel2,
                white
            ) {
                pickQueue.launch(
                    arrayOf(
                        "video/*"
                    )
                )
            }
        )

        queueText =
            text(
                "Kuyruk boş.",
                13f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }

        advancedContainer.addView(
            queueText
        )

        queueStartButton =
            button(
                "▶ KUYRUĞU BAŞLAT",
                panel2,
                white
            ) {
                startQueue()
            }

        advancedContainer.addView(
            queueStartButton,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(8)
            }
        )

        advancedContainer.addView(
            button(
                "🗑 AI MODELLERİNİ SİL",
                panel2,
                white
            ) {
                AlertDialog.Builder(
                    this
                )
                    .setTitle(
                        "AI modelleri silinsin mi?"
                    )
                    .setMessage(
                        "Dublajı tekrar kullanmak istediğinde modellerin yeniden indirilmesi gerekir."
                    )
                    .setNegativeButton(
                        "Vazgeç",
                        null
                    )
                    .setPositiveButton(
                        "Sil"
                    ) { _, _ ->
                        ModelManager(
                            this
                        ).clearAll()

                        refreshModelStatus()
                    }
                    .show()
            },
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(12)
            }
        )

        advancedContainer.addView(
            text(
                "Geçmiş",
                13f,
                white,
                true
            ).apply {
                setPadding(
                    0,
                    dp(16),
                    0,
                    dp(4)
                )
            }
        )

        historyText =
            text(
                "Geçmiş boş.",
                12f,
                muted
            )

        advancedContainer.addView(
            historyText
        )

        advancedContainer.addView(
            button(
                "⟳ GEÇMİŞİ YENİLE",
                panel2,
                white
            ) {
                refreshHistory()
            },
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(8)
            }
        )

        advancedContainer.addView(
            button(
                "🗑 GEÇMİŞİ TEMİZLE",
                panel2,
                white
            ) {
                HistoryStore(
                    this
                ).clear()

                refreshHistory()
            },
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                topMargin =
                    dp(8)
            }
        )

        body.addView(
            text(
                "VideoForge işlemleri cihaz üzerinde çalışır. Linkten indirme yalnız doğrudan ve erişilebilir video dosyaları içindir.",
                11f,
                muted
            ).apply {
                setPadding(
                    0,
                    dp(16),
                    0,
                    dp(20)
                )
            }
        )

        setContentView(
            root
        )

        refreshButtons()
    }

    private fun currentOptions(preview: Boolean = false): StudioOptions {
        val target = StudioOptions.TARGETS.getOrElse(targetSpinner.selectedItemPosition) { StudioOptions.TARGETS.first() }.code
        val quality = when (qualitySpinner.selectedItemPosition) {
            0 -> StudioOptions.QUALITY_FAST
            2 -> StudioOptions.QUALITY_HIGH
            else -> StudioOptions.QUALITY_BALANCED
        }
        val profileCodes = speakerSpinners.map { sp ->
            when (sp.selectedItemPosition) {
                1 -> "deep"
                2 -> "natural"
                3 -> "bright"
                else -> "auto"
            }
        }
        val source = StudioOptions.SOURCES.getOrElse(sourceSpinner.selectedItemPosition) { StudioOptions.SOURCES.first() }.code
        val speakerMode = when (speakerModeSpinner.selectedItemPosition) {
            1 -> StudioOptions.SPEAKER_SINGLE
            2 -> StudioOptions.SPEAKER_MULTI
            else -> StudioOptions.SPEAKER_AUTO
        }
        return StudioOptions(
            sourceLanguage = source,
            targetLanguage = target,
            quality = quality,
            speakerMode = speakerMode,
            saveSubtitles = subtitleSwitch.isChecked,
            previewOnly = preview,
            previewSeconds = 30,
            preserveBackground = backgroundSwitch.isChecked,
            timeSync = timeSyncSwitch.isChecked,
            resumeEnabled = resumeSwitch.isChecked,
            ttsVolume = ttsSeek.progress / 100f,
            backgroundVolume = bgSeek.progress / 100f,
            speakerProfiles = profileCodes
        )
    }

    private fun startSingle(uri: Uri, preview: Boolean) {
        if (!ModelManager(this).isReady()) { status("Önce AI modellerini hazırla."); return }
        runCatching { StorageGuard.requireEnough(this, uri, if (preview) 30 else null) }
            .onFailure { status(it.message ?: "Depolama kontrolü başarısız."); return }
        val options = currentOptions(preview)
        val i = baseServiceIntent(DubForegroundService.MODE_DUB, options).apply {
            putExtra(DubForegroundService.EXTRA_VIDEO_URI, uri.toString())
            putExtra(DubForegroundService.EXTRA_SOURCE_LABEL, displayName(uri))
        }
        startService(i)
        setWorking(true)
    }

    private fun startQueue() {
        if (selectedQueue.isEmpty()) { status("Önce kuyruğa video seç."); return }
        if (!ModelManager(this).isReady()) { status("Önce AI modellerini hazırla."); return }
        val i = baseServiceIntent(DubForegroundService.MODE_QUEUE, currentOptions(false)).apply {
            putStringArrayListExtra(DubForegroundService.EXTRA_VIDEO_URIS, ArrayList(selectedQueue.map { it.toString() }))
        }
        startService(i)
        setWorking(true)
    }

    private fun startUrl(url: String, preview: Boolean) {
        val i = baseServiceIntent(DubForegroundService.MODE_URL_DUB, currentOptions(preview)).apply {
            putExtra(DubForegroundService.EXTRA_VIDEO_URL, url)
            putExtra(DubForegroundService.EXTRA_SOURCE_LABEL, "URL videosu")
        }
        startService(i)
        setWorking(true)
    }

    private fun downloadVideoOnly(
        url: String
    ) {
        val trimmed =
            url.trim()

        if (trimmed.isBlank()) {
            status(
                "Önce video bağlantısını gir."
            )
            return
        }

        val uri =
            runCatching {
                Uri.parse(
                    trimmed
                )
            }.getOrNull()

        if (
            uri == null ||
            (
                !uri.scheme.equals(
                    "https",
                    true
                ) &&
                !uri.scheme.equals(
                    "http",
                    true
                )
            ) ||
            uri.host.isNullOrBlank()
        ) {
            status(
                "Geçerli bir http:// veya https:// video bağlantısı gir."
            )
            return
        }

        val host =
            uri.host
                .orEmpty()
                .lowercase(
                    Locale.US
                )

        if (
            host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1"
        ) {
            status(
                "Yerel cihaz bağlantıları desteklenmiyor."
            )
            return
        }

        val rawName =
            uri.lastPathSegment
                .orEmpty()

        val detectedExtension =
            rawName
                .substringAfterLast(
                    '.',
                    ""
                )
                .lowercase(
                    Locale.US
                )

        val extension =
            detectedExtension.takeIf {
                it in setOf(
                    "mp4",
                    "webm",
                    "mov",
                    "mkv",
                    "m4v",
                    "3gp"
                )
            } ?: "mp4"

        val stamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(
                Date()
            )

        val fileName =
            "VideoForge_$stamp.$extension"

        runCatching {
            val request =
                DownloadManager.Request(
                    uri
                )
                    .setTitle(
                        fileName
                    )
                    .setDescription(
                        "Video indiriliyor"
                    )
                    .setMimeType(
                        "video/*"
                    )
                    .setAllowedOverMetered(
                        true
                    )
                    .setAllowedOverRoaming(
                        false
                    )
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "VideoForge/$fileName"
                    )

            val manager =
                getSystemService(
                    DOWNLOAD_SERVICE
                ) as DownloadManager

            manager.enqueue(
                request
            )
        }.onSuccess {
            status(
                "İndirme başlatıldı. Dosya İndirilenler/VideoForge klasörüne kaydedilecek."
            )
        }.onFailure {
            status(
                "İndirme başlatılamadı: ${it.message ?: "Bilinmeyen hata"}"
            )
        }
    }

    private fun baseServiceIntent(mode: String, options: StudioOptions): Intent = Intent(this, DubForegroundService::class.java).apply {
        action = DubForegroundService.ACTION_START
        putExtra(DubForegroundService.EXTRA_MODE, mode)
        options.writeTo(this)
    }

    private fun refreshModelStatus() {
        val manager = ModelManager(this)
        val size = StorageGuard.human(manager.totalSizeBytes())
        modelText.text = if (manager.isReady()) "AI modelleri: hazır ✅ • $size" else "AI modelleri: hazır değil • $size"
        refreshButtons()
    }

    private fun refreshButtons() {
        val ready = ModelManager(this).isReady()
        startButton.isEnabled = ready && selectedVideo != null
        previewButton.isEnabled = ready && selectedVideo != null
        queueStartButton.isEnabled = ready && selectedQueue.isNotEmpty()
    }

    private fun setWorking(working: Boolean) {
        startButton.isEnabled = !working && ModelManager(this).isReady() && selectedVideo != null
        previewButton.isEnabled = !working && ModelManager(this).isReady() && selectedVideo != null
        queueStartButton.isEnabled = !working && ModelManager(this).isReady() && selectedQueue.isNotEmpty()
    }

    private fun refreshHistory() {
        val items = HistoryStore(this).read().take(6)
        historyText.text = if (items.isEmpty()) "Geçmiş boş." else items.joinToString("\n\n") { e ->
            val time = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(e.timestamp))
            val target = StudioOptions.TARGETS.firstOrNull { it.code == e.targetLanguage }?.label ?: e.targetLanguage
            "$time • ${if (e.preview) "Önizleme" else "Dublaj"} • $target\n${e.sourceLabel}\n${e.speakers} konuşmacı • ${e.turns} bölüm"
        }
        items.firstOrNull()?.let { e ->
            if (lastOutput == null) lastOutput = Uri.parse(e.outputUri)
            if (lastSubtitle == null && !e.subtitleUri.isNullOrBlank()) lastSubtitle = Uri.parse(e.subtitleUri)
        }
    }

    private fun editSubtitle() {
        val uri = lastSubtitle ?: return
        val current = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull().orEmpty()
        if (current.isBlank()) { status("Altyazı dosyası açılamadı."); return }
        val editor = EditText(this).apply {
            setText(current)
            minLines = 14
            gravity = android.view.Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(this)
            .setTitle("Altyazıyı düzenle")
            .setView(editor)
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Kaydet") { _, _ ->
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter(Charsets.UTF_8).use { it.write(editor.text.toString()) }
                }.onSuccess { status("Altyazı güncellendi.") }
                    .onFailure { status("Altyazı kaydedilemedi: ${it.message}") }
            }.show()
    }

    private fun shareLast() {
        val uri = lastOutput ?: return
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(i, "VideoForge sonucunu paylaş")) }
    }

    private fun openVideo(uri: Uri) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(i) }
    }

    private fun status(message: String) { statusText.text = message }

    private fun persist(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "video"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        return name
    }

    private fun button(label: String, bg: Int, fg: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(fg)
        background = rounded(bg, 16f)
        isAllCaps = false
        setPadding(dp(12))
        setOnClickListener { onClick() }
    }

    private fun spinner(values: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@VideoForgeActivity, android.R.layout.simple_spinner_dropdown_item, values)
        setBackgroundColor(Color.rgb(19, 35, 58))
    }

    private fun switch(label: String, checked: Boolean, color: Int): Switch = Switch(this).apply {
        text = label
        setTextColor(color)
        isChecked = checked
        setPadding(0, dp(8), 0, 0)
    }

    private fun seek(initial: Int, min: Int, max: Int): SeekBar = SeekBar(this).apply {
        this.max = max
        if (Build.VERSION.SDK_INT >= 26) this.min = min
        progress = initial
    }

    private fun rounded(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
