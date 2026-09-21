package com.appforge.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.security.OwnerAccessPolicy
import com.appforge.studio.security.ProCodeClient
import com.appforge.studio.security.ProStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AdminProCodesPanel(
    serverUrl: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(serverUrl) {
        ProCodeClient(context, serverUrl)
    }

    var busy by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf("")
    }
    var lastCode by remember {
        mutableStateOf("")
    }
    var codes by remember {
        mutableStateOf(
            emptyList<ProCodeClient.CodeRow>()
        )
    }


    var grants by remember {
        mutableStateOf(
            emptyList<ProCodeClient.GrantRow>()
        )
    }

    var pendingRevoke by remember {
        mutableStateOf<ProCodeClient.GrantRow?>(null)
    }

    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Pro Yönetimi",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                "Tek kullanımlık aktivasyon kodu oluştur. " +
                    "Kod yalnızca oluşturulduğunda gösterilir."
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = ""
                        lastCode = ""

                        try {
                            val token =
                                OwnerAccessPolicy
                                    .currentGoogleIdToken()
                                    ?: error(
                                        "Google yönetici girişi gerekli."
                                    )

                            val result =
                                withContext(Dispatchers.IO) {
                                    client.issueAdminCode(
                                        token
                                    )
                                }

                            lastCode = result.code
                            message =
                                "Kod oluşturuldu. " +
                                    "Yedi gün içinde kullanılmalı."
                        } catch (error: Exception) {
                            message =
                                error.message
                                    ?.take(160)
                                    ?: "Kod oluşturulamadı."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text("YENİ PRO KODU OLUŞTUR")
            }

            if (lastCode.isNotBlank()) {
                OutlinedTextField(
                    value = lastCode,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = {
                        Text("Bir kez gösterilen Pro kodu")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager

                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "AppForge Pro",
                                lastCode
                            )
                        )

                        message =
                            "Kod panoya kopyalandı."
                    }
                ) {
                    Text("KODU KOPYALA")
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true

                        try {
                            val token =
                                OwnerAccessPolicy
                                    .currentGoogleIdToken()
                                    ?: error(
                                        "Google yönetici girişi gerekli."
                                    )

                            codes =
                                withContext(Dispatchers.IO) {
                                    client.listAdminCodes(
                                        token
                                    )
                                }

                            message =
                                "${codes.size} kod kaydı gösteriliyor."
                        } catch (error: Exception) {
                            message =
                                error.message
                                    ?.take(160)
                                    ?: "Kod listesi alınamadı."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text("KODLARI YENİLE")
            }

            codes.forEach { entry ->
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "${entry.state.uppercase()} • " +
                            entry.id.take(8)
                    )

                    if (entry.state == "issued") {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true

                                    try {
                                        val token =
                                            OwnerAccessPolicy
                                                .currentGoogleIdToken()
                                                ?: error(
                                                    "Google yönetici girişi gerekli."
                                                )

                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            client.revokeUnusedCode(
                                                entry.id,
                                                token
                                            )
                                        }

                                        codes =
                                            codes.map {
                                                if (
                                                    it.id ==
                                                    entry.id
                                                ) {
                                                    it.copy(
                                                        state = "revoked"
                                                    )
                                                } else {
                                                    it
                                                }
                                            }

                                        message =
                                            "Kullanılmamış kod iptal edildi."
                                    } catch (
                                        error: Exception
                                    ) {
                                        message =
                                            error.message
                                                ?.take(160)
                                                ?: "Kod iptal edilemedi."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        ) {
                            Text("KULLANILMAMIŞ KODU İPTAL ET")
                        }
                    }
                }
            }


            Text(
                "Verilmiş Pro yetkileri",
                style =
                    MaterialTheme.typography.titleMedium
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = ""

                        try {
                            val token =
                                OwnerAccessPolicy
                                    .currentGoogleIdToken()
                                    ?: error(
                                        "Google yönetici girişi gerekli."
                                    )

                            grants =
                                withContext(Dispatchers.IO) {
                                    client.listAdminGrants(
                                        token
                                    )
                                }

                            message =
                                "Son ${grants.size} Pro yetkisi gösteriliyor."
                        } catch (error: Exception) {
                            message =
                                error.message
                                    ?.take(160)
                                    ?: "Yetkiler alınamadı."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text("PRO YETKİLERİNİ YENİLE")
            }

            grants.forEach { grant ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Kurulum: " +
                                grant.installationId.take(8)
                        )

                        Text(
                            "Kod: " +
                                grant.activationCodeId.take(8)
                        )

                        Text(
                            if (grant.state == "active") {
                                "PRO AKTİF"
                            } else {
                                "PRO İPTAL EDİLMİŞ"
                            }
                        )

                        if (grant.state == "active") {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    pendingRevoke = grant
                                }
                            ) {
                                Text(
                                    "PRO YETKİSİNİ GERİ AL"
                                )
                            }
                        }
                    }
                }
            }

            if (message.isNotBlank()) {
                Text(message)
            }

            Text(
                "Kullanılmamış kodları veya verilmiş aktif Pro " +
                    "yetkilerini ayrı ayrı iptal edebilirsin.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    val selectedGrant = pendingRevoke

    if (selectedGrant != null) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    pendingRevoke = null
                }
            },
            title = {
                Text("Pro yetkisini geri al")
            },
            text = {
                Text(
                    "Bu kurulumun aktif Pro yetkisi " +
                        "iptal edilecek. Kullanılmış " +
                        "aktivasyon kodu yeniden " +
                        "kullanılamaz. Devam edilsin mi?"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = ""

                            try {
                                val token =
                                    OwnerAccessPolicy
                                        .currentGoogleIdToken()
                                        ?: error(
                                            "Google yönetici girişi gerekli."
                                        )

                                withContext(
                                    Dispatchers.IO
                                ) {
                                    client.revokeAdminGrant(
                                        selectedGrant.installationId,
                                        token
                                    )
                                }

                                grants =
                                    grants.map { grant ->
                                        if (
                                            grant.installationId ==
                                            selectedGrant.installationId
                                        ) {
                                            grant.copy(
                                                state = "revoked"
                                            )
                                        } else {
                                            grant
                                        }
                                    }

                                pendingRevoke = null

                                message =
                                    "Pro yetkisi geri alındı."
                            } catch (
                                error: Exception
                            ) {
                                message =
                                    error.message
                                        ?.take(160)
                                        ?: "Pro yetkisi iptal edilemedi."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("YETKİYİ İPTAL ET")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingRevoke = null
                    }
                ) {
                    Text("VAZGEÇ")
                }
            }
        )
    }

}

