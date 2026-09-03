@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.AppForgeBuildNumbers
import com.appforge.studio.io.AppSettingsStore
import com.appforge.studio.io.ProjectLibrary
import com.appforge.studio.io.SavedProject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val V2Bg = Color(0xFF060711)
private val V2Surface = Color(0xFF101426)
private val V2Surface2 = Color(0xFF171B32)
private val V2Primary = Color(0xFF63D9FF)
private val V2Secondary = Color(0xFFB59CFF)
private val V2Warm = Color(0xFFFFB45E)
private val V2Text = Color(0xFFF4F7FF)
private val V2Muted = Color(0xFFA9B1C7)
private val V2Success = Color(0xFF7AE7B7)

private object CopyV2 {
    private val tr = mapOf(
        "studio" to "AppForge Studio",
        "tagline" to "Fikrinden çalışan uygulamaya.",
        "hero_title" to "Yeni bir şey üret",
        "hero_body" to "Kaynağını seç, AppForge analiz etsin ve uygun derleme yolunu otomatik hazırlasın.",
        "quick" to "Hızlı Oluştur",
        "advanced" to "Gelişmiş Oluştur",
        "convert" to "Dönüştür",
        "projects" to "Projeler",
        "successful_builds" to "Başarılı derleme",
        "latest_build" to "Son derleme",
        "ai_title" to "AppForge AI",
        "ai_body" to "Projeni analiz eder, ayarlarını açıklar, derleme sorunlarını yorumlar ve doğru ekrana yönlendirir.",
        "recent_projects" to "Son projeler",
        "no_projects" to "Henüz proje yok",
        "no_projects_body" to "İlk uygulamanı Hızlı Oluştur ile birkaç adımda başlatabilirsin.",
        "tools" to "Studio araçları",
        "other_apps" to "Diğer Uygulamalar",
        "templates" to "Şablonlar",
        "history" to "Derleme Geçmişi",
        "account" to "Hesabım",
        "settings" to "Ayarlar",
        "trash" to "Geri Dönüşüm",
        "backup" to "Yedekleme",
        "import" to "Projeleri İçe Aktar",
        "export" to "Projeleri Dışa Aktar",
        "export_android" to "Android Projelerini Dışa Aktar",
        "pro" to "PRO",
        "free" to "FREE",
        "login" to "GİRİŞ YAP",
        "updated" to "Güncellendi"
    )

    private val en = mapOf(
        "studio" to "AppForge Studio",
        "tagline" to "From idea to working app.",
        "hero_title" to "Create something new",
        "hero_body" to "Choose your source. AppForge analyzes it and prepares the right build path automatically.",
        "quick" to "Quick Create",
        "advanced" to "Advanced Create",
        "convert" to "Convert",
        "projects" to "Projects",
        "successful_builds" to "Successful builds",
        "latest_build" to "Latest build",
        "ai_title" to "AppForge AI",
        "ai_body" to "Analyzes your project, explains settings, diagnoses build issues, and routes you to the right screen.",
        "recent_projects" to "Recent projects",
        "no_projects" to "No projects yet",
        "no_projects_body" to "Start your first app in a few steps with Quick Create.",
        "tools" to "Studio tools",
        "other_apps" to "Other Apps",
        "templates" to "Templates",
        "history" to "Build History",
        "account" to "Account",
        "settings" to "Settings",
        "trash" to "Trash",
        "backup" to "Backup",
        "import" to "Import Projects",
        "export" to "Export Projects",
        "export_android" to "Export Android Projects",
        "pro" to "PRO",
        "free" to "FREE",
        "login" to "SIGN IN",
        "updated" to "Updated"
    )

