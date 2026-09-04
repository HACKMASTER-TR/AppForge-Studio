package com.appforge.studio.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun TerminalUltimatePanel(
    workspace: File,
    onRunCommand: (String) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenGit: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSsh: () -> Unit,
    onOpenBuilder: () -> Unit,
    onOpenAi: () -> Unit
) {
    var mode by
        remember {
            mutableStateOf(
                TerminalUltimateMode.EASY
            )
        }

    var pendingAction by
        remember {
            mutableStateOf<UltimateAction?>(
                null
            )
        }

    var editorVisible by
        remember(workspace.absolutePath) {
            mutableStateOf(false)
        }

    var automationVisible by
        remember(workspace.absolutePath) {
            mutableStateOf(false)
        }

    var securityVisible by
        remember(workspace.absolutePath) {
            mutableStateOf(false)
        }

    var aiHandoffRefresh by
        remember(workspace.absolutePath) {
            mutableStateOf(0)
        }

    val detection =
        remember(
            workspace.absolutePath,
            workspace.lastModified()
        ) {
            AppForgeProjectDetector.detect(
                workspace
            )
        }

    val health =
        remember(
            workspace.absolutePath,
            workspace.lastModified(),
            detection
        ) {
            ProjectHealthInspector.inspect(
                workspace,
                detection
            )
        }

    pendingAction
        ?.let { action ->
            val explanation =
                TerminalCommandAdvisor.explain(
                    action.command
                )

            AlertDialog(
                onDismissRequest = {
                    pendingAction =
                        null
                },
                title = {
                    Text(
                        explanation.title
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
                            action.title,
                            fontWeight =
                                FontWeight.Black
                        )

                        Text(
                            action.description,
                            color =
                                TerminalMuted,
                            fontSize =
                                12.sp
                        )

                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        TerminalBackground
                                )
                        ) {
                            Text(
                                TerminalSecretMasker.redact(
                                    action.command
                                ),
                                modifier =
                                    Modifier.padding(
                                        12.dp
                                    ),
                                color =
                                    TerminalPrimary,
                                fontFamily =
                                    FontFamily.Monospace,
                                fontSize =
                                    11.sp
                            )
                        }

                        Text(
                            explanation.description,
                            color =
                                TerminalText,
                            fontSize =
                                12.sp
                        )

                        Text(
                            "Risk: ${explanation.risk}",
                            color =
                                if (
                                    explanation.allowed &&
                                    !explanation.requiresConfirmation
                                ) {
                                    TerminalPrimary
                                } else {
                                    TerminalWarning
                                },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                11.sp
                        )

                        if (
                            action.target ==
                            UltimateActionTarget.LINUX
                        ) {
                            Text(
                                "Bu görev doğrulanmış AppForge rootless Linux ortamı ister. Linux Modu'na geçerek aynı proje kökünde gerçek PTY ve araç zincirleriyle çalıştırabilirsin.",
                                color =
                                    TerminalSecondary,
                                fontSize =
                                    11.sp,
                                lineHeight =
                                    16.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    when {
                        !explanation.allowed ->
                            TextButton(
                                onClick = {
                                    pendingAction =
                                        null
                                }
                            ) {
                                Text("Kapat")
                            }

                        action.target ==
                            UltimateActionTarget.LINUX ->
                            Button(
                                onClick = {
                                    pendingAction =
                                        null
                                    mode =
                                        TerminalUltimateMode.LINUX
                                }
                            ) {
                                Text("Linux Moduna Geç")
                            }

                        action.command ==
                            "appforge build" ->
                            Button(
                                onClick = {
                                    pendingAction =
                                        null
                                    onOpenBuilder()
                                }
                            ) {
                                Text("Builder'ı Aç")
                            }

                        else ->
                            Button(
                                onClick = {
                                    pendingAction =
                                        null
                                    onRunCommand(
                                        action.command
                                    )
                                }
                            ) {
                                Text("Çalıştır")
                            }
                    }
                },
                dismissButton = {
                    if (explanation.allowed) {
                        TextButton(
                            onClick = {
                                pendingAction =
                                    null
                            }
                        ) {
                            Text("Vazgeç")
                        }
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
            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            TerminalSurfaceRaised
                    )
            ) {
                Column(
                    modifier =
                        Modifier.padding(15.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "AppForge Terminal Ultimate",
                        color =
                            TerminalText,
                        fontWeight =
                            FontWeight.Black,
                        fontSize =
                            20.sp
                    )

                    Text(
                        "Termux + VS Code + görsel Git + deployment + SSH + AI geliştirici için güvenli temel.",
                        color =
                            TerminalMuted,
                        fontSize =
                            11.sp,
                        lineHeight =
                            16.sp
                    )

                    Text(
                        "Çalışma alanı: ${workspace.name}",
                        color =
                            TerminalPrimary,
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            10.sp
                    )
                }
            }
        }

        item {
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
                TerminalUltimateMode.entries
                    .forEach { item ->
                        if (item == mode) {
                            Button(
                                onClick = {
                                    mode = item
                                }
                            ) {
                                Text(item.title)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    mode = item
                                }
                            ) {
                                Text(item.title)
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
                        Modifier.padding(14.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        detection.kind.title,
                        color =
                            TerminalPrimary,
                        fontWeight =
                            FontWeight.Black,
                        fontSize =
                            17.sp
                    )

                    Text(
                        if (
                            detection.markers.isEmpty()
                        ) {
                            "Proje işareti bulunamadı; genel çalışma alanı olarak kullanılacak."
                        } else {
                            "Algılanan işaretler: ${detection.markers.joinToString()}"
                        },
                        color =
                            TerminalMuted,
                        fontSize =
                            11.sp
                    )
                }
            }
        }

        item {
            UltimateNavigationCard(
                title =
                    "Proje Otomasyonu + Paket Mağazası",
                detail =
                    "Proje türüne göre gerekli Linux araç zincirlerini, install/test/build adımlarını ve uygun deployment hedeflerini tek yerde yönet.",
                button =
                    if (automationVisible) {
                        "Otomasyonu Gizle"
                    } else {
                        "Otomasyonu Aç"
                    },
                onClick = {
                    automationVisible =
                        !automationVisible
                }
            )
        }

        if (automationVisible) {
            item {
                UltimateProjectAutomationPanel(
                    workspace = workspace,
                    detection = detection,
                    onOpenDeployment =
                        onOpenConnections,
                    onAiHandoff = { packet ->
                        UltimateAiHandoffStore
                            .publish(packet)
                        aiHandoffRefresh += 1
                        mode =
                            TerminalUltimateMode.AI
                    }
                )
            }
        }

        item {
            UltimateNavigationCard(
                title =
                    "Güvenlik + Geri Yükleme",
                detail =
                    "Biyometrik onay, güvenli restore point ve gizli veri korumasını yönet.",
                button =
                    if (securityVisible) {
                        "Güvenliği Gizle"
                    } else {
                        "Güvenliği Aç"
                    },
                onClick = {
                    securityVisible =
                        !securityVisible
                }
            )
        }

        if (securityVisible) {
            item {
                TerminalSecurityCenterPanel(
                    workspace = workspace
                )
            }
        }

        when (mode) {
            TerminalUltimateMode.EASY -> {
                item {
                    TerminalPanelTitle(
                        "Kolay Mod",
                        "Komut ezberlemeden hazır görev seç. Çalıştırmadan önce Türkçe açıklama ve risk seviyesi gösterilir."
                    )
                }

                items(
                    detection.actions,
                    key = {
                        "${it.id}-${it.command}"
                    }
                ) { action ->
                    UltimateActionCard(
                        action = action,
                        onClick = {
                            pendingAction =
                                action
                        }
                    )
                }

                item {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenFiles,
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text("Dosyalar")
                        }

                        OutlinedButton(
                            onClick = onOpenGit,
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text("Git")
                        }
                    }
                }
            }

            TerminalUltimateMode.ADVANCED -> {
                item {
                    TerminalPanelTitle(
                        "Gelişmiş Mod",
                        "Tam terminal, dosya editörü, Git ve AppForge Builder aynı proje kökünü kullanır."
                    )
                }

                item {
                    UltimateNavigationCard(
                        title =
                            "Terminal",
                        detail =
                            "Çoklu oturum, geçmiş ve güvenlik politikası.",
                        button =
                            "Terminali Aç",
                        onClick =
                            onOpenTerminal
                    )
                }

                item {
                    UltimateNavigationCard(
                        title =
                            "Gelişmiş Kod Editörü",
                        detail =
                            "Çoklu dosya sekmeleri, arama/değiştir, undo/redo, diff ve güvenli geri yükleme noktaları. LSP sunucusu otomatik çalıştırılmaz.",
                        button =
                            if (editorVisible) {
                                "Editörü Gizle"
                            } else {
                                "Editörü Aç"
                            },
                        onClick = {
                            editorVisible =
                                !editorVisible
                        }
                    )
                }

                if (editorVisible) {
                    item {
                        UltimateCodeEditorPanel(
                            workspace = workspace
                        )
                    }
                }

                item {
                    UltimateNavigationCard(
                        title =
                            "Dosya Yöneticisi",
                        detail =
                            "Mevcut güvenli dosya yöneticisi ve temel metin editörünü aç.",
                        button =
                            "Dosyaları Aç",
                        onClick =
                            onOpenFiles
                    )
                }

                item {
                    UltimateNavigationCard(
                        title =
                            "Görsel Git",
                        detail =
                            "Mevcut gömülü JGit motoru ile status, commit, pull, push ve clone.",
                        button =
                            "Git'i Aç",
                        onClick =
                            onOpenGit
                    )
                }

                item {
                    UltimateNavigationCard(
                        title =
                            "APK / AAB",
                        detail =
                            "AppForge Builder ile Android çıktısı oluştur.",
                        button =
                            "Builder",
                        onClick =
                            onOpenBuilder
                    )
                }
            }

            TerminalUltimateMode.LINUX -> {
                item {
                    TerminalPanelTitle(
                        "Linux Modu",
                        "Root gerektirmeyen Debian/Ubuntu çalışma alanının güvenli giriş noktası."
                    )
                }

                item {
                    LinuxRuntimePanel(
                        workspace = workspace,
                        onOpenSsh = onOpenSsh
                    )
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
                                Modifier.padding(14.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                "Native Linux engine: Aşama 2B",
                                color =
                                    TerminalWarning,
                                fontWeight =
                                    FontWeight.Black
                            )

                            Text(
                                "Android'in çalıştırılabilir dosya kısıtlamaları nedeniyle PRoot/PTY motorunu indirilen rastgele binary olarak çalıştırmayacağız. Native motor APK ile güvenilir şekilde paketlenecek; Debian/Ubuntu rootfs doğrulanmış arşiv olarak kurulacak.",
                                color =
                                    TerminalMuted,
                                fontSize =
                                    11.sp,
                                lineHeight =
                                    16.sp
                            )

                            Text(
                                "Hedef: apt, Python, Node.js, Java, PHP, Go, Rust, C/C++ ve etkileşimli PTY.",
                                color =
                                    TerminalSecondary,
                                fontSize =
                                    11.sp
                            )

                            Button(
                                onClick = onOpenSsh
                            ) {
                                Text(
                                    "Şimdi Uzak Linux / SSH Aç"
                                )
                            }
                        }
                    }
                }
            }

            TerminalUltimateMode.SERVER -> {
                item {
                    TerminalPanelTitle(
                        "Sunucu Modu",
                        "SSH ve bulut sağlayıcı bağlantılarını tek merkezden yönet."
                    )
                }

                item {
                    UltimateNavigationCard(
                        title =
                            "SSH / Uzak Linux",
                        detail =
                            "Mevcut güvenli SSH profilleri ve host fingerprint doğrulaması.",
                        button =
                            "SSH Aç",
                        onClick =
                            onOpenSsh
                    )
                }

                item {
                    UltimateNavigationCard(
                        title =
                            "Deployment Bağlantıları",
                        detail =
                            "GitHub ve Railway OAuth bağlantılarını aç. Vercel, Cloudflare, Firebase, Supabase ve Render sağlayıcı adaptörleri sonraki aşamalarda aynı katmana eklenecek.",
                        button =
                            "Bağlantıları Aç",
                        onClick =
                            onOpenConnections
                    )
                }
            }

            TerminalUltimateMode.AI -> {
                item {
                    TerminalPanelTitle(
                        "AI Modu",
                        "Türkçe geliştirici, hata açıklaması ve güvenli komut hazırlama."
                    )
                }

                val pipelineHandoff =
                    remember(
                        aiHandoffRefresh
                    ) {
                        UltimateAiHandoffStore
                            .peek()
                    }

                if (pipelineHandoff != null) {
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
                                    Modifier.padding(14.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Pipeline hata aktarımı",
                                    color =
                                        TerminalWarning,
                                    fontWeight =
                                        FontWeight.Black
                                )

                                Text(
                                    pipelineHandoff,
                                    color =
                                        TerminalMuted,
                                    fontFamily =
                                        FontFamily.Monospace,
                                    fontSize =
                                        9.sp,
                                    lineHeight =
                                        13.sp
                                )

                                Row(
                                    horizontalArrangement =
                                        Arrangement.spacedBy(7.dp)
                                ) {
                                    Button(
                                        onClick =
                                            onOpenAi
                                    ) {
                                        Text(
                                            "AppForge AI Geliştiriciyi Aç"
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            UltimateAiHandoffStore
                                                .clear()
                                            aiHandoffRefresh += 1
                                        }
                                    ) {
                                        Text(
                                            "Aktarımı Temizle"
                                        )
                                    }
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
                                Modifier.padding(14.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Güvenli AI çalışma akışı",
                                color =
                                    TerminalText,
                                fontWeight =
                                    FontWeight.Black
                            )

                            Text(
                                "AI komutu doğrudan sessizce çalıştırmaz. Önce açıklama, risk ve komut önizlemesi gösterilir; tehlikeli komutlar mevcut TerminalCommandPolicy tarafından ayrıca onay ister.",
                                color =
                                    TerminalMuted,
                                fontSize =
                                    11.sp,
                                lineHeight =
                                    16.sp
                            )

                            Text(
                                "Terminal çıktılarındaki token, parola ve API anahtarı benzeri değerler Ultimate maskeleme katmanından geçirilir.",
                                color =
                                    TerminalSecondary,
                                fontSize =
                                    11.sp
                            )

                            Button(
                                onClick = onOpenAi
                            ) {
                                Text(
                                    "AppForge AI Geliştiriciyi Aç"
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            TerminalPanelTitle(
                "Proje Sağlığı",
                "Hızlı ve yerel kontroller; hiçbir dosya dışarı gönderilmez."
            )
        }

        items(
            health,
            key = {
                "${it.level}-${it.title}"
            }
        ) { item ->
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
                        Modifier.padding(12.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        when (item.level) {
                            ProjectHealthLevel.OK ->
                                "✓ ${item.title}"

                            ProjectHealthLevel.INFO ->
                                "• ${item.title}"

                            ProjectHealthLevel.WARNING ->
                                "⚠ ${item.title}"
                        },
                        color =
                            when (item.level) {
                                ProjectHealthLevel.OK ->
                                    TerminalPrimary

                                ProjectHealthLevel.INFO ->
                                    TerminalSecondary

                                ProjectHealthLevel.WARNING ->
                                    TerminalWarning
                            },
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        item.detail,
                        color =
                            TerminalMuted,
                        fontSize =
                            10.sp,
                        lineHeight =
                            15.sp
                    )
                }
            }
        }

        item {
            Text(
                "Ultimate Foundation • gizli bilgi maskeleme etkin • mevcut Terminal/Files/Git/Connections/SSH/Tools korunur",
                color =
                    TerminalMuted,
                fontSize =
                    9.sp,
                modifier =
                    Modifier.padding(
                        vertical = 8.dp
                    )
            )
        }
    }
}

