@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.appforge.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
internal fun HowToUseCenterScreen(
    languageCode: String,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "how_to_title"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                NoteCard("HTML to APK Builder ile web sitenizi veya HTML dosyanızı gerçek bir Android uygulamasına dönüştürebilirsiniz. Kodlama bilmenize gerek yok.")
            }
            item { ExpandableGuideCard("➕", "Proje Oluşturun", "Açmak için dokunun", "Yeni bir proje oluşturun veya Hızlı Oluştur modunu seçin. Uygulama adını belirleyin ve varsayılan paket adı otomatik oluşsun.") }
            item { ExpandableGuideCard("☁", "İçeriğinizi Ekleyin", "Açmak için dokunun", "Yerel HTML/ZIP veya HTTPS web sitesi kullanın. Hızlı mod güvenli varsayılanları uygular.") }
            item { ExpandableGuideCard("⚙", "Ayarları Yapılandırın", "Açmak için dokunun", "İzinler, Native Bridge, tema, AdMob, Billing ve Firebase gibi seçenekleri gelişmiş modda ayarlayın.") }
            item { ExpandableGuideCard("🖼", "Bir İkon Seçin", "Açmak için dokunun", "PNG ikon yükleyin. İsterseniz AppForge varsayılan ikonu kullanılabilir.") }
            item { ExpandableGuideCard("🔧", "Oluşturun ve İndirin", "Açmak için dokunun", "Build Service'e gönderin, canlı build loglarını izleyin ve APK/AAB çıktısını alın.") }
            item { ExpandableGuideCard("✎", "Yayınlayın (İsteğe Bağlı)", "Açmak için dokunun", "Play Store'da yayınlayacaksanız imzalama anahtarı, mağaza listesi ve gizlilik politikası hazırlayın.") }
            item { ExpandableGuideCard("🔐", "Play Store imzalama anahtarı seçimi", "Açmak için dokunun", "Release güncellemelerinde aynı keystore'u kullanmalısınız. Keystore Yöneticisi üzerinden parmak izlerini ve yedekleri kontrol edin.") }
        }
    }
}

@Composable
private fun ExpandableGuideCard(
    icon: String,
    title: String,
    subtitle: String,
    body: String
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp)
                }
                Text(if (expanded) "⌃" else "⌄", fontSize = 22.sp)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Text(body, color = TextSecondary, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
internal fun PlayPublishingGuideScreen(
    languageCode: String,
    onBack: () -> Unit
) {
    val playGuideConfiguration =
        LocalConfiguration.current

    val playGuideScreenWidthDp =
        playGuideConfiguration.screenWidthDp

    val playGuideScreenHeightDp =
        playGuideConfiguration.screenHeightDp

    val playGuideCompact =
        playGuideScreenWidthDp < 380

    val playGuideTablet =
        minOf(
            playGuideScreenWidthDp,
            playGuideScreenHeightDp
        ) >= 600

    val playGuideWide =
        playGuideScreenWidthDp >= 600

    val playGuideContentMaxWidth =
        if (playGuideWide) 860.dp else 10000.dp

    val playGuideHorizontalPadding =
        when {
            playGuideCompact -> 10.dp
            playGuideTablet -> 28.dp
            else -> 16.dp
        }

    val steps = listOf(
        "1. Google Play Console Hesabı Oluşturun" to "Google Play Console web sitesini ziyaret edin ve tek seferlik kayıt ücretini ödeyin.",
        "2. APK'nızı / AAB'nizi Oluşturun" to "Bu uygulamada HTML içeriğinizle proje oluşturun, paket adını ayarlayın ve üretim imzası için keystore hazırlayın.",
        "3. Keystore'unuzu Yedekleyin" to "Keystore Yöneticisi'nde imza anahtarınızı güvenli bir yerde saklayın.",
        "4. Uygulama Listesi Oluşturun" to "Uygulama adı, kategori, kısa açıklama ve tam açıklamayı hazırlayın.",
        "5. İçerik Derecelendirmesi" to "İçerik derecelendirmesi anketini tamamlayın.",
        "6. Gizlilik Politikası" to "Uygulamanız internet veya kullanıcı verisi kullanıyorsa bir gizlilik politikası URL'si ekleyin.",
        "7. AAB'yi Yükleyin" to "Üretim > Sürümler > Üretim oluştur bölümüne imzalı paketinizi yükleyin.",
        "8. Yayınlayın" to "Tüm form ve işaretleri tamamladıktan sonra yayını gönderin.",
        "9. Güncellemeler" to "Yeni sürümlerde versionCode'u artırın ve aynı keystore ile tekrar imzalayın.",
        "10. Keystore'unuzu mu Kaybettiniz?" to "Play App Signing etkinse upload key reset sürecini kullanın; değilse eski imza olmadan güncelleme yapamazsınız.",
        "11. Bu Uygulamayı Yeniden mi Yüklediniz?" to "Yerel keystore ve proje dosyalarınızı geri aktarın, ardından mevcut packageName ile derleyin."
    )
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(t(languageCode, "play_publish_title"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )
        LazyColumn(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = playGuideContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    horizontal = playGuideHorizontalPadding,
                    vertical =
                        if (playGuideCompact) 10.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (playGuideCompact) 9.dp else 14.dp)
        ) {
            items(steps) { step ->
                GuideStepCard(step.first, step.second)
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    title: String,
    body: String
) {
    val guideCardCompact =
        LocalConfiguration.current.screenWidthDp < 380

    Card(
        colors = CardDefaults.cardColors(containerColor = Card2),
        shape = RoundedCornerShape(if (guideCardCompact) 18.dp else 22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (guideCardCompact) 13.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = if (guideCardCompact) 17.sp else 20.sp
            )
            Text(
                body,
                color = TextSecondary,
                lineHeight = if (guideCardCompact) 18.sp else 21.sp,
                fontSize = if (guideCardCompact) 13.sp else 14.sp
            )
        }
    }
}
