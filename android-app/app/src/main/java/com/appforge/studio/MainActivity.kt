@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.build.BuildApiClient
import com.appforge.studio.build.BuildCompareResult
import com.appforge.studio.build.TestLabResult
import com.appforge.studio.ai.AppForgeLocalAssistant
import com.appforge.studio.ai.LocalAiBackend
import com.appforge.studio.ai.LocalAiModelInfo
import com.appforge.studio.ai.LocalAiModelStore
import com.appforge.studio.ai.LocalAiModelDownloader
import com.appforge.studio.i18n.StudioI18n
import com.appforge.studio.io.AppSettingsStore
import com.appforge.studio.io.KeystoreVault
import com.appforge.studio.io.ManagedKeystore
import com.appforge.studio.io.ProjectBackupManager
import com.appforge.studio.io.ProjectImporter
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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

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

        /*
         * APK yükleme izni ekranından döndüğünde
         * kuruluma otomatik devam et.
         */
        val installerPrefs =
            getSharedPreferences(
                "appforge_installer",
                Context.MODE_PRIVATE
            )

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
}

private val Bg = Color(0xFF08070D)
private val Card2 = Color(0xFF111820)
private val Accent = Color(0xFF8CC9F6)
private val TextSecondary = Color(0xFFA5ADB7)

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


private enum class AppScreen { HOME, MODE_SELECT, QUICK, BUILDER, PREVIEW, PRODUCTION, TEST_LAB, AI_ASSISTANT, LIBRARY, HISTORY, ACCOUNT, TEMPLATES, SETTINGS, LEGAL, HELP, PLAY_GUIDE, PRO, KEYSTORES, LANGUAGE }

