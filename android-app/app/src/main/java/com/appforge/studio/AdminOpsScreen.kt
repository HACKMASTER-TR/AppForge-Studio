package com.appforge.studio

import android.content.Context
import com.appforge.studio.build.BuildApiClient
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import com.appforge.studio.security.SecureAccountStore
import com.appforge.studio.security.StudioDeviceIdentity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val FULL_ADMIN_ACCOUNT =
    "28550040284a@gmail.com"

private val ADMIN_TERMINAL_STATES =
    setOf(
        "success",
        "failed",
        "cancelled",
        "canceled"
    )

private data class AdminSystemSnapshot(
    val queued: Int = 0,
    val running: Int = 0,
    val liveReplicas: Int = 0,
    val liveSlots: Int = 0,
    val slotsPerReplica: Int = 0,
    val averageBuildSeconds: Int? = null,
    val estimatedDrainSeconds: Int? = null,
    val autoscaleEnabled: Boolean = false,
    val autoscaleThreshold: Int = 0,
    val minReplicas: Int = 0,
    val maxReplicas: Int = 0,
    val desiredReplicas: Int = 0,
    val autoscaleAction: String = "hold",
    val role: String = "",
    val fullAccess: Boolean = false
)

private data class AdminLoadItem(
    val slot: Int,
    val buildId: String? = null,
    val status: String = "Bekliyor",
    val progress: Int = 0,
    val error: String? = null
)


