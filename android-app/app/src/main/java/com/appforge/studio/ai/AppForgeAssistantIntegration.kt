package com.appforge.studio.ai

import java.util.Locale

enum class AssistantDestination {
    PROJECTS,
    QUICK_CREATE,
    ADVANCED_CREATE,
    CONVERSION,
    SOURCE,
    PERMISSIONS,
    FEATURES,
    APPEARANCE,
    NATIVE_BRIDGE,
    MONETIZATION,
    DEEP_LINK,
    SIGNING,
    BUILD_SETTINGS,
    BUILD,
    PREVIEW,
    PRODUCTION,
    TEST_LAB,
    TEMPLATES,
    HISTORY,
    TRASH,
    SETTINGS,
    ACCOUNT,
    HELP,
    PLAY_GUIDE,
    PRO,
    KEYSTORES
}

data class AssistantAppAction(
    val destination: AssistantDestination,
    val label: String,
    val description: String
)

data class AssistantQuickGuidance(
    val answer: String,
    val actions: List<AssistantAppAction>
)

data class AssistantRuntimeContext(
    val workspace: String,
    val builderStep: Int,
    val buildStatus: String,
    val buildProgress: Int,
    val sourceTechnology: String,
    val sourceBuildReady: Boolean,
    val signedIn: Boolean,
    val proActive: Boolean,
    val hasApk: Boolean,
    val hasAab: Boolean,
    val hasExe: Boolean,
    val buildDiagnosis: String? = null
)

object AppForgeAssistantIntegration {

    private data class FeatureRoute(
        val action: AssistantAppAction,
        val keywords: Set<String>,
        val handbookLine: String
    )

