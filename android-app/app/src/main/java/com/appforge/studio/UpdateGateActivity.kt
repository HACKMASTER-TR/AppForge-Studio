package com.appforge.studio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.appforge.studio.model.DEFAULT_BUILD_SERVICE_URL
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class StudioUpdateState {
    NORMAL,
    OPTIONAL,
    FORCED,
    MAINTENANCE
}

data class StudioUpdatePolicy(
    val state: StudioUpdateState,
    val latestVersionCode: Int,
    val minSupportedVersionCode: Int,
    val message: String,
    val playStoreUrl: String
)

private sealed interface GateUiState {
    data object Loading : GateUiState
    data class Ready(val policy: StudioUpdatePolicy) : GateUiState
    data class Error(
        val message: String,
        val canContinueOffline: Boolean
    ) : GateUiState
}

class UpdateGateActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    private var uiState by mutableStateOf<GateUiState>(GateUiState.Loading)
    private var forcedUpdateLaunching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateGateSession.revoke()
        appUpdateManager = AppUpdateManagerFactory.create(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    UpdateGateContent(
                        state = uiState,
                        currentVersion = BuildConfig.VERSION_NAME,
                        onRetry = ::loadPolicy,
                        onContinue = ::openStudio,
                        onUpdate = ::beginUpdate,
                        onOpenStore = ::openPlayStore
                    )
                }
            }
        }

        loadPolicy()
    }

    override fun onResume() {
        super.onResume()

        if (!::appUpdateManager.isInitialized) return

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (
                    !forcedUpdateLaunching &&
                    info.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    forcedUpdateLaunching = true
                    val resumed = runCatching {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            AppUpdateType.IMMEDIATE,
                            this,
                            REQUEST_IMMEDIATE_UPDATE
                        )
                    }.getOrDefault(false)

                    if (!resumed) {
                        forcedUpdateLaunching = false
                    }
                }
            }
    }

    @Deprecated("Play Core still reports immediate update completion through activity result.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_IMMEDIATE_UPDATE) return

        forcedUpdateLaunching = false

        if (resultCode != Activity.RESULT_OK) {
            val policy = (uiState as? GateUiState.Ready)?.policy
            if (policy?.state == StudioUpdateState.FORCED) {
                uiState = GateUiState.Ready(policy)
            }
        }
    }

    private fun loadPolicy() {
        uiState = GateUiState.Loading

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetchPolicy()
                }
            }

            result.onSuccess { policy ->
                savePolicy(policy)
                uiState = GateUiState.Ready(policy)

                if (policy.state == StudioUpdateState.NORMAL) {
                    openStudio()
                } else if (policy.state == StudioUpdateState.FORCED) {
                    beginUpdate()
                }
            }.onFailure {
                val cached = loadCachedPolicy()

                if (
                    cached?.state == StudioUpdateState.FORCED ||
                    cached?.state == StudioUpdateState.MAINTENANCE
                ) {
                    uiState = GateUiState.Ready(cached)
                } else {
                    uiState = GateUiState.Error(
                        message = "Sürüm kontrolü yapılamadı. İnternet bağlantını kontrol edip tekrar deneyebilirsin.",
                        canContinueOffline = true
                    )
                }
            }
        }
    }

    private fun fetchPolicy(): StudioUpdatePolicy {
        val url = URL(
            DEFAULT_BUILD_SERVICE_URL.trimEnd('/') +
                "/api/client/android/policy?versionCode=${BuildConfig.VERSION_CODE}"
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "X-AppForge-Version-Code",
                BuildConfig.VERSION_CODE.toString()
            )
        }

        try {
            val code = connection.responseCode
            val text = (
                if (code in 200..299) connection.inputStream
                else connection.errorStream
                )
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            require(code in 200..299) {
                "Sürüm politikası alınamadı."
            }

            return parsePolicy(JSONObject(text))
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePolicy(json: JSONObject): StudioUpdatePolicy {
        val state = runCatching {
            StudioUpdateState.valueOf(
                json.optString("state", "NORMAL").uppercase()
            )
        }.getOrDefault(StudioUpdateState.NORMAL)

        return StudioUpdatePolicy(
            state = state,
            latestVersionCode = json.optInt(
                "latestVersionCode",
                BuildConfig.VERSION_CODE
            ),
            minSupportedVersionCode = json.optInt(
                "minSupportedVersionCode",
                BuildConfig.VERSION_CODE
            ),
            message = json.optString(
                "message",
                "AppForge Studio'nun yeni sürümü hazır."
            ).take(500),
            playStoreUrl = json.optString(
                "playStoreUrl",
                PLAY_STORE_FALLBACK
            ).ifBlank { PLAY_STORE_FALLBACK }
        )
    }

    private fun beginUpdate() {
        val policy = (uiState as? GateUiState.Ready)?.policy ?: return

        if (policy.state == StudioUpdateState.MAINTENANCE) return

        if (policy.state != StudioUpdateState.FORCED) {
            openPlayStore()
            return
        }

        if (forcedUpdateLaunching) return

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                val available =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        info.updateAvailability() ==
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

                if (
                    available &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    forcedUpdateLaunching = true
                    val started = runCatching {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            AppUpdateType.IMMEDIATE,
                            this,
                            REQUEST_IMMEDIATE_UPDATE
                        )
                    }.getOrDefault(false)

                    if (!started) {
                        forcedUpdateLaunching = false
                        openPlayStore()
                    }
                } else {
                    openPlayStore()
                }
            }
            .addOnFailureListener {
                openPlayStore()
            }
    }

    private fun openPlayStore() {
        val policy = (uiState as? GateUiState.Ready)?.policy
        val url = policy?.playStoreUrl ?: PLAY_STORE_FALLBACK

        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            setPackage("com.android.vending")
        }

        runCatching {
            startActivity(marketIntent)
        }.getOrElse {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        }
    }

    private fun openStudio() {
        val policy = (uiState as? GateUiState.Ready)?.policy

        if (
            policy?.state == StudioUpdateState.FORCED ||
            policy?.state == StudioUpdateState.MAINTENANCE
        ) {
            return
        }

        UpdateGateSession.approve()

        val original = intent
        val target = Intent(
            this,
            MainActivity::class.java
        ).apply {
            action = original?.action
            data = original?.data
            original?.extras?.let(::putExtras)
            original?.categories?.forEach(::addCategory)
            putExtra("appforge_gate_checked", true)
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        startActivity(target)
        finish()
    }

    private fun savePolicy(policy: StudioUpdatePolicy) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString("state", policy.state.name)
            .putInt("latest", policy.latestVersionCode)
            .putInt("minimum", policy.minSupportedVersionCode)
            .putString("message", policy.message)
            .putString("store", policy.playStoreUrl)
            .apply()
    }

    private fun loadCachedPolicy(): StudioUpdatePolicy? {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.contains("state")) return null

        return runCatching {
            StudioUpdatePolicy(
                state = StudioUpdateState.valueOf(
                    prefs.getString("state", "NORMAL") ?: "NORMAL"
                ),
                latestVersionCode = prefs.getInt("latest", BuildConfig.VERSION_CODE),
                minSupportedVersionCode = prefs.getInt("minimum", BuildConfig.VERSION_CODE),
                message = prefs.getString("message", "")
                    .orEmpty(),
                playStoreUrl = prefs.getString("store", PLAY_STORE_FALLBACK)
                    .orEmpty()
                    .ifBlank { PLAY_STORE_FALLBACK }
            )
        }.getOrNull()
    }

    companion object {
        private const val REQUEST_IMMEDIATE_UPDATE = 52201
        private const val PREFS = "appforge_update_policy_v1"
        private const val PLAY_STORE_FALLBACK =
            "https://play.google.com/store/apps/details?id=com.appforge.studio"
    }
}