@Composable
fun ProCodeActivationPanel(
    serverUrl: String,
    onVerified: (ProStatus) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(serverUrl) {
        ProCodeClient(context, serverUrl)
    }

    var code by remember {
        mutableStateOf("")
    }

    var busy by remember {
        mutableStateOf(false)
    }

    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Pro aktivasyon kodu",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                "Yönetici tarafından verilen tek kullanımlık " +
                    "kodu burada etkinleştir."
            )

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.trim().take(60)
                },
                singleLine = true,
                label = {
                    Text("AFPRO-...")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled =
                    !busy &&
                        code.startsWith("AFPRO-") &&
                        code.length == 49,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true

                        try {
                            val verified =
                                withContext(Dispatchers.IO) {
                                    client.redeemAndVerify(
                                        code
                                    )
                                }

                            onVerified(verified)
                            code = ""
                            onMessage(
                                "Yönetici Pro yetkisi " +
                                    "sunucuda doğrulandı."
                            )
                        } catch (error: Exception) {
                            onMessage(
                                error.message
                                    ?.take(160)
                                    ?: "Pro kodu etkinleştirilemedi."
                            )
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(
                    if (busy) {
                        "DOĞRULANIYOR..."
                    } else {
                        "PRO KODUNU ETKİNLEŞTİR"
                    }
                )
            }

            OutlinedButton(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val verified = withContext(Dispatchers.IO) {
                                client.recoverInstallation()
                            }
                            onVerified(verified)
                            onMessage("Pro kurulumu doğrulanarak kurtarıldı.")
                        } catch (error: Exception) {
                            onVerified(ProStatus(
                                active = false,
                                source = null,
                                productId = null,
                                expiresAt = null,
                                integrityRequired = true
                            ))
                            onMessage(error.message?.take(160)
                                ?: "Pro kurulumu kurtarılamadı.")
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("KURULUMU ANAHTARLA KURTAR") }

            OutlinedButton(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true

                        try {
                            val verified =
                                withContext(Dispatchers.IO) {
                                    client.verifyStatus()
                                }

                            onVerified(verified)
                            onMessage(
                                "Pro yetkisi sunucuda yenilendi."
                            )
                        } catch (error: Exception) {
                            onVerified(
                                ProStatus(
                                    active = false,
                                    source = null,
                                    productId = null,
                                    expiresAt = null,
                                    integrityRequired = true
                                )
                            )

                            onMessage(
                                error.message
                                    ?.take(160)
                                    ?: "Pro durumu doğrulanamadı."
                            )
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text("MEVCUT PRO YETKİSİNİ DOĞRULA")
            }

            if (BuildConfig.DEBUG && BuildConfig.PRO_RECOVERY_TEST) {
                OutlinedButton(
                    enabled = !busy && client.hasInstallation(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val verified =
                                    withContext(Dispatchers.IO) {
                                        client.testLiveStatusReplay()
                                    }
                                onVerified(verified)
                                onMessage(
                                    "LIVE_REPLAY=BLOCKED_409 " +
                                        "• FRESH_VERIFY=PASS"
                                )
                            } catch (error: Exception) {
                                onMessage(
                                    error.message?.take(160)
                                        ?: "LIVE_REPLAY=FAIL"
                                )
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("STAGING CHALLENGE REPLAY TEST")
                }
            }
        }
    }
}