    private val routes =
        listOf(
            FeatureRoute(
                AssistantAppAction(AssistantDestination.PROJECTS, "Projeleri aç", "Kayıtlı projeleri görüntüle ve devam et."),
                setOf("proje", "projeler", "kaydet", "yükle", "kütüphane"),
                "Projeler: yerel proje listesi, güvenli kaydetme, geri yükleme ve proje silme."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.QUICK_CREATE, "Hızlı oluştur", "En az ayarla yeni uygulama başlat."),
                setOf("hızlı", "quick", "kolay", "yeni uygulama", "oluştur"),
                "Hızlı Oluştur: ad, kaynak ve ikonla güvenli varsayılanları otomatik kurar."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.ADVANCED_CREATE, "Gelişmiş düzenleyici", "Tüm proje ayarlarını adım adım düzenle."),
                setOf("gelişmiş", "advanced", "düzenle", "builder", "ayar"),
                "Gelişmiş Düzenleyici: kaynak, izin, WebView, görünüm, bridge, gelir, deep link, imza ve build adımları."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.CONVERSION, "Dönüşümü aç", "APK ve EXE dönüşüm araçlarını kullan."),
                setOf("dönüşüm", "çevir", "apk exe", "exe apk", "conversion"),
                "Dönüşüm: yalnız AppForge manifesti taşıyan APK/EXE çıktıları arasında güvenli dönüşüm yapar."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.SOURCE, "Kaynağı aç", "HTML, ZIP, URL veya kaynak proje seç."),
                setOf("kaynak", "html", "zip", "url", "flutter", "react native", "python", "unity", "maui"),
                "Kaynak: HTML/ZIP/URL ile desteklenen Android, Flutter, React Native, Python, C++, .NET ve Unity proje algılama."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.PERMISSIONS, "İzinleri aç", "Kamera, mikrofon, konum, bildirim, ağ, uyanık tutma ve NFC izinlerini yönet."),
                setOf("izin", "kamera", "mikrofon", "konum", "bildirim", "ağ", "nfc", "wake lock", "dosya", "permission"),
                "İzinler: kamera, mikrofon, konum, bildirim, ağ durumu, WAKE_LOCK, NFC, yükleme ve indirme yeteneklerini güvenli biçimde açar."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.FEATURES, "WebView ayarları", "Web, çevrimdışı ve görüntüleme seçeneklerini düzenle."),
                setOf("webview", "javascript", "önbellek", "cache", "zoom", "mixed content"),
                "WebView: JavaScript, DOM storage, zoom, viewport, medya, offline cache ve güvenli mixed-content ayarları."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.APPEARANCE, "Görünümü düzenle", "Tema, renk, ikon ve splash ayarlarını aç."),
                setOf("görünüm", "tema", "renk", "ikon", "splash", "tasarım"),
                "Görünüm: ikon, splash, yön, ana/arka plan ve sistem çubuğu renkleri."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.NATIVE_BRIDGE, "Native Bridge'i aç", "Web içeriği ile güvenli cihaz özelliklerini bağla."),
                setOf("native bridge", "bridge", "paylaş", "titreşim", "qr", "media3", "clipboard"),
                "Native Bridge: origin-kısıtlı paylaşım, titreşim, QR ve Media3 köprüsü; hassas eski bridge kapalıdır."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.MONETIZATION, "Gelir ve Firebase", "AdMob, Billing, UMP ve Firebase'i yapılandır."),
                setOf("admob", "reklam", "billing", "satın alma", "firebase", "crashlytics", "analytics", "fcm"),
                "Gelir/Firebase: AdMob, UMP, Play Billing, Analytics, Crashlytics ve Messaging yapılandırması."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.DEEP_LINK, "Deep Link'i aç", "Uygulama bağlantılarını yapılandır."),
                setOf("deep link", "deeplink", "app link", "scheme", "host"),
                "Deep Link: scheme, host ve yol önekiyle uygulama bağlantısı tanımlar."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.SIGNING, "İmzalamayı aç", "Debug veya release keystore ayarlarını kontrol et."),
                setOf("imza", "imzalama", "keystore", "jks", "alias", "release"),
                "İmzalama: debug veya Pro ile yönetilen özel JKS/keystore; parolalar proje/AI bağlamına yazılmaz."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.BUILD_SETTINGS, "Build ayarları", "Sürüm, çıktı ve servis ayarlarını düzenle."),
                setOf("versioncode", "versionname", "sürüm", "apk aab", "çıktı", "build ayarı"),
                "Build Ayarları: versionName/versionCode, APK/AAB/BOTH, proje kaydı ve Build Service bağlantısı."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.BUILD, "Derleme ekranı", "Ön kontrolü ve canlı build durumunu gör."),
                setOf("build", "derle", "hata", "başarısız", "log", "apk", "aab"),
                "Derleme: ön kontrol, kuyruk/worker durumu, güvenli log, yeniden deneme ve APK/AAB/EXE indirme."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.PREVIEW, "Önizlemeyi aç", "Telefon/tablet görünümünü ve inspector'ı kullan."),
                setOf("preview", "önizleme", "cihaz", "konsol", "network", "performans"),
                "Önizleme: cihaz boyutları, konsol, ağ, performans ve güvenlik inspector araçları."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.PRODUCTION, "Production Center", "Yayın kontrolü, yedek ve sürüm araçlarını aç."),
                setOf("production", "yayın", "hazır", "yedek", "backup", "release note"),
                "Production Center: yayın hazırlık kontrolleri, sürüm artırma, yedek, compare ve release-note araçları."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.TEST_LAB, "Test Lab'i aç", "Build geçmişini ve artifact raporlarını incele."),
                setOf("test lab", "test", "artifact", "karşılaştır", "compare"),
                "Test Lab: artifact analizi, iki build karşılaştırması ve hassas verileri ayıklanmış raporlar."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.TEMPLATES, "Şablonları aç", "Hazır başlangıç projelerinden birini seç."),
                setOf("şablon", "template", "örnek", "başlangıç"),
                "Şablonlar: kategorili hazır HTML projelerini doğrudan yeni projeye uygular."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.HISTORY, "Geçmişi aç", "Önceki build ve çıktıları görüntüle."),
                setOf("geçmiş", "history", "önceki build", "eski çıktı"),
                "Geçmiş: önceki build durumları ve indirilebilir çıktılar."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.TRASH, "Geri dönüşüm kutusu", "Son 30 günde silinen projeleri gör veya geri yükle."),
                setOf("geri dönüşüm", "çöp", "silinen proje", "geri yükle", "30 gün"),
                "Geri Dönüşüm Kutusu: silinen projeleri 30 gün saklar, geri yükler ve süresi dolunca proje dosyalarıyla birlikte temizler."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.ACCOUNT, "Hesabı aç", "Oturum ve Build Service bağlantısını yönet."),
                setOf("hesap", "giriş", "oturum", "api key", "kayıt"),
                "Hesap: güvenli oturum ve Build Service erişimi; anahtarlar Android güvenli deposunda tutulur."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.HELP, "Yardımı aç", "Kullanım rehberlerini ve aranabilir yardım konularını gör."),
                setOf("yardım", "nasıl kullanılır", "rehber", "destek", "sık soru"),
                "Yardım Merkezi: kategorili ve aranabilir AppForge kullanım makaleleri."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.PLAY_GUIDE, "Play rehberini aç", "AAB yayın adımlarını kontrol et."),
                setOf("play store", "google play", "yayınla", "aab", "production track"),
                "Play Rehberi: AAB, benzersiz package, artan versionCode, imza, politika ve test kanalı kontrol listesi."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.PRO, "Pro seçenekleri", "Doğrulanmış Pro ve Pro Aylık planlarını gör."),
                setOf("pro", "ücret", "abonelik", "filigran", "sınırsız"),
                "Pro: sunucu ve Play Integrity ile doğrulanan sınırsız proje/filigransız build yetkisi."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.KEYSTORES, "Keystore yöneticisi", "İmza kayıtlarını ve sertifika parmak izlerini yönet."),
                setOf("keystore yönet", "sertifika", "sha1", "sha256", "parmak izi", "jks"),
                "Keystore Yöneticisi: güvenli JKS kayıtları, alias bilgisi ve sertifika parmak izleri."
            ),
            FeatureRoute(
                AssistantAppAction(AssistantDestination.SETTINGS, "Ayarları aç", "Dil, güvenlik, yardım ve uygulama ayarlarını yönet."),
                setOf("ayarlar", "dil", "önbellek", "gizlilik", "yardım"),
                "Ayarlar: dil, keystore, Pro, yardım, Play rehberi, yasal bilgiler, geri bildirim ve cache temizliği."
            )
        )

    fun applicationMap(): String =
        buildString {
            appendLine("APPFORGE UYGULAMA HARİTASI:")
            routes.forEach { appendLine("- ${it.handbookLine}") }
            appendLine("- Yerel AI: LiteRT-LM ile cihazda çalışır; uygulama haritası, proje ve güvenli çalışma durumu bağlamını kullanır.")
            appendLine("- Güvenlik: API anahtarı, oturum belirteci, keystore ve parolalar AI bağlamına eklenmez.")
            appendLine("- Sınırlar: güncel internet/Play kuralı doğrulanamaz; SSR-only web, eksik native hedef ve eksik worker/lisans koşulları otomatik olarak varmış gibi gösterilmez.")
        }.trim()

    fun runtimeSummary(context: AssistantRuntimeContext): String =
        buildString {
            appendLine("UYGULAMANIN ANLIK DURUMU:")
            appendLine("- Gelinen çalışma alanı: ${context.workspace}; builder adımı: ${context.builderStep}/10")
            appendLine("- Build durumu: ${context.buildStatus}; ilerleme: %${context.buildProgress}")
            appendLine("- Kaynak teknolojisi: ${context.sourceTechnology}; build-ready: ${context.sourceBuildReady}")
            appendLine("- Oturum açık: ${context.signedIn}; Pro aktif: ${context.proActive}")
            appendLine("- Hazır çıktılar: APK=${context.hasApk}, AAB=${context.hasAab}, EXE=${context.hasExe}")
            context.buildDiagnosis?.takeIf { it.isNotBlank() }?.let {
                appendLine("- Güvenli build tanısı: $it")
            }
        }.trim()

    fun actionsFor(question: String, limit: Int = 3): List<AssistantAppAction> {
        val normalized = normalize(question)
        val tokens = normalized.split(" ").filter { it.length >= 3 }.toSet()

        return routes
            .map { route ->
                val score = route.keywords.sumOf { keyword ->
                    val key = normalize(keyword)
                    when {
                        normalized.contains(key) -> 6
                        tokens.any { it in key || key in it } -> 2
                        else -> 0
                    }
                }
                route to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .distinctBy { it.first.action.destination }
            .take(limit)
            .map { it.first.action }
    }

    fun quickGuidance(question: String): AssistantQuickGuidance? {
        val normalized = normalize(question)
        val navigationIntent =
            listOf(
                "aç",
                "götür",
                "yönlendir",
                "nerede",
                "nereden",
                "hangi adım",
                "nasıl giderim",
                "nasıl açarım"
            ).any { normalized.contains(it) }

        if (!navigationIntent) return null

        val actions = actionsFor(question, limit = 2)
        val primary = actions.firstOrNull() ?: return null

        return AssistantQuickGuidance(
            answer =
                "${primary.label} seçeneği doğru bölüm. " +
                    "Aşağıdaki kısayola dokunarak doğrudan açabilirsin. " +
                    primary.description,
            actions = actions
        )
    }

    fun diagnosisAnswer(diagnosis: BuildErrorDiagnosis): String =
        buildString {
            appendLine("${diagnosis.title} (${diagnosis.confidence}% güven)")
            appendLine()
            appendLine("Neden: ${diagnosis.reason}")
            appendLine()
            appendLine("Çözüm: ${diagnosis.solution}")
            diagnosis.evidence?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Güvenli log ipucu: $it")
            }
        }.trim()

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
