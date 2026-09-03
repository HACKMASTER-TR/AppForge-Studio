package com.appforge.studio.terminal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun SshWorkspacePanel(
    sshClient: SshTerminalClient
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var profiles by
        remember {
            mutableStateOf(
                SshProfileStore.load(
                    context
                )
            )
        }

    var profileId by
        remember {
            mutableStateOf(
                UUID.randomUUID()
                    .toString()
            )
        }

    var profileName by
        remember {
            mutableStateOf("")
        }

    var host by
        remember {
            mutableStateOf("")
        }

    var port by
        remember {
            mutableStateOf("22")
        }

    var username by
        remember {
            mutableStateOf("")
        }

    var password by
        remember {
            mutableStateOf("")
        }

    var workingDirectory by
        remember {
            mutableStateOf("~")
        }

    var privateKey by
        remember {
            mutableStateOf<ByteArray?>(
                null
            )
        }

    var privateKeyName by
        remember {
            mutableStateOf("")
        }

    var passphrase by
        remember {
            mutableStateOf("")
        }

    var command by
        remember {
            mutableStateOf("pwd && ls -la")
        }

    var output by
        remember {
            mutableStateOf(
                "Önce sunucu bilgilerini girip anahtar parmak izini doğrulayın."
            )
        }

    var busy by
        remember {
            mutableStateOf(false)
        }

    var operationId by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    var pendingProbe by
        remember {
            mutableStateOf<SshHostProbe?>(
                null
            )
        }

    var trustedRefresh by
        remember {
            mutableStateOf(0)
        }

    DisposableEffect(operationId) {
        val activeOperation =
            operationId

        onDispose {
            activeOperation
                ?.let(
                    sshClient::cancel
                )
        }
    }

    DisposableEffect(privateKey) {
        val keyBytes =
            privateKey

        onDispose {
            keyBytes?.fill(0)
        }
    }

    fun currentProfile() =
        SshProfile(
            id = profileId,
            name =
                profileName.ifBlank {
                    if (
                        username.isNotBlank() &&
                        host.isNotBlank()
                    ) {
                        "$username@$host"
                    } else {
                        "SSH Sunucusu"
                    }
                },
            host = host.trim(),
            port =
                port.toIntOrNull()
                    ?: 0,
            username =
                username.trim(),
            workingDirectory =
                workingDirectory
                    .trim()
                    .ifBlank {
                        "~"
                    }
        )

    fun currentAuth() =
        SshAuth(
            password = password,
            privateKey = privateKey,
            privateKeyName =
                privateKeyName,
            passphrase = passphrase
        )

    val trusted =
        remember(
            host,
            port,
            username,
            trustedRefresh
        ) {
            sshClient.isTrusted(
                currentProfile()
            )
        }

    val keyPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                runCatching {
                    context.contentResolver
                        .openInputStream(uri)
                        ?.use {
                            input ->
                            val output =
                                java.io.ByteArrayOutputStream()
                            val buffer =
                                ByteArray(8_192)

                            while (
                                output.size() <=
                                MAX_SSH_KEY_BYTES
                            ) {
                                val read =
                                    input.read(buffer)

                                if (read <= 0) break

                                output.write(
                                    buffer,
                                    0,
                                    read
                                )
                            }

                            output.toByteArray()
                        }
                        ?: error(
                            "Özel anahtar okunamadı."
                        )
                }.onSuccess { bytes ->
                    if (bytes.size > MAX_SSH_KEY_BYTES) {
                        output =
                            "Özel anahtar dosyası çok büyük."
                    } else {
                        privateKey =
                            bytes

                        privateKeyName =
                            uri.lastPathSegment
                                ?.substringAfterLast('/')
                                ?.ifBlank {
                                    "private-key"
                                }
                                ?: "private-key"

                        output =
                            "Özel anahtar yalnız bu oturum için belleğe alındı."
                    }
                }.onFailure {
                    output =
                        it.message
                            ?: "Özel anahtar okunamadı."
                }
            }
        }

    pendingProbe
        ?.let { probe ->
            AlertDialog(
                onDismissRequest = {
                    pendingProbe =
                        null
                },
                title = {
                    Text("Sunucu anahtarını doğrula")
                },
                text = {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(9.dp)
                    ) {
                        Text(
                            "Bu parmak izini sunucu yöneticisinden aldığın değerle karşılaştır. Eşleşiyorsa güven."
                        )

                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        TerminalBackground
                                )
                        ) {
                            SelectionContainer {
                                Column(
                                    modifier =
                                        Modifier.padding(12.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        "${probe.host}:${probe.port}",
                                        color =
                                            TerminalSecondary,
                                        fontFamily =
                                            FontFamily.Monospace
                                    )

                                    Text(
                                        probe.keyType,
                                        color =
                                            TerminalMuted,
                                        fontSize =
                                            10.sp
                                    )

                                    Text(
                                        probe.fingerprint,
                                        color =
                                            TerminalWarning,
                                        fontFamily =
                                            FontFamily.Monospace,
                                        fontSize =
                                            11.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingProbe =
                                null

                            scope.launch {
                                runCatching {
                                    sshClient.trustHost(
                                        probe
                                    )
                                }.onSuccess {
                                    trustedRefresh +=
                                        1

                                    output =
                                        "Sunucu anahtarı güvenilir olarak kaydedildi. Artık komut çalıştırabilirsin."
                                }.onFailure {
                                    output =
                                        it.message
                                            ?: "Sunucu anahtarı kaydedilemedi."
                                }
                            }
                        }
                    ) {
                        Text("Güven")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingProbe =
                                null
                        }
                    ) {
                        Text("Vazgeç")
                    }
                }
            )
        }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        item {
            TerminalPanelTitle(
                "Güvenli SSH terminali",
                "Sunucu profilleri saklanır; parola, token, özel anahtar ve passphrase saklanmaz."
            )
        }

        if (profiles.isNotEmpty()) {
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState()
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    profiles.forEach { profile ->
                        OutlinedButton(
                            onClick = {
                                profileId =
                                    profile.id

                                profileName =
                                    profile.name

                                host =
                                    profile.host

                                port =
                                    profile.port
                                        .toString()

                                username =
                                    profile.username

                                workingDirectory =
                                    profile.workingDirectory

                                password =
                                    ""

                                passphrase =
                                    ""

                                privateKey =
                                    null

                                privateKeyName =
                                    ""

                                output =
                                    "Profil yüklendi. Kimlik bilgisini girin."
                            }
                        ) {
                            Text(profile.name)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalSurface
                    )
            ) {
                Column(
                    modifier =
                        Modifier.padding(13.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = {
                            profileName = it.take(120)
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        label = {
                            Text("Profil adı")
                        },
                        placeholder = {
                            Text("Üretim Sunucusu")
                        },
                        singleLine = true
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = {
                                host = it.take(253)
                            },
                            modifier =
                                Modifier.weight(1f),
                            label = {
                                Text("Sunucu / IP")
                            },
                            placeholder = {
                                Text("192.168.1.10")
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = port,
                            onValueChange = {
                                port =
                                    it.filter(
                                        Char::isDigit
                                    ).take(5)
                            },
                            modifier =
                                Modifier.weight(0.45f),
                            label = {
                                Text("Port")
                            },
                            singleLine = true
                        )
                    }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it.take(128)
                            },
                            modifier =
                                Modifier.weight(1f),
                            label = {
                                Text("Kullanıcı")
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = workingDirectory,
                            onValueChange = {
                                workingDirectory =
                                    it.take(2_048)
                            },
                            modifier =
                                Modifier.weight(1f),
                            label = {
                                Text("Başlangıç klasörü")
                            },
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password =
                                it.take(32 * 1_024)
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        label = {
                            Text("Parola (bellekte tutulur)")
                        },
                        visualTransformation =
                            PasswordVisualTransformation(),
                        singleLine = true
                    )

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                keyPicker.launch(
                                    arrayOf(
                                        "application/x-pem-file",
                                        "application/pkcs8",
                                        "text/plain",
                                        "application/octet-stream"
                                    )
                                )
                            }
                        ) {
                            Text(
                                if (privateKey == null) {
                                    "Özel Anahtar Seç"
                                } else {
                                    "Anahtar: $privateKeyName"
                                }
                            )
                        }

                        if (privateKey != null) {
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = {
                                    passphrase =
                                        it.take(32 * 1_024)
                                },
                                label = {
                                    Text("Passphrase")
                                },
                                visualTransformation =
                                    PasswordVisualTransformation(),
                                singleLine = true
                            )

                            TextButton(
                                onClick = {
                                    privateKey =
                                        null

                                    privateKeyName =
                                        ""

                                    passphrase =
                                        ""
                                }
                            ) {
                                Text(
                                    "Temizle",
                                    color =
                                        TerminalError
                                )
                            }
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val profile =
                                    currentProfile()

                                profiles =
                                    SshProfileStore.save(
                                        context,
                                        profile
                                    )

                                profileId =
                                    profile.id

                                output =
                                    "SSH profili kaydedildi; gizli bilgiler kaydedilmedi."
                            },
                            enabled =
                                host.isNotBlank() &&
                                    username.isNotBlank() &&
                                    (
                                        port.toIntOrNull()
                                            ?.let {
                                                it in 1..65_535
                                            } == true
                                    )
                        ) {
                            Text("Profili Kaydet")
                        }

                        OutlinedButton(
                            onClick = {
                                profiles =
                                    SshProfileStore.delete(
                                        context,
                                        profileId
                                    )

                                output =
                                    "Profil silindi."
                            },
                            enabled =
                                profiles.any {
                                    it.id == profileId
                                }
                        ) {
                            Text("Profili Sil")
                        }

                        Button(
                            onClick = {
                                if (busy) {
                                    return@Button
                                }

                                busy =
                                    true

                                output =
                                    "Sunucu anahtarı alınıyor…"

                                scope.launch {
                                    runCatching {
                                        sshClient.probe(
                                            currentProfile()
                                        )
                                    }.onSuccess {
                                        pendingProbe =
                                            it

                                        output =
                                            "Parmak izi alındı; onay penceresini kontrol edin."
                                    }.onFailure {
                                        output =
                                            "SSH bağlantısı kurulamadı: ${it.message ?: "Bilinmeyen hata"}"
                                    }

                                    busy =
                                        false
                                }
                            },
                            enabled =
                                !busy &&
                                    host.isNotBlank() &&
                                    username.isNotBlank() &&
                                    (
                                        port.toIntOrNull()
                                            ?.let {
                                                it in 1..65_535
                                            } == true
                                    )
                        ) {
                            Text(
                                if (trusted) {
                                    "Anahtarı Yeniden Doğrula"
                                } else {
                                    "Bağlantıyı Doğrula"
                                }
                            )
                        }
                    }

                    Text(
                        if (trusted) {
                            "✓ Bu sunucunun anahtarı güvenilir."
                        } else {
                            "! Komut göndermeden önce sunucu anahtarını doğrulayın."
                        },
                        color =
                            if (trusted) {
                                TerminalPrimary
                            } else {
                                TerminalWarning
                            },
                        fontSize =
                            11.sp
                    )
                }
            }
        }

        item {
            TerminalPanelTitle(
                "Uzak komut",
                "Python, Node.js, Git, Docker ve paket yöneticisi sunucuda kuruluysa doğrudan kullanılabilir."
            )
        }

        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = {
                        command =
                            it.take(16 * 1_024)
                    },
                    modifier =
                        Modifier.weight(1f),
                    label = {
                        Text("SSH komutu")
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction =
                                ImeAction.Send
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onSend = {
                                if (
                                    trusted &&
                                    !busy &&
                                    command.isNotBlank() &&
                                    (
                                        password.isNotEmpty() ||
                                            privateKey != null
                                    )
                                ) {
                                    val id =
                                        UUID.randomUUID()
                                            .toString()

                                    operationId =
                                        id

                                    busy =
                                        true

                                    output =
                                        "\$ $command\n"

                                    scope.launch {
                                        runCatching {
                                            sshClient.execute(
                                                id,
                                                currentProfile(),
                                                currentAuth(),
                                                command
                                            )
                                        }.onSuccess {
                                            if (operationId == id) {
                                                output +=
                                                    it.output.ifBlank {
                                                        "Komut ${it.exitCode} koduyla tamamlandı."
                                                    }

                                                workingDirectory =
                                                    it.workingDirectory.path
                                            }
                                        }.onFailure {
                                            if (operationId == id) {
                                                output +=
                                                    "SSH hatası: ${it.message ?: "Bilinmeyen hata"}"
                                            }
                                        }

                                        if (operationId == id) {
                                            operationId =
                                                null

                                            busy =
                                                false
                                        }
                                    }
                                }
                            }
                        )
                )

                if (busy && operationId != null) {
                    Button(
                        onClick = {
                            operationId
                                ?.let(
                                    sshClient::cancel
                                )

                            operationId =
                                null

                            busy =
                                false

                            output +=
                                "\n^C  SSH komutu durduruldu."
                        }
                    ) {
                        Text("Durdur")
                    }
                } else {
                    Button(
                        onClick = {
                            val id =
                                UUID.randomUUID()
                                    .toString()

                            operationId =
                                id

                            busy =
                                true

                            output =
                                "\$ $command\n"

                            scope.launch {
                                runCatching {
                                    sshClient.execute(
                                        id,
                                        currentProfile(),
                                        currentAuth(),
                                        command
                                    )
                                }.onSuccess {
                                    if (operationId == id) {
                                        output +=
                                            it.output.ifBlank {
                                                "Komut ${it.exitCode} koduyla tamamlandı."
                                            }

                                        workingDirectory =
                                            it.workingDirectory.path
                                    }
                                }.onFailure {
                                    if (operationId == id) {
                                        output +=
                                            "SSH hatası: ${it.message ?: "Bilinmeyen hata"}"
                                    }
                                }

                                if (operationId == id) {
                                    operationId =
                                        null

                                    busy =
                                        false
                                }
                            }
                        },
                        enabled =
                            trusted &&
                                !busy &&
                                command.isNotBlank() &&
                                (
                                    password.isNotEmpty() ||
                                        privateKey != null
                                )
                    ) {
                        Text("Çalıştır")
                    }
                }
            }
        }

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalBackground
                    )
            ) {
                SelectionContainer {
                    Text(
                        output,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 170.dp)
                                .padding(12.dp),
                        color =
                            if (
                                output.contains(
                                    "hatası",
                                    ignoreCase = true
                                ) ||
                                output.contains(
                                    "kurulamadı",
                                    ignoreCase = true
                                )
                            ) {
                                TerminalError
                            } else {
                                TerminalText
                            },
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            11.sp,
                        lineHeight =
                            15.sp
                    )
                }
            }
        }

        if (busy) {
            item {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color =
                            TerminalPrimary,
                        strokeWidth =
                            2.dp
                    )

                    Text(
                        "SSH işlemi sürüyor…",
                        color =
                            TerminalMuted
                    )
                }
            }
        }
    }
}

private const val MAX_SSH_KEY_BYTES =
    1 * 1_024 * 1_024
