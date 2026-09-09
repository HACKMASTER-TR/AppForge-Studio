@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.appforge.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.i18n.StudioI18n

private fun settingsT(
    languageCode: String,
    key: String
): String =
    StudioI18n.t(
        languageCode,
        key
    )


private data class SettingsEntry(
    val icon: String,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
internal fun SettingsHubScreen(
    languageCode: String,
    proUnlocked: Boolean,
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenKeystore: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenHowTo: () -> Unit,
    onOpenPlayGuide: () -> Unit,
    onOpenLegal: () -> Unit,
    onFeedback: () -> Unit,
    onClearCache: () -> Unit
) {
    val settingsConfiguration =
        LocalConfiguration.current

    val settingsScreenWidthDp =
        settingsConfiguration.screenWidthDp

    val settingsScreenHeightDp =
        settingsConfiguration.screenHeightDp

    val settingsCompact =
        settingsScreenWidthDp < 380

    val settingsTablet =
        minOf(
            settingsScreenWidthDp,
            settingsScreenHeightDp
        ) >= 600

    val settingsWide =
        settingsScreenWidthDp >= 600

    val settingsContentMaxWidth =
        if (settingsWide) 880.dp else 10000.dp

    val settingsHorizontalPadding =
        when {
            settingsCompact -> 10.dp
            settingsTablet -> 28.dp
            else -> 16.dp
        }

    val entries = listOf(
        SettingsEntry(
            "🌐",
            settingsT(languageCode, "language"),
            settingsT(languageCode, "system_default"),
            onOpenLanguage
        ),
        SettingsEntry(
            "🔐",
            settingsT(languageCode, "keystore_manager"),
            "JKS / keystore kasası ve parmak izleri",
            onOpenKeystore
        ),
        SettingsEntry(
            "★",
            settingsT(languageCode, "pro"),
            if (proUnlocked) settingsT(languageCode, "active") else "Standart",
            onOpenPro
        ),
        SettingsEntry(
            "❓",
            settingsT(languageCode, "how_to_use"),
            "AppForge, Terminal, Excel Tools ve VideoForge kullanım rehberi",
            onOpenHowTo
        ),
        SettingsEntry(
            "ⓘ",
            settingsT(languageCode, "play_guide"),
            "Google Play yayınlama adımları",
            onOpenPlayGuide
        ),
        SettingsEntry(
            "🛡",
            settingsT(languageCode, "legal"),
            "Kullanım koşulları ve gizlilik",
            onOpenLegal
        ),
        SettingsEntry(
            "✉",
            settingsT(languageCode, "send_feedback"),
            "28550040284a@gmail.com",
            onFeedback
        ),
        SettingsEntry(
            "🗑",
            settingsT(languageCode, "clear_cache"),
            "Geçici build dosyalarını ve önbellekleri temizler",
            onClearCache
        )
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(settingsT(languageCode, "settings"), fontWeight = FontWeight.Bold)
                    Text(
                        settingsT(languageCode, "settings_subtitle"),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(
                        max = settingsContentMaxWidth
                    )
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = settingsHorizontalPadding,
                    vertical =
                        if (settingsCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries) { item ->
                SettingsCardRow(item)
            }
        }
    }
}

@Composable
private fun SettingsCardRow(entry: SettingsEntry) {
    val settingsCardCompact =
        LocalConfiguration.current
            .screenWidthDp < 380

    Card(
        onClick = entry.onClick,
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (settingsCardCompact) 18.dp else 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (settingsCardCompact) 12.dp else 16.dp,
                vertical = if (settingsCardCompact) 14.dp else 18.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF23344E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(if (settingsCardCompact) 44.dp else 52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                    entry.icon,
                    fontSize = if (settingsCardCompact) 21.sp else 24.sp
                )
                }
            }

            Spacer(Modifier.width(if (settingsCardCompact) 10.dp else 14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (settingsCardCompact) 16.sp else 18.sp
                )
                Text(
                    entry.subtitle,
                    color = TextSecondary,
                    lineHeight = if (settingsCardCompact) 16.sp else 18.sp,
                    fontSize = if (settingsCardCompact) 12.sp else 14.sp
                )
            }

            Text(
                "›",
                fontSize = if (settingsCardCompact) 24.sp else 28.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
internal fun LanguageSettingsScreen(
    languageCode: String,
    onBack: () -> Unit,
    onSelect: (String) -> Unit
) {
    val languageConfiguration =
        LocalConfiguration.current

    val languageScreenWidthDp =
        languageConfiguration.screenWidthDp

    val languageScreenHeightDp =
        languageConfiguration.screenHeightDp

    val languageCompact =
        languageScreenWidthDp < 380

    val languageTablet =
        minOf(
            languageScreenWidthDp,
            languageScreenHeightDp
        ) >= 600

    val languageWide =
        languageScreenWidthDp >= 600

    val languageContentMaxWidth =
        if (languageWide) 880.dp else 10000.dp

    val languageHorizontalPadding =
        when {
            languageCompact -> 10.dp
            languageTablet -> 28.dp
            else -> 16.dp
        }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(settingsT(languageCode, "choose_language"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(
                        max = languageContentMaxWidth
                    )
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = languageHorizontalPadding,
                    vertical =
                        if (languageCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(StudioI18n.languages) { lang ->
                Card(
                    onClick = { onSelect(lang.code) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (lang.code == languageCode) Color(0xFF1B3158) else Card2
                    ),
                    shape = RoundedCornerShape(if (languageCompact) 17.dp else 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.nativeLabel, fontWeight = FontWeight.Bold)
                            Text(lang.englishLabel, color = TextSecondary, fontSize = 12.sp)
                        }
                        RadioButton(
                            selected = lang.code == languageCode,
                            onClick = { onSelect(lang.code) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LegalCenterScreen(
    languageCode: String,
    onBack: () -> Unit
) {
    val legalConfiguration =
        LocalConfiguration.current

    val legalScreenWidthDp =
        legalConfiguration.screenWidthDp

    val legalScreenHeightDp =
        legalConfiguration.screenHeightDp

    val legalCompact =
        legalScreenWidthDp < 380

    val legalTablet =
        minOf(
            legalScreenWidthDp,
            legalScreenHeightDp
        ) >= 600

    val legalWide =
        legalScreenWidthDp >= 600

    val legalContentMaxWidth =
        if (legalWide) 860.dp else 10000.dp

    val legalHorizontalPadding =
        when {
            legalCompact -> 10.dp
            legalTablet -> 28.dp
            else -> 16.dp
        }

    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(settingsT(languageCode, "legal_title"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = legalContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = legalHorizontalPadding,
                    vertical =
                        if (legalCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (legalCompact) 9.dp else 14.dp)
        ) {
            item {
                LegalInfoCard(
                    icon = "📄",
                    title = settingsT(languageCode, "terms_of_use"),
                    body = "1. APK'ya dönüştürdüğünüz içerikten yalnız siz sorumlusunuz.\n2. Yalnızca size ait olan veya kullanım izni aldığınız içerikleri dönüştürün.\n3. Dönüştürülen APK'lar sunucularımızda saklanmaz veya dağıtılmaz.\n4. Tüm APK oluşturma işlemi cihazınızda yerel olarak gerçekleşir.\n5. Uygulamanın kötüye kullanımından sorumlu değiliz."
                )
            }
            item {
                LegalInfoCard(
                    icon = "🛡",
                    title = settingsT(languageCode, "privacy_policy"),
                    body = "1. Kişisel veri toplamıyoruz.\n2. Tüm proje verileri cihazınızda yerel olarak saklanır.\n3. İnternet izni yalnız sizin isteğiniz üzerine URL içeriği getirmek veya Build Service ile iletişim kurmak için kullanılır.\n4. Verilerinizi üçüncü taraflarla paylaşmayız."
                )
            }
        }
    }
}

@Composable
internal fun LegalInfoCard(
    icon: String,
    title: String,
    body: String
) {
    val legalCardCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (legalCardCompact) 19.dp else 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (legalCardCompact) 13.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "$icon  $title",
                fontWeight = FontWeight.Bold,
                fontSize = if (legalCardCompact) 18.sp else 22.sp
            )
            Text(
                body,
                color = TextSecondary,
                lineHeight = if (legalCardCompact) 19.sp else 21.sp,
                fontSize = if (legalCardCompact) 13.sp else 14.sp
            )
        }
    }
}
