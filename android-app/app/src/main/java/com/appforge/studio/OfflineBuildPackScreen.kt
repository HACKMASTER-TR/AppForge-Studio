@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.appforge.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appforge.studio.build.OfflineBuildPackManager
import com.appforge.studio.build.OfflineBuildPackStatus
import com.appforge.studio.build.WindowsPortableExePackager
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun OfflineBuildPackScreen(
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var status by
        remember {
            mutableStateOf(
                OfflineBuildPackManager.status(
                    context
                )
            )
        }

    var installing by
        remember {
            mutableStateOf(
                false
            )
        }

    var detail by
        remember {
            mutableStateOf(
                if (
                    status.completeTargetReady
                ) {
                    "Tam çevrimdışı paket hazır."
                } else if (
                    status.currentAndroidEnginesReady &&
                    status.windowsHostReady
                ) {
                    "Android / Node / Python ve Windows Host hazır. " +
                        "Cihaz EXE paketleme kabulü bekliyor."
                } else if (
                    status.currentAndroidEnginesReady
                ) {
                    "Android / Node / Python paketi hazır. " +
                        "Windows Host kurulumu bekliyor."
                } else {
                    "Paket henüz kurulmadı."
                }
            )
        }


    val licensePreferences =
        remember {
            context.getSharedPreferences(
                "appforge_offline_pack",
                android.content.Context.MODE_PRIVATE
            )
        }

    var androidSdkLicenseAccepted by
        remember {
            mutableStateOf(
                licensePreferences
                    .getBoolean(
                        "android_sdk_license_2026_04_28",
                        false
                    )
            )
        }

    var showAndroidSdkLicenseDialog by
        remember {
            mutableStateOf(
                false
            )
        }


    var windowsSmokeBusy by
        remember {
            mutableStateOf(false)
        }

    var windowsSmokeFile by
        remember {
            mutableStateOf<File?>(null)
        }

    val windowsSmokeSaveLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.CreateDocument(
                    "application/octet-stream"
                )
        ) { uri ->
            val source = windowsSmokeFile

            if (
                uri != null &&
                source?.isFile == true
            ) {
                val result =
                    runCatching {
                        context.contentResolver
                            .openOutputStream(uri, "w")
                            ?.use { output ->
                                source.inputStream()
                                    .buffered(1024 * 1024)
                                    .use { input ->
                                        input.copyTo(
                                            output,
                                            1024 * 1024
                                        )
                                    }
                            }
                            ?: error(
                                "Windows EXE kayıt hedefi açılamadı."
                            )
                    }

                detail =
                    result.fold(
                        onSuccess = {
                            "Windows kabul EXE'si kaydedildi. " +
                                "Dosyayı gerçek Windows bilgisayarda aç."
                        },
                        onFailure = {
                            "Windows EXE kaydedilemedi: " +
                                (
                                    it.message
                                        ?: it.javaClass.simpleName
                                )
                        }
                    )
            }
        }

    fun beginWindowsSmoke() {
        windowsSmokeBusy = true
        windowsSmokeFile = null
        detail =
            "Cihaz-local Windows EXE kabul dosyası oluşturuluyor..."

        scope.launch {
            val result =
                runCatching {
                    WindowsPortableExePackager
                        .createAcceptanceSmoke(
                            context
                        ) { message ->
                            scope.launch {
                                detail = message
                            }
                        }
                }

            windowsSmokeFile =
                result.getOrNull()

            detail =
                result.fold(
                    onSuccess = {
                        "Cihazda gerçek Portable EXE oluşturuldu. " +
                            "Şimdi Windows'a kaydet ve çalıştır."
                    },
                    onFailure = {
                        "Cihaz EXE paketleme testi başarısız: " +
                            (
                                it.message
                                    ?: it.javaClass.simpleName
                            )
                    }
                )

            windowsSmokeBusy = false
        }
    }


    fun beginInstall() {
        installing =
            true

        detail =
            "Paket kurulumu başlatılıyor..."

        scope.launch {

            val result =
                runCatching {
                    OfflineBuildPackManager
                        .installReadyComponents(
                            context = context,
                            androidSdkLicenseAccepted = true
                        ) {
                            message ->

                            scope.launch {
                                detail =
                                    message
                            }
                        }
                }

            status =
                OfflineBuildPackManager
                    .status(
                        context
                    )

            detail =
                result.fold(
                    onSuccess = {
                        if (
                            it.completeTargetReady
                        ) {
                            "Tam çevrimdışı paket hazır."
                        } else if (
                            it.windowsHostReady
                        ) {
                            "Android / Node / Python ve Windows Host hazır. " +
                                "Cihaz-local EXE paketleme kabulü bekliyor."
                        } else {
                            "Hazır Android bileşenleri kuruldu. " +
                                "Windows Host kurulumu tamamlanmadı."
                        }
                    },

                    onFailure = {
                        "Kurulum durdu: " +
                            (
                                it.message
                                    ?: it.javaClass.simpleName
                            )
                    }
                )

            installing =
                false
        }
    }

    if (
        showAndroidSdkLicenseDialog
    ) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showAndroidSdkLicenseDialog =
                    false
            },

            title = {
                Text(
                    "Android SDK Lisansı"
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
                        "Android SDK araçlarını kullanabilmek için " +
                            "Google Android SDK Lisans Sözleşmesi'ni " +
                            "inceleyip kabul etmen gerekir."
                    )

                    Text(
                        "Bir kurum veya işveren adına kabul ediyorsan " +
                            "bunu yapmaya yetkili olman gerekir."
                    )

                    androidx.compose.material3.TextButton(
                        onClick = {
                            val termsUri =
                                android.net.Uri.Builder()
                                    .scheme(
                                        "https"
                                    )
                                    .authority(
                                        "developer.android.com"
                                    )
                                    .appendPath(
                                        "studio"
                                    )
                                    .appendPath(
                                        "terms"
                                    )
                                    .build()

                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        termsUri
                                    )
                                )
                            }
                        }
                    ) {
                        Text(
                            "KOŞULLARI AÇ"
                        )
                    }
                }
            },

            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        androidSdkLicenseAccepted =
                            true

                        licensePreferences
                            .edit()
                            .putBoolean(
                                "android_sdk_license_2026_04_28",
                                true
                            )
                            .putLong(
                                "android_sdk_license_accepted_at",
                                System.currentTimeMillis()
                            )
                            .apply()

                        showAndroidSdkLicenseDialog =
                            false

                        beginInstall()
                    }
                ) {
                    Text(
                        "KABUL ET VE KUR"
                    )
                }
            },

            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showAndroidSdkLicenseDialog =
                            false
                    }
                ) {
                    Text(
                        "İPTAL"
                    )
                }
            }
        )
    }

    Scaffold(
        containerColor =
            MaterialTheme
                .colorScheme
                .background,

        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Tam Çevrimdışı Derleme Paketi",
                        fontWeight =
                            FontWeight.Bold
                    )
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
                                MaterialTheme
                                    .colorScheme
                                    .surface
                        )
            )
        }
    ) {
        padding ->

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        padding
                    ),

            contentPadding =
                PaddingValues(
                    16.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            item {
                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
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
                            Arrangement.spacedBy(
                                10.dp
                            )
                    ) {
                        Text(
                            "Tek seferlik kurulum",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "Hedef paket; Android SDK, JDK, Gradle, " +
                                "Node.js/npm, Python/Chaquopy ve Portable EXE " +
                                "araçlarını cihazda tutacak."
                        )

                        Text(
                            "Tahmini indirme: 1,5–2,3 GB • " +
                                "hedef cihaz kullanımı: yaklaşık 4–6 GB"
                        )

                        Text(
                            detail
                        )

                        Button(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),

                            enabled =
                                !installing,

                            onClick = {
                                if (
                                    androidSdkLicenseAccepted
                                ) {
                                    beginInstall()
                                } else {
                                    showAndroidSdkLicenseDialog =
                                        true
                                }
                            }
                        ) {
                            if (
                                installing
                            ) {
                                Row(
                                    verticalAlignment =
                                        Alignment.CenterVertically,

                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            10.dp
                                        )
                                ) {
                                    CircularProgressIndicator()

                                    Text(
                                        "Hazırlanıyor..."
                                    )
                                }
                            } else {
                                Text(
                                    if (
                                        !androidSdkLicenseAccepted
                                    ) {
                                        "ANDROID SDK LİSANSINI İNCELE VE KUR"
                                    } else if (
                                        status.currentAndroidEnginesReady
                                    ) {
                                        "PAKETİ KONTROL ET / TAMAMLA"
                                    } else {
                                        "HAZIR BİLEŞENLERİ İNDİR VE KUR"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                OfflinePackComponentCard(
                    title =
                        "Android SDK + JDK + Gradle",

                    subtitle =
                        "APK/AAB • Android API 37.0 • Build Tools 36 • Gradle cache",

                    ready =
                        status.androidCoreReady
                )
            }

            item {
                OfflinePackComponentCard(
                    title =
                        "Node.js + npm",

                    subtitle =
                        "React / Vue / Svelte / Vite araç zinciri. " +
                            "Proje sürümüne özel npm bağımlılıkları " +
                            "ayrıca cache doğrulamasından geçecek.",

                    ready =
                        status.nodeToolchainReady
                )
            }

            item {
                OfflinePackComponentCard(
                    title =
                        "Python + Chaquopy",

                    subtitle =
                        "Python Android runtime ve Chaquopy Gradle bağımlılıkları",

                    ready =
                        status.pythonAndroidReady
                )
            }

            item {
                OfflinePackComponentCard(
                    title =
                        "Windows Portable EXE",

                    subtitle =
                        "Generic x64 host • 375025483 byte • SHA-256 sabit. " +
                            "Host indirildikten sonra cihaz-local EXE üretimi " +
                            "ve son Windows kabulü tamamlanmadan READY olmayacak.",

                    ready =
                        status.windowsExeReady,

                    pendingText =
                        if (
                            status.windowsHostReady
                        ) {
                            "HOST KURULDU • CİHAZ EXE TESTİ BEKLİYOR"
                        } else {
                            "BEKLİYOR"
                        }
                )
            }

            if (
                status.windowsHostReady &&
                !status.windowsExeReady
            ) {
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant
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
                                "Windows cihaz kabul testi",
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                "Doğrulanmış generic host telefonda kopyalanacak, " +
                                    "AppForge proje payload'ı eklenecek ve gerçek " +
                                    "Portable EXE oluşturulacak. Bu işlem internet " +
                                    "gerektirmez."
                            )

                            Button(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                enabled =
                                    !windowsSmokeBusy &&
                                        !installing,
                                onClick = {
                                    beginWindowsSmoke()
                                }
                            ) {
                                Text(
                                    if (windowsSmokeBusy) {
                                        "EXE OLUŞTURULUYOR..."
                                    } else {
                                        "CİHAZ EXE KABUL DOSYASI OLUŞTUR"
                                    }
                                )
                            }

                            if (
                                windowsSmokeFile?.isFile == true
                            ) {
                                Text(
                                    "EXE boyutu: " +
                                        (
                                            windowsSmokeFile
                                                ?.length()
                                                ?: 0L
                                            ) +
                                        " byte"
                                )

                                Button(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    onClick = {
                                        windowsSmokeSaveLauncher
                                            .launch(
                                                "AppForge-Windows-Device-Smoke.exe"
                                            )
                                    }
                                ) {
                                    Text(
                                        "WINDOWS TEST EXE'SİNİ KAYDET"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflinePackComponentCard(
    title: String,
    subtitle: String,
    ready: Boolean,
    pendingText: String = "BEKLİYOR"
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
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
                Arrangement.spacedBy(
                    6.dp
                )
        ) {
            Text(
                title,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                subtitle
            )

            Text(
                if (
                    ready
                ) {
                    "HAZIR ✓"
                } else {
                    pendingText
                },

                color =
                    if (
                        ready
                    ) {
                        MaterialTheme
                            .colorScheme
                            .primary
                    } else {
                        MaterialTheme
                            .colorScheme
                            .secondary
                    }
            )
        }
    }
}