@Composable
private fun UltimateActionCard(
    action: UltimateAction,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
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
                Arrangement.spacedBy(4.dp)
        ) {
            Text(
                action.title,
                color =
                    TerminalText,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                action.description,
                color =
                    TerminalMuted,
                fontSize =
                    10.sp
            )

            Text(
                TerminalSecretMasker.redact(
                    action.command
                ),
                color =
                    TerminalPrimary,
                fontFamily =
                    FontFamily.Monospace,
                fontSize =
                    10.sp
            )

            Text(
                when (action.target) {
                    UltimateActionTarget.LOCAL ->
                        "Yerel"

                    UltimateActionTarget.LINUX ->
                        "Linux"

                    UltimateActionTarget.SERVER ->
                        "Sunucu"

                    UltimateActionTarget.APPFORGE ->
                        "AppForge"
                },
                color =
                    TerminalSecondary,
                fontSize =
                    9.sp
            )
        }
    }
}

@Composable
private fun UltimateNavigationCard(
    title: String,
    detail: String,
    button: String,
    onClick: () -> Unit
) {
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
                Arrangement.spacedBy(7.dp)
        ) {
            Text(
                title,
                color =
                    TerminalText,
                fontWeight =
                    FontWeight.Black
            )

            Text(
                detail,
                color =
                    TerminalMuted,
                fontSize =
                    10.sp,
                lineHeight =
                    15.sp
            )

            OutlinedButton(
                onClick = onClick
            ) {
                Text(button)
            }
        }
    }
}
