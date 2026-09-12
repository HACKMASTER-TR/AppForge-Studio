@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.appforge.studio

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
): String = StudioI18n.t(languageCode, key)

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
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val compact = configuration.screenWidthDp < 380
    val tablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val wide = configuration.screenWidthDp >= 600
    val maxWidth = if (wide) 880.dp else 10000.dp
    val horizontalPadding = when {
        compact -> 10.dp
        tablet -> 28.dp
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
            "🛒",
            "Pro ve Satın Almalar",
            "Play fiyatları, kota, ek paketler, geri yükleme ve abonelik yönetimi",
            {
                context.startActivity(
                    Intent(
                        context,
                        ProPurchasesActivity::class.java
                    )
                )
            }
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
            "Kullanım koşulları, gizlilik, bulut build, ödeme ve AI açıklamaları",
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
                    Text(
                        settingsT(languageCode, "settings"),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        settingsT(languageCode, "settings_subtitle"),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = horizontalPadding,
                vertical = if (compact) 10.dp else 16.dp
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
    val compact = LocalConfiguration.current.screenWidthDp < 380

    Card(
        onClick = entry.onClick,
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (compact) 18.dp else 22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 12.dp else 16.dp,
                    vertical = if (compact) 14.dp else 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF23344E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(if (compact) 44.dp else 52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        entry.icon,
                        fontSize = if (compact) 21.sp else 24.sp
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(
                Modifier.width(if (compact) 10.dp else 14.dp)
            )

            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 16.sp else 18.sp
                )
                Text(
                    entry.subtitle,
                    color = TextSecondary,
                    lineHeight = if (compact) 16.sp else 18.sp,
                    fontSize = if (compact) 12.sp else 14.sp
                )
            }

            Text(
                "›",
                fontSize = if (compact) 24.sp else 28.sp,
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
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp < 380
    val tablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val wide = configuration.screenWidthDp >= 600
    val maxWidth = if (wide) 880.dp else 10000.dp
    val horizontalPadding = when {
        compact -> 10.dp
        tablet -> 28.dp
        else -> 16.dp
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    settingsT(languageCode, "choose_language"),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = horizontalPadding,
                vertical = if (compact) 10.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(StudioI18n.languages) { lang ->
                Card(
                    onClick = { onSelect(lang.code) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (lang.code == languageCode) {
                            Color(0xFF1B3158)
                        } else {
                            Card2
                        }
                    ),
                    shape = RoundedCornerShape(if (compact) 17.dp else 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.nativeLabel, fontWeight = FontWeight.Bold)
                            Text(
                                lang.englishLabel,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
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
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp < 380
    val tablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val wide = configuration.screenWidthDp >= 600
    val maxWidth = if (wide) 860.dp else 10000.dp
    val horizontalPadding = when {
        compact -> 10.dp
        tablet -> 28.dp
        else -> 16.dp
    }

    val sections = listOf(
        Triple(
            "📄",
            settingsT(languageCode, "terms_of_use"),
            "AppForge ile dönüştürdüğün veya derlediğin kaynak, içerik, marka, izin ve dağıtım haklarından sen sorumlusun. Yalnız sana ait olan veya kullanma hakkın bulunan içerikleri işle. AppForge'ın güvenlik, kota, imzalama ve mağaza doğrulamalarını aşmaya çalışma."
        ),
        Triple(
            "☁️",
            "Bulut build ve proje verileri",
            "Build Service kullandığında gerekli proje kaynakları, build ayarları ve teknik metadata resmi AppForge sunucusuna gönderilebilir. Build logları ve çıktılar hizmetin çalışma ve saklama politikası kapsamında tutulabilir. Yerel araçlar yalnız cihazda çalıştıkları ölçüde buluta veri göndermez; kullanılan özelliğe göre davranış değişir."
        ),
        Triple(
            "🔐",
            settingsT(languageCode, "privacy_policy"),
            "Hesap, oturum, cihaz güvenliği, build geçmişi, kota ve satın alma doğrulaması için gerekli teknik veriler işlenebilir. Parolalar, API anahtarları ve keystore parolaları Yerel AI bağlamına eklenmez. Android istemcisindeki hassas hesap bağlantıları güvenli depoda tutulur. Hesap silme akışı sunucudaki hesap verilerinin silinmesini başlatır; üçüncü tarafların kendi saklama yükümlülükleri ayrıca geçerli olabilir."
        ),
        Triple(
            "🛒",
            "Google Play ödemeleri ve abonelikler",
            "Ödemeyi Google Play işler. AppForge sunucusu purchase token, ürün, abonelik durumu ve entitlement bilgisini Google Play ile doğrular. İptal, süre dolumu, refund veya revoke sonrası Pro erişimi sunucu gerçeğine göre kaldırılabilir. Ek kota paketleri yalnız uygun aktif Pro Aylık döneminde geçerlidir ve sonraki döneme devretmez."
        ),
        Triple(
            "✨",
            "Yerel AI ve otomasyon",
            "Yerel AI, desteklenen cihazlarda LiteRT-LM ile cihaz üzerinde çalışır ve AppForge'ın güvenli proje/runtime özetini kullanabilir. Gizli anahtarlar bu özete eklenmez. Yerel model internetteki güncel Play kurallarını veya dış servis durumunu kendiliğinden bilemez; emin olmadığı AppForge özelliğini uydurmaması gerekir."
        ),
        Triple(
            "🔌",
            "Üçüncü taraf hizmetleri ve açık kaynak",
            "Google Play, Firebase, AdMob, GitHub, Railway ve benzeri entegrasyonlar yalnız kullandığın özelliğe göre devreye girebilir ve kendi şartlarına tabidir. AppForge içinde kullanılan açık kaynak bileşenlerin lisans koşulları dağıtım ve kullanım sırasında geçerliliğini korur."
        )
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    settingsT(languageCode, "legal_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        LazyColumn(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = horizontalPadding,
                vertical = if (compact) 10.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 14.dp)
        ) {
            items(sections) { section ->
                LegalInfoCard(
                    icon = section.first,
                    title = section.second,
                    body = section.third
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
    val compact = LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (compact) 19.dp else 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 13.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "$icon  $title",
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 18.sp else 22.sp
            )
            Text(
                body,
                color = TextSecondary,
                lineHeight = if (compact) 19.sp else 21.sp,
                fontSize = if (compact) 13.sp else 14.sp
            )
        }
    }
}
