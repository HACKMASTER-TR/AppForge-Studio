@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.appforge.studio.ui
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.io.ProjectLibrary
import com.appforge.studio.io.SavedProject
import com.appforge.studio.security.OwnerAccessPolicy
@Composable
fun StudioHomeV2(
    proUnlocked: Boolean,
    accountEmail: String?,
    buildServiceUrl: String,
    buildApiKey: String,
    onCreateQuick: () -> Unit,
    onCreateAdvanced: () -> Unit,
    onCreateConversion: () -> Unit,
    onOpenProject: (SavedProject) -> Unit,
    onOpenAi: () -> Unit,
    onOpenUnifiedAgent: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenOtherApps: () -> Unit,
    onImportProject: () -> Unit,
    onExportAllProjects: () -> Unit,
    onExportAllAndroidProjects: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenHistory: () -> Unit,
    onInstallDownloadedApk: (android.net.Uri, String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenPro: () -> Unit
) {
    val context = LocalContext.current
    val fullAdmin =
        OwnerAccessPolicy.isActiveOwner(context, accountEmail)
    val allProjects =
        remember(accountEmail) {
            ProjectLibrary.load(context)
                .sortedByDescending { it.updatedAt }
        }
    val projects =
        remember(allProjects) {
            allProjects.take(8)
        }
    val builds =
        remember(accountEmail) {
            ProjectLibrary.loadBuilds(context)
        }
    val buildFolderOpen =
        rememberSaveable(accountEmail) {
            mutableStateOf(false)
        }
    if (buildFolderOpen.value) {
        BackHandler {
            buildFolderOpen.value = false
        }
        DownloadedApkFolderScreen(
            onBack = { buildFolderOpen.value = false },
            onInstall = onInstallDownloadedApk,
            buildServiceUrl = buildServiceUrl,
            buildApiKey = buildApiKey
        )
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = { HomeTopTitle() },
                actions = {
                    TextButton(onClick = onOpenAdmin) {
                        Text(if (fullAdmin) "Yönetici" else "YÖNETİCİ GİRİŞİ")
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text("Ayarlar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 32.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {
            item {
                ModernHomeHero(
                    projectCount = allProjects.size,
                    buildCount = builds.size,
                    proUnlocked = proUnlocked,
                    onCreateQuick = onCreateQuick,
                    onCreateAdvanced = onCreateAdvanced
                )
            }
            item {
                HomeSectionTitle(
                    "Hızlı erişim",
                    "Üretim, AI ve proje araçlarına hızlı ulaş."
                )
            }
            item {
                ModernHomeActionRow(
                    "AI",
                    "AppForge AI",
                    "Projeyi analiz et ve düzelt.",
                    onOpenAi,
                    "AGENT",
                    "Unified Agent",
                    "Geliştirme akışını yönet.",
                    onOpenUnifiedAgent
                )
            }
            item {
                ModernHomeActionRow(
                    "BUILD",
                    "Derlemeler",
                    "${builds.size} kayıt • APK / AAB / EXE",
                    { buildFolderOpen.value = true },
                    "CONVERT",
                    "Dönüştür",
                    "APK ↔ EXE proje dönüşümü.",
                    onCreateConversion
                )
            }
            item {
                ModernHomeActionRow(
                    "IMPORT",
                    "İçe aktar",
                    "Hazır projeyi AppForge'a getir.",
                    onImportProject,
                    "TASK",
                    "Görevler",
                    "Aktif çalışma listesini aç.",
                    onOpenTasks
                )
            }
            if (fullAdmin) {
                item {
                    OwnerAdminCard(
                        terminalTitle = "Terminal",
                        onClick = onOpenTerminal,
                        onAdminClick = onOpenAdmin
                    )
                }
            }
            item {
                HomeSectionTitle(
                    "Projelerin",
                    if (allProjects.isEmpty()) {
                        "Yeni proje oluştur veya içe aktar."
                    } else {
                        "Son güncellenen projeler."
                    }
                )
            }
            if (projects.isEmpty()) {
                item {
                    EmptyProjectCard(
                        "Yeni proje oluşturabilir veya mevcut bir projeyi içe aktarabilirsin."
                    )
                }
            } else {
                items(
                    items = projects,
                    key = { it.id }
                ) { project ->
                    ModernProjectCard(
                        project = project,
                        onClick = {
                            onOpenProject(project)
                        }
                    )
                }
            }
            item {
                HomeSectionTitle(
                    "Proje araçları",
                    "Yönetim, geçmiş ve dışa aktarma."
                )
            }
            item {
                HomeToolRow(
                    "Şablonlar",
                    onOpenTemplates,
                    "Geçmiş",
                    onOpenHistory
                )
            }
            item {
                HomeToolRow(
                    "Uygulamalar",
                    onOpenOtherApps,
                    "Geri Dönüşüm",
                    onOpenTrash
                )
            }
            item {
                HomeToolRow(
                    "Tümünü dışa aktar",
                    onExportAllProjects,
                    "Android dışa aktar",
                    onExportAllAndroidProjects
                )
            }
            item {
                ModernProCard(
                    proUnlocked = proUnlocked,
                    onClick = onOpenPro
                )
            }
        }
    }
}