@Composable
private fun AppForgeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(ProjectDraft()) }
    var currentProjectId by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var step by remember { mutableIntStateOf(1) }

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
                AppScreen.PREVIEW ||
            screen ==
                AppScreen.PRODUCTION ||
            screen ==
                AppScreen.AI_ASSISTANT ||
            screen ==
                AppScreen.HISTORY ||
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
    var logs by remember { mutableStateOf(listOf<String>()) }
    var preflight by remember { mutableStateOf(listOf<String>()) }
    var buildId by remember { mutableStateOf<String?>(null) }
    var apkUrl by remember { mutableStateOf<String?>(null) }
    var aabUrl by remember { mutableStateOf<String?>(null) }

    val sourcePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val key = draft.packageName.replace(".", "_").ifBlank { "project" }
                val result = ProjectImporter.importLocalSource(context, uri, key)
                draft = draft.copy(
                    sourceUri = uri.toString(),
                    sourceLabel = uri.lastPathSegment ?: "Seçili dosya",
                    importedFolder = result.projectDir.absolutePath,
                    startPage = result.startPage.absolutePath
                )
                status = "Kaynak hazır: ${result.startPage.name}"
            } catch (t: Throwable) {
                status = "Hata: ${t.message}"
            }
        }
    }

    val keystorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
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
            draft = draft.copy(
                iconUri = uri.toString(),
                iconName = uri.lastPathSegment ?: "icon.png"
            )
            status = "Uygulama ikonu seçildi."
        }
    }

    val firebasePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            draft = draft.copy(
                firebaseConfigUri = uri.toString(),
                firebaseConfigName = uri.lastPathSegment ?: "google-services.json"
            )
            status = "Firebase yapılandırması seçildi."
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

    val backupImportLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenDocument()
        ) {
            uri: Uri? ->
            if (uri != null) {
                try {
                    val imported =
                        ProjectBackupManager
                            .importFromUri(
                                context,
                                uri
                            )

                    draft =
                        imported.draft

                    currentProjectId =
                        null

                    serverUrl =
                        draft.buildServiceUrl

                    status =
                        "AppForge proje yedeği içe aktarıldı."

                    step = 1
                    screen =
                        AppScreen.BUILDER
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


    val startBuildWithDraft: (ProjectDraft) -> Unit = { buildDraft ->
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
            status = "Derleme hazırlanıyor..."
            progress = 2
            logs = emptyList()
            preflight = emptyList()
            apkUrl = null
            aabUrl = null

            try {
                validateDraft(
                    effectiveBuildDraft,
                    serverUrl
                )

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

                            ZipUtils.zipDirectory(
                                sourceDir,
                                File(
                                    context.cacheDir,
                                    "build-upload/project.zip"
                                )
                            )
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

                val created =
                    withContext(
                        Dispatchers.IO
                    ) {
                        client.createBuild(
                            effectiveBuildDraft,
                            zip,
                            idempotencyKey =
                                "android-${effectiveBuildDraft.packageName}-${System.currentTimeMillis()}"
                        )
                    }

                buildId =
                    created.buildId

                status =
                    created.status

                screen =
                    AppScreen.BUILDER

                step = 9

                while (true) {
                    delay(1500)

                    val s =
                        withContext(
                            Dispatchers.IO
                        ) {
                            client.getBuild(
                                created.buildId
                            )
                        }

                    status =
                        s.status

                    progress =
                        s.progress

                    logs =
                        s.logs

                    preflight =
                        s.preflight

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

                    if (
                        s.status ==
                            "success" ||
                        s.status ==
                            "failed" ||
                        s.status ==
                            "cancelled"
                    ) {
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
                            }
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

                step = 9
            }
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
                AppScreen.HOME ->
                    StudioHomeScreen(
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

                        onOpenPro = {
                            screen =
                                AppScreen.PRO
                        }
                    )

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
                                    "image/png"
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
                        draft = applyTemplate(draft, template)
                        status = "Şablon uygulandı: ${template.name}"
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

                AppScreen.HELP -> HowToUseCenterScreen(
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
                                Text("v3.0 Local AI Assistant", fontSize = 12.sp, color = TextSecondary)
                            }
                        },
                        navigationIcon = {
                            LabeledActionButton(
                                icon = "←",
                                label = "Geri",
                                onClick = {
                                    screen =
                                        AppScreen.HOME
                                }
                            )
                        },
                        actions = {},
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                    )

                    BuilderShortcutBar(
                        onHome = {
                            screen = AppScreen.HOME
                        },
                        onHistory = {
                            openWorkspaceScreen(
                                AppScreen.HISTORY
                            )
                        },
                        onTemplates = {
                            openWorkspaceScreen(
                                AppScreen.TEMPLATES
                            )
                        },
                        onAi = {
                            openWorkspaceScreen(
                                AppScreen.AI_ASSISTANT
                            )
                        },
                        onSettings = {
                            openWorkspaceScreen(
                                AppScreen.SETTINGS
                            )
                        },
                        onAccount = {
                            openWorkspaceScreen(
                                AppScreen.ACCOUNT
                            )
                        }
                    )

                    LinearProgressIndicator(
                        progress = { step / 9f },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        10.dp,
                                    vertical =
                                        4.dp
                                ),
                        horizontalArrangement =
                            Arrangement
                                .spacedBy(
                                    8.dp
                                )
                    ) {
                        OutlinedButton(
                            onClick = {
                                openWorkspaceScreen(
                                    AppScreen.PREVIEW
                                )
                            },
                            modifier =
                                Modifier
                                    .weight(
                                        1f
                                    )
                        ) {
                            Text(
                                "👁 Önizleme"
                            )
                        }

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
                                "🚀 Production"
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
                                "✨ AI"
                            )
                        }
                    }

                    Box(Modifier.weight(1f)) {
                        when (step) {
                            1 -> SourceStep(draft, status, { draft = it }) {
                                sourcePicker.launch(arrayOf("text/html", "application/zip", "application/octet-stream"))
                            }

                            2 -> FeaturesStep(draft) { draft = it }

                            3 -> AppearanceStep(draft, { draft = it }) {
                                iconPicker.launch(arrayOf("image/png"))
                            }

                            4 -> NativeBridgeStep(draft) { draft = it }

                            5 -> MonetizationStep(
                                draft = draft,
                                update = { draft = it },
                                onPickFirebase = {
                                    firebasePicker.launch(arrayOf("application/json", "text/json", "text/plain"))
                                }
                            )

                            6 -> DeepLinkStep(draft) { draft = it }

                            7 -> SigningStep(draft, { draft = it }) {
                                keystorePicker.launch(arrayOf(
                                    "application/octet-stream",
                                    "application/x-java-keystore"
                                ))
                            }

                            8 -> BuildSettingsStep(
                                draft = draft,
                                update = { draft = it },
                                serverUrl = serverUrl,
                                apiKey = apiKey,
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

                                        status =
                                            "Proje kütüphaneye kaydedildi."
                                    }
                                }
                            )

                            else -> BuildStep(
                                status = status,
                                progress = progress,
                                logs = logs,
                                preflight = preflight,
                                buildId = buildId,
                                appName = draft.appName,
                                serverUrl = serverUrl,
                                apiKey = apiKey,
                                apkUrl = apkUrl,
                                aabUrl = aabUrl
                            )
                        }
                    }

                    Row(
                        Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (step > 1) {
                            OutlinedButton(
                                onClick = { step-- },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Text("Geri")
                            }
                        }

                        Button(
                            onClick = {
                                if (step < 9) {
                                    step++
                                } else {
                                    startBuildWithDraft(
                                        draft
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text(if (step == 9) "UYGULAMAYI DERLE" else "Devam")
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
        qrScanner =
            false,
        admobEnabled =
            false,
        billingEnabled =
            false,
        firebaseAnalyticsEnabled =
            false,
        firebaseCrashlyticsEnabled =
            false
    )

@Composable
private fun CreateModeSelectionScreen(
    onQuick: () -> Unit,
    onAdvanced: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment =
            Alignment.Center
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(
                        max = 620.dp
                    ),
            shape =
                RoundedCornerShape(
                    30.dp
                ),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            Color(
                                0xFF0E1519
                            )
                    )
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal =
                                28.dp,
                            vertical =
                                30.dp
                        ),
                verticalArrangement =
                    Arrangement
                        .spacedBy(
                            20.dp
                        )
            ) {
                Text(
                    text =
                        "Nasıl oluşturmak istersin?",
                    fontSize =
                        28.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                CreateModeCard(
                    icon = "⚡",
                    title =
                        "Hızlı Oluştur",
                    description =
                        "Sadece isim, içerik ve ikon. Gerisini AppForge otomatik ayarlar.",
                    onClick =
                        onQuick
                )

                CreateModeCard(
                    icon = "☷",
                    title =
                        "Gelişmiş Oluştur",
                    description =
                        "Paket adı, tema, izinler, Native Bridge, Billing ve imzalama üzerinde tam kontrol.",
                    onClick =
                        onAdvanced
                )

                Text(
                    text =
                        "v2.0 • Hızlı modda güvenli varsayılanlar kullanılır.",
                    fontSize =
                        12.sp,
                    color =
                        TextSecondary
                )
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
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth(),
        shape =
            RoundedCornerShape(
                24.dp
            ),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Color(
                            0xFF171E22
                        )
                )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            22.dp,
                        vertical =
                            24.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize =
                    36.sp,
                modifier =
                    Modifier
                        .width(
                            70.dp
                        )
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f),
                verticalArrangement =
                    Arrangement
                        .spacedBy(
                            6.dp
                        )
            ) {
                Text(
                    text = title,
                    fontSize =
                        21.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        description,
                    fontSize =
                        15.sp,
                    color =
                        TextSecondary,
                    lineHeight =
                        21.sp
                )
            }

            Text(
                text = "⋮",
                fontSize =
                    28.sp,
                color =
                    TextSecondary
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
                            FontWeight.Bold
                    )

                    Text(
                        "İsim + içerik + ikon",
                        fontSize =
                            12.sp,
                        color =
                            TextSecondary
                    )
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
                        onPreview
                ) {
                    Text(
                        "Önizle"
                    )
                }

                TextButton(
                    onClick =
                        onAdvanced
                ) {
                    Text(
                        "Gelişmiş"
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
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    20.dp
                ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        16.dp
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
                                .padding(
                                    18.dp
                                ),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    12.dp
                                )
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
                                .padding(
                                    18.dp
                                ),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    12.dp
                                )
                    ) {
                        Text(
                            "2. İçerik",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement
                                    .spacedBy(
                                        8.dp
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
                                        .height(
                                            52.dp
                                        )
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
                                .padding(
                                    18.dp
                                ),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    12.dp
                                )
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
                                    .height(
                                        52.dp
                                    )
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
                                .padding(
                                    16.dp
                                ),
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
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
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
                                .padding(18.dp),
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
                                .padding(18.dp),
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
                                .padding(18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
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

                        if (
                            draft.firebaseAnalyticsEnabled ||
                            draft.firebaseCrashlyticsEnabled
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
        }        Button(
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
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            20.dp,
                        vertical =
                            16.dp
                    )
                    .height(
                        58.dp
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
        draft.camera
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
                        File(it)
                            .exists()
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

                    Text(
                        "Preview + Console + Network + Performance + Security",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )
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
                        "Check"
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
                    .fillMaxSize(),
            contentPadding =
                PaddingValues(
                    14.dp
                ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        12.dp
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
                            .padding(
                                14.dp
                            ),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    10.dp
                                )
                    ) {
                        Row(
                            horizontalArrangement =
                                Arrangement
                                    .spacedBy(
                                        6.dp
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
                                                        "Console"

                                                    PreviewInspectorTab.NETWORK ->
                                                        "Network"

                                                    PreviewInspectorTab.PERFORMANCE ->
                                                        "Perf"

                                                    PreviewInspectorTab.SECURITY ->
                                                        "Security"
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
                                horizontalArrangement =
                                    Arrangement
                                        .spacedBy(
                                            8.dp
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
                            "Önizlenecek kaynak hazır değil. Yerel modda HTML/ZIP seç veya URL modunda HTTPS adresi gir."
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
                                RoundedCornerShape(
                                    28.dp
                                ),
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
                        "JavaScript Console",
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
                        "Network Inspector",
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
                        "Performance Inspector",
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
                        "Security Center",
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
            Modifier
                .fillMaxWidth()
                .padding(
                    14.dp
                ),
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
                        12.sp
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
                    .padding(
                        12.dp
                    ),
            fontSize =
                11.sp,
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

                    Text(
                        "Önizleme, yayın kontrolü, sürümleme ve proje yedekleri",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )
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
            contentPadding =
                PaddingValues(
                    16.dp
                ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        14.dp
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
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
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
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
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
                Button(
                    onClick =
                        onPreview,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                56.dp
                            )
                ) {
                    Text(
                        "👁  ${t(languageCode, "preview")}",
                        fontWeight =
                            FontWeight.Bold
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
                            .height(
                                56.dp
                            )
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
                        RoundedCornerShape(
                            20.dp
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
                                    7.dp
                                )
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
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                14.dp
                            ),
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    6.dp
                                )
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
                        RoundedCornerShape(
                            20.dp
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
                                    10.dp
                                )
                    ) {
                        InfoLine(
                            "Sürüm",
                            "${draft.versionName} • ${draft.versionCode}"
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement
                                    .spacedBy(
                                        8.dp
                                    )
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
                        RoundedCornerShape(
                            20.dp
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
                                    10.dp
                                )
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
                            "Güvenlik için keystore parolaları, Build API anahtarları ve hassas imzalama bilgileri ZIP yedeğine yazılmaz.",
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
                            .height(
                                52.dp
                            )
                ) {
                    Text(
                        "🧩 Şablon Kataloğunu Aç"
                    )
                }
            }

            item {
                NoteCard(
                    "Production Center yerel hazırlık kontrollerini yapar. Gerçek APK/AAB compile ve cihaz kurulum testi için resmi Build Service sonucu hâlâ esas alınır."
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
                    14.dp
                ),
            horizontalAlignment =
                Alignment
                    .CenterHorizontally
        ) {
            Text(
                value,
                fontSize =
                    23.sp,
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
                    11.sp
            )
        }
    }
}

@Composable
private fun ProductionCheckCard(
    check: ProductionCheck
) {
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
                        14.dp
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
                        14.dp
                    )
            ) {
                Box(
                    Modifier
                        .size(
                            44.dp
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
                            23.sp
                    )
                }
            }

            Spacer(
                Modifier.width(
                    12.dp
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
                        18.sp,
                    fontSize =
                        12.sp
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
                    "${remoteBuilds.size} başarılı build bulundu."
            } catch (
                t: Throwable
            ) {
                message =
                    "Build geçmişi alınamadı: ${t.message}"
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
        if (
            apiKey.isNotBlank()
        ) {
            loadHistory()
        } else {
            message =
                "Test Lab için Build Service API anahtarı gerekli."
        }
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Test Lab",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "APK/AAB Analyzer • Security • Compare • Release Notes",
                        color =
                            TextSecondary,
                        fontSize =
                            12.sp
                    )
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
            contentPadding =
                PaddingValues(
                    14.dp
                ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        12.dp
                    )
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
                    "Başarılı Build'ler",
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
                        RoundedCornerShape(
                            18.dp
                        )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                14.dp
                            ),
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
                            "${item.packageName}\n${item.buildId}",
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
                                    "Analiz Et"
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
                                        "Build karşılaştırması hazır."
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
                                .height(
                                    52.dp
                                )
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
                        "Artifact Size Analyzer",
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
                        "Security Center",
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
                                .padding(
                                    14.dp
                                )
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
                            "Release Notes Generator",
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
                        "Build Compare",
                        "${compare.leftBuildId.take(8)}… → ${compare.rightBuildId.take(8)}…"
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
    languageCode: String,
    modelInfo: LocalAiModelInfo?,
    importMessage: String,
    installing: Boolean,
    installProgress: Int,
    onInstallDefaultModel: () -> Unit,
    onImportModel: () -> Unit,
    onModelChanged: (LocalAiModelInfo?) -> Unit,
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

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
        remember {
            mutableStateOf(
                true
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

    fun sendQuestion() {
        val question =
            input.trim()

        if (
            question.isBlank() ||
            generating
        ) {
            return
        }

        if (!initialized) {
            status =
                "Yerel AI hazırlanıyor. Birkaç saniye sonra tekrar dene."
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
                            includeProjectContext
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

                    Text(
                        "Cihazda çalışan Yerel AI • Çevrimdışı",
                        fontSize =
                            12.sp,
                        color =
                            TextSecondary
                    )
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
                    .weight(
                        1f
                    )
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    14.dp
                ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        10.dp
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
                            22.dp
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
                                    "Uygulama adı, package, sürüm, build ve açık özellikler AI bağlamına eklenir; parola/API anahtarı eklenmez.",
                                    color =
                                        TextSecondary,
                                    fontSize =
                                        11.sp
                                )
                            }

                            Switch(
                                checked =
                                    includeProjectContext,
                                onCheckedChange = {
                                    includeProjectContext =
                                        it
                                }
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
                    "Yerel Asistan AI yanıtı için AppForge Build Service veya başka bir bulut LLM API'si çağırmaz. Model cihazda çalışır. Güncel internet bilgilerini kendiliğinden kontrol edemez."
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
                        listOf(
                            "Proje neden build olmuyor?",
                            "Free ve Pro arasındaki fark ne?",
                            "APK boyutunu nasıl küçültürüm?",
                            "Play Store için hangi ayarlar eksik?",
                            "Bu projede hangi izinler açık?"
                        ).forEach {
                            prompt ->
                            OutlinedButton(
                                onClick = {
                                    input =
                                        prompt
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
                    18.dp
                ),
            modifier =
                Modifier
                    .fillMaxWidth(
                        0.9f
                    )
        ) {
            Column(
                Modifier.padding(
                    13.dp
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

    if (d.firebaseAnalyticsEnabled || d.firebaseCrashlyticsEnabled) {
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
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Section("1. Kaynak", "HTML/ZIP veya HTTPS web adresi.") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = d.sourceMode == SourceMode.LOCAL,
                    onClick = { update(d.copy(sourceMode = SourceMode.LOCAL)) },
                    label = { Text("HTML / ZIP") }
                )
                FilterChip(
                    selected = d.sourceMode == SourceMode.URL,
                    onClick = { update(d.copy(sourceMode = SourceMode.URL)) },
                    label = { Text("Web URL") }
                )
            }
        }

        item {
            OutlinedTextField(
                value = d.appName,
                onValueChange = { appName ->
                    update(
                        d.copy(
                            appName = appName,
                            packageName = quickPackageName(appName)
                        )
                    )
                },
                label = { Text("Uygulama adı") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = d.packageName,
                onValueChange = { update(d.copy(packageName = it)) },
                label = { Text("Paket adı") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (d.sourceMode == SourceMode.LOCAL) {
            item {
                Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                    Text(if (d.sourceLabel.isBlank()) "HTML veya ZIP seç" else d.sourceLabel)
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = d.webUrl,
                    onValueChange = { update(d.copy(webUrl = it.trim())) },
                    label = { Text("https://site.com") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item { NoteCard(status) }
    }
}

@Composable
private fun FeaturesStep(d: ProjectDraft, update: (ProjectDraft) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Section("2. WebView Pro", "Android özelliklerini seç.") }
        item { Toggle("Dosya yükleme", d.fileUpload) { update(d.copy(fileUpload = it)) } }
        item { Toggle("DownloadManager", d.downloads) { update(d.copy(downloads = it)) } }
        item { Toggle("Kamera ile fotoğraf", d.camera) { update(d.copy(camera = it)) } }
        item { Toggle("Konum / Geolocation", d.location) { update(d.copy(location = it)) } }
        item { Toggle("Bildirim izni", d.notifications) { update(d.copy(notifications = it)) } }
        item { Toggle("Offline cache", d.offlineCache) { update(d.copy(offlineCache = it)) } }
        item { Toggle("Tam ekran", d.fullscreen) { update(d.copy(fullscreen = it)) } }
    }
}

@Composable
private fun AppearanceStep(
    d: ProjectDraft,
    update: (ProjectDraft) -> Unit,
    onPickIcon: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("3. Görünüm", "İkon, tema ve Splash.") }

        item {
            Button(onClick = onPickIcon, modifier = Modifier.fillMaxWidth()) {
                Text(if (d.iconName.isBlank()) "PNG Uygulama İkonu Seç" else "İkon: ${d.iconName}")
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("unspecified", "portrait", "landscape").forEach {
                    FilterChip(
                        selected = d.orientation == it,
                        onClick = { update(d.copy(orientation = it)) },
                        label = { Text(it) }
                    )
                }
            }
        }

        item { ColorField("Ana renk", d.primaryColor) { update(d.copy(primaryColor = it)) } }
        item { ColorField("Arka plan", d.backgroundColor) { update(d.copy(backgroundColor = it)) } }
        item { ColorField("Status bar", d.statusBarColor) { update(d.copy(statusBarColor = it)) } }
        item { ColorField("Navigation bar", d.navigationBarColor) { update(d.copy(navigationBarColor = it)) } }
        item { Toggle("Android 12+ Splash", d.splashEnabled) { update(d.copy(splashEnabled = it)) } }

        if (d.splashEnabled) {
            item {
                OutlinedTextField(
                    value = d.splashText,
                    onValueChange = { update(d.copy(splashText = it)) },
                    label = { Text("Splash alt yazısı") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NativeBridgeStep(d: ProjectDraft, update: (ProjectDraft) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Section("4. Native Bridge", "Web sayfasından Android özellikleri.") }
        item { Toggle("JavaScript Bridge", d.javascriptBridge) { update(d.copy(javascriptBridge = it)) } }

        if (d.javascriptBridge) {
            if (d.sourceMode == SourceMode.URL) {
                item {
                    Toggle(
                        "Uzak URL'de Native Bridge'e izin ver",
                        d.remoteBridgeAllowed
                    ) {
                        update(d.copy(remoteBridgeAllowed = it))
                    }
                }
                if (d.remoteBridgeAllowed) {
                    item {
                        NoteCard(
                            "Uzak Native Bridge yalnız seçilen HTTPS origininde çalışır. Yine de yalnız tamamen güvendiğin site için aç."
                        )
                    }
                }
            }

            item { Toggle("Paylaşım", d.shareBridge) { update(d.copy(shareBridge = it)) } }
            item { Toggle("Panoya kopyalama", d.clipboardBridge) { update(d.copy(clipboardBridge = it)) } }
            item { Toggle("Titreşim / Haptic", d.vibrationBridge) { update(d.copy(vibrationBridge = it)) } }
            item { Toggle("QR / Barkod Tarayıcı", d.qrScanner) { update(d.copy(qrScanner = it)) } }
        }
    }
}

@Composable
private fun MonetizationStep(
    draft: ProjectDraft,
    update: (ProjectDraft) -> Unit,
    onPickFirebase: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("5. Para Kazanma + Firebase", "AdMob, Billing, Analytics ve Crashlytics.") }

        item { Toggle("AdMob", draft.admobEnabled) { update(draft.copy(admobEnabled = it)) } }

        if (draft.admobEnabled) {
            item {
                OutlinedTextField(
                    value = draft.admobAppId,
                    onValueChange = { update(draft.copy(admobAppId = it.trim())) },
                    label = { Text("AdMob App ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = draft.admobBannerUnitId,
                    onValueChange = { update(draft.copy(admobBannerUnitId = it.trim())) },
                    label = { Text("Banner Ad Unit ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = draft.admobInterstitialUnitId,
                    onValueChange = { update(draft.copy(admobInterstitialUnitId = it.trim())) },
                    label = { Text("Interstitial Ad Unit ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = draft.admobRewardedUnitId,
                    onValueChange = { update(draft.copy(admobRewardedUnitId = it.trim())) },
                    label = { Text("Rewarded Ad Unit ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Toggle("UMP / GDPR", draft.umpConsentEnabled) {
                    update(draft.copy(umpConsentEnabled = it))
                }
            }
        }

        item {
            Toggle("Google Play Billing", draft.billingEnabled) {
                update(draft.copy(billingEnabled = it))
            }
        }

        if (draft.billingEnabled) {
            item {
                OutlinedTextField(
                    value = draft.billingProductIds,
                    onValueChange = { update(draft.copy(billingProductIds = it)) },
                    label = { Text("Kalıcı ürün ID'leri") },
                    supportingText = { Text("premium,remove_ads") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = draft.billingSubscriptionIds,
                    onValueChange = { update(draft.copy(billingSubscriptionIds = it)) },
                    label = { Text("Abonelik ID'leri") },
                    supportingText = { Text("pro_monthly,pro_yearly") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = draft.consumableProductIds,
                    onValueChange = { update(draft.copy(consumableProductIds = it)) },
                    label = { Text("Consumable ürün ID'leri") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = draft.removeAdsProductId,
                    onValueChange = { update(draft.copy(removeAdsProductId = it.trim())) },
                    label = { Text("Reklam kaldırma ürün ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = draft.purchaseVerificationUrl,
                    onValueChange = { update(draft.copy(purchaseVerificationUrl = it.trim())) },
                    label = { Text("Satın alma doğrulama URL") },
                    supportingText = { Text("https://api.site.com/api/verify-purchase") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item { Text("Firebase", fontWeight = FontWeight.Bold) }

        item {
            Toggle("Firebase Analytics", draft.firebaseAnalyticsEnabled) {
                update(draft.copy(firebaseAnalyticsEnabled = it))
            }
        }

        item {
            Toggle("Firebase Crashlytics", draft.firebaseCrashlyticsEnabled) {
                update(draft.copy(firebaseCrashlyticsEnabled = it))
            }
        }

        if (draft.firebaseAnalyticsEnabled || draft.firebaseCrashlyticsEnabled) {
            item {
                Button(onClick = onPickFirebase, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (draft.firebaseConfigName.isBlank())
                            "google-services.json SEÇ"
                        else
                            "Firebase: ${draft.firebaseConfigName}"
                    )
                }
            }
        }
    }
}

@Composable
private fun DeepLinkStep(d: ProjectDraft, update: (ProjectDraft) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("6. Deep Link", "Web bağlantılarından uygulamayı aç.") }
        item { Toggle("Deep Link aktif", d.deepLinkEnabled) { update(d.copy(deepLinkEnabled = it)) } }

        if (d.deepLinkEnabled) {
            item {
                OutlinedTextField(
                    value = d.deepLinkScheme,
                    onValueChange = { update(d.copy(deepLinkScheme = it.trim())) },
                    label = { Text("Scheme") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = d.deepLinkHost,
                    onValueChange = { update(d.copy(deepLinkHost = it.trim())) },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = d.deepLinkPathPrefix,
                    onValueChange = {
                        update(d.copy(deepLinkPathPrefix = it.trim().ifBlank { "/" }))
                    },
                    label = { Text("Path prefix") },
                    modifier = Modifier.fillMaxWidth()
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
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("7. İmzalama", "Debug veya kendi release keystore'un.") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = d.signingMode == SigningMode.DEBUG,
                    onClick = { update(d.copy(signingMode = SigningMode.DEBUG)) },
                    label = { Text("Debug") }
                )
                FilterChip(
                    selected = d.signingMode == SigningMode.CUSTOM,
                    onClick = { update(d.copy(signingMode = SigningMode.CUSTOM)) },
                    label = { Text("Kendi Keystore'um") }
                )
            }
        }

        if (d.signingMode == SigningMode.CUSTOM) {
            item {
                Button(onClick = onPickKeystore, modifier = Modifier.fillMaxWidth()) {
                    Text(if (d.keystoreName.isBlank()) "JKS / Keystore seç" else d.keystoreName)
                }
            }
            item {
                OutlinedTextField(
                    value = d.keyAlias,
                    onValueChange = { update(d.copy(keyAlias = it)) },
                    label = { Text("Key alias") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = d.storePassword,
                    onValueChange = { update(d.copy(storePassword = it)) },
                    label = { Text("Store password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = d.keyPassword,
                    onValueChange = { update(d.copy(keyPassword = it)) },
                    label = { Text("Key password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
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
    onServerUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Section("8. Build Service", "Sunucu, API anahtarı ve çıktı türü.") }

        item {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = {
                    Text("Build Service URL")
                },
                supportingText = {
                    Text(
                        "AppForge resmi Build Service • Değiştirilemez"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKey,
                label = { Text("Build API Key") },
                supportingText = { Text("Sunucuda APPFORGE_API_KEY ayarlıysa gerekli.") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("apk", "aab", "both").forEach {
                    FilterChip(
                        selected = draft.buildOutput == it,
                        onClick = { update(draft.copy(buildOutput = it)) },
                        label = { Text(it.uppercase()) }
                    )
                }
            }
        }

        item {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("PROJEYİ KAYDET / GÜNCELLE")
            }
        }

        item {
            NoteCard(
                "Firebase ${if (draft.firebaseConfigUri != null) "Hazır" else "Yok"} • Billing ${onOff(draft.billingEnabled)} • Crashlytics ${onOff(draft.firebaseCrashlyticsEnabled)}"
            )
        }
    }
}

@Composable
private fun BuildStep(
    status: String,
    progress: Int,
    logs: List<String>,
    preflight: List<String>,
    buildId: String?,
    appName: String,
    serverUrl: String,
    apiKey: String,
    apkUrl: String?,
    aabUrl: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadMessage by remember { mutableStateOf("") }
    var apkDownloadId by
        remember(buildId) {
            mutableStateOf<Long?>(
                null
            )
        }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("9. Derleme", "Güvenli Build Service + Play Store ön-kontrol.") }
        item { Text("Durum: $status", color = TextSecondary) }

        item {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (buildId != null) item { InfoLine("Build ID", buildId) }

        if (preflight.isNotEmpty()) {
            item { Text("Preflight", fontWeight = FontWeight.Bold) }
            items(preflight) { NoteCard(it) }
        }

        if (apkUrl != null) {
            item {
                Button(
                    onClick = {
                        val id = buildId ?: return@Button
                        scope.launch {
                            try {
                                val ticket = withContext(Dispatchers.IO) {
                                    BuildApiClient(context, serverUrl, apiKey)
                                        .createDownloadTicket(id, "apk")
                                }
                                val request =
                                    DownloadManager.Request(
                                        Uri.parse(ticket.url)
                                    )
                                        .setTitle("AppForge APK")
                                        .setDescription("APK indiriliyor")
                                        .setMimeType(
                                            "application/vnd.android.package-archive"
                                        )
                                        .setNotificationVisibility(
                                            DownloadManager.Request
                                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                        )
                                        .setAllowedOverMetered(true)
                                        .setAllowedOverRoaming(true)
                                        .setDestinationInExternalPublicDir(
                                            Environment.DIRECTORY_DOWNLOADS,
                                            artifactDownloadName(appName, id, "apk")
                                        )

                                val manager =
                                    context.getSystemService(
                                        Context.DOWNLOAD_SERVICE
                                    ) as DownloadManager

                                val queuedId =
                                    manager.enqueue(request)

                                apkDownloadId =
                                    queuedId

                                downloadMessage =
                                    "APK indiriliyor • İndirme tamamlanınca APK'YI KUR butonuna bas."
                            } catch (t: Throwable) {
                                downloadMessage = "İndirme hatası: ${t.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("APK'YI İNDİR") }
            }
        }

        if (apkDownloadId != null) {
            item {
                Button(
                    onClick = {
                        val id =
                            apkDownloadId
                                ?: return@Button

                        scope.launch {

                            downloadMessage =
                                "APK hazırlanıyor • indirme kontrol ediliyor..."

                            val waitError =
                                waitForApkDownload(
                                    context = context,
                                    downloadId = id
                                )

                            if (
                                waitError != null
                            ) {
                                downloadMessage =
                                    waitError

                                return@launch
                            }

                            downloadMessage =
                                installDownloadedApk(
                                    context = context,
                                    downloadId = id
                                )
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text("APK'YI KUR")
                }
            }
        }

        if (aabUrl != null) {
            item {
                Button(
                    onClick = {
                        val id = buildId ?: return@Button
                        scope.launch {
                            try {
                                val ticket = withContext(Dispatchers.IO) {
                                    BuildApiClient(context, serverUrl, apiKey)
                                        .createDownloadTicket(id, "aab")
                                }
                                val request =
                                    DownloadManager.Request(
                                        Uri.parse(ticket.url)
                                    )
                                        .setTitle("AppForge AAB")
                                        .setDescription("AAB indiriliyor")
                                        .setMimeType(
                                            "application/octet-stream"
                                        )
                                        .setNotificationVisibility(
                                            DownloadManager.Request
                                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                        )
                                        .setAllowedOverMetered(true)
                                        .setAllowedOverRoaming(true)
                                        .setDestinationInExternalPublicDir(
                                            Environment.DIRECTORY_DOWNLOADS,
                                            artifactDownloadName(appName, id, "aab")
                                        )

                                val manager =
                                    context.getSystemService(
                                        Context.DOWNLOAD_SERVICE
                                    ) as DownloadManager

                                manager.enqueue(request)

                                downloadMessage =
                                    "AAB indiriliyor • İndirilenler klasörüne kaydedilecek."
                            } catch (t: Throwable) {
                                downloadMessage = "İndirme hatası: ${t.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("AAB'Yİ İNDİR") }
            }
        }

        if (downloadMessage.isNotBlank()) {
            item { NoteCard(downloadMessage) }
        }

        item { Text("Canlı Gradle logu", fontWeight = FontWeight.Bold) }
        items(logs.takeLast(100)) {
            Text(it, color = TextSecondary, fontSize = 12.sp)
        }
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
            contentPadding =
                PaddingValues(
                    16.dp
                ),
            verticalArrangement =
                Arrangement
                    .spacedBy(
                        10.dp
                    )
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
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    16.dp
                                ),
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
                        Modifier.padding(
                            16.dp
                        )
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
                                Arrangement
                                    .spacedBy(
                                        8.dp
                                    )
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
    val context = LocalContext.current
    val builds = remember { ProjectLibrary.loadBuilds(context) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Build Geçmişi") },
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(builds, key = { it.id }) { b ->
                    Card(colors = CardDefaults.cardColors(containerColor = Card2)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(b.projectName, fontWeight = FontWeight.Bold)
                            Text(b.packageName, color = TextSecondary, fontSize = 12.sp)
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
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("AppForge Hesabı") },
            navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (session != null) {
                item { NoteCard("Giriş yapıldı: ${session.email}") }
                item {
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    val token = withContext(Dispatchers.IO) {
                                        AppForgeAccountClient(serverUrl)
                                            .createBuildApiToken(session.token, "Android App")
                                    }
                                    onApiKeyCreated(token)
                                    message = "Build API token oluşturuldu ve aktif edildi."
                                } catch (t: Throwable) {
                                    message = "Hata: ${t.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("YENİ BUILD API TOKEN OLUŞTUR")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onSession(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Çıkış Yap") }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        val loginResult =
                                            withContext(Dispatchers.IO) {
                                                AppForgeAccountClient(serverUrl)
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
                                            AppForgeAccountClient(serverUrl)
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
    )
)

private fun normalizeTemplateCategory(template: RemoteTemplate): String {
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
                    Text(
                        "Native Android API'lerini kullanan hazır HTML projeleri",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = Card2
                        ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    start = 18.dp,
                    end = 18.dp,
                    top = 6.dp,
                    bottom = 28.dp
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
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
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
                    horizontal = 12.dp,
                    vertical = 10.dp
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
    Card(
        onClick = onOpen,
        colors =
            CardDefaults.cardColors(
                containerColor = spec.container
            ),
        shape =
            RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 18.dp
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
                            .size(64.dp),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        spec.icon,
                        color = spec.accent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    spec.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
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
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = spec.container
            ),
        shape =
            RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${spec.icon}  ${spec.title}",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
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
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = Card2
            ),
        shape =
            RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                template.name,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
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
            "Adım adım kullanım rehberi",
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
            contentPadding = PaddingValues(16.dp),
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
    Card(
        onClick = entry.onClick,
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF23344E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(entry.icon, fontSize = 24.sp)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(entry.subtitle, color = TextSecondary, lineHeight = 18.sp)
            }

            Text("›", fontSize = 28.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun LanguageSettingsScreen(
    languageCode: String,
    onBack: () -> Unit,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "choose_language"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(StudioI18n.languages) { lang ->
                Card(
                    onClick = { onSelect(lang.code) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (lang.code == languageCode) Color(0xFF1B3158) else Card2
                    ),
                    shape = RoundedCornerShape(20.dp)
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
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "legal_title"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    Uri.parse("https://example.com/privacy")
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("$icon  $title", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(body, color = TextSecondary, lineHeight = 21.sp)
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(body, color = TextSecondary, lineHeight = 21.sp)
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
            contentPadding =
                PaddingValues(
                    20.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp
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
                            30.dp
                        )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    24.dp
                                ),
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
                                66.sp,
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
                                27.sp
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
                            .height(
                                54.dp
                            )
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
    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Card2
                ),
        shape =
            RoundedCornerShape(
                26.dp
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
                        20.dp
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
                            18.dp
                        )
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(
                                    58.dp
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
                                28.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    Modifier.width(
                        14.dp
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
                            23.sp
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
                    25.sp,
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
                            54.dp
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
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                Text("Keystore'larınız", fontWeight = FontWeight.Bold, fontSize = 22.sp)
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

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Button(
                onClick = {
                    reload()
                    onMessage("${KeystoreVault.count(context)} keystore bulundu.")
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9ACEFF), contentColor = Color(0xFF0D213D))
            ) {
                Text(t(languageCode, "find_backups"), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().height(52.dp)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF23344E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔒", fontSize = 24.sp)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF253246)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
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
            fileUpload = features?.optBoolean("fileUpload", current.fileUpload) ?: current.fileUpload,
            downloads = features?.optBoolean("downloads", current.downloads) ?: current.downloads,
            fullscreen = features?.optBoolean("fullscreen", current.fullscreen) ?: current.fullscreen,
            camera = features?.optBoolean("camera", current.camera) ?: current.camera,
            offlineCache = features?.optBoolean("offlineCache", current.offlineCache) ?: current.offlineCache
        )
    }.getOrDefault(current)
}

private fun onOff(v: Boolean) = if (v) "Açık" else "Kapalı"

@Composable
private fun Section(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextSecondary)
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Card2), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = value, onCheckedChange = onChange)
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
    Card(colors = CardDefaults.cardColors(containerColor = Card2)) {
        Text(text, modifier = Modifier.padding(14.dp), color = TextSecondary)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Card2)) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, color = TextSecondary, modifier = Modifier.width(80.dp))
            Text(value, fontSize = 12.sp)
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
        if (
            extension.lowercase() ==
            "aab"
        ) {
            "aab"
        } else {
            "apk"
        }

    return "$safeName.$safeExtension"
}