@Composable
fun AdminOpsScreen(
    serverUrl: String,
    apiKey: String,
    accountEmail: String,
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val authorized =
        accountEmail
            .trim()
            .equals(
                FULL_ADMIN_ACCOUNT,
                ignoreCase = true
            )

    val adminApi =
        remember(
            serverUrl,
            apiKey
        ) {
            AdminOpsApiClient(
                context = context,
                baseUrl = serverUrl,
                apiKey = apiKey
            )
        }

    val buildApi =
        remember(
            serverUrl,
            apiKey
        ) {
            BuildApiClient(
                context = context,
                baseUrl = serverUrl,
                apiKey = apiKey
            )
        }

    var snapshot by
        remember {
            mutableStateOf(
                AdminSystemSnapshot()
            )
        }

    var systemError by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    var showAccountManagement by
        remember {
            mutableStateOf(
                false
            )
        }

    if (
        showAccountManagement
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

    var loadItems by
        remember {
            mutableStateOf(
                emptyList<AdminLoadItem>()
            )
        }

    var submitting by
        remember {
            mutableStateOf(
                false
            )
        }

    var pendingLoadCount by
        remember {
            mutableStateOf<Int?>(
                null
            )
        }

    var info by
        remember {
            mutableStateOf(
                ""
            )
        }

    suspend fun refreshSystem() {
        val result =
            withContext(
                Dispatchers.IO
            ) {
                adminApi.systemStatus()
            }

        snapshot =
            result

        systemError =
            null
    }

    suspend fun refreshBuilds() {
        val ids =
            loadItems
                .mapNotNull {
                    it.buildId
                }

        if (
            ids.isEmpty()
        ) {
            return
        }

        val statuses =
            withContext(
                Dispatchers.IO
            ) {
                adminApi.buildStatuses(
                    ids
                )
            }

        loadItems =
            loadItems.map {
                item ->

                val buildId =
                    item.buildId

                val remote =
                    if (
                        buildId == null
                    ) {
                        null
                    } else {
                        statuses[
                            buildId
                        ]
                    }

                if (
                    remote == null
                ) {
                    item
                } else {
                    item.copy(
                        status =
                            remote.status,
                        progress =
                            remote.progress,
                        error =
                            remote.error
                    )
                }
            }
    }

    suspend fun createOneBuild(
        slot: Int,
        batchId: Long
    ): AdminLoadItem {
        val draft =
            ProjectDraft(
                appName =
                    "AppForge Load Test $slot",
                packageName =
                    "com.appforge.loadtest.b${batchId}.s${slot}",
                sourceMode =
                    SourceMode.URL,
                sourceLabel =
                    "Admin Autoscale Load Test",
                webUrl =
                    "https://example.com",
                buildOutput =
                    "apk",
                minSdk =
                    26,
                targetSdk =
                    37,
                splashEnabled =
                    false,
                fileUpload =
                    false,
                downloads =
                    false,
                offlineCache =
                    false,
                buildServiceUrl =
                    serverUrl,
                buildApiKey =
                    apiKey
            )

        val idempotencyKey =
            "admin-load-" +
                batchId +
                "-" +
                slot +
                "-" +
                UUID.randomUUID()
                    .toString()

        var lastError: Throwable? =
            null

        repeat(
            5
        ) {
            attempt ->

            try {
                val created =
                    buildApi.createBuild(
                        draft = draft,
                        projectZip = null,
                        idempotencyKey =
                            idempotencyKey,
                        cacheIdentityNonce =
                            "admin-load-" +
                                idempotencyKey
                    )

                return AdminLoadItem(
                    slot = slot,
                    buildId =
                        created.buildId,
                    status =
                        created.status,
                    progress =
                        0
                )

            } catch (
                error: Throwable
            ) {
                lastError =
                    error

                if (
                    attempt <
                    4
                ) {
                    delay(
                        1_500L *
                            (
                                attempt +
                                    1
                            )
                    )
                }
            }
        }

        return AdminLoadItem(
            slot = slot,
            status =
                "Hata",
            progress =
                0,
            error =
                lastError
                    ?.message
                    .orEmpty()
        )
    }

    fun startLoadTest(
        count: Int
    ) {
        if (
            !authorized ||
            submitting
        ) {
            return
        }

        submitting =
            true

        info =
            "$count gerçek build sunucuya gönderiliyor..."

        loadItems =
            (1..count)
                .map {
                    AdminLoadItem(
                        slot = it,
                        status =
                            "Hazırlanıyor"
                    )
                }

        scope.launch {
            try {
                val batchId =
                    System.currentTimeMillis()

                val gate =
                    Semaphore(
                        permits = 12
                    )

                val created =
                    coroutineScope {
                        (1..count)
                            .map {
                                slot ->

                                async(
                                    Dispatchers.IO
                                ) {
                                    gate.withPermit {
                                        createOneBuild(
                                            slot =
                                                slot,
                                            batchId =
                                                batchId
                                        )
                                    }
                                }
                            }
                            .awaitAll()
                    }
                    .sortedBy {
                        it.slot
                    }

                loadItems =
                    created

                val accepted =
                    created.count {
                        !it.buildId
                            .isNullOrBlank()
                    }

                info =
                    "$accepted/$count build kuyruğa kabul edildi."

                /*
                 * Production threshold'e güvenmenin yanında,
                 * admin yük testi workflow'u doğrudan da uyandırır.
                 * Redis cooldown gereksiz workflow spamini engeller.
                 */
                runCatching {
                    withContext(
                        Dispatchers.IO
                    ) {
                        adminApi.dispatchAutoscale(
                            reason =
                                "android_admin_load_$count"
                        )
                    }
                }

                runCatching {
                    refreshSystem()
                }

            } finally {
                submitting =
                    false
            }
        }
    }

    fun cancelOne(
        buildId: String
    ) {
        scope.launch {
            runCatching {
                withContext(
                    Dispatchers.IO
                ) {
                    buildApi.cancelBuild(
                        buildId
                    )
                }
            }
                .onSuccess {
                    info =
                        "Build iptal isteği gönderildi."
                }
                .onFailure {
                    info =
                        "İptal hatası: ${it.message.orEmpty()}"
                }

            runCatching {
                refreshBuilds()
            }

            runCatching {
                refreshSystem()
            }
        }
    }

    fun cancelAll() {
        val activeIds =
            loadItems
                .filter {
                    it.buildId != null &&
                    it.status
                        .trim()
                        .lowercase() !in
                        ADMIN_TERMINAL_STATES
                }
                .mapNotNull {
                    it.buildId
                }

        if (
            activeIds.isEmpty()
        ) {
            info =
                "Aktif test build'i yok."

            return
        }

        scope.launch {
            val gate =
                Semaphore(
                    permits = 8
                )

            coroutineScope {
                activeIds
                    .map {
                        buildId ->

                        async(
                            Dispatchers.IO
                        ) {
                            gate.withPermit {
                                runCatching {
                                    buildApi.cancelBuild(
                                        buildId
                                    )
                                }
                            }
                        }
                    }
                    .awaitAll()
            }

            info =
                "${activeIds.size} build için iptal isteği gönderildi."

            runCatching {
                refreshBuilds()
            }

            runCatching {
                refreshSystem()
            }
        }
    }

    LaunchedEffect(
        authorized
    ) {
        if (
            !authorized
        ) {
            return@LaunchedEffect
        }

        while (
            isActive
        ) {
            runCatching {
                refreshSystem()
            }
                .onFailure {
                    systemError =
                        it.message
                            .orEmpty()
                }

            if (
                loadItems.any {
                    it.buildId != null &&
                    it.status
                        .trim()
                        .lowercase() !in
                        ADMIN_TERMINAL_STATES
                }
            ) {
                runCatching {
                    refreshBuilds()
                }
            }

            delay(
                3_000L
            )
        }
    }

    if (
        pendingLoadCount !=
        null
    ) {
        val count =
            pendingLoadCount
                ?: 0

        AlertDialog(
            onDismissRequest = {
                pendingLoadCount =
                    null
            },
            title = {
                Text(
                    "Gerçek production yük testi"
                )
            },
            text = {
                Text(
                    "$count adet gerçek Android APK build'i oluşturulacak. " +
                        "Worker kapasitesi ve Railway kullanımı artabilir."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingLoadCount =
                            null

                        startLoadTest(
                            count
                        )
                    }
                ) {
                    Text(
                        "$count BUILD BAŞLAT"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingLoadCount =
                            null
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
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Yönetici Sistem Durumu",
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall
                    )

                    Text(
                        "AppForge Studio 5.0.20 • Admin Console",
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

        if (
            !authorized
        ) {
            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Bu ekran yalnız tam yetkili yönetici hesabına açıktır.",
                        modifier =
                            Modifier.padding(
                                16.dp
                            )
                    )
                }
            }

            return@LazyColumn
        }

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    Text(
                        "TAM YETKİ"
                    )

                    Text(
                        accountEmail
                    )

                    Text(
                        "Rol: ${snapshot.role.ifBlank { "kontrol ediliyor" }}"
                    )

                    Text(
                        if (
                            snapshot.fullAccess
                        ) {
                            "ADMIN + PRO • Sunucu yetkisi aktif"
                        } else {
                            "Sunucu admin yetkisi bekleniyor"
                        }
                    )
                }
            }
        }

        item {
            Button(
                modifier =
                    Modifier.fillMaxWidth(),
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
            AdminMetricCard(
                title =
                    "Build Kuyruğu",
                value =
                    "${snapshot.queued} queued • ${snapshot.running} running"
            )
        }

        item {
            AdminMetricCard(
                title =
                    "Android Worker",
                value =
                    "${snapshot.liveReplicas} replica • ${snapshot.liveSlots} slot"
            )
        }

        item {
            AdminMetricCard(
                title =
                    "Autoscale",
                value =
                    "Hedef ${snapshot.desiredReplicas} replica • " +
                        "${snapshot.autoscaleAction}"
            )
        }

        item {
            AdminMetricCard(
                title =
                    "Autoscale Aralığı",
                value =
                    "${snapshot.minReplicas} → ${snapshot.maxReplicas} replica • " +
                        "eşik ${snapshot.autoscaleThreshold}"
            )
        }

        item {
            AdminMetricCard(
                title =
                    "Ortalama Build",
                value =
                    snapshot.averageBuildSeconds
                        ?.let {
                            "$it saniye"
                        }
                        ?: "Henüz ölçüm yok"
            )
        }

        item {
            AdminMetricCard(
                title =
                    "Tahmini Kuyruk Boşalma",
                value =
                    snapshot.estimatedDrainSeconds
                        ?.let {
                            formatAdminDuration(
                                it
                            )
                        }
                        ?: "Hesaplanamıyor"
            )
        }

        if (
            !systemError.isNullOrBlank()
        ) {
            item {
                Text(
                    "Sistem durumu hatası: $systemError",
                    color =
                        MaterialTheme
                            .colorScheme
                            .error
                )
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            Text(
                "Autoscale Yük Testi",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge
            )

            Spacer(
                modifier =
                    Modifier.height(
                        4.dp
                    )
            )

            Text(
                "Cache kullanılmaz. Her seçenek gerçek APK build'leri üretir."
            )
        }

        item {
            Button(
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !submitting,
                onClick = {
                    pendingLoadCount =
                        10
                }
            ) {
                Text(
                    "10 GERÇEK BUILD TESTİ"
                )
            }
        }

        item {
            Button(
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !submitting,
                onClick = {
                    pendingLoadCount =
                        25
                }
            ) {
                Text(
                    "25 GERÇEK BUILD TESTİ"
                )
            }
        }

        item {
            Button(
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !submitting,
                onClick = {
                    pendingLoadCount =
                        50
                }
            ) {
                Text(
                    "50 GERÇEK BUILD TESTİ"
                )
            }
        }

        item {
            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        runCatching {
                            refreshSystem()
                        }
                    }
                }
            ) {
                Text(
                    "SİSTEM DURUMUNU YENİLE"
                )
            }
        }

        item {
            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    loadItems.any {
                        it.buildId != null &&
                        it.status
                            .trim()
                            .lowercase() !in
                            ADMIN_TERMINAL_STATES
                    },
                onClick = {
                    cancelAll()
                }
            ) {
                Text(
                    "TÜM AKTİF TEST BUILD'LERİNİ İPTAL ET"
                )
            }
        }

        if (
            info.isNotBlank()
        ) {
            item {
                Text(
                    info
                )
            }
        }

        if (
            loadItems.isNotEmpty()
        ) {
            item {
                HorizontalDivider()
            }

            item {
                val success =
                    loadItems.count {
                        it.status
                            .equals(
                                "success",
                                ignoreCase = true
                            )
                    }

                val finished =
                    loadItems.count {
                        it.status
                            .trim()
                            .lowercase() in
                            ADMIN_TERMINAL_STATES
                    }

                Text(
                    "Yük testi • $finished/${loadItems.size} tamamlandı • " +
                        "$success başarılı",
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )
            }
        }

        items(
            items =
                loadItems,
            key = {
                it.slot
            }
        ) {
            item ->

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            12.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            5.dp
                        )
                ) {
                    Text(
                        "#${item.slot} • ${item.status} • %${item.progress}"
                    )

                    item.buildId
                        ?.let {
                            Text(
                                it.take(
                                    13
                                ) + "…",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                    if (
                        !item.error
                            .isNullOrBlank()
                    ) {
                        Text(
                            item.error,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                    }

                    if (
                        item.buildId !=
                        null &&
                        item.status
                            .trim()
                            .lowercase() !in
                            ADMIN_TERMINAL_STATES
                    ) {
                        OutlinedButton(
                            onClick = {
                                cancelOne(
                                    item.buildId
                                )
                            }
                        ) {
                            Text(
                                "BUILD'İ İPTAL ET"
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun AdminMetricCard(
    title: String,
    value: String
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Column(
            modifier =
                Modifier.padding(
                    14.dp
                )
        ) {
            Text(
                title,
                style =
                    MaterialTheme
                        .typography
                        .labelLarge
            )

            Text(
                value,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )
        }
    }
}


private fun formatAdminDuration(
    seconds: Int
): String {
    if (
        seconds <
        60
    ) {
        return "$seconds sn"
    }

    val minutes =
        seconds /
        60

    val remainingSeconds =
        seconds %
        60

    if (
        minutes <
        60
    ) {
        return "${minutes} dk ${remainingSeconds} sn"
    }

    val hours =
        minutes /
        60

    val remainingMinutes =
        minutes %
        60

    return "${hours} sa ${remainingMinutes} dk"
}


private data class AdminRemoteBuild(
    val status: String,
    val progress: Int,
    val error: String?
)


private class AdminOpsApiClient(
    private val context: Context,
    private val baseUrl: String,
    private val apiKey: String
) {

    fun systemStatus(): AdminSystemSnapshot {
        val json =
            request(
                path =
                    "/api/admin/system-status",
                method =
                    "GET"
            )

        val account =
            json.optJSONObject(
                "account"
            )
                ?: JSONObject()

        val queue =
            json.optJSONObject(
                "queue"
            )
                ?: JSONObject()

        val capacity =
            json.optJSONObject(
                "capacity"
            )
                ?: JSONObject()

        val performance =
            json.optJSONObject(
                "performance"
            )
                ?: JSONObject()

        val autoscale =
            json.optJSONObject(
                "autoscale"
            )
                ?: JSONObject()

        return AdminSystemSnapshot(
            queued =
                queue.optInt(
                    "queued",
                    0
                ),
            running =
                queue.optInt(
                    "running",
                    0
                ),
            liveReplicas =
                capacity.optInt(
                    "liveReplicas",
                    0
                ),
            liveSlots =
                capacity.optInt(
                    "liveSlots",
                    0
                ),
            slotsPerReplica =
                capacity.optInt(
                    "slotsPerReplica",
                    0
                ),
            averageBuildSeconds =
                if (
                    performance.isNull(
                        "averageBuildSeconds"
                    )
                ) {
                    null
                } else {
                    performance.optInt(
                        "averageBuildSeconds"
                    )
                },
            estimatedDrainSeconds =
                if (
                    performance.isNull(
                        "estimatedDrainSeconds"
                    )
                ) {
                    null
                } else {
                    performance.optInt(
                        "estimatedDrainSeconds"
                    )
                },
            autoscaleEnabled =
                autoscale.optBoolean(
                    "enabled",
                    false
                ),
            autoscaleThreshold =
                autoscale.optInt(
                    "queueThreshold",
                    0
                ),
            minReplicas =
                autoscale.optInt(
                    "minReplicas",
                    0
                ),
            maxReplicas =
                autoscale.optInt(
                    "maxReplicas",
                    0
                ),
            desiredReplicas =
                autoscale.optInt(
                    "desiredReplicas",
                    0
                ),
            autoscaleAction =
                autoscale.optString(
                    "action",
                    "hold"
                ),
            role =
                account.optString(
                    "role",
                    ""
                ),
            fullAccess =
                account.optBoolean(
                    "fullAccess",
                    false
                )
        )
    }


    fun dispatchAutoscale(
        reason: String
    ) {
        request(
            path =
                "/api/admin/autoscale/dispatch",
            method =
                "POST",
            body =
                JSONObject()
                    .put(
                        "reason",
                        reason
                    )
        )
    }


    fun buildStatuses(
        ids: List<String>
    ): Map<String, AdminRemoteBuild> {
        if (
            ids.isEmpty()
        ) {
            return emptyMap()
        }

        val body =
            JSONObject()
                .put(
                    "ids",
                    JSONArray(
                        ids
                    )
                )

        val json =
            request(
                path =
                    "/api/admin/build-statuses",
                method =
                    "POST",
                body =
                    body
            )

        val array =
            json.optJSONArray(
                "builds"
            )
                ?: JSONArray()

        val result =
            mutableMapOf<
                String,
                AdminRemoteBuild
            >()

        for (
            index in
            0 until
                array.length()
        ) {
            val item =
                array.optJSONObject(
                    index
                )
                    ?: continue

            val id =
                item.optString(
                    "id",
                    ""
                )
                    .trim()

            if (
                id.isBlank()
            ) {
                continue
            }

            result[
                id
            ] =
                AdminRemoteBuild(
                    status =
                        item.optString(
                            "status",
                            ""
                        ),
                    progress =
                        item.optInt(
                            "progress",
                            0
                        ),
                    error =
                        if (
                            item.isNull(
                                "error"
                            )
                        ) {
                            null
                        } else {
                            item.optString(
                                "error",
                                ""
                            )
                        }
                )
        }

        return result
    }


    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null
    ): JSONObject {
        val connection =
            (
                URL(
                    "${baseUrl.trimEnd('/')}$path"
                ).openConnection()
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
                        doOutput =
                            true

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
                    writer ->
                    writer.write(
                        body.toString()
                    )
                }
        }

        val code =
            connection.responseCode

        val stream =
            if (
                code in
                200..299
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
            code !in
            200..299
        ) {
            val message =
                runCatching {
                    JSONObject(
                        text
                    ).optString(
                        "error",
                        text
                    )
                }
                    .getOrDefault(
                        text
                    )

            error(
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
    }
}
