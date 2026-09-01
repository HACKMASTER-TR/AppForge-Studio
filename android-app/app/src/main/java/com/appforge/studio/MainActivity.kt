@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.provider.Settings
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.build.BuildApiClient
import com.appforge.studio.build.BuildCompareResult
import com.appforge.studio.build.TestLabResult
import com.appforge.studio.ai.AppForgeKnowledgeBase
import com.appforge.studio.ai.AppForgeProjectAdvisor
import com.appforge.studio.ai.AppForgeBuildErrorAdvisor
import com.appforge.studio.ai.AppForgeAssistantIntegration
import com.appforge.studio.ai.AppForgeAiCommandParser
import com.appforge.studio.ai.AppForgeSmartSuggestions
import com.appforge.studio.ai.AssistantAppAction
import com.appforge.studio.ai.AssistantDestination
import com.appforge.studio.ai.AssistantRuntimeContext
import com.appforge.studio.ai.LocalAiChatStore
import com.appforge.studio.ai.StoredAiMessage
import com.appforge.studio.ai.AppForgeLocalAssistant
import com.appforge.studio.ai.LocalAiBackend
import com.appforge.studio.ai.LocalAiModelInfo
import com.appforge.studio.ai.LocalAiModelStore
import com.appforge.studio.ai.LocalAiModelDownloader
import com.appforge.studio.i18n.StudioI18n
import com.appforge.studio.io.AppSettingsStore
import com.appforge.studio.io.AppIconProcessor
import com.appforge.studio.io.AppForgeApkConversion
import com.appforge.studio.io.AppForgeExeConversion
import com.appforge.studio.io.KeystoreVault
import com.appforge.studio.io.ManagedKeystore
import com.appforge.studio.io.ProjectBackupManager
import com.appforge.studio.io.ProjectImporter
import com.appforge.studio.io.FirebaseConfigInspector
import com.appforge.studio.io.SourceCapabilityAnalysis
import com.appforge.studio.io.SourceCapabilityAnalyzer
import com.appforge.studio.io.TemplateProjectFactory
import com.appforge.studio.io.ProjectLibrary
import com.appforge.studio.io.PwaInspector
import com.appforge.studio.io.SavedBuild
import com.appforge.studio.io.SavedProject
import com.appforge.studio.io.ZipUtils
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode
import com.appforge.studio.net.AppForgeAccountClient
import com.appforge.studio.net.LoginResult
import com.appforge.studio.net.RemoteTemplate
import com.appforge.studio.net.Session
import com.appforge.studio.net.WorkspaceClient
import com.appforge.studio.security.AppSignatureVerifier
import com.appforge.studio.security.ProStatus
import com.appforge.studio.security.StudioSecurityClient
import com.appforge.studio.security.StudioBillingManager
import com.appforge.studio.security.SecureAccountStore
import com.appforge.studio.security.StudioPlanPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    var openBuildFromNotification by mutableStateOf(false)
        private set

    var buildIdFromNotification by mutableStateOf<String?>(null)
        private set

    var buildServerUrlFromNotification by mutableStateOf<String?>(null)
        private set

    var buildNotificationSequence by mutableIntStateOf(0)
        private set


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        openBuildFromNotification =
            intent?.getBooleanExtra("appforge_open_builds", false) == true

        buildIdFromNotification =
            intent?.getStringExtra("appforge_build_id")

        buildServerUrlFromNotification =
            intent?.getStringExtra("appforge_build_server_url")

        if (openBuildFromNotification) {
            buildNotificationSequence += 1
        }

        /*
         * Android 13+ bildirim izni.
         * Kullanıcıya ilk kez yalnızca bir defa sorulur.
         */
        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
        ) {
            val permissionPrefs =
                getSharedPreferences(
                    "appforge_permissions",
                    Context.MODE_PRIVATE
                )

            val alreadyAsked =
                permissionPrefs.getBoolean(
                    "notifications_asked",
                    false
                )

            val granted =
                checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

            if (
                !alreadyAsked &&
                !granted
            ) {
                permissionPrefs
                    .edit()
                    .putBoolean(
                        "notifications_asked",
                        true
                    )
                    .apply()

                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ),
                    9101
                )
            }
        }

        setContent {
            AppForgeApp()
        }
    }

    override fun onResume() {
        super.onResume()
        BuildProgressService.stop(this)

        /*
         * APK yükleme izni ekranından döndüğünde
         * kuruluma otomatik devam et.
         */
        val installerPrefs =
            getSharedPreferences(
                "appforge_installer",
                Context.MODE_PRIVATE
            )

        val pendingApkPath =
            installerPrefs.getString(
                "pending_apk_path",
                null
            )

        if (
            !pendingApkPath.isNullOrBlank() &&
            (
                Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.O ||
                packageManager
                    .canRequestPackageInstalls()
            )
        ) {
            installerPrefs
                .edit()
                .remove(
                    "pending_apk_path"
                )
                .apply()

            installCachedApk(
                context = this,
                apkFile =
                    File(
                        pendingApkPath
                    )
            )
        }

        val pendingDownloadId =
            installerPrefs.getLong(
                "pending_download_id",
                -1L
            )

        if (
            pendingDownloadId > 0L &&
            (
                Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.O ||
                packageManager
                    .canRequestPackageInstalls()
            )
        ) {
            installerPrefs
                .edit()
                .remove(
                    "pending_download_id"
                )
                .apply()

            installDownloadedApk(
                context = this,
                downloadId = pendingDownloadId
            )
        }
    }

    override fun onStop() {
        BuildProgressService.startPending(this)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("appforge_open_builds", false)) {
            buildIdFromNotification =
                intent.getStringExtra("appforge_build_id")

            buildServerUrlFromNotification =
                intent.getStringExtra("appforge_build_server_url")

            openBuildFromNotification = true
            buildNotificationSequence += 1
        }
    }

    fun consumeBuildNotificationNavigation(): Boolean {
        if (!openBuildFromNotification) return false
        openBuildFromNotification = false
        return true
    }
}

private val Bg = Color(0xFF060711)
private val Card2 = Color(0xFF101426)
private val Accent = Color(0xFF63D9FF)
private val TextSecondary = Color(0xFFA9B1C7)

private const val APPFORGE_DOWNLOAD_FOLDER =
    "AppForge Studio"

private fun persistReadUriPermission(
    context: Context,
    uri: Uri
) {
    runCatching {
        context.contentResolver
            .takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
    }
}

private enum class AiDownloadNetwork {
    WIFI,
    MOBILE,
    OFFLINE
}

private fun getAiDownloadNetwork(
    context: Context
): AiDownloadNetwork {

    val manager =
        context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as? ConnectivityManager
            ?: return AiDownloadNetwork.OFFLINE

    val network =
        manager.activeNetwork
            ?: return AiDownloadNetwork.OFFLINE

    val capabilities =
        manager.getNetworkCapabilities(
            network
        )
            ?: return AiDownloadNetwork.OFFLINE

    val internetAvailable =
        capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) &&
        capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )

    if (!internetAvailable) {
        return AiDownloadNetwork.OFFLINE
    }

    if (
        capabilities.hasTransport(
            NetworkCapabilities.TRANSPORT_WIFI
        ) ||
        capabilities.hasTransport(
            NetworkCapabilities.TRANSPORT_ETHERNET
        )
    ) {
        return AiDownloadNetwork.WIFI
    }

    if (
        capabilities.hasTransport(
            NetworkCapabilities.TRANSPORT_CELLULAR
        )
    ) {
        return AiDownloadNetwork.MOBILE
    }

    /*
     * Bilinmeyen bağlantı türü:
     * ölçümlü ise mobil gibi davran,
     * ölçümsüz ise Wi-Fi gibi davran.
     */
    return if (
        manager.isActiveNetworkMetered
    ) {
        AiDownloadNetwork.MOBILE
    } else {
        AiDownloadNetwork.WIFI
    }
}


private fun isTransientBuildNetworkError(
    error: Throwable
): Boolean {

    var current: Throwable? =
        error

    while (
        current != null
    ) {

        if (
            current is java.net.UnknownHostException ||
            current is java.net.SocketTimeoutException ||
            current is java.net.ConnectException ||
            current is java.net.NoRouteToHostException ||
            current is java.net.SocketException
        ) {
            return true
        }

        val message =
            current.message
                .orEmpty()
                .lowercase()

        val transientMessage =
            listOf(
                "unable to resolve host",
                "failed to connect",
                "connection reset",
                "connection refused",
                "network is unreachable",
                "no route to host",
                "timed out",
                "timeout"
            ).any {
                message.contains(
                    it
                )
            }

        if (
            transientMessage
        ) {
            return true
        }

        current =
            current.cause
    }

    return false
}


private suspend fun <T> retryInitialBuildRequest(
    maxAttempts: Int = 5,
    initialDelayMs: Long = 1_500L,
    onRetry: (
        attempt: Int,
        maxAttempts: Int,
        error: Throwable
    ) -> Unit,
    request: suspend () -> T
): T {

    var attempt =
        1

    var waitMs =
        initialDelayMs

    while (
        true
    ) {
        try {
            return request()
        } catch (
            t: Throwable
        ) {

            if (
                !isTransientBuildNetworkError(
                    t
                ) ||
                attempt >=
                    maxAttempts
            ) {
                throw t
            }

            attempt +=
                1

            onRetry(
                attempt,
                maxAttempts,
                t
            )

            delay(
                waitMs
            )

            waitMs =
                (
                    waitMs *
                        2
                ).coerceAtMost(
                    6_000L
                )
        }
    }
}


private enum class AppScreen { ONBOARDING, HOME, MODE_SELECT, CONVERSION, QUICK, BUILDER, PREVIEW, PRODUCTION, TEST_LAB, AI_ASSISTANT, LIBRARY, HISTORY, TRASH, ACCOUNT, TEMPLATES, SETTINGS, LEGAL, HELP, PLAY_GUIDE, PRO, KEYSTORES, LANGUAGE }

@Composable
private fun AppForgeApp() {
    val context = LocalContext.current
    val hostActivity = context as? MainActivity
    val scope = rememberCoroutineScope()

    val appConfiguration =
        LocalConfiguration.current

    val appScreenWidthDp =
        appConfiguration.screenWidthDp

    val appScreenHeightDp =
        appConfiguration.screenHeightDp

    val builderCompact =
        appScreenWidthDp < 380

    val builderTablet =
        minOf(
            appScreenWidthDp,
            appScreenHeightDp
        ) >= 600

    val builderWide =
        appScreenWidthDp >= 600

    val builderContentMaxWidth =
        if (builderWide) {
            980.dp
        } else {
            10000.dp
        }

    val builderHorizontalPadding =
        when {
            builderCompact -> 10.dp
            builderTablet -> 28.dp
            else -> 20.dp
        }

    var draft by remember { mutableStateOf(ProjectDraft()) }

    var sourceAnalysis by
        remember {
            mutableStateOf<SourceCapabilityAnalysis?>(
                null
            )
        }

    var currentProjectId by remember { mutableStateOf<String?>(null) }
    var autosaveBaseline by
        remember {
            mutableStateOf<Pair<String, ProjectDraft>?>(
                null
            )
        }
    var screen by remember {
        mutableStateOf(
            if (
                context.getSharedPreferences(
                    "appforge_onboarding",
                    Context.MODE_PRIVATE
                ).getBoolean(
                    "completed",
                    false
                )
            ) {
                AppScreen.HOME
            } else {
                AppScreen.ONBOARDING
            }
        )
    }

    var step by remember { mutableIntStateOf(1) }

    LaunchedEffect(hostActivity?.buildNotificationSequence) {
        if (hostActivity?.consumeBuildNotificationNavigation() == true) {
            screen = AppScreen.BUILDER
            step = 10
        }
    }

    /*
     * Önizleme / Production / AI gibi yardımcı ekranlardan
     * geri dönerken proje ve mevcut builder adımı korunur.
     */
    var workspaceReturnScreen by
        remember {
            mutableStateOf(
                AppScreen.HOME
            )
        }

    var workspaceReturnStep by
        remember {
            mutableIntStateOf(
                1
            )
        }

    fun openWorkspaceScreen(
        target: AppScreen
    ) {
        workspaceReturnScreen =
            screen

        workspaceReturnStep =
            step

        screen =
            target
    }

    fun returnFromWorkspace() {
        val destination =
            workspaceReturnScreen

        screen =
            destination

        if (
            destination ==
            AppScreen.BUILDER
        ) {
            step =
                workspaceReturnStep
        }
    }

    BackHandler(
        enabled =
            screen ==
                AppScreen.CONVERSION ||
            screen ==
                AppScreen.PREVIEW ||
            screen ==
                AppScreen.PRODUCTION ||
            screen ==
                AppScreen.AI_ASSISTANT ||
            screen ==
                AppScreen.HISTORY ||
            screen ==
                AppScreen.TRASH ||
            screen ==
                AppScreen.TEMPLATES ||
            screen ==
                AppScreen.SETTINGS ||
            screen ==
                AppScreen.ACCOUNT
    ) {
        returnFromWorkspace()
    }

    var serverUrl by remember { mutableStateOf(draft.buildServiceUrl) }
    var apiKey by remember {
        mutableStateOf(
            SecureAccountStore
                .loadBuildApiKey(context)
                .orEmpty()
                .ifBlank {
                    draft.buildApiKey
                }
        )
    }
    var session by remember {
        mutableStateOf<Session?>(
            SecureAccountStore
                .loadSession(context)
        )
    }
    var prefs by remember { mutableStateOf(AppSettingsStore.load(context)) }
    var proStatus by remember { mutableStateOf<ProStatus?>(null) }
    var proSecurityMessage by remember { mutableStateOf("") }
    var keystoreRefresh by remember { mutableIntStateOf(0) }

    var status by remember { mutableStateOf("Hazır") }
    var progress by remember { mutableIntStateOf(0) }

    var buildStartedAtMs by
        remember {
            mutableStateOf<Long?>(
                null
            )
        }

    var buildElapsedMs by
        remember {
            mutableLongStateOf(
                0L
            )
        }

    var buildTimerRunning by
        remember {
            mutableStateOf(
                false
            )
        }

    var logs by remember { mutableStateOf(listOf<String>()) }
    var preflight by remember { mutableStateOf(listOf<String>()) }
    var buildId by remember { mutableStateOf<String?>(null) }
    var buildNo by remember { mutableStateOf<Long?>(null) }
    var apkUrl by remember { mutableStateOf<String?>(null) }
    var aabUrl by remember { mutableStateOf<String?>(null) }
    var exeUrl by remember { mutableStateOf<String?>(null) }

    var queuePosition by
        remember {
            mutableStateOf<Int?>(
                null
            )
        }

    var queueAhead by
        remember {
            mutableStateOf<Int?>(
                null
            )
        }

    var queueWorkerSlots by
        remember {
            mutableIntStateOf(
                0
            )
        }

    var queueEtaSeconds by
        remember {
            mutableStateOf<Int?>(
                null
            )
        }

    var queueEstimate by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    /*
     * Build süresi:
     * Başlatıldığı andan itibaren canlı sayar.
     * Başarı / hata / iptal durumunda son değerde kalır.
     */
    LaunchedEffect(
        buildTimerRunning,
        buildStartedAtMs
    ) {
        while (
            buildTimerRunning
        ) {
            val startedAt =
                buildStartedAtMs
                    ?: break

            buildElapsedMs =
                (
                    System.currentTimeMillis() -
                        startedAt
                ).coerceAtLeast(
                    0L
                )

            delay(
                250L
            )
        }
    }

    LaunchedEffect(
        status,
        progress,
        buildTimerRunning
    ) {
        if (
            !buildTimerRunning
        ) {
            return@LaunchedEffect
        }

        val normalizedStatus =
            status
                .trim()
                .lowercase()

        val terminal =
            progress >= 100 ||
                normalizedStatus in
                    setOf(
                        "success",
                        "succeeded",
                        "completed",
                        "done",
                        "failed",
                        "cancelled",
                        "canceled"
                    ) ||
                normalizedStatus
                    .startsWith(
                        "hata"
                    ) ||
                normalizedStatus
                    .contains(
                        "tamamlandı"
                    ) ||
                normalizedStatus
                    .contains(
                        "iptal"
                    )

        if (
            terminal
        ) {
            val startedAt =
                buildStartedAtMs

            if (
                startedAt != null
            ) {
                buildElapsedMs =
                    (
                        System.currentTimeMillis() -
                            startedAt
                    ).coerceAtLeast(
                        0L
                    )
            }

            buildTimerRunning =
                false
        }
    }

    // Yalnız daha önce açıkça kaydedilmiş projeleri debounce ile güncelle.
    // Proje açılışı gerçek değişiklik sayılmaz. İlk farklı taslak Free planda
    // kalıcı slotu bir kez talep eder; sonraki değişiklikler aynı slotu kullanır.
    // ProjectLibrary secret alanları diske yazmadığı için izolasyon korunur.
    LaunchedEffect(currentProjectId, draft) {
        val projectId =
            currentProjectId
                ?: run {
                    autosaveBaseline = null
                    return@LaunchedEffect
                }

        val baseline =
            autosaveBaseline

        if (
            baseline == null ||
            baseline.first != projectId
        ) {
            autosaveBaseline =
                projectId to draft
            return@LaunchedEffect
        }

        if (baseline.second == draft) {
            return@LaunchedEffect
        }

        delay(1200L)

        val canSaveProject =
            proStatus?.active == true ||
            ProjectLibrary
                .claimFreeProjectSlot(
                    context,
                    draft.packageName
                        .trim(),
                    5
                )

        if (!canSaveProject) {
            status =
                "Ücretsiz denemede toplam 5 farklı proje hakkın doldu. " +
                "Bu projedeki ilk değişikliği kaydetmek için Pro veya Pro Aylık gerekli."
            return@LaunchedEffect
        }

        ProjectLibrary.save(
            context,
            draft,
            projectId
        )

        autosaveBaseline =
            projectId to draft
    }

    LaunchedEffect(hostActivity?.buildNotificationSequence) {
        val activity =
            hostActivity
                ?: return@LaunchedEffect

        if (activity.buildNotificationSequence <= 0) {
            return@LaunchedEffect
        }

        val notificationBuildId =
            activity
                .buildIdFromNotification
                ?.takeIf { it.isNotBlank() }
                ?: return@LaunchedEffect

        val notificationServerUrl =
            activity
                .buildServerUrlFromNotification
                ?.takeIf { it.isNotBlank() }
                ?: serverUrl

        if (notificationServerUrl.isBlank()) {
            status = "AppForge bağlantısı hazırlanamadı"
            return@LaunchedEffect
        }

        serverUrl = notificationServerUrl
        buildId = notificationBuildId
        status = "Derleme durumu yükleniyor..."

        val notificationClient =
            BuildApiClient(
                context,
                notificationServerUrl,
                apiKey
            )

        while (true) {
            try {
                val s =
                    withContext(Dispatchers.IO) {
                        notificationClient.getBuild(
                            notificationBuildId
                        )
                    }

                buildId = s.buildId
                buildNo = s.buildNo
                status = s.status
                progress = if (s.status == "success") 100 else s.progress
                logs = s.logs
                preflight = s.preflight

                queuePosition =
                    s.queuePosition

                queueAhead =
                    s.queueAhead

                queueWorkerSlots =
                    s.queueCompatibleWorkerSlots

                queueEtaSeconds =
                    s.queueEstimatedWaitSeconds

                queueEstimate =
                    s.queueEstimate

                apkUrl =
                    if (s.apkAvailable) {
                        "available"
                    } else {
                        null
                    }

                aabUrl =
                    if (s.aabAvailable) {
                        "available"
                    } else {
                        null
                    }

                exeUrl =
                    if (s.exeAvailable) {
                        "available"
                    } else {
                        null
                    }

                val active =
                    s.status == "queued" ||
                        s.status == "building"

                if (!active) {
                    BuildProgressService.clear(context)
                    break
                }

                delay(1_000L)
            } catch (t: Throwable) {
                status = "Derleme durumu alınamadı"
                logs =
                    logs +
                        "Bildirimden build yeniden yüklenemedi: ${t.message.orEmpty()}"
                break
            }
        }
    }

    var conversionApkUri by
        remember {
            mutableStateOf<Uri?>(null)
        }

    var conversionApkName by
        remember {
            mutableStateOf("")
        }

    var conversionExeUri by
        remember {
            mutableStateOf<Uri?>(null)
        }

    var conversionExeName by
        remember {
            mutableStateOf("")
        }

    fun navigateFromAssistant(
        destination: AssistantDestination
    ) {
        val targetBuilderStep =
            when (
                destination
            ) {
                AssistantDestination.SOURCE -> 1
                AssistantDestination.PERMISSIONS -> 2
                AssistantDestination.FEATURES -> 3
                AssistantDestination.APPEARANCE -> 4
                AssistantDestination.NATIVE_BRIDGE -> 5
                AssistantDestination.MONETIZATION -> 6
                AssistantDestination.DEEP_LINK -> 7
                AssistantDestination.SIGNING -> 8
                AssistantDestination.BUILD_SETTINGS -> 9
                AssistantDestination.BUILD -> 10
                AssistantDestination.ADVANCED_CREATE -> 1
                else -> null
            }

        if (
            targetBuilderStep != null
        ) {
            step =
                targetBuilderStep

            screen =
                AppScreen.BUILDER

            status =
                "Yerel AI ilgili düzenleme adımını açtı."

            return
        }

        screen =
            when (
                destination
            ) {
                AssistantDestination.PROJECTS -> AppScreen.HOME
                AssistantDestination.QUICK_CREATE -> AppScreen.MODE_SELECT
                AssistantDestination.CONVERSION -> AppScreen.CONVERSION
                AssistantDestination.PREVIEW -> AppScreen.PRODUCTION
                AssistantDestination.PRODUCTION -> AppScreen.PRODUCTION
                AssistantDestination.TEST_LAB -> AppScreen.TEST_LAB
                AssistantDestination.TEMPLATES -> AppScreen.TEMPLATES
                AssistantDestination.HISTORY -> AppScreen.HISTORY
                AssistantDestination.TRASH -> AppScreen.TRASH
                AssistantDestination.SETTINGS -> AppScreen.SETTINGS
                AssistantDestination.ACCOUNT -> AppScreen.ACCOUNT
                AssistantDestination.HELP -> AppScreen.HELP
                AssistantDestination.PLAY_GUIDE -> AppScreen.PLAY_GUIDE
                AssistantDestination.PRO -> AppScreen.PRO
                AssistantDestination.KEYSTORES -> AppScreen.KEYSTORES
                else -> AppScreen.HOME
            }

        status =
            "Yerel AI ilgili uygulama bölümünü açtı."
    }

    /*
     * Aynı anda yalnızca tek build oluşturulabilir/takip edilir.
     * Birden fazla polling coroutine'in aynı UI state'ini
     * değiştirmesini engeller.
     */
    var buildBusy by
        remember {
            mutableStateOf(false)
        }

    val conversionApkPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument()
        ) {
            uri: Uri? ->

            if (uri != null) {
                conversionApkUri =
                    uri

                conversionApkName =
                    uri.lastPathSegment
                        ?: "selected.apk"

                status =
                    "APK seçildi: $conversionApkName"
            }
        }

    val conversionExePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument()
        ) {
            uri: Uri? ->

            if (uri != null) {
                conversionExeUri =
                    uri

                conversionExeName =
                    uri.lastPathSegment
                        ?: "selected.exe"

                status =
                    "EXE seçildi: $conversionExeName"
            }
        }


    val sourcePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val key = draft.packageName.replace(".", "_").ifBlank { "project" }
                val result =
                    ProjectImporter.importLocalSource(
                        context,
                        uri,
                        key
                    )

                val analysis =
                    SourceCapabilityAnalyzer
                        .analyze(
                            result.projectDir
                        )

                sourceAnalysis =
                    analysis

                draft =
                    draft.copy(
                        sourceUri =
                            uri.toString(),

                        sourceLabel =
                            uri.lastPathSegment
                                ?: "Seçili dosya",

                        importedFolder =
                            result.projectDir
                                .absolutePath,

                        startPage =
                            result.startPage
                                .absolutePath,

                        sourceTechnology =
                            analysis.technologyId,

                        sourceTechnologyLabel =
                            analysis.technologyLabel,

                        sourceBuildEngine =
                            analysis.buildEngine,

                        sourceBuildReady =
                            analysis.buildReady,

                        camera =
                            analysis.camera,

                        microphone =
                            analysis.microphone,

                        location =
                            analysis.location,

                        notifications =
                            analysis.notifications,

                        networkState =
                            analysis.networkState,

                        wakeLock =
                            analysis.wakeLock,

                        nfc =
                            analysis.nfc,

                        additionalPermissions =
                            analysis.additionalPermissions,

                        fileUpload =
                            analysis.fileUpload ||
                                analysis.camera ||
                                analysis.microphone,

                        downloads =
                            analysis.downloads,

                        webMediaAutoplayEnabled =
                            draft.webMediaAutoplayEnabled ||
                                analysis.mediaPlayer,

                        webJavaScriptEnabled =
                            draft.webJavaScriptEnabled ||
                                analysis.mediaPlayer ||
                                analysis.qrScanner,

                        mediaPlayerBridge =
                            analysis.mediaPlayer,

                        qrScanner =
                            analysis.qrScanner,

                        javascriptBridge =
                            draft.javascriptBridge ||
                                analysis.mediaPlayer ||
                                analysis.qrScanner
                    )

                val detected =
                    analysis.detectedLabels()

                status =
                    if (
                        analysis.buildReady
                    ) {
                        "Proje algılandı: ${analysis.technologyLabel} • " +
                            (
                                if (
                                    detected.isEmpty()
                                ) {
                                    "${analysis.scannedFiles} dosya tarandı"
                                } else {
                                    "Otomatik: ${detected.joinToString(", ")}"
                                }
                            )
                    } else {
                        "Proje algılandı: ${analysis.technologyLabel} • " +
                            "uygun derleme yolu otomatik seçilecek"
                    }
            } catch (t: Throwable) {
                status = "Hata: ${t.message}"
            }
        }
    }

    val keystorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            persistReadUriPermission(
                context,
                uri
            )
            draft = draft.copy(
                keystoreUri = uri.toString(),
                keystoreName = uri.lastPathSegment ?: "release.jks"
            )
            status = "Keystore seçildi."
        }
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            persistReadUriPermission(
                context,
                uri
            )
            status =
                "Uygulama ikonu hazırlanıyor..."

            scope.launch {
                try {
                    val prepared =
                        withContext(
                            Dispatchers.IO
                        ) {
                            AppIconProcessor
                                .prepare(
                                    context = context,
                                    source = uri,
                                    backgroundColor =
                                        draft.primaryColor
                                )
                        }

                    draft =
                        draft.copy(
                            iconUri = prepared.uri,
                            iconName = prepared.name
                        )

                    status =
                        "İkon hazırlandı: ${prepared.sourceWidth}×${prepared.sourceHeight} → güvenli 1024×1024 PNG"
                } catch (
                    t: Throwable
                ) {
                    status =
                        "İkon hazırlanamadı: ${t.message}"
                }
            }
        }
    }

    val firebasePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            persistReadUriPermission(
                context,
                uri
            )
            status = "Firebase yapılandırması doğrulanıyor..."
            scope.launch {
                try {
                    val inspection = withContext(Dispatchers.IO) {
                        FirebaseConfigInspector.inspect(context, uri, draft.packageName)
                    }
                    if (!inspection.packageMatches) {
                        status =
                            "Firebase package uyuşmuyor. Beklenen: ${draft.packageName}; dosya: ${inspection.packageNames.joinToString()}"
                        return@launch
                    }
                    draft = draft.copy(
                        firebaseConfigUri = uri.toString(),
                        firebaseConfigName = uri.lastPathSegment ?: "google-services.json"
                    )
                    status = "Firebase doğrulandı • ${inspection.projectId}"
                } catch (t: Throwable) {
                    status = "Firebase dosyası geçersiz: ${t.message}"
                }
            }
        }
    }


    val backupExportLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .CreateDocument(
                        "application/zip"
                    )
        ) {
            uri: Uri? ->
            if (uri != null) {
                try {
                    ProjectBackupManager
                        .exportToUri(
                            context,
                            draft,
                            uri
                        )

                    status =
                        "AppForge proje yedeği dışa aktarıldı."
                } catch (
                    t: Throwable
                ) {
                    status =
                        "Yedek dışa aktarılamadı: ${t.message}"
                }
            }
        }

    val allProjectsExportLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .CreateDocument(
                        "application/zip"
                    )
        ) {
            uri: Uri? ->

            if (uri != null) {
                try {
                    ProjectBackupManager
                        .exportAllProjectsToUri(
                            context,
                            uri
                        )

                    status =
                        "Tüm AppForge projeleri ZIP olarak dışa aktarıldı."
                } catch (
                    t: Throwable
                ) {
                    status =
                        "Projeler dışa aktarılamadı: ${t.message}"
                }
            }
        }


    val allAndroidProjectsExportLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .CreateDocument(
                        "application/zip"
                    )
        ) {
            uri: Uri? ->

            if (uri != null) {
                try {
                    ProjectBackupManager
                        .exportAllAndroidProjectsToUri(
                            context,
                            uri
                        )

                    status =
                        "Android Studio projeleri ZIP olarak dışa aktarıldı."
                } catch (
                    t: Throwable
                ) {
                    status =
                        "Android projeleri dışa aktarılamadı: ${t.message}"
                }
            }
        }


    val backupImportLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenDocument()
        ) {
            uri: Uri? ->
            if (uri != null) {
                try {
                    val importedProjects =
                        ProjectBackupManager
                            .importManyFromUri(
                                context,
                                uri
                            )

                    var importedCount =
                        0

                    var skippedCount =
                        0

                    var firstImportedId:
                        String? =
                        null

                    var firstImportedDraft:
                        ProjectDraft? =
                        null

                    importedProjects.forEach {
                        imported ->

                        val importedDraft =
                            imported.draft

                        val canSaveImported =
                            proStatus?.active ==
                                true ||
                                ProjectLibrary
                                    .claimFreeProjectSlot(
                                        context,
                                        importedDraft
                                            .packageName
                                            .trim(),
                                        5
                                    )

                        if (
                            !canSaveImported
                        ) {
                            skippedCount++

                            imported
                                .importedFolder
                                ?.deleteRecursively()

                            return@forEach
                        }

                        val importedId =
                            ProjectLibrary
                                .save(
                                    context,
                                    importedDraft
                                )

                        importedCount++

                        if (
                            firstImportedId ==
                                null
                        ) {
                            firstImportedId =
                                importedId

                            firstImportedDraft =
                                importedDraft
                        }
                    }

                    val importedId =
                        firstImportedId

                    val importedDraft =
                        firstImportedDraft

                    if (
                        importedId != null &&
                        importedDraft != null
                    ) {
                        draft =
                            importedDraft

                        currentProjectId =
                            importedId

                        autosaveBaseline =
                            importedId to
                                importedDraft

                        serverUrl =
                            importedDraft
                                .buildServiceUrl

                        sourceAnalysis =
                            importedDraft
                                .importedFolder
                                ?.let {
                                    folderPath ->

                                    runCatching {
                                        SourceCapabilityAnalyzer
                                            .analyze(
                                                File(
                                                    folderPath
                                                )
                                            )
                                    }.getOrNull()
                                }

                        status =
                            buildString {
                                append(
                                    "$importedCount proje başarıyla içe aktarıldı."
                                )

                                if (
                                    skippedCount >
                                        0
                                ) {
                                    append(
                                        " $skippedCount proje sınır nedeniyle atlandı."
                                    )
                                }
                            }

                        step =
                            1

                        screen =
                            AppScreen.BUILDER
                    } else {
                        status =
                            if (
                                skippedCount >
                                    0
                            ) {
                                "Ücretsiz proje sınırı dolu. İçe aktarma için Pro gerekli."
                            } else {
                                "İçe aktarılacak proje bulunamadı."
                            }
                    }
                } catch (
                    t: Throwable
                ) {
                    status =
                        "Yedek içe aktarılamadı: ${t.message}"
                }
            }
        }



    val managedKeystorePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenDocument()
        ) {
            uri: Uri? ->
            if (uri != null) {
                try {
                    val imported =
                        KeystoreVault
                            .importFromUri(
                                context,
                                uri
                            )

                    keystoreRefresh++

                    status =
                        "Keystore kasaya eklendi: ${imported.name}"
                } catch (
                    t: Throwable
                ) {
                    status =
                        "Keystore içe aktarılamadı: ${t.message}"
                }
            }
        }

    var aiModelInfo by
        remember {
            mutableStateOf(
                LocalAiModelStore
                    .load(
                        context
                    )
            )
        }

    var aiModelImportMessage by
        remember {
            mutableStateOf(
                ""
            )
        }

    var aiModelInstalling by
        remember {
            mutableStateOf(
                false
            )
        }

    var aiModelInstallProgress by
        remember {
            mutableIntStateOf(
                0
            )
        }

    var showMobileAiDownloadDialog by
        remember {
            mutableStateOf(
                false
            )
        }

    var mobileAiAllowedForSession by
        remember {
            mutableStateOf(
                false
            )
        }

    var mobileAiDeclinedForSession by
        remember {
            mutableStateOf(
                false
            )
        }

    val aiModelPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenDocument()
        ) {
            uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    aiModelImportMessage =
                        "Yerel AI modeli kopyalanıyor..."

                    try {
                        val imported =
                            withContext(
                                Dispatchers.IO
                            ) {
                                LocalAiModelStore
                                    .importModel(
                                        context,
                                        uri
                                    )
                            }

                        aiModelInfo =
                            imported

                        aiModelImportMessage =
                            "Model hazır: ${imported.name}"
                    } catch (
                        t: Throwable
                    ) {
                        aiModelImportMessage =
                            "Model içe aktarılamadı: ${t.message}"
                    }
                }
            }
        }


    // AI_NETWORK_AUTO_INSTALL_V1
    //
    // Wi-Fi:
    //   otomatik indir.
    //
    // Mobil veri:
    //   kullanıcıdan izin iste.
    //
    // Kullanıcı Wi-Fi beklemeyi seçerse:
    //   bu uygulama oturumu boyunca tekrar sorma.
    //   Wi-Fi geldiğinde otomatik devam et.
    LaunchedEffect(
        aiModelInfo,
        mobileAiAllowedForSession,
        mobileAiDeclinedForSession
    ) {
        while (
            aiModelInfo == null
        ) {

            if (
                aiModelInstalling
            ) {
                delay(
                    2_000L
                )

                continue
            }

            when (
                getAiDownloadNetwork(
                    context
                )
            ) {

                AiDownloadNetwork.OFFLINE -> {
                    aiModelImportMessage =
                        "Yerel AI hazırlanacak • internet bağlantısı bekleniyor."

                    delay(
                        10_000L
                    )

                    continue
                }


                AiDownloadNetwork.MOBILE -> {

                    if (
                        !mobileAiAllowedForSession
                    ) {

                        if (
                            !mobileAiDeclinedForSession
                        ) {
                            showMobileAiDownloadDialog =
                                true
                        }

                        aiModelImportMessage =
                            "Yerel AI • Wi-Fi bekleniyor."

                        delay(
                            3_000L
                        )

                        continue
                    }
                }


                AiDownloadNetwork.WIFI -> {
                    /*
                     * Kullanıcıdan hiçbir şey istemeden
                     * otomatik devam.
                     */
                }
            }


            showMobileAiDownloadDialog =
                false

            aiModelInstalling =
                true

            aiModelInstallProgress =
                0

            aiModelImportMessage =
                "Yerel AI hazırlanıyor • %0"


            try {

                val installed =
                    LocalAiModelDownloader
                        .install(
                            context
                        ) {
                            value ->

                            aiModelInstallProgress =
                                value

                            aiModelImportMessage =
                                "Yerel AI hazırlanıyor • %$value"
                        }


                aiModelInfo =
                    installed

                aiModelInstallProgress =
                    100

                aiModelImportMessage =
                    "Yerel AI hazır • cihaz üzerinde çalışıyor."


            } catch (
                t: Throwable
            ) {

                val reason =
                    t.message
                        .orEmpty()

                aiModelImportMessage =
                    when {

                        reason.contains(
                            "boş alan",
                            ignoreCase = true
                        ) ->
                            "Yerel AI için yeterli depolama alanı bekleniyor."

                        else ->
                            "Yerel AI hazırlanamadı • otomatik tekrar denenecek."
                    }


                delay(
                    if (
                        reason.contains(
                            "boş alan",
                            ignoreCase = true
                        )
                    ) {
                        300_000L
                    } else {
                        60_000L
                    }
                )

            } finally {

                aiModelInstalling =
                    false
            }
        }
    }


    // AUTO_PRO_STATUS_REFRESH_V1
    //
    // Uygulama açıldığında veya hesap değiştiğinde
    // Pro yetkisini sunucudan otomatik yenile.
    LaunchedEffect(
        session?.token,
        serverUrl
    ) {
        val current =
            session

        if (current == null) {
            proStatus =
                null

            proSecurityMessage =
                ""

            return@LaunchedEffect
        }

        proSecurityMessage =
            "Pro yetkisi kontrol ediliyor..."

        try {
            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    StudioSecurityClient(
                        context =
                            context,
                        baseUrl =
                            serverUrl,
                        accessToken =
                            current.token
                    ).proStatus(
                        current.userId
                    )
                }

            proStatus =
                result

            proSecurityMessage =
                if (
                    result.active
                ) {
                    "Sunucu doğrulaması başarılı. Pro yetkisi aktif."
                } else {
                    "Hesapta aktif Pro yetkisi bulunamadı."
                }

        } catch (
            t: Throwable
        ) {
            proStatus =
                null

            proSecurityMessage =
                "Pro durumu otomatik doğrulanamadı: ${t.message}"
        }
    }


    val startBuildWithDraft: (ProjectDraft) -> Unit =
        buildStart@{ buildDraft ->

        if (session == null) {
            status =
                "Production derlemesi için kayıt ol veya giriş yap."

            openWorkspaceScreen(
                AppScreen.ACCOUNT
            )

            return@buildStart
        }

        if (
            buildBusy
        ) {
            status =
                "Bir derleme zaten devam ediyor."

            screen =
                AppScreen.BUILDER

            step = 10

            return@buildStart
        }

        buildBusy =
            true
        val storedVersionCode =
            ProjectLibrary
                .load(context)
                .firstOrNull {
                    it.packageName ==
                        buildDraft.packageName
                }
                ?.let {
                    ProjectLibrary
                        .restore(
                            context,
                            it.id
                        )
                        ?.versionCode
                }
                ?: 0

        val effectiveBuildDraft =
            if (
                buildDraft.autoVersionCode
            ) {
                buildDraft.copy(
                    versionCode =
                        maxOf(
                            buildDraft.versionCode,
                            storedVersionCode
                        ) + 1
                )
            } else {
                buildDraft
            }

        draft =
            effectiveBuildDraft

        scope.launch {
            buildStartedAtMs =
                System.currentTimeMillis()

            buildElapsedMs =
                0L

            buildTimerRunning =
                true

            status = "Derleme hazırlanıyor..."
            progress = 2
            logs = emptyList()
            preflight = emptyList()
            buildId = null
            buildNo = null
            apkUrl = null
            aabUrl = null
            exeUrl = null

            queuePosition =
                null

            queueAhead =
                null

            queueWorkerSlots =
                0

            queueEtaSeconds =
                null

            queueEstimate =
                null

            try {
                validateDraft(
                    effectiveBuildDraft,
                    serverUrl
                )

                var uploadCacheHit = false

                val zip =
                    withContext(
                        Dispatchers.IO
                    ) {
                        if (
                            effectiveBuildDraft.sourceMode ==
                            SourceMode.LOCAL
                        ) {
                            val sourceDir =
                                effectiveBuildDraft
                                    .importedFolder
                                    ?.let(::File)
                                    ?: error(
                                        "Önce HTML/ZIP kaynağı seç."
                                    )

                            ZipUtils.cachedZipDirectory(
                                sourceDir = sourceDir,
                                cacheDir = File(
                                    context.cacheDir,
                                    "build-upload-cache"
                                )
                            ).also {
                                uploadCacheHit = it.cacheHit
                            }.file
                        } else {
                            null
                        }
                    }

                val client =
                    BuildApiClient(
                        context = context,
                        baseUrl = serverUrl,
                        apiKey = apiKey
                    )

                if (uploadCacheHit) {
                    status =
                        "Kaynak değişmedi • hızlı ZIP önbelleği kullanıldı"
                }

                /*
                 * Aynı idempotency key bütün tekrar
                 * denemelerinde korunur.
                 *
                 * Böylece sunucu isteği almış fakat
                 * telefon cevabı alamamış olsa bile
                 * ikinci bir build oluşturulmaz.
                 */
                val buildIdempotencyKey =
                    "android-${effectiveBuildDraft.packageName}-${System.currentTimeMillis()}"

                val created =
                    retryInitialBuildRequest(
                        maxAttempts =
                            5,
                        onRetry = {
                            attempt,
                            maxAttempts,
                            _ ->

                            status =
                                "AppForge bağlantısı kuruluyor • $attempt/$maxAttempts"
                        }
                    ) {
                        withContext(
                            Dispatchers.IO
                        ) {
                            client.createBuild(
                                effectiveBuildDraft,
                                zip,
                                idempotencyKey =
                                    buildIdempotencyKey
                            )
                        }
                    }

                buildId =
                    created.buildId

                buildNo =
                    created.buildNo

                BuildProgressService.track(
                    context = context,
                    buildId = created.buildId,
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    appName = effectiveBuildDraft.appName
                )

                status =
                    created.status

                screen =
                    AppScreen.BUILDER

                step = 10

                /*
                 * Build başladıktan sonra geçici ağ / DNS
                 * problemi build'i başarısız saymamalı.
                 *
                 * Worker build'e devam eder.
                 * Telefon aynı Build ID üzerinden yeniden
                 * bağlanmayı sınırsız şekilde dener.
                 */
                var consecutiveNetworkErrors =
                    0

                while (true) {
                    delay(
                        1500
                    )

                    val s =
                        try {
                            withContext(
                                Dispatchers.IO
                            ) {
                                client.getBuild(
                                    created.buildId
                                )
                            }
                        } catch (
                            t: Throwable
                        ) {

                            if (
                                !isTransientBuildNetworkError(
                                    t
                                )
                            ) {
                                throw t
                            }

                            consecutiveNetworkErrors +=
                                1

                            status =
                                "Bağlantı kesildi • yeniden bağlanılıyor..."

                            /*
                             * İlk kesintide ve daha sonra
                             * her 5 denemede bir yerel bilgi
                             * satırı göster.
                             */
                            if (
                                consecutiveNetworkErrors ==
                                    1 ||
                                consecutiveNetworkErrors %
                                    5 ==
                                    0
                            ) {
                                logs =
                                    (
                                        logs +
                                            "🌐 Build devam ediyor • sunucu bağlantısı yeniden kuruluyor (${consecutiveNetworkErrors}. deneme)"
                                    ).takeLast(
                                        120
                                    )
                            }

                            val reconnectDelay =
                                (
                                    1_500L *
                                        consecutiveNetworkErrors
                                ).coerceAtMost(
                                    10_000L
                                )

                            delay(
                                reconnectDelay
                            )

                            continue
                        }

                    if (
                        consecutiveNetworkErrors >
                            0
                    ) {
                        logs =
                            (
                                logs +
                                    "✅ AppForge bağlantısı yeniden kuruldu."
                            ).takeLast(
                                120
                            )
                    }

                    consecutiveNetworkErrors =
                        0

                    status =
                        s.status

                    progress = if (s.status == "success") 100 else s.progress
                    buildNo =
                        s.buildNo
                            ?: buildNo

                    logs =
                        s.logs

                    preflight =
                        s.preflight

                    queuePosition =
                        s.queuePosition

                    queueAhead =
                        s.queueAhead

                    queueWorkerSlots =
                        s.queueCompatibleWorkerSlots

                    queueEtaSeconds =
                        s.queueEstimatedWaitSeconds

                    queueEstimate =
                        s.queueEstimate

                    apkUrl =
                        if (
                            s.apkAvailable
                        ) {
                            "available"
                        } else {
                            null
                        }

                    aabUrl =
                        if (
                            s.aabAvailable
                        ) {
                            "available"
                        } else {
                            null
                        }

                    exeUrl =
                        if (
                            s.exeAvailable
                        ) {
                            "available"
                        } else {
                            null
                        }

                    if (
                        s.status ==
                            "success" ||
                        s.status ==
                            "failed" ||
                        s.status ==
                            "cancelled"
                    ) {
                        BuildProgressService.clear(context)
                        if (
                            s.status ==
                                "success"
                        ) {
                            val canSaveProject =
                                proStatus?.active ==
                                    true ||
                                ProjectLibrary
                                    .claimFreeProjectSlot(
                                        context,
                                        effectiveBuildDraft
                                            .packageName
                                            .trim(),
                                        5
                                    )

                            if (canSaveProject) {
                                val existingProjectId =
                                    currentProjectId
                                        ?: ProjectLibrary
                                            .load(context)
                                            .firstOrNull {
                                                it.packageName ==
                                                    effectiveBuildDraft
                                                        .packageName
                                            }
                                            ?.id

                                currentProjectId =
                                    ProjectLibrary.save(
                                        context,
                                        effectiveBuildDraft,
                                        existingProjectId
                                    )
                            }
                        }

                        ProjectLibrary.saveBuild(
                            context,
                            created.buildId,
                            effectiveBuildDraft,
                            s.status,
                            if (
                                s.apkAvailable
                            ) {
                                "available"
                            } else {
                                null
                            },
                            if (
                                s.aabAvailable
                            ) {
                                "available"
                            } else {
                                null
                            },
                            if (
                                s.exeAvailable
                            ) {
                                "available"
                            } else {
                                null
                            },
                            buildNo =
                                s.buildNo
                        )

                        break
                    }
                }
            } catch (t: Throwable) {
                status =
                    "Hata: ${t.message}"

                progress = 0

                screen =
                    AppScreen.BUILDER

                step = 10

            } finally {
                buildBusy =
                    false
            }
        }
    }

    LaunchedEffect(
        conversionApkUri
    ) {
        val selectedUri =
            conversionApkUri
                ?: return@LaunchedEffect

        status =
            "APK doğrulanıyor..."

        try {
            val converted =
                withContext(
                    Dispatchers.IO
                ) {
                    AppForgeApkConversion
                        .extract(
                            context,
                            selectedUri
                        )
                }

            status =
                "AppForge APK doğrulandı • Windows projesi hazırlanıyor..."

            val conversionDraft =
                ProjectDraft(
                    appName =
                        converted.appName,

                    packageName =
                        converted.appId,

                    sourceMode =
                        converted.sourceMode,

                    sourceLabel =
                        conversionApkName,

                    sourceUri =
                        selectedUri.toString(),

                    importedFolder =
                        converted
                            .projectDir
                            ?.absolutePath,

                    webUrl =
                        converted.webUrl,

                    versionName =
                        converted.versionName,

                    versionCode =
                        converted.versionCode,

                    webJavaScriptEnabled =
                        converted.webJavaScriptEnabled,

                    webDomStorageEnabled =
                        converted.webDomStorageEnabled,

                    webZoomEnabled =
                        converted.webZoomEnabled,

                    webWideViewPortEnabled =
                        converted.webWideViewPortEnabled,

                    webOverviewModeEnabled =
                        converted.webOverviewModeEnabled,

                    webMediaAutoplayEnabled =
                        converted.webMediaAutoplayEnabled,

                    webMixedContentAllowed =
                        converted.webMixedContentAllowed,

                    mediaPlayerBridge =
                        converted.mediaPlayerBridge,

                    buildOutput =
                        "exe",

                    buildServiceUrl =
                        serverUrl,

                    buildApiKey =
                        apiKey
                )

            draft =
                conversionDraft

            startBuildWithDraft(
                conversionDraft
            )

            /*
             * İşleme tamamlandıktan sonra temizle.
             * Böylece LaunchedEffect kendisini iptal etmez.
             */
            conversionApkUri =
                null

        } catch (
            t: Throwable
        ) {
            status =
                "Dönüşüm hatası: ${t.message}"

            progress =
                0

            screen =
                AppScreen.CONVERSION

            conversionApkUri =
                null
        }
    }


    LaunchedEffect(
        conversionExeUri
    ) {
        val selectedUri =
            conversionExeUri
                ?: return@LaunchedEffect

        status =
            "EXE doğrulanıyor..."

        try {
            val converted =
                withContext(
                    Dispatchers.IO
                ) {
                    AppForgeExeConversion
                        .extract(
                            context,
                            selectedUri
                        )
                }

            status =
                "AppForge EXE doğrulandı • Android projesi hazırlanıyor..."

            val conversionDraft =
                ProjectDraft(
                    appName =
                        converted.appName,

                    packageName =
                        converted.appId,

                    sourceMode =
                        converted.sourceMode,

                    sourceLabel =
                        conversionExeName,

                    sourceUri =
                        selectedUri.toString(),

                    importedFolder =
                        converted
                            .projectDir
                            ?.absolutePath,

                    webUrl =
                        converted.webUrl,

                    versionName =
                        converted.versionName,

                    versionCode =
                        converted.versionCode,

                    webJavaScriptEnabled =
                        converted.webJavaScriptEnabled,

                    webDomStorageEnabled =
                        converted.webDomStorageEnabled,

                    webZoomEnabled =
                        converted.webZoomEnabled,

                    webWideViewPortEnabled =
                        converted.webWideViewPortEnabled,

                    webOverviewModeEnabled =
                        converted.webOverviewModeEnabled,

                    webMediaAutoplayEnabled =
                        converted.webMediaAutoplayEnabled,

                    webMixedContentAllowed =
                        converted.webMixedContentAllowed,

                    mediaPlayerBridge =
                        converted.mediaPlayerBridge,

                    buildOutput =
                        "apk",

                    buildServiceUrl =
                        serverUrl,

                    buildApiKey =
                        apiKey
                )

            draft =
                conversionDraft

            startBuildWithDraft(
                conversionDraft
            )

            /*
             * APK → EXE akışındaki gibi URI,
             * extraction/build başlangıcından sonra temizlenir.
             */
            conversionExeUri =
                null

        } catch (
            t: Throwable
        ) {
            status =
                "Dönüşüm hatası: ${t.message}"

            progress =
                0

            screen =
                AppScreen.CONVERSION

            conversionExeUri =
                null
        }
    }


    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF061827),
            secondary = Color(0xFFB79CE5),
            background = Bg,
            onBackground = Color(0xFFE8EDF4),
            surface = Card2,
            onSurface = Color(0xFFE8EDF4),
            surfaceVariant = Color(0xFF18212A),
            onSurfaceVariant = TextSecondary,
            outline = Color(0xFF3B4652)
        )
    ) {

        // MOBILE_AI_DOWNLOAD_DIALOG_V1
        if (
            showMobileAiDownloadDialog &&
            aiModelInfo == null
        ) {
            AlertDialog(
                onDismissRequest = {
                    showMobileAiDownloadDialog =
                        false

                    mobileAiDeclinedForSession =
                        true

                    aiModelImportMessage =
                        "Yerel AI • Wi-Fi bekleniyor."
                },

                title = {
                    Text(
                        "Yerel AI"
                    )
                },

                text = {
                    Text(
                        "Yerel AI modeli yaklaşık 586 MB. " +
                        "Şu anda mobil veri kullanıyorsun. " +
                        "Mobil veri ile indirilsin mi?"
                    )
                },

                confirmButton = {
                    TextButton(
                        onClick = {
                            showMobileAiDownloadDialog =
                                false

                            mobileAiDeclinedForSession =
                                false

                            mobileAiAllowedForSession =
                                true

                            aiModelImportMessage =
                                "Yerel AI mobil veri ile hazırlanıyor..."
                        }
                    ) {
                        Text(
                            "Mobil Veriyle İndir"
                        )
                    }
                },

                dismissButton = {
                    TextButton(
                        onClick = {
                            showMobileAiDownloadDialog =
                                false

                            mobileAiAllowedForSession =
                                false

                            mobileAiDeclinedForSession =
                                true

                            aiModelImportMessage =
                                "Yerel AI • Wi-Fi bekleniyor."
                        }
                    ) {
                        Text(
                            "Wi-Fi Bekle"
                        )
                    }
                }
            )
        }

        Surface(Modifier.fillMaxSize(), color = Bg) {
            when (screen) {

                AppScreen.ONBOARDING ->
                    AppForgeOnboardingScreen(
                        onDone = {
                            context
                                .getSharedPreferences(
                                    "appforge_onboarding",
                                    Context.MODE_PRIVATE
                                )
                                .edit()
                                .putBoolean(
                                    "completed",
                                    true
                                )
                                .apply()

                            screen =
                                AppScreen.HOME
                        }
                    )

                AppScreen.HOME ->
                    AppForgeMotionBackground {
                        com.appforge.studio.ui.StudioHomeV2(
                        proUnlocked =
                            proStatus?.active == true,

                        onCreateQuick = {
                            val fresh =
                                createQuickDraft(
                                    ProjectDraft()
                                )

                            draft = fresh

                            currentProjectId =
                                null

                            serverUrl =
                                fresh.buildServiceUrl

                            status =
                                "Hızlı oluşturma hazır."

                            screen =
                                AppScreen.QUICK
                        },

                        onCreateAdvanced = {
                            val fresh =
                                ProjectDraft()

                            draft = fresh

                            currentProjectId =
                                null

                            serverUrl =
                                fresh.buildServiceUrl

                            step = 1

                            status =
                                "Gelişmiş oluşturma hazır."

                            screen =
                                AppScreen.BUILDER
                        },

                        onCreateConversion = {
                            status =
                                "Dönüşüm araçları hazır."

                            openWorkspaceScreen(
                                AppScreen.CONVERSION
                            )
                        },

                        onOpenProject = {
                            saved ->

                            ProjectLibrary
                                .restore(
                                    context,
                                    saved.id
                                )
                                ?.let {
                                    restored ->

                                    draft =
                                        restored

                                    currentProjectId =
                                        saved.id

                                    serverUrl =
                                        restored
                                            .buildServiceUrl

                                    apiKey =
                                        SecureAccountStore
                                            .loadBuildApiKey(
                                                context
                                            )
                                            .orEmpty()

                                    step = 1

                                    screen =
                                        AppScreen.BUILDER

                                    status =
                                        "Proje yüklendi: ${saved.name}"
                                }
                        },

                        onOpenAi = {
                            openWorkspaceScreen(
                                AppScreen.AI_ASSISTANT
                            )
                        },

                        onOpenTemplates = {
                            openWorkspaceScreen(
                                AppScreen.TEMPLATES
                            )
                        },

                        onOpenSettings = {
                            openWorkspaceScreen(
                                AppScreen.SETTINGS
                            )
                        },

                        onImportProject = {
                            backupImportLauncher
                                .launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream"
                                    )
                                )
                        },

                        onExportAllProjects = {
                            if (
                                ProjectLibrary
                                    .load(
                                        context
                                    )
                                    .isEmpty()
                            ) {
                                status =
                                    "Dışa aktarılacak proje yok."
                            } else {
                                allProjectsExportLauncher
                                    .launch(
                                        "AppForge_Tum_Projeler.zip"
                                    )
                            }
                        },

                        onExportAllAndroidProjects = {
                            if (
                                ProjectLibrary
                                    .load(
                                        context
                                    )
                                    .isEmpty()
                            ) {
                                status =
                                    "Dışa aktarılacak proje yok."
                            } else {
                                allAndroidProjectsExportLauncher
                                    .launch(
                                        "AppForge_Tum_Android_Projeleri.zip"
                                    )
                            }
                        },

                        onOpenAccount = {
                            openWorkspaceScreen(
                                AppScreen.ACCOUNT
                            )
                        },

                        onOpenHistory = {
                            openWorkspaceScreen(
                                AppScreen.HISTORY
                            )
                        },

                        onOpenTrash = {
                            openWorkspaceScreen(
                                AppScreen.TRASH
                            )
                        },

                        onOpenPro = {
                            screen =
                                AppScreen.PRO
                        }
                    )
                    }

                AppScreen.MODE_SELECT ->
                    CreateModeSelectionScreen(
                        onQuick = {
                            draft =
                                createQuickDraft(
                                    draft
                                )

                            status =
                                "Hızlı oluşturma hazır."

                            screen =
                                AppScreen.QUICK
                        },
                        onAdvanced = {
                            step = 1
                            status =
                                "Gelişmiş oluşturma hazır."

                            screen =
                                AppScreen.BUILDER
                        },
                        onConversion = {
                            status =
                                "Dönüşüm araçları hazır."

                            openWorkspaceScreen(
                                AppScreen.CONVERSION
                            )
                        }
                    )

                AppScreen.CONVERSION ->
                    ConversionScreen(
                        selectedApkName =
                            conversionApkName,
                        selectedExeName =
                            conversionExeName,
                        status =
                            status,
                        onBack = {
                            returnFromWorkspace()
                        },
                        onApkToExe = {
                            conversionApkPicker.launch(
                                arrayOf(
                                    "application/vnd.android.package-archive",
                                    "application/octet-stream"
                                )
                            )
                        },
                        onExeToApk = {
                            /*
                             * Android üreticileri .exe dosyalarını
                             * farklı MIME türleriyle tanıyabiliyor.
                             * Tüm dosyaları seçilebilir göster;
                             * AppForgeExeConversion zaten MZ +
                             * AppForge footer/manifest doğrulaması yapıyor.
                             */
                            conversionExePicker.launch(
                                arrayOf("*/*")
                            )
                        }
                    )

                AppScreen.QUICK ->
                    QuickCreateScreen(
                        draft = draft,
                        status = status,
                        onDraftChange = {
                            draft = it
                        },
                        onBack = {
                            screen =
                                AppScreen.HOME
                        },
                        onPickSource = {
                            sourcePicker.launch(
                                arrayOf(
                                    "text/html",
                                    "application/zip",
                                    "application/octet-stream"
                                )
                            )
                        },
                        onPickIcon = {
                            iconPicker.launch(
                                arrayOf(
                                    "image/*"
                                )
                            )
                        },
                        onPickFirebase = {
                            firebasePicker.launch(
                                arrayOf(
                                    "application/json",
                                    "text/json",
                                    "application/octet-stream"
                                )
                            )
                        },
                        onAdvanced = {
                            step = 1
                            screen =
                                AppScreen.BUILDER
                        },
                        onPreview = {
                            openWorkspaceScreen(
                                AppScreen.PREVIEW
                            )
                        },
                        onBuild = {
                            /*
                             * Quick varsayılanları ekrana girerken
                             * zaten uygulanıyor.
                             *
                             * Build sırasında tekrar createQuickDraft()
                             * çağırmak kamera/konum gibi sonradan
                             * değiştirilmiş ayarları sıfırlıyordu.
                             */
                            startBuildWithDraft(
                                draft
                            )
                        }
                    )

                AppScreen.LIBRARY -> ProjectLibraryScreen(
                    proUnlocked = proStatus?.active == true,
                    onBack = { screen = AppScreen.HOME },
                    onLoad = { saved ->
                        ProjectLibrary.restore(context, saved.id)?.let {
                            draft = it
                            sourceAnalysis =
                                it.importedFolder
                                    ?.let { folderPath ->
                                        runCatching {
                                            SourceCapabilityAnalyzer
                                                .analyze(
                                                    File(folderPath)
                                                )
                                        }.getOrNull()
                                    }
                            currentProjectId = saved.id
                            serverUrl = it.buildServiceUrl
                            apiKey =
                                SecureAccountStore
                                    .loadBuildApiKey(
                                        context
                                    )
                                    .orEmpty()
                            step = 1
                            screen = AppScreen.BUILDER
                            status = "Proje yüklendi: ${saved.name}"
                        }
                    }
                )

                AppScreen.HISTORY -> BuildHistoryScreen(
                    onBack = {
                        returnFromWorkspace()
                    }
                )

                AppScreen.TRASH -> StudioTrashScreen(
                    onBack = {
                        returnFromWorkspace()
                    },
                    onMessage = {
                        status = it
                    }
                )

                AppScreen.ACCOUNT -> AccountScreen(
                    serverUrl = serverUrl,
                    session = session,
                    onSession = {
                        session = it

                        if (it != null) {
                            SecureAccountStore
                                .saveSession(
                                    context,
                                    it
                                )
                        } else {
                            SecureAccountStore
                                .clearAll(
                                    context
                                )

                            apiKey = ""

                            draft =
                                draft.copy(
                                    buildApiKey = ""
                                )
                        }
                    },
                    onApiKeyCreated = {
                        apiKey = it

                        draft =
                            draft.copy(
                                buildApiKey = it
                            )

                        SecureAccountStore
                            .saveBuildApiKey(
                                context,
                                it
                            )
                    },
                    onBack = {
                        returnFromWorkspace()
                    }
                )

                AppScreen.TEMPLATES -> TemplatesScreen(
                    serverUrl = serverUrl,
                    session = session,
                    onApply = { template ->
                        val configuredDraft =
                            applyTemplate(
                                draft,
                                template
                            )

                        val generated =
                            runCatching {
                                TemplateProjectFactory
                                    .materialize(
                                        context,
                                        template
                                    )
                            }
                                .getOrNull()

                        draft =
                            if (
                                generated != null
                            ) {
                                configuredDraft.copy(
                                    appName =
                                        configuredDraft
                                            .appName
                                            .ifBlank {
                                                template.name
                                            },
                                    packageName =
                                        if (
                                            configuredDraft
                                                .packageName
                                                .isBlank() ||
                                            configuredDraft
                                                .packageName ==
                                                "com.example.myapp"
                                        ) {
                                            quickPackageName(
                                                template.name
                                            )
                                        } else {
                                            configuredDraft
                                                .packageName
                                        },
                                    sourceMode =
                                        SourceMode.LOCAL,
                                    sourceUri =
                                        null,
                                    sourceLabel =
                                        "${template.name} • Hazır şablon",
                                    importedFolder =
                                        generated
                                            .projectDir
                                            .absolutePath,
                                    startPage =
                                        generated
                                            .startPage
                                            .absolutePath
                                )
                            } else {
                                configuredDraft
                            }

                        currentProjectId =
                            null

                        status =
                            if (
                                generated != null
                            ) {
                                "Hazır proje oluşturuldu: ${template.name}"
                            } else {
                                "Şablon uygulandı: ${template.name}"
                            }

                        step = 1
                        screen = AppScreen.BUILDER
                    },
                    onBack = {
                        returnFromWorkspace()
                    }
                )


                AppScreen.SETTINGS -> SettingsHubScreen(
                    languageCode = prefs.languageCode,
                    proUnlocked = proStatus?.active == true,
                    onBack = {
                        returnFromWorkspace()
                    },
                    onOpenLanguage = { screen = AppScreen.LANGUAGE },
                    onOpenKeystore = { screen = AppScreen.KEYSTORES },
                    onOpenPro = { screen = AppScreen.PRO },
                    onOpenHowTo = { screen = AppScreen.HELP },
                    onOpenPlayGuide = { screen = AppScreen.PLAY_GUIDE },
                    onOpenLegal = { screen = AppScreen.LEGAL },
                    onFeedback = {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_SENDTO,
                                Uri.parse("mailto:28550040284a@gmail.com")
                            )
                            intent.putExtra(Intent.EXTRA_SUBJECT, "AppForge Studio Geri Bildirim")
                            context.startActivity(intent)
                        }.onFailure {
                            status = "E-posta uygulaması açılamadı: ${it.message}"
                        }
                    },
                    onClearCache = {
                        val cleared = clearTemporaryCache(context)
                        status = "${StudioI18n.t(prefs.languageCode, "cache_cleared")}: ${formatFileSize(cleared)}"
                    }
                )

                AppScreen.LANGUAGE -> LanguageSettingsScreen(
                    languageCode = prefs.languageCode,
                    onBack = { screen = AppScreen.SETTINGS },
                    onSelect = { code ->
                        prefs = AppSettingsStore.updateLanguage(context, code)
                        status = "Dil güncellendi: $code"
                    }
                )

                AppScreen.LEGAL -> LegalCenterScreen(
                    languageCode = prefs.languageCode,
                    onBack = { screen = AppScreen.SETTINGS }
                )

                AppScreen.HELP -> AppForgeHelpCenterScreen(
                    languageCode = prefs.languageCode,
                    onBack = { screen = AppScreen.SETTINGS }
                )

                AppScreen.PLAY_GUIDE -> PlayPublishingGuideScreen(
                    languageCode = prefs.languageCode,
                    onBack = { screen = AppScreen.SETTINGS }
                )

                AppScreen.PRO -> ProUpgradeScreen(
                    languageCode = prefs.languageCode,
                    serverUrl = serverUrl,
                    session = session,
                    currentStatus = proStatus,
                    securityMessage = proSecurityMessage,
                    onBack = { screen = AppScreen.SETTINGS },
                    onVerified = { result ->
                        proStatus = result
                        proSecurityMessage =
                            if (result.active) {
                                "Sunucu doğrulaması başarılı. Pro yetkisi aktif."
                            } else {
                                "Hesapta aktif Pro yetkisi bulunamadı."
                            }
                    },
                    onSecurityMessage = {
                        proSecurityMessage = it
                    }
                )

                AppScreen.KEYSTORES -> KeystoreManagerScreen(
                    languageCode = prefs.languageCode,
                    refreshKey = keystoreRefresh,
                    onBack = { screen = AppScreen.SETTINGS },
                    onImport = {
                        if (
                            proStatus?.active ==
                            true
                        ) {
                            managedKeystorePicker.launch(
                                arrayOf(
                                    "application/x-java-keystore",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        } else {
                            status =
                                "Özel keystore içe aktarma için sunucu doğrulamalı Pro gerekli."
                            screen =
                                AppScreen.PRO
                        }
                    },
                    onMessage = { status = it }
                )


                AppScreen.PREVIEW -> AppPreviewScreen(
                    draft = draft,
                    languageCode = prefs.languageCode,
                    onBack = {
                        returnFromWorkspace()
                    },
                    onOpenProduction = {
                        screen =
                            AppScreen.PRODUCTION
                    }
                )

                AppScreen.PRODUCTION -> ProductionCenterScreen(
                    draft = draft,
                    languageCode = prefs.languageCode,
                    proUnlocked = proStatus?.active == true,
                    onDraftChange = {
                        draft = it
                    },
                    onBack = {
                        returnFromWorkspace()
                    },
                    onPreview = {
                        screen =
                            AppScreen.PREVIEW
                    },
                    onTemplates = {
                        screen =
                            AppScreen.TEMPLATES
                    },
                    onTestLab = {
                        screen =
                            AppScreen.TEST_LAB
                    },
                    onExportBackup = {
                        val safeName =
                            draft.appName
                                .ifBlank {
                                    "AppForgeProject"
                                }
                                .replace(
                                    Regex(
                                        "[^A-Za-z0-9._-]+"
                                    ),
                                    "_"
                                )

                        backupExportLauncher.launch(
                            "${safeName}_AppForge.zip"
                        )
                    },
                    onImportBackup = {
                        backupImportLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/octet-stream"
                            )
                        )
                    }
                )


                AppScreen.AI_ASSISTANT -> LocalAiAssistantScreen(
                    draft =
                        draft,
                    onDraftChange = {
                        updated ->
                        draft =
                            updated

                        status =
                            "AI proje düzeltmeleri uygulandı."
                    },
                    currentProjectId =
                        currentProjectId,
                    onSelectProject = {
                        saved ->

                        ProjectLibrary
                            .restore(
                                context,
                                saved.id
                            )
                            ?.let {
                                restored ->

                                draft =
                                    restored

                                currentProjectId =
                                    saved.id

                                serverUrl =
                                    restored.buildServiceUrl

                                sourceAnalysis =
                                    restored.importedFolder
                                        ?.let {
                                            folderPath ->

                                            runCatching {
                                                SourceCapabilityAnalyzer
                                                    .analyze(
                                                        File(folderPath)
                                                    )
                                            }.getOrNull()
                                        }

                                status =
                                    "AI analiz projesi seçildi: ${saved.name}"
                            }
                    },
                    runtimeContext =
                        AssistantRuntimeContext(
                            workspace =
                                workspaceReturnScreen.name,
                            builderStep =
                                workspaceReturnStep,
                            buildStatus =
                                status,
                            buildProgress =
                                progress,
                            sourceTechnology =
                                draft.sourceTechnologyLabel,
                            sourceBuildReady =
                                draft.sourceBuildReady,
                            signedIn =
                                session != null,
                            proActive =
                                proStatus?.active == true,
                            hasApk =
                                !apkUrl.isNullOrBlank(),
                            hasAab =
                                !aabUrl.isNullOrBlank(),
                            hasExe =
                                !exeUrl.isNullOrBlank(),
                            buildDiagnosis =
                                if (
                                    logs.isNotEmpty() ||
                                    preflight.isNotEmpty()
                                ) {
                                    AppForgeBuildErrorAdvisor
                                        .diagnose(
                                            logs,
                                            preflight,
                                            status
                                        )
                                        .let {
                                            "${it.title}: ${it.reason}"
                                        }
                                } else {
                                    null
                                }
                        ),
                    buildLogs =
                        logs,
                    buildPreflight =
                        preflight,
                    languageCode =
                        prefs.languageCode,
                    modelInfo =
                        aiModelInfo,
                    importMessage =
                        aiModelImportMessage,
                    installing =
                        aiModelInstalling,
                    installProgress =
                        aiModelInstallProgress,
                    onInstallDefaultModel = {
                        if (
                            !aiModelInstalling
                        ) {
                            scope.launch {
                                aiModelInstalling =
                                    true

                                aiModelInstallProgress =
                                    0

                                aiModelImportMessage =
                                    "Yerel AI hazırlanıyor..."

                                try {
                                    val installed =
                                        LocalAiModelDownloader
                                            .install(
                                                context
                                            ) {
                                                progress ->

                                                aiModelInstallProgress =
                                                    progress

                                                aiModelImportMessage =
                                                    "Yerel AI indiriliyor • %$progress"
                                            }

                                    aiModelInfo =
                                        installed

                                    aiModelImportMessage =
                                        "Yerel AI kuruldu • ${installed.name}"
                                } catch (
                                    t: Throwable
                                ) {
                                    aiModelImportMessage =
                                        "Yerel AI kurulamadı: ${t.message}"
                                } finally {
                                    aiModelInstalling =
                                        false
                                }
                            }
                        }
                    },
                    onImportModel = {
                        aiModelPicker.launch(
                            arrayOf(
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    onModelChanged = {
                        aiModelInfo =
                            it
                    },
                    onNavigate = {
                        destination ->

                        navigateFromAssistant(
                            destination
                        )
                    },
                    onBack = {
                        returnFromWorkspace()
                    }
                )

                AppScreen.TEST_LAB -> TestLabScreen(
                    serverUrl =
                        serverUrl,
                    apiKey =
                        apiKey,
                    languageCode =
                        prefs.languageCode,
                    onBack = {
                        screen =
                            AppScreen.PRODUCTION
                    }
                )

                AppScreen.BUILDER -> Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text("AppForge Studio", fontWeight = FontWeight.Bold)
                                Text(
                                    "Adım $step/10 • " +
                                        when (step) {
                                            1 -> "Proje"
                                            2 -> "İzinler"
                                            3 -> "WebView"
                                            4 -> "Görünüm"
                                            5 -> "Native Bridge"
                                            6 -> "Gelir ve Firebase"
                                            7 -> "Deep Link"
                                            8 -> "İmzalama"
                                            9 -> "Derleme ayarları"
                                            else -> "Derleme"
                                        },
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            LabeledActionButton(
                                icon = "⌂",
                                label = "Ana Sayfa",
                                onClick = {
                                    screen =
                                        AppScreen.HOME
                                }
                            )
                        },
                        actions = {},
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                    )

                    LinearProgressIndicator(
                        progress = { step / 10f },
                        modifier =
                            Modifier
                                .align(
                                    Alignment.CenterHorizontally
                                )
                                .widthIn(
                                    max =
                                        builderContentMaxWidth
                                )
                                .fillMaxWidth()
                    )

                    Row(
                        modifier =
                            Modifier
                                .align(
                                    Alignment.CenterHorizontally
                                )
                                .widthIn(
                                    max =
                                        builderContentMaxWidth
                                )
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        if (builderCompact) {
                                            6.dp
                                        } else {
                                            10.dp
                                        },
                                    vertical =
                                        4.dp
                                ),
                        horizontalArrangement =
                            Arrangement
                                .spacedBy(
                                    if (builderCompact) {
                                        4.dp
                                    } else {
                                        8.dp
                                    }
                                )
                    ) {
                        Button(
                            onClick = {
                                openWorkspaceScreen(
                                    AppScreen.PRODUCTION
                                )
                            },
                            modifier =
                                Modifier
                                    .weight(
                                        1f
                                    )
                        ) {
                            Text(
                                if (builderCompact) {
                                    "Üretim"
                                } else {
                                    "🚀 Üretim"
                                },
                                fontSize =
                                    if (builderCompact) {
                                        12.sp
                                    } else {
                                        14.sp
                                    },
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                openWorkspaceScreen(
                                    AppScreen.AI_ASSISTANT
                                )
                            },
                            modifier =
                                Modifier
                                    .weight(
                                        1f
                                    )
                        ) {
                            Text(
                                if (builderCompact) {
                                    "AI"
                                } else {
                                    "✨ AI"
                                },
                                fontSize =
                                    if (builderCompact) {
                                        12.sp
                                    } else {
                                        14.sp
                                    },
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .align(
                                    Alignment.CenterHorizontally
                                )
                                .widthIn(
                                    max =
                                        builderContentMaxWidth
                                )
                                .fillMaxWidth()
                    ) {
                        when (step) {
                            1 -> SourceStep(draft, status, { draft = it }) {
                                sourcePicker.launch(arrayOf("text/html", "application/zip", "application/octet-stream"))
                            }

                            2 -> PermissionsStep(
                                draft,
                                sourceAnalysis
                            ) { draft = it }

                            3 -> FeaturesStep(
                                draft,
                                sourceAnalysis
                            ) { draft = it }

                            4 -> AppearanceStep(draft, { draft = it }) {
                                iconPicker.launch(arrayOf("image/*"))
                            }

                            5 -> NativeBridgeStep(
                                draft,
                                sourceAnalysis
                            ) { draft = it }

                            6 -> MonetizationStep(
                                draft = draft,
                                update = { draft = it },
                                onPickFirebase = {
                                    firebasePicker.launch(arrayOf("application/json", "text/json", "text/plain"))
                                }
                            )

                            7 -> DeepLinkStep(draft) { draft = it }

                            8 -> SigningStep(draft, { draft = it }) {
                                keystorePicker.launch(arrayOf(
                                    "application/octet-stream",
                                    "application/x-java-keystore"
                                ))
                            }

                            9 -> BuildSettingsStep(
                                draft = draft,
                                update = { draft = it },
                                serverUrl = serverUrl,
                                apiKey = apiKey,
                                statusMessage = status,
                                onServerUrl = {
                                    serverUrl = it
                                    draft = draft.copy(buildServiceUrl = it)
                                },
                                onApiKey = {
                                    apiKey = it

                                    draft =
                                        draft.copy(
                                            buildApiKey = it
                                        )

                                    // API anahtarı proje JSON'una yazılmaz.
                                    // Android Keystore ile şifrelenmiş
                                    // hesap/genel ayar olarak saklanır.
                                    SecureAccountStore
                                        .saveBuildApiKey(
                                            context,
                                            it
                                        )
                                },
                                onSave = {
                                    val packageName =
                                        draft.packageName
                                            .trim()

                                    val canUseSlot =
                                        proStatus?.active ==
                                            true ||
                                        ProjectLibrary
                                            .claimFreeProjectSlot(
                                                context,
                                                packageName,
                                                5
                                            )

                                    if (!canUseSlot) {
                                        status =
                                            "Ücretsiz denemede toplam 5 farklı proje hakkın doldu. Proje silmek yeni hak açmaz. Yeni proje için Pro veya Pro Aylık gerekli."
                                    } else {
                                        currentProjectId =
                                            ProjectLibrary.save(
                                                context,
                                                draft,
                                                currentProjectId
                                            )

                                    }
                                }
                            )

                            else -> BuildStep(
                                draft = draft,
                                onDraftChange = {
                                    updated ->
                                    draft =
                                        updated
                                },
                                onRetryBuild = {
                                    retryDraft ->
                                    startBuildWithDraft(
                                        retryDraft
                                    )
                                },
                                status = status,
                                progress = progress,
                                buildElapsedMs = buildElapsedMs,
                                buildTimerRunning = buildTimerRunning,
                                logs = logs,
                                preflight = preflight,
                                buildId = buildId,
                                buildNo = buildNo,
                                appName = draft.appName,
                                serverUrl = serverUrl,
                                apiKey = apiKey,
                                apkUrl = apkUrl,
                                aabUrl = aabUrl,
                                exeUrl = exeUrl,
                                buildOutput =
                                    draft.buildOutput,
                                queuePosition =
                                    queuePosition,
                                queueAhead =
                                    queueAhead,
                                queueWorkerSlots =
                                    queueWorkerSlots,
                                queueEtaSeconds =
                                    queueEtaSeconds,
                                queueEstimate =
                                    queueEstimate
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .align(
                                    Alignment.CenterHorizontally
                                )
                                .widthIn(
                                    max =
                                        builderContentMaxWidth
                                )
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        builderHorizontalPadding,
                                    vertical =
                                        if (builderCompact) {
                                            10.dp
                                        } else {
                                            20.dp
                                        }
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                if (builderCompact) {
                                    6.dp
                                } else {
                                    10.dp
                                }
                            )
                    ) {
                        if (step > 1) {
                            OutlinedButton(
                                onClick = { step-- },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(
                                            if (builderCompact) {
                                                48.dp
                                            } else {
                                                52.dp
                                            }
                                        )
                            ) {
                                Text("Geri")
                            }
                        }

                        Button(
                            enabled =
                                !(
                                    step == 10 &&
                                    buildBusy
                                ),
                            onClick = {
                                if (step < 10) {
                                    step++
                                } else {
                                    startBuildWithDraft(
                                        draft
                                    )
                                }
                            },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(
                                        if (builderCompact) {
                                            48.dp
                                        } else {
                                            52.dp
                                        }
                                    )
                        ) {
                            Text(
                                when {
                                    step < 10 ->
                                        "Devam"

                                    buildBusy ->
                                        if (builderCompact) {
                                            "DERLENİYOR"
                                        } else {
                                            "DERLEME DEVAM EDİYOR"
                                        }

                                    else ->
                                        if (builderCompact) {
                                            "DERLE"
                                        } else {
                                            "UYGULAMAYI DERLE"
                                        }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


private fun quickPackageName(
    appName: String
): String {
    val normalized =
        appName
            .lowercase()
            .replace(
                Regex(
                    "[^a-z0-9]+"
                ),
                ""
            )
            .take(40)

    val safeName =
        when {
            normalized.isBlank() ->
                "myapp"

            normalized.first()
                .isDigit() ->
                "app$normalized"

            else ->
                normalized
        }

    return "com.appforge.$safeName"
}

private fun createQuickDraft(
    base: ProjectDraft
): ProjectDraft =
    base.copy(
        packageName =
            quickPackageName(
                base.appName
            ),
        versionName =
            "1.0.0",
        versionCode =
            base.versionCode.coerceAtLeast(1),
        autoVersionCode = true,
        buildOutput =
            "apk",
        orientation =
            "unspecified",
        splashEnabled =
            true,
        splashText =
            base.appName
                .ifBlank {
                    "AppForge App"
                },
        signingMode =
            SigningMode.DEBUG,
        keystoreUri =
            null,
        keystoreName =
            "",
        keyAlias =
            "",
        storePassword =
            "",
        keyPassword =
            "",
        fileUpload =
            true,
        downloads =
            true,
        fullscreen =
            false,
        notifications =
            true,
        camera =
            false,
        location =
            false,
        offlineCache =
            true,
        deepLinkEnabled =
            false,
        javascriptBridge =
            false,
        remoteBridgeAllowed =
            false,
        shareBridge =
            false,
        clipboardBridge =
            false,
        vibrationBridge =
            false,
        mediaPlayerBridge =
            false,
        qrScanner =
            false,
        admobEnabled =
            false,
        billingEnabled =
            false,
        firebaseAnalyticsEnabled =
            false,
        firebaseCrashlyticsEnabled =
            false,
        firebaseMessagingEnabled =
            false
    )

@Composable
private fun CreateModeSelectionScreen(
    onQuick: () -> Unit,
    onAdvanced: () -> Unit,
    onConversion: () -> Unit
) {
    val configuration =
        LocalConfiguration.current

    val screenWidthDp =
        configuration.screenWidthDp

    val screenHeightDp =
        configuration.screenHeightDp

    val compact =
        screenWidthDp < 380

    val tablet =
        minOf(
            screenWidthDp,
            screenHeightDp
        ) >= 600

    val outerHorizontalPadding =
        when {
            compact -> 12.dp
            tablet -> 32.dp
            else -> 24.dp
        }

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = outerHorizontalPadding,
                vertical =
                    if (compact) 14.dp else 24.dp
            ),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        item {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp),
                shape =
                    RoundedCornerShape(
                        if (compact) 24.dp else 30.dp
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color(0xFF0E1519)
                    )
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    when {
                                        compact -> 16.dp
                                        tablet -> 30.dp
                                        else -> 28.dp
                                    },
                                vertical =
                                    when {
                                        compact -> 18.dp
                                        tablet -> 32.dp
                                        else -> 30.dp
                                    }
                            ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            when {
                                compact -> 12.dp
                                tablet -> 22.dp
                                else -> 20.dp
                            }
                        )
                ) {
                    Text(
                        text = "Nasıl oluşturmak istersin?",
                        fontSize =
                            when {
                                compact -> 23.sp
                                tablet -> 30.sp
                                else -> 28.sp
                            },
                        lineHeight =
                            when {
                                compact -> 27.sp
                                tablet -> 35.sp
                                else -> 33.sp
                            },
                        fontWeight = FontWeight.Bold
                    )

                    CreateModeCard(
                        icon = "⚡",
                        title = "Hızlı Oluştur",
                        description =
                            "Sadece isim, içerik ve ikon. Gerisini AppForge otomatik ayarlar.",
                        onClick = onQuick
                    )

                    CreateModeCard(
                        icon = "☷",
                        title = "Gelişmiş Oluştur",
                        description =
                            "Paket adı, tema, izinler, Native Bridge, Billing ve imzalama üzerinde tam kontrol.",
                        onClick = onAdvanced
                    )

                    CreateModeCard(
                        icon = "🔄",
                        title = "Dönüşüm",
                        description =
                            "APK → Windows EXE veya EXE → Android APK dönüşüm araçları.",
                        onClick = onConversion
                    )

                    Text(
                        text =
                            "v2.0 • Hızlı modda güvenli varsayılanlar kullanılır.",
                        fontSize =
                            if (compact) 11.sp else 12.sp,
                        lineHeight =
                            if (compact) 15.sp else 17.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionScreen(
    selectedApkName: String,
    selectedExeName: String,
    status: String,
    onBack: () -> Unit,
    onApkToExe: () -> Unit,
    onExeToApk: () -> Unit
) {
    val configuration =
        LocalConfiguration.current

    val screenWidthDp =
        configuration.screenWidthDp

    val screenHeightDp =
        configuration.screenHeightDp

    val compact =
        screenWidthDp < 380

    val tablet =
        minOf(
            screenWidthDp,
            screenHeightDp
        ) >= 600

    val horizontalPadding =
        when {
            compact -> 12.dp
            tablet -> 32.dp
            else -> 20.dp
        }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Dönüşüm",
                            fontWeight = FontWeight.Bold,
                            fontSize =
                                if (compact) 18.sp else 20.sp
                        )

                        if (!compact) {
                            Text(
                                "APK ↔ EXE araçları",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            if (compact) "←" else "← Geri"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding =
                PaddingValues(
                    horizontal = horizontalPadding,
                    vertical =
                        if (compact) 12.dp else 20.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    if (compact) 10.dp else 16.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            if (compact) 10.dp else 16.dp
                        )
                ) {
                    Text(
                        "Ne dönüştürmek istersin?",
                        fontSize =
                            when {
                                compact -> 22.sp
                                tablet -> 28.sp
                                else -> 26.sp
                            },
                        lineHeight =
                            when {
                                compact -> 26.sp
                                tablet -> 33.sp
                                else -> 31.sp
                            },
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "AppForge tarafından oluşturulan veya dönüştürme yetkiniz bulunan uygulamalar için.",
                        color = TextSecondary,
                        fontSize =
                            if (compact) 12.sp else 14.sp,
                        lineHeight =
                            if (compact) 17.sp else 20.sp
                    )

                    if (status.isNotBlank()) {
                        NoteCard(status)
                    }

                    CreateModeCard(
                        icon = "📱",
                        title = "APK → Windows EXE",
                        description =
                            "Android APK içindeki AppForge proje verisini kullanarak Windows portable EXE oluştur.",
                        onClick = onApkToExe
                    )

                    if (selectedApkName.isNotBlank()) {
                        Text(
                            "Seçilen APK: $selectedApkName",
                            color = Accent,
                            fontSize =
                                if (compact) 12.sp else 13.sp
                        )
                    }

                    CreateModeCard(
                        icon = "🖥️",
                        title = "EXE → Android APK",
                        description =
                            "AppForge Windows EXE içindeki proje verisini kullanarak Android APK oluştur.",
                        onClick = onExeToApk
                    )

                    if (selectedExeName.isNotBlank()) {
                        Text(
                            "Seçilen EXE: $selectedExeName",
                            color = Accent,
                            fontSize =
                                if (compact) 12.sp else 13.sp
                        )
                    }

                    NoteCard(
                        "İlk sürüm AppForge proje manifesti bulunan çıktıları destekleyecek."
                    )
                }
            }
        }
    }
}


@Composable
private fun CreateModeCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val compact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                if (compact) 18.dp else 24.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF171E22)
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            if (compact) 14.dp else 22.dp,
                        vertical =
                            if (compact) 16.dp else 24.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize =
                    if (compact) 28.sp else 36.sp,
                modifier =
                    Modifier.width(
                        if (compact) 48.dp else 70.dp
                    )
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        if (compact) 4.dp else 6.dp
                    )
            ) {
                Text(
                    text = title,
                    fontSize =
                        if (compact) 17.sp else 21.sp,
                    lineHeight =
                        if (compact) 21.sp else 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = description,
                    fontSize =
                        if (compact) 12.sp else 15.sp,
                    color = TextSecondary,
                    lineHeight =
                        if (compact) 17.sp else 21.sp
                )
            }

            Spacer(
                Modifier.width(
                    if (compact) 6.dp else 10.dp
                )
            )

            Text(
                text = "⋮",
                fontSize =
                    if (compact) 22.sp else 28.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun QuickCreateScreen(
    draft: ProjectDraft,
    status: String,
    onDraftChange: (ProjectDraft) -> Unit,
    onBack: () -> Unit,
    onPickSource: () -> Unit,
    onPickIcon: () -> Unit,
    onPickFirebase: () -> Unit,
    onAdvanced: () -> Unit,
    onPreview: () -> Unit,
    onBuild: () -> Unit
) {
    val quickConfiguration =
        LocalConfiguration.current

    val quickScreenWidthDp =
        quickConfiguration.screenWidthDp

    val quickScreenHeightDp =
        quickConfiguration.screenHeightDp

    val quickCompact =
        quickScreenWidthDp < 380

    val quickTablet =
        minOf(
            quickScreenWidthDp,
            quickScreenHeightDp
        ) >= 600

    val quickWide =
        quickScreenWidthDp >= 600

    val quickContentMaxWidth =
        if (quickWide) 840.dp else 10000.dp

    val quickHorizontalPadding =
        when {
            quickCompact -> 12.dp
            quickTablet -> 28.dp
            else -> 20.dp
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Hızlı Oluştur",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            if (quickCompact) 17.sp else 20.sp
                    )

                    if (!quickCompact) {
                        Text(
                            "İsim + içerik + ikon",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text("←")
                }
            },
            actions = {
                TextButton(
                    onClick =
                        onAdvanced
                ) {
                    Text(
                        if (quickCompact) {
                            "Ayarlar"
                        } else {
                            "Gelişmiş"
                        },
                        fontSize =
                            if (quickCompact) 12.sp else 14.sp
                    )
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .align(
                        Alignment.CenterHorizontally
                    )
                    .widthIn(max = quickContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal =
                        quickHorizontalPadding,
                    vertical =
                        if (quickCompact) 12.dp else 20.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    if (quickCompact) 10.dp else 16.dp
                )
        ) {
            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (quickCompact) 14.dp else 18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (quickCompact) 8.dp else 12.dp)
                    ) {
                        Text(
                            "2. İçerik",
                            fontWeight =
                                FontWeight.Bold
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
                                    if (quickCompact) 6.dp else 8.dp
                                )
                        ) {
                            FilterChip(
                                selected =
                                    draft.sourceMode ==
                                    SourceMode.LOCAL,
                                onClick = {
                                    onDraftChange(
                                        draft.copy(
                                            sourceMode =
                                                SourceMode.LOCAL
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "HTML / ZIP"
                                    )
                                }
                            )

                            FilterChip(
                                selected =
                                    draft.sourceMode ==
                                    SourceMode.URL,
                                onClick = {
                                    onDraftChange(
                                        draft.copy(
                                            sourceMode =
                                                SourceMode.URL,
                                            javascriptBridge =
                                                false,
                                            remoteBridgeAllowed =
                                                false
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "Web sitesi"
                                    )
                                }
                            )
                        }

                        if (
                            draft.sourceMode ==
                            SourceMode.LOCAL
                        ) {
                            OutlinedButton(
                                onClick =
                                    onPickSource,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(if (quickCompact) 48.dp else 52.dp)
                            ) {
                                Text(
                                    if (
                                        draft.startPage
                                            .isNullOrBlank()
                                    ) {
                                        "HTML / ZIP seç"
                                    } else {
                                        "✓ ${
                                            draft.sourceLabel
                                                .ifBlank {
                                                    "Kaynak seçildi"
                                                }
                                        }"
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onDraftChange(
                                        draft.copy(
                                            autoVersionCode =
                                                !draft.autoVersionCode
                                        )
                                    )
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Otomatik sürüm arttır: ${
                                        if (draft.autoVersionCode) "Açık" else "Kapalı"
                                    }"
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value =
                                    draft.webUrl,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            webUrl =
                                                it
                                        )
                                    )
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                singleLine =
                                    true,
                                placeholder = {
                                    Text(
                                        "https://ornek.com"
                                    )
                                }
                            )

                            Text(
                                text =
                                    "Hızlı mod uzak sitelerde Native Bridge'i güvenlik için kapalı tutar.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (quickCompact) 14.dp else 18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (quickCompact) 8.dp else 12.dp)
                    ) {
                        Text(
                            "1. Uygulama adı",
                            fontWeight =
                                FontWeight.Bold
                        )

                        OutlinedTextField(
                            value =
                                draft.appName,
                            onValueChange = {
                                onDraftChange(
                                    draft.copy(
                                        appName =
                                            it,
                                        packageName =
                                            quickPackageName(
                                                it
                                            )
                                    )
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            singleLine =
                                true,
                            placeholder = {
                                Text(
                                    "Örn. Benim Uygulamam"
                                )
                            }
                        )

                        if (
                            draft.appName
                                .isNotBlank()
                        ) {
                            Text(
                                text =
                                    "Paket adı otomatik: ${
                                        quickPackageName(
                                            draft.appName
                                        )
                                    }",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }
                    }
                }
            }

                item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (quickCompact) 14.dp else 18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (quickCompact) 8.dp else 12.dp)
                    ) {
                        Text(
                            "3. İkon",
                            fontWeight =
                                FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick =
                                onPickIcon,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (quickCompact) 48.dp else 52.dp)
                        ) {
                            Text(
                                if (
                                    draft.iconUri
                                        .isNullOrBlank()
                                ) {
                                    "PNG ikon seç"
                                } else {
                                    "✓ ${
                                        draft.iconName
                                            .ifBlank {
                                                "İkon seçildi"
                                            }
                                    }"
                                }
                            )
                        }

                        Text(
                            text =
                                "İkon seçmezsen AppForge varsayılan ikon kullanır.",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )
                    }
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Color(
                                        0xFF111B28
                                    )
                            ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (quickCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    6.dp
                                )
                    ) {
                        Text(
                            "AppForge otomatik ayarlayacak",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "• Paket adı\n• SDK / WebView ayarları\n• Güvenli izinler\n• Splash ekranı\n• Debug imzalama\n• APK + AAB çıktısı",
                            color =
                                TextSecondary,
                            lineHeight =
                                21.sp
                        )
                    }
                }
            }

            item {
                Text(
                    text =
                        status,
                    color =
                        if (
                            status.startsWith(
                                "Hata"
                            )
                        ) {
                            MaterialTheme
                                .colorScheme
                                .error
                        } else {
                            TextSecondary
                        },
                    fontSize =
                        13.sp
                )
            }

            item {
                Column(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalArrangement =
                        Arrangement.spacedBy(if (quickCompact) 8.dp else 12.dp)
                ) {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (quickCompact) 14.dp else 18.dp),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                "📷 Kamera ile fotoğraf",
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Spacer(
                                Modifier.height(4.dp)
                            )

                            Text(
                                "HTML kamera/capture alanlarının telefon kamerasını açmasına izin verir.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }

                        Spacer(
                            Modifier.width(12.dp)
                        )

                        Switch(
                            checked =
                                draft.camera,
                            onCheckedChange = {
                                enabled ->

                                onDraftChange(
                                    draft.copy(
                                        camera =
                                            enabled
                                    )
                                )
                            }
                        )
                    }
                }

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (quickCompact) 14.dp else 18.dp),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                "📍 Konum / Geolocation",
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Spacer(
                                Modifier.height(4.dp)
                            )

                            Text(
                                "Web uygulamasının cihaz konumuna erişmesine izin verir.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }

                        Spacer(
                            Modifier.width(12.dp)
                        )

                        Switch(
                            checked =
                                draft.location,
                            onCheckedChange = {
                                enabled ->

                                onDraftChange(
                                    draft.copy(
                                        location =
                                            enabled
                                    )
                                )
                            }
                        )
                    }
                }

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (quickCompact) 14.dp else 18.dp),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                "🔗 Deep Link",
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Spacer(
                                Modifier.height(4.dp)
                            )

                            Text(
                                if (
                                    draft.deepLinkEnabled
                                ) {
                                    "appforge://${draft.packageName.lowercase()}/"
                                } else {
                                    "Bağlantıdan uygulamayı doğrudan aç."
                                },
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }

                        Spacer(
                            Modifier.width(12.dp)
                        )

                        Switch(
                            checked =
                                draft.deepLinkEnabled,
                            onCheckedChange = {
                                enabled ->

                                onDraftChange(
                                    draft.copy(
                                        deepLinkEnabled =
                                            enabled,
                                        deepLinkScheme =
                                            if (enabled) {
                                                "appforge"
                                            } else {
                                                draft.deepLinkScheme
                                            },
                                        deepLinkHost =
                                            if (enabled) {
                                                draft.packageName
                                                    .lowercase()
                                            } else {
                                                draft.deepLinkHost
                                            },
                                        deepLinkPathPrefix =
                                            "/"
                                    )
                                )
                            }
                        )
                    }
                }

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (quickCompact) 14.dp else 18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (quickCompact) 8.dp else 12.dp)
                    ) {

                        Text(
                            "🚀 FAST Extended",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                18.sp
                        )

                        Text(
                            "Native Bridge, QR, reklam, satın alma ve Firebase özellikleri.",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )

                        // ==========================================
                        // NATIVE BRIDGE
                        // ==========================================

                        Toggle(
                            "🌉 Native Bridge",
                            draft.javascriptBridge
                        ) { enabled ->

                            onDraftChange(
                                draft.copy(
                                    javascriptBridge =
                                        enabled,

                                    shareBridge =
                                        enabled,

                                    clipboardBridge =
                                        enabled,

                                    vibrationBridge =
                                        enabled,

                                    /*
                                     * Medya oynatıcı ağır/native bir özelliktir.
                                     * Ana Bridge açıldığında otomatik açılmaz.
                                     */
                                    mediaPlayerBridge =
                                        draft.mediaPlayerBridge,

                                    qrScanner =
                                        enabled
                                )
                            )
                        }

                        if (
                            draft.javascriptBridge
                        ) {

                            Toggle(
                                "📤 Paylaşım",
                                draft.shareBridge
                            ) {
                                onDraftChange(
                                    draft.copy(
                                        shareBridge =
                                            it
                                    )
                                )
                            }

                            Toggle(
                                "📋 Panoya kopyalama",
                                draft.clipboardBridge
                            ) {
                                onDraftChange(
                                    draft.copy(
                                        clipboardBridge =
                                            it
                                    )
                                )
                            }

                            Toggle(
                                "📳 Titreşim / Haptic",
                                draft.vibrationBridge
                            ) {
                                onDraftChange(
                                    draft.copy(
                                        vibrationBridge =
                                            it
                                    )
                                )
                            }

                            Toggle(
                                "🎵 Medya Oynatıcı / Arka Plan Ses",
                                draft.mediaPlayerBridge
                            ) {
                                onDraftChange(
                                    draft.copy(
                                        mediaPlayerBridge =
                                            it
                                    )
                                )
                            }

                            Toggle(
                                "📷 QR / Barkod Tarayıcı",
                                draft.qrScanner
                            ) {
                                onDraftChange(
                                    draft.copy(
                                        qrScanner =
                                            it
                                    )
                                )
                            }

                            if (
                                draft.sourceMode ==
                                    SourceMode.URL
                            ) {
                                Toggle(
                                    "🌐 Uzak site Native Bridge",
                                    draft.remoteBridgeAllowed
                                ) {
                                    onDraftChange(
                                        draft.copy(
                                            remoteBridgeAllowed =
                                                it
                                        )
                                    )
                                }

                                Text(
                                    "Yalnız güvendiğiniz HTTPS sitelerinde açın.",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        11.sp
                                )
                            }
                        }


                        Spacer(
                            Modifier.height(4.dp)
                        )


                        // ==========================================
                        // ADMOB
                        // ==========================================

                        Toggle(
                            "💰 AdMob",
                            draft.admobEnabled
                        ) {
                            onDraftChange(
                                draft.copy(
                                    admobEnabled =
                                        it,

                                    umpConsentEnabled =
                                        if (it) {
                                            draft.umpConsentEnabled
                                        } else {
                                            false
                                        }
                                )
                            )
                        }

                        if (
                            draft.admobEnabled
                        ) {

                            OutlinedTextField(
                                value =
                                    draft.admobAppId,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            admobAppId =
                                                it.trim()
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "AdMob App ID"
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "ca-app-pub-...~..."
                                    )
                                },
                                singleLine =
                                    true,
                                modifier =
                                    Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value =
                                    draft.admobBannerUnitId,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            admobBannerUnitId =
                                                it.trim()
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "Banner Unit ID"
                                    )
                                },
                                singleLine =
                                    true,
                                modifier =
                                    Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value =
                                    draft.admobInterstitialUnitId,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            admobInterstitialUnitId =
                                                it.trim()
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "Interstitial Unit ID"
                                    )
                                },
                                singleLine =
                                    true,
                                modifier =
                                    Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value =
                                    draft.admobRewardedUnitId,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            admobRewardedUnitId =
                                                it.trim()
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "Rewarded Unit ID"
                                    )
                                },
                                singleLine =
                                    true,
                                modifier =
                                    Modifier.fillMaxWidth()
                            )

                            Toggle(
                                "🛡️ UMP Consent",
                                draft.umpConsentEnabled
                            ) {
                                onDraftChange(
                                    draft.copy(
                                        umpConsentEnabled =
                                            it
                                    )
                                )
                            }
                        }


                        Spacer(
                            Modifier.height(4.dp)
                        )


                        // ==========================================
                        // BILLING
                        // ==========================================

                        Toggle(
                            "🛒 Google Play Billing",
                            draft.billingEnabled
                        ) {
                            onDraftChange(
                                draft.copy(
                                    billingEnabled =
                                        it
                                )
                            )
                        }

                        if (
                            draft.billingEnabled
                        ) {

                            OutlinedTextField(
                                value =
                                    draft.billingProductIds,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            billingProductIds =
                                                it
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "Ürün ID'leri"
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "premium,coins100"
                                    )
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value =
                                    draft.billingSubscriptionIds,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            billingSubscriptionIds =
                                                it
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "Abonelik ID'leri"
                                    )
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value =
                                    draft.removeAdsProductId,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            removeAdsProductId =
                                                it.trim()
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "Reklam kaldırma ürün ID"
                                    )
                                },
                                singleLine =
                                    true,
                                modifier =
                                    Modifier.fillMaxWidth()
                            )
                        }


                        Spacer(
                            Modifier.height(4.dp)
                        )


                        // ==========================================
                        // FIREBASE
                        // ==========================================

                        Toggle(
                            "📊 Firebase Analytics",
                            draft.firebaseAnalyticsEnabled
                        ) {
                            onDraftChange(
                                draft.copy(
                                    firebaseAnalyticsEnabled =
                                        it
                                )
                            )
                        }

                        Toggle(
                            "💥 Firebase Crashlytics",
                            draft.firebaseCrashlyticsEnabled
                        ) {
                            onDraftChange(
                                draft.copy(
                                    firebaseCrashlyticsEnabled =
                                        it
                                )
                            )
                        }

                        Toggle(
                            "🔔 Firebase Cloud Messaging",
                            draft.firebaseMessagingEnabled
                        ) {
                            onDraftChange(
                                draft.copy(
                                    firebaseMessagingEnabled =
                                        it,
                                    notifications =
                                        if (it) {
                                            true
                                        } else {
                                            draft.notifications
                                        }
                                )
                            )
                        }

                        if (
                            draft.firebaseAnalyticsEnabled || draft.firebaseCrashlyticsEnabled || draft.firebaseMessagingEnabled
                        ) {

                            Button(
                                onClick =
                                    onPickFirebase,
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (
                                        draft.firebaseConfigName
                                            .isBlank()
                                    ) {
                                        "google-services.json SEÇ"
                                    } else {
                                        "✅ ${draft.firebaseConfigName}"
                                    }
                                )
                            }
                        }
                    }
                }
                }
            }
        }

        Button(
            onClick =
                onBuild,
            enabled =
                draft.appName
                    .isNotBlank() &&
                (
                    (
                        draft.sourceMode ==
                        SourceMode.LOCAL &&
                        !draft.startPage
                            .isNullOrBlank()
                    ) ||
                    (
                        draft.sourceMode ==
                        SourceMode.URL &&
                        draft.webUrl
                            .startsWith(
                                "https://",
                                true
                            )
                    )
                ),
            modifier =
                Modifier
                    .align(
                        Alignment.CenterHorizontally
                    )
                    .widthIn(max = quickContentMaxWidth)
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            quickHorizontalPadding,
                        vertical =
                            if (quickCompact) 10.dp else 16.dp
                    )
                    .height(
                        if (quickCompact) 52.dp else 58.dp
                    )
        ) {
            Text(
                "⚡ HIZLI OLUŞTUR",
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}


private data class PreviewPreset(
    val key: String,
    val label: String,
    val width: Int,
    val height: Int
)

private val previewPresets =
    listOf(
        PreviewPreset(
            "phone",
            "Telefon",
            360,
            800
        ),
        PreviewPreset(
            "large_phone",
            "Büyük Telefon",
            412,
            915
        ),
        PreviewPreset(
            "tablet",
            "Tablet",
            800,
            1280
        )
    )

private enum class CheckLevel {
    PASS,
    WARN,
    BLOCK
}

private data class ProductionCheck(
    val title: String,
    val detail: String,
    val level: CheckLevel
)

private fun productionChecks(
    draft: ProjectDraft
): List<ProductionCheck> {
    val checks =
        mutableListOf<
            ProductionCheck
        >()

    checks +=
        ProductionCheck(
            "Uygulama adı",
            if (
                draft.appName
                    .isBlank()
            ) {
                "Uygulama adı eksik."
            } else {
                draft.appName
            },
            if (
                draft.appName
                    .isBlank()
            ) {
                CheckLevel.BLOCK
            } else {
                CheckLevel.PASS
            }
        )

    val packageOk =
        Regex(
            "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"
        ).matches(
            draft.packageName
        )

    checks +=
        ProductionCheck(
            "Package name",
            draft.packageName,
            if (
                packageOk
            ) {
                CheckLevel.PASS
            } else {
                CheckLevel.BLOCK
            }
        )

    val sourceReady =
        if (
            draft.sourceMode ==
            SourceMode.LOCAL
        ) {
            !draft.startPage
                .isNullOrBlank() &&
                File(
                    draft.startPage!!
                ).exists()
        } else {
            draft.webUrl
                .startsWith(
                    "https://",
                    true
                )
        }

    checks +=
        ProductionCheck(
            "Kaynak",
            if (
                sourceReady
            ) {
                if (
                    draft.sourceMode ==
                    SourceMode.LOCAL
                ) {
                    "Yerel HTML/ZIP hazır"
                } else {
                    "HTTPS URL hazır"
                }
            } else {
                "Kaynak eksik veya geçersiz."
            },
            if (
                sourceReady
            ) {
                CheckLevel.PASS
            } else {
                CheckLevel.BLOCK
            }
        )

    checks +=
        ProductionCheck(
            "Sürüm",
            "${draft.versionName} • versionCode ${draft.versionCode}",
            if (
                draft.versionCode >
                0 &&
                draft.versionName
                    .isNotBlank()
            ) {
                CheckLevel.PASS
            } else {
                CheckLevel.BLOCK
            }
        )

    checks +=
        ProductionCheck(
            "Uygulama ikonu",
            if (
                draft.iconUri
                    .isNullOrBlank()
            ) {
                "Özel ikon seçilmedi; AppForge varsayılan ikon kullanacak."
            } else {
                draft.iconName
                    .ifBlank {
                        "Özel ikon hazır"
                    }
            },
            if (
                draft.iconUri
                    .isNullOrBlank()
            ) {
                CheckLevel.WARN
            } else {
                CheckLevel.PASS
            }
        )

    checks +=
        ProductionCheck(
            "Play Store imzalama",
            if (
                draft.signingMode ==
                SigningMode.CUSTOM
            ) {
                "Release keystore seçili."
            } else {
                "Debug signing seçili. Play Store üretim sürümü için release keystore gerekir."
            },
            if (
                draft.signingMode ==
                SigningMode.CUSTOM
            ) {
                CheckLevel.PASS
            } else {
                CheckLevel.WARN
            }
        )

    if (
        draft.admobEnabled ||
        draft.billingEnabled ||
        draft.location ||
        draft.camera ||
        draft.microphone ||
        draft.nfc
    ) {
        checks +=
            ProductionCheck(
                "Gizlilik / Data safety",
                "Google Play Console'da gizlilik politikası ve Data safety beyanını ayrıca tamamla.",
                CheckLevel.WARN
            )
    }

    if (
        draft.remoteBridgeAllowed
    ) {
        checks +=
            ProductionCheck(
                "Remote Native Bridge",
                "Uzak içerikte Native Bridge açık. Yalnız güvenilir HTTPS origin kullan.",
                CheckLevel.WARN
            )
    }

    if (draft.webMixedContentAllowed) {
        checks +=
            ProductionCheck(
                "Mixed content",
                "HTTPS sayfa içinde güvensiz HTTP içeriğe izin veriliyor. Üretimde kapatılması önerilir.",
                CheckLevel.WARN
            )
    }

    val firebaseEnabled =
        draft.firebaseAnalyticsEnabled ||
            draft.firebaseCrashlyticsEnabled ||
            draft.firebaseMessagingEnabled

    if (firebaseEnabled) {
        checks +=
            ProductionCheck(
                "Firebase yapılandırması",
                if (draft.firebaseConfigUri.isNullOrBlank()) {
                    "Firebase özelliği açık ancak google-services.json seçilmedi."
                } else {
                    draft.firebaseConfigName.ifBlank { "google-services.json hazır" }
                },
                if (draft.firebaseConfigUri.isNullOrBlank()) CheckLevel.BLOCK else CheckLevel.PASS
            )
    }

    if ("BACKGROUND_LOCATION" in draft.additionalPermissions && !draft.location) {
        checks +=
            ProductionCheck(
                "Arka plan konumu",
                "Arka plan konumu seçili fakat temel konum izni kapalı.",
                CheckLevel.BLOCK
            )
    }

    return checks
}

private fun bumpPatchVersion(
    versionName: String
): String {
    val parts =
        versionName
            .split(".")
            .map {
                it.toIntOrNull()
            }

    return if (
        parts.size >=
        3 &&
        parts[0] != null &&
        parts[1] != null &&
        parts[2] != null
    ) {
        "${parts[0]}.${parts[1]}.${parts[2]!! + 1}"
    } else {
        "1.0.1"
    }
}

private enum class PreviewInspectorTab {
    PREVIEW,
    CONSOLE,
    NETWORK,
    PERFORMANCE,
    SECURITY
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AppPreviewScreen(
    draft: ProjectDraft,
    languageCode: String,
    onBack: () -> Unit,
    onOpenProduction: () -> Unit
) {
    val previewConfiguration =
        LocalConfiguration.current

    val previewScreenWidthDp =
        previewConfiguration.screenWidthDp

    val previewScreenHeightDp =
        previewConfiguration.screenHeightDp

    val previewCompact =
        previewScreenWidthDp < 380

    val previewTablet =
        minOf(
            previewScreenWidthDp,
            previewScreenHeightDp
        ) >= 600

    val previewWide =
        previewScreenWidthDp >= 600

    val previewContentMaxWidth =
        if (previewWide) 980.dp else 10000.dp

    val previewHorizontalPadding =
        when {
            previewCompact -> 10.dp
            previewTablet -> 28.dp
            else -> 14.dp
        }

    var preset by
        remember {
            mutableStateOf(
                previewPresets[0]
            )
        }

    var landscape by
        remember {
            mutableStateOf(
                false
            )
        }

    var reloadKey by
        remember {
            mutableIntStateOf(
                0
            )
        }

    var selectedTab by
        remember {
            mutableStateOf(
                PreviewInspectorTab.PREVIEW
            )
        }

    var webViewRef by
        remember {
            mutableStateOf<
                WebView?
            >(
                null
            )
        }

    val consoleEvents =
        remember {
            mutableStateListOf<
                String
            >()
        }

    val networkEvents =
        remember {
            mutableStateListOf<
                String
            >()
        }

    var performanceJson by
        remember {
            mutableStateOf(
                "Henüz performans verisi yok."
            )
        }

    val sourceUrl =
        when (
            draft.sourceMode
        ) {
            SourceMode.LOCAL ->
                draft.startPage
                    ?.takeIf {
                        val file = File(it)
                        file.exists() &&
                            file.isFile &&
                            file.extension.lowercase() in
                                setOf("html", "htm")
                    }
                    ?.let {
                        Uri.fromFile(
                            File(it)
                        ).toString()
                    }

            SourceMode.URL ->
                draft.webUrl
                    .takeIf {
                        it.startsWith(
                            "https://",
                            true
                        )
                    }
        }

    val expectedHost =
        if (
            draft.sourceMode ==
            SourceMode.URL
        ) {
            runCatching {
                Uri.parse(
                    draft.webUrl
                ).host
            }.getOrNull()
        } else {
            null
        }

    val previewSecurity =
        remember(
            draft
        ) {
            productionChecks(
                draft
            )
        }

    DisposableEffect(
        Unit
    ) {
        onDispose {
            webViewRef
                ?.stopLoading()

            webViewRef
                ?.destroy()

            webViewRef =
                null
        }
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        t(
                            languageCode,
                            "preview"
                        ),
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (!previewCompact) {
                        Text(
                            "Preview + Console + Network + Performance + Security",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text("←")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        reloadKey++
                        consoleEvents.clear()
                        networkEvents.clear()
                        performanceJson =
                            "Yeniden yükleniyor..."
                        webViewRef
                            ?.reload()
                    }
                ) {
                    Text("↻")
                }

                TextButton(
                    onClick =
                        onOpenProduction
                ) {
                    Text(
                        if (previewCompact) {
                            "✓"
                        } else {
                            "Check"
                        },
                        fontSize =
                            if (previewCompact) 12.sp else 14.sp
                    )
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(
                        Alignment.CenterHorizontally
                    )
                    .widthIn(
                        max = previewContentMaxWidth
                    )
                    .fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal =
                        previewHorizontalPadding,
                    vertical =
                        if (previewCompact) 10.dp else 14.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    if (previewCompact) 8.dp else 12.dp
                )
        ) {
            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(if (previewCompact) 10.dp else 14.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (previewCompact) 7.dp else 10.dp)
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(
                                        rememberScrollState()
                                    ),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    if (previewCompact) 4.dp else 6.dp
                                )
                        ) {
                            PreviewInspectorTab
                                .entries
                                .forEach {
                                    tab ->
                                    FilterChip(
                                        selected =
                                            selectedTab ==
                                            tab,
                                        onClick = {
                                            selectedTab =
                                                tab
                                        },
                                        label = {
                                            Text(
                                                when (
                                                    tab
                                                ) {
                                                    PreviewInspectorTab.PREVIEW ->
                                                        "Önizleme"

                                                    PreviewInspectorTab.CONSOLE ->
                                                        "Konsol"

                                                    PreviewInspectorTab.NETWORK ->
                                                        "Ağ"

                                                    PreviewInspectorTab.PERFORMANCE ->
                                                        "Performans"

                                                    PreviewInspectorTab.SECURITY ->
                                                        "Güvenlik"
                                                }
                                            )
                                        }
                                    )
                                }
                        }

                        if (
                            selectedTab ==
                            PreviewInspectorTab.PREVIEW
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        if (previewCompact) 5.dp else 8.dp
                                    )
                            ) {
                                previewPresets
                                    .forEach {
                                        item ->
                                        FilterChip(
                                            selected =
                                                preset.key ==
                                                item.key,
                                            onClick = {
                                                preset =
                                                    item
                                            },
                                            label = {
                                                Text(
                                                    item.label
                                                )
                                            }
                                        )
                                    }
                            }

                            Row(
                                verticalAlignment =
                                    Alignment
                                        .CenterVertically
                            ) {
                                Text(
                                    "Yatay görünüm",
                                    modifier =
                                        Modifier
                                            .weight(
                                                1f
                                            )
                                )

                                Switch(
                                    checked =
                                        landscape,
                                    onCheckedChange = {
                                        landscape =
                                            it
                                    }
                                )
                            }

                            Text(
                                if (
                                    landscape
                                ) {
                                    "${preset.height} × ${preset.width}"
                                } else {
                                    "${preset.width} × ${preset.height}"
                                },
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }
                    }
                }
            }

            if (
                selectedTab ==
                PreviewInspectorTab.PREVIEW
            ) {
                item {
                    if (
                        sourceUrl ==
                        null
                    ) {
                        NoteCard(
                            if (
                                draft.sourceMode == SourceMode.LOCAL &&
                                !draft.startPage.isNullOrBlank()
                            ) {
                                "Bu proje ${draft.sourceTechnologyLabel} olarak algılandı. Native Android/Kotlin ve diğer native kaynaklar WebView ile önizlenmez. Üretim Merkezi ve Test Laboratuvarı'nı kullan."
                            } else {
                                "Önizlenecek web kaynağı hazır değil. HTML/HTM seç veya URL modunda HTTPS adresi gir."
                            }
                        )
                    } else {
                        val ratio =
                            if (
                                landscape
                            ) {
                                preset.height
                                    .toFloat() /
                                preset.width
                                    .toFloat()
                            } else {
                                preset.width
                                    .toFloat() /
                                preset.height
                                    .toFloat()
                            }

                        Card(
                            colors =
                                CardDefaults
                                    .cardColors(
                                        containerColor =
                                            Color.Black
                                    ),
                            shape =
                                RoundedCornerShape(if (previewCompact) 20.dp else 28.dp),
                            border =
                                androidx.compose
                                    .foundation
                                    .BorderStroke(
                                        2.dp,
                                        Color(
                                            0xFF39485E
                                        )
                                    ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(
                                        ratio
                                    )
                        ) {
                            key(
                                sourceUrl,
                                reloadKey,
                                preset.key,
                                landscape
                            ) {
                                AndroidView(
                                    modifier =
                                        Modifier
                                            .fillMaxSize(),
                                    factory = {
                                        ctx ->
                                        WebView(
                                            ctx
                                        ).apply {
                                            webViewRef =
                                                this

                                            settings
                                                .javaScriptEnabled =
                                                true

                                            settings
                                                .domStorageEnabled =
                                                true

                                            settings
                                                .allowContentAccess =
                                                false

                                            settings
                                                .allowFileAccess =
                                                draft.sourceMode ==
                                                SourceMode.LOCAL

                                            settings
                                                .mediaPlaybackRequiresUserGesture =
                                                true

                                            webChromeClient =
                                                object :
                                                    WebChromeClient() {
                                                    override fun onConsoleMessage(
                                                        consoleMessage: ConsoleMessage?
                                                    ): Boolean {
                                                        val msg =
                                                            consoleMessage
                                                                ?: return true

                                                        if (
                                                            consoleEvents.size >=
                                                            500
                                                        ) {
                                                            consoleEvents.removeAt(
                                                                0
                                                            )
                                                        }

                                                        consoleEvents.add(
                                                            "${msg.messageLevel()} • ${msg.sourceId()}:${msg.lineNumber()} • ${msg.message()}"
                                                        )

                                                        return true
                                                    }
                                                }

                                            webViewClient =
                                                object :
                                                    WebViewClient() {
                                                    override fun shouldOverrideUrlLoading(
                                                        view: WebView?,
                                                        request: WebResourceRequest?
                                                    ): Boolean {
                                                        val target =
                                                            request
                                                                ?.url
                                                                ?: return true

                                                        return if (
                                                            draft.sourceMode ==
                                                            SourceMode.LOCAL
                                                        ) {
                                                            target.scheme !=
                                                                "file"
                                                        } else {
                                                            target.scheme !=
                                                                "https" ||
                                                            target.host !=
                                                                expectedHost
                                                        }
                                                    }

                                                    override fun shouldInterceptRequest(
                                                        view: WebView?,
                                                        request: WebResourceRequest?
                                                    ): WebResourceResponse? {
                                                        val req =
                                                            request

                                                        if (
                                                            req != null
                                                        ) {
                                                            view?.post {
                                                                if (
                                                                    networkEvents.size >=
                                                                    750
                                                                ) {
                                                                    networkEvents.removeAt(
                                                                        0
                                                                    )
                                                                }

                                                                networkEvents.add(
                                                                    "${req.method} • ${req.url}"
                                                                )
                                                            }
                                                        }

                                                        return super
                                                            .shouldInterceptRequest(
                                                                view,
                                                                request
                                                            )
                                                    }

                                                    override fun onPageFinished(
                                                        view: WebView?,
                                                        url: String?
                                                    ) {
                                                        super.onPageFinished(
                                                            view,
                                                            url
                                                        )

                                                        view?.evaluateJavascript(
                                                            """
                                                            (() => {
                                                              const n = performance.getEntriesByType('navigation')[0];
                                                              const r = performance.getEntriesByType('resource');
                                                              return JSON.stringify({
                                                                url: location.href,
                                                                domContentLoadedMs: n ? Math.round(n.domContentLoadedEventEnd) : null,
                                                                loadMs: n ? Math.round(n.loadEventEnd) : null,
                                                                transferBytes: r.reduce((a,x)=>a+(x.transferSize||0),0),
                                                                decodedBytes: r.reduce((a,x)=>a+(x.decodedBodySize||0),0),
                                                                resources: r.length
                                                              });
                                                            })();
                                                            """.trimIndent()
                                                        ) {
                                                            value ->
                                                            performanceJson =
                                                                value
                                                                    ?: "{}"
                                                        }
                                                    }
                                                }

                                            loadUrl(
                                                sourceUrl
                                            )
                                        }
                                    },
                                    update = {
                                        webView ->
                                        webViewRef =
                                            webView
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    NoteCard(
                        "Önizleme güvenlik amacıyla AppForge Native Bridge'i enjekte etmez. HTML/CSS/JS görünümü ve temel WebView davranışı test edilir."
                    )
                }
            }

            if (
                selectedTab ==
                PreviewInspectorTab.CONSOLE
            ) {
                item {
                    InspectorHeader(
                        "JavaScript Konsolu",
                        "${consoleEvents.size} kayıt",
                        onClear = {
                            consoleEvents.clear()
                        }
                    )
                }

                if (
                    consoleEvents.isEmpty()
                ) {
                    item {
                        NoteCard(
                            "Henüz console mesajı yok."
                        )
                    }
                } else {
                    items(
                        consoleEvents
                            .takeLast(
                                200
                            )
                            .reversed()
                    ) {
                        line ->
                        InspectorLine(
                            line
                        )
                    }
                }
            }

            if (
                selectedTab ==
                PreviewInspectorTab.NETWORK
            ) {
                item {
                    InspectorHeader(
                        "Ağ Denetleyicisi",
                        "${networkEvents.size} istek",
                        onClear = {
                            networkEvents.clear()
                        }
                    )
                }

                if (
                    networkEvents.isEmpty()
                ) {
                    item {
                        NoteCard(
                            "Henüz ağ isteği yakalanmadı."
                        )
                    }
                } else {
                    items(
                        networkEvents
                            .takeLast(
                                250
                            )
                            .reversed()
                    ) {
                        line ->
                        InspectorLine(
                            line
                        )
                    }
                }
            }

            if (
                selectedTab ==
                PreviewInspectorTab.PERFORMANCE
            ) {
                item {
                    Section(
                        "Performans Denetleyicisi",
                        "Navigation Timing ve resource boyutlarının canlı özeti."
                    )
                }

                item {
                    NoteCard(
                        performanceJson
                    )
                }

                item {
                    NoteCard(
                        "Bu metrikler editör WebView oturumuna aittir; gerçek cihaz ve release APK performansı farklı olabilir."
                    )
                }
            }

            if (
                selectedTab ==
                PreviewInspectorTab.SECURITY
            ) {
                item {
                    Section(
                        "Güvenlik Merkezi",
                        "Kaynak, izin, imzalama, Billing ve Native Bridge risklerini kontrol eder."
                    )
                }

                items(
                    previewSecurity
                ) {
                    check ->
                    ProductionCheckCard(
                        check
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorHeader(
    title: String,
    detail: String,
    onClear: () -> Unit
) {
    val compact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Card2
                ),
        shape =
            RoundedCornerShape(if (compact) 15.dp else 18.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(if (compact) 10.dp else 14.dp),
            verticalAlignment =
                Alignment
                    .CenterVertically
        ) {
            Column(
                Modifier
                    .weight(
                        1f
                    )
            ) {
                Text(
                    title,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    detail,
                    color =
                        TextSecondary,
                    fontSize =
                        if (compact) 11.sp else 12.sp
                )
            }

            TextButton(
                onClick =
                    onClear
            ) {
                Text(
                    "Temizle"
                )
            }
        }
    }
}

@Composable
private fun InspectorLine(
    text: String
) {
    val compact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Color(
                            0xFF0D1726
                        )
                ),
        shape =
            RoundedCornerShape(
                12.dp
            )
    ) {
        Text(
            text,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 9.dp else 12.dp),
            fontSize =
                if (compact) 10.sp else 11.sp,
            color =
                Color(
                    0xFFC9D5E7
                )
        )
    }
}

@Composable
private fun ProductionCenterScreen(
    draft: ProjectDraft,
    languageCode: String,
    proUnlocked: Boolean,
    onDraftChange: (ProjectDraft) -> Unit,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onTemplates: () -> Unit,
    onTestLab: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val productionConfiguration =
        LocalConfiguration.current

    val productionScreenWidthDp =
        productionConfiguration.screenWidthDp

    val productionScreenHeightDp =
        productionConfiguration.screenHeightDp

    val productionCompact =
        productionScreenWidthDp < 380

    val productionTablet =
        minOf(
            productionScreenWidthDp,
            productionScreenHeightDp
        ) >= 600

    val productionWide =
        productionScreenWidthDp >= 600

    val productionContentMaxWidth =
        if (productionWide) 920.dp else 10000.dp

    val productionHorizontalPadding =
        when {
            productionCompact -> 10.dp
            productionTablet -> 28.dp
            else -> 16.dp
        }

    val context =
        LocalContext.current

    val projects =
        remember {
            ProjectLibrary
                .load(
                    context
                )
        }

    val builds =
        remember {
            ProjectLibrary
                .loadBuilds(
                    context
                )
        }

    val slotsUsed =
        remember {
            ProjectLibrary
                .freeProjectSlotsUsed(
                    context
                )
        }

    val checks =
        productionChecks(
            draft
        )

    val pwa =
        remember(
            draft.importedFolder
        ) {
            PwaInspector
                .inspect(
                    draft.importedFolder
                )
        }

    val blocked =
        checks.count {
            it.level ==
            CheckLevel.BLOCK
        }

    val warnings =
        checks.count {
            it.level ==
            CheckLevel.WARN
        }

    val passed =
        checks.count {
            it.level ==
            CheckLevel.PASS
        }

    val successfulBuilds =
        builds.count {
            it.status ==
            "success"
        }

    Column(
        Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        t(
                            languageCode,
                            "production_center"
                        ),
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (!productionCompact) {
                        Text(
                            "Yayın kontrolü, sürümleme, test ve proje yedekleri",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text("←")
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(
                        Alignment.CenterHorizontally
                    )
                    .widthIn(
                        max = productionContentMaxWidth
                    )
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal =
                        productionHorizontalPadding,
                    vertical =
                        if (productionCompact) 10.dp else 16.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    if (productionCompact) 10.dp else 14.dp
                )
        ) {
            item {
                Section(
                    t(
                        languageCode,
                        "dashboard"
                    ),
                    "Proje ve build durumunun kısa özeti."
                )
            }

            item {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(if (productionCompact) 6.dp else 8.dp)
                ) {
                    ProductionStat(
                        label =
                            "Aktif Proje",
                        value =
                            projects.size
                                .toString(),
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    )

                    ProductionStat(
                        label =
                            if (
                                proUnlocked
                            ) {
                                "Proje Hakkı"
                            } else {
                                "Deneme"
                            },
                        value =
                            if (
                                proUnlocked
                            ) {
                                "∞"
                            } else {
                                "$slotsUsed/5"
                            },
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    )

                    ProductionStat(
                        label =
                            "Build",
                        value =
                            builds.size
                                .toString(),
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(if (productionCompact) 6.dp else 8.dp)
                ) {
                    ProductionStat(
                        label =
                            "Başarılı",
                        value =
                            successfulBuilds
                                .toString(),
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    )

                    ProductionStat(
                        label =
                            "PASS",
                        value =
                            passed
                                .toString(),
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    )

                    ProductionStat(
                        label =
                            "Uyarı",
                        value =
                            warnings
                                .toString(),
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    )
                }
            }

            item {
                Section(
                    t(
                        languageCode,
                        "appforge_check"
                    ),
                    if (
                        blocked ==
                        0
                    ) {
                        "Build engeli bulunmadı. $warnings uyarı var."
                    } else {
                        "$blocked kritik eksik var. Build / yayın öncesi düzelt."
                    }
                )
            }

            items(
                checks
            ) {
                check ->
                ProductionCheckCard(
                    check
                )
            }

            if (
                draft.webMixedContentAllowed ||
                draft.remoteBridgeAllowed ||
                draft.versionCode < 1 ||
                draft.versionName.isBlank()
            ) {
                item {
                    OutlinedButton(
                        onClick = {
                            onDraftChange(
                                draft.copy(
                                    webMixedContentAllowed = false,
                                    remoteBridgeAllowed = false,
                                    versionCode = draft.versionCode.coerceAtLeast(1),
                                    versionName = draft.versionName.ifBlank { "1.0.0" }
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🛡 GÜVENLİ DÜZELTMELERİ UYGULA")
                    }
                }
            }

            item {
                Section(
                    "Test Lab + Artifact Inspector",
                    "Son build'in APK/AAB boyutunu, güvenlik kontrollerini ve sürüm farklarını analiz et."
                )
            }

            item {
                Button(
                    onClick =
                        onTestLab,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(if (productionCompact) 50.dp else 56.dp)
                ) {
                    Text(
                        "🧪 TEST LAB'I AÇ",
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            item {
                Section(
                    "PWA Inspector",
                    "manifest.webmanifest / manifest.json ve service worker öğelerini algılar."
                )
            }

            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(if (productionCompact) 17.dp else 20.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(if (productionCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (productionCompact) 5.dp else 7.dp)
                    ) {
                        Text(
                            if (
                                pwa.detected
                            ) {
                                "✓ PWA yapısı algılandı"
                            } else {
                                "PWA manifest / service worker algılanmadı"
                            },
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                if (
                                    pwa.detected
                                ) {
                                    Color(
                                        0xFF65E3A1
                                    )
                                } else {
                                    TextSecondary
                                }
                        )

                        if (
                            pwa.detected
                        ) {
                            InfoLine(
                                "Manifest",
                                pwa.manifestPath
                                    ?: "-"
                            )

                            InfoLine(
                                "Ad",
                                pwa.appName
                                    ?: "-"
                            )

                            InfoLine(
                                "start_url",
                                pwa.startUrl
                                    ?: "-"
                            )

                            InfoLine(
                                "Display",
                                pwa.display
                                    ?: "-"
                            )

                            InfoLine(
                                "İkon",
                                pwa.iconCount
                                    .toString()
                            )

                            InfoLine(
                                "SW",
                                pwa.serviceWorkerFiles
                                    .size
                                    .toString()
                            )
                        }
                    }
                }
            }

            item {
                Section(
                    "Native Module Center",
                    "Kamera, konum, QR ve Native Bridge modüllerini tek yerde yönet."
                )
            }

            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(if (productionCompact) 17.dp else 20.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(if (productionCompact) 10.dp else 14.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (productionCompact) 5.dp else 6.dp)
                    ) {
                        CompactModuleToggle(
                            "Native Bridge",
                            draft.javascriptBridge
                        ) {
                            onDraftChange(
                                draft.copy(
                                    javascriptBridge =
                                        it
                                )
                            )
                        }

                        CompactModuleToggle(
                            "Kamera",
                            draft.camera
                        ) {
                            onDraftChange(
                                draft.copy(
                                    camera =
                                        it
                                )
                            )
                        }

                        CompactModuleToggle(
                            "Konum",
                            draft.location
                        ) {
                            onDraftChange(
                                draft.copy(
                                    location =
                                        it
                                )
                            )
                        }

                        CompactModuleToggle(
                            "QR / Barcode",
                            draft.qrScanner
                        ) {
                            onDraftChange(
                                draft.copy(
                                    qrScanner =
                                        it
                                )
                            )
                        }

                        CompactModuleToggle(
                            "Paylaşım",
                            draft.shareBridge
                        ) {
                            onDraftChange(
                                draft.copy(
                                    shareBridge =
                                        it
                                )
                            )
                        }

                        CompactModuleToggle(
                            "Pano",
                            draft.clipboardBridge
                        ) {
                            onDraftChange(
                                draft.copy(
                                    clipboardBridge =
                                        it
                                )
                            )
                        }

                        CompactModuleToggle(
                            "Titreşim",
                            draft.vibrationBridge
                        ) {
                            onDraftChange(
                                draft.copy(
                                    vibrationBridge =
                                        it
                                )
                            )
                        }

                        CompactModuleToggle(
                            "Medya / Arka Plan Ses",
                            draft.mediaPlayerBridge
                        ) {
                            onDraftChange(
                                draft.copy(
                                    mediaPlayerBridge =
                                        it
                                )
                            )
                        }
                    }
                }
            }

            item {
                Section(
                    "Sürüm Yöneticisi",
                    "versionCode ve versionName yönetimi."
                )
            }

            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(if (productionCompact) 17.dp else 20.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(if (productionCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (productionCompact) 7.dp else 10.dp)
                    ) {
                        InfoLine(
                            "Sürüm",
                            "${draft.versionName} • ${draft.versionCode}"
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(if (productionCompact) 6.dp else 8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onDraftChange(
                                        draft.copy(
                                            versionCode =
                                                draft.versionCode +
                                                1
                                        )
                                    )
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                            ) {
                                Text(
                                    "versionCode +1"
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onDraftChange(
                                        draft.copy(
                                            versionName =
                                                bumpPatchVersion(
                                                    draft.versionName
                                                )
                                        )
                                    )
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                            ) {
                                Text(
                                    "Patch +1"
                                )
                            }
                        }

                        Row(
                            verticalAlignment =
                                Alignment
                                    .CenterVertically
                        ) {
                            Column(
                                Modifier
                                    .weight(
                                        1f
                                    )
                            ) {
                                Text(
                                    t(
                                        languageCode,
                                        "auto_version"
                                    ),
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    "Açıksa her build başlatıldığında versionCode bir artırılır.",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        12.sp
                                )
                            }

                            Switch(
                                checked =
                                    draft.autoVersionCode,
                                onCheckedChange = {
                                    onDraftChange(
                                        draft.copy(
                                            autoVersionCode =
                                                it
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item {
                Section(
                    "Proje Yedekleme",
                    "AppForge ayarlarını ve yerel HTML kaynağını tek ZIP dosyasında taşı."
                )
            }

            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(if (productionCompact) 17.dp else 20.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(if (productionCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (productionCompact) 7.dp else 10.dp)
                    ) {
                        Button(
                            onClick =
                                onExportBackup,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                        ) {
                            Text(
                                t(
                                    languageCode,
                                    "export_backup"
                                )
                            )
                        }

                        OutlinedButton(
                            onClick =
                                onImportBackup,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                        ) {
                            Text(
                                t(
                                    languageCode,
                                    "import_backup"
                                )
                            )
                        }

                        Text(
                            "Güvenlik için keystore parolaları, hesap erişim bilgileri ve hassas imzalama bilgileri ZIP yedeğine yazılmaz.",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )
                    }
                }
            }

            item {
                Section(
                    "Şablon Merkezi",
                    "Hazır başlangıçları ve Native API şablonlarını kullan."
                )
            }

            item {
                OutlinedButton(
                    onClick =
                        onTemplates,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(if (productionCompact) 48.dp else 52.dp)
                ) {
                    Text(
                        "🧩 Şablon Kataloğunu Aç"
                    )
                }
            }

            item {
                NoteCard(
                    "Production Center yerel hazırlık kontrollerini yapar. Son APK/AAB sonucu AppForge derleme altyapısında doğrulanır."
                )
            }
        }
    }
}

@Composable
private fun ProductionStat(
    label: String,
    value: String,
    modifier: Modifier =
        Modifier
) {
    val compact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Card(
        modifier =
            modifier,
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Card2
                ),
        shape =
            RoundedCornerShape(
                18.dp
            )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    if (compact) 10.dp else 14.dp
                ),
            horizontalAlignment =
                Alignment
                    .CenterHorizontally
        ) {
            Text(
                value,
                fontSize =
                    if (compact) 20.sp else 23.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    Accent
            )

            Text(
                label,
                color =
                    TextSecondary,
                fontSize =
                    if (compact) 10.sp else 11.sp
            )
        }
    }
}

@Composable
private fun ProductionCheckCard(
    check: ProductionCheck
) {
    val compact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val accent =
        when (
            check.level
        ) {
            CheckLevel.PASS ->
                Color(
                    0xFF65E3A1
                )

            CheckLevel.WARN ->
                Color(
                    0xFFFFC857
                )

            CheckLevel.BLOCK ->
                Color(
                    0xFFFF7373
                )
        }

    val symbol =
        when (
            check.level
        ) {
            CheckLevel.PASS ->
                "✓"

            CheckLevel.WARN ->
                "!"

            CheckLevel.BLOCK ->
                "×"
        }

    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Card2
                ),
        shape =
            RoundedCornerShape(
                18.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        if (compact) 10.dp else 14.dp
                    ),
            verticalAlignment =
                Alignment
                    .CenterVertically
        ) {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                accent.copy(
                                    alpha =
                                        0.15f
                                )
                        ),
                shape =
                    RoundedCornerShape(
                        if (compact) 12.dp else 14.dp
                    )
            ) {
                Box(
                    Modifier
                        .size(
                            if (compact) 38.dp else 44.dp
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        symbol,
                        color =
                            accent,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            if (compact) 20.sp else 23.sp
                    )
                }
            }

            Spacer(
                Modifier.width(
                    if (compact) 8.dp else 12.dp
                )
            )

            Column(
                Modifier
                    .weight(
                        1f
                    )
            ) {
                Text(
                    check.title,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    check.detail,
                    color =
                        TextSecondary,
                    lineHeight =
                        if (compact) 16.sp else 18.sp,
                    fontSize =
                        if (compact) 11.sp else 12.sp
                )
            }
        }
    }
}


@Composable
private fun CompactModuleToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val compact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Row(
        modifier =
            Modifier
                .fillMaxWidth(),
        verticalAlignment =
            Alignment
                .CenterVertically
    ) {
        Text(
            label,
            fontSize =
                if (compact) 13.sp else 14.sp,
            lineHeight =
                if (compact) 17.sp else 19.sp,
            modifier =
                Modifier
                    .weight(
                        1f
                    )
        )

        Switch(
            checked =
                checked,
            onCheckedChange =
                onChange
        )
    }
}

private fun humanBytes(
    value: Long
): String {
    if (
        value <=
        0
    ) {
        return "0 B"
    }

    val units =
        listOf(
            "B",
            "KB",
            "MB",
            "GB"
        )

    var amount =
        value.toDouble()

    var index =
        0

    while (
        amount >=
        1024 &&
        index <
        units.lastIndex
    ) {
        amount /=
            1024

        index++
    }

    return String.format(
        "%.2f %s",
        amount,
        units[index]
    )
}

@Composable
private fun TestLabScreen(
    serverUrl: String,
    apiKey: String,
    languageCode: String,
    onBack: () -> Unit
) {
    val testLabConfiguration =
        LocalConfiguration.current

    val testLabScreenWidthDp =
        testLabConfiguration.screenWidthDp

    val testLabScreenHeightDp =
        testLabConfiguration.screenHeightDp

    val testLabCompact =
        testLabScreenWidthDp < 380

    val testLabTablet =
        minOf(
            testLabScreenWidthDp,
            testLabScreenHeightDp
        ) >= 600

    val testLabWide =
        testLabScreenWidthDp >= 600

    val testLabContentMaxWidth =
        if (testLabWide) 980.dp else 10000.dp

    val testLabHorizontalPadding =
        when {
            testLabCompact -> 10.dp
            testLabTablet -> 28.dp
            else -> 16.dp
        }


    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var remoteBuilds by
        remember {
            mutableStateOf<
                List<
                    com.appforge.studio.build.RemoteBuildHistoryItem
                >
            >(
                emptyList()
            )
        }

    var selectedBuildId by
        remember {
            mutableStateOf<
                String?
            >(
                null
            )
        }

    var leftBuildId by
        remember {
            mutableStateOf<
                String?
            >(
                null
            )
        }

    var rightBuildId by
        remember {
            mutableStateOf<
                String?
            >(
                null
            )
        }

    var report by
        remember {
            mutableStateOf<
                TestLabResult?
            >(
                null
            )
        }

    var comparison by
        remember {
            mutableStateOf<
                BuildCompareResult?
            >(
                null
            )
        }

    var releaseNotes by
        remember {
            mutableStateOf<
                List<String>
            >(
                emptyList()
            )
        }

    var message by
        remember {
            mutableStateOf(
                ""
            )
        }

    var loading by
        remember {
            mutableStateOf(
                false
            )
        }

    fun client() =
        BuildApiClient(
            context =
                context,
            baseUrl =
                serverUrl,
            apiKey =
                apiKey
        )

    fun loadHistory() {
        loading =
            true

        scope.launch {
            try {
                remoteBuilds =
                    withContext(
                        Dispatchers.IO
                    ) {
                        client()
                            .history()
                    }
                        .filter {
                            it.status ==
                            "success"
                        }
                        .take(
                            30
                        )

                message =
                    "${remoteBuilds.size} başarılı derleme bulundu."
            } catch (
                t: Throwable
            ) {
                message =
                    "Derleme geçmişi alınamadı: ${t.message}"
            } finally {
                loading =
                    false
            }
        }
    }

    LaunchedEffect(
        serverUrl,
        apiKey
    ) {
        loadHistory()
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Test Laboratuvarı",
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (!testLabCompact) {
                        Text(
                            "APK/AAB Analizi • Güvenlik • Karşılaştırma • Sürüm Notları",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text("←")
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        loadHistory()
                    },
                    enabled =
                        !loading
                ) {
                    Text(
                        "Yenile"
                    )
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = testLabContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = testLabHorizontalPadding,
                    vertical =
                        if (testLabCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (testLabCompact) 8.dp else 12.dp)
        ) {
            if (
                message.isNotBlank()
            ) {
                item {
                    NoteCard(
                        message
                    )
                }
            }

            item {
                Section(
                    "Başarılı Derlemeler",
                    "Analiz etmek veya iki sürümü karşılaştırmak için build seç."
                )
            }

            items(
                remoteBuilds,
                key = {
                    it.buildId
                }
            ) {
                item ->
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(if (testLabCompact) 15.dp else 18.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(if (testLabCompact) 11.dp else 14.dp),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    8.dp
                                )
                    ) {
                        Text(
                            item.appName
                                .ifBlank {
                                    item.packageName
                                },
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "${item.packageName}\n${AppForgeBuildNumbers.label(item.buildNo)}",
                            color =
                                TextSecondary,
                            fontSize =
                                11.sp
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement
                                    .spacedBy(
                                        6.dp
                                    )
                        ) {
                            Button(
                                onClick = {
                                    selectedBuildId =
                                        item.buildId

                                    loading =
                                        true

                                    scope.launch {
                                        try {
                                            report =
                                                withContext(
                                                    Dispatchers.IO
                                                ) {
                                                    client()
                                                        .testLab(
                                                            item.buildId
                                                        )
                                                }

                                            releaseNotes =
                                                withContext(
                                                    Dispatchers.IO
                                                ) {
                                                    client()
                                                        .releaseNotes(
                                                            item.buildId
                                                        )
                                                }

                                            message =
                                                "Artifact analizi tamamlandı."
                                        } catch (
                                            t: Throwable
                                        ) {
                                            message =
                                                "Analiz başarısız: ${t.message}"
                                        } finally {
                                            loading =
                                                false
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                            ) {
                                Text(
                                    if (testLabCompact) "Analiz" else "Analiz Et"
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    leftBuildId =
                                        item.buildId
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                            ) {
                                Text(
                                    if (
                                        leftBuildId ==
                                        item.buildId
                                    ) {
                                        "A ✓"
                                    } else {
                                        "A"
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    rightBuildId =
                                        item.buildId
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                            ) {
                                Text(
                                    if (
                                        rightBuildId ==
                                        item.buildId
                                    ) {
                                        "B ✓"
                                    } else {
                                        "B"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (
                leftBuildId !=
                null &&
                rightBuildId !=
                null &&
                leftBuildId !=
                rightBuildId
            ) {
                item {
                    Button(
                        onClick = {
                            val left =
                                leftBuildId

                            val right =
                                rightBuildId

                            if (
                                left == null ||
                                right ==
                                null
                            ) {
                                return@Button
                            }

                            loading =
                                true

                            scope.launch {
                                try {
                                    comparison =
                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            client()
                                                .compareBuilds(
                                                    left,
                                                    right
                                                )
                                        }

                                    message =
                                        "Sürüm karşılaştırması hazır."
                                } catch (
                                    t: Throwable
                                ) {
                                    message =
                                        "Karşılaştırma başarısız: ${t.message}"
                                } finally {
                                    loading =
                                        false
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(if (testLabCompact) 48.dp else 52.dp)
                    ) {
                        Text(
                            "A ↔ B KARŞILAŞTIR"
                        )
                    }
                }
            }

            val currentReport =
                report

            if (
                currentReport !=
                null
            ) {
                item {
                    Section(
                        "Çıktı Boyut Analizi",
                        "${currentReport.appName} • ${currentReport.packageName}"
                    )
                }

                currentReport.apk
                    ?.let {
                        artifact ->
                        item {
                            ArtifactReportCard(
                                artifact
                            )
                        }
                    }

                currentReport.aab
                    ?.let {
                        artifact ->
                        item {
                            ArtifactReportCard(
                                artifact
                            )
                        }
                    }

                item {
                    Section(
                        "Güvenlik Merkezi",
                        "Sunucu tarafı build yapılandırma kontrolleri."
                    )
                }

                items(
                    currentReport.security
                ) {
                    insight ->
                    Card(
                        colors =
                            CardDefaults
                                .cardColors(
                                    containerColor =
                                        Card2
                                ),
                        shape =
                            RoundedCornerShape(
                                16.dp
                            )
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(if (testLabCompact) 11.dp else 14.dp)
                        ) {
                            Text(
                                "${insight.severity.uppercase()} • ${insight.title}",
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                insight.detail,
                                color =
                                    TextSecondary
                            )
                        }
                    }
                }

                if (
                    releaseNotes
                        .isNotEmpty()
                ) {
                    item {
                        Section(
                            "Sürüm Notu Oluşturucu",
                            "Önceki başarılı build ile değişikliklerden oluşturuldu."
                        )
                    }

                    items(
                        releaseNotes
                    ) {
                        note ->
                        NoteCard(
                            "• $note"
                        )
                    }
                }
            }

            val compare =
                comparison

            if (
                compare !=
                null
            ) {
                item {
                    Section(
                        "Sürüm Karşılaştırma",
                        "${
                            AppForgeBuildNumbers.label(
                                remoteBuilds
                                    .firstOrNull {
                                        it.buildId ==
                                            compare.leftBuildId
                                    }
                                    ?.buildNo
                            )
                        } → ${
                            AppForgeBuildNumbers.label(
                                remoteBuilds
                                    .firstOrNull {
                                        it.buildId ==
                                            compare.rightBuildId
                                    }
                                    ?.buildNo
                            )
                        }"
                    )
                }

                item {
                    NoteCard(
                        "Değişiklik: ${compare.changeCount}\nAPK farkı: ${humanBytes(compare.apkDeltaBytes)}\nAAB farkı: ${humanBytes(compare.aabDeltaBytes)}"
                    )
                }

                items(
                    compare.changes
                        .take(
                            30
                        )
                ) {
                    change ->
                    InspectorLine(
                        change
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactReportCard(
    artifact:
        com.appforge.studio.build.ArtifactSizeReport
) {
    val artifactReportCompact =
        LocalConfiguration.current.screenWidthDp < 380


    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Card2
                ),
        shape =
            RoundedCornerShape(
                if (artifactReportCompact) if (artifactReportCompact) 12.dp else 16.dp else 20.dp
            )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    16.dp
                ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        8.dp
                    )
        ) {
            Text(
                artifact.kind
                    .uppercase(),
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    20.sp
            )

            InfoLine(
                "Dosya",
                humanBytes(
                    artifact.fileSizeBytes
                )
            )

            InfoLine(
                "Açılmış",
                humanBytes(
                    artifact.uncompressedBytes
                )
            )

            InfoLine(
                "Girdi",
                artifact.entryCount
                    .toString()
            )

            Text(
                "Kategori boyutları",
                fontWeight =
                    FontWeight.Bold
            )

            artifact.groups
                .entries
                .sortedByDescending {
                    it.value
                }
                .take(
                    10
                )
                .forEach {
                    group ->
                    Text(
                        "${group.key}: ${humanBytes(group.value)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )
                }

            Text(
                "En büyük dosyalar",
                fontWeight =
                    FontWeight.Bold
            )

            artifact.topFiles
                .take(
                    10
                )
                .forEach {
                    file ->
                    Text(
                        "${humanBytes(file.sizeBytes)} • ${file.path}",
                        color =
                            TextSecondary,
                        fontSize =
                            11.sp
                    )
                }
        }
    }
}


private data class LocalAiChatMessage(
    val id: Long,
    val role: String,
    val text: String
)

@Composable
private fun LocalAiAssistantScreen(
    draft: ProjectDraft,
    onDraftChange: (ProjectDraft) -> Unit,
    currentProjectId: String?,
    onSelectProject: (SavedProject) -> Unit,
    runtimeContext: AssistantRuntimeContext,
    buildLogs: List<String>,
    buildPreflight: List<String>,
    languageCode: String,
    modelInfo: LocalAiModelInfo?,
    importMessage: String,
    installing: Boolean,
    installProgress: Int,
    onInstallDefaultModel: () -> Unit,
    onImportModel: () -> Unit,
    onModelChanged: (LocalAiModelInfo?) -> Unit,
    onNavigate: (AssistantDestination) -> Unit,
    onBack: () -> Unit
) {
    val aiConfiguration =
        LocalConfiguration.current

    val aiScreenWidthDp =
        aiConfiguration.screenWidthDp

    val aiScreenHeightDp =
        aiConfiguration.screenHeightDp

    val aiCompact =
        aiScreenWidthDp < 380

    val aiTablet =
        minOf(
            aiScreenWidthDp,
            aiScreenHeightDp
        ) >= 600

    val aiWide =
        aiScreenWidthDp >= 600

    val aiContentMaxWidth =
        if (aiWide) 980.dp else 10000.dp

    val aiHorizontalPadding =
        when {
            aiCompact -> 10.dp
            aiTablet -> 28.dp
            else -> 16.dp
        }


    val context =
        LocalContext.current

    val availableProjects =
        remember(
            currentProjectId
        ) {
            ProjectLibrary.load(
                context
            )
        }

    val selectedProject =
        availableProjects.firstOrNull {
            it.id == currentProjectId
        }

    var projectMenuExpanded by
        remember {
            mutableStateOf(
                false
            )
        }

    val scope =
        rememberCoroutineScope()

    val assistant =
        remember {
            AppForgeLocalAssistant(
                context
            )
        }

    var backend by
        remember(
            modelInfo?.path
        ) {
            mutableStateOf(
                modelInfo
                    ?.backend
                    ?: LocalAiBackend.CPU
            )
        }

    var initialized by
        remember {
            mutableStateOf(
                false
            )
        }

    var initializing by
        remember {
            mutableStateOf(
                false
            )
        }

    var generating by
        remember {
            mutableStateOf(
                false
            )
        }

    var includeProjectContext by
        remember(
            currentProjectId
        ) {
            mutableStateOf(
                currentProjectId != null
            )
        }

    var input by
        remember {
            mutableStateOf(
                ""
            )
        }

    var status by
        remember {
            mutableStateOf(
                "Model seçildiğinde tüm AI çıkarımı cihaz üzerinde yapılır."
            )
        }

    var suggestedActions by
        remember {
            mutableStateOf<List<AssistantAppAction>>(
                emptyList()
            )
        }

    var generationJob by
        remember {
            mutableStateOf<
                Job?
            >(
                null
            )
        }

    var nextId by
        remember {
            mutableLongStateOf(
                1L
            )
        }

    val messages =
        remember {
            mutableStateListOf<
                LocalAiChatMessage
            >()
        }

    var historyLoaded by
        remember {
            mutableStateOf(
                false
            )
        }

    LaunchedEffect(
        Unit
    ) {
        if (
            messages.isEmpty()
        ) {
            LocalAiChatStore
                .load(
                    context
                )
                .forEach {
                    stored ->
                    messages.add(
                        LocalAiChatMessage(
                            id =
                                nextId++,
                            role =
                                stored.role,
                            text =
                                stored.text
                        )
                    )
                }
        }

        historyLoaded =
            true
    }

    /*
     * Cevap model tarafından token token üretilirken
     * her token için diske yazma. 700 ms sessizlikten sonra
     * son 20 mesajı yalnız cihazda sakla.
     */
    val historySnapshot =
        messages.map {
            StoredAiMessage(
                role =
                    it.role,
                text =
                    it.text
            )
        }

    LaunchedEffect(
        historyLoaded,
        historySnapshot
    ) {
        if (
            historyLoaded
        ) {
            delay(
                700L
            )

            LocalAiChatStore
                .save(
                    context,
                    historySnapshot
                )
        }
    }

    fun addMessage(
        role: String,
        text: String
    ): Long {
        val id =
            nextId

        nextId++

        messages.add(
            LocalAiChatMessage(
                id =
                    id,
                role =
                    role,
                text =
                    text
            )
        )

        return id
    }

    fun replaceMessage(
        id: Long,
        text: String
    ) {
        val index =
            messages.indexOfFirst {
                it.id ==
                id
            }

        if (
            index >=
            0
        ) {
            messages[index] =
                messages[index]
                    .copy(
                        text =
                            text
                    )
        }
    }

    fun initializeModel() {
        val model =
            modelInfo

        if (
            model == null
        ) {
            status =
                "Önce .litertlm model dosyası içe aktar."
            return
        }

        initialized =
            false

        initializing =
            true

        scope.launch {
            try {
                val result =
                    assistant.initialize(
                        model,
                        backend
                    )

                backend =
                    result.backend

                val updated =
                    LocalAiModelStore
                        .updateBackend(
                            context,
                            result.backend
                        )

                if (
                    updated !=
                    null
                ) {
                    onModelChanged(
                        updated
                    )
                }

                initialized =
                    true

                status =
                    "Yerel AI hazır • ${result.backend.name}"
            } catch (
                t: Throwable
            ) {
                status =
                    "Model başlatılamadı: ${t.message}"
            } finally {
                initializing =
                    false
            }
        }
    }

    fun sendQuestion(
        prefilledQuestion: String? = null
    ) {
        val question =
            (
                prefilledQuestion
                    ?: input
            ).trim()

        if (
            question.isBlank() ||
            generating
        ) {
            return
        }

        suggestedActions =
            AppForgeAssistantIntegration
                .actionsFor(
                    question
                )

        AppForgeAiCommandParser
            .parse(question, draft)
            ?.let { command ->
                input = ""
                onDraftChange(command.draft)
                suggestedActions =
                    listOf(
                        AssistantAppAction(
                            command.destination,
                            "Değişikliği aç",
                            "Uygulanan proje ayarını gözden geçir."
                        )
                    )
                addMessage("user", question)
                addMessage("assistant", command.answer)
                status = "Komut uygulandı • cihaz üzerinde anında"
                return
            }

        val normalizedQuestion =
            question.lowercase()

        val asksBuildDiagnosis =
            listOf(
                "build",
                "derleme",
                "derlen",
                "hata",
                "başarısız",
                "neden olmadı",
                "log"
            ).any {
                normalizedQuestion.contains(
                    it
                )
            }

        if (
            asksBuildDiagnosis &&
            (
                buildLogs.isNotEmpty() ||
                    buildPreflight.isNotEmpty()
            )
        ) {
            val diagnosis =
                AppForgeBuildErrorAdvisor
                    .diagnose(
                        logs = buildLogs,
                        preflight = buildPreflight,
                        status = runtimeContext.buildStatus
                    )

            input = ""

            addMessage(
                "user",
                question
            )

            addMessage(
                "assistant",
                AppForgeAssistantIntegration
                    .diagnosisAnswer(
                        diagnosis
                    )
            )

            status =
                "Build tanısı tamamlandı • güvenli yerel analiz"

            return
        }

        val quickGuidance =
            if (
                languageCode == "tr" ||
                languageCode == "system"
            ) {
                AppForgeAssistantIntegration
                    .quickGuidance(
                        question
                    )
            } else {
                null
            }

        if (quickGuidance != null) {
            input = ""

            suggestedActions =
                quickGuidance.actions

            addMessage(
                "user",
                question
            )

            addMessage(
                "assistant",
                quickGuidance.answer
            )

            status =
                "Yanıt tamamlandı • hızlı yönlendirme"

            return
        }

        /*
         * AppForge sık soruları modelden bağımsızdır.
         * Model henüz yüklenirken bile bilgi tabanından anında cevap ver.
         */
        val instantAnswer =
            if (
                languageCode == "tr" ||
                languageCode == "system"
            ) {
                AppForgeKnowledgeBase
                    .directTurkishAnswer(
                        question
                    )
            } else {
                null
            }

        if (
            !instantAnswer.isNullOrBlank()
        ) {
            input =
                ""

            addMessage(
                "user",
                question
            )

            addMessage(
                "assistant",
                instantAnswer
            )

            status =
                "Yanıt tamamlandı • hızlı bilgi tabanı"

            return
        }

        if (!initialized) {
            status =
                "Yerel AI hazırlanıyor; bilgi ve yönlendirme soruları bu sırada da anında çalışır."
            return
        }

        input =
            ""

        addMessage(
            "user",
            question
        )

        val answerId =
            addMessage(
                "assistant",
                ""
            )

        generating =
            true

        status =
            "Yerel model yanıt üretiyor..."

        generationJob =
            scope.launch {
                val result =
                    StringBuilder()

                try {
                    assistant.ask(
                        question =
                            question,
                        draft =
                            draft,
                        includeProjectContext =
                            includeProjectContext,
                        runtimeContext =
                            runtimeContext
                    ) {
                        part ->
                        result.append(
                            part
                        )

                        replaceMessage(
                            answerId,
                            result.toString()
                        )
                    }

                    if (
                        result.isEmpty()
                    ) {
                        replaceMessage(
                            answerId,
                            "Model boş yanıt döndürdü."
                        )
                    }

                    status =
                        "Yanıt tamamlandı • yerel"
                } catch (
                    t: Throwable
                ) {
                    if (
                        result.isEmpty()
                    ) {
                        replaceMessage(
                            answerId,
                            "Yanıt oluşturulamadı."
                        )
                    }

                    status =
                        "Yerel AI hatası: ${t.message}"
                } finally {
                    generating =
                        false

                    generationJob =
                        null
                }
            }
    }

    // AUTO_LOCAL_AI_START_V2
    LaunchedEffect(
        modelInfo?.path
    ) {
        if (
            modelInfo != null &&
            !initialized &&
            !initializing
        ) {
            initializeModel()
        }
    }

    DisposableEffect(
        assistant
    ) {
        onDispose {
            generationJob
                ?.cancel()

            assistant.close()
        }
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        t(
                            languageCode,
                            "local_ai"
                        ),
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (!aiCompact) {
                        Text(
                            "Cihazda çalışan Yerel AI • Çevrimdışı",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text("←")
                }
            },
            actions = {
                if (
                    messages
                        .isNotEmpty()
                ) {
                    TextButton(
                        onClick = {
                            generationJob
                                ?.cancel()

                            messages.clear()

                            suggestedActions =
                                emptyList()

                            LocalAiChatStore
                                .clear(
                                    context
                                )

                            scope.launch {
                                runCatching {
                                    assistant
                                        .resetConversation()
                                }
                            }

                            status =
                                "Yeni sohbet hazır."
                        }
                    ) {
                        Text(
                            "Temizle"
                        )
                    }
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = aiContentMaxWidth)
                    .weight(
                        1f
                    )
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = aiHorizontalPadding,
                    vertical =
                        if (aiCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (aiCompact) 8.dp else 12.dp)
        ) {
            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(if (aiCompact) 18.dp else 22.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(if (aiCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    10.dp
                                )
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment
                                    .CenterVertically
                        ) {
                            Text(
                                "🧠",
                                fontSize =
                                    32.sp
                            )

                            Spacer(
                                Modifier.width(
                                    10.dp
                                )
                            )

                            Column(
                                Modifier.weight(
                                    1f
                                )
                            ) {
                                Text(
                                    if (
                                        modelInfo != null
                                    ) {
                                        "AppForge Yerel AI"
                                    } else {
                                        "Yerel AI hazırlanıyor"
                                    },
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    if (
                                        modelInfo !=
                                        null
                                    ) {
                                        "Cihaz üzerinde çalışıyor • Çevrimdışı"
                                    } else {
                                        "AppForge modeli otomatik olarak hazırlar"
                                    },
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        11.sp
                                )
                            }
                        }

                        Text(
                            text =
                                when {
                                    modelInfo == null &&
                                        installing ->
                                        "Yerel AI hazırlanıyor • %$installProgress"

                                    modelInfo == null ->
                                        "Yerel AI otomatik hazırlanıyor"

                                    initializing ->
                                        "Yerel AI başlatılıyor..."

                                    initialized ->
                                        "✓ Yerel AI hazır"

                                    else ->
                                        "Yerel AI hazırlanıyor..."
                                },
                            color =
                                if (initialized) {
                                    Color(0xFF65E3A1)
                                } else {
                                    TextSecondary
                                },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                13.sp
                        )

                        if (installing) {
                            LinearProgressIndicator(
                                modifier =
                                    Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            "Analiz edilen proje",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    projectMenuExpanded =
                                        true
                                },
                                enabled =
                                    availableProjects.isNotEmpty(),
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    selectedProject
                                        ?.let {
                                            "${it.name} • ${it.packageName}"
                                        }
                                        ?: if (
                                            availableProjects.isEmpty()
                                        ) {
                                            "Önce bir proje kaydet"
                                        } else {
                                            "Proje seç"
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            DropdownMenu(
                                expanded =
                                    projectMenuExpanded,
                                onDismissRequest = {
                                    projectMenuExpanded =
                                        false
                                }
                            ) {
                                availableProjects.forEach {
                                    project ->

                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    project.name,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    project.packageName,
                                                    color = TextSecondary,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            projectMenuExpanded =
                                                false

                                            includeProjectContext =
                                                true

                                            onSelectProject(
                                                project
                                            )

                                            status =
                                                "Analiz projesi seçildi: ${project.name}"
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment =
                                Alignment
                                    .CenterVertically
                        ) {
                            Column(
                                Modifier.weight(
                                    1f
                                )
                            ) {
                                Text(
                                    "Mevcut proje bağlamını kullan",
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    "Uygulama adı, paket, sürüm, derleme ve açık özellikler AI bağlamına eklenir; hassas hesap bilgileri eklenmez.",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        11.sp
                                )
                            }

                            Switch(
                                checked =
                                    includeProjectContext &&
                                        currentProjectId != null,
                                onCheckedChange = {
                                    includeProjectContext =
                                        it
                                },
                                enabled =
                                    currentProjectId != null
                            )
                        }

                        if (
                            importMessage
                                .isNotBlank()
                        ) {
                            Text(
                                importMessage,
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }

                        Text(
                            status,
                            color =
                                if (
                                    initialized
                                ) {
                                    Color(
                                        0xFF65E3A1
                                    )
                                } else {
                                    TextSecondary
                                },
                            fontSize =
                                12.sp
                        )
                    }
                }
            }

            item {
                NoteCard(
                    "Yerel Asistan yanıtları cihaz üzerinde üretir; proje içeriğini harici bir AI hizmetine göndermez. Güncel internet bilgilerini kendiliğinden kontrol edemez."
                )
            }

            if (
                messages.isEmpty()
            ) {
                item {
                    Section(
                        "Hızlı Sorular",
                        "Bir örneğe dokun veya kendi sorunu yaz."
                    )
                }

                item {
                    Column(
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    7.dp
                                )
                    ) {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        Color(0xFF102037)
                                ),
                            shape =
                                RoundedCornerShape(if (aiCompact) 17.dp else 20.dp),
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(if (aiCompact) 12.dp else 16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        10.dp
                                    )
                            ) {
                                Text(
                                    "🧠 AI Proje Asistanı",
                                    fontWeight =
                                        FontWeight.Bold,
                                    fontSize =
                                        18.sp
                                )

                                Text(
                                    "Mevcut projeyi model çalıştırmadan anında denetle. Parolalar ve API anahtarları rapora eklenmez.",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        12.sp,
                                    lineHeight =
                                        18.sp
                                )

                                Button(
                                    onClick = {
                                        addMessage(
                                            "user",
                                            "Projeyi analiz et"
                                        )

                                        addMessage(
                                            "assistant",
                                            AppForgeProjectAdvisor
                                                .projectAnalysisText(
                                                    draft
                                                )
                                        )

                                        status =
                                            "Proje analizi tamamlandı."
                                    },
                                    enabled =
                                        currentProjectId != null,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "🔍 PROJEYİ ANALİZ ET"
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        addMessage(
                                            "user",
                                            "Bu proje Play Store'a hazır mı?"
                                        )

                                        addMessage(
                                            "assistant",
                                            AppForgeProjectAdvisor
                                                .playStoreText(
                                                    draft
                                                )
                                        )

                                        status =
                                            "Play Store hazırlık denetimi tamamlandı."
                                    },
                                    enabled =
                                        currentProjectId != null,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "🚀 PLAY STORE'A HAZIRLA"
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val fixResult =
                                            AppForgeProjectAdvisor
                                                .applySafeFixes(
                                                    draft
                                                )

                                        onDraftChange(
                                            fixResult.draft
                                        )

                                        addMessage(
                                            "user",
                                            "Güvenli düzeltmeleri uygula"
                                        )

                                        addMessage(
                                            "assistant",
                                            AppForgeProjectAdvisor
                                                .fixesText(
                                                    fixResult
                                                )
                                        )

                                        status =
                                            if (
                                                fixResult
                                                    .changes
                                                    .isEmpty()
                                            ) {
                                                "Otomatik güvenli düzeltme gerekmiyor."
                                            } else {
                                                "${fixResult.changes.size} güvenli düzeltme uygulandı."
                                            }
                                    },
                                    enabled =
                                        currentProjectId != null,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "⚡ GÜVENLİ DÜZELT"
                                    )
                                }
                            }
                        }

                        Text(
                            if (
                                currentProjectId != null
                            ) {
                                "Bu projeye göre"
                            } else {
                                "Önce analiz projesini seç"
                            },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                16.sp
                        )

                        Text(
                            if (
                                currentProjectId != null
                            ) {
                                "AppForge seçili proje ayarlarına göre en yararlı soruları otomatik seçer."
                            } else {
                                "Üstteki proje seçiciden kayıtlı bir proje seçmeden proje analizi yapılmaz."
                            },
                            color =
                                TextSecondary,
                            fontSize =
                                11.sp
                        )

                        (
                            if (
                                currentProjectId != null
                            ) {
                                AppForgeSmartSuggestions
                                    .forProject(
                                        draft
                                    )
                            } else {
                                emptyList()
                            }
                        )
                            .forEach {
                                prompt ->
                                OutlinedButton(
                                    onClick = {
                                        when (
                                            prompt
                                        ) {
                                            "Projeyi analiz et" -> {
                                                addMessage(
                                                    "user",
                                                    prompt
                                                )

                                                addMessage(
                                                    "assistant",
                                                    AppForgeProjectAdvisor
                                                        .projectAnalysisText(
                                                            draft
                                                        )
                                                )

                                                status =
                                                    "Proje analizi tamamlandı."
                                            }

                                            "Bu proje Play Store'a hazır mı?" -> {
                                                addMessage(
                                                    "user",
                                                    prompt
                                                )

                                                addMessage(
                                                    "assistant",
                                                    AppForgeProjectAdvisor
                                                        .playStoreText(
                                                            draft
                                                        )
                                                )

                                                status =
                                                    "Play Store hazırlık denetimi tamamlandı."
                                            }

                                            else -> {
                                                sendQuestion(
                                                    prompt
                                                )
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        prompt
                                    )
                                }
                            }

                        Text(
                            "Konuşma geçmişi yalnız bu cihazda tutulur • son 20 mesaj",
                            color =
                                TextSecondary,
                            fontSize =
                                10.sp
                        )

                        Text(
                            "Sık Sorulanlar",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                16.sp
                        )

                        AppForgeKnowledgeBase
                            .quickQuestions()
                            .take(
                                if (aiCompact) 4 else 6
                            )
                            .forEach {
                            prompt ->
                            OutlinedButton(
                                onClick = {
                                    sendQuestion(
                                        prompt
                                    )
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                            ) {
                                Text(
                                    prompt
                                )
                            }
                        }

                        Text(
                            "Tüm AppForge özellikleri bilgi tabanında hazır. Başka bir konu için aşağıdaki alana yazman yeterli.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            items(
                messages,
                key = {
                    it.id
                }
            ) {
                LocalAiMessageBubble(
                    it
                )
            }

            if (
                suggestedActions.isNotEmpty()
            ) {
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Color(0xFF102037)
                            ),
                        shape =
                            RoundedCornerShape(
                                if (aiCompact) 17.dp else 20.dp
                            ),
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        if (aiCompact) 12.dp else 16.dp
                                    ),
                            verticalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Uygulamada devam et",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Text(
                                "Yanıtla ilgili bölümü doğrudan açabilirsin.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )

                            suggestedActions.forEach {
                                action ->

                                OutlinedButton(
                                    onClick = {
                                        onNavigate(
                                            action.destination
                                        )
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier =
                                            Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            action.label,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            action.description,
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            Color(
                                0xFF0C1627
                            )
                    ),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = aiContentMaxWidth)
                    .fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    topStart =
                        22.dp,
                    topEnd =
                        22.dp
                )
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        12.dp
                    ),
                verticalArrangement =
                    Arrangement
                        .spacedBy(
                            8.dp
                        )
            ) {
                OutlinedTextField(
                    value =
                        input,
                    onValueChange = {
                        input =
                            it
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    placeholder = {
                        Text(
                            t(
                                languageCode,
                                "ask_ai"
                            )
                        )
                    },
                    maxLines =
                        5,
                    enabled =
                        !generating
                )

                Row(
                    horizontalArrangement =
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
                ) {
                    if (
                        generating
                    ) {
                        OutlinedButton(
                            onClick = {
                                generationJob
                                    ?.cancel()

                                generating =
                                    false

                                status =
                                    "Yanıt üretimi durduruldu."

                                scope.launch {
                                    runCatching {
                                        assistant
                                            .resetConversation()
                                    }
                                }
                            },
                            modifier =
                                Modifier
                                    .weight(
                                        1f
                                    )
                        ) {
                            Text(
                                "Durdur"
                            )
                        }
                    }

                    Button(
                        onClick = {
                            sendQuestion()
                        },
                        enabled =
                            initialized &&
                            !generating &&
                            input
                                .isNotBlank(),
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    ) {
                        Text(
                            if (
                                generating
                            ) {
                                "Yanıtlıyor..."
                            } else {
                                "Gönder"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalAiMessageBubble(
    message: LocalAiChatMessage
) {
    val aiBubbleCompact =
        LocalConfiguration.current.screenWidthDp < 380


    val user =
        message.role ==
        "user"

    Row(
        modifier =
            Modifier
                .fillMaxWidth(),
        horizontalArrangement =
            if (
                user
            ) {
                Arrangement.End
            } else {
                Arrangement.Start
            }
    ) {
        Card(
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            if (
                                user
                            ) {
                                Accent
                            } else {
                                Card2
                            }
                    ),
            shape =
                RoundedCornerShape(
                    if (aiBubbleCompact) 15.dp else 18.dp
                ),
            modifier =
                Modifier
                    .fillMaxWidth(
                        if (aiBubbleCompact) 0.96f else 0.9f
                    )
        ) {
            Column(
                Modifier.padding(
                    if (aiBubbleCompact) 11.dp else 13.dp
                )
            ) {
                Text(
                    if (
                        user
                    ) {
                        "Sen"
                    } else {
                        "AppForge AI"
                    },
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        11.sp,
                    color =
                        if (
                            user
                        ) {
                            Color.White
                        } else {
                            Color(
                                0xFF8DB4FF
                            )
                        }
                )

                Spacer(
                    Modifier.height(
                        4.dp
                    )
                )

                Text(
                    message.text
                        .ifBlank {
                            "…"
                        },
                    lineHeight =
                        20.sp
                )
            }
        }
    }
}

private fun validateDraft(d: ProjectDraft, serverUrl: String) {
    require(d.appName.isNotBlank()) { "Uygulama adı gerekli." }
    require(Regex("""^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$""").matches(d.packageName)) {
        "Geçerli package name gir."
    }

    require(d.minSdk in 26..37) {
        "Min SDK 26 ile 37 arasında olmalı."
    }

    require(d.targetSdk in 26..37) {
        "Hedef SDK 26 ile 37 arasında olmalı."
    }

    require(d.minSdk <= d.targetSdk) {
        "Min SDK, Hedef SDK değerinden büyük olamaz."
    }

    if (d.sourceMode == SourceMode.URL) {
        require(d.webUrl.startsWith("https://", true)) {
            "URL https:// ile başlamalı."
        }
    }

    if (d.signingMode == SigningMode.CUSTOM) {
        require(!d.keystoreUri.isNullOrBlank()) { "Keystore seç." }
        require(d.keyAlias.isNotBlank()) { "Key alias gerekli." }
        require(d.storePassword.isNotBlank()) { "Store password gerekli." }
        require(d.keyPassword.isNotBlank()) { "Key password gerekli." }
        require(serverUrl.startsWith("https://", true)) {
            "Özel keystore ile imzalama için Build Service HTTPS olmalı."
        }
    }

    if (d.deepLinkEnabled) {
        require(d.deepLinkScheme.isNotBlank()) { "Deep link scheme gerekli." }
        require(d.deepLinkHost.isNotBlank()) { "Deep link host gerekli." }
    }

    if (d.admobEnabled) {
        require(d.admobAppId.isNotBlank()) { "AdMob App ID gerekli." }
    }

    if (d.billingEnabled) {
        require(
            d.purchaseVerificationUrl.startsWith(
                "https://",
                true
            )
        ) {
            "Billing için HTTPS purchase verification URL gerekli."
        }

        require(
            d.billingProductIds.isNotBlank() ||
            d.billingSubscriptionIds.isNotBlank()
        ) { "En az bir ürün veya abonelik ID gerekli." }

        if (d.purchaseVerificationUrl.isNotBlank()) {
            require(d.purchaseVerificationUrl.startsWith("https://", true)) {
                "Doğrulama URL'si HTTPS olmalı."
            }
        }
    }

    if (d.firebaseAnalyticsEnabled || d.firebaseCrashlyticsEnabled || d.firebaseMessagingEnabled) {
        require(!d.firebaseConfigUri.isNullOrBlank()) {
            "Firebase için google-services.json seç."
        }
    }
}

@Composable
private fun SourceStep(
    d: ProjectDraft,
    status: String,
    update: (ProjectDraft) -> Unit,
    onPick: () -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    fun autoPackageName(
        appName: String
    ): String {

        val normalized =
            appName
                .trim()
                .lowercase(
                    java.util.Locale.ROOT
                )
                .replace(
                    'ç',
                    'c'
                )
                .replace(
                    'ğ',
                    'g'
                )
                .replace(
                    'ı',
                    'i'
                )
                .replace(
                    'ö',
                    'o'
                )
                .replace(
                    'ş',
                    's'
                )
                .replace(
                    'ü',
                    'u'
                )

        var segment =
            normalized
                .replace(
                    Regex(
                        "[^a-z0-9]"
                    ),
                    ""
                )

        if (
            segment.isBlank()
        ) {
            segment =
                "myapp"
        }

        if (
            segment.first()
                .isDigit()
        ) {
            segment =
                "app$segment"
        }

        return "com.example.$segment"
    }

    val packageRegex =
        Regex(
            """^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$"""
        )

    val appNameValid =
        d.appName.trim().isNotBlank()

    val packageValid =
        packageRegex.matches(
            d.packageName.trim()
        )

    val localSourceValid =
        !d.startPage.isNullOrBlank() &&
        (
            !d.sourceUri.isNullOrBlank() ||
            !d.importedFolder.isNullOrBlank()
        )

    val webUrlValid =
        d.webUrl.trim()
            .startsWith(
                "https://",
                ignoreCase = true
            ) &&
        d.webUrl.trim().length > 8

    val sourceValid =
        if (
            d.sourceMode ==
            SourceMode.LOCAL
        ) {
            localSourceValid
        } else {
            webUrlValid
        }

    val stepReady =
        appNameValid &&
        packageValid &&
        sourceValid

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
    ) {
        item {
            Section(
                "1. Proje",
                "Uygulama, sürüm, Android SDK ve kaynak ayarlarını yapılandır."
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement =
                    Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp)
            ) {
                FilterChip(
                    selected =
                        d.sourceMode ==
                        SourceMode.LOCAL,
                    onClick = {
                        update(
                            d.copy(
                                sourceMode =
                                    SourceMode.LOCAL
                            )
                        )
                    },
                    label = {
                        Text(
                            "HTML / ZIP"
                        )
                    }
                )

                FilterChip(
                    selected =
                        d.sourceMode ==
                        SourceMode.URL,
                    onClick = {
                        update(
                            d.copy(
                                sourceMode =
                                    SourceMode.URL
                            )
                        )
                    },
                    label = {
                        Text(
                            "Web URL"
                        )
                    }
                )
            }
        }

        item {
            OutlinedTextField(
                value =
                    d.appName,
                onValueChange = {
                    appName ->

                    /*
                     * Package name hâlâ otomatik moddaysa
                     * uygulama adına göre güncelle.
                     *
                     * Kullanıcı daha önce özel bir package name
                     * yazdıysa ona dokunma.
                     */
                    val currentPackage =
                        d.packageName
                            .trim()

                    val oldAutoPackage =
                        autoPackageName(
                            d.appName
                        )

                    val autoMode =
                        currentPackage.isBlank() ||
                        currentPackage ==
                            "com.example.myapp" ||
                        currentPackage ==
                            oldAutoPackage

                    val nextPackage =
                        if (
                            autoMode
                        ) {
                            autoPackageName(
                                appName
                            )
                        } else {
                            d.packageName
                        }

                    update(
                        d.copy(
                            appName =
                                appName,

                            packageName =
                                nextPackage
                        )
                    )
                },
                label = {
                    Text(
                        "Uygulama adı"
                    )
                },
                placeholder = {
                    Text(
                        "Örn: Benim Uygulamam"
                    )
                },
                singleLine = true,
                isError =
                    d.appName.isNotEmpty() &&
                    !appNameValid,
                supportingText = {
                    if (
                        d.appName.isEmpty()
                    ) {
                        Text(
                            "Play Store ve cihazda görünecek isim."
                        )
                    } else if (
                        appNameValid
                    ) {
                        Text(
                            "✓ Uygulama adı geçerli"
                        )
                    } else {
                        Text(
                            "Uygulama adı boş bırakılamaz."
                        )
                    }
                },
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value =
                    d.packageName,
                onValueChange = {
                    packageName ->

                    update(
                        d.copy(
                            packageName =
                                packageName
                                    .trim()
                                    .lowercase()
                        )
                    )
                },
                label = {
                    Text(
                        "Paket adı"
                    )
                },
                placeholder = {
                    Text(
                        "com.firma.uygulama"
                    )
                },
                singleLine = true,
                isError =
                    d.packageName.isNotEmpty() &&
                    !packageValid,
                supportingText = {
                    if (
                        packageValid
                    ) {
                        Text(
                            "✓ Paket adı geçerli"
                        )
                    } else {
                        Text(
                            "Örn: com.hackmaster.uygulama"
                        )
                    }
                },
                modifier =
                    Modifier.fillMaxWidth()
            )
        }


        // APPFORGE_STEP1_VERSION_SDK
        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = Card2
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            if (formCompact) 12.dp
                            else 16.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            if (formCompact) 8.dp
                            else 10.dp
                        )
                ) {
                    Text(
                        "Sürüm ayarları",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            16.sp
                    )

                    OutlinedTextField(
                        value =
                            d.versionCode.toString(),
                        onValueChange = {
                            raw ->

                            raw
                                .filter {
                                    it.isDigit()
                                }
                                .toIntOrNull()
                                ?.takeIf {
                                    it >= 1
                                }
                                ?.let {
                                    update(
                                        d.copy(
                                            versionCode = it,
                                            autoVersionCode = false
                                        )
                                    )
                                }
                        },
                        label = {
                            Text("Version Code")
                        },
                        supportingText = {
                            Text(
                                "Her yeni Play Store sürümünde artırılmalı."
                            )
                        },
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                update(
                                    d.copy(
                                        versionCode =
                                            (
                                                d.versionCode - 1
                                            ).coerceAtLeast(1),
                                        autoVersionCode =
                                            false
                                    )
                                )
                            },
                            enabled =
                                d.versionCode > 1,
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text("−1")
                        }

                        Button(
                            onClick = {
                                update(
                                    d.copy(
                                        versionCode =
                                            d.versionCode + 1,
                                        autoVersionCode =
                                            false
                                    )
                                )
                            },
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text("VERSION +1")
                        }
                    }

                    OutlinedTextField(
                        value =
                            d.versionName,
                        onValueChange = {
                            update(
                                d.copy(
                                    versionName =
                                        it.trim()
                                            .take(32)
                                )
                            )
                        },
                        label = {
                            Text("Version Name")
                        },
                        placeholder = {
                            Text("1.0.0")
                        },
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                "Otomatik Version Code",
                                fontWeight =
                                    FontWeight.SemiBold,
                                fontSize =
                                    13.sp
                            )

                            Text(
                                if (d.autoVersionCode) {
                                    "Yeni build başlatılırken otomatik artırılır."
                                } else {
                                    "Version Code manuel yönetiliyor."
                                },
                                color =
                                    TextSecondary,
                                fontSize =
                                    11.sp
                            )
                        }

                        Switch(
                            checked =
                                d.autoVersionCode,
                            onCheckedChange = {
                                update(
                                    d.copy(
                                        autoVersionCode = it
                                    )
                                )
                            }
                        )
                    }

                    HorizontalDivider()

                    Text(
                        "Android SDK",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            16.sp
                    )

                    Text(
                        "Min SDK en eski desteklenen Android sürümünü, Hedef SDK ise uygulamanın hedeflediği Android API seviyesini belirler.",
                        color =
                            TextSecondary,
                        fontSize =
                            11.sp,
                        lineHeight =
                            16.sp
                    )

                    OutlinedTextField(
                        value =
                            if (d.minSdk < 0) {
                                ""
                            } else {
                                d.minSdk.toString()
                            },
                        onValueChange = {
                            raw ->

                            val digits =
                                raw.filter {
                                    it.isDigit()
                                }

                            if (digits.isBlank()) {
                                update(
                                    d.copy(
                                        minSdk = -1
                                    )
                                )
                            } else {
                                digits
                                    .toIntOrNull()
                                    ?.let {
                                        value ->

                                        update(
                                            d.copy(
                                                minSdk = value
                                            )
                                        )
                                    }
                            }
                        },
                        label = {
                            Text("Min SDK")
                        },
                        supportingText = {
                            Text(
                                "API 26–37 • Varsayılan: 26"
                            )
                        },
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value =
                            if (d.targetSdk < 0) {
                                ""
                            } else {
                                d.targetSdk.toString()
                            },
                        onValueChange = {
                            raw ->

                            val digits =
                                raw.filter {
                                    it.isDigit()
                                }

                            if (digits.isBlank()) {
                                update(
                                    d.copy(
                                        targetSdk = -1
                                    )
                                )
                            } else {
                                digits
                                    .toIntOrNull()
                                    ?.let {
                                        value ->

                                        update(
                                            d.copy(
                                                targetSdk = value
                                            )
                                        )
                                    }
                            }
                        },
                        label = {
                            Text("Hedef SDK")
                        },
                        supportingText = {
                            Text(
                                "API 26–37 • Güncel öneri: 37"
                            )
                        },
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Text(
                        "Aktif sürüm: ${d.versionName} • code ${d.versionCode}",
                        color =
                            Accent,
                        fontWeight =
                            FontWeight.SemiBold,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "SDK: min ${if (d.minSdk < 0) "—" else d.minSdk} • target ${if (d.targetSdk < 0) "—" else d.targetSdk} • compile 37",
                        color =
                            Accent,
                        fontWeight =
                            FontWeight.SemiBold,
                        fontSize =
                            12.sp
                    )
                }
            }
        }

        if (
            d.sourceMode ==
            SourceMode.LOCAL
        ) {
            item {
                Button(
                    onClick =
                        onPick,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (
                            d.sourceLabel
                                .isBlank()
                        ) {
                            "HTML veya ZIP seç"
                        } else {
                            "Kaynağı değiştir"
                        }
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        update(
                            d.copy(
                                autoVersionCode =
                                    !d.autoVersionCode
                            )
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Otomatik sürüm arttır: ${
                            if (d.autoVersionCode) "Açık" else "Kapalı"
                        }"
                    )
                }
            }

            if (
                d.sourceLabel.isNotBlank()
            ) {
                item {
                    Card(
                        colors =
                            CardDefaults
                                .cardColors(
                                    containerColor =
                                        Card2
                                ),
                        shape =
                            RoundedCornerShape(
                                16.dp
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(if (formCompact) 12.dp else 16.dp),
                            verticalArrangement =
                                Arrangement
                                    .spacedBy(
                                        6.dp
                                    )
                        ) {
                            Text(
                                "Seçilen kaynak",
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                d.sourceLabel,
                                color =
                                    TextSecondary
                            )

                            if (
                                !d.startPage
                                    .isNullOrBlank()
                            ) {
                                Text(
                                    "Başlangıç: ${
                                        File(
                                            d.startPage!!
                                        ).name
                                    }",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        12.sp
                                )
                            }

                            Text(
                                if (
                                    localSourceValid
                                ) {
                                    "✓ Yerel proje hazır"
                                } else {
                                    "Kaynak doğrulanamadı"
                                },
                                fontSize =
                                    12.sp
                            )
                        }
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value =
                        d.webUrl,
                    onValueChange = {
                        update(
                            d.copy(
                                webUrl =
                                    it.trim()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Web adresi"
                        )
                    },
                    placeholder = {
                        Text(
                            "https://site.com"
                        )
                    },
                    singleLine = true,
                    isError =
                        d.webUrl.isNotBlank() &&
                        !webUrlValid,
                    supportingText = {
                        if (
                            d.webUrl.isBlank()
                        ) {
                            Text(
                                "HTTPS web adresi gir."
                            )
                        } else if (
                            webUrlValid
                        ) {
                            Text(
                                "✓ HTTPS adresi geçerli"
                            )
                        } else {
                            Text(
                                "Adres https:// ile başlamalı."
                            )
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                Card2
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
                ) {
                    Text(
                        "Proje özeti",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        if (
                            appNameValid
                        ) {
                            "✓ Uygulama adı"
                        } else {
                            "○ Uygulama adı gerekli"
                        },
                        fontSize =
                            13.sp
                    )

                    Text(
                        if (
                            packageValid
                        ) {
                            "✓ Paket adı"
                        } else {
                            "○ Paket adı geçersiz"
                        },
                        fontSize =
                            13.sp
                    )

                    Text(
                        if (
                            sourceValid
                        ) {
                            "✓ Kaynak hazır"
                        } else {
                            "○ Kaynak gerekli"
                        },
                        fontSize =
                            13.sp
                    )

                    Spacer(
                        Modifier.height(
                            4.dp
                        )
                    )

                    Text(
                        if (
                            stepReady
                        ) {
                            "✅ 1. aşama hazır"
                        } else {
                            "Eksik alanları tamamla."
                        },
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            if (
                                stepReady
                            ) {
                                Accent
                            } else {
                                TextSecondary
                            }
                    )
                }
            }
        }

        if (
            status.isNotBlank() &&
            status != "Hazır"
        ) {
            item {
                NoteCard(
                    status
                )
            }
        }
    }
}

private data class ExtraPermissionSpec(
    val key: String,
    val title: String,
    val description: String,
    val manifestLabel: String
)

private val extraPermissionSpecs =
    listOf(
        ExtraPermissionSpec("BLUETOOTH", "Bluetooth ve yakın cihazlar", "Android 12+ tarama ve bağlantı izinlerini ekler.", "BLUETOOTH_SCAN / CONNECT"),
        ExtraPermissionSpec("BIOMETRIC", "Biyometrik doğrulama", "Parmak izi veya yüz doğrulama kullanan native projeler içindir.", "USE_BIOMETRIC"),
        ExtraPermissionSpec("CALENDAR", "Takvim", "Takvim etkinliklerini okuma ve yazma yeteneklerini ekler.", "READ / WRITE_CALENDAR"),
        ExtraPermissionSpec("CONTACTS", "Kişiler", "Kişi listesini okuma ve kullanıcı onayıyla kayıt ekleme yeteneklerini ekler.", "READ / WRITE_CONTACTS"),
        ExtraPermissionSpec("BACKGROUND_LOCATION", "Arka plan konumu", "Yüksek hassasiyetli izindir; yalnız gerçek arka plan konum ihtiyacında aç.", "ACCESS_BACKGROUND_LOCATION"),
        ExtraPermissionSpec("EXACT_ALARM", "Kesin alarm", "Tam zamanında alarm/hatırlatıcı kuran uygulamalar içindir.", "SCHEDULE_EXACT_ALARM"),
        ExtraPermissionSpec("MEDIA_IMAGES", "Fotoğraf erişimi", "Android 13+ cihazlarda galerideki görselleri okumayı sağlar.", "READ_MEDIA_IMAGES"),
        ExtraPermissionSpec("MEDIA_VIDEO", "Video erişimi", "Android 13+ cihazlarda galerideki videoları okumayı sağlar.", "READ_MEDIA_VIDEO"),
        ExtraPermissionSpec("ACTIVITY_RECOGNITION", "Fiziksel aktivite", "Adım ve hareket tanıma özellikleri içindir.", "ACTIVITY_RECOGNITION")
    )

@Composable
private fun PermissionsStep(
    d: ProjectDraft,
    analysis: SourceCapabilityAnalysis?,
    update: (ProjectDraft) -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val permissionCount =
        listOf(
            d.camera,
            d.microphone,
            d.location,
            d.notifications,
            d.networkState,
            d.wakeLock,
            d.nfc
        ).count { it }
            + d.additionalPermissions.size

    LazyColumn(
        contentPadding = PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 8.dp else 12.dp)
    ) {
        item {
            Section(
                "2. İzinler",
                "HTML/ZIP içeriği otomatik analiz edilir. İstersen seçimleri değiştirebilirsin."
            )
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = Card2
                    ),
                shape =
                    RoundedCornerShape(18.dp),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
                ) {
                    Text(
                        "İzin özeti",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "$permissionCount isteğe bağlı izin seçili",
                        color =
                            TextSecondary,
                        fontSize =
                            13.sp
                    )

                    Text(
                        "✓ INTERNET • Otomatik",
                        color =
                            Accent,
                        fontSize =
                            12.sp,
                        fontWeight =
                            FontWeight.Medium
                    )
                }
            }
        }

        if (
            analysis != null &&
            analysis.detectedLabels()
                .isNotEmpty()
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            "🔍 Otomatik algılandı",
                            color =
                                Accent,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "${analysis.scannedFiles} kaynak dosyası tarandı",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )

                        analysis
                            .detectedDetails()
                            .forEach {
                                detail ->

                                Text(
                                    "✓ $detail",
                                    fontSize =
                                        12.sp
                                )
                            }
                    }
                }
            }
        }

        item {
            FeatureToggleCard(
                title = "Kamera",
                description =
                    if (
                        analysis?.camera ==
                        true
                    ) {
                        "Web uygulamasının kameradan fotoğraf çekmesini sağlar.\n" +
                            "✓ Otomatik algılandı • " +
                            (
                                analysis.cameraReason
                                    ?: "Kamera kullanımı"
                            )
                    } else {
                        "Web uygulamasının kameradan fotoğraf çekmesini sağlar."
                    },
                checked = d.camera,
                permission = "CAMERA"
            ) {
                update(
                    d.copy(
                        camera = it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title = "Mikrofon",
                description =
                    if (analysis?.microphone == true) {
                        "Ses kaydı ve WebRTC mikrofon erişimi için RECORD_AUDIO iznini ekler.\n" +
                            "✓ Otomatik algılandı • " +
                            (analysis.microphoneReason ?: "Mikrofon kullanımı")
                    } else {
                        "Ses kaydı ve WebRTC mikrofon erişimi için RECORD_AUDIO iznini ekler."
                    },
                checked = d.microphone,
                permission = "RECORD_AUDIO"
            ) {
                update(
                    d.copy(
                        microphone = it,
                        fileUpload =
                            d.fileUpload || it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title = "Konum",
                description =
                    if (
                        analysis?.location ==
                        true
                    ) {
                        "Web uygulamasının cihaz konumunu istemesini sağlar.\n" +
                            "✓ Otomatik algılandı • " +
                            (
                                analysis.locationReason
                                    ?: "navigator.geolocation"
                            )
                    } else {
                        "Web uygulamasının cihaz konumunu istemesini sağlar."
                    },
                checked = d.location,
                permission =
                    "ACCESS_FINE_LOCATION"
            ) {
                update(
                    d.copy(
                        location = it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title = "Ağ durumunu okuma",
                description =
                    if (analysis?.networkState == true) {
                        "İnternet bağlantısının Wi-Fi veya mobil veri durumunu kontrol etmeyi sağlar.\n" +
                            "✓ Otomatik algılandı • " +
                            (analysis.networkStateReason ?: "Ağ durumu kullanımı")
                    } else {
                        "İnternet bağlantısının Wi-Fi veya mobil veri durumunu kontrol etmeyi sağlar."
                    },
                checked = d.networkState,
                permission = "ACCESS_NETWORK_STATE"
            ) {
                update(
                    d.copy(
                        networkState = it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title = "Ekranı/işlemi uyanık tutma",
                description =
                    if (analysis?.wakeLock == true) {
                        "Uzun medya, aktarım veya kiosk işlemlerinde WAKE_LOCK yeteneğini ekler.\n" +
                            "✓ Otomatik algılandı • " +
                            (analysis.wakeLockReason ?: "Wake Lock kullanımı")
                    } else {
                        "Uzun medya, aktarım veya kiosk işlemlerinde WAKE_LOCK yeteneğini ekler."
                    },
                checked = d.wakeLock,
                permission = "WAKE_LOCK"
            ) {
                update(
                    d.copy(
                        wakeLock = it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title = "NFC",
                description =
                    if (analysis?.nfc == true) {
                        "NFC destekli kaynak projeler için NFC donanım erişimini bildirir.\n" +
                            "✓ Otomatik algılandı • " +
                            (analysis.nfcReason ?: "NFC kullanımı")
                    } else {
                        "NFC destekli kaynak projeler için NFC donanım erişimini bildirir."
                    },
                checked = d.nfc,
                permission = "NFC"
            ) {
                update(
                    d.copy(
                        nfc = it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title = "Bildirimler",
                description =
                    if (
                        analysis?.notifications ==
                        true
                    ) {
                        "Android 13+ cihazlarda bildirim iznini etkinleştirir.\n" +
                            "✓ Otomatik algılandı • " +
                            (
                                analysis.notificationsReason
                                    ?: "Bildirim API kullanımı"
                            )
                    } else {
                        "Android 13+ cihazlarda bildirim iznini etkinleştirir."
                    },
                checked =
                    d.notifications,
                permission =
                    "POST_NOTIFICATIONS"
            ) {
                update(
                    d.copy(
                        notifications = it
                    )
                )
            }
        }

        item {
            Text(
                "Gelişmiş izinler",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        items(
            extraPermissionSpecs,
            key = { it.key }
        ) { spec ->
            FeatureToggleCard(
                title = spec.title,
                description = spec.description,
                checked = spec.key in d.additionalPermissions,
                permission = spec.manifestLabel
            ) { enabled ->
                update(
                    d.copy(
                        additionalPermissions =
                            if (enabled) {
                                d.additionalPermissions + spec.key
                            } else {
                                d.additionalPermissions - spec.key
                            }
                    )
                )
            }
        }

        item {
            NoteCard(
                "🔍 Yüklenen HTML/ZIP veya kaynak projede Kamera, Mikrofon, Konum, Bildirim, Ağ durumu, WAKE_LOCK, NFC ve ilgili özellikler otomatik algılanıp işaretlenir."
            )
        }

        item {
            NoteCard(
                "AppForge geniş depolama izni istemez. Dosya işlemleri modern Android dosya seçicileriyle yapılır."
            )
        }

        item {
            NoteCard(
                "Media3 etkinse gerekli medya ve arka plan servis izinleri build sırasında otomatik eklenir."
            )
        }
    }
}


@Composable
private fun FeaturesStep(
    d: ProjectDraft,
    analysis: SourceCapabilityAnalysis?,
    update: (ProjectDraft) -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val enabledCount =
        listOf(
            d.webJavaScriptEnabled,
            d.webDomStorageEnabled,
            d.webZoomEnabled,
            d.webWideViewPortEnabled,
            d.webOverviewModeEnabled,
            d.webMediaAutoplayEnabled,
            d.webMixedContentAllowed,
            d.fileUpload,
            d.downloads,
            d.offlineCache,
            d.fullscreen
        ).count {
            it
        }

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 8.dp else 12.dp)
    ) {
        item {
            Section(
                "3. WebView Pro",
                "Kaynak kod otomatik analiz edilir. Gereken WebView özellikleri açılır; istersen seçimleri değiştirebilirsin."
            )
        }

        if (
            analysis != null
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                if (formCompact) 12.dp else 16.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            "🔍 Otomatik WebView analizi",
                            color =
                                Accent,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "${analysis.scannedFiles} kaynak dosyası tarandı",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )

                        if (analysis.fileUpload) {
                            Text(
                                "✓ Dosya yükleme • ${
                                    analysis.fileUploadReason
                                        ?: "Dosya seçici kullanımı bulundu"
                                }",
                                fontSize =
                                    12.sp
                            )
                        }

                        if (analysis.downloads) {
                            Text(
                                "✓ DownloadManager • ${
                                    analysis.downloadsReason
                                        ?: "İndirme kullanımı bulundu"
                                }",
                                fontSize =
                                    12.sp
                            )
                        }

                        if (analysis.mediaPlayer) {
                            Text(
                                "✓ Medya özellikleri • ${
                                    analysis.mediaPlayerReason
                                        ?: "Medya kullanımı bulundu"
                                }",
                                fontSize =
                                    12.sp
                            )
                        }

                        if (
                            !analysis.fileUpload &&
                            !analysis.downloads &&
                            !analysis.mediaPlayer
                        ) {
                            Text(
                                "Ek WebView özelliği gerektiren kullanım bulunmadı.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Card2
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp)
                ) {
                    Text(
                        "WebView yapılandırması",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "$enabledCount / 11 özellik açık",
                        color =
                            TextSecondary,
                        fontSize =
                            13.sp
                    )

                    Text(
                        "Güvenli ve modern varsayılanlarla başla. İhtiyacın olmayan özellikleri kapatabilirsin.",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp,
                        lineHeight =
                            17.sp
                    )

                    Button(
                        onClick = {
                            update(
                                d.copy(
                                    webJavaScriptEnabled =
                                        true,
                                    webDomStorageEnabled =
                                        true,
                                    webZoomEnabled =
                                        true,
                                    webWideViewPortEnabled =
                                        true,
                                    webOverviewModeEnabled =
                                        true,
                                    webMediaAutoplayEnabled =
                                        true,
                                    webMixedContentAllowed =
                                        false,
                                    fileUpload =
                                        true,
                                    downloads =
                                        true,
                                    offlineCache =
                                        true,
                                    fullscreen =
                                        false
                                )
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "ÖNERİLEN AYARLAR"
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Web motoru",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            FeatureToggleCard(
                title =
                    "JavaScript",
                description =
                    "Modern web uygulamalarının JavaScript kodlarını çalıştırmasını sağlar.",
                checked =
                    d.webJavaScriptEnabled,
                recommended =
                    true
            ) {
                enabled ->

                update(
                    d.copy(
                        webJavaScriptEnabled =
                            enabled,

                        // Native Bridge JavaScript olmadan
                        // çalışamayacağı için güvenli şekilde kapat.
                        javascriptBridge =
                            d.javascriptBridge &&
                                enabled,

                        remoteBridgeAllowed =
                            d.remoteBridgeAllowed &&
                                enabled
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "DOM Storage",
                description =
                    "localStorage ve sessionStorage kullanan web uygulamalarını destekler.",
                checked =
                    d.webDomStorageEnabled,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        webDomStorageEnabled =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Yakınlaştırma / Pinch Zoom",
                description =
                    "İki parmakla sayfa yakınlaştırma ve uzaklaştırmayı etkinleştirir.",
                checked =
                    d.webZoomEnabled,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        webZoomEnabled =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Geniş Viewport",
                description =
                    "Masaüstü ve responsive web sayfalarının ekran genişliğine daha doğru uyarlanmasını sağlar.",
                checked =
                    d.webWideViewPortEnabled,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        webWideViewPortEnabled =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Overview Mode",
                description =
                    "Sayfanın ilk açılışta ekran genişliğine sığdırılmasına yardımcı olur.",
                checked =
                    d.webOverviewModeEnabled,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        webOverviewModeEnabled =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Medya Autoplay",
                description =
                    "HTML5 ses ve videoların ek kullanıcı dokunuşu gerektirmeden oynatılabilmesini sağlar.",
                checked =
                    d.webMediaAutoplayEnabled,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        webMediaAutoplayEnabled =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Mixed Content Uyumluluğu",
                description =
                    "HTTPS sayfanın bazı HTTP alt kaynaklarını compatibility modunda yüklemesine izin verir. Yalnız gerektiğinde aç.",
                checked =
                    d.webMixedContentAllowed
            ) {
                update(
                    d.copy(
                        webMixedContentAllowed =
                            it
                    )
                )
            }
        }

        if (
            d.webMixedContentAllowed
        ) {
            item {
                NoteCard(
                    "⚠ Mixed Content güvenliği azaltabilir. AppForge MIXED_CONTENT_ALWAYS_ALLOW yerine daha sınırlı COMPATIBILITY_MODE kullanır."
                )
            }
        }

        item {
            Text(
                "Dosya, önbellek ve ekran",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            FeatureToggleCard(
                title =
                    "Dosya yükleme",
                description =
                    "HTML dosya seçme alanlarının Android dosya seçicisini kullanmasını sağlar.",
                checked =
                    d.fileUpload,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        fileUpload =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "DownloadManager",
                description =
                    "Web içeriğinden indirilen dosyaları Android indirme yöneticisine gönderir.",
                checked =
                    d.downloads,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        downloads =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Offline Cache",
                description =
                    "Web içeriğinin önbellekten tekrar kullanılmasına yardımcı olur.",
                checked =
                    d.offlineCache,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        offlineCache =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Tam ekran",
                description =
                    "Sistem çubuklarını gizleyerek uygulamaya daha geniş ekran alanı verir.",
                checked =
                    d.fullscreen
            ) {
                update(
                    d.copy(
                        fullscreen =
                            it
                    )
                )
            }
        }

        item {
            NoteCard(
                "Dosya sistemi erişimi AppForge tarafından güvenli şekilde yönetilir; WebView'e genel depolama erişimi verilmez."
            )
        }
    }
}


@Composable
private fun FeatureToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    permission: String? = null,
    recommended: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Card2
                ),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (formCompact) 12.dp else 16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
        ) {
            Column(
                modifier =
                    Modifier.weight(
                        1f
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        5.dp
                    )
            ) {
                Text(
                    title,
                    fontWeight =
                        FontWeight.Medium
                )

                Text(
                    description,
                    color =
                        TextSecondary,
                    fontSize =
                        12.sp,
                    lineHeight =
                        17.sp
                )

                if (
                    permission != null
                ) {
                    Text(
                        "Android izni: $permission",
                        color =
                            Accent,
                        fontSize =
                            11.sp,
                        fontWeight =
                            FontWeight.Medium
                    )
                } else if (
                    recommended
                ) {
                    Text(
                        "Önerilen",
                        color =
                            Accent,
                        fontSize =
                            11.sp,
                        fontWeight =
                            FontWeight.Medium
                    )
                }
            }

            Switch(
                checked =
                    checked,
                onCheckedChange =
                    onCheckedChange
            )
        }
    }
}

@Composable
private fun AppearanceStep(
    d: ProjectDraft,
    update: (ProjectDraft) -> Unit,
    onPickIcon: () -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val orientationLabel =
        when (
            d.orientation
        ) {
            "portrait" ->
                "Dikey"

            "landscape" ->
                "Yatay"

            else ->
                "Otomatik"
        }

    val darkPresetSelected =
        d.primaryColor.equals(
            "#6B7CFF",
            true
        ) &&
        d.backgroundColor.equals(
            "#07101F",
            true
        )

    val lightPresetSelected =
        d.primaryColor.equals(
            "#3F51B5",
            true
        ) &&
        d.backgroundColor.equals(
            "#FFFFFF",
            true
        )

    val oledPresetSelected =
        d.primaryColor.equals(
            "#8CC9F6",
            true
        ) &&
        d.backgroundColor.equals(
            "#000000",
            true
        )

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
    ) {
        item {
            Section(
                "4. Görünüm",
                "İkon, ekran yönü, tema ve Splash ayarları."
            )
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                Card2
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 7.dp else 10.dp)
                ) {
                    Text(
                        "Uygulama ikonu",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        if (
                            d.iconName.isBlank()
                        ) {
                            "Henüz özel ikon seçilmedi."
                        } else {
                            "✓ Özel ikon seçildi"
                        },
                        color =
                            TextSecondary,
                        fontSize =
                            13.sp
                    )

                    Button(
                        onClick =
                            onPickIcon,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (
                                d.iconName.isBlank()
                            ) {
                                "PNG Uygulama İkonu Seç"
                            } else {
                                "İkonu değiştir"
                            }
                        )
                    }

                    Text(
                        "PNG/JPEG otomatik yönlendirilir, gerçek PNG'ye çevrilir ve adaptive-icon güvenli alanına sığdırılır.",
                        color =
                            TextSecondary,
                        fontSize =
                            11.sp
                    )
                }
            }
        }

        item {
            Text(
                "Uygulama türü",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        if (formCompact) 5.dp else 7.dp
                    )
            ) {
                FilterChip(
                    selected =
                        d.appCategory == "auto",
                    onClick = {
                        update(
                            d.copy(
                                appCategory = "auto"
                            )
                        )
                    },
                    label = {
                        Text("Otomatik")
                    }
                )

                FilterChip(
                    selected =
                        d.appCategory == "game",
                    onClick = {
                        update(
                            d.copy(
                                appCategory = "game"
                            )
                        )
                    },
                    label = {
                        Text("Oyun")
                    }
                )

                FilterChip(
                    selected =
                        d.appCategory == "none",
                    onClick = {
                        update(
                            d.copy(
                                appCategory = "none"
                            )
                        )
                    },
                    label = {
                        Text("Standart uygulama")
                    }
                )
            }
        }

        item {
            NoteCard(
                "Otomatik mod Unity ve oyun işaretli projeleri android:appCategory=\"game\" olarak üretir. Telefonun Gaming Hub'a taşıma kararı üreticiye aittir; istersen türü elle değiştirebilirsin."
            )
        }

        item {
            Text(
                "Ekran yönü",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement =
                    Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
            ) {
                FilterChip(
                    selected =
                        d.orientation ==
                            "unspecified",
                    onClick = {
                        update(
                            d.copy(
                                orientation =
                                    "unspecified"
                            )
                        )
                    },
                    label = {
                        Text(
                            "Otomatik"
                        )
                    }
                )

                FilterChip(
                    selected =
                        d.orientation ==
                            "portrait",
                    onClick = {
                        update(
                            d.copy(
                                orientation =
                                    "portrait"
                            )
                        )
                    },
                    label = {
                        Text(
                            "Dikey"
                        )
                    }
                )

                FilterChip(
                    selected =
                        d.orientation ==
                            "landscape",
                    onClick = {
                        update(
                            d.copy(
                                orientation =
                                    "landscape"
                            )
                        )
                    },
                    label = {
                        Text(
                            "Yatay"
                        )
                    }
                )
            }
        }

        item {
            Text(
                "Hazır temalar",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp),
                modifier =
                    Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth()
            ) {
                FilterChip(
                    selected =
                        darkPresetSelected,
                    onClick = {
                        update(
                            d.copy(
                                primaryColor =
                                    "#6B7CFF",
                                backgroundColor =
                                    "#07101F",
                                statusBarColor =
                                    "#07101F",
                                navigationBarColor =
                                    "#07101F"
                            )
                        )
                    },
                    label = {
                        Text("Koyu")
                    },
                    modifier =
                        Modifier.widthIn(
                            min =
                                if (formCompact) 92.dp else 108.dp
                        )
                )

                FilterChip(
                    selected =
                        lightPresetSelected,
                    onClick = {
                        update(
                            d.copy(
                                primaryColor =
                                    "#3F51B5",
                                backgroundColor =
                                    "#FFFFFF",
                                statusBarColor =
                                    "#FFFFFF",
                                navigationBarColor =
                                    "#FFFFFF"
                            )
                        )
                    },
                    label = {
                        Text("Açık")
                    },
                    modifier =
                        Modifier.widthIn(
                            min =
                                if (formCompact) 92.dp else 108.dp
                        )
                )

                FilterChip(
                    selected =
                        oledPresetSelected,
                    onClick = {
                        update(
                            d.copy(
                                primaryColor =
                                    "#8CC9F6",
                                backgroundColor =
                                    "#000000",
                                statusBarColor =
                                    "#000000",
                                navigationBarColor =
                                    "#000000"
                            )
                        )
                    },
                    label = {
                        Text("OLED")
                    },
                    modifier =
                        Modifier.widthIn(
                            min =
                                if (formCompact) 92.dp else 108.dp
                        )
                )
            }
        }

        item {
            Text(
                "Renkler",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            ColorField(
                "Ana renk",
                d.primaryColor
            ) {
                update(
                    d.copy(
                        primaryColor =
                            it
                    )
                )
            }
        }

        item {
            ColorField(
                "Arka plan",
                d.backgroundColor
            ) {
                update(
                    d.copy(
                        backgroundColor =
                            it
                    )
                )
            }
        }

        item {
            ColorField(
                "Status bar",
                d.statusBarColor
            ) {
                update(
                    d.copy(
                        statusBarColor =
                            it
                    )
                )
            }
        }

        item {
            ColorField(
                "Navigation bar",
                d.navigationBarColor
            ) {
                update(
                    d.copy(
                        navigationBarColor =
                            it
                    )
                )
            }
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                Card2
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 7.dp else 10.dp)
                ) {
                    Toggle(
                        "Android 12+ Splash",
                        d.splashEnabled
                    ) {
                        update(
                            d.copy(
                                splashEnabled =
                                    it
                            )
                        )
                    }

                    if (
                        d.splashEnabled
                    ) {
                        OutlinedTextField(
                            value =
                                d.splashText,
                            onValueChange = {
                                update(
                                    d.copy(
                                        splashText =
                                            it
                                    )
                                )
                            },
                            label = {
                                Text(
                                    "Splash alt yazısı"
                                )
                            },
                            placeholder = {
                                Text(
                                    d.appName.ifBlank {
                                        "Uygulama açılıyor..."
                                    }
                                )
                            },
                            singleLine = true,
                            modifier =
                                Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                Card2
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
                ) {
                    Text(
                        "Görünüm özeti",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        if (
                            d.iconName.isBlank()
                        ) {
                            "○ Varsayılan ikon"
                        } else {
                            "✓ Özel ikon"
                        },
                        fontSize =
                            13.sp
                    )

                    Text(
                        "✓ Ekran yönü: $orientationLabel",
                        fontSize =
                            13.sp
                    )

                    Text(
                        if (
                            d.splashEnabled
                        ) {
                            "✓ Android Splash açık"
                        } else {
                            "○ Android Splash kapalı"
                        },
                        fontSize =
                            13.sp
                    )

                    Text(
                        "Ana renk: ${d.primaryColor}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Arka plan: ${d.backgroundColor}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeBridgeStep(
    d: ProjectDraft,
    analysis: SourceCapabilityAnalysis?,
    update: (ProjectDraft) -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val featureCount =
        if (
            d.javascriptBridge
        ) {
            listOf(
                d.shareBridge,
                d.clipboardBridge,
                d.vibrationBridge,
                d.mediaPlayerBridge,
                d.qrScanner
            ).count {
                it
            }
        } else {
            0
        }

    val bridgeModeText =
        when {
            !d.javascriptBridge ->
                "Kapalı"

            d.sourceMode ==
                SourceMode.LOCAL ->
                "Yerel içerik"

            d.remoteBridgeAllowed ->
                "HTTPS uzak origin"

            else ->
                "Uzak içerikte kapalı"
        }

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
    ) {
        item {
            Section(
                "5. Native Bridge",
                "Kaynak kod analiz edilir; gereken AppForge Android köprüleri otomatik açılır."
            )
        }

        if (
            analysis != null
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                if (formCompact) 12.dp else 16.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            "🔍 Otomatik Native Bridge analizi",
                            color =
                                Accent,
                            fontWeight =
                                FontWeight.Bold
                        )

                        if (analysis.mediaPlayer) {
                            Text(
                                "✓ Media3 / arka plan medya • ${
                                    analysis.mediaPlayerReason
                                        ?: "Medya kullanımı bulundu"
                                }",
                                fontSize =
                                    12.sp
                            )
                        }

                        if (analysis.qrScanner) {
                            Text(
                                "✓ QR / Barkod • ${
                                    analysis.qrScannerReason
                                        ?: "QR kullanımı bulundu"
                                }",
                                fontSize =
                                    12.sp
                            )
                        }

                        if (
                            analysis.mediaPlayer ||
                            analysis.qrScanner
                        ) {
                            Text(
                                "✓ JavaScript Bridge otomatik etkinleştirildi",
                                color =
                                    Accent,
                                fontSize =
                                    12.sp
                            )
                        } else {
                            Text(
                                "Native Bridge gerektiren kullanım bulunmadı.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                Card2
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 7.dp else 10.dp)
                ) {
                    Text(
                        "Native Bridge durumu",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        if (
                            d.javascriptBridge
                        ) {
                            "✓ JavaScript Bridge açık"
                        } else {
                            "○ JavaScript Bridge kapalı"
                        },
                        fontSize =
                            13.sp
                    )

                    Text(
                        "Mod: $bridgeModeText",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "$featureCount / 5 Android özelliği açık",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )
                }
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "JavaScript Bridge",
                description =
                    "Web sayfasının AppForge Android API'lerine erişmesini sağlayan ana bağlantıdır.",
                checked =
                    d.javascriptBridge,
                recommended =
                    true
            ) {
                update(
                    d.copy(
                        javascriptBridge =
                            it
                    )
                )
            }
        }

        if (
            d.javascriptBridge
        ) {
            if (
                d.sourceMode ==
                SourceMode.URL
            ) {
                item {
                    FeatureToggleCard(
                        title =
                            "Uzak URL'de Native Bridge",
                        description =
                            "Bridge yalnız seçtiğin HTTPS web kaynağında kullanılabilir.",
                        checked =
                            d.remoteBridgeAllowed
                    ) {
                        update(
                            d.copy(
                                remoteBridgeAllowed =
                                    it
                            )
                        )
                    }
                }

                if (
                    d.remoteBridgeAllowed
                ) {
                    item {
                        Card(
                            colors =
                                CardDefaults
                                    .cardColors(
                                        containerColor =
                                            Card2
                                    ),
                            shape =
                                RoundedCornerShape(
                                    18.dp
                                ),
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(if (formCompact) 12.dp else 16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
                            ) {
                                Text(
                                    "⚠ Güvenlik",
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    "Uzak Native Bridge yalnız tamamen güvendiğin HTTPS sitesi için açık olmalı.",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        12.sp,
                                    lineHeight =
                                        17.sp
                                )

                                if (
                                    d.webUrl.isNotBlank()
                                ) {
                                    Text(
                                        "Kaynak: ${d.webUrl}",
                                        color =
                                            Accent,
                                        fontSize =
                                            11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            update(
                                d.copy(
                                    shareBridge =
                                        true,
                                    clipboardBridge =
                                        true,
                                    vibrationBridge =
                                        true,
                                    mediaPlayerBridge =
                                        true,
                                    qrScanner =
                                        true
                                )
                            )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "Tümünü aç"
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            update(
                                d.copy(
                                    shareBridge =
                                        false,
                                    clipboardBridge =
                                        false,
                                    vibrationBridge =
                                        false,
                                    mediaPlayerBridge =
                                        false,
                                    qrScanner =
                                        false
                                )
                            )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "Tümünü kapat"
                        )
                    }
                }
            }

            item {
                Text(
                    "Android API'leri",
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        14.sp
                )
            }

            item {
                FeatureToggleCard(
                    title =
                        "Paylaşım",
                    description =
                        "Web içeriğinden Android paylaşım ekranını açmayı sağlar.",
                    checked =
                        d.shareBridge,
                    recommended =
                        true
                ) {
                    update(
                        d.copy(
                            shareBridge =
                                it
                        )
                    )
                }
            }

            item {
                FeatureToggleCard(
                    title =
                        "Panoya kopyalama",
                    description =
                        "Web uygulamasının metni Android panosuna kopyalayabilmesini sağlar.",
                    checked =
                        d.clipboardBridge,
                    recommended =
                        true
                ) {
                    update(
                        d.copy(
                            clipboardBridge =
                                it
                        )
                    )
                }
            }

            item {
                FeatureToggleCard(
                    title =
                        "Titreşim / Haptic",
                    description =
                        "Web uygulamasından kısa titreşim ve haptic geri bildirim çalıştırır.",
                    checked =
                        d.vibrationBridge,
                    permission =
                        "Titreşim"
                ) {
                    update(
                        d.copy(
                            vibrationBridge =
                                it
                        )
                    )
                }
            }

            item {
                FeatureToggleCard(
                    title =
                        "🎵 Medya Oynatıcı / Arka Plan Ses",
                    description =
                        "Müzik, radyo ve podcast uygulamaları için Android medya oturumu ve arka plan oynatma altyapısını etkinleştirir.",
                    checked =
                        d.mediaPlayerBridge,
                    permission =
                        "Medya bildirimi"
                ) {
                    update(
                        d.copy(
                            mediaPlayerBridge =
                                it
                        )
                    )
                }
            }

            if (
                d.mediaPlayerBridge
            ) {
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Card2
                            ),
                        shape =
                            RoundedCornerShape(
                                18.dp
                            ),
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(if (formCompact) 12.dp else 16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    6.dp
                                )
                        ) {
                            Text(
                                "Android Media Session",
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                "• Arka planda ses",
                                fontSize =
                                    12.sp
                            )

                            Text(
                                "• Bildirim paneli medya kontrolleri",
                                fontSize =
                                    12.sp
                            )

                            Text(
                                "• Kilit ekranı kontrolleri",
                                fontSize =
                                    12.sp
                            )

                            Text(
                                "• Bluetooth / araç medya tuşları",
                                fontSize =
                                    12.sp
                            )

                            Text(
                                "Sonraki aşamada Media3 runtime bağlanacak.",
                                color =
                                    Accent,
                                fontSize =
                                    11.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (formCompact) 12.dp else 16.dp),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    5.dp
                                )
                        ) {
                            Text(
                                "QR / Barkod Tarayıcı",
                                fontWeight =
                                    FontWeight.Medium
                            )

                            Text(
                                "QR kod ve barkod taramayı Android üzerinden açar.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp,
                                lineHeight =
                                    17.sp
                            )

                            Text(
                                "⚡ Normal FAST BUILD destekli",
                                color =
                                    Accent,
                                fontSize =
                                    11.sp,
                                fontWeight =
                                    FontWeight.Medium
                            )
                        }

                        Switch(
                            checked =
                                d.qrScanner,
                            onCheckedChange = {
                                update(
                                    d.copy(
                                        qrScanner =
                                            it
                                    )
                                )
                            }
                        )
                    }
                }
            }

            item {
                NoteCard(
                    "Web tarafında özellikler window.AppForge API'si üzerinden kullanılabilir. QR sonucu event olarak web sayfasına iletilir."
                )
            }
        } else {
            item {
                NoteCard(
                    "Android Bridge özelliklerini kullanmak için önce JavaScript Bridge'i aç."
                )
            }
        }
    }
}

@Composable
private fun MonetizationStep(
    draft: ProjectDraft,
    update: (ProjectDraft) -> Unit,
    onPickFirebase: () -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val admobConfigured =
        !draft.admobEnabled ||
        draft.admobAppId
            .startsWith(
                "ca-app-pub-"
            )

    val billingProductCount =
        (
            draft.billingProductIds
                .split(",") +
            draft.billingSubscriptionIds
                .split(",") +
            draft.consumableProductIds
                .split(",")
        )
            .map {
                it.trim()
            }
            .count {
                it.isNotBlank()
            }

    val firebaseEnabled =
        draft.firebaseAnalyticsEnabled ||
        draft.firebaseCrashlyticsEnabled ||
        draft.firebaseMessagingEnabled

    val firebaseConfigured =
        !firebaseEnabled ||
        draft.firebaseConfigName
            .isNotBlank()

    val enabledServiceCount =
        listOf(
            draft.admobEnabled,
            draft.billingEnabled,
            draft.firebaseAnalyticsEnabled,
            draft.firebaseCrashlyticsEnabled,
            draft.firebaseMessagingEnabled
        ).count {
            it
        }

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
    ) {
        item {
            Section(
                "6. Para Kazanma + Firebase",
                "AdMob, Billing, Analytics, Crashlytics ve Cloud Messaging."
            )
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Card2
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp)
                ) {
                    Text(
                        "Production servisleri",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "$enabledServiceCount / 5 servis açık",
                        color =
                            TextSecondary,
                        fontSize =
                            13.sp
                    )

                    Text(
                        if (
                            admobConfigured &&
                            firebaseConfigured
                        ) {
                            "✓ Yapılandırma durumu iyi"
                        } else {
                            "⚠ Eksik yapılandırma var"
                        },
                        color =
                            if (
                                admobConfigured &&
                                firebaseConfigured
                            ) {
                                Accent
                            } else {
                                TextSecondary
                            },
                        fontWeight =
                            FontWeight.Medium,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Bu servisler açık olduğunda derleme ek Android SDK'ları içerir.",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp,
                        lineHeight =
                            17.sp
                    )
                }
            }
        }

        item {
            Text(
                "Reklam",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            FeatureToggleCard(
                title =
                    "Google AdMob",
                description =
                    "Banner, geçiş ve ödüllü reklam SDK'sını uygulamaya ekler.",
                checked =
                    draft.admobEnabled
            ) {
                update(
                    draft.copy(
                        admobEnabled =
                            it
                    )
                )
            }
        }

        if (
            draft.admobEnabled
        ) {
            item {
                OutlinedTextField(
                    value =
                        draft.admobAppId,
                    onValueChange = {
                        update(
                            draft.copy(
                                admobAppId =
                                    it.trim()
                            )
                        )
                    },
                    label = {
                        Text(
                            "AdMob App ID"
                        )
                    },
                    placeholder = {
                        Text(
                            "ca-app-pub-...~..."
                        )
                    },
                    isError =
                        draft.admobAppId
                            .isNotBlank() &&
                        !draft.admobAppId
                            .startsWith(
                                "ca-app-pub-"
                            ),
                    supportingText = {
                        if (
                            draft.admobAppId
                                .isBlank()
                        ) {
                            Text(
                                "AdMob uygulama kimliğini gir."
                            )
                        } else if (
                            admobConfigured
                        ) {
                            Text(
                                "✓ App ID biçimi uygun"
                            )
                        } else {
                            Text(
                                "AdMob App ID ca-app-pub- ile başlamalı."
                            )
                        }
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        draft.admobBannerUnitId,
                    onValueChange = {
                        update(
                            draft.copy(
                                admobBannerUnitId =
                                    it.trim()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Banner Ad Unit ID"
                        )
                    },
                    placeholder = {
                        Text(
                            "İsteğe bağlı"
                        )
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        draft.admobInterstitialUnitId,
                    onValueChange = {
                        update(
                            draft.copy(
                                admobInterstitialUnitId =
                                    it.trim()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Geçiş reklamı Unit ID"
                        )
                    },
                    placeholder = {
                        Text(
                            "İsteğe bağlı"
                        )
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        draft.admobRewardedUnitId,
                    onValueChange = {
                        update(
                            draft.copy(
                                admobRewardedUnitId =
                                    it.trim()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Ödüllü reklam Unit ID"
                        )
                    },
                    placeholder = {
                        Text(
                            "İsteğe bağlı"
                        )
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                FeatureToggleCard(
                    title =
                        "UMP / GDPR izin yönetimi",
                    description =
                        "Gerekli bölgelerde reklam izni ve gizlilik onayı akışını etkinleştirir.",
                    checked =
                        draft.umpConsentEnabled,
                    recommended =
                        true
                ) {
                    update(
                        draft.copy(
                            umpConsentEnabled =
                                it
                        )
                    )
                }
            }

            item {
                NoteCard(
                    "Yayın öncesi reklam gösterimlerini ve UMP izin akışını gerçek cihazda test et."
                )
            }
        }

        item {
            Text(
                "Google Play",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            FeatureToggleCard(
                title =
                    "Google Play Billing",
                description =
                    "Tek seferlik ürünler, abonelikler ve uygulama içi satın almaları etkinleştirir.",
                checked =
                    draft.billingEnabled
            ) {
                update(
                    draft.copy(
                        billingEnabled =
                            it
                    )
                )
            }
        }

        if (
            draft.billingEnabled
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                5.dp
                            )
                    ) {
                        Text(
                            "Billing özeti",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "$billingProductCount ürün tanımlı",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                draft.purchaseVerificationUrl
                                    .startsWith(
                                        "https://"
                                    )
                            ) {
                                "✓ Sunucu doğrulaması tanımlı"
                            } else {
                                "○ Sunucu doğrulaması tanımlı değil"
                            },
                            fontSize =
                                12.sp
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value =
                        draft.billingProductIds,
                    onValueChange = {
                        update(
                            draft.copy(
                                billingProductIds =
                                    it
                            )
                        )
                    },
                    label = {
                        Text(
                            "Tek seferlik ürün ID'leri"
                        )
                    },
                    supportingText = {
                        Text(
                            "Örn: premium,remove_ads"
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        draft.billingSubscriptionIds,
                    onValueChange = {
                        update(
                            draft.copy(
                                billingSubscriptionIds =
                                    it
                            )
                        )
                    },
                    label = {
                        Text(
                            "Abonelik ID'leri"
                        )
                    },
                    supportingText = {
                        Text(
                            "Örn: pro_monthly,pro_yearly"
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        draft.consumableProductIds,
                    onValueChange = {
                        update(
                            draft.copy(
                                consumableProductIds =
                                    it
                            )
                        )
                    },
                    label = {
                        Text(
                            "Tüketilebilir ürün ID'leri"
                        )
                    },
                    supportingText = {
                        Text(
                            "Virgülle ayır."
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        draft.removeAdsProductId,
                    onValueChange = {
                        update(
                            draft.copy(
                                removeAdsProductId =
                                    it.trim()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Reklam kaldırma ürün ID"
                        )
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        draft.purchaseVerificationUrl,
                    onValueChange = {
                        update(
                            draft.copy(
                                purchaseVerificationUrl =
                                    it.trim()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Satın alma doğrulama URL"
                        )
                    },
                    placeholder = {
                        Text(
                            "https://api.site.com/api/verify-purchase"
                        )
                    },
                    isError =
                        draft.purchaseVerificationUrl
                            .isNotBlank() &&
                        !draft.purchaseVerificationUrl
                            .startsWith(
                                "https://"
                            ),
                    supportingText = {
                        if (
                            draft.purchaseVerificationUrl
                                .isBlank()
                        ) {
                            Text(
                                "Production için sunucu tarafı doğrulama önerilir."
                            )
                        } else if (
                            draft.purchaseVerificationUrl
                                .startsWith(
                                    "https://"
                                )
                        ) {
                            Text(
                                "✓ HTTPS doğrulama adresi"
                            )
                        } else {
                            Text(
                                "Doğrulama adresi HTTPS olmalı."
                            )
                        }
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Text(
                "Firebase",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            FeatureToggleCard(
                title =
                    "Firebase Analytics",
                description =
                    "Uygulama kullanım olaylarını Firebase Analytics ile ölçer.",
                checked =
                    draft.firebaseAnalyticsEnabled
            ) {
                update(
                    draft.copy(
                        firebaseAnalyticsEnabled =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Firebase Crashlytics",
                description =
                    "Uygulama çökmelerini ve hata raporlarını Firebase Crashlytics'e gönderir.",
                checked =
                    draft.firebaseCrashlyticsEnabled
            ) {
                update(
                    draft.copy(
                        firebaseCrashlyticsEnabled =
                            it
                    )
                )
            }
        }

        item {
            FeatureToggleCard(
                title =
                    "Firebase Cloud Messaging",
                description =
                    "Push bildirimlerini, foreground mesajlarını ve data mesajlarını FCM ile alır.",
                checked =
                    draft.firebaseMessagingEnabled
            ) {
                update(
                    draft.copy(
                        firebaseMessagingEnabled =
                            it,
                        notifications =
                            if (it) {
                                true
                            } else {
                                draft.notifications
                            }
                    )
                )
            }
        }

        if (
            firebaseEnabled
        ) {
            item {
                Button(
                    onClick =
                        onPickFirebase,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (
                            draft.firebaseConfigName
                                .isBlank()
                        ) {
                            "google-services.json SEÇ"
                        } else {
                            "✓ Firebase yapılandırması seçildi"
                        }
                    )
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            "Firebase durumu",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            if (
                                draft.firebaseAnalyticsEnabled
                            ) {
                                "✓ Analytics açık"
                            } else {
                                "○ Analytics kapalı"
                            },
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                draft.firebaseCrashlyticsEnabled
                            ) {
                                "✓ Crashlytics açık"
                            } else {
                                "○ Crashlytics kapalı"
                            },
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                draft.firebaseMessagingEnabled
                            ) {
                                "✓ Cloud Messaging açık"
                            } else {
                                "○ Cloud Messaging kapalı"
                            },
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                firebaseConfigured
                            ) {
                                "✓ google-services.json hazır"
                            } else {
                                "⚠ google-services.json gerekli"
                            },
                            color =
                                if (
                                    firebaseConfigured
                                ) {
                                    Accent
                                } else {
                                    TextSecondary
                                },
                            fontSize =
                                12.sp
                        )
                    }
                }
            }
        }

        if (
            enabledServiceCount == 0
        ) {
            item {
                NoteCard(
                    "AdMob, Billing ve Firebase kapalı. Bu bölüm isteğe bağlıdır."
                )
            }
        }
    }
}

@Composable
private fun DeepLinkStep(
    d: ProjectDraft,
    update: (ProjectDraft) -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val schemeRegex =
        Regex(
            """^[a-z][a-z0-9+.-]*$"""
        )

    val schemeValid =
        d.deepLinkScheme.isNotBlank() &&
        schemeRegex.matches(
            d.deepLinkScheme
        )

    val hostValid =
        d.deepLinkHost.isNotBlank() &&
        !d.deepLinkHost.contains(
            " "
        )

    val normalizedPath =
        d.deepLinkPathPrefix
            .trim()
            .let {
                when {
                    it.isBlank() ->
                        "/"

                    it.startsWith("/") ->
                        it

                    else ->
                        "/$it"
                }
            }

    val deepLinkReady =
        !d.deepLinkEnabled ||
        (
            schemeValid &&
            hostValid
        )

    val exampleLink =
        if (
            schemeValid &&
            hostValid
        ) {
            "${d.deepLinkScheme}://${d.deepLinkHost}$normalizedPath"
        } else {
            "myapp://example.com/"
        }

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
    ) {
        item {
            Section(
                "7. Deep Link",
                "Web bağlantılarından uygulamanın belirli ekranlarını aç."
            )
        }

        item {
            FeatureToggleCard(
                title =
                    "Deep Link aktif",
                description =
                    "Belirlediğin bağlantılar açıldığında Android uygulamasının çalışmasını sağlar.",
                checked =
                    d.deepLinkEnabled
            ) {
                update(
                    d.copy(
                        deepLinkEnabled =
                            it
                    )
                )
            }
        }

        if (
            d.deepLinkEnabled
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
                    ) {
                        Text(
                            "Deep Link durumu",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            if (
                                schemeValid
                            ) {
                                "✓ Scheme geçerli"
                            } else {
                                "○ Scheme gerekli"
                            },
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                hostValid
                            ) {
                                "✓ Host geçerli"
                            } else {
                                "○ Host gerekli"
                            },
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                deepLinkReady
                            ) {
                                "✅ Yapılandırma hazır"
                            } else {
                                "Eksik alanları tamamla."
                            },
                            color =
                                if (
                                    deepLinkReady
                                ) {
                                    Accent
                                } else {
                                    TextSecondary
                                },
                            fontWeight =
                                FontWeight.Medium,
                            fontSize =
                                12.sp
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value =
                        d.deepLinkScheme,
                    onValueChange = {
                        update(
                            d.copy(
                                deepLinkScheme =
                                    it
                                        .trim()
                                        .lowercase()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Scheme"
                        )
                    },
                    placeholder = {
                        Text(
                            "myapp"
                        )
                    },
                    supportingText = {
                        if (
                            d.deepLinkScheme.isBlank()
                        ) {
                            Text(
                                "Örn: myapp"
                            )
                        } else if (
                            schemeValid
                        ) {
                            Text(
                                "✓ Scheme geçerli"
                            )
                        } else {
                            Text(
                                "Küçük harfle başlamalı; boşluk içeremez."
                            )
                        }
                    },
                    isError =
                        d.deepLinkScheme
                            .isNotBlank() &&
                        !schemeValid,
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        d.deepLinkHost,
                    onValueChange = {
                        update(
                            d.copy(
                                deepLinkHost =
                                    it
                                        .trim()
                                        .lowercase()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Host"
                        )
                    },
                    placeholder = {
                        Text(
                            "example.com"
                        )
                    },
                    supportingText = {
                        if (
                            d.deepLinkHost.isBlank()
                        ) {
                            Text(
                                "Örn: example.com"
                            )
                        } else if (
                            hostValid
                        ) {
                            Text(
                                "✓ Host geçerli"
                            )
                        } else {
                            Text(
                                "Host boşluk içeremez."
                            )
                        }
                    },
                    isError =
                        d.deepLinkHost
                            .isNotBlank() &&
                        !hostValid,
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        d.deepLinkPathPrefix,
                    onValueChange = {
                        update(
                            d.copy(
                                deepLinkPathPrefix =
                                    it
                            )
                        )
                    },
                    label = {
                        Text(
                            "Path prefix"
                        )
                    },
                    placeholder = {
                        Text(
                            "/"
                        )
                    },
                    supportingText = {
                        Text(
                            "Örn: /urun veya /profil"
                        )
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
                    ) {
                        Text(
                            "Bağlantı önizlemesi",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            exampleLink,
                            color =
                                Accent,
                            fontSize =
                                13.sp
                        )

                        Text(
                            "Bu yapıya uyan bağlantılar uygulamayı açabilir.",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )
                    }
                }
            }

            item {
                NoteCard(
                    "Google doğrulanmış HTTPS App Links farklı bir yapılandırmadır. Bu bölüm özel scheme tabanlı Deep Link içindir."
                )
            }
        } else {
            item {
                NoteCard(
                    "Deep Link kapalı. Uygulama normal şekilde açılmaya devam eder."
                )
            }
        }
    }
}

@Composable
private fun SigningStep(
    d: ProjectDraft,
    update: (ProjectDraft) -> Unit,
    onPickKeystore: () -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val customSigning =
        d.signingMode ==
            SigningMode.CUSTOM

    val keystoreReady =
        d.keystoreName.isNotBlank()

    val aliasReady =
        d.keyAlias.isNotBlank()

    val storePasswordReady =
        d.storePassword.isNotBlank()

    val keyPasswordReady =
        d.keyPassword.isNotBlank()

    val releaseReady =
        customSigning &&
        keystoreReady &&
        aliasReady &&
        storePasswordReady &&
        keyPasswordReady

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
    ) {
        item {
            Section(
                "8. İmzalama",
                "Debug test imzası veya kendi release keystore'un."
            )
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Card2
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
                ) {
                    Text(
                        "İmzalama durumu",
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (
                        customSigning
                    ) {
                        Text(
                            if (
                                releaseReady
                            ) {
                                "✅ Release imzası hazır"
                            } else {
                                "⚠ Release bilgileri eksik"
                            },
                            color =
                                if (
                                    releaseReady
                                ) {
                                    Accent
                                } else {
                                    TextSecondary
                                },
                            fontWeight =
                                FontWeight.Medium,
                            fontSize =
                                13.sp
                        )

                        Text(
                            "Google Play için kendi keystore'un kullanılacak.",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )
                    } else {
                        Text(
                            "⚠ Debug signing seçili",
                            fontWeight =
                                FontWeight.Medium,
                            fontSize =
                                13.sp
                        )

                        Text(
                            "Debug imzası test içindir. Play Store production yayını için release keystore kullan.",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp,
                            lineHeight =
                                17.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                "İmzalama türü",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement =
                    Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp)
            ) {
                FilterChip(
                    selected =
                        d.signingMode ==
                            SigningMode.DEBUG,
                    onClick = {
                        update(
                            d.copy(
                                signingMode =
                                    SigningMode.DEBUG
                            )
                        )
                    },
                    label = {
                        Text(
                            "Debug"
                        )
                    }
                )

                FilterChip(
                    selected =
                        d.signingMode ==
                            SigningMode.CUSTOM,
                    onClick = {
                        update(
                            d.copy(
                                signingMode =
                                    SigningMode.CUSTOM
                            )
                        )
                    },
                    label = {
                        Text(
                            "Release Keystore"
                        )
                    }
                )
            }
        }

        if (
            customSigning
        ) {
            item {
                Button(
                    onClick =
                        onPickKeystore,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (
                            d.keystoreName.isBlank()
                        ) {
                            "JKS / KEYSTORE SEÇ"
                        } else {
                            "✓ Keystore seçildi"
                        }
                    )
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                6.dp
                            )
                    ) {
                        Text(
                            "Release kontrolü",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            if (
                                keystoreReady
                            ) {
                                "✓ Keystore hazır"
                            } else {
                                "○ Keystore gerekli"
                            },
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                aliasReady
                            ) {
                                "✓ Key alias hazır"
                            } else {
                                "○ Key alias gerekli"
                            },
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                storePasswordReady
                            ) {
                                "✓ Store password hazır"
                            } else {
                                "○ Store password gerekli"
                            },
                            fontSize =
                                12.sp
                        )

                        Text(
                            if (
                                keyPasswordReady
                            ) {
                                "✓ Key password hazır"
                            } else {
                                "○ Key password gerekli"
                            },
                            fontSize =
                                12.sp
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value =
                        d.keyAlias,
                    onValueChange = {
                        update(
                            d.copy(
                                keyAlias =
                                    it.trim()
                            )
                        )
                    },
                    label = {
                        Text(
                            "Key alias"
                        )
                    },
                    placeholder = {
                        Text(
                            "Örn: upload"
                        )
                    },
                    supportingText = {
                        if (
                            aliasReady
                        ) {
                            Text(
                                "✓ Alias girildi"
                            )
                        } else {
                            Text(
                                "Keystore oluştururken verdiğin alias."
                            )
                        }
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        d.storePassword,
                    onValueChange = {
                        update(
                            d.copy(
                                storePassword =
                                    it
                            )
                        )
                    },
                    label = {
                        Text(
                            "Store password"
                        )
                    },
                    supportingText = {
                        Text(
                            if (
                                storePasswordReady
                            ) {
                                "✓ Girildi"
                            } else {
                                "Keystore parolası gerekli."
                            }
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value =
                        d.keyPassword,
                    onValueChange = {
                        update(
                            d.copy(
                                keyPassword =
                                    it
                            )
                        )
                    },
                    label = {
                        Text(
                            "Key password"
                        )
                    },
                    supportingText = {
                        Text(
                            if (
                                keyPasswordReady
                            ) {
                                "✓ Girildi"
                            } else {
                                "Alias anahtar parolası gerekli."
                            }
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            item {
                NoteCard(
                    "Keystore dosyanı ve parolalarını güvenli bir yerde yedekle. Aynı Play Store uygulamasını gelecekte güncellemek için aynı imzalama anahtarına ihtiyaç duyarsın."
                )
            }
        } else {
            item {
                NoteCard(
                    "Debug signing ile APK ve AAB test derlemeleri oluşturabilirsin; production Play Store yayını için Release Keystore seç."
                )
            }
        }
    }
}

@Composable
private fun BuildSettingsStep(
    draft: ProjectDraft,
    update: (ProjectDraft) -> Unit,
    serverUrl: String,
    apiKey: String,
    statusMessage: String,
    onServerUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onSave: () -> Unit
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val apiKeyReady =
        apiKey.isNotBlank()

    val releaseSigning =
        draft.signingMode ==
            SigningMode.CUSTOM

    val outputLabel =
        when (
            draft.buildOutput
        ) {
            "exe" ->
                "Windows EXE"

            "aab" ->
                "AAB"

            "both" ->
                "APK + AAB"

            else ->
                "APK"
        }

    val buildRouteText =
        when (
            draft.buildOutput
        ) {
            "exe" ->
                "EXE • Windows x64 • Electron Portable"

            "aab" ->
                "AAB • Gradle bundleRelease"

            "both" ->
                "BOTH • FAST APK uygunsa Hybrid + AAB"

            else ->
                "APK • Uygunsa FAST BUILD"
        }

    val productionFeatureCount =
        listOf(
            draft.admobEnabled,
            draft.billingEnabled,
            draft.firebaseAnalyticsEnabled,
            draft.firebaseCrashlyticsEnabled,
            draft.firebaseMessagingEnabled,
            draft.mediaPlayerBridge,
            draft.qrScanner
        ).count {
            it
        }

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
    ) {
        item {
            Section(
                "9. Özet & Derleme",
                "Son ayarlarını kontrol et ve çıktı türünü seç."
            )
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Card2
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    Text(
                        "Proje özeti",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "Uygulama: ${draft.appName.ifBlank { "—" }}",
                        fontSize =
                            13.sp
                    )

                    Text(
                        "Paket: ${draft.packageName}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Sürüm: ${draft.versionName} (${draft.versionCode})",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Kaynak: ${
                            if (
                                draft.sourceMode ==
                                    SourceMode.LOCAL
                            ) {
                                "HTML / ZIP"
                            } else {
                                "Web URL"
                            }
                        }",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "İzinler: ${
                            listOf(
                                draft.camera,
                                draft.microphone,
                                draft.location,
                                draft.notifications,
                                draft.networkState,
                                draft.wakeLock,
                                draft.nfc
                            ).count {
                                it
                            }
                        } isteğe bağlı",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "JavaScript: ${onOff(draft.webJavaScriptEnabled)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "DOM Storage: ${onOff(draft.webDomStorageEnabled)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Zoom: ${onOff(draft.webZoomEnabled)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Autoplay: ${onOff(draft.webMediaAutoplayEnabled)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Mixed Content: ${onOff(draft.webMixedContentAllowed)}",
                        color =
                            if (
                                draft.webMixedContentAllowed
                            ) {
                                Accent
                            } else {
                                TextSecondary
                            },
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Native Bridge: ${onOff(draft.javascriptBridge)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Media3: ${onOff(draft.mediaPlayerBridge)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )
                }
            }
        }

        item {
            Text(
                "Çıktı türü",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                listOf(
                    "apk",
                    "aab",
                    "both",
                    "exe"
                ).forEach {
                    output ->
                    val windowsExeCompatible =
                        draft.sourceMode ==
                            SourceMode.URL ||
                        draft.sourceBuildEngine
                            .trim()
                            .lowercase() in
                            setOf(
                                "webview-static",
                                "node-web"
                            )

                    val outputEnabled =
                        output != "exe" ||
                            windowsExeCompatible

                    FilterChip(
                        selected =
                            draft.buildOutput ==
                                output,
                        enabled =
                            outputEnabled,
                        onClick = {
                            if (
                                outputEnabled
                            ) {
                                update(
                                    draft.copy(
                                        buildOutput =
                                            output
                                    )
                                )
                            }
                        },
                        label = {
                            Text(
                                output.uppercase()
                            )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    )
                }
            }
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Card2
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp)
                ) {
                    Text(
                        "Çıktı bilgisi",
                        fontWeight =
                            FontWeight.Bold
                    )

                    when (
                        draft.buildOutput
                    ) {
                        "exe" -> {
                            Text(
                                "Windows EXE",
                                fontWeight =
                                    FontWeight.Medium
                            )

                            Text(
                                "Windows 10/11 x64 için kurulum gerektirmeyen portable EXE oluşturur.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )

                            Text(
                                "Windows 10/11 x64 için taşınabilir çıktı hazırlanır.",
                                color =
                                    Accent,
                                fontSize =
                                    11.sp
                            )
                        }

                        "aab" -> {
                            Text(
                                "AAB",
                                fontWeight =
                                    FontWeight.Medium
                            )

                            Text(
                                "Google Play Console'a yüklemek için Android App Bundle oluşturur.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )
                        }

                        "both" -> {
                            Text(
                                "APK + AAB",
                                fontWeight =
                                    FontWeight.Medium
                            )

                            Text(
                                "Test/kurulum için APK ve Play Store için AAB birlikte oluşturulur.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )

                            Text(
                                "APK ve AAB birlikte hazırlanır.",
                                color =
                                    Accent,
                                fontSize =
                                    11.sp
                            )
                        }

                        else -> {
                            Text(
                                "APK",
                                fontWeight =
                                    FontWeight.Medium
                            )

                            Text(
                                "Telefona doğrudan kurulabilen APK oluşturur.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )

                            Text(
                                "APK doğrudan kurulum için hazırlanır.",
                                color =
                                    Accent,
                                fontSize =
                                    11.sp
                            )
                        }
                    }
                }
            }
        }

        if (
            draft.buildOutput == "exe" &&
            draft.sourceMode !=
                SourceMode.URL &&
            draft.sourceBuildEngine
                .trim()
                .lowercase() !in
                setOf(
                    "webview-static",
                    "node-web"
                )
        ) {
            item {
                NoteCard(
                    "Windows EXE bu proje türüyle uyumlu değil. " +
                    "Native Android/Flutter/React Native kaynakları için APK/AAB kullan. " +
                    "EXE için web tabanlı veya HTTPS URL kaynağı seç."
                )
            }
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Card2
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    Text(
                        "Production özeti",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        if (
                            releaseSigning
                        ) {
                            "✓ Release Keystore"
                        } else {
                            "⚠ Debug signing"
                        },
                        fontSize =
                            12.sp
                    )

                    Text(
                        "$productionFeatureCount production özelliği aktif",
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Firebase: ${
                            if (
                                draft.firebaseConfigUri != null
                            ) {
                                "Hazır"
                            } else {
                                "Yok"
                            }
                        }",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Billing: ${onOff(draft.billingEnabled)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Crashlytics: ${onOff(draft.firebaseCrashlyticsEnabled)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "FCM: ${onOff(draft.firebaseMessagingEnabled)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "Medya oynatıcı: ${onOff(draft.mediaPlayerBridge)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "QR/Barkod: ${onOff(draft.qrScanner)}",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )
                }
            }
        }

        if (
            !releaseSigning &&
            (
                draft.buildOutput ==
                    "aab" ||
                draft.buildOutput ==
                    "both"
            )
        ) {
            item {
                NoteCard(
                    "Play Store'a göndereceğin AAB için Release Keystore kullanman önerilir."
                )
            }
        }

        item {
            Button(
                onClick =
                    onSave,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    "PROJEYİ KAYDET / GÜNCELLE"
                )
            }
        }

        if (
            statusMessage.isNotBlank()
        ) {
            item {
                NoteCard(
                    statusMessage
                )
            }
        }

        item {
            NoteCard(
                "Ayarları kaydettikten sonra Devam ile son derleme ve Play Store ön-kontrol ekranına geçebilirsin."
            )
        }
    }
}

@Composable
private fun BuildStep(
    draft: ProjectDraft,
    onDraftChange: (ProjectDraft) -> Unit,
    onRetryBuild: (ProjectDraft) -> Unit,
    status: String,
    progress: Int,
    buildElapsedMs: Long,
    buildTimerRunning: Boolean,
    logs: List<String>,
    preflight: List<String>,
    buildId: String?,
    buildNo: Long?,
    appName: String,
    serverUrl: String,
    apiKey: String,
    apkUrl: String?,
    aabUrl: String?,
    exeUrl: String?,
    buildOutput: String,
    queuePosition: Int?,
    queueAhead: Int?,
    queueWorkerSlots: Int,
    queueEtaSeconds: Int?,
    queueEstimate: String?
) {
    val formCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var downloadMessage by
        remember {
            mutableStateOf("")
        }

    /*
     * Android DownloadManager bazı cihazlarda büyük .exe
     * dosyalarını sessizce reddedebiliyor.
     *
     * EXE için Storage Access Framework kullan:
     * kullanıcı hedef dosyayı seçer ve AppForge çıktıyı
     * doğrudan o URI'ye stream eder.
     */
    val exeSaveLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.CreateDocument(
                    "application/octet-stream"
                )
        ) {
            destination: Uri? ->

            val id =
                buildId

            if (
                destination != null &&
                id != null
            ) {
                scope.launch {
                    try {
                        downloadMessage =
                            "Windows EXE indiriliyor..."

                        val ticket =
                            withContext(
                                Dispatchers.IO
                            ) {
                                BuildApiClient(
                                    context,
                                    serverUrl,
                                    apiKey
                                ).createDownloadTicket(
                                    id,
                                    "exe"
                                )
                            }

                        withContext(
                            Dispatchers.IO
                        ) {
                            downloadArtifactToUri(
                                context,
                                ticket.url,
                                destination
                            )
                        }

                        downloadMessage =
                            "✅ Windows EXE başarıyla kaydedildi."

                    } catch (
                        t: Throwable
                    ) {
                        downloadMessage =
                            "EXE indirme hatası: ${t.message}"
                    }
                }
            }
        }

    var apkCachedPath by
        remember(buildId) {
            mutableStateOf<String?>(
                null
            )
        }

    var apkDownloading by
        remember(buildId) {
            mutableStateOf(
                false
            )
        }

    var showLogs by
        remember(buildId) {
            mutableStateOf(false)
        }

    var showCancelConfirm by
        remember(buildId) {
            mutableStateOf(false)
        }

    var cancelInProgress by
        remember(buildId) {
            mutableStateOf(false)
        }

    var cancelMessage by
        remember(buildId) {
            mutableStateOf("")
        }

    val backendProgress =
        progress.coerceIn(
            0,
            100
        )

    val normalizedStatus =
        status
            .trim()
            .lowercase()

    val progressTerminalFailure =
        normalizedStatus ==
            "failed" ||
        normalizedStatus ==
            "cancelled" ||
        normalizedStatus ==
            "canceled" ||
        normalizedStatus
            .startsWith(
                "hata:"
            )

    var flowingProgress by
        remember(
            buildId
        ) {
            mutableIntStateOf(
                if (
                    backendProgress > 0
                ) {
                    1
                } else {
                    0
                }
            )
        }

    LaunchedEffect(
        buildId,
        backendProgress,
        normalizedStatus
    ) {
        val active =
            backendProgress > 0 ||
            normalizedStatus ==
                "queued" ||
            normalizedStatus ==
                "building" ||
            normalizedStatus ==
                "success"

        if (
            !active
        ) {
            flowingProgress =
                0

            return@LaunchedEffect
        }

        if (
            flowingProgress <= 0
        ) {
            flowingProgress =
                1
        }

        if (
            normalizedStatus ==
                "success"
        ) {
            while (
                flowingProgress <
                    100
            ) {
                delay(
                    18L
                )

                flowingProgress +=
                    1
            }

            return@LaunchedEffect
        }

        if (
            progressTerminalFailure
        ) {
            return@LaunchedEffect
        }

        while (
            flowingProgress <
                99
        ) {
            val serverTarget =
                backendProgress
                    .coerceIn(
                        1,
                        99
                    )

            val waitMs =
                when {
                    flowingProgress <
                        serverTarget ->
                        40L

                    flowingProgress <
                        60 ->
                        850L

                    flowingProgress <
                        85 ->
                        1_150L

                    else ->
                        1_650L
                }

            delay(
                waitMs
            )

            flowingProgress +=
                1
        }
    }

    val safeProgress =
        flowingProgress
            .coerceIn(
                0,
                100
            )

    val queueWaitLabel =
        when {
            queueEtaSeconds == null ->
                null

            queueEtaSeconds <= 0 ->
                "Çok yakında"

            queueEtaSeconds < 60 ->
                "< 1 dk"

            queueEtaSeconds < 3600 ->
                "${
                    (
                        queueEtaSeconds +
                        59
                    ) / 60
                } dk"

            else -> {
                val totalMinutes =
                    (
                        queueEtaSeconds +
                        59
                    ) / 60

                val hours =
                    totalMinutes /
                        60

                val minutes =
                    totalMinutes %
                        60

                if (
                    minutes == 0
                ) {
                    "$hours sa"
                } else {
                    "$hours sa $minutes dk"
                }
            }
        }

    val statusLabel =
        when (
            normalizedStatus
        ) {
            "success" ->
                "✅ Başarılı"

            "failed" ->
                "❌ Başarısız"

            "cancelled",
            "canceled" ->
                "⛔ İptal edildi"

            "queued" ->
                "⏳ Sırada"

            "running",
            "building",
            "processing" ->
                "⚙️ Derleniyor"

            else ->
                status.ifBlank {
                    "Hazırlanıyor"
                }
        }

    val stageLabel =
        when {
            normalizedStatus ==
                "failed" ||
            normalizedStatus
                .startsWith(
                    "hata:"
                ) ->
                "Derleme başarısız"

            normalizedStatus ==
                "cancelled" ||
            normalizedStatus ==
                "canceled" ->
                "Derleme iptal edildi"

            normalizedStatus ==
                "success" ->
                "Tamamlandı"

            backendProgress >= 100 ->
                "Tamamlandı"

            backendProgress >= 90 ->
                "Çıktılar hazırlanıyor"

            backendProgress >= 70 ->
                "Uygulama paketleniyor"

            backendProgress >= 40 ->
                "Kaynaklar derleniyor"

            backendProgress >= 15 ->
                "Proje hazırlanıyor"

            backendProgress > 0 ->
                "Build başlatılıyor"

            else ->
                "Bekleniyor"
        }

    val buildSucceeded =
        buildId != null &&
        normalizedStatus ==
            "success"

    val buildFailed =
        normalizedStatus ==
            "failed" ||
        normalizedStatus
            .startsWith(
                "hata:"
            )

    val availableOutputs =
        listOf(
            apkUrl,
            aabUrl,
            exeUrl
        ).count {
            it != null
        }

    val logsVisible =
        showLogs ||
        buildFailed

    val buildActive =
        buildId != null &&
        normalizedStatus !=
            "success" &&
        normalizedStatus !=
            "failed" &&
        normalizedStatus !=
            "cancelled" &&
        normalizedStatus !=
            "canceled" &&
        !buildFailed



    val userPreflight =
        remember(
            preflight
        ) {
            AppForgeUiSanitizer
                .preflight(
                    preflight
                )
        }


    val buildDiagnosis =
        remember(
            buildFailed,
            logs,
            preflight,
            status
        ) {
            if (
                buildFailed
            ) {
                AppForgeBuildErrorAdvisor
                    .diagnose(
                        logs = logs,
                        preflight = preflight,
                        status = status
                    )
            } else {
                null
            }
        }

    val safeFixPreview =
        remember(
            draft
        ) {
            AppForgeProjectAdvisor
                .applySafeFixes(
                    draft
                )
        }

    LaunchedEffect(
        normalizedStatus
    ) {
        when (
            normalizedStatus
        ) {
            "cancelled",
            "canceled" -> {
                if (
                    cancelMessage.isNotBlank()
                ) {
                    cancelMessage =
                        "⛔ Derleme iptal edildi."
                }
            }

            "success" -> {
                if (
                    cancelMessage.contains(
                        "İptal isteği"
                    )
                ) {
                    cancelMessage =
                        "ℹ️ Derleme iptal edilmeden önce tamamlandı."
                }
            }
        }
    }

    if (
        showCancelConfirm
    ) {
        AlertDialog(
            onDismissRequest = {
                if (
                    !cancelInProgress
                ) {
                    showCancelConfirm =
                        false
                }
            },
            title = {
                Text(
                    "Derlemeyi iptal et?"
                )
            },
            text = {
                Text(
                    "Çalışan build durdurulacak. " +
                    "Tamamlanmamış APK, AAB veya EXE çıktıları kullanılamaz."
                )
            },
            confirmButton = {
                Button(
                    enabled =
                        !cancelInProgress,
                    onClick = {
                        val id =
                            buildId

                        if (
                            id == null
                        ) {
                            showCancelConfirm =
                                false

                            return@Button
                        }

                        showCancelConfirm =
                            false

                        cancelInProgress =
                            true

                        cancelMessage =
                            "İptal isteği gönderiliyor..."

                        scope.launch {
                            try {
                                val cancelResult =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        BuildApiClient(
                                            context,
                                            serverUrl,
                                            apiKey
                                        ).cancelBuild(
                                            id
                                        )
                                    }

                                cancelMessage =
                                    when (
                                        cancelResult.status
                                    ) {
                                        "cancelled" ->
                                            "⛔ Derleme iptal edildi."

                                        "success" ->
                                            "ℹ️ Derleme iptal isteğinden önce tamamlanmış."

                                        "failed" ->
                                            "ℹ️ Derleme zaten başarısız olarak tamamlanmış."

                                        else ->
                                            "⛔ İptal isteği gönderildi • çalışan işlem durduruluyor."
                                    }
                            } catch (
                                t: Throwable
                            ) {
                                cancelMessage =
                                    if (
                                        isTransientBuildNetworkError(
                                            t
                                        )
                                    ) {
                                        "İptal isteği gönderilemedi • bağlantıyı kontrol edip tekrar dene."
                                    } else {
                                        "İptal hatası: ${t.message}"
                                    }
                            } finally {
                                cancelInProgress =
                                    false
                            }
                        }
                    }
                ) {
                    Text(
                        if (
                            cancelInProgress
                        ) {
                            "İPTAL EDİLİYOR..."
                        } else {
                            "EVET, İPTAL ET"
                        }
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled =
                        !cancelInProgress,
                    onClick = {
                        showCancelConfirm =
                            false
                    }
                ) {
                    Text(
                        "VAZGEÇ"
                    )
                }
            }
        )
    }

    LazyColumn(
        contentPadding =
            PaddingValues(if (formCompact) 12.dp else 20.dp),
        verticalArrangement =
            Arrangement.spacedBy(if (formCompact) 10.dp else 14.dp)
    ) {
        item {
            Section(
                "10. Derleme",
                "Derleme durumunu takip et, çıktıları indir ve ön kontrolleri incele."
            )
        }

        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Card2
                    ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(if (formCompact) 12.dp else 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (formCompact) 7.dp else 10.dp)
                ) {
                    Text(
                        "Derleme durumu",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        statusLabel,
                        color =
                            if (
                                buildSucceeded
                            ) {
                                Accent
                            } else {
                                TextSecondary
                            },
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            16.sp
                    )

                    Text(
                        "$stageLabel • %$safeProgress",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )

                    LinearProgressIndicator(
                        progress = {
                            safeProgress /
                                100f
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    if (
                        buildTimerRunning ||
                        buildElapsedMs > 0L
                    ) {
                        Text(
                            text =
                                if (
                                    buildTimerRunning
                                ) {
                                    "⏱ Geçen süre: " +
                                        formatBuildDuration(
                                            buildElapsedMs
                                        )
                                } else {
                                    "⏱ Toplam süre: " +
                                        formatBuildDuration(
                                            buildElapsedMs
                                        )
                                },
                            color =
                                Accent,
                            fontSize =
                                13.sp,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Spacer(
                            Modifier.height(
                                6.dp
                            )
                        )
                    }

                    if (
                        buildId != null
                    ) {
                        Text(
                            "Derleme No",
                            color =
                                TextSecondary,
                            fontSize =
                                11.sp
                        )

                        Text(
                            AppForgeBuildNumbers
                                .label(
                                    buildNo
                                ),
                            color =
                                Accent,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                13.sp
                        )
                    }

                    if (
                        normalizedStatus ==
                            "queued"
                    ) {
                        Spacer(
                            Modifier.height(
                                4.dp
                            )
                        )

                        HorizontalDivider()

                        Spacer(
                            Modifier.height(
                                4.dp
                            )
                        )

                        Text(
                            text =
                                queuePosition
                                    ?.let {
                                        "⏳ Sırada $it. build"
                                    }
                                    ?: "⏳ Kuyruk sırası hesaplanıyor...",
                            color =
                                Accent,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                14.sp
                        )

                        queueAhead
                            ?.let {
                                ahead ->

                                Text(
                                    text =
                                        if (
                                            ahead == 0
                                        ) {
                                            "Önünde başka build yok."
                                        } else {
                                            "Önünde $ahead build var."
                                        },
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        12.sp
                                )
                            }

                        Text(
                            text =
                                if (
                                    queueWorkerSlots >
                                    0
                                ) {
                                    "⚙ $queueWorkerSlots uygun build slotu aktif"
                                } else {
                                    "⚙ Uygun worker bekleniyor"
                                },
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )

                        queueWaitLabel
                            ?.let {
                                wait ->

                                Text(
                                    "≈ Tahmini bekleme: $wait",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        12.sp
                                )
                            }

                        if (
                            queueEstimate ==
                                "approximate"
                        ) {
                            Text(
                                "Süre worker yüküne ve daha yüksek öncelikli build'lere göre değişebilir.",
                                color =
                                    TextSecondary,
                                fontSize =
                                    10.sp,
                                lineHeight =
                                    14.sp
                            )
                        }
                    }

                    if (
                        logs.isNotEmpty() &&
                        buildActive
                    ) {
                        Text(
                            "Derleme işlemi devam ediyor.",
                            color =
                                TextSecondary,
                            fontSize =
                                11.sp,
                            lineHeight =
                                16.sp
                        )
                    }
                }
            }
        }

        if (
            buildActive
        ) {
            item {
                OutlinedButton(
                    enabled =
                        !cancelInProgress,
                    onClick = {
                        showCancelConfirm =
                            true
                    },
                    colors =
                        ButtonDefaults
                            .outlinedButtonColors(
                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .error
                            ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (
                            cancelInProgress
                        ) {
                            "İPTAL EDİLİYOR..."
                        } else {
                            "DERLEMEYİ İPTAL ET"
                        }
                    )
                }
            }
        }

        if (
            cancelMessage.isNotBlank()
        ) {
            item {
                NoteCard(
                    cancelMessage
                )
            }
        }

        if (
            userPreflight.isNotEmpty()
        ) {
            item {
                Text(
                    "Ön Kontroller",
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        14.sp
                )
            }

            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (formCompact) 6.dp else 8.dp)
                    ) {
                        Text(
                            "${userPreflight.size} kontrol tamamlandı",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )

                        userPreflight.forEach {
                            check ->
                            Text(
                                check,
                                fontSize =
                                    12.sp
                            )
                        }
                    }
                }
            }
        }

        if (
            buildSucceeded
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (formCompact) 5.dp else 7.dp)
                    ) {
                        Text(
                            "✅ Derleme tamamlandı",
                            color =
                                Accent,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "$availableOutputs çıktı indirilmeye hazır.",
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )

                        if (
                            apkUrl != null
                        ) {
                            Text(
                                "✓ APK hazır",
                                fontSize =
                                    12.sp
                            )
                        }

                        if (
                            aabUrl != null
                        ) {
                            Text(
                                "✓ AAB hazır",
                                fontSize =
                                    12.sp
                            )
                        }

                        if (
                            exeUrl != null
                        ) {
                            Text(
                                "✓ Windows EXE hazır",
                                fontSize =
                                    12.sp
                            )
                        }
                    }
                }
            }
        }

        if (
            buildFailed
        ) {
            item {
                val diagnosis =
                    buildDiagnosis

                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Color(0xFF28171B)
                        ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (formCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(if (formCompact) 7.dp else 10.dp)
                    ) {
                        Text(
                            "🧠 Build Hatası Asistanı",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                18.sp
                        )

                        if (
                            diagnosis !=
                                null
                        ) {
                            Text(
                                diagnosis.title,
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    Color(0xFFFFB4AB)
                            )

                            Text(
                                "Tanı güveni: %${diagnosis.confidence}",
                                color =
                                    TextSecondary,
                                fontSize =
                                    12.sp
                            )

                            Text(
                                "Neden?",
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    13.sp
                            )

                            Text(
                                diagnosis.reason,
                                color =
                                    TextSecondary,
                                lineHeight =
                                    19.sp
                            )

                            Text(
                                "Ne yapmalısın?",
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    13.sp
                            )

                            Text(
                                diagnosis.solution,
                                color =
                                    TextSecondary,
                                lineHeight =
                                    19.sp
                            )

                        } else {
                            Text(
                                "Derleme tamamlanamadı. Canlı log ayrıntıları aşağıda gösteriliyor.",
                                color =
                                    TextSecondary
                            )
                        }

                        if (
                            safeFixPreview
                                .changes
                                .isNotEmpty()
                        ) {
                            Button(
                                onClick = {
                                    val fixed =
                                        AppForgeProjectAdvisor
                                            .applySafeFixes(
                                                draft
                                            )

                                    onDraftChange(
                                        fixed.draft
                                    )

                                    onRetryBuild(
                                        fixed.draft
                                    )
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "⚡ GÜVENLİ DÜZELT VE TEKRAR DENE"
                                )
                            }

                            Text(
                                "Otomatik düzeltilecek: ${safeFixPreview.changes.joinToString(" • ")}",
                                color =
                                    TextSecondary,
                                fontSize =
                                    11.sp,
                                lineHeight =
                                    16.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onRetryBuild(
                                    draft
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "↻ AYNI AYARLARLA TEKRAR DENE"
                            )
                        }

                        Text(
                            "Keystore parolaları ve hassas hesap bilgileri tanı kartına taşınmaz.",
                            color =
                                TextSecondary,
                            fontSize =
                                10.sp
                        )
                    }
                }
            }
        }

        if (
            apkUrl != null
        ) {
            item {
                Button(
                    enabled =
                        !apkDownloading,
                    onClick = {
                        val id =
                            buildId
                                ?: return@Button

                        if (
                            apkDownloading
                        ) {
                            return@Button
                        }

                        scope.launch {
                            apkDownloading =
                                true

                            apkCachedPath =
                                null

                            downloadMessage =
                                "APK indiriliyor • bağlantı kuruluyor..."

                            try {
                                val result =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        val ticket =
                                            BuildApiClient(
                                                context,
                                                serverUrl,
                                                apiKey
                                            )
                                                .createDownloadTicket(
                                                    id,
                                                    "apk"
                                                )

                                        val fileName =
                                            artifactDownloadName(
                                                appName,
                                                AppForgeBuildNumbers.label(buildNo),
                                                "apk"
                                            )

                                        val apkFile =
                                            downloadApkToInstallerCache(
                                                context =
                                                    context,
                                                url =
                                                    ticket.url,
                                                fileName =
                                                    fileName
                                            )

                                        val published =
                                            runCatching {
                                                publishApkToDownloads(
                                                    context =
                                                        context,
                                                    sourceFile =
                                                        apkFile,
                                                    fileName =
                                                        fileName
                                                )
                                            }
                                                .getOrDefault(
                                                    false
                                                )

                                        Pair(
                                            apkFile,
                                            published
                                        )
                                    }

                                val apkFile =
                                    result.first

                                apkCachedPath =
                                    apkFile.absolutePath

                                val sizeMb =
                                    apkFile.length()
                                        .toDouble() /
                                    (
                                        1024.0 *
                                        1024.0
                                    )

                                downloadMessage =
                                    if (
                                        result.second
                                    ) {
                                        "✅ APK indirildi • " +
                                        String.format(
                                            "%.1f MB",
                                            sizeMb
                                        ) +
                                        " • Downloads klasörüne kaydedildi."
                                    } else {
                                        "✅ APK indirildi • " +
                                        String.format(
                                            "%.1f MB",
                                            sizeMb
                                        ) +
                                        " • Kuruluma hazır."
                                    }

                            } catch (
                                t: Throwable
                            ) {
                                downloadMessage =
                                    "❌ APK indirme hatası: " +
                                    (
                                        t.message
                                            ?: t.javaClass.simpleName
                                    )
                            } finally {
                                apkDownloading =
                                    false
                            }
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (
                            apkDownloading
                        ) {
                            "APK İNDİRİLİYOR..."
                        } else if (
                            apkCachedPath != null
                        ) {
                            "APK'YI TEKRAR İNDİR"
                        } else {
                            "APK'YI İNDİR"
                        }
                    )
                }
            }
        }

        if (
            apkCachedPath != null
        ) {
            item {
                Button(
                    onClick = {
                        val path =
                            apkCachedPath
                                ?: return@Button

                        downloadMessage =
                            installCachedApk(
                                context =
                                    context,
                                apkFile =
                                    File(
                                        path
                                    )
                            )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "APK'YI KUR"
                    )
                }
            }
        }

        if (
            aabUrl != null
        ) {
            item {
                Button(
                    onClick = {
                        val id =
                            buildId
                                ?: return@Button

                        scope.launch {
                            try {
                                val ticket =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        BuildApiClient(
                                            context,
                                            serverUrl,
                                            apiKey
                                        )
                                            .createDownloadTicket(
                                                id,
                                                "aab"
                                            )
                                    }

                                val request =
                                    DownloadManager.Request(
                                        Uri.parse(
                                            ticket.url
                                        )
                                    )
                                        .setTitle(
                                            "AppForge AAB"
                                        )
                                        .setDescription(
                                            "AAB indiriliyor"
                                        )
                                        .setMimeType(
                                            "application/octet-stream"
                                        )
                                        .setNotificationVisibility(
                                            DownloadManager.Request
                                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                        )
                                        .setAllowedOverMetered(
                                            true
                                        )
                                        .setAllowedOverRoaming(
                                            true
                                        )
                                        .setDestinationInExternalPublicDir(
                                            Environment.DIRECTORY_DOWNLOADS,
                                            "$APPFORGE_DOWNLOAD_FOLDER/${artifactDownloadName(
                                                appName,
                                                AppForgeBuildNumbers.label(buildNo),
                                                "aab"
                                            )}"
                                        )

                                val manager =
                                    context.getSystemService(
                                        Context.DOWNLOAD_SERVICE
                                    ) as DownloadManager

                                manager.enqueue(
                                    request
                                )

                                downloadMessage =
                                    "AAB indiriliyor • Downloads/AppForge Studio klasörüne kaydedilecek."
                            } catch (
                                t: Throwable
                            ) {
                                downloadMessage =
                                    "İndirme hatası: ${t.message}"
                            }
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "AAB'Yİ İNDİR"
                    )
                }
            }
        }

        if (
            exeUrl != null
        ) {
            item {
                Button(
                    onClick = {
                        val id =
                            buildId
                                ?: return@Button

                        val fileName =
                            artifactDownloadName(
                                                appName,
                                                AppForgeBuildNumbers.label(buildNo),
                                                "exe"
                                            )

                        if (
                            Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.Q
                        ) {
                            scope.launch {
                                try {
                                    downloadMessage =
                                        "Windows EXE indiriliyor..."

                                    val ticket =
                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            BuildApiClient(
                                                context,
                                                serverUrl,
                                                apiKey
                                            ).createDownloadTicket(
                                                id,
                                                "exe"
                                            )
                                        }

                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        downloadArtifactToDownloads(
                                            context,
                                            ticket.url,
                                            fileName
                                        )
                                    }

                                    downloadMessage =
                                        "✅ Windows EXE Downloads/AppForge Studio klasörüne kaydedildi."

                                } catch (
                                    t: Throwable
                                ) {
                                    downloadMessage =
                                        "EXE indirme hatası: ${t.message}"
                                }
                            }
                        } else {
                            exeSaveLauncher.launch(
                                fileName
                            )
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "WINDOWS EXE'Yİ İNDİR"
                    )
                }
            }
        }

        if (
            downloadMessage.isNotBlank()
        ) {
            item {
                NoteCard(
                    downloadMessage
                )
            }
        }


    }
}



private fun downloadArtifactToDownloads(
    context: Context,
    url: String,
    fileName: String
): Uri {

    require(
        Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
    ) {
        "Otomatik Downloads kaydı Android 10+ gerektirir."
    }

    val resolver =
        context.contentResolver

    val values =
        ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                fileName
            )

            put(
                MediaStore.MediaColumns.MIME_TYPE,
                "application/octet-stream"
            )

            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$APPFORGE_DOWNLOAD_FOLDER"
            )

            put(
                MediaStore.MediaColumns.IS_PENDING,
                1
            )
        }

    val destination =
        resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        )
            ?: error(
                "AppForge Studio download dosyası oluşturulamadı."
            )

    try {
        downloadArtifactToUri(
            context,
            url,
            destination
        )

        val completed =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    0
                )
            }

        resolver.update(
            destination,
            completed,
            null,
            null
        )

        return destination

    } catch (
        t: Throwable
    ) {
        /*
         * Yarım kalan dosyayı kullanıcıya gösterme.
         */
        runCatching {
            resolver.delete(
                destination,
                null,
                null
            )
        }

        throw t
    }
}


private fun downloadArtifactToUri(
    context: Context,
    url: String,
    destination: Uri
) {
    require(
        url.startsWith(
            "https://",
            ignoreCase = true
        )
    ) {
        "İndirme adresi HTTPS değil."
    }

    val connection =
        java.net.URL(
            url
        ).openConnection()
            as java.net.HttpURLConnection

    try {
        connection.instanceFollowRedirects =
            true

        connection.connectTimeout =
            20_000

        /*
         * Windows portable EXE yüzlerce MB olabilir.
         * Büyük dosyalar için geniş read timeout.
         */
        connection.readTimeout =
            900_000

        connection.requestMethod =
            "GET"

        connection.setRequestProperty(
            "Accept",
            "application/octet-stream,*/*"
        )

        connection.setRequestProperty(
            "User-Agent",
            "AppForge-Studio-Android"
        )

        connection.connect()

        val code =
            connection.responseCode

        if (
            code !in 200..299
        ) {
            val detail =
                runCatching {
                    connection
                        .errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                                .take(
                                    300
                                )
                        }
                }
                    .getOrNull()
                    .orEmpty()

            error(
                "Sunucu HTTP $code" +
                    if (
                        detail.isBlank()
                    ) {
                        ""
                    } else {
                        " • $detail"
                    }
            )
        }

        require(
            connection.url.protocol
                .equals(
                    "https",
                    ignoreCase = true
                )
        ) {
            "İndirme yönlendirmesi HTTPS değil."
        }

        val output =
            context
                .contentResolver
                .openOutputStream(
                    destination,
                    "w"
                )
                ?: error(
                    "Hedef dosya açılamadı."
                )

        connection
            .inputStream
            .buffered(
                1024 * 1024
            )
            .use {
                input ->

                output
                    .buffered(
                        1024 * 1024
                    )
                    .use {
                        out ->

                        input.copyTo(
                            out,
                            1024 * 1024
                        )

                        out.flush()
                    }
            }

    } finally {
        connection.disconnect()
    }
}


private fun downloadApkToInstallerCache(
    context: Context,
    url: String,
    fileName: String
): File {

    require(
        url.startsWith(
            "https://",
            ignoreCase = true
        )
    ) {
        "APK indirme adresi HTTPS değil."
    }

    val installerDir =
        File(
            context.cacheDir,
            "apk-installer"
        ).apply {
            mkdirs()
        }

    val safeName =
        fileName
            .ifBlank {
                "AppForge-generated.apk"
            }
            .let {
                if (
                    it.endsWith(
                        ".apk",
                        ignoreCase = true
                    )
                ) {
                    it
                } else {
                    "$it.apk"
                }
            }

    val target =
        File(
            installerDir,
            safeName
        )

    val temporary =
        File(
            installerDir,
            "$safeName.download"
        )

    target.delete()
    temporary.delete()

    val connection =
        java.net.URL(
            url
        )
            .openConnection()
            as java.net.HttpURLConnection

    try {
        connection.instanceFollowRedirects =
            true

        connection.connectTimeout =
            20_000

        connection.readTimeout =
            180_000

        connection.requestMethod =
            "GET"

        connection.setRequestProperty(
            "Accept",
            "application/vnd.android.package-archive,application/octet-stream,*/*"
        )

        connection.setRequestProperty(
            "User-Agent",
            "AppForge-Studio-Android"
        )

        connection.connect()

        val code =
            connection.responseCode

        if (
            code !in 200..299
        ) {
            val detail =
                runCatching {
                    connection
                        .errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                                .take(
                                    300
                                )
                        }
                }
                    .getOrNull()
                    .orEmpty()

            error(
                "Sunucu HTTP $code" +
                if (
                    detail.isBlank()
                ) {
                    ""
                } else {
                    " • $detail"
                }
            )
        }

        connection
            .inputStream
            .buffered()
            .use { input ->

                temporary
                    .outputStream()
                    .buffered()
                    .use { output ->

                        input.copyTo(
                            output,
                            1024 * 1024
                        )

                        output.flush()
                    }
            }

        if (
            !temporary.exists() ||
            temporary.length() <
                1024L
        ) {
            error(
                "İndirilen APK boş veya eksik."
            )
        }

        val zipValid =
            temporary
                .inputStream()
                .use { input ->
                    val first =
                        input.read()

                    val second =
                        input.read()

                    first ==
                        0x50 &&
                    second ==
                        0x4B
                }

        if (
            !zipValid
        ) {
            error(
                "Sunucudan geçerli APK yerine farklı bir dosya geldi."
            )
        }

        if (
            !temporary.renameTo(
                target
            )
        ) {
            temporary
                .copyTo(
                    target,
                    overwrite = true
                )

            temporary.delete()
        }

        return target

    } finally {
        connection.disconnect()
    }
}


private fun publishApkToDownloads(
    context: Context,
    sourceFile: File,
    fileName: String
): Boolean {

    if (
        Build.VERSION.SDK_INT <
        Build.VERSION_CODES.Q
    ) {
        return false
    }

    val resolver =
        context.contentResolver

    val values =
        android.content.ContentValues()
            .apply {
                put(
                    android.provider.MediaStore
                        .MediaColumns
                        .DISPLAY_NAME,
                    fileName
                )

                put(
                    android.provider.MediaStore
                        .MediaColumns
                        .MIME_TYPE,
                    "application/vnd.android.package-archive"
                )

                put(
                    android.provider.MediaStore
                        .MediaColumns
                        .RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$APPFORGE_DOWNLOAD_FOLDER"
                )

                put(
                    android.provider.MediaStore
                        .MediaColumns
                        .IS_PENDING,
                    1
                )
            }

    val uri =
        resolver.insert(
            android.provider.MediaStore
                .Downloads
                .EXTERNAL_CONTENT_URI,
            values
        )
            ?: return false

    try {
        resolver
            .openOutputStream(
                uri,
                "w"
            )
            ?.use { output ->

                sourceFile
                    .inputStream()
                    .buffered()
                    .use { input ->

                        input.copyTo(
                            output,
                            1024 * 1024
                        )
                    }
            }
            ?: error(
                "Downloads dosyası açılamadı."
            )

        val ready =
            android.content.ContentValues()
                .apply {
                    put(
                        android.provider.MediaStore
                            .MediaColumns
                            .IS_PENDING,
                        0
                    )
                }

        resolver.update(
            uri,
            ready,
            null,
            null
        )

        return true

    } catch (
        t: Throwable
    ) {
        runCatching {
            resolver.delete(
                uri,
                null,
                null
            )
        }

        throw t
    }
}


private fun installCachedApk(
    context: Context,
    apkFile: File
): String {

    if (
        !apkFile.exists() ||
        apkFile.length() <=
            0L
    ) {
        return (
            "APK bulunamadı. " +
            "APK'YI İNDİR butonuna tekrar bas."
        )
    }

    if (
        Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O &&
        !context.packageManager
            .canRequestPackageInstalls()
    ) {
        return runCatching {

            context
                .getSharedPreferences(
                    "appforge_installer",
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    "pending_apk_path",
                    apkFile.absolutePath
                )
                .apply()

            val permissionIntent =
                Intent(
                    Settings
                        .ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse(
                        "package:${context.packageName}"
                    )
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            context.startActivity(
                permissionIntent
            )

            "Bu kaynaktan izin ver seçeneğini aç. AppForge Studio'ya dönünce kurulum otomatik devam edecek."

        }.getOrElse {
            "APK yükleme izni açılamadı: ${it.message}"
        }
    }

    return runCatching {

        val installUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

        val intent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {

                setDataAndType(
                    installUri,
                    "application/vnd.android.package-archive"
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        context.startActivity(
            intent
        )

        "✅ Android APK yükleyici açıldı."

    }.getOrElse {
        "APK yükleyici açılamadı: ${it.message}"
    }
}


private suspend fun waitForApkDownload(
    context: Context,
    downloadId: Long,
    timeoutMs: Long = 120_000L
): String? {

    val manager =
        context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

    val deadline =
        System.currentTimeMillis() +
            timeoutMs


    while (
        System.currentTimeMillis() <
        deadline
    ) {

        val result =
            withContext(
                Dispatchers.IO
            ) {

                val query =
                    DownloadManager.Query()
                        .setFilterById(
                            downloadId
                        )

                var status: Int? =
                    null

                runCatching {

                    manager
                        .query(query)
                        .use { cursor ->

                            if (
                                cursor.moveToFirst()
                            ) {

                                val index =
                                    cursor
                                        .getColumnIndex(
                                            DownloadManager
                                                .COLUMN_STATUS
                                        )

                                if (
                                    index >= 0
                                ) {
                                    status =
                                        cursor
                                            .getInt(
                                                index
                                            )
                                }
                            }
                        }
                }

                val apkUri =
                    runCatching {
                        manager
                            .getUriForDownloadedFile(
                                downloadId
                            )
                    }.getOrNull()

                Pair(
                    status,
                    apkUri
                )
            }


        val currentStatus =
            result.first

        val apkUri =
            result.second


        /*
         * URI oluştuysa dosya Android DownloadManager
         * tarafından kuruluma hazır hale gelmiştir.
         */
        if (
            apkUri != null
        ) {
            return null
        }


        if (
            currentStatus ==
            DownloadManager.STATUS_FAILED
        ) {
            return "APK indirmesi başarısız."
        }


        delay(
            500L
        )
    }


    return (
        "APK indirmesi devam ediyor. " +
        "İndirme tamamlanınca tekrar APK'YI KUR'a bas."
    )
}


private fun installDownloadedApk(
    context: Context,
    downloadId: Long
): String {

    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {

        return runCatching {

            context
                .getSharedPreferences(
                    "appforge_installer",
                    Context.MODE_PRIVATE
                )
                .edit()
                .putLong(
                    "pending_download_id",
                    downloadId
                )
                .apply()

            val permissionIntent =
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse(
                        "package:${context.packageName}"
                    )
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            context.startActivity(
                permissionIntent
            )

            "Bu kaynaktan izin ver seçeneğini aç ve AppForge Studio'ya geri dön."

        }.getOrElse {

            "APK yükleme izni ekranı açılamadı: ${it.message}"
        }
    }


    val manager =
        context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager


    val downloadedUri =
        manager.getUriForDownloadedFile(
            downloadId
        )
            ?: return (
                "İndirilen APK bulunamadı. " +
                "APK'YI İNDİR butonuna tekrar bas."
            )


    return runCatching {

        /*
         * DownloadManager URI'sini doğrudan yükleyiciye
         * vermiyoruz. APK önce AppForge cache alanına
         * kopyalanıyor.
         */
        val installerDir =
            File(
                context.cacheDir,
                "apk-installer"
            ).apply {
                mkdirs()
            }


        val apkFile =
            File(
                installerDir,
                "AppForge-generated.apk"
            )


        context.contentResolver
            .openInputStream(
                downloadedUri
            )
            ?.use { input ->

                apkFile
                    .outputStream()
                    .use { output ->

                        input.copyTo(
                            output
                        )
                    }
            }
            ?: error(
                "İndirilen APK okunamadı."
            )


        if (
            !apkFile.exists() ||
            apkFile.length() <= 0L
        ) {
            error(
                "APK geçici klasöre kopyalanamadı."
            )
        }


        val installUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )


        val installIntent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {

                setDataAndType(
                    installUri,
                    "application/vnd.android.package-archive"
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }


        context.startActivity(
            installIntent
        )


        "Android APK yükleyici açıldı."

    }.getOrElse {

        "APK yükleyici açılamadı: ${it.message}"
    }
}


@Composable
private fun ProjectLibraryScreen(
    proUnlocked: Boolean,
    onBack: () -> Unit,
    onLoad: (SavedProject) -> Unit
) {
    val libraryConfiguration =
        LocalConfiguration.current

    val libraryScreenWidthDp =
        libraryConfiguration.screenWidthDp

    val libraryScreenHeightDp =
        libraryConfiguration.screenHeightDp

    val libraryCompact =
        libraryScreenWidthDp < 380

    val libraryTablet =
        minOf(
            libraryScreenWidthDp,
            libraryScreenHeightDp
        ) >= 600

    val libraryWide =
        libraryScreenWidthDp >= 600

    val libraryContentMaxWidth =
        if (libraryWide) 880.dp else 10000.dp

    val libraryHorizontalPadding =
        when {
            libraryCompact -> 10.dp
            libraryTablet -> 28.dp
            else -> 16.dp
        }

    val context =
        LocalContext.current

    var projects by
        remember {
            mutableStateOf(
                ProjectLibrary
                    .load(context)
            )
        }

    var trialSlotsUsed by
        remember {
            mutableIntStateOf(
                ProjectLibrary
                    .freeProjectSlotsUsed(
                        context
                    )
            )
        }

    Column(
        Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    "Proje Kütüphanesi"
                )
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text("←")
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(
                        max = libraryContentMaxWidth
                    )
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = libraryHorizontalPadding,
                    vertical =
                        if (libraryCompact) 10.dp else 16.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(if (libraryCompact) 7.dp else 10.dp)
        ) {
            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    if (
                                        proUnlocked
                                    ) {
                                        Color(
                                            0xFF173929
                                        )
                                    } else {
                                        Card2
                                    }
                            ),
                    shape =
                        RoundedCornerShape(if (libraryCompact) 17.dp else 20.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (libraryCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    5.dp
                                )
                    ) {
                        Text(
                            if (
                                proUnlocked
                            ) {
                                "Proje Hakkı • SINIRSIZ"
                            } else {
                                "Deneme Hakkı • $trialSlotsUsed / 5"
                            },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                18.sp
                        )

                        Text(
                            if (
                                proUnlocked
                            ) {
                                "${projects.size} kayıtlı proje • Pro ve Pro Aylık'ta proje sınırı yok."
                            } else {
                                "${(5 - trialSlotsUsed).coerceAtLeast(0)} yeni proje hakkın kaldı. Proje silmek hakkı geri getirmez."
                            },
                            color =
                                TextSecondary
                        )
                    }
                }
            }

            if (
                projects.isEmpty()
            ) {
                item {
                    NoteCard(
                        "Henüz kayıtlı proje yok."
                    )
                }
            }

            items(
                projects,
                key = {
                    it.id
                }
            ) {
                p ->
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            )
                ) {
                    Column(
                        Modifier.padding(if (libraryCompact) 12.dp else 16.dp)
                    ) {
                        Text(
                            p.name,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            p.packageName,
                            color =
                                TextSecondary,
                            fontSize =
                                12.sp
                        )

                        Text(
                            DateFormat
                                .getDateTimeInstance()
                                .format(
                                    Date(
                                        p.updatedAt
                                    )
                                ),
                            color =
                                TextSecondary,
                            fontSize =
                                11.sp
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(if (libraryCompact) 6.dp else 8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onLoad(
                                        p
                                    )
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                            ) {
                                Text(
                                    "Aç"
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    ProjectLibrary
                                        .delete(
                                            context,
                                            p.id
                                        )

                                    projects =
                                        ProjectLibrary
                                            .load(
                                                context
                                            )

                                    trialSlotsUsed =
                                        ProjectLibrary
                                            .freeProjectSlotsUsed(
                                                context
                                            )
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                            ) {
                                Text(
                                    "Sil"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildHistoryScreen(onBack: () -> Unit) {
    val historyConfiguration =
        LocalConfiguration.current

    val historyScreenWidthDp =
        historyConfiguration.screenWidthDp

    val historyScreenHeightDp =
        historyConfiguration.screenHeightDp

    val historyCompact =
        historyScreenWidthDp < 380

    val historyTablet =
        minOf(
            historyScreenWidthDp,
            historyScreenHeightDp
        ) >= 600

    val historyWide =
        historyScreenWidthDp >= 600

    val historyContentMaxWidth =
        if (historyWide) 880.dp else 10000.dp

    val historyHorizontalPadding =
        when {
            historyCompact -> 10.dp
            historyTablet -> 28.dp
            else -> 16.dp
        }

    val context = LocalContext.current
    val builds = remember { ProjectLibrary.loadBuilds(context) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Derleme Geçmişi") },
            navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        if (builds.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz build geçmişi yok.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(
                            max = historyContentMaxWidth
                        )
                        .fillMaxWidth(),
                contentPadding =
                PaddingValues(
                    horizontal = historyHorizontalPadding,
                    vertical =
                        if (historyCompact) 10.dp else 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (historyCompact) 7.dp else 10.dp)
            ) {
                items(builds, key = { it.id }) { b ->
                    Card(colors = CardDefaults.cardColors(containerColor = Card2)) {
                        Column(Modifier.padding(if (historyCompact) 12.dp else 16.dp)) {
                            Text(b.projectName, fontWeight = FontWeight.Bold)
                            Text(b.packageName, color = TextSecondary, fontSize = 12.sp)
                            Text(
                                AppForgeBuildNumbers.label(
                                    b.buildNo
                                ),
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text("Durum: ${b.status}", color = TextSecondary)
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(b.createdAt)),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun AccountScreen(
    serverUrl: String,
    session: Session?,
    onSession: (Session?) -> Unit,
    onApiKeyCreated: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val accountConfiguration =
        LocalConfiguration.current

    val accountScreenWidthDp =
        accountConfiguration.screenWidthDp

    val accountScreenHeightDp =
        accountConfiguration.screenHeightDp

    val accountCompact =
        accountScreenWidthDp < 380

    val accountTablet =
        minOf(
            accountScreenWidthDp,
            accountScreenHeightDp
        ) >= 600

    val accountWide =
        accountScreenWidthDp >= 600

    val accountContentMaxWidth =
        if (accountWide) 820.dp else 10000.dp

    val accountHorizontalPadding =
        when {
            accountCompact -> 10.dp
            accountTablet -> 28.dp
            else -> 16.dp
        }


    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var transferTwoFactorCode by remember { mutableStateOf("") }

    var showForgotPasswordDialog by
        remember {
            mutableStateOf(false)
        }

    var forgotPasswordEmail by
        remember {
            mutableStateOf("")
        }

    var showDeleteAccount by
        remember {
            mutableStateOf(false)
        }

    var deletePassword by
        remember {
            mutableStateOf("")
        }

    var deleteTwoFactorCode by
        remember {
            mutableStateOf("")
        }

    var deleteConfirmation by
        remember {
            mutableStateOf("")
        }

    if (
        showForgotPasswordDialog
    ) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    showForgotPasswordDialog =
                        false
                }
            },
            title = {
                Text(
                    "Şifremi Unuttum"
                )
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    Text(
                        "AppForge hesabında kullandığın e-posta adresini yaz. " +
                            "Hesap mevcutsa parola sıfırlama bağlantısı gönderilecek.",
                        color =
                            TextSecondary,
                        lineHeight =
                            19.sp
                    )

                    OutlinedTextField(
                        value =
                            forgotPasswordEmail,
                        onValueChange = {
                            forgotPasswordEmail =
                                it
                        },
                        label = {
                            Text(
                                "E-posta"
                            )
                        },
                        singleLine =
                            true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled =
                        !busy &&
                        forgotPasswordEmail
                            .trim()
                            .contains("@") &&
                        forgotPasswordEmail
                            .trim()
                            .contains("."),
                    onClick = {
                        busy =
                            true

                        scope.launch {
                            try {
                                val result =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        AppForgeAccountClient(
                                            context,
                                            serverUrl
                                        ).forgotPassword(
                                            forgotPasswordEmail
                                                .trim()
                                        )
                                    }

                                message =
                                    result

                                showForgotPasswordDialog =
                                    false
                            } catch (
                                t: Throwable
                            ) {
                                message =
                                    "Parola sıfırlama isteği gönderilemedi: ${t.message}"
                            } finally {
                                busy =
                                    false
                            }
                        }
                    }
                ) {
                    Text(
                        if (busy) {
                            "Gönderiliyor..."
                        } else {
                            "SIFIRLAMA BAĞLANTISI GÖNDER"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled =
                        !busy,
                    onClick = {
                        showForgotPasswordDialog =
                            false
                    }
                ) {
                    Text(
                        "İptal"
                    )
                }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("AppForge Hesabı") },
            navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = accountContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = accountHorizontalPadding,
                    vertical =
                        if (accountCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (accountCompact) 8.dp else 12.dp)
        ) {
            if (session != null) {
                item { NoteCard("Giriş yapıldı: ${session.email}") }
                item {
                    OutlinedButton(
                        onClick = {
                            onSession(
                                null
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Çıkış Yap"
                        )
                    }
                }

                item {
                    HorizontalDivider()
                }

                item {
                    OutlinedButton(
                        onClick = {
                            showDeleteAccount =
                                !showDeleteAccount

                            deletePassword =
                                ""

                            deleteTwoFactorCode =
                                ""

                            deleteConfirmation =
                                ""
                        },
                        enabled =
                            !busy,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (
                                showDeleteAccount
                            ) {
                                "Hesap Silmeyi İptal Et"
                            } else {
                                "Hesabımı Sil"
                            }
                        )
                    }
                }

                if (
                    showDeleteAccount
                ) {
                    item {
                        NoteCard(
                            "Bu işlem kalıcıdır. Hesabın, projelerin, build kayıtların, hesap erişim anahtarların ve AppForge Pro hesabına bağlı erişimin silinir. Aktif bir build varsa önce tamamlanması veya iptal edilmesi gerekir."
                        )
                    }

                    item {
                        OutlinedTextField(
                            value =
                                deletePassword,
                            onValueChange = {
                                deletePassword =
                                    it
                            },
                            label = {
                                Text(
                                    "Hesap parolası"
                                )
                            },
                            visualTransformation =
                                PasswordVisualTransformation(),
                            singleLine =
                                true,
                            modifier =
                                Modifier.fillMaxWidth()
                        )
                    }

                    if (
                        session
                            .twoFactorEnabled
                    ) {
                        item {
                            OutlinedTextField(
                                value =
                                    deleteTwoFactorCode,
                                onValueChange = {
                                    deleteTwoFactorCode =
                                        it
                                            .filter {
                                                ch ->
                                                ch.isDigit()
                                            }
                                            .take(
                                                6
                                            )
                                },
                                label = {
                                    Text(
                                        "2FA kodu"
                                    )
                                },
                                singleLine =
                                    true,
                                modifier =
                                    Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value =
                                deleteConfirmation,
                            onValueChange = {
                                deleteConfirmation =
                                    it
                            },
                            label = {
                                Text(
                                    "Onaylamak için SİL yaz"
                                )
                            },
                            singleLine =
                                true,
                            modifier =
                                Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                busy =
                                    true

                                scope.launch {
                                    try {
                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            AppForgeAccountClient(
                                                context,
                                                serverUrl
                                            ).deleteAccount(
                                                email =
                                                    session.email,
                                                password =
                                                    deletePassword,
                                                twoFactorCode =
                                                    deleteTwoFactorCode
                                            )
                                        }

                                        deletePassword =
                                            ""

                                        deleteTwoFactorCode =
                                            ""

                                        deleteConfirmation =
                                            ""

                                        showDeleteAccount =
                                            false

                                        onSession(
                                            null
                                        )

                                        message =
                                            "Hesabın ve hesabına bağlı AppForge verileri kalıcı olarak silindi."
                                    } catch (
                                        t: Throwable
                                    ) {
                                        message =
                                            "Hesap silinemedi: ${t.message}"
                                    } finally {
                                        busy =
                                            false
                                    }
                                }
                            },
                            enabled =
                                !busy &&
                                deletePassword.length >=
                                    8 &&
                                deleteConfirmation
                                    .trim() ==
                                    "SİL" &&
                                (
                                    !session
                                        .twoFactorEnabled ||
                                    deleteTwoFactorCode
                                        .length ==
                                        6
                                ),
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "HESABI KALICI OLARAK SİL"
                            )
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Görünen ad (kayıt için)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("E-posta") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Parola") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(if (accountCompact) 6.dp else 8.dp)) {
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        val loginResult =
                                            withContext(Dispatchers.IO) {
                                                AppForgeAccountClient(context, serverUrl)
                                                    .login(email, password)
                                            }

                                        when (loginResult) {
                                            is LoginResult.Success -> {
                                                onSession(loginResult.session)
                                                message = "Giriş başarılı."
                                            }

                                            is LoginResult.TwoFactorRequired -> {
                                                message =
                                                    "Bu hesap için iki aşamalı doğrulama gerekiyor."
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        message = "Hata: ${t.message}"
                                    } finally { busy = false }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Giriş") }

                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        val s = withContext(Dispatchers.IO) {
                                            AppForgeAccountClient(context, serverUrl)
                                                .register(email, password, displayName)
                                        }
                                        onSession(s)
                                        message = "Hesap oluşturuldu."
                                    } catch (t: Throwable) {
                                        message = "Hata: ${t.message}"
                                    } finally { busy = false }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Kayıt Ol") }
                    }
                }
                item {
                    TextButton(
                        onClick = {
                            forgotPasswordEmail =
                                email.trim()

                            showForgotPasswordDialog =
                                true
                        },
                        enabled =
                            !busy,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Şifremi Unuttum?"
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = transferTwoFactorCode,
                        onValueChange = {
                            transferTwoFactorCode =
                                it.filter(Char::isDigit).take(6)
                        },
                        label = { Text("2FA kodu (etkinse)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    val transferred =
                                        withContext(Dispatchers.IO) {
                                            AppForgeAccountClient(context, serverUrl)
                                                .transferDevice(
                                                    email,
                                                    password,
                                                    transferTwoFactorCode
                                                )
                                        }
                                    onSession(transferred)
                                    message =
                                        "Bu cihaz etkinleştirildi; önceki cihazın erişimi kapatıldı."
                                } catch (t: Throwable) {
                                    message = "Cihaz değiştirilemedi: ${t.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled =
                            !busy &&
                            email.isNotBlank() &&
                            password.length >= 8,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("BU CİHAZI ETKİNLEŞTİR")
                    }
                }
            }

            if (message.isNotBlank()) item { NoteCard(message) }
        }
    }
}


private data class TemplateCategorySpec(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val container: Color,
    val accent: Color
)

private fun templateCategoryCatalog() = listOf(
    TemplateCategorySpec(
        key = "interaction",
        title = "Etkileşim",
        subtitle = "Toast, titreşim, fener ve bildirimler",
        icon = "⌁",
        container = Color(0xFF10343B),
        accent = Color(0xFF19E3D6)
    ),
    TemplateCategorySpec(
        key = "starters",
        title = "Başlangıçlar",
        subtitle = "Boş sayfalar, tam ekran düzenleri ve basit başlangıçlar",
        icon = "✎",
        container = Color(0xFF173545),
        accent = Color(0xFF78D6FF)
    ),
    TemplateCategorySpec(
        key = "libraries",
        title = "Starter Libraries",
        subtitle = "React, Vue, Bootstrap ve yaygın kütüphanelerle hazır başlangıçlar",
        icon = "</>",
        container = Color(0xFF4B2132),
        accent = Color(0xFFFF6A7A)
    ),
    TemplateCategorySpec(
        key = "ads",
        title = "Reklamlar",
        subtitle = "Reklam göstererek para kazan",
        icon = "$",
        container = Color(0xFF153A26),
        accent = Color(0xFF49E17E)
    ),
    TemplateCategorySpec(
        key = "device",
        title = "Cihaz",
        subtitle = "Kamera, mikrofon, konum, dosya, pano ve paylaşım",
        icon = "⌖",
        container = Color(0xFF1A2D5A),
        accent = Color(0xFF5B96FF)
    ),
    TemplateCategorySpec(
        key = "sensors",
        title = "Sensörler",
        subtitle = "İvmeölçer, jiroskop, manyetometre, adım ve yönelim",
        icon = "◌",
        container = Color(0xFF4B2A10),
        accent = Color(0xFFFF912E)
    ),
    TemplateCategorySpec(
        key = "system",
        title = "Sistem",
        subtitle = "Pil, cihaz bilgisi, izinler ve parlaklık",
        icon = "⚙",
        container = Color(0xFF38205C),
        accent = Color(0xFF9E73FF)
    ),
    TemplateCategorySpec(
        key = "panels",
        title = "Panel",
        subtitle = "Tüm API'leri bir arada sunan hazır paneller",
        icon = "▦",
        container = Color(0xFF4A420E),
        accent = Color(0xFFFFD53D)
    ),
    TemplateCategorySpec(
        key = "productivity",
        title = "Verimlilik",
        subtitle = "Görev, not ve günlük iş akışları",
        icon = "✓",
        container = Color(0xFF17382D),
        accent = Color(0xFF65E3A1)
    ),
    TemplateCategorySpec(
        key = "business",
        title = "İşletme",
        subtitle = "Stok, randevu ve operasyon panelleri",
        icon = "▤",
        container = Color(0xFF243557),
        accent = Color(0xFF86A9FF)
    ),
    TemplateCategorySpec(
        key = "commerce",
        title = "E-ticaret ve Menü",
        subtitle = "Ürün, mobil menü, sepet ve mağaza başlangıçları",
        icon = "◈",
        container = Color(0xFF4A2A22),
        accent = Color(0xFFFF9A72)
    ),
    TemplateCategorySpec(
        key = "events",
        title = "Etkinlik",
        subtitle = "Davetiye, katılım ve etkinlik bilgi ekranları",
        icon = "★",
        container = Color(0xFF412653),
        accent = Color(0xFFD991FF)
    )
)

private fun normalizeTemplateCategory(template: RemoteTemplate): String {
    /*
     * Sunucudan gelen kategori AppForge'un mevcut
     * kategori anahtarlarından biriyse doğrudan onu kullan.
     *
     * Böylece:
     * "AdMob Başlangıç" -> ads
     * "Bootstrap Başlangıç" -> libraries
     * gibi şablonlar açıklamadaki "başlangıç"
     * kelimesi yüzünden yanlış kategoriye düşmez.
     */
    val explicitCategory =
        template.category
            .trim()
            .lowercase()

    val supportedCategories =
        setOf(
            "interaction",
            "starters",
            "libraries",
            "ads",
            "device",
            "sensors",
            "system",
            "panels",
            "productivity",
            "business",
            "commerce",
            "events"
        )

    if (
        explicitCategory in
            supportedCategories
    ) {
        return explicitCategory
    }

    /*
     * Eski / genel kategori isimlerinde geriye dönük
     * uyumluluk için metin tabanlı sınıflandırma.
     */
    val haystack = (
        template.category + " " +
        template.name + " " +
        template.description
    ).lowercase()

    return when {
        haystack.contains("toast") ||
            haystack.contains("notify") ||
            haystack.contains("notification") ||
            haystack.contains("vibrate") ||
            haystack.contains("flash") ||
            haystack.contains("interaction") ||
            haystack.contains("etkileşim") ->
            "interaction"

        haystack.contains("starter") ||
            haystack.contains("blank") ||
            haystack.contains("basic") ||
            haystack.contains("başlang") ->
            "starters"

        haystack.contains("react") ||
            haystack.contains("vue") ||
            haystack.contains("bootstrap") ||
            haystack.contains("library") ||
            haystack.contains("kütüphane") ->
            "libraries"

        haystack.contains("admob") ||
            haystack.contains("rewarded") ||
            haystack.contains("banner") ||
            haystack.contains("interstitial") ||
            haystack.contains("reklam") ->
            "ads"

        haystack.contains("camera") ||
            haystack.contains("microphone") ||
            haystack.contains("location") ||
            haystack.contains("share") ||
            haystack.contains("clipboard") ||
            haystack.contains("device") ||
            haystack.contains("cihaz") ->
            "device"

        haystack.contains("sensor") ||
            haystack.contains("accelerometer") ||
            haystack.contains("gyroscope") ||
            haystack.contains("magnetometer") ||
            haystack.contains("step") ||
            haystack.contains("sensör") ->
            "sensors"

        haystack.contains("battery") ||
            haystack.contains("permission") ||
            haystack.contains("brightness") ||
            haystack.contains("system") ||
            haystack.contains("sistem") ->
            "system"

        haystack.contains("dashboard") ||
            haystack.contains("panel") ||
            haystack.contains("hub") ->
            "panels"

        else ->
            "starters"
    }
}

@Composable
private fun TemplatesScreen(
    serverUrl: String,
    session: Session?,
    onApply: (RemoteTemplate) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val templatesConfiguration =
        LocalConfiguration.current

    val templatesScreenWidthDp =
        templatesConfiguration.screenWidthDp

    val templatesScreenHeightDp =
        templatesConfiguration.screenHeightDp

    val templatesCompact =
        templatesScreenWidthDp < 380

    val templatesTablet =
        minOf(
            templatesScreenWidthDp,
            templatesScreenHeightDp
        ) >= 600

    val templatesWide =
        templatesScreenWidthDp >= 600

    val templatesContentMaxWidth =
        if (templatesWide) 980.dp else 10000.dp

    val templatesHorizontalPadding =
        when {
            templatesCompact -> 10.dp
            templatesTablet -> 28.dp
            else -> 16.dp
        }


    val scope = rememberCoroutineScope()
    val catalog = remember { templateCategoryCatalog() }

    var templates by remember { mutableStateOf<List<RemoteTemplate>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedCategoryKey by remember { mutableStateOf<String?>(null) }

    fun load() {
        val current = session

        if (current == null) {
            templates = emptyList()
            message =
                "Sunucudaki gerçek şablonları almak için hesabına giriş yap. Kategorileri yine de gezebilirsin."
            return
        }

        loading = true

        scope.launch {
            try {
                templates =
                    withContext(
                        Dispatchers.IO
                    ) {
                        WorkspaceClient(
                            context,
                            serverUrl,
                            current.token
                        ).listTemplates()
                    }

                message =
                    "${templates.size} şablon yüklendi."
            } catch (t: Throwable) {
                message =
                    "Hata: ${t.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(session, serverUrl) {
        load()
    }

    val counts =
        remember(templates) {
            catalog.associate { spec ->
                spec.key to templates.count { tmpl ->
                    normalizeTemplateCategory(tmpl) == spec.key
                }
            }
        }

    val queryLower =
        query.trim().lowercase()

    val filteredCatalog =
        catalog.filter { spec ->
            if (queryLower.isBlank()) {
                true
            } else {
                spec.title.lowercase().contains(queryLower) ||
                    spec.subtitle.lowercase().contains(queryLower) ||
                    templates.any { tmpl ->
                        normalizeTemplateCategory(tmpl) == spec.key &&
                            (
                                tmpl.name.lowercase().contains(queryLower) ||
                                tmpl.description.lowercase().contains(queryLower)
                            )
                    }
            }
        }

    val matchedTemplates =
        templates.filter { tmpl ->
            queryLower.isNotBlank() &&
                (
                    tmpl.name.lowercase().contains(queryLower) ||
                    tmpl.description.lowercase().contains(queryLower) ||
                    tmpl.category.lowercase().contains(queryLower)
                )
        }

    val selectedSpec =
        catalog.firstOrNull { it.key == selectedCategoryKey }

    val selectedTemplates =
        templates.filter { tmpl ->
            selectedCategoryKey != null &&
                normalizeTemplateCategory(tmpl) == selectedCategoryKey
        }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Şablonlar",
                        fontWeight = FontWeight.Bold
                    )
                    if (!templatesCompact) {
                        Text(
                            "Native Android API'lerini kullanan hazır HTML projeleri",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Text("←")
                }
            },
            actions = {
                TextButton(
                    onClick = { load() },
                    enabled = !loading
                ) {
                    Text(
                        if (loading) "Yükleniyor..." else "Yenile"
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg
                )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = templatesContentMaxWidth).fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = templatesHorizontalPadding,
                    vertical =
                        if (templatesCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (templatesCompact) 8.dp else 12.dp)
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = Card2
                        ),
                    shape = RoundedCornerShape(if (templatesCompact) 18.dp else 22.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (templatesCompact) 13.dp else 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Hazır şablon kataloğu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )

                        Text(
                            "Kategori gez, ilgili örnekleri filtrele ve tek dokunuşla projene uygula.",
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    if (templatesCompact) 7.dp else 10.dp
                                )
                        ) {
                            TemplateStatPill(
                                label = "Kategori",
                                value = catalog.size.toString()
                            )

                            TemplateStatPill(
                                label = "Şablon",
                                value = templates.size.toString()
                            )

                            TemplateStatPill(
                                label = "Giriş",
                                value = if (session != null) "Açık" else "Kapalı"
                            )
                        }

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Şablon veya kategori ara") },
                            placeholder = { Text("Örn. kamera, panel, reklam, react") }
                        )
                    }
                }
            }

            if (message.isNotBlank()) {
                item {
                    NoteCard(message)
                }
            }

            item {
                Text(
                    "Kategoriler",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(filteredCatalog, key = { it.key }) { spec ->
                AdvancedTemplateCategoryCard(
                    spec = spec,
                    count = counts[spec.key] ?: 0,
                    loading = loading,
                    onOpen = {
                        selectedCategoryKey = spec.key
                    }
                )
            }

            if (queryLower.isNotBlank()) {
                item {
                    Text(
                        "Arama eşleşmeleri",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (matchedTemplates.isEmpty()) {
                    item {
                        NoteCard(
                            "Aramana uyan sunucu şablonu bulunamadı."
                        )
                    }
                } else {
                    items(matchedTemplates.take(8), key = { it.slug }) { template ->
                        RemoteTemplateCard(
                            template = template,
                            onApply = {
                                onApply(template)
                            }
                        )
                    }
                }
            }
        }
    }

    if (selectedSpec != null) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedCategoryKey = null
            },
            containerColor = Bg
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = templatesHorizontalPadding,
                    end = templatesHorizontalPadding,
                    top = 6.dp,
                    bottom = if (templatesCompact) 18.dp else 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CategorySheetHeader(
                        spec = selectedSpec,
                        count = selectedTemplates.size
                    )
                }

                if (selectedTemplates.isEmpty()) {
                    item {
                        NoteCard(
                            if (session == null) {
                                "Bu kategorideki gerçek sunucu şablonlarını görmek için önce hesabına giriş yap."
                            } else {
                                "Bu kategoride henüz sunucudan gelen hazır şablon bulunmuyor."
                            }
                        )
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Card2),
                            shape = RoundedCornerShape(if (templatesCompact) 15.dp else 18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(if (templatesCompact) 12.dp else 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Bu kategori için önerilen içerik",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "• Hazır başlangıç dosyaları\n• Demo HTML sayfaları\n• Native Bridge örnek çağrıları\n• İzin / özellik demoları\n• Tek tuşla projeye uygulama",
                                    color = TextSecondary,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                } else {
                    items(selectedTemplates, key = { it.slug }) { template ->
                        RemoteTemplateCard(
                            template = template,
                            onApply = {
                                onApply(template)
                                selectedCategoryKey = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateStatPill(
    label: String,
    value: String
) {
    val templatePillCompact =
        LocalConfiguration.current.screenWidthDp < 380


    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF102037)
            ),
        shape =
            RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = if (templatePillCompact) 10.dp else 12.dp,
                    vertical = if (templatePillCompact) 8.dp else 10.dp
                )
        ) {
            Text(
                value,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AdvancedTemplateCategoryCard(
    spec: TemplateCategorySpec,
    count: Int,
    loading: Boolean,
    onOpen: () -> Unit
) {
    val templateCategoryCompact =
        LocalConfiguration.current.screenWidthDp < 380


    Card(
        onClick = onOpen,
        colors =
            CardDefaults.cardColors(
                containerColor = spec.container
            ),
        shape =
            RoundedCornerShape(if (templateCategoryCompact) 19.dp else 24.dp)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (templateCategoryCompact) 13.dp else 18.dp,
                        vertical = if (templateCategoryCompact) 13.dp else 18.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            spec.accent.copy(
                                alpha = 0.18f
                            )
                    ),
                shape =
                    RoundedCornerShape(18.dp)
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(if (templateCategoryCompact) 50.dp else 64.dp),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        spec.icon,
                        color = spec.accent,
                        fontSize = if (templateCategoryCompact) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(if (templateCategoryCompact) 11.dp else 16.dp))

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    spec.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (templateCategoryCompact) 17.sp else 20.sp
                )
                Text(
                    spec.subtitle,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )
                Text(
                    if (loading) {
                        "Yükleniyor..."
                    } else {
                        "$count hazır şablon"
                    },
                    color = spec.accent,
                    fontSize = 12.sp
                )
            }

            Text(
                "←",
                color = Color.White,
                fontSize = 30.sp
            )
        }
    }
}

@Composable
private fun CategorySheetHeader(
    spec: TemplateCategorySpec,
    count: Int
) {
    val categorySheetCompact =
        LocalConfiguration.current.screenWidthDp < 380


    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = spec.container
            ),
        shape =
            RoundedCornerShape(if (categorySheetCompact) 18.dp else 22.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (categorySheetCompact) 13.dp else 18.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${spec.icon}  ${spec.title}",
                fontWeight = FontWeight.Bold,
                fontSize = if (categorySheetCompact) 18.sp else 22.sp
            )
            Text(
                spec.subtitle,
                color = TextSecondary,
                lineHeight = 19.sp
            )
            Text(
                "$count şablon gösteriliyor",
                color = spec.accent,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RemoteTemplateCard(
    template: RemoteTemplate,
    onApply: () -> Unit
) {
    val remoteTemplateCompact =
        LocalConfiguration.current.screenWidthDp < 380


    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = Card2
            ),
        shape =
            RoundedCornerShape(if (remoteTemplateCompact) 15.dp else 18.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(if (remoteTemplateCompact) 12.dp else 16.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                template.name,
                fontWeight = FontWeight.Bold,
                fontSize = if (remoteTemplateCompact) 15.sp else 17.sp
            )

            Text(
                template.description,
                color = TextSecondary,
                lineHeight = 19.sp
            )

            AssistChip(
                onClick = {},
                label = {
                    Text(template.category.ifBlank { "Genel" })
                }
            )

            Button(
                onClick = onApply,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("ŞABLONU UYGULA")
            }
        }
    }
}


private fun t(languageCode: String, key: String) =
    StudioI18n.t(languageCode, key)

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024
        idx++
    }
    return String.format("%.1f %s", value, units[idx])
}

private fun clearTemporaryCache(context: Context): Long {
    fun folderSize(file: File): Long =
        if (!file.exists()) 0L
        else if (file.isFile) file.length()
        else file.listFiles()?.sumOf { folderSize(it) } ?: 0L

    val cache = context.cacheDir
    val bytes = folderSize(cache)
    cache.deleteRecursively()
    cache.mkdirs()
    return bytes
}

@Composable
private fun AppForgeHelpCenterScreen(
    languageCode: String,
    onBack: () -> Unit
) {
    val helpConfiguration =
        LocalConfiguration.current

    val helpScreenWidthDp =
        helpConfiguration.screenWidthDp

    val helpScreenHeightDp =
        helpConfiguration.screenHeightDp

    val helpCompact =
        helpScreenWidthDp < 380

    val helpTablet =
        minOf(
            helpScreenWidthDp,
            helpScreenHeightDp
        ) >= 600

    val helpWide =
        helpScreenWidthDp >= 600

    val helpContentMaxWidth =
        if (helpWide) 900.dp else 10000.dp

    val helpHorizontalPadding =
        when {
            helpCompact -> 10.dp
            helpTablet -> 28.dp
            else -> 16.dp
        }

    var query by
        remember {
            mutableStateOf(
                ""
            )
        }

    var selectedCategory by
        remember {
            mutableStateOf(
                "Tümü"
            )
        }

    val allArticles =
        remember {
            AppForgeKnowledgeBase
                .helpArticles()
        }

    val categories =
        remember(
            allArticles
        ) {
            listOf(
                "Tümü"
            ) +
                AppForgeKnowledgeBase
                    .helpCategories()
        }

    val matchedArticles =
        remember(
            query,
            selectedCategory,
            allArticles
        ) {
            val source =
                if (
                    query
                        .trim()
                        .isBlank()
                ) {
                    allArticles
                } else {
                    AppForgeKnowledgeBase
                        .searchHelp(
                            query =
                                query,
                            maxResults =
                                100
                        )
                }

            source.filter {
                selectedCategory ==
                    "Tümü" ||
                it.category ==
                    selectedCategory
            }
        }

    Column(
        modifier =
            Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Yardım Merkezi",
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (!helpCompact) {
                        Text(
                            "${allArticles.size} AppForge yardım konusu • cihaz içinde arama",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text(
                        "←"
                    )
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = helpContentMaxWidth)
                    .fillMaxWidth().fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = helpHorizontalPadding,
                    vertical =
                        if (helpCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (helpCompact) 9.dp else 14.dp)
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Card2
                        ),
                    shape =
                        RoundedCornerShape(if (helpCompact) 18.dp else 22.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (helpCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            )
                    ) {
                        Text(
                            "🔎 AppForge hakkında ara",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                if (helpCompact) 18.sp else 20.sp
                        )

                        Text(
                            "APK, AAB, keystore, Billing, Media3, Firebase, Native Bridge, Play Store, Python veya başka bir AppForge özelliğini yaz.",
                            color =
                                TextSecondary,
                            lineHeight =
                                19.sp
                        )

                        OutlinedTextField(
                            value =
                                query,
                            onValueChange = {
                                query =
                                    it
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            singleLine =
                                true,
                            label = {
                                Text(
                                    "Yardım konusu ara"
                                )
                            },
                            placeholder = {
                                Text(
                                    "Örn. keystore, APK, Billing, Python"
                                )
                            },
                            trailingIcon = {
                                if (
                                    query.isNotBlank()
                                ) {
                                    TextButton(
                                        onClick = {
                                            query =
                                                ""
                                        }
                                    ) {
                                        Text(
                                            "Temizle"
                                        )
                                    }
                                }
                            }
                        )

                        Text(
                            if (
                                query.isBlank()
                            ) {
                                "${matchedArticles.size} konu gösteriliyor"
                            } else {
                                "\"$query\" için ${matchedArticles.size} sonuç"
                            },
                            color =
                                Accent,
                            fontSize =
                                12.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    items(
                        categories,
                        key = {
                            it
                        }
                    ) {
                        category ->
                        FilterChip(
                            selected =
                                selectedCategory ==
                                    category,
                            onClick = {
                                selectedCategory =
                                    category
                            },
                            label = {
                                Text(
                                    category
                                )
                            }
                        )
                    }
                }
            }

            if (
                matchedArticles.isEmpty()
            ) {
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Card2
                            ),
                        shape =
                            RoundedCornerShape(if (helpCompact) 15.dp else 18.dp)
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(if (helpCompact) 12.dp else 16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            Text(
                                "Sonuç bulunamadı",
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                "Farklı bir kelime dene. Örn. APK, WebView, Firebase, Pro, build, imza veya Play Store.",
                                color =
                                    TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(
                    matchedArticles,
                    key = {
                        it.title
                    }
                ) {
                    article ->
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Card2
                            ),
                        shape =
                            RoundedCornerShape(if (helpCompact) 17.dp else 20.dp),
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(if (helpCompact) 12.dp else 16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            AssistChip(
                                onClick = {
                                    selectedCategory =
                                        article.category
                                },
                                label = {
                                    Text(
                                        article.category
                                    )
                                }
                            )

                            Text(
                                article.title,
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    if (helpCompact) 16.sp else 18.sp
                            )

                            Text(
                                article.text,
                                color =
                                    TextSecondary,
                                lineHeight =
                                    20.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Color(0xFF102037)
                        ),
                    shape =
                        RoundedCornerShape(if (helpCompact) 17.dp else 20.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (helpCompact) 12.dp else 16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            )
                    ) {
                        Text(
                            "✨ Yerel AI ile devam et",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "Yardım Merkezi sabit ve doğrulanmış AppForge bilgilerini anında gösterir. Daha özel proje sorularında ana ekrandaki Yerel AI Asistanı'nı kullanabilirsin.",
                            color =
                                TextSecondary,
                            lineHeight =
                                19.sp
                        )
                    }
                }
            }
        }
    }
}


private data class SettingsEntry(
    val icon: String,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun SettingsHubScreen(
    languageCode: String,
    proUnlocked: Boolean,
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenKeystore: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenHowTo: () -> Unit,
    onOpenPlayGuide: () -> Unit,
    onOpenLegal: () -> Unit,
    onFeedback: () -> Unit,
    onClearCache: () -> Unit
) {
    val settingsConfiguration =
        LocalConfiguration.current

    val settingsScreenWidthDp =
        settingsConfiguration.screenWidthDp

    val settingsScreenHeightDp =
        settingsConfiguration.screenHeightDp

    val settingsCompact =
        settingsScreenWidthDp < 380

    val settingsTablet =
        minOf(
            settingsScreenWidthDp,
            settingsScreenHeightDp
        ) >= 600

    val settingsWide =
        settingsScreenWidthDp >= 600

    val settingsContentMaxWidth =
        if (settingsWide) 880.dp else 10000.dp

    val settingsHorizontalPadding =
        when {
            settingsCompact -> 10.dp
            settingsTablet -> 28.dp
            else -> 16.dp
        }

    val entries = listOf(
        SettingsEntry(
            "🌐",
            t(languageCode, "language"),
            t(languageCode, "system_default"),
            onOpenLanguage
        ),
        SettingsEntry(
            "🔐",
            t(languageCode, "keystore_manager"),
            "JKS / keystore kasası ve parmak izleri",
            onOpenKeystore
        ),
        SettingsEntry(
            "★",
            t(languageCode, "pro"),
            if (proUnlocked) t(languageCode, "active") else "Standart",
            onOpenPro
        ),
        SettingsEntry(
            "❓",
            t(languageCode, "how_to_use"),
            "Arama, SSS ve AppForge kullanım rehberi",
            onOpenHowTo
        ),
        SettingsEntry(
            "ⓘ",
            t(languageCode, "play_guide"),
            "Google Play yayınlama adımları",
            onOpenPlayGuide
        ),
        SettingsEntry(
            "🛡",
            t(languageCode, "legal"),
            "Kullanım koşulları ve gizlilik",
            onOpenLegal
        ),
        SettingsEntry(
            "✉",
            t(languageCode, "send_feedback"),
            "28550040284a@gmail.com",
            onFeedback
        ),
        SettingsEntry(
            "🗑",
            t(languageCode, "clear_cache"),
            "Geçici build dosyalarını ve önbellekleri temizler",
            onClearCache
        )
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(t(languageCode, "settings"), fontWeight = FontWeight.Bold)
                    Text(
                        t(languageCode, "settings_subtitle"),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(
                        max = settingsContentMaxWidth
                    )
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = settingsHorizontalPadding,
                    vertical =
                        if (settingsCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries) { item ->
                SettingsCardRow(item)
            }
        }
    }
}

@Composable
private fun SettingsCardRow(entry: SettingsEntry) {
    val settingsCardCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Card(
        onClick = entry.onClick,
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (settingsCardCompact) 18.dp else 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (settingsCardCompact) 12.dp else 16.dp,
                vertical = if (settingsCardCompact) 14.dp else 18.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF23344E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(if (settingsCardCompact) 44.dp else 52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                    entry.icon,
                    fontSize = if (settingsCardCompact) 21.sp else 24.sp
                )
                }
            }

            Spacer(Modifier.width(if (settingsCardCompact) 10.dp else 14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (settingsCardCompact) 16.sp else 18.sp
                )
                Text(
                    entry.subtitle,
                    color = TextSecondary,
                    lineHeight = if (settingsCardCompact) 16.sp else 18.sp,
                    fontSize = if (settingsCardCompact) 12.sp else 14.sp
                )
            }

            Text(
                "›",
                fontSize = if (settingsCardCompact) 24.sp else 28.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun LanguageSettingsScreen(
    languageCode: String,
    onBack: () -> Unit,
    onSelect: (String) -> Unit
) {
    val languageConfiguration =
        LocalConfiguration.current

    val languageScreenWidthDp =
        languageConfiguration.screenWidthDp

    val languageScreenHeightDp =
        languageConfiguration.screenHeightDp

    val languageCompact =
        languageScreenWidthDp < 380

    val languageTablet =
        minOf(
            languageScreenWidthDp,
            languageScreenHeightDp
        ) >= 600

    val languageWide =
        languageScreenWidthDp >= 600

    val languageContentMaxWidth =
        if (languageWide) 880.dp else 10000.dp

    val languageHorizontalPadding =
        when {
            languageCompact -> 10.dp
            languageTablet -> 28.dp
            else -> 16.dp
        }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "choose_language"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(
                        max = languageContentMaxWidth
                    )
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = languageHorizontalPadding,
                    vertical =
                        if (languageCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(StudioI18n.languages) { lang ->
                Card(
                    onClick = { onSelect(lang.code) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (lang.code == languageCode) Color(0xFF1B3158) else Card2
                    ),
                    shape = RoundedCornerShape(if (languageCompact) 17.dp else 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.nativeLabel, fontWeight = FontWeight.Bold)
                            Text(lang.englishLabel, color = TextSecondary, fontSize = 12.sp)
                        }
                        RadioButton(
                            selected = lang.code == languageCode,
                            onClick = { onSelect(lang.code) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalCenterScreen(
    languageCode: String,
    onBack: () -> Unit
) {
    val legalConfiguration =
        LocalConfiguration.current

    val legalScreenWidthDp =
        legalConfiguration.screenWidthDp

    val legalScreenHeightDp =
        legalConfiguration.screenHeightDp

    val legalCompact =
        legalScreenWidthDp < 380

    val legalTablet =
        minOf(
            legalScreenWidthDp,
            legalScreenHeightDp
        ) >= 600

    val legalWide =
        legalScreenWidthDp >= 600

    val legalContentMaxWidth =
        if (legalWide) 860.dp else 10000.dp

    val legalHorizontalPadding =
        when {
            legalCompact -> 10.dp
            legalTablet -> 28.dp
            else -> 16.dp
        }

    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "legal_title"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = legalContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = legalHorizontalPadding,
                    vertical =
                        if (legalCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (legalCompact) 9.dp else 14.dp)
        ) {
            item {
                LegalInfoCard(
                    icon = "📄",
                    title = t(languageCode, "terms_of_use"),
                    body = "1. APK'ya dönüştürdüğünüz içerikten yalnız siz sorumlusunuz.\n2. Yalnızca size ait olan veya kullanım izni aldığınız içerikleri dönüştürün.\n3. Dönüştürülen APK'lar sunucularımızda saklanmaz veya dağıtılmaz.\n4. Tüm APK oluşturma işlemi cihazınızda yerel olarak gerçekleşir.\n5. Uygulamanın kötüye kullanımından sorumlu değiliz."
                )
            }
            item {
                LegalInfoCard(
                    icon = "🛡",
                    title = t(languageCode, "privacy_policy"),
                    body = "1. Kişisel veri toplamıyoruz.\n2. Tüm proje verileri cihazınızda yerel olarak saklanır.\n3. İnternet izni yalnız sizin isteğiniz üzerine URL içeriği getirmek veya Build Service ile iletişim kurmak için kullanılır.\n4. Verilerinizi üçüncü taraflarla paylaşmayız."
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://hackmaster-tr.github.io/AppForge-Studio/privacy.html")
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(if (legalCompact) 48.dp else 52.dp)
                ) {
                    Text("Gizlilik politikasını tarayıcıda aç")
                }
            }
        }
    }
}

@Composable
private fun LegalInfoCard(
    icon: String,
    title: String,
    body: String
) {
    val legalCardCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (legalCardCompact) 19.dp else 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (legalCardCompact) 13.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "$icon  $title",
                fontWeight = FontWeight.Bold,
                fontSize = if (legalCardCompact) 18.sp else 22.sp
            )
            Text(
                body,
                color = TextSecondary,
                lineHeight = if (legalCardCompact) 19.sp else 21.sp,
                fontSize = if (legalCardCompact) 13.sp else 14.sp
            )
        }
    }
}

@Composable
private fun HowToUseCenterScreen(
    languageCode: String,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "how_to_title"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                NoteCard("HTML to APK Builder ile web sitenizi veya HTML dosyanızı gerçek bir Android uygulamasına dönüştürebilirsiniz. Kodlama bilmenize gerek yok.")
            }
            item { ExpandableGuideCard("➕", "Proje Oluşturun", "Açmak için dokunun", "Yeni bir proje oluşturun veya Hızlı Oluştur modunu seçin. Uygulama adını belirleyin ve varsayılan paket adı otomatik oluşsun.") }
            item { ExpandableGuideCard("☁", "İçeriğinizi Ekleyin", "Açmak için dokunun", "Yerel HTML/ZIP veya HTTPS web sitesi kullanın. Hızlı mod güvenli varsayılanları uygular.") }
            item { ExpandableGuideCard("⚙", "Ayarları Yapılandırın", "Açmak için dokunun", "İzinler, Native Bridge, tema, AdMob, Billing ve Firebase gibi seçenekleri gelişmiş modda ayarlayın.") }
            item { ExpandableGuideCard("🖼", "Bir İkon Seçin", "Açmak için dokunun", "PNG ikon yükleyin. İsterseniz AppForge varsayılan ikonu kullanılabilir.") }
            item { ExpandableGuideCard("🔧", "Oluşturun ve İndirin", "Açmak için dokunun", "Build Service'e gönderin, canlı build loglarını izleyin ve APK/AAB çıktısını alın.") }
            item { ExpandableGuideCard("✎", "Yayınlayın (İsteğe Bağlı)", "Açmak için dokunun", "Play Store'da yayınlayacaksanız imzalama anahtarı, mağaza listesi ve gizlilik politikası hazırlayın.") }
            item { ExpandableGuideCard("🔐", "Play Store imzalama anahtarı seçimi", "Açmak için dokunun", "Release güncellemelerinde aynı keystore'u kullanmalısınız. Keystore Yöneticisi üzerinden parmak izlerini ve yedekleri kontrol edin.") }
        }
    }
}

@Composable
private fun ExpandableGuideCard(
    icon: String,
    title: String,
    subtitle: String,
    body: String
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp)
                }
                Text(if (expanded) "⌃" else "⌄", fontSize = 22.sp)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Text(body, color = TextSecondary, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun PlayPublishingGuideScreen(
    languageCode: String,
    onBack: () -> Unit
) {
    val playGuideConfiguration =
        LocalConfiguration.current

    val playGuideScreenWidthDp =
        playGuideConfiguration.screenWidthDp

    val playGuideScreenHeightDp =
        playGuideConfiguration.screenHeightDp

    val playGuideCompact =
        playGuideScreenWidthDp < 380

    val playGuideTablet =
        minOf(
            playGuideScreenWidthDp,
            playGuideScreenHeightDp
        ) >= 600

    val playGuideWide =
        playGuideScreenWidthDp >= 600

    val playGuideContentMaxWidth =
        if (playGuideWide) 860.dp else 10000.dp

    val playGuideHorizontalPadding =
        when {
            playGuideCompact -> 10.dp
            playGuideTablet -> 28.dp
            else -> 16.dp
        }

    val steps = listOf(
        "1. Google Play Console Hesabı Oluşturun" to "Google Play Console web sitesini ziyaret edin ve tek seferlik kayıt ücretini ödeyin.",
        "2. APK'nızı / AAB'nizi Oluşturun" to "Bu uygulamada HTML içeriğinizle proje oluşturun, paket adını ayarlayın ve üretim imzası için keystore hazırlayın.",
        "3. Keystore'unuzu Yedekleyin" to "Keystore Yöneticisi'nde imza anahtarınızı güvenli bir yerde saklayın.",
        "4. Uygulama Listesi Oluşturun" to "Uygulama adı, kategori, kısa açıklama ve tam açıklamayı hazırlayın.",
        "5. İçerik Derecelendirmesi" to "İçerik derecelendirmesi anketini tamamlayın.",
        "6. Gizlilik Politikası" to "Uygulamanız internet veya kullanıcı verisi kullanıyorsa bir gizlilik politikası URL'si ekleyin.",
        "7. AAB'yi Yükleyin" to "Üretim > Sürümler > Üretim oluştur bölümüne imzalı paketinizi yükleyin.",
        "8. Yayınlayın" to "Tüm form ve işaretleri tamamladıktan sonra yayını gönderin.",
        "9. Güncellemeler" to "Yeni sürümlerde versionCode'u artırın ve aynı keystore ile tekrar imzalayın.",
        "10. Keystore'unuzu mu Kaybettiniz?" to "Play App Signing etkinse upload key reset sürecini kullanın; değilse eski imza olmadan güncelleme yapamazsınız.",
        "11. Bu Uygulamayı Yeniden mi Yüklediniz?" to "Yerel keystore ve proje dosyalarınızı geri aktarın, ardından mevcut packageName ile derleyin."
    )
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "play_publish_title"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )
        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = playGuideContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = playGuideHorizontalPadding,
                    vertical =
                        if (playGuideCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (playGuideCompact) 9.dp else 14.dp)
        ) {
            items(steps) { step ->
                GuideStepCard(step.first, step.second)
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    title: String,
    body: String
) {
    val guideCardCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (guideCardCompact) 18.dp else 22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (guideCardCompact) 13.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = if (guideCardCompact) 17.sp else 20.sp
            )
            Text(
                body,
                color = TextSecondary,
                lineHeight = if (guideCardCompact) 18.sp else 21.sp,
                fontSize = if (guideCardCompact) 13.sp else 14.sp
            )
        }
    }
}

@Composable
private fun ProUpgradeScreen(
    languageCode: String,
    serverUrl: String,
    session: Session?,
    currentStatus: ProStatus?,
    securityMessage: String,
    onBack: () -> Unit,
    onVerified: (ProStatus) -> Unit,
    onSecurityMessage: (String) -> Unit
) {
    val proConfiguration =
        LocalConfiguration.current

    val proScreenWidthDp =
        proConfiguration.screenWidthDp

    val proScreenHeightDp =
        proConfiguration.screenHeightDp

    val proCompact =
        proScreenWidthDp < 380

    val proTablet =
        minOf(
            proScreenWidthDp,
            proScreenHeightDp
        ) >= 600

    val proWide =
        proScreenWidthDp >= 600

    val proContentMaxWidth =
        if (proWide) 900.dp else 10000.dp

    val proHorizontalPadding =
        when {
            proCompact -> 10.dp
            proTablet -> 28.dp
            else -> 16.dp
        }

    val context =
        LocalContext.current

    val activity =
        context as?
        android.app.Activity

    val scope =
        rememberCoroutineScope()

    var checking by
        remember {
            mutableStateOf(
                false
            )
        }

    var purchasingPlan by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    var securityConfig by
        remember {
            mutableStateOf<
                com.appforge.studio.security.SecurityConfig?
            >(
                null
            )
        }

    var prices by
        remember {
            mutableStateOf(
                StudioPlanPrice()
            )
        }

    var billingManager by
        remember {
            mutableStateOf<
                StudioBillingManager?
            >(
                null
            )
        }

    val signature =
        remember {
            AppSignatureVerifier
                .check(context)
        }

    fun refreshProStatus() {
        val current =
            session

        if (
            current == null
        ) {
            onSecurityMessage(
                "Önce hesabına giriş yap."
            )
            return
        }

        checking = true

        scope.launch {
            try {
                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        StudioSecurityClient(
                            context =
                                context,
                            baseUrl =
                                serverUrl,
                            accessToken =
                                current.token
                        ).proStatus(
                            current.userId
                        )
                    }

                onVerified(
                    result
                )
            } catch (
                t: Throwable
            ) {
                onSecurityMessage(
                    "Pro doğrulama başarısız: ${t.message}"
                )
            } finally {
                checking =
                    false
            }
        }
    }

    fun activatePurchase(
        productId: String,
        purchaseToken: String
    ) {
        val current =
            session

        val cfg =
            securityConfig

        if (
            current == null ||
            cfg == null
        ) {
            onSecurityMessage(
                "Hesap veya Pro ürün bilgisi hazır değil."
            )
            return
        }

        val plan =
            if (
                productId ==
                cfg.proMonthlyProductId
            ) {
                "monthly"
            } else {
                "lifetime"
            }

        purchasingPlan =
            plan

        scope.launch {
            try {
                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        StudioSecurityClient(
                            context =
                                context,
                            baseUrl =
                                serverUrl,
                            accessToken =
                                current.token
                        ).activatePro(
                            userId =
                                current.userId,
                            purchaseToken =
                                purchaseToken,
                            plan =
                                plan
                        )
                    }

                onVerified(
                    result
                )

                onSecurityMessage(
                    if (
                        plan ==
                        "monthly"
                    ) {
                        "Pro Aylık doğrulandı ve hesabına tanımlandı."
                    } else {
                        "Tek seferlik Pro doğrulandı ve hesabına tanımlandı."
                    }
                )
            } catch (
                t: Throwable
            ) {
                onSecurityMessage(
                    "Satın alma doğrulaması başarısız: ${t.message}"
                )
            } finally {
                purchasingPlan =
                    null
            }
        }
    }

    LaunchedEffect(
        serverUrl,
        session?.token
    ) {
        val current =
            session

        if (
            current == null
        ) {
            billingManager
                ?.close()

            billingManager =
                null

            securityConfig =
                null

            prices =
                StudioPlanPrice()

            return@LaunchedEffect
        }

        try {
            val cfg =
                withContext(
                    Dispatchers.IO
                ) {
                    StudioSecurityClient(
                        context =
                            context,
                        baseUrl =
                            serverUrl,
                        accessToken =
                            current.token
                    ).config()
                }

            securityConfig =
                cfg

            val manager =
                StudioBillingManager(
                    context =
                        context,
                    lifetimeProductId =
                        cfg.proProductId,
                    monthlyProductId =
                        cfg.proMonthlyProductId,
                    onPurchase = {
                        purchase ->
                        activatePurchase(
                            productId =
                                purchase.productId,
                            purchaseToken =
                                purchase.purchaseToken
                        )
                    },
                    onMessage =
                        onSecurityMessage
                )

            billingManager
                ?.close()

            billingManager =
                manager

            manager.start {
                manager.queryPlans {
                    result ->
                    prices =
                        result
                }
            }
        } catch (
            t: Throwable
        ) {
            onSecurityMessage(
                "Pro planları yüklenemedi: ${t.message}"
            )
        }
    }

    DisposableEffect(
        Unit
    ) {
        onDispose {
            billingManager
                ?.close()
        }
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    t(
                        languageCode,
                        "pro_title"
                    ),
                    fontWeight =
                        FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(
                    onClick =
                        onBack
                ) {
                    Text("←")
                }
            },
            colors =
                TopAppBarDefaults
                    .topAppBarColors(
                        containerColor =
                            Bg
                    )
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = proContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = proHorizontalPadding,
                    vertical =
                        if (proCompact) 10.dp else 20.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (proCompact) 9.dp else 14.dp)
        ) {
            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    Card2
                            ),
                    shape =
                        RoundedCornerShape(if (proCompact) 22.dp else 30.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(if (proCompact) 16.dp else 24.dp),
                        horizontalAlignment =
                            Alignment
                                .CenterHorizontally,
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    12.dp
                                )
                    ) {
                        Text(
                            if (
                                currentStatus
                                    ?.active ==
                                true
                            ) {
                                "★"
                            } else {
                                "✦"
                            },
                            fontSize =
                                if (proCompact) 50.sp else 66.sp,
                            color =
                                Color(
                                    0xFFFFD400
                                )
                        )

                        Text(
                            if (
                                currentStatus
                                    ?.active ==
                                true
                            ) {
                                "PRO AKTİF"
                            } else {
                                t(
                                    languageCode,
                                    "choose_plan"
                                )
                            },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                if (proCompact) 22.sp else 27.sp
                        )

                        Text(
                            if (
                                currentStatus
                                    ?.active ==
                                true
                            ) {
                                when (
                                    currentStatus.source
                                ) {
                                    "google_play_subscription" ->
                                        "Pro Aylık aboneliğin doğrulandı."

                                    "google_play" ->
                                        "Tek seferlik Pro lisansın doğrulandı."

                                    else ->
                                        "Pro yetkin resmi AppForge sunucusunda doğrulandı."
                                }
                            } else {
                                "İki plan da aynı Pro özelliklerini açar. Tek seferlik satın alma kalıcıdır; aylık plan otomatik yenilenir."
                            },
                            color =
                                TextSecondary,
                            lineHeight =
                                21.sp
                        )
                    }
                }
            }

            if (
                currentStatus
                    ?.active !=
                true
            ) {
                item {
                    ProPlanCard(
                        badge =
                            "TEK SEFERLİK",
                        icon =
                            "★",
                        title =
                            t(
                                languageCode,
                                "pro_lifetime"
                            ),
                        description =
                            t(
                                languageCode,
                                "pro_lifetime_desc"
                            ),
                        price =
                            prices
                                .lifetimePrice
                                ?: "Google Play fiyatı",
                        accent =
                            Color(
                                0xFFFFD400
                            ),
                        features =
                            listOf(
                                "Kalıcı Pro erişimi",
                                "Sınırsız proje oluşturma (deneme kotası yok)",
                                "Built with AppForge watermark kaldırma",
                                "Özel keystore / custom signing",
                                "Firebase ve Billing araçları",
                                "Gelişmiş Native Bridge seçenekleri",
                                "Gelecekteki Pro araçlarına erişim"
                            ),
                        buttonText =
                            t(
                                languageCode,
                                "buy_once"
                            ),
                        enabled =
                            session != null &&
                            activity != null &&
                            purchasingPlan ==
                            null,
                        onClick = {
                            val manager =
                                billingManager

                            if (
                                manager ==
                                null ||
                                activity ==
                                null
                            ) {
                                onSecurityMessage(
                                    "Google Play Billing henüz hazır değil."
                                )
                            } else {
                                purchasingPlan =
                                    "lifetime"

                                manager
                                    .launchLifetime(
                                        activity
                                    )

                                purchasingPlan =
                                    null
                            }
                        }
                    )
                }

                item {
                    ProPlanCard(
                        badge =
                            "ESNEK PLAN",
                        icon =
                            "↻",
                        title =
                            t(
                                languageCode,
                                "pro_monthly"
                            ),
                        description =
                            t(
                                languageCode,
                                "pro_monthly_desc"
                            ),
                        price =
                            prices
                                .monthlyPrice
                                ?.let {
                                    "$it / ay"
                                }
                                ?: "Google Play aylık fiyatı",
                        accent =
                            Color(
                                0xFF84C8FF
                            ),
                        features =
                            listOf(
                                "Tüm Pro özellikleri",
                                "Sınırsız proje oluşturma (deneme kotası yok)",
                                "Built with AppForge watermark kaldırma",
                                "Aylık otomatik yenileme",
                                "Google Play üzerinden yönetim",
                                "İstediğin zaman iptal edebilme",
                                "Aktif abonelik süresince tam erişim"
                            ),
                        buttonText =
                            t(
                                languageCode,
                                "subscribe_monthly"
                            ),
                        enabled =
                            session != null &&
                            activity != null &&
                            purchasingPlan ==
                            null,
                        onClick = {
                            val manager =
                                billingManager

                            if (
                                manager ==
                                null ||
                                activity ==
                                null
                            ) {
                                onSecurityMessage(
                                    "Google Play Billing henüz hazır değil."
                                )
                            } else {
                                purchasingPlan =
                                    "monthly"

                                manager
                                    .launchMonthly(
                                        activity
                                    )

                                purchasingPlan =
                                    null
                            }
                        }
                    )
                }
            }

            item {
                LegalInfoCard(
                    icon = "🔏",
                    title =
                        "İmza Kontrolü",
                    body =
                        when {
                            !signature.configured ->
                                "Release sertifika SHA-256 henüz Gradle property olarak yapılandırılmadı. Play Integrity sunucu tarafında yine kullanılabilir."

                            signature.valid ->
                                "Uygulama imzası beklenen release sertifikasıyla eşleşiyor."

                            else ->
                                "UYARI: Uygulama imzası beklenen release sertifikasıyla eşleşmiyor."
                        }
                )
            }

            item {
                LegalInfoCard(
                    icon = "☁️",
                    title =
                        "Sunucu Yetkisi",
                    body =
                        if (
                            session ==
                            null
                        ) {
                            "Pro planı satın almak veya doğrulamak için önce AppForge hesabına giriş yap."
                        } else if (
                            currentStatus
                                ?.active ==
                            true
                        ) {
                            buildString {
                                append(
                                    "Hesap: ${session.email}\n"
                                )
                                append(
                                    "Kaynak: ${currentStatus.source ?: "server"}\n"
                                )
                                append(
                                    "Ürün: ${currentStatus.productId ?: "-"}"
                                )

                                if (
                                    !currentStatus
                                        .expiresAt
                                        .isNullOrBlank()
                                ) {
                                    append(
                                        "\nBitiş / yenileme zamanı: ${currentStatus.expiresAt}"
                                    )
                                }
                            }
                        } else {
                            "Hesap: ${session.email}\nAktif Pro yetkisi henüz doğrulanmadı."
                        }
                )
            }

            if (
                securityMessage
                    .isNotBlank()
            ) {
                item {
                    NoteCard(
                        securityMessage
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        refreshProStatus()
                    },
                    enabled =
                        session != null &&
                        !checking,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(if (proCompact) 49.dp else 54.dp)
                ) {
                    Text(
                        if (
                            checking
                        ) {
                            "DOĞRULANIYOR..."
                        } else {
                            "SATIN ALIMLARI / PRO DURUMUNU DOĞRULA"
                        },
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            item {
                NoteCard(
                    "Satın alımlar Google Play Billing üzerinden yapılır; Pro erişimi yalnız AppForge sunucusu Google Play satın alımını ve gerektiğinde Play Integrity sonucunu doğruladıktan sonra açılır. Aylık abonelik Google Play'de iptal edilene kadar otomatik yenilenir."
                )
            }
        }
    }
}

@Composable
private fun ProPlanCard(
    badge: String,
    icon: String,
    title: String,
    description: String,
    price: String,
    accent: Color,
    features: List<String>,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val proCardCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Card2
                ),
        shape =
            RoundedCornerShape(
                if (proCardCompact) 20.dp else 26.dp
            ),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                accent.copy(
                    alpha =
                        0.5f
                )
            )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        if (proCardCompact) 14.dp else 20.dp
                    ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        12.dp
                    )
        ) {
            Row(
                verticalAlignment =
                    Alignment
                        .CenterVertically
            ) {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    accent.copy(
                                        alpha =
                                            0.15f
                                    )
                            ),
                    shape =
                        RoundedCornerShape(
                            if (proCardCompact) 15.dp else 18.dp
                        )
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(
                                    if (proCardCompact) 48.dp else 58.dp
                                ),
                        contentAlignment =
                            Alignment
                                .Center
                    ) {
                        Text(
                            icon,
                            color =
                                accent,
                            fontSize =
                                if (proCardCompact) 23.sp else 28.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    Modifier.width(
                        if (proCardCompact) 10.dp else 14.dp
                    )
                )

                Column(
                    Modifier.weight(
                        1f
                    )
                ) {
                    Text(
                        badge,
                        color =
                            accent,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            11.sp
                    )

                    Text(
                        title,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            if (proCardCompact) 19.sp else 23.sp
                    )

                    Text(
                        description,
                        color =
                            TextSecondary
                    )
                }
            }

            Text(
                price,
                fontSize =
                    if (proCardCompact) 21.sp else 25.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    accent
            )

            features.forEach {
                feature ->
                Text(
                    "✓ $feature",
                    color =
                        TextSecondary,
                    lineHeight =
                        20.sp
                )
            }

            Button(
                onClick =
                    onClick,
                enabled =
                    enabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            if (proCardCompact) 49.dp else 54.dp
                        ),
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                accent,
                            contentColor =
                                Color(
                                    0xFF0A0F16
                                )
                        )
            ) {
                Text(
                    buttonText,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun KeystoreManagerScreen(
    languageCode: String,
    refreshKey: Int,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onMessage: (String) -> Unit
) {
    val keystoreConfiguration =
        LocalConfiguration.current

    val keystoreScreenWidthDp =
        keystoreConfiguration.screenWidthDp

    val keystoreScreenHeightDp =
        keystoreConfiguration.screenHeightDp

    val keystoreCompact =
        keystoreScreenWidthDp < 380

    val keystoreTablet =
        minOf(
            keystoreScreenWidthDp,
            keystoreScreenHeightDp
        ) >= 600

    val keystoreWide =
        keystoreScreenWidthDp >= 600

    val keystoreContentMaxWidth =
        if (keystoreWide) 900.dp else 10000.dp

    val keystoreHorizontalPadding =
        when {
            keystoreCompact -> 10.dp
            keystoreTablet -> 28.dp
            else -> 16.dp
        }

    val context = LocalContext.current
    var keys by remember(refreshKey) { mutableStateOf(KeystoreVault.load(context)) }

    fun reload() {
        keys = KeystoreVault.load(context)
    }

    LaunchedEffect(refreshKey) {
        reload()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "keystore_manager"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = keystoreContentMaxWidth)
                    .fillMaxWidth().weight(1f),
            contentPadding =
                PaddingValues(
                    horizontal = keystoreHorizontalPadding,
                    vertical =
                        if (keystoreCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (keystoreCompact) 9.dp else 14.dp)
        ) {
            item {
                LegalInfoCard(
                    icon = "ℹ",
                    title = "Keystore Nedir?",
                    body = "Keystore, uygulamanızın dijital imzasıdır. Google Play'de yayınladığınız uygulamanın güncellemelerinde aynı keystore'u kullanmak zorundasınız. Kaybederseniz uygulamanızı güncelleyemezsiniz."
                )
            }

            item {
                LegalInfoCard(
                    icon = "💡",
                    title = "Önemli İpucu",
                    body = "Varsayılan keystore otomatik olarak oluşturulur. Pro sürümde özel keystore içe aktarabilir ve mevcut imzanızı koruyabilirsiniz."
                )
            }

            item {
                ExpandableGuideCard(
                    icon = "⚠",
                    title = "İmza anahtarınızı mı kaybettiniz?",
                    subtitle = "Açmak için dokunun",
                    body = "Play App Signing açıksa upload key reset süreci ile devam edebilirsiniz. Değilse aynı packageName için uygulamayı güncellemeniz mümkün olmayabilir."
                )
            }

            item {
                Text(
                    "Keystore'larınız",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (keystoreCompact) 18.sp else 22.sp
                )
            }

            if (keys.isEmpty()) {
                item {
                    NoteCard("Henüz içe aktarılmış keystore yok.")
                }
            } else {
                items(keys, key = { it.id }) { item ->
                    ManagedKeystoreCard(
                        item = item,
                        languageCode = languageCode,
                        onCopy = { text, label ->
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                            onMessage("$label panoya kopyalandı.")
                        },
                        onDelete = {
                            KeystoreVault.delete(context, item.id)
                            reload()
                            onMessage("Keystore silindi: ${item.name}")
                        }
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = keystoreContentMaxWidth)
                    .fillMaxWidth()
                    .padding(
                        horizontal = keystoreHorizontalPadding,
                        vertical = if (keystoreCompact) 10.dp else 14.dp
                    )
        ) {
            Button(
                onClick = {
                    reload()
                    onMessage("${KeystoreVault.count(context)} keystore bulundu.")
                },
                modifier = Modifier.fillMaxWidth().height(if (keystoreCompact) 48.dp else 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9ACEFF), contentColor = Color(0xFF0D213D))
            ) {
                Text(t(languageCode, "find_backups"), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().height(if (keystoreCompact) 48.dp else 52.dp)
            ) {
                Text(t(languageCode, "import_keystore"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ManagedKeystoreCard(
    item: ManagedKeystore,
    languageCode: String,
    onCopy: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    val managedKeyCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (managedKeyCompact) 18.dp else 22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (managedKeyCompact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF23344E)),
                    shape = RoundedCornerShape(if (managedKeyCompact) 13.dp else 16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(if (managedKeyCompact) 42.dp else 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔒", fontSize = if (managedKeyCompact) 20.sp else 24.sp)
                    }
                }

                Spacer(Modifier.width(if (managedKeyCompact) 9.dp else 12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (managedKeyCompact) 17.sp else 20.sp
                )
                    Text(item.algorithm, color = TextSecondary, fontSize = 12.sp)
                    Text(item.savedPath, color = TextSecondary, fontSize = 12.sp)
                }

                TextButton(onClick = onDelete) {
                    Text(t(languageCode, "delete"))
                }
            }

            Text("Sertifika parmak izleri", fontWeight = FontWeight.Bold)
            FingerprintBox("SHA-1", item.sha1)
            FingerprintBox("SHA-256", item.sha256)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onCopy(item.sha1, "SHA-1") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(t(languageCode, "copy_sha1"))
                }
                OutlinedButton(
                    onClick = { onCopy(item.sha256, "SHA-256") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(t(languageCode, "copy_sha256"))
                }
            }
        }
    }
}

@Composable
private fun FingerprintBox(
    label: String,
    value: String
) {
    val fingerprintCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF253246)),
        shape = RoundedCornerShape(if (fingerprintCompact) 12.dp else 14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(if (fingerprintCompact) 10.dp else 14.dp)) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(value, fontSize = 12.sp)
        }
    }
}

private fun applyTemplate(current: ProjectDraft, template: RemoteTemplate): ProjectDraft {
    return runCatching {
        val obj = JSONObject(template.configJson)
        val features = obj.optJSONObject("features")

        current.copy(
            sourceMode = runCatching {
                SourceMode.valueOf(obj.optString("sourceMode", current.sourceMode.name))
            }.getOrDefault(current.sourceMode),
            orientation = obj.optString("orientation", current.orientation),
            appCategory = obj.optString("appCategory", current.appCategory),
            fileUpload = features?.optBoolean("fileUpload", current.fileUpload) ?: current.fileUpload,
            downloads = features?.optBoolean("downloads", current.downloads) ?: current.downloads,
            fullscreen = features?.optBoolean("fullscreen", current.fullscreen) ?: current.fullscreen,
            camera = features?.optBoolean("camera", current.camera) ?: current.camera,
            microphone = features?.optBoolean("microphone", current.microphone) ?: current.microphone,
            location = features?.optBoolean("location", current.location) ?: current.location,
            networkState = features?.optBoolean("networkState", current.networkState) ?: current.networkState,
            wakeLock = features?.optBoolean("wakeLock", current.wakeLock) ?: current.wakeLock,
            nfc = features?.optBoolean("nfc", current.nfc) ?: current.nfc,
            additionalPermissions =
                features?.optJSONArray("additionalPermissions")
                    ?.let { array ->
                        buildSet {
                            for (index in 0 until array.length()) {
                                array.optString(index)
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }
                    }
                    ?: current.additionalPermissions,
            notifications = features?.optBoolean("notifications", current.notifications) ?: current.notifications,
            offlineCache = features?.optBoolean("offlineCache", current.offlineCache) ?: current.offlineCache,

            javascriptBridge =
                features?.optBoolean(
                    "javascriptBridge",
                    current.javascriptBridge
                ) ?: current.javascriptBridge,

            shareBridge =
                features?.optBoolean(
                    "shareBridge",
                    current.shareBridge
                ) ?: current.shareBridge,

            clipboardBridge =
                features?.optBoolean(
                    "clipboardBridge",
                    current.clipboardBridge
                ) ?: current.clipboardBridge,

            vibrationBridge =
                features?.optBoolean(
                    "vibrationBridge",
                    current.vibrationBridge
                ) ?: current.vibrationBridge,

            mediaPlayerBridge =
                features?.optBoolean(
                    "mediaPlayerBridge",
                    current.mediaPlayerBridge
                ) ?: current.mediaPlayerBridge,

            qrScanner =
                features?.optBoolean(
                    "qrScanner",
                    current.qrScanner
                ) ?: current.qrScanner
        )
    }.getOrDefault(current)
}

private fun onOff(v: Boolean) = if (v) "Açık" else "Kapalı"

@Composable
private fun Section(
    title: String,
    subtitle: String
) {
    val compact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                if (compact) {
                    3.dp
                } else {
                    4.dp
                }
            )
    ) {
        Text(
            text = title,
            fontSize =
                if (compact) {
                    20.sp
                } else {
                    22.sp
                },
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text = subtitle,
            color =
                TextSecondary,
            fontSize =
                if (compact) {
                    12.sp
                } else {
                    14.sp
                },
            lineHeight =
                if (compact) {
                    16.sp
                } else {
                    19.sp
                }
        )
    }
}

@Composable
private fun Toggle(
    label: String,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    val compact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = Card2
            ),
        shape =
            RoundedCornerShape(
                if (compact) {
                    14.dp
                } else {
                    16.dp
                }
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        if (compact) {
                            12.dp
                        } else {
                            15.dp
                        }
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier =
                    Modifier.weight(1f),
                fontSize =
                    if (compact) {
                        13.sp
                    } else {
                        14.sp
                    },
                lineHeight =
                    if (compact) {
                        17.sp
                    } else {
                        19.sp
                    }
            )

            Spacer(
                Modifier.width(
                    if (compact) {
                        8.dp
                    } else {
                        12.dp
                    }
                )
            )

            Switch(
                checked = value,
                onCheckedChange = onChange
            )
        }
    }
}

@Composable
private fun ColorField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text("HEX: #RRGGBB") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NoteCard(text: String) {
    val noteCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (noteCompact) 14.dp else 18.dp)
    ) {
        Text(
            text,
            modifier =
                Modifier.padding(
                    if (noteCompact) 11.dp else 14.dp
                ),
            color = TextSecondary,
            fontSize =
                if (noteCompact) 12.sp else 14.sp,
            lineHeight =
                if (noteCompact) 17.sp else 20.sp
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val infoCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (infoCompact) 14.dp else 18.dp)
    ) {
        if (infoCompact) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(11.dp),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label,
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Text(
                    value,
                    fontSize = 12.sp
                )
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    label,
                    color = TextSecondary,
                    modifier = Modifier.width(80.dp)
                )

                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp
                )
            }
        }
    }
}


private fun artifactDownloadName(
    appName: String,
    buildId: String,
    extension: String
): String {
    val safeName =
        appName
            .trim()
            .replace(
                Regex("""[^\p{L}\p{N}._-]+"""),
                "_"
            )
            .trim(
                '_',
                '-',
                '.'
            )
            .take(60)
            .ifBlank {
                "AppForge-App"
            }

    val safeExtension =
        when (
            extension
                .trim()
                .lowercase()
        ) {
            "aab" ->
                "aab"

            "exe" ->
                "exe"

            else ->
                "apk"
        }

    return "$safeName.$safeExtension"
}


private fun formatBuildDuration(
    durationMs: Long
): String {
    val totalSeconds =
        (
            durationMs /
                1000L
        ).coerceAtLeast(
            0L
        )

    val hours =
        totalSeconds /
            3600L

    val minutes =
        (
            totalSeconds %
                3600L
        ) /
            60L

    val seconds =
        totalSeconds %
            60L

    return if (
        hours > 0L
    ) {
        String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    } else {
        String.format(
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}
