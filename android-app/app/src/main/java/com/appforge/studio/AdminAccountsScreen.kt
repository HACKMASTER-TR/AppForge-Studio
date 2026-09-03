package com.appforge.studio

import android.content.Context
import com.appforge.studio.security.SecureAccountStore
import com.appforge.studio.security.StudioDeviceIdentity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder


private data class ManagedAppForgeAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val proActive: Boolean,
    val proSource: String?,
    val freeProjectUsed: Int,
    val freeProjectLimit: Int,
    val customFreeProjectLimit: Int?
)


@Composable
fun AdminAccountsScreen(
    serverUrl: String,
    apiKey: String,
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val api =
        remember(
            serverUrl,
            apiKey
        ) {
            AdminAccountsApi(
                context = context,
                serverUrl = serverUrl,
                apiKey = apiKey
            )
        }

    var search by
        remember {
            mutableStateOf("")
        }

    var accounts by
        remember {
            mutableStateOf(
                emptyList<ManagedAppForgeAccount>()
            )
        }

    var displayName by
        remember {
            mutableStateOf("")
        }

    var email by
        remember {
            mutableStateOf("")
        }

    var password by
        remember {
            mutableStateOf("")
        }

    var busy by
        remember {
            mutableStateOf(false)
        }

    var message by
        remember {
            mutableStateOf("")
        }

    var projectLimitInputs by
        remember {
            mutableStateOf(
                emptyMap<
                    String,
                    String
                >()
            )
        }

    suspend fun refresh() {
        val loaded =
            withContext(
                Dispatchers.IO
            ) {
                api.listAccounts(
                    search
                )
            }

        accounts =
            loaded

        projectLimitInputs =
            loaded.associate {
                account ->
                account.id to
                    account.freeProjectLimit
                        .toString()
            }
    }

    fun create(
        givePro: Boolean
    ) {
        if (busy) return

        if (
            !email.contains("@")
        ) {
            message =
                "Geçerli e-posta gir."
            return
        }

        if (
            password.length < 8
        ) {
            message =
                "Parola en az 8 karakter olmalı."
            return
        }

        busy = true

        scope.launch {
            try {
                withContext(
                    Dispatchers.IO
                ) {
                    api.createAccount(
                        displayName =
                            displayName,
                        email =
                            email,
                        password =
                            password,
                        pro =
                            givePro
                    )
                }

                message =
                    if (givePro) {
                        "Hesap oluşturuldu ve PRO verildi."
                    } else {
                        "FREE hesap oluşturuldu."
                    }

                displayName = ""
                email = ""
                password = ""

                refresh()

            } catch (
                error: Throwable
            ) {
                message =
                    error.message
                        .orEmpty()

            } finally {
                busy = false
            }
        }
    }

    fun togglePro(
        account: ManagedAppForgeAccount
    ) {
        if (busy) return

        busy = true

        scope.launch {
            try {
                withContext(
                    Dispatchers.IO
                ) {
                    api.setPro(
                        account.id,
                        !account.proActive
                    )
                }

                message =
                    if (
                        account.proActive
                    ) {
                        "${account.email} → FREE"
                    } else {
                        "${account.email} → PRO"
                    }

                refresh()

            } catch (
                error: Throwable
            ) {
                message =
                    error.message
                        .orEmpty()

            } finally {
                busy = false
            }
        }
    }

    fun updateProjectLimit(
        account: ManagedAppForgeAccount,
        resetToDefault: Boolean
    ) {
        if (busy) return

        val requestedLimit =
            if (
                resetToDefault
            ) {
                null
            } else {
                projectLimitInputs[
                    account.id
                ]
                    ?.trim()
                    ?.toIntOrNull()
            }

        if (
            !resetToDefault &&
            (
                requestedLimit ==
                    null ||
                requestedLimit <
                    1 ||
                requestedLimit >
                    100000
            )
        ) {
            message =
                "FREE proje hakkı 1 ile 100000 arasında olmalı."

            return
        }

        busy =
            true

        scope.launch {
            try {
                withContext(
                    Dispatchers.IO
                ) {
                    api.setProjectLimit(
                        account.id,
                        requestedLimit
                    )
                }

                message =
                    if (
                        resetToDefault
                    ) {
                        "${account.email} → varsayılan FREE proje limitine döndürüldü."
                    } else {
                        "${account.email} → FREE proje hakkı $requestedLimit olarak ayarlandı."
                    }

                refresh()

            } catch (
                error: Throwable
            ) {
                message =
                    error.message
                        .orEmpty()

            } finally {
                busy =
                    false
            }
        }
    }


    LaunchedEffect(Unit) {
        runCatching {
            refresh()
        }
            .onFailure {
                message =
                    it.message
                        .orEmpty()
            }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Hesap Yönetimi",
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall
                    )

                    Text(
                        "Yalnız ADMIN"
                    )
                }

                OutlinedButton(
                    onClick =
                        onBack
                ) {
                    Text("GERİ")
                }
            }
        }

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(14.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Yeni AppForge Hesabı",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    OutlinedTextField(
                        modifier =
                            Modifier.fillMaxWidth(),
                        value =
                            displayName,
                        onValueChange = {
                            displayName = it
                        },
                        label = {
                            Text("Ad")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        modifier =
                            Modifier.fillMaxWidth(),
                        value =
                            email,
                        onValueChange = {
                            email = it
                        },
                        label = {
                            Text("E-posta")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        modifier =
                            Modifier.fillMaxWidth(),
                        value =
                            password,
                        onValueChange = {
                            password = it
                        },
                        label = {
                            Text(
                                "İlk parola • min. 8 karakter"
                            )
                        },
                        visualTransformation =
                            PasswordVisualTransformation(),
                        singleLine = true
                    )

                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),
                        enabled =
                            !busy,
                        onClick = {
                            create(false)
                        }
                    ) {
                        Text(
                            "FREE HESAP OLUŞTUR"
                        )
                    }

                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),
                        enabled =
                            !busy,
                        onClick = {
                            create(true)
                        }
                    ) {
                        Text(
                            "HESAP OLUŞTUR + PRO VER"
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                modifier =
                    Modifier.fillMaxWidth(),
                value =
                    search,
                onValueChange = {
                    search = it
                },
                label = {
                    Text(
                        "E-posta veya isim ara"
                    )
                },
                singleLine = true
            )
        }

        item {
            Button(
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !busy,
                onClick = {
                    scope.launch {
                        try {
                            refresh()
                        } catch (
                            error: Throwable
                        ) {
                            message =
                                error.message
                                    .orEmpty()
                        }
                    }
                }
            ) {
                Text(
                    "HESAPLARI GETİR"
                )
            }
        }

        if (
            message.isNotBlank()
        ) {
            item {
                Text(message)
            }
        }

        items(
            items =
                accounts,
            key = {
                it.id
            }
        ) {
            account ->

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(14.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        account.displayName
                            .ifBlank {
                                account.email
                            },
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        account.email
                    )

                    val googlePlayManaged =
                        account.proSource
                            ?.trim()
                            ?.lowercase()
                            ?.startsWith(
                                "google_play"
                            ) == true

                    Text(
                        when {
                            account.role ==
                                "admin" ->
                                "ADMIN + PRO"

                            googlePlayManaged &&
                            account.proActive ->
                                "GOOGLE PLAY PRO"

                            googlePlayManaged ->
                                "GOOGLE PLAY • SATIN ALMA İLE YÖNETİLİR"

                            account.proActive ->
                                "PRO • YÖNETİCİ TARAFINDAN VERİLDİ"

                            else ->
                                "FREE"
                        }
                    )

                    if (
                        account.role !=
                            "admin"
                    ) {
                        Text(
                            "FREE proje hakkı: ${account.freeProjectUsed} / ${account.freeProjectLimit}",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )

                        Text(
                            if (
                                account.customFreeProjectLimit !=
                                    null
                            ) {
                                "Özel kullanıcı limiti aktif."
                            } else {
                                "Varsayılan FREE limiti kullanılıyor."
                            },
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )

                        if (
                            account.proActive
                        ) {
                            Text(
                                "PRO aktifken proje sayısı sınırsızdır. Bu değer hesap tekrar FREE olursa uygulanır.",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                        OutlinedTextField(
                            modifier =
                                Modifier.fillMaxWidth(),
                            value =
                                projectLimitInputs[
                                    account.id
                                ]
                                    ?: account
                                        .freeProjectLimit
                                        .toString(),
                            onValueChange = {
                                value ->

                                val filtered =
                                    value
                                        .filter {
                                            it.isDigit()
                                        }
                                        .take(
                                            6
                                        )

                                projectLimitInputs =
                                    projectLimitInputs +
                                        (
                                            account.id to
                                                filtered
                                        )
                            },
                            label = {
                                Text(
                                    "FREE proje hakkı"
                                )
                            },
                            singleLine =
                                true
                        )

                        Button(
                            modifier =
                                Modifier.fillMaxWidth(),
                            enabled =
                                !busy,
                            onClick = {
                                updateProjectLimit(
                                    account,
                                    false
                                )
                            }
                        ) {
                            Text(
                                "PROJE HAKKINI UYGULA"
                            )
                        }

                        OutlinedButton(
                            modifier =
                                Modifier.fillMaxWidth(),
                            enabled =
                                !busy,
                            onClick = {
                                updateProjectLimit(
                                    account,
                                    true
                                )
                            }
                        ) {
                            Text(
                                "VARSAYILAN LİMİTE DÖN"
                            )
                        }
                    }

                    if (
                        account.role !=
                            "admin" &&
                        !googlePlayManaged
                    ) {
                        OutlinedButton(
                            modifier =
                                Modifier.fillMaxWidth(),
                            enabled =
                                !busy,
                            onClick = {
                                togglePro(
                                    account
                                )
                            }
                        ) {
                            Text(
                                if (
                                    account.proActive
                                ) {
                                    "PRO KALDIR"
                                } else {
                                    "PRO VER"
                                }
                            )
                        }

                    } else if (
                        googlePlayManaged
                    ) {
                        Text(
                            "Satın alma Google Play tarafından yönetilir. " +
                                "Admin paneli bu PRO yetkisini değiştiremez.",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }
        }
    }
}


private class AdminAccountsApi(
    private val context: Context,
    private val serverUrl: String,
    private val apiKey: String
) {

    fun listAccounts(
        search: String
    ): List<ManagedAppForgeAccount> {
        val encoded =
            URLEncoder.encode(
                search,
                "UTF-8"
            )

        val json =
            request(
                "/api/admin/users?search=$encoded",
                "GET"
            )

        val array =
            json.optJSONArray("users")
                ?: JSONArray()

        return buildList {
            for (
                i in
                0 until array.length()
            ) {
                val item =
                    array.optJSONObject(i)
                        ?: continue

                add(
                    ManagedAppForgeAccount(
                        id =
                            item.optString("id"),
                        email =
                            item.optString("email"),
                        displayName =
                            item.optString(
                                "displayName"
                            ),
                        role =
                            item.optString(
                                "role",
                                "user"
                            ),
                        proActive =
                            item.optBoolean(
                                "proActive",
                                false
                            ),
                        proSource =
                            item.optString(
                                "proSource",
                                ""
                            )
                                .trim()
                                .takeIf {
                                    it.isNotBlank()
                                },

                        freeProjectUsed =
                            item.optInt(
                                "freeProjectUsed",
                                0
                            ),

                        freeProjectLimit =
                            item.optInt(
                                "freeProjectLimit",
                                5
                            )
                                .coerceAtLeast(
                                    1
                                ),

                        customFreeProjectLimit =
                            if (
                                item.isNull(
                                    "customFreeProjectLimit"
                                )
                            ) {
                                null
                            } else {
                                item.optInt(
                                    "customFreeProjectLimit",
                                    0
                                )
                                    .takeIf {
                                        it > 0
                                    }
                            }
                    )
                )
            }
        }
    }


    fun createAccount(
        displayName: String,
        email: String,
        password: String,
        pro: Boolean
    ) {
        request(
            "/api/admin/users",
            "POST",
            JSONObject()
                .put(
                    "displayName",
                    displayName.trim()
                )
                .put(
                    "email",
                    email.trim()
                )
                .put(
                    "password",
                    password
                )
                .put(
                    "pro",
                    pro
                )
        )
    }


    fun setPro(
        userId: String,
        active: Boolean
    ) {
        request(
            "/api/admin/users/$userId/pro",
            "POST",
            JSONObject()
                .put(
                    "active",
                    active
                )
        )
    }


    fun setProjectLimit(
        userId: String,
        limit: Int?
    ) {
        request(
            "/api/admin/users/$userId/project-limit",
            "POST",
            JSONObject()
                .put(
                    "limit",
                    limit
                        ?: JSONObject.NULL
                )
        )
    }


    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null
    ): JSONObject {
        val connection =
            (
                URL(
                    "${serverUrl.trimEnd('/')}$path"
                )
                    .openConnection()
                as HttpURLConnection
            )
                .apply {
                    requestMethod =
                        method

                    connectTimeout =
                        15_000

                    readTimeout =
                        30_000

                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    setRequestProperty(
                        "X-AppForge-Device-ID",
                        StudioDeviceIdentity.value(
                            context
                        )
                    )

                    SecureAccountStore
                        .loadSession(
                            context
                        )
                        ?.token
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            setRequestProperty(
                                "Authorization",
                                "Bearer $it"
                            )
                        }

                    if (
                        apiKey.isNotBlank()
                    ) {
                        setRequestProperty(
                            "X-AppForge-Key",
                            apiKey
                        )
                    }

                    if (
                        body != null
                    ) {
                        doOutput = true

                        setRequestProperty(
                            "Content-Type",
                            "application/json"
                        )
                    }
                }

        if (
            body != null
        ) {
            connection.outputStream
                .bufferedWriter(
                    Charsets.UTF_8
                )
                .use {
                    it.write(
                        body.toString()
                    )
                }
        }

        val code =
            connection.responseCode

        val stream =
            if (
                code in 200..299
            ) {
                connection.inputStream
            } else {
                connection.errorStream
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

        connection.disconnect()

        if (
            code !in 200..299
        ) {
            val error =
                runCatching {
                    JSONObject(text)
                        .optString(
                            "error",
                            text
                        )
                }
                    .getOrDefault(text)

            throw IllegalStateException(
                "HTTP $code • $error"
            )
        }

        return if (
            text.isBlank()
        ) {
            JSONObject()
        } else {
            JSONObject(text)
        }
    }
}
