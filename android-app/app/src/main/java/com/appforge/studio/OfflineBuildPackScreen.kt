@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.appforge.studio

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
import kotlinx.coroutines.launch

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
                    status.currentAndroidEnginesReady
                ) {
                    "Android / Node / Python paketi hazır. Portable EXE doğrulaması bekliyor."
                } else {
                    "Paket henüz kurulmadı."
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

                                installing =
                                    true

                                detail =
                                    "Paket kurulumu başlatılıyor..."

                                scope.launch {

                                    val result =
                                        runCatching {
                                            OfflineBuildPackManager
                                                .installReadyComponents(
                                                    context
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
                                                } else {
                                                    "Hazır bileşenler kuruldu. " +
                                                        "Portable EXE motoru " +
                                                        "Windows kabul testini bekliyor."
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
                        "Paket mimarisine dahil. Android üzerinde EXE motoru " +
                            "ve gerçek Windows çalıştırma testi tamamlanmadan " +
                            "READY olmayacak.",

                    ready =
                        status.windowsExeReady
                )
            }
        }
    }
}

@Composable
private fun OfflinePackComponentCard(
    title: String,
    subtitle: String,
    ready: Boolean
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
                    "BEKLİYOR"
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
