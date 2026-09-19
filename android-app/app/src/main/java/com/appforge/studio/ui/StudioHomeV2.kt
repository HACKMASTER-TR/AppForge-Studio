@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appforge.studio.io.ProjectLibrary
import com.appforge.studio.io.SavedProject
import com.appforge.studio.security.OwnerAccessPolicy
import java.text.DateFormat
import java.util.Date

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
        OwnerAccessPolicy
            .isActiveOwner(
                context,
                accountEmail
            )

    val loggedIn =
        !accountEmail
            .isNullOrBlank()
    val projects = remember(accountEmail) { ProjectLibrary.load(context).take(8) }
    val builds = remember(accountEmail) { ProjectLibrary.loadBuilds(context) }
    val buildFolderOpen = rememberSaveable(accountEmail) { mutableStateOf(false) }

    if (buildFolderOpen.value) {
        BackHandler { buildFolderOpen.value = false }
        DownloadedApkFolderScreen(
            onBack = { buildFolderOpen.value = false },
            onInstall = onInstallDownloadedApk,
            buildServiceUrl = buildServiceUrl,
            buildApiKey = buildApiKey
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AppForge Studio", fontWeight = FontWeight.Black)
                        Text("Proje seç • AppForge cihazda derlesin")
                    }
                },
                actions = {
                    if (fullAdmin) {
                        TextButton(onClick = onOpenAdmin) {
                            Text("Admin")
                        }
                    }
                    TextButton(
                        onClick = onOpenAccount
                    ) {
                        Text(
                            if (loggedIn) {
                                "Hesap"
                            } else {
                                "GİRİŞ YAP"
                            }
                        )
                    }
                    TextButton(onClick = onOpenSettings) { Text("Ayarlar") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Yeni proje", fontWeight = FontWeight.Black)
                        Text("Kaynağı seç. Dil ve build motorunu AppForge otomatik belirler.")
                        Button(onClick = onCreateQuick, modifier = Modifier.fillMaxWidth()) {
                            Text("YENİ PROJE")
                        }
                        OutlinedButton(onClick = onCreateAdvanced, modifier = Modifier.fillMaxWidth()) {
                            Text("GELİŞMİŞ AYARLAR")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenAi() },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AppForge AI", fontWeight = FontWeight.Bold)
                        Text("Projeyi analiz et, sorunları açıkla ve doğru ayara git.")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (fullAdmin) {
                        OutlinedButton(
                            onClick = onOpenTerminal,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Terminal")
                        }
                    }
                    OutlinedButton(
                        onClick = { buildFolderOpen.value = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Derlemeler (${builds.size})")
                    }
                }
            }

            item { Text("Projeler", fontWeight = FontWeight.Black) }

            if (projects.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Henüz proje yok. Yeni Proje ile başlayabilirsin.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenProject(project) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(project.name, fontWeight = FontWeight.Bold)
                            Text(project.packageName)
                            Text(
                                DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT
                                ).format(Date(project.updatedAt))
                            )
                        }
                    }
                }
            }

            item { Text("Araçlar", fontWeight = FontWeight.Black) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onOpenTemplates, modifier = Modifier.weight(1f)) {
                        Text("Şablonlar")
                    }
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                        Text("Geçmiş")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onOpenTrash, modifier = Modifier.weight(1f)) {
                        Text("Geri Dönüşüm")
                    }
                    OutlinedButton(onClick = onOpenPro, modifier = Modifier.weight(1f)) {
                        Text(if (proUnlocked) "PRO" else "Plan")
                    }
                }
            }
        }
    }
}
