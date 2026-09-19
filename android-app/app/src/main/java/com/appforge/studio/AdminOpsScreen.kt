package com.appforge.studio

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.security.SecureAccountStore
import com.appforge.studio.security.StudioDeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class AdminSystemSnapshot(
    val email: String = "",
    val role: String = "",
    val fullAccess: Boolean = false
)

@Composable
fun AdminOpsScreen(
    serverUrl: String,
    apiKey: String,
    accountEmail: String,
    onOpenSecondBrain: () -> Unit,
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val adminApi =
        remember(
            serverUrl
        ) {
            AdminOpsApiClient(
                context = context,
                baseUrl = serverUrl
            )
        }

    var snapshot by
        remember(
            serverUrl,
            accountEmail
        ) {
            mutableStateOf<AdminSystemSnapshot?>(
                null
            )
        }

    var systemError by
        remember(
            serverUrl,
            accountEmail
        ) {
            mutableStateOf<String?>(
                null
            )
        }

    var refreshing by
        remember(
            serverUrl,
            accountEmail
        ) {
            mutableStateOf(
                true
            )
        }

    var showAccountManagement by
        remember {
            mutableStateOf(
                false
            )
        }

    suspend fun refreshAuthorization() {
        refreshing =
            true

        try {
            snapshot =
                withContext(
                    Dispatchers.IO
                ) {
                    adminApi
                        .systemStatus()
                }

            systemError =
                null
        } finally {
            refreshing =
                false
        }
    }

    LaunchedEffect(
        serverUrl,
        accountEmail
    ) {
        runCatching {
            refreshAuthorization()
        }
            .onFailure {
                snapshot =
                    null

                systemError =
                    it.message
                        .orEmpty()
                        .ifBlank {
                            "Sunucu admin yetkisi doğrulanamadı."
                        }
            }
    }

    val authorized =
        snapshot
            ?.fullAccess == true &&
            snapshot
                ?.role
                ?.equals(
                    "admin",
                    ignoreCase = true
                ) == true

    if (
        showAccountManagement &&
        authorized
    ) {
        AdminAccountsScreen(
            serverUrl =
                serverUrl,
            apiKey =
                apiKey,
            onBack = {
                showAccountManagement =
                    false
            }
        )

        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    16.dp
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            3.dp
                        )
                ) {
                    Text(
                        "Yönetici",
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall
                    )

                    Text(
                        "HTTPS Control Plane",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }

                OutlinedButton(
                    onClick =
                        onBack
                ) {
                    Text(
                        "GERİ"
                    )
                }
            }
        }

        item {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(
                                16.dp
                            ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            7.dp
                        )
                ) {
                    Text(
                        "Sunucu Yetkisi",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    if (
                        refreshing &&
                        snapshot ==
                            null
                    ) {
                        CircularProgressIndicator()
                    }

                    Text(
                        when {
                            authorized ->
                                "ADMIN + PRO • Sunucu yetkisi aktif"

                            !systemError
                                .isNullOrBlank() ->
                                "Sunucu admin yetkisi doğrulanamadı"

                            snapshot != null ->
                                "Bu hesap sunucuda yönetici olarak yetkili değil"

                            else ->
                                "HTTPS üzerinden doğrulanıyor..."
                        }
                    )

                    val verifiedEmail =
                        snapshot
                            ?.email
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: accountEmail
                                .takeIf {
                                    it.isNotBlank()
                                }

                    verifiedEmail
                        ?.let {
                            Text(
                                it,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                    snapshot
                        ?.role
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            Text(
                                "Rol: $it",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                    if (
                        !systemError
                            .isNullOrBlank()
                    ) {
                        Text(
                            systemError
                                .orEmpty(),
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }
        }

        if (
            authorized
        ) {
            item {
                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    onClick = {
                        showAccountManagement =
                            true
                    }
                ) {
                    Text(
                        "HESAP YÖNETİMİ"
                    )
                }
            }

            item {
                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    onClick =
                        onOpenSecondBrain
                ) {
                    Text(
                        "2. BEYİN"
                    )
                }
            }
        }

        item {
            OutlinedButton(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                enabled =
                    !refreshing,
                onClick = {
                    scope.launch {
                        runCatching {
                            refreshAuthorization()
                        }
                            .onFailure {
                                snapshot =
                                    null

                                systemError =
                                    it.message
                                        .orEmpty()
                                        .ifBlank {
                                            "Sunucu admin yetkisi doğrulanamadı."
                                        }
                            }
                    }
                }
            ) {
                Text(
                    if (
                        refreshing
                    ) {
                        "DOĞRULANIYOR..."
                    } else {
                        "YETKİYİ YENİLE"
                    }
                )
            }
        }
    }
}

private class AdminOpsApiClient(
    private val context: Context,
    private val baseUrl: String
) {
    fun systemStatus(): AdminSystemSnapshot {
        val json =
            request(
                path =
                    "/api/admin/system-status"
            )

        val account =
            json.optJSONObject(
                "account"
            )
                ?: JSONObject()

        return AdminSystemSnapshot(
            email =
                account
                    .optString(
                        "email",
                        ""
                    )
                    .trim(),
            role =
                account
                    .optString(
                        "role",
                        ""
                    )
                    .trim(),
            fullAccess =
                account
                    .optBoolean(
                        "fullAccess",
                        false
                    )
        )
    }

    private fun controlPlaneBaseUrl(): String {
        val value =
            baseUrl
                .trim()
                .trimEnd('/')

        require(
            value.startsWith(
                "https://",
                ignoreCase = true
            ) ||
                value.startsWith(
                    "http://10.0.2.2",
                    ignoreCase = true
                )
        ) {
            "Yönetici API'si üretimde HTTPS control plane gerektirir."
        }

        return value
    }

    private fun request(
        path: String
    ): JSONObject {
        val session =
            SecureAccountStore
                .loadSession(
                    context
                )
                ?: error(
                    "Yönetici doğrulaması için aktif AppForge oturumu gerekli."
                )

        val token =
            session
                .token
                .trim()

        require(
            token.isNotBlank()
        ) {
            "Yönetici doğrulaması için oturum anahtarı eksik."
        }

        val connection =
            (
                URL(
                    controlPlaneBaseUrl() +
                        path
                )
                    .openConnection()
                as HttpURLConnection
            )
                .apply {
                    requestMethod =
                        "GET"

                    connectTimeout =
                        15_000

                    readTimeout =
                        30_000

                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    setRequestProperty(
                        "Authorization",
                        "Bearer $token"
                    )

                    setRequestProperty(
                        "X-AppForge-Device-ID",
                        StudioDeviceIdentity
                            .value(
                                context
                            )
                    )
                }

        try {
            val code =
                connection
                    .responseCode

            val stream =
                if (
                    code in
                    200..299
                ) {
                    connection
                        .inputStream
                } else {
                    connection
                        .errorStream
                }

            val text =
                stream
                    ?.bufferedReader(
                        Charsets.UTF_8
                    )
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()

            if (
                code !in
                200..299
            ) {
                val message =
                    runCatching {
                        JSONObject(
                            text
                        )
                            .optString(
                                "error",
                                text
                            )
                    }
                        .getOrDefault(
                            text
                        )
                        .ifBlank {
                            "Yönetici yetkisi doğrulanamadı."
                        }

                throw IllegalStateException(
                    "HTTP $code • $message"
                )
            }

            return if (
                text.isBlank()
            ) {
                JSONObject()
            } else {
                JSONObject(
                    text
                )
            }
        } finally {
            connection
                .disconnect()
        }
    }
}