    private val de = mapOf(
        "studio" to "AppForge Studio",
        "tagline" to "Von der Idee zur fertigen App.",
        "hero_title" to "Etwas Neues erstellen",
        "hero_body" to "Quelle auswählen. AppForge analysiert sie und bereitet automatisch den passenden Build-Weg vor.",
        "quick" to "Schnell erstellen",
        "advanced" to "Erweitert erstellen",
        "convert" to "Konvertieren",
        "projects" to "Projekte",
        "successful_builds" to "Erfolgreiche Builds",
        "latest_build" to "Letzter Build",
        "ai_title" to "AppForge AI",
        "ai_body" to "Analysiert dein Projekt, erklärt Einstellungen, diagnostiziert Build-Probleme und öffnet den richtigen Bereich.",
        "recent_projects" to "Letzte Projekte",
        "no_projects" to "Noch keine Projekte",
        "no_projects_body" to "Starte deine erste App in wenigen Schritten mit Schnell erstellen.",
        "tools" to "Studio-Werkzeuge",
        "other_apps" to "Weitere Apps",
        "templates" to "Vorlagen",
        "history" to "Build-Verlauf",
        "account" to "Konto",
        "settings" to "Einstellungen",
        "trash" to "Papierkorb",
        "backup" to "Sicherung",
        "import" to "Projekte importieren",
        "export" to "Projekte exportieren",
        "export_android" to "Android-Projekte exportieren",
        "pro" to "PRO",
        "free" to "FREE",
        "login" to "ANMELDEN",
        "updated" to "Aktualisiert"
    )

    private val ar = mapOf(
        "studio" to "AppForge Studio",
        "tagline" to "من الفكرة إلى تطبيق يعمل.",
        "hero_title" to "أنشئ شيئًا جديدًا",
        "hero_body" to "اختر المصدر، وسيحلله AppForge ويجهز مسار البناء المناسب تلقائيًا.",
        "quick" to "إنشاء سريع",
        "advanced" to "إنشاء متقدم",
        "convert" to "تحويل",
        "projects" to "المشاريع",
        "successful_builds" to "عمليات البناء الناجحة",
        "latest_build" to "آخر بناء",
        "ai_title" to "AppForge AI",
        "ai_body" to "يحلل مشروعك ويشرح الإعدادات ويشخص مشاكل البناء وينقلك إلى الشاشة المناسبة.",
        "recent_projects" to "المشاريع الأخيرة",
        "no_projects" to "لا توجد مشاريع بعد",
        "no_projects_body" to "ابدأ تطبيقك الأول بخطوات قليلة عبر الإنشاء السريع.",
        "tools" to "أدوات Studio",
        "other_apps" to "تطبيقات أخرى",
        "templates" to "القوالب",
        "history" to "سجل البناء",
        "account" to "الحساب",
        "settings" to "الإعدادات",
        "trash" to "سلة المحذوفات",
        "backup" to "النسخ الاحتياطي",
        "import" to "استيراد المشاريع",
        "export" to "تصدير المشاريع",
        "export_android" to "تصدير مشاريع Android",
        "pro" to "PRO",
        "free" to "FREE",
        "login" to "تسجيل الدخول",
        "updated" to "تم التحديث"
    )

    fun resolve(configured: String): String {
        if (configured != "system") return configured
        val system = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (system in setOf("tr", "en", "de", "ar")) system else "en"
    }

    fun text(language: String, key: String): String =
        when (resolve(language)) {
            "en" -> en[key]
            "de" -> de[key]
            "ar" -> ar[key]
            else -> tr[key]
        } ?: tr[key] ?: key
}