@Composable
private fun UpdateGateContent(
    state: GateUiState,
    currentVersion: String,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onUpdate: () -> Unit,
    onOpenStore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AppForge Studio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sürüm $currentVersion",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))

            when (state) {
                GateUiState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Sürüm ve güvenlik politikası kontrol ediliyor…")
                }

                is GateUiState.Error -> {
                    Text(
                        text = "Sürüm kontrolü tamamlanamadı",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.message,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tekrar dene")
                    }
                    if (state.canContinueOffline) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Çevrimdışı devam et")
                        }
                    }
                }

                is GateUiState.Ready -> {
                    val policy = state.policy
                    val title = when (policy.state) {
                        StudioUpdateState.NORMAL -> "Hazır"
                        StudioUpdateState.OPTIONAL -> "Yeni sürüm hazır"
                        StudioUpdateState.FORCED -> "Güncelleme gerekli"
                        StudioUpdateState.MAINTENANCE -> "Bakım modu"
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = policy.message,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))

                    when (policy.state) {
                        StudioUpdateState.NORMAL -> {
                            CircularProgressIndicator()
                        }

                        StudioUpdateState.OPTIONAL -> {
                            Button(
                                onClick = onUpdate,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Google Play'den güncelle")
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onContinue,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Şimdi değil")
                            }
                        }

                        StudioUpdateState.FORCED -> {
                            Button(
                                onClick = onUpdate,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Şimdi güncelle")
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onOpenStore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Google Play'i aç")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Bu sürümle uygulamaya devam edilemez.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }

                        StudioUpdateState.MAINTENANCE -> {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Durumu yenile")
                            }
                        }
                    }
                }
            }
        }
    }
}
