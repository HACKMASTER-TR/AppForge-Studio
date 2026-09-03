package com.appforge.studio.terminal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.BuildConfig
import com.appforge.studio.security.ExternalServiceConnection
import com.appforge.studio.security.SecureAccountStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ConnectionsPanel(
    onOpenGit: () -> Unit,
    railwayAuthorizationUri: Uri?,
    railwayAuthorizationSequence: Int,
    onRailwayAuthorizationConsumed: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var githubConnection by
        remember {
            mutableStateOf(
                SecureAccountStore.loadExternalConnection(
                    context,
                    ExternalProvider.GITHUB.key
                )
            )
        }

    var railwayConnection by
        remember {
            mutableStateOf(
                SecureAccountStore.loadExternalConnection(
                    context,
                    ExternalProvider.RAILWAY.key
                )
            )
        }

    var busyProvider by
        remember {
            mutableStateOf<ExternalProvider?>(null)
        }

    var manualProvider by
        remember {
            mutableStateOf<ExternalProvider?>(null)
        }

    var manualToken by
        remember {
            mutableStateOf("")
        }

    var manualError by
        remember {
            mutableStateOf("")
        }

    var authorization by
        remember {
            mutableStateOf<DeviceAuthorization?>(null)
        }

    var pollInterval by
        remember {
            mutableLongStateOf(5L)
        }

    var authorizationStatus by
        remember {
            mutableStateOf("")
        }

    var message by
        remember {
            mutableStateOf("")
        }

    var disconnectProvider by
        remember {
            mutableStateOf<ExternalProvider?>(null)
        }

    fun clientId(provider: ExternalProvider): String =
        when (provider) {
            ExternalProvider.GITHUB ->
                BuildConfig.APPFORGE_GITHUB_OAUTH_CLIENT_ID

            ExternalProvider.RAILWAY ->
                BuildConfig.APPFORGE_RAILWAY_OAUTH_CLIENT_ID
        }

    fun updateConnection(
        provider: ExternalProvider,
        connection: ExternalServiceConnection?
    ) {
        when (provider) {
            ExternalProvider.GITHUB ->
                githubConnection = connection

            ExternalProvider.RAILWAY ->
                railwayConnection = connection
        }
    }

    fun saveConnection(
        provider: ExternalProvider,
        token: OAuthToken,
        identity: ExternalIdentity,
        type: String
    ) {
        val connection =
            ExternalServiceConnection(
                provider = provider.key,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                tokenType = type,
                accountLabel =
                    listOf(
                        identity.label
                            .trim()
                            .take(120),
                        identity.detail
                            .trim()
                            .take(160)
                    ).filter {
                        it.isNotBlank()
                    }.joinToString(" • "),
                scopes =
                    token.scopes
                        .trim()
                        .take(512),
                expiresAt = token.expiresAt
            )

        SecureAccountStore.saveExternalConnection(
            context,
            connection
        )
        updateConnection(provider, connection)
    }

    fun openAuthorizationPage(
        url: String,
        fallbackMessage: String
    ) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        }.onFailure {
            message =
                if (it is ActivityNotFoundException) {
                    fallbackMessage
                } else {
                    "Tarayıcı açılamadı: ${it.message.orEmpty()}"
                }
        }
    }

    fun beginGithubDeviceFlow() {
        if (busyProvider != null) return

        val provider = ExternalProvider.GITHUB
        val id = clientId(provider)

        if (id.isBlank()) {
            manualError = ""
            manualProvider = provider
            return
        }

        busyProvider = provider
        message = ""
        scope.launch {
            runCatching {
                ExternalConnectionsClient
                    .startDeviceAuthorization(
                        provider,
                        id
                    )
            }.onSuccess {
                authorization = it
                pollInterval = it.intervalSeconds
                authorizationStatus =
                    "Tarayıcı onayı bekleniyor…"
                openAuthorizationPage(
                    it.verificationUriComplete,
                    "Tarayıcı bulunamadı; adresi ve kodu elle açabilirsin."
                )
            }.onFailure {
                message =
                    it.message
                        ?: "Yetkilendirme başlatılamadı."
            }

            busyProvider = null
        }
    }

    fun beginRailwayFlow() {
        if (busyProvider != null) return

        val provider = ExternalProvider.RAILWAY
        val id = clientId(provider)

        if (id.isBlank()) {
            manualError = ""
            manualProvider = provider
            return
        }

        busyProvider = provider
        message = ""
        scope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) {
                        ExternalConnectionsClient
                            .startRailwayAuthorization(id)
                            .also {
                                SecureAccountStore
                                    .savePendingExternalAuthorization(
                                        context,
                                        it.pending
                                    )
                            }
                    }
                }

            result.onSuccess {
                message =
                    "Railway onayı tarayıcıda açıldı; işlem bitince AppForge'a dönülecek."
                openAuthorizationPage(
                    it.authorizationUri,
                    "Tarayıcı bulunamadı; Railway hesabını kişisel token ile bağlayabilirsin."
                )
            }.onFailure {
                SecureAccountStore
                    .clearPendingExternalAuthorization(
                        context,
                        provider.key
                    )
                message =
                    it.message
                        ?: "Railway yetkilendirmesi başlatılamadı."
            }

            busyProvider = null
        }
    }

    LaunchedEffect(authorization) {
        val value = authorization
            ?: return@LaunchedEffect

        while (
            isActive &&
            authorization === value &&
            System.currentTimeMillis() < value.expiresAt
        ) {
            delay(pollInterval * 1_000L)

            when (
                val result =
                    runCatching {
                        ExternalConnectionsClient
                            .pollDeviceAuthorization(
                                value,
                                pollInterval
                            )
                    }.getOrElse {
                        DevicePollResult.Pending(
                            intervalSeconds =
                                (pollInterval + 2L)
                                    .coerceAtMost(30L),
                            message =
                                "Ağ bağlantısı bekleniyor; yetkilendirme yeniden denenecek…"
                        )
                    }
            ) {
                is DevicePollResult.Pending -> {
                    pollInterval =
                        result.intervalSeconds
                    authorizationStatus =
                        result.message
                }

                is DevicePollResult.Authorized -> {
                    busyProvider = value.provider
                    val identity =
                        runCatching {
                            ExternalConnectionsClient
                                .validateIdentity(
                                    value.provider,
                                    result.token.accessToken
                                )
                        }.getOrElse {
                            authorizationStatus =
                                it.message
                                    ?: "Hesap doğrulanamadı."
                            busyProvider = null
                            return@LaunchedEffect
                        }

                    runCatching {
                        saveConnection(
                            value.provider,
                            result.token,
                            identity,
                            "oauth"
                        )
                    }.getOrElse {
                        authorizationStatus =
                            it.message
                                ?: "Bağlantı güvenli kasaya kaydedilemedi."
                        busyProvider = null
                        return@LaunchedEffect
                    }
                    message =
                        "${value.provider.title} hesabı bağlandı."
                    authorization = null
                    busyProvider = null
                    return@LaunchedEffect
                }

                is DevicePollResult.Failed -> {
                    authorizationStatus =
                        result.message
                    return@LaunchedEffect
                }
            }
        }

        if (
            authorization === value &&
            System.currentTimeMillis() >= value.expiresAt
        ) {
            authorizationStatus =
                "Yetkilendirme kodunun süresi doldu."
        }
    }

    LaunchedEffect(
        railwayAuthorizationSequence
    ) {
        val callback = railwayAuthorizationUri
            ?: return@LaunchedEffect

        busyProvider = ExternalProvider.RAILWAY
        message = "Railway yetkilendirmesi doğrulanıyor…"

        try {
            val pending =
                SecureAccountStore
                    .loadPendingExternalAuthorization(
                        context,
                        ExternalProvider.RAILWAY.key
                    )
                    ?: error(
                        "Railway yetkilendirme isteği bulunamadı veya süresi doldu."
                    )

            val token =
                ExternalConnectionsClient
                    .exchangeRailwayCallback(
                        callback.toString(),
                        pending,
                        BuildConfig
                            .APPFORGE_RAILWAY_OAUTH_CLIENT_ID
                    )

            val identity =
                ExternalConnectionsClient
                    .validateIdentity(
                        ExternalProvider.RAILWAY,
                        token.accessToken
                    )

            saveConnection(
                ExternalProvider.RAILWAY,
                token,
                identity,
                "oauth"
            )
            message = "Railway hesabı bağlandı."
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            message =
                failure.message
                    ?: "Railway yetkilendirmesi tamamlanamadı."
        }

        SecureAccountStore
            .clearPendingExternalAuthorization(
                context,
                ExternalProvider.RAILWAY.key
            )
        onRailwayAuthorizationConsumed()
        busyProvider = null
    }

    LaunchedEffect(Unit) {
        val current = railwayConnection
        val clientId =
            BuildConfig.APPFORGE_RAILWAY_OAUTH_CLIENT_ID

        if (
            current != null &&
            current.tokenType == "oauth" &&
            current.refreshToken.isNotBlank() &&
            current.expiresAt > 0L &&
            current.expiresAt <=
                System.currentTimeMillis() + 120_000L &&
            clientId.isNotBlank()
        ) {
            runCatching {
                ExternalConnectionsClient
                    .refreshRailway(
                        current,
                        clientId
                    )
                    .also {
                        SecureAccountStore
                            .saveExternalConnection(
                                context,
                                it
                            )
                    }
            }.onSuccess {
                railwayConnection = it
            }.onFailure {
                message =
                    "Railway oturumu yenilenemedi; gerekirse hesabı yeniden yetkilendir."
            }
        }
    }

    authorization?.let { value ->
        AlertDialog(
            onDismissRequest = {
                authorization = null
            },
            title = {
                Text(
                    "${value.provider.title} yetkilendirmesi"
                )
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Tarayıcıda aşağıdaki tek kullanımlık kodu onayla. Şifren AppForge'a gelmez."
                    )

                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    TerminalBackground
                            )
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(14.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                value.userCode,
                                color = TerminalPrimary,
                                fontFamily =
                                    FontFamily.Monospace,
                                fontWeight =
                                    FontWeight.Black,
                                fontSize = 24.sp
                            )
                            Text(
                                value.verificationUri,
                                color = TerminalMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        authorizationStatus,
                        color = TerminalMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        openAuthorizationPage(
                            value.verificationUriComplete,
                            "Tarayıcı bulunamadı; adresi ve kodu elle açabilirsin."
                        )
                    }
                ) {
                    Text("Tarayıcıyı Aç")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    value.userCode
                                )
                            )
                            message = "Kod kopyalandı."
                        }
                    ) {
                        Text("Kodu Kopyala")
                    }
                    TextButton(
                        onClick = {
                            authorization = null
                        }
                    ) {
                        Text("İptal")
                    }
                }
            }
        )
    }

    manualProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = {
                if (busyProvider == null) {
                    manualProvider = null
                    manualToken = ""
                }
            },
            title = {
                Text("${provider.title} tokenı bağla")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "Token önce ${provider.title} üzerinde doğrulanır, ardından Android Keystore ile şifrelenir."
                    )
                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = {
                            manualToken =
                                it.take(32 * 1_024)
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        label = {
                            Text("Kişisel erişim tokenı")
                        },
                        visualTransformation =
                            PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = busyProvider == null
                    )

                    if (manualError.isNotBlank()) {
                        Text(
                            manualError,
                            color = TerminalError,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (busyProvider != null) return@Button

                        busyProvider = provider
                        message = ""
                        manualError = ""
                        scope.launch {
                            runCatching {
                                val clean = manualToken.trim()
                                val identity =
                                    ExternalConnectionsClient
                                        .validateIdentity(
                                            provider,
                                            clean
                                        )

                                saveConnection(
                                    provider = provider,
                                    token =
                                        OAuthToken(
                                            accessToken = clean,
                                            refreshToken = "",
                                            scopes =
                                                "Kişisel token",
                                            expiresAt = 0L
                                        ),
                                    identity = identity,
                                    type = "manual"
                                )
                            }.onSuccess {
                                message =
                                    "${provider.title} hesabı bağlandı."
                                manualProvider = null
                                manualToken = ""
                            }.onFailure {
                                manualError =
                                    it.message
                                        ?: "Token doğrulanamadı."
                            }

                            busyProvider = null
                        }
                    },
                    enabled =
                        manualToken.isNotBlank() &&
                            busyProvider == null
                ) {
                    if (busyProvider == provider) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Doğrula ve Bağla")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        manualProvider = null
                        manualToken = ""
                        manualError = ""
                    },
                    enabled = busyProvider == null
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }

    disconnectProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = {
                disconnectProvider = null
            },
            title = {
                Text("${provider.title} bağlantısını kaldır")
            },
            text = {
                Text(
                    "Şifreli token bu cihazdan silinecek. Sağlayıcı tarafındaki uygulama iznini ayrıca ${provider.title} hesap ayarlarından kaldırabilirsin."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SecureAccountStore
                            .clearExternalConnection(
                                context,
                                provider.key
                            )
                        updateConnection(provider, null)
                        disconnectProvider = null
                        message =
                            "${provider.title} bağlantısı cihazdan kaldırıldı."
                    }
                ) {
                    Text("Bağlantıyı Kaldır")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        disconnectProvider = null
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        item {
            TerminalPanelTitle(
                "Hesap Bağlantıları",
                "GitHub ve Railway yetkilerini tarayıcıdan ver; parolalar uygulamaya girilmez."
            )
        }

        if (message.isNotBlank()) {
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                TerminalSurfaceRaised
                        )
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(12.dp),
                        color =
                            if (
                                message.contains(
                                    "başarısız",
                                    ignoreCase = true
                                ) ||
                                message.contains(
                                    "doğrulanamadı",
                                    ignoreCase = true
                                )
                            ) {
                                TerminalError
                            } else {
                                TerminalText
                            },
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            ProviderConnectionCard(
                provider = ExternalProvider.GITHUB,
                connection = githubConnection,
                oauthConfigured =
                    BuildConfig
                        .APPFORGE_GITHUB_OAUTH_CLIENT_ID
                        .isNotBlank(),
                busy =
                    busyProvider ==
                        ExternalProvider.GITHUB,
                onAuthorize = {
                    beginGithubDeviceFlow()
                },
                onManual = {
                    manualError = ""
                    manualProvider =
                        ExternalProvider.GITHUB
                },
                onDisconnect = {
                    disconnectProvider =
                        ExternalProvider.GITHUB
                },
                onOpenService = {
                    openExternalUrl(
                        context,
                        "https://github.com"
                    )
                },
                extraAction = {
                    if (githubConnection != null) {
                        OutlinedButton(
                            onClick = onOpenGit,
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text("Git İşlemlerini Aç")
                        }
                    }
                }
            )
        }

        item {
            ProviderConnectionCard(
                provider = ExternalProvider.RAILWAY,
                connection = railwayConnection,
                oauthConfigured =
                    BuildConfig
                        .APPFORGE_RAILWAY_OAUTH_CLIENT_ID
                        .isNotBlank(),
                busy =
                    busyProvider ==
                        ExternalProvider.RAILWAY,
                onAuthorize = {
                    beginRailwayFlow()
                },
                onManual = {
                    manualError = ""
                    manualProvider =
                        ExternalProvider.RAILWAY
                },
                onDisconnect = {
                    disconnectProvider =
                        ExternalProvider.RAILWAY
                },
                onOpenService = {
                    openExternalUrl(
                        context,
                        "https://railway.com/dashboard"
                    )
                }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalSurface
                    ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "Güvenlik",
                        color = TerminalText,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "• OAuth client secret APK içine konmaz.\n• Railway mobil bağlantısı PKCE ve tek kullanımlık state ile korunur.\n• Tokenlar Android Keystore AES-GCM ile şifrelenir.\n• Tokenlar terminal çıktısında veya Git uzak adresinde gösterilmez.\n• Bağlantıyı kaldırınca cihazdaki şifreli kayıt silinir.",
                        color = TerminalMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConnectionCard(
    provider: ExternalProvider,
    connection: ExternalServiceConnection?,
    oauthConfigured: Boolean,
    busy: Boolean,
    onAuthorize: () -> Unit,
    onManual: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenService: () -> Unit,
    extraAction: @Composable () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = TerminalSurface
            ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement =
                Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (provider == ExternalProvider.GITHUB) {
                        "⑂"
                    } else {
                        "R"
                    },
                    color = TerminalPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 25.sp
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        provider.title,
                        color = TerminalText,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Text(
                        if (connection == null) {
                            "Bağlı değil"
                        } else {
                            connection.accountLabel
                                .ifBlank { "Bağlı hesap" }
                        },
                        color =
                            if (connection == null) {
                                TerminalMuted
                            } else {
                                TerminalSuccess
                            },
                        fontSize = 11.sp
                    )
                }
                if (busy) {
                    CircularProgressIndicator(
                        color = TerminalPrimary,
                        strokeWidth = 2.dp
                    )
                }
            }

            if (connection != null) {
                HorizontalDivider(
                    color = TerminalSurfaceRaised
                )
                Text(
                    buildString {
                        append(
                            if (connection.tokenType == "oauth") {
                                "OAuth"
                            } else {
                                "Kişisel token"
                            }
                        )
                        if (connection.scopes.isNotBlank()) {
                            append(" • ")
                            append(connection.scopes)
                        }
                        if (connection.expiresAt > 0L) {
                            append("\nSüre: ")
                            append(
                                DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT
                                ).format(
                                    Date(connection.expiresAt)
                                )
                            )
                        }
                    },
                    color = TerminalMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
                extraAction()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenService,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Web'i Aç")
                    }
                    TextButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Kaldır")
                    }
                }
            } else {
                Text(
                    if (oauthConfigured) {
                        "Tarayıcıda ${provider.title} hesabını onaylayarak güvenli şekilde bağlan."
                    } else {
                        "Bu derlemede OAuth istemci kimliği yok. Doğrulanmış kişisel token ile bağlanabilirsin."
                    },
                    color = TerminalMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Button(
                    onClick = onAuthorize,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) {
                    Text(
                        if (oauthConfigured) {
                            "${provider.title} ile Yetkilendir"
                        } else {
                            "Token ile Bağlan"
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    if (oauthConfigured) {
                        TextButton(
                            onClick = onManual,
                            modifier = Modifier.weight(1f),
                            enabled = !busy
                        ) {
                            Text("Token Kullan")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenService,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Web")
                    }
                }
            }
        }
    }
}

private fun openExternalUrl(
    context: Context,
    url: String
) {
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    }
}