@Composable
fun StudioHomeV2(
    proUnlocked: Boolean,
    accountEmail: String?,
    onCreateQuick: () -> Unit,
    onCreateAdvanced: () -> Unit,
    onCreateConversion: () -> Unit,
    onOpenProject: (SavedProject) -> Unit,
    onOpenAi: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOtherApps: () -> Unit,
    onImportProject: () -> Unit,
    onExportAllProjects: () -> Unit,
    onExportAllAndroidProjects: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenPro: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val compact = config.screenWidthDp < 390
    val tablet = minOf(config.screenWidthDp, config.screenHeightDp) >= 600
    val language = AppSettingsStore.load(context).languageCode
    fun t(key: String) = CopyV2.text(language, key)

    val loggedIn =
        !accountEmail
            .isNullOrBlank()

    val fullAdmin =
        accountEmail
            ?.trim()
            ?.equals(
                "28550040284a@gmail.com",
                ignoreCase = true
            ) == true

    val projects =
        remember(
            accountEmail
        ) {
            ProjectLibrary.load(
                context
            )
        }

    val builds =
        remember(
            accountEmail
        ) {
            ProjectLibrary.loadBuilds(
                context
            )
        }
    val successfulBuilds = remember(builds) { builds.count { it.status == "success" } }
    val latestBuild = remember(builds) { builds.maxByOrNull { it.createdAt } }

    Scaffold(
        containerColor = V2Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(t("studio"), color = V2Text, fontWeight = FontWeight.Black, fontSize = 21.sp)
                        Text(t("tagline"), color = V2Muted, fontSize = 11.sp)
                    }
                },
                actions = {
                    val accountBadgeText =
                        when {
                            !loggedIn ->
                                t("login")

                            fullAdmin ->
                                "ADMIN"

                            proUnlocked ->
                                t("pro")

                            else ->
                                t("free")
                        }

                    val accountBadgeBackground =
                        when {
                            !loggedIn ->
                                V2Primary

                            fullAdmin ->
                                V2Warm

                            proUnlocked ->
                                V2Secondary

                            else ->
                                V2Surface2
                        }

                    val accountBadgeTextColor =
                        if (
                            !loggedIn ||
                            fullAdmin ||
                            proUnlocked
                        ) {
                            Color(0xFF100B1A)
                        } else {
                            V2Text
                        }

                    Card(
                        onClick = {
                            when {
                                !loggedIn ->
                                    onOpenAccount()

                                fullAdmin ->
                                    onOpenAdmin()

                                else ->
                                    onOpenPro()
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                accountBadgeBackground
                        )
                    ) {
                        Text(
                            accountBadgeText,
                            modifier =
                                Modifier.padding(
                                    horizontal = 13.dp,
                                    vertical = 7.dp
                                ),
                            color =
                                accountBadgeTextColor,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                11.sp
                        )
                    }

                    Spacer(
                        Modifier.width(
                            12.dp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = V2Bg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                horizontal = when {
                    tablet -> 30.dp
                    compact -> 12.dp
                    else -> 18.dp
                },
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (
                fullAdmin
            ) {
                item {
                    Card(
                        onClick =
                            onOpenAdmin,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .widthIn(
                                    max = 980.dp
                                ),
                        shape =
                            RoundedCornerShape(
                                22.dp
                            ),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    V2Warm
                            )
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 18.dp,
                                        vertical = 16.dp
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        4.dp
                                    )
                            ) {
                                Text(
                                    "⚙ YÖNETİCİ PANELİ",
                                    color =
                                        Color(
                                            0xFF18100A
                                        ),
                                    fontWeight =
                                        FontWeight.Black,
                                    fontSize =
                                        16.sp
                                )

                                Text(
                                    "Hesap yönetimi • PRO yetkileri • Sistem durumu • Autoscale",
                                    color =
                                        Color(
                                            0xFF382614
                                        ),
                                    fontSize =
                                        12.sp,
                                    lineHeight =
                                        16.sp
                                )
                            }

                            Text(
                                "›",
                                color =
                                    Color(
                                        0xFF18100A
                                    ),
                                fontWeight =
                                    FontWeight.Black,
                                fontSize =
                                    28.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    shape = RoundedCornerShape(if (compact) 24.dp else 30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF153A58),
                                        Color(0xFF33234E),
                                        Color(0xFF4B2B22)
                                    )
                                )
                            )
                            .padding(if (compact) 18.dp else 26.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "APPFORGE • STUDIO V2",
                                color = V2Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                t("hero_title"),
                                color = V2Text,
                                fontWeight = FontWeight.Black,
                                fontSize = if (compact) 28.sp else 38.sp,
                                lineHeight = if (compact) 32.sp else 42.sp
                            )
                            Text(
                                t("hero_body"),
                                color = Color(0xFFD5DBE8),
                                fontSize = if (compact) 13.sp else 15.sp,
                                lineHeight = if (compact) 18.sp else 21.sp
                            )
                            Button(
                                onClick = onCreateQuick,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = V2Primary,
                                    contentColor = Color(0xFF041018)
                                )
                            ) {
                                Text("⚡  ${t("quick")}", fontWeight = FontWeight.Black)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = onCreateAdvanced, modifier = Modifier.weight(1f)) {
                                    Text("⌘ ${t("advanced")}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(onClick = onCreateConversion, modifier = Modifier.weight(1f)) {
                                    Text("⇄ ${t("convert")}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(Modifier.weight(1f), projects.size.toString(), t("projects"), V2Primary)
                    StatCard(Modifier.weight(1f), successfulBuilds.toString(), t("successful_builds"), V2Success)
                    StatCard(
                        Modifier.weight(1f),
                        latestBuild
                            ?.let { AppForgeBuildNumbers.label(it.buildNo) }
                            ?.takeIf { it != "AF------" }
                            ?: "—",
                        t("latest_build"),
                        V2Warm
                    )
                }
            }

            item {
                Card(
                    onClick = onOpenAi,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121C36))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(if (compact) 15.dp else 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (compact) 52.dp else 62.dp)
                                .background(
                                    Brush.linearGradient(listOf(V2Primary, V2Secondary)),
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("AI", color = Color(0xFF07101B), fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t("ai_title"), color = V2Text, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text(t("ai_body"), color = V2Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Text("›", color = V2Primary, fontSize = 28.sp)
                    }
                }
            }

            item {
                ToolCard(
                    "▦",
                    t("other_apps"),
                    onOpenOtherApps,
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 980.dp)
                )
            }

            item { V2Section(t("recent_projects"), Modifier.widthIn(max = 980.dp)) }

            if (projects.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = V2Surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(t("no_projects"), color = V2Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(t("no_projects_body"), color = V2Muted, fontSize = 13.sp)
                            Button(onClick = onCreateQuick) { Text(t("quick")) }
                        }
                    }
                }
            } else {
                items(projects.take(8), key = { it.id }) { project ->
                    ProjectCardV2(project, language) { onOpenProject(project) }
                }
            }

            item { V2Section(t("tools"), Modifier.widthIn(max = 980.dp)) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolCard("✦", t("templates"), onOpenTemplates, Modifier.weight(1f))
                        ToolCard("↺", t("history"), onOpenHistory, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolCard("◎", t("account"), onOpenAccount, Modifier.weight(1f))
                        ToolCard("⚙", t("settings"), onOpenSettings, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolCard("⌫", t("trash"), onOpenTrash, Modifier.weight(1f))
                        ToolCard("⇄", t("convert"), onCreateConversion, Modifier.weight(1f))
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = V2Surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(t("backup"), color = V2Text, fontWeight = FontWeight.Bold)
                        HorizontalDivider(color = Color(0xFF2A3047))
                        OutlinedButton(onClick = onImportProject, modifier = Modifier.fillMaxWidth()) {
                            Text("↓ ${t("import")}")
                        }
                        OutlinedButton(onClick = onExportAllProjects, modifier = Modifier.fillMaxWidth()) {
                            Text("↑ ${t("export")}")
                        }
                        OutlinedButton(onClick = onExportAllAndroidProjects, modifier = Modifier.fillMaxWidth()) {
                            Text("↑ ${t("export_android")}")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = V2Surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                value,
                color = accent,
                fontWeight = FontWeight.Black,
                fontSize = if (value.startsWith("AF-")) 13.sp else 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, color = V2Muted, fontSize = 10.sp, maxLines = 2, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun V2Section(title: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = V2Text, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.width(34.dp).height(3.dp).background(
                Brush.horizontalGradient(listOf(V2Primary, V2Secondary)),
                RoundedCornerShape(99.dp)
            )
        )
    }
}

@Composable
private fun ProjectCardV2(
    project: SavedProject,
    language: String,
    onClick: () -> Unit
) {
    val updatedLabel = CopyV2.text(language, "updated")
    val date = remember(project.updatedAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(project.updatedAt))
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = V2Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(
                    Brush.linearGradient(listOf(V2Secondary, V2Primary)),
                    RoundedCornerShape(15.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    project.name.trim().firstOrNull()?.uppercase() ?: "A",
                    color = Color(0xFF07101A),
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    color = V2Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    project.packageName,
                    color = V2Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("$updatedLabel • $date", color = Color(0xFF78839D), fontSize = 10.sp)
            }
            Text("›", color = V2Primary, fontSize = 26.sp)
        }
    }
}

@Composable
private fun ToolCard(
    icon: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = V2Surface2)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(icon, color = V2Primary, fontSize = 20.sp)
            Text(
                title,
                color = V2Text,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
