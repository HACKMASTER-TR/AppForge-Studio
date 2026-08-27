package com.appforge.studio.ai

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import java.util.Locale

data class KnowledgeChunk(
    val title: String,
    val text: String,
    val keywords: Set<String>
)


data class HelpArticle(
    val category: String,
    val title: String,
    val text: String,
    val keywords: Set<String>
)

private data class FastFaq(
    val question: String,
    val answer: String,
    val keywords: Set<String>,
    val aliases: Set<String> = emptySet()
)

object AppForgeKnowledgeBase {

    private const val LANGUAGE_SUPPORT_ANSWER =
        "AppForge HTML5, CSS3 ve JavaScript projelerini; HTTPS web sitelerini; " +
            "React, Vue, Angular, Svelte, Vite ve npm/TypeScript web projelerini destekler. " +
            "Next.js için static export, Nuxt için static generate yapılandırması gerekir. " +
            "Android Kotlin/Java Gradle, Flutter/Dart, React Native, Expo managed, " +
            "Python/Flask/Django, appforge_main girişli C/C++ ve net10.0-android hedefli " +
            ".NET MAUI veya .NET Android kaynak projeleri doğrudan Android build motorlarına sahiptir. " +
            "Node.js ve PHP backend projeleri Android içinde çalıştırılmaz; appforge.remote.json ile " +
            "önceden yayınlanmış public HTTPS backend kullanılır. Unity build yolu yalnız lisanslı " +
            "dedicated Unity Worker kurulduğunda açılır. WPF/WinForms, SSR-only Next.js/Nuxt ve " +
            "gerekli giriş kontratı bulunmayan projeler build-ready değildir. Kaynak proje motorları " +
            "AppForge Native Bridge, AdMob, Billing ve Firebase seçeneklerini otomatik enjekte etmez; " +
            "bu projeler kendi native paketlerini kullanmalıdır."

    /*
     * AppForge Studio'nun cihaz içi bilgi tabanı.
     *
     * Buradaki bilgiler:
     * - hızlı FAQ yanıtları,
     * - yerel LLM grounding,
     * - proje bağlamı
     *
     * için kullanılır. Hızlı FAQ eşleşirse LLM hiç çalıştırılmaz.
     */
    private val chunks =
        listOf(
            KnowledgeChunk(
                "AppForge Studio nedir?",
                "AppForge Studio; HTML, ZIP veya web projelerini Android uygulamasına dönüştürmek, APK/AAB üretmek, önizlemek, imzalamak, test etmek ve proje ayarlarını yönetmek için kullanılan bir mobil geliştirme aracıdır.",
                setOf("appforge", "studio", "nedir", "html", "zip", "apk", "aab")
            ),
            KnowledgeChunk(
                "Hızlı Oluştur",
                "Hızlı Oluştur; uygulama adı, içerik ve isteğe bağlı ikonla hızlı proje başlatır. Paket adı, temel SDK/WebView ayarları, güvenli izinler, splash ve APK/AAB hazırlıklarının büyük bölümü AppForge tarafından otomatik düzenlenir.",
                setOf("hızlı", "oluştur", "quick", "otomatik", "paket", "splash")
            ),
            KnowledgeChunk(
                "Gelişmiş oluşturma",
                "Gelişmiş/Production akışı Kaynak, İzinler, WebView, Görünüm, Native Bridge, Para Kazanma ve Firebase, Deep Link, İmzalama, Özet/Build ve Derleme adımlarından oluşur.",
                setOf("gelişmiş", "production", "adım", "kaynak", "izin", "webview", "imzalama", "derleme")
            ),
            KnowledgeChunk(
                "HTML ve ZIP kaynağı",
                "Yerel HTML veya ZIP seçildiğinde AppForge başlangıç sayfasını ve kaynak içeriği analiz eder. HTML/ZIP içeriğine göre ihtiyaç duyulan bazı izin ve WebView seçenekleri otomatik önerilebilir.",
                setOf("html", "zip", "yerel", "kaynak", "index", "analiz")
            ),
            KnowledgeChunk(
                "Web sitesi kaynağı",
                "Web URL modu bir web adresini Android WebView tabanlı projede açmak için kullanılır. Uzak içerikte Native Bridge kullanılıyorsa yalnız güvenilir HTTPS originlerine izin verilmelidir.",
                setOf("web", "url", "site", "webview", "uzak", "https")
            ),
            KnowledgeChunk(
                "Paket adı",
                "Android package name uygulamanın benzersiz kimliğidir. Genellikle com.sirket.uygulama biçimindedir. Play Store'a çıkan uygulamanın package name değeri sonradan başka bir uygulamayla değiştirilemez.",
                setOf("package", "paket", "adı", "kimlik", "com")
            ),
            KnowledgeChunk(
                "Otomatik paket adı",
                "AppForge uygulama adından geçerli bir package name üretebilir. Yayınlamadan önce package name değerinin benzersiz ve kalıcı olarak kullanılmaya uygun olduğundan emin olunmalıdır.",
                setOf("otomatik", "package", "paket", "ad", "isim")
            ),
            KnowledgeChunk(
                "Android izinleri",
                "Kamera, konum ve bildirim gibi izinler ihtiyaç olduğunda etkinleştirilebilir. HTML/ZIP analizi bazı izinleri önerebilir. Gereksiz izinleri açmamak Play Store politikaları ve kullanıcı güveni açısından daha iyidir.",
                setOf("izin", "kamera", "konum", "bildirim", "permission")
            ),
            KnowledgeChunk(
                "WebView ayarları",
                "WebView bölümünde JavaScript, DOM Storage, pinch zoom, geniş viewport, overview mode, medya autoplay ve mixed content gibi seçenekler yönetilir. Önerilen güvenli varsayılanlarla başlamak uygundur.",
                setOf("webview", "javascript", "dom", "zoom", "viewport", "autoplay", "mixed")
            ),
            KnowledgeChunk(
                "Mixed Content",
                "Mixed Content kapalıyken HTTPS sayfanın HTTP alt kaynakları engellenir. Yalnız gerçekten gerekli ve güvenilir eski içeriklerde uyumluluk amacıyla açılmalıdır.",
                setOf("mixed", "content", "http", "https", "uyumluluk")
            ),
            KnowledgeChunk(
                "Görünüm",
                "Görünüm adımında uygulama ikonu, ekran yönü, koyu/açık/OLED tema, ana renk, arka plan, status bar, navigation bar ve Android 12+ splash davranışı ayarlanabilir.",
                setOf("görünüm", "ikon", "tema", "oled", "renk", "splash", "yön")
            ),
            KnowledgeChunk(
                "Uygulama ikonu",
                "Özel uygulama ikonu için kare PNG kullanılması önerilir. Play Store mağaza simgesi ile Android uygulama içindeki launcher ikonunun gereksinimleri farklı olabilir.",
                setOf("ikon", "simge", "png", "launcher", "play")
            ),
            KnowledgeChunk(
                "Native Bridge",
                "Native Bridge web içeriğinin AppForge Android API'lerine kontrollü biçimde erişmesini sağlar. Paylaşım, pano, titreşim/haptic, QR/barkod ve desteklenen diğer native özellikler bu katman üzerinden kullanılabilir.",
                setOf("native", "bridge", "android", "api", "paylaşım", "pano", "titreşim")
            ),
            KnowledgeChunk(
                "Remote Native Bridge",
                "Remote Native Bridge uzak web içeriğine native yetenek açtığı için yalnız sahip olunan veya güvenilen HTTPS originlerinde kullanılmalıdır. Güvenilmeyen bir URL için açılması önerilmez.",
                setOf("remote", "native", "bridge", "origin", "https", "güvenlik")
            ),
            KnowledgeChunk(
                "Paylaşım ve pano",
                "Native Bridge paylaşım özelliği Android paylaşım ekranını açabilir; pano özelliği web uygulamasının metni Android panosuna kopyalamasını sağlar.",
                setOf("paylaşım", "share", "pano", "clipboard")
            ),
            KnowledgeChunk(
                "Titreşim ve Haptic",
                "Titreşim/Haptic modülü web uygulamasından kısa titreşim veya haptic geri bildirim tetiklemek için kullanılır.",
                setOf("titreşim", "haptic", "vibration")
            ),
            KnowledgeChunk(
                "Media3 ve arka plan ses",
                "Media3 seçeneği müzik, radyo veya podcast benzeri projelerde Android medya oturumu, arka plan oynatma altyapısı ve medya bildirim kumandalarını etkinleştirmek için kullanılır.",
                setOf("media3", "medya", "arka", "plan", "ses", "müzik", "bildirim")
            ),
            KnowledgeChunk(
                "QR ve Barkod",
                "QR/Barkod Tarayıcı Native Bridge üzerinden kamera tabanlı tarama işlevi sağlar. Kamera izni gerektiğinde build ayarlarına eklenmelidir.",
                setOf("qr", "barkod", "barcode", "kamera", "tarayıcı")
            ),
            KnowledgeChunk(
                "Para kazanma",
                "Production bölümünde AdMob, Google Play Billing, Firebase Analytics ve Firebase Crashlytics isteğe bağlı olarak etkinleştirilebilir. Açılan servisler build'e ilgili Android SDK ve yapılandırmalarını ekleyebilir.",
                setOf("para", "kazanma", "admob", "billing", "firebase")
            ),
            KnowledgeChunk(
                "AdMob",
                "AdMob etkinleştirildiğinde reklam SDK'sı projeye eklenebilir. Reklam kullanılan yayınlarda Play Console reklam ve veri güvenliği beyanları gerçek uygulama davranışına göre doldurulmalıdır.",
                setOf("admob", "reklam", "ads", "ad", "id")
            ),
            KnowledgeChunk(
                "Google Play Billing",
                "Google Play Billing tek seferlik ürün, abonelik ve uygulama içi satın alma akışları için kullanılır. AppForge Studio'nun kendi Pro erişiminde satın alma tokenı resmi sunucuda doğrulanmadan gerçek Pro yetkisi verilmez.",
                setOf("billing", "satın", "abonelik", "ürün", "play", "token")
            ),
            KnowledgeChunk(
                "Firebase Analytics",
                "Firebase Analytics uygulama kullanım olaylarını ölçmek için isteğe bağlıdır. Etkinleştirildiğinde veri güvenliği beyanları uygulamanın gerçek veri kullanımına göre güncellenmelidir.",
                setOf("firebase", "analytics", "analiz", "olay")
            ),
            KnowledgeChunk(
                "Firebase Crashlytics",
                "Firebase Crashlytics uygulama çökmesi ve hata raporlarını toplamak için isteğe bağlıdır. Etkinleştirildiğinde ilgili Firebase yapılandırması ve Play Console veri beyanları kontrol edilmelidir.",
                setOf("firebase", "crashlytics", "çökme", "hata")
            ),
            KnowledgeChunk(
                "Deep Link",
                "Deep Link özelliği belirlenen bağlantılar açıldığında Android uygulamasının ilgili giriş noktasına yönlenmesini sağlar. Kullanılmıyorsa kapalı bırakılabilir.",
                setOf("deep", "link", "bağlantı", "url")
            ),
            KnowledgeChunk(
                "Debug ve Release imzalama",
                "Debug signing test içindir. Google Play production veya gerçek dağıtım için release keystore kullanılmalıdır. Aynı uygulamanın gelecekteki güncellemelerinde doğru imzalama zinciri korunmalıdır.",
                setOf("debug", "release", "signing", "imza", "keystore")
            ),
            KnowledgeChunk(
                "Keystore",
                "Keystore Android uygulamasını imzalamak için kullanılan anahtar kasasıdır. Alias, store password ve key password bilgileri güvenli biçimde saklanmalıdır; kaybedilmemeleri önemlidir.",
                setOf("keystore", "alias", "password", "parola", "jks")
            ),
            KnowledgeChunk(
                "Play App Signing ve Upload Key",
                "Google Play App Signing sertifikası ile geliştiricinin yükleme için kullandığı Upload Key aynı kavram değildir. Play'e AAB yüklerken doğru upload key ile imzalama yapılır.",
                setOf("play", "app", "signing", "upload", "key", "sertifika")
            ),
            KnowledgeChunk(
                "APK",
                "APK Android cihaza doğrudan kurulabilen paket biçimidir. Test, doğrudan dağıtım ve cihaz kurulumu için kullanışlıdır.",
                setOf("apk", "kur", "paket", "android")
            ),
            KnowledgeChunk(
                "AAB",
                "AAB Android App Bundle biçimidir ve Google Play yayını için tercih edilen çıktıdır. Google Play AAB'den cihaza uygun APK'ları üretir.",
                setOf("aab", "bundle", "play", "mağaza")
            ),
            KnowledgeChunk(
                "APK ve AAB farkı",
                "APK doğrudan cihaza kurulabilir. AAB doğrudan normal kullanıcı kurulumu için değildir; Google Play gibi dağıtım sistemi AAB'den cihaza uygun APK üretir.",
                setOf("apk", "aab", "fark", "bundle")
            ),
            KnowledgeChunk(
                "BOTH çıktısı",
                "BOTH seçeneği desteklenen build akışında hem APK hem AAB çıktısı istemek için kullanılır.",
                setOf("both", "apk", "aab", "ikisi")
            ),
            KnowledgeChunk(
                "EXE çıktısı ve dönüşüm",
                "AppForge dönüşüm araçları AppForge tarafından oluşturulmuş ve gerekli proje manifesti/verisi bulunan desteklenen çıktılar arasında APK ve Windows EXE dönüşüm akışları sunar. Her rastgele APK veya EXE'nin kaynak kodunu otomatik dönüştürmez.",
                setOf("exe", "windows", "dönüşüm", "apk", "manifest")
            ),
            KnowledgeChunk(
                "Build Service",
                "Resmi AppForge Build Service gerçek Android derlemesini, worker işlerini, cache kullanımını, canlı logları ve oluşan artifact bağlantılarını yönetir.",
                setOf("build", "service", "compile", "worker", "cache", "artifact")
            ),
            KnowledgeChunk(
                "Preflight",
                "Preflight build öncesinde package name, versionCode, versionName, proje kaynağı, imzalama ve desteklenen güvenlik/build ayarları gibi kontrolleri gösterir.",
                setOf("preflight", "kontrol", "package", "version", "imza")
            ),
            KnowledgeChunk(
                "Build cache",
                "Build cache aynı veya benzer bağımlılık ve derleme parçalarının tekrar kullanımını hızlandırabilir. Cache MISS tek başına hata değildir; yalnızca uygun önbellek eşleşmesi bulunmadığını gösterir.",
                setOf("cache", "miss", "build", "önbellek")
            ),
            KnowledgeChunk(
                "Build başarısız",
                "Build başarısızsa önce Preflight ve canlı Gradle logları kontrol edilmelidir. Geçersiz package name, sürüm bilgisi, eksik kaynak, imzalama bilgisi, bağımlılık veya ağ/worker sorunu yaygın nedenlerdir.",
                setOf("build", "başarısız", "hata", "gradle", "log", "olmuyor")
            ),
            KnowledgeChunk(
                "APK kurulumu",
                "APK kurulmazsa aynı package name'e sahip farklı imzalı eski sürüm, cihazın bilinmeyen uygulama yükleme izni, bozuk/eksik APK veya Android sürüm uyumsuzluğu kontrol edilmelidir.",
                setOf("apk", "kurulmuyor", "yüklenmiyor", "imza", "cihaz")
            ),
            KnowledgeChunk(
                "Sürümleme",
                "versionCode Android/Google Play'in güncelleme sırasını belirleyen pozitif tam sayıdır ve yeni Play yüklemelerinde daha yüksek olmalıdır. versionName kullanıcıya gösterilen okunabilir sürüm adıdır.",
                setOf("versioncode", "versionname", "sürüm", "güncelleme", "kod")
            ),
            KnowledgeChunk(
                "Otomatik versionCode",
                "Otomatik versionCode seçeneği build akışında sürüm kodunu artırmaya yardımcı olur. Play Store'a gönderilen her yeni AAB'nin daha önce kullanılmamış daha yüksek versionCode taşıması gerekir.",
                setOf("otomatik", "versioncode", "artır", "play")
            ),
            KnowledgeChunk(
                "Önizleme",
                "Önizleme ekranında telefon, büyük telefon ve tablet boyutları ile dikey/yatay görünüm kontrol edilebilir. Console, Network, Performance ve Security sekmeleri build öncesi inceleme içindir.",
                setOf("preview", "önizleme", "telefon", "tablet", "console", "network", "performance", "security")
            ),
            KnowledgeChunk(
                "Test Lab",
                "Test Lab başarılı APK/AAB çıktılarının boyutlarını, dosya kategorilerini, büyük dosyaları ve güvenlik kontrollerini analiz eder. Uygun build'ler karşılaştırılabilir ve release notes hazırlanabilir.",
                setOf("test", "lab", "analiz", "apk", "aab", "karşılaştır")
            ),
            KnowledgeChunk(
                "PWA Inspector",
                "PWA Inspector manifest.webmanifest veya uyumlu manifest.json, service worker, start_url, display modu ve ikon bilgilerini incelemek için kullanılır.",
                setOf("pwa", "manifest", "service", "worker", "start_url")
            ),
            KnowledgeChunk(
                "Şablonlar",
                "Şablonlar bölümü etkileşim, başlangıç, kütüphane, reklam, cihaz, sensör, sistem ve panel gibi kategorilerde hazır HTML başlangıç projeleri sunabilir.",
                setOf("şablon", "template", "başlangıç", "kategori")
            ),
            KnowledgeChunk(
                "Projelerim",
                "Projelerim ekranı yerel projeleri listelemek, yeni proje oluşturmak ve mevcut proje akışına dönmek için kullanılır.",
                setOf("projelerim", "proje", "liste", "oluştur")
            ),
            KnowledgeChunk(
                "Geçmiş ve build kayıtları",
                "Geçmiş bölümü daha önceki build veya proje işlemlerini takip etmek için kullanılır. Başarılı çıktılar uygun olduğunda tekrar indirilebilir veya analiz akışına gönderilebilir.",
                setOf("geçmiş", "history", "build", "kayıt", "indir")
            ),
            KnowledgeChunk(
                "Proje yedeği",
                "Production Center proje ZIP yedeğini dışa veya içe aktarabilir. Yerel HTML ve proje ayarları yedeklenir; keystore parolaları ve API anahtarları yedeğe yazılmaz.",
                setOf("backup", "yedek", "zip", "keystore", "api")
            ),
            KnowledgeChunk(
                "Keystore Yöneticisi",
                "Keystore Yöneticisi JKS/keystore kayıtlarını ve sertifika parmak izlerini yönetmeye yardımcı olur. Parolaların güvenli yedeği kullanıcının sorumluluğundadır.",
                setOf("keystore", "yönetici", "jks", "parmak", "sha")
            ),
            KnowledgeChunk(
                "Yerel AI",
                "AppForge Yerel AI Asistan LiteRT-LM uyumlu yerel modelle cihaz üzerinde yanıt üretir. AppForge'a ait sık sorular bilgi tabanından LLM çalıştırılmadan anında cevaplanabilir.",
                setOf("yerel", "ai", "yapay", "zeka", "litertlm", "hızlı", "faq")
            ),
            KnowledgeChunk(
                "Yerel AI gizliliği",
                "Yerel AI'da model çıkarımı cihazda yapılır. Soru, yanıt üretmek amacıyla AppForge Build Service'e veya başka bir bulut LLM API'sine gönderilmez. Kullanıcı ayrıca uzak build başlatırsa build için gereken proje verisi Build Service'e ayrı bir işlem olarak gönderilebilir.",
                setOf("yerel", "ai", "gizlilik", "offline", "sunucu", "bulut")
            ),
            KnowledgeChunk(
                "Yerel AI modeli",
                "Yerel AI modeli cihazda saklanır ve ilk hazırlıkta indirilebilir veya desteklenen model dosyası içe aktarılabilir. Model hazır olduktan sonra çıkarım cihaz üzerinde yapılır.",
                setOf("ai", "model", "indir", "litertlm", "dosya", "cihaz")
            ),
            KnowledgeChunk(
                "Yerel AI proje bağlamı",
                "Mevcut proje bağlamını kullan seçeneği açık olduğunda Yerel AI'ya uygulama adı, package, sürüm, kaynak tipi ve açık özelliklerin özeti eklenebilir; parola ve API anahtarlarının bağlama eklenmemesi amaçlanır.",
                setOf("ai", "proje", "bağlam", "context", "parola", "api")
            ),
            KnowledgeChunk(
                "Free plan",
                "Free hesapta toplam 5 farklı proje oluşturma deneme hakkı vardır. Proje silinse bile kullanılan hak geri gelmez. Aynı package name daha önce hakkı tüketmişse tekrar oluşturmak yeni hak tüketmez.",
                setOf("free", "ücretsiz", "5", "proje", "deneme", "hak")
            ),
            KnowledgeChunk(
                "Pro planları",
                "Pro tek seferlik satın almadır. Pro Aylık Google Play üzerinden otomatik yenilenen aboneliktir. İki Pro seçeneğinde de proje sayısı sınırsızdır ve Free projelerdeki Built with AppForge filigranı kaldırılır.",
                setOf("pro", "aylık", "abonelik", "watermark", "filigran", "sınırsız")
            ),
            KnowledgeChunk(
                "Pro güvenliği",
                "Gerçek Pro yetkisi yalnız cihazdaki yerel bir bayrakla açılmaz. Resmi AppForge sunucusu satın alma bilgisini ve gerektiğinde Play Integrity sonucunu doğrular; Pro-only build özellikleri sunucu tarafında korunabilir.",
                setOf("pro", "güvenlik", "integrity", "sunucu", "yetki")
            ),
            KnowledgeChunk(
                "Play Integrity",
                "Play Integrity AppForge Studio'nun desteklenen güvenlik kontrollerinde uygulama/cihaz isteğinin güvenilirliğini doğrulamaya yardımcı olur. Pro yetkisi gibi hassas sunucu işlemleri gerektiğinde Integrity sonucu ile korunabilir.",
                setOf("play", "integrity", "güvenlik", "doğrulama")
            ),
            KnowledgeChunk(
                "Google Play yayını",
                "Google Play için AAB, benzersiz package name, artan versionCode, release imzalama, mağaza girişi, gerekli politika/beyanlar ve uygun test/yayın kanalı hazırlanmalıdır. Play Console'daki gereksinimler uygulamanın gerçek özelliklerine göre doldurulmalıdır.",
                setOf("google", "play", "yayın", "aab", "mağaza", "production")
            ),
            KnowledgeChunk(
                "Play Store Rehberi",
                "AppForge Studio Ayarlar içindeki Play Store Rehberi Google Play'e hazırlık adımlarını hatırlatmak için kullanılır; nihai gereksinimler Play Console'da görünen güncel kurallardır.",
                setOf("play", "store", "rehber", "ayarlar", "yayın")
            ),
            KnowledgeChunk(
                "Gizlilik ve hesap silme",
                "Yasal bölüm kullanım koşulları ve gizlilik bilgilerine erişim sağlar. Hesap kullanan uygulamalarda Google Play gereksinimlerine uygun hesap silme akışı uygulama içinde ve gerekli web sayfasında sunulabilir.",
                setOf("gizlilik", "privacy", "hesap", "silme", "yasal")
            ),
            KnowledgeChunk(
                "Dil",
                "Ayarlar içindeki Dil bölümü desteklenen arayüz dilini seçmek için kullanılır. Yerel AI yanıt dili de uygulama dil tercihine göre yönlendirilebilir.",
                setOf("dil", "language", "türkçe", "english", "ayar")
            ),
            KnowledgeChunk(
                "Önbelleği Temizle",
                "Önbelleği Temizle seçeneği geçici build dosyaları ve uygulama önbelleklerini temizlemeye yardımcı olur. Kalıcı proje veya keystore verisini silmeden önce ekrandaki açıklamalar kontrol edilmelidir.",
                setOf("önbellek", "cache", "temizle", "geçici")
            ),
            KnowledgeChunk(
                "Geri bildirim",
                "Ayarlar içindeki Geri Bildirim Gönder seçeneği destek iletişimine ulaşmak için kullanılabilir. Teknik sorun bildirirken hata mesajı, build adımı ve mümkünse log bilgisi eklemek çözümü hızlandırır.",
                setOf("geri", "bildirim", "destek", "hata", "log")
            )
        )

    private val fastFaqs =
        listOf(
            FastFaq(
                "Free ve Pro arasındaki fark ne?",
                "Free planda toplam 5 farklı proje oluşturma deneme hakkı vardır ve kullanılan hak proje silinse de geri gelmez.\n\nPro ve Pro Aylık seçeneklerinde proje sayısı sınırsızdır ve Free projelerdeki \"Built with AppForge\" filigranı kaldırılır. Pro tek seferlik satın almadır; Pro Aylık Google Play üzerinden otomatik yenilenen aboneliktir.",
                setOf("free", "pro", "ücretsiz", "fark"),
                setOf("free pro farkı", "ücretsiz pro farkı")
            ),
            FastFaq(
                "AppForge Studio ne işe yarar?",
                "AppForge Studio; HTML, ZIP veya web projelerini Android uygulamasına dönüştürmek, APK/AAB üretmek, önizlemek, imzalamak, test etmek ve proje ayarlarını yönetmek için kullanılır.",
                setOf("appforge", "studio", "ne", "işe", "yarar"),
                setOf("appforge nedir", "uygulama ne işe yarar")
            ),
            FastFaq(
                "APK ile AAB arasındaki fark ne?",
                "APK doğrudan Android cihaza kurulabilir. AAB ise Google Play yayını için kullanılan Android App Bundle biçimidir; Google Play AAB'den cihaza uygun APK'ları üretir.",
                setOf("apk", "aab", "fark"),
                setOf("apk aab farkı", "aab nedir")
            ),
            FastFaq(
                "HTML/ZIP projesini nasıl APK yaparım?",
                "Yeni proje veya Hızlı Oluştur'da HTML/ZIP kaynağını seç, uygulama adı ve gerekli ayarları kontrol et, ardından APK çıktısı seçerek build başlat. Gelişmiş akışta izin, WebView, görünüm, Native Bridge ve imzalama ayarlarını ayrıca düzenleyebilirsin.",
                setOf("html", "zip", "apk", "nasıl"),
                setOf("html apk", "zip apk")
            ),
            FastFaq(
                "Web sitesini nasıl Android uygulamasına çeviririm?",
                "Kaynak adımında Web URL seçeneğini kullanıp HTTPS adresini gir. WebView ayarlarını kontrol et ve gerekiyorsa yalnız güvenilir originler için Native Bridge'i etkinleştir. Sonra APK veya AAB build alabilirsin.",
                setOf("web", "site", "url", "android", "çevir"),
                setOf("siteyi apk yap", "web url apk")
            ),
            FastFaq(
                "Play Store'a nasıl yayınlarım?",
                "Google Play için AAB oluştur, package name ve artan versionCode'u kontrol et, Release Keystore ile imzala ve Play Console'da mağaza girişi, görseller, içerik/politika beyanları ile test/yayın kanalını tamamla. Yeni AAB yüklerken versionCode daha önce kullanılmamış olmalıdır.",
                setOf("play", "store", "yayın", "aab"),
                setOf("google play yayın", "play console")
            ),
            FastFaq(
                "Package name nedir?",
                "Package name Android uygulamasının benzersiz kimliğidir; örneğin com.sirket.uygulama. Play Store'da yayınlanan uygulamanın package name değeri kalıcı kabul edilmelidir.",
                setOf("package", "name", "paket", "adı"),
                setOf("paket adı nedir")
            ),
            FastFaq(
                "versionCode ve versionName nedir?",
                "versionCode Android ve Google Play'in güncelleme sırasını belirleyen pozitif tam sayıdır; her yeni Play sürümünde daha yüksek olmalıdır. versionName ise kullanıcıya gösterilen okunabilir sürüm adıdır.",
                setOf("versioncode", "versionname", "sürüm"),
                setOf("sürüm kodu nedir")
            ),
            FastFaq(
                "Release Keystore nedir?",
                "Release Keystore üretim Android uygulamasını imzalamak için kullanılan anahtar kasasıdır. JKS/keystore dosyası, alias, store password ve key password güvenli biçimde yedeklenmelidir. Debug imza Play production için kullanılmamalıdır.",
                setOf("release", "keystore", "jks", "imza"),
                setOf("keystore nedir", "release imza")
            ),
            FastFaq(
                "Debug ve Release imza farkı ne?",
                "Debug signing test içindir. Release signing gerçek dağıtım ve Google Play production için kullanılır. Güncellemelerde doğru imzalama zincirinin korunması gerekir.",
                setOf("debug", "release", "imza", "fark"),
                setOf("debug signing release")
            ),
            FastFaq(
                "Native Bridge nedir?",
                "Native Bridge web içeriğinin AppForge Android API'lerine kontrollü erişmesini sağlar. Paylaşım, pano, titreşim/haptic, QR/barkod ve desteklenen native özellikler bu katman üzerinden kullanılabilir.",
                setOf("native", "bridge", "nedir"),
                setOf("javascript bridge nedir")
            ),
            FastFaq(
                "Media3 ne işe yarar?",
                "Media3; müzik, radyo veya podcast gibi projelerde Android medya oturumu, arka plan oynatma ve medya bildirim kumandaları için kullanılır.",
                setOf("media3", "medya", "arka", "plan"),
                setOf("arka plan ses", "medya oynatıcı")
            ),
            FastFaq(
                "Hangi Android izinlerini açmalıyım?",
                "Yalnız projenin gerçekten kullandığı izinleri aç. Kamera özelliği için kamera, konum özelliği için konum, Android 13+ bildirimleri için bildirim izni gerekebilir. HTML/ZIP analizi bazı ihtiyaçları otomatik önerebilir.",
                setOf("android", "izin", "kamera", "konum"),
                setOf("hangi izinler", "permission")
            ),
            FastFaq(
                "WebView için önerilen ayarlar neler?",
                "Modern web uygulamalarında JavaScript ve DOM Storage çoğu zaman gerekir. Geniş viewport ve overview responsive içerikte yardımcı olabilir. Mixed Content güvenlik için varsayılan olarak kapalı tutulmalı; yalnız zorunlu uyumluluk durumunda açılmalıdır.",
                setOf("webview", "önerilen", "ayar"),
                setOf("webview ayarları")
            ),
            FastFaq(
                "Build neden başarısız oluyor?",
                "Önce Preflight ve canlı Gradle logunu kontrol et. Geçersiz package name, versionCode/versionName, eksik proje kaynağı, yanlış imzalama bilgisi, bağımlılık sorunu veya ağ/worker hatası en yaygın nedenlerdir.",
                setOf("build", "başarısız", "olmuyor", "hata"),
                setOf("proje neden build olmuyor", "derleme hatası")
            ),
            FastFaq(
                "Build cache MISS hata mı?",
                "Hayır. Build cache MISS yalnızca uygun önbellek eşleşmesi bulunmadığını gösterir. Build diğer kontroller başarılıysa normal şekilde devam edebilir.",
                setOf("cache", "miss", "hata"),
                setOf("build cache miss")
            ),
            FastFaq(
                "APK neden kurulmuyor?",
                "Aynı package name'e sahip farklı imzalı eski uygulama varsa imza çakışabilir. Ayrıca bilinmeyen uygulama yükleme izni, bozuk APK veya Android sürüm uyumsuzluğunu kontrol et.",
                setOf("apk", "kurulmuyor", "yüklenmiyor"),
                setOf("apk kuramıyorum")
            ),
            FastFaq(
                "APK boyutunu nasıl küçültürüm?",
                "Gereksiz büyük medya ve kütüphaneleri kaldır, görüntü/video dosyalarını optimize et ve Test Lab'de en büyük dosyaları kontrol et. Release build'de küçültme/optimizasyon seçenekleri de toplam boyutu azaltabilir.",
                setOf("apk", "boyut", "küçült"),
                setOf("apk çok büyük")
            ),
            FastFaq(
                "Proje yedeğini nasıl alırım?",
                "Production Center'daki proje yedekleme aracını kullanarak proje ZIP yedeğini dışa aktarabilirsin. Yerel HTML ve proje ayarları yedeklenir; keystore parolaları ve API anahtarları güvenlik nedeniyle yedeğe yazılmaz.",
                setOf("proje", "yedek", "backup"),
                setOf("yedek nasıl alınır")
            ),
            FastFaq(
                "Şablonlar ne işe yarar?",
                "Şablonlar bölümü farklı kullanım senaryoları için hazır HTML başlangıç projeleri sunar. Bir şablonu başlangıç noktası olarak seçip kendi içeriğin ve ayarlarınla geliştirebilirsin.",
                setOf("şablon", "template", "ne", "işe"),
                setOf("hazır şablon")
            ),
            FastFaq(
                "Yerel AI internete bağlanıyor mu?",
                "Yanıt üretimi cihazdaki yerel model üzerinde yapılır; soru cevap üretmek için AppForge Build Service'e veya başka bir bulut LLM API'sine gönderilmez. Model ilk kez hazırlanırken indirme için internet gerekebilir.",
                setOf("yerel", "ai", "internet", "offline", "çevrimdışı"),
                setOf("ai çevrimdışı mı", "ai gizlilik")
            ),
            FastFaq(
                "Yerel AI modeli nerede çalışıyor?",
                "Yerel AI modeli cihaz üzerinde çalışır. Model cihazda saklanır; ilk hazırlıkta indirilebilir veya desteklenen model dosyası içe aktarılabilir.",
                setOf("yerel", "ai", "model", "cihaz"),
                setOf("litertlm model")
            ),
            FastFaq(
                "Play Integrity ne işe yarar?",
                "Play Integrity uygulama/cihaz isteğinin güvenilirliğini doğrulamaya yardımcı olur. AppForge'da Pro gibi hassas sunucu işlemleri gerektiğinde Integrity doğrulamasıyla korunabilir.",
                setOf("play", "integrity", "ne", "işe"),
                setOf("integrity nedir")
            ),
            FastFaq(
                "AdMob, Billing ve Firebase ne zaman açılmalı?",
                "Yalnız projen gerçekten kullanıyorsa aç. AdMob reklam, Billing uygulama içi satın alma/abonelik, Analytics kullanım olayları ve Crashlytics çökme raporları içindir. Etkinleştirilen servisler Play Console veri ve reklam beyanlarını etkileyebilir.",
                setOf("admob", "billing", "firebase", "aç"),
                setOf("para kazanma firebase")
            ),
            FastFaq(
                "Deep Link nedir?",
                "Deep Link belirlenen bağlantılar açıldığında Android uygulamasının ilgili giriş noktasına yönlenmesini sağlar. İhtiyacın yoksa kapalı bırakabilirsin.",
                setOf("deep", "link", "nedir"),
                setOf("deeplink")
            ),
            FastFaq(
                "Preview ne işe yarar?",
                "Preview ekranı uygulamayı farklı telefon/tablet boyutları ve yönlerde build öncesi kontrol etmeyi sağlar. Console, Network, Performance ve Security sekmeleri hata ve performans incelemesine yardımcı olur.",
                setOf("preview", "önizleme", "ne", "işe"),
                setOf("önizleme nedir")
            ),
            FastFaq(
                "Test Lab ne işe yarar?",
                "Test Lab APK/AAB çıktılarının boyutlarını, büyük dosyalarını ve güvenlik kontrollerini analiz eder. Uygun build'leri karşılaştırabilir ve release notes hazırlamana yardımcı olabilir.",
                setOf("test", "lab", "ne", "işe"),
                setOf("testlab")
            ),
            FastFaq(
                "PWA Inspector ne işe yarar?",
                "PWA Inspector manifest, service worker, start_url, display modu ve ikon bilgilerini kontrol eder. Web projesinin PWA yapısını hızlıca incelemek için kullanılır.",
                setOf("pwa", "inspector", "ne", "işe"),
                setOf("pwa nedir")
            ),
            FastFaq(
                "APK ve EXE dönüşümü nasıl çalışır?",
                "Dönüşüm araçları AppForge tarafından oluşturulmuş ve gerekli proje manifesti/verisi bulunan desteklenen çıktılarda APK ile Windows EXE arasında proje verisini kullanarak dönüşüm akışı sağlar. Rastgele bir APK veya EXE'nin kaynak kodunu otomatik çıkarmaz.",
                setOf("apk", "exe", "dönüşüm"),
                setOf("exe apk", "apk exe")
            ),
            FastFaq(
                "Önbelleği temizlemek projelerimi siler mi?",
                "Önbelleği Temizle geçici build dosyalarını ve önbellekleri temizlemek içindir. Kalıcı proje/keystore verisini silmeden önce ekrandaki açıklamaları kontrol etmek yine de önemlidir.",
                setOf("önbellek", "temizle", "proje", "sil"),
                setOf("cache temizle")
            ),
            FastFaq(
                "Hesabımı nasıl silebilirim?",
                "AppForge Studio'nun hesap silme seçeneğini uygulama içindeki hesap/yasal akıştan kullanabilirsin. Google Play için gerekli web tabanlı hesap silme sayfası da uygulamanın yayın yapılandırmasına göre sunulur.",
                setOf("hesap", "sil", "silme"),
                setOf("account delete")
            )
        )

    fun quickQuestions(): List<String> =
        listOf(
            "AppForge Studio ne işe yarar?",
            "Free ve Pro arasındaki fark ne?",
            "HTML/ZIP projesini nasıl APK yaparım?",
            "Web sitesini nasıl Android uygulamasına çeviririm?",
            "APK ile AAB arasındaki fark ne?",
            "Play Store'a nasıl yayınlarım?",
            "Package name nedir?",
            "versionCode ve versionName nedir?",
            "Release Keystore nedir?",
            "Native Bridge nedir?",
            "Media3 ne işe yarar?",
            "Hangi Android izinlerini açmalıyım?",
            "WebView için önerilen ayarlar neler?",
            "Build neden başarısız oluyor?",
            "APK neden kurulmuyor?",
            "APK boyutunu nasıl küçültürüm?",
            "Proje yedeğini nasıl alırım?",
            "Yerel AI internete bağlanıyor mu?",
            "Play Integrity ne işe yarar?",
            "AdMob, Billing ve Firebase ne zaman açılmalı?",
            "Deep Link nedir?",
            "Preview ne işe yarar?",
            "Test Lab ne işe yarar?",
            "PWA Inspector ne işe yarar?"
        )

    fun retrieve(
        question: String,
        maxChunks: Int = 5
    ): List<KnowledgeChunk> {
        val tokens =
            tokenize(
                question
            )

        return chunks
            .map {
                chunk ->
                val haystack =
                    normalize(
                        chunk.title +
                            " " +
                            chunk.text +
                            " " +
                            chunk.keywords.joinToString(" ")
                    )

                var score =
                    0

                for (
                    token in
                    tokens
                ) {
                    if (
                        chunk.keywords.any {
                            key ->
                            val normalizedKey =
                                normalize(key)

                            normalizedKey.contains(token) ||
                                token.contains(normalizedKey)
                        }
                    ) {
                        score +=
                            6
                    }

                    if (
                        haystack.contains(
                            token
                        )
                    ) {
                        score +=
                            1
                    }
                }

                chunk to
                    score
            }
            .sortedByDescending {
                it.second
            }
            .filter {
                it.second >
                    0
            }
            .take(
                maxChunks
            )
            .map {
                it.first
            }
            .ifEmpty {
                chunks.take(
                    5
                )
            }
    }

    /*
     * Bilgi tabanında cevabı kesin olan sorularda LLM çalıştırılmaz.
     * Bu yol model yüklenmemiş olsa bile milisaniyeler içinde cevap verir.
     */
    fun directTurkishAnswer(
        question: String
    ): String? {

        val normalizedQuestion =
            normalize(
                question
            )

        if (
            normalizedQuestion.isBlank()
        ) {
            return null
        }


        if (
            normalizedQuestion.contains(
                "python"
            ) ||
            normalizedQuestion.contains(
                "programlama dili"
            ) ||
            normalizedQuestion.contains(
                "yazılım dili"
            ) ||
            normalizedQuestion.contains(
                "hangi dilleri"
            ) ||
            normalizedQuestion.contains(
                "dil deste"
            )
        ) {
            return LANGUAGE_SUPPORT_ANSWER
        }

        fastFaqs
            .firstOrNull {
                faq ->
                normalizedQuestion ==
                    normalize(
                        faq.question
                    ) ||
                    faq.aliases.any {
                        alias ->
                        normalizedQuestion ==
                            normalize(
                                alias
                            )
                    }
            }
            ?.let {
                return it.answer
            }

        val tokens =
            tokenize(
                question
            )

        val best =
            fastFaqs
                .map {
                    faq ->
                    var score =
                        0

                    val normalizedFaqQuestion =
                        normalize(
                            faq.question
                        )

                    if (
                        normalizedQuestion.contains(
                            normalizedFaqQuestion
                        ) ||
                        normalizedFaqQuestion.contains(
                            normalizedQuestion
                        )
                    ) {
                        score +=
                            40
                    }

                    faq.aliases
                        .forEach {
                            alias ->
                            val normalizedAlias =
                                normalize(
                                    alias
                                )

                            if (
                                normalizedAlias.length >=
                                    5 &&
                                normalizedQuestion.contains(
                                    normalizedAlias
                                )
                            ) {
                                score +=
                                    25
                            }
                        }

                    val matchedKeywords =
                        faq.keywords.count {
                            keyword ->
                            val key =
                                normalize(
                                    keyword
                                )

                            key in
                                tokens ||
                                (
                                    key.length >=
                                        4 &&
                                    normalizedQuestion.contains(
                                        key
                                    )
                                )
                        }

                    score +=
                        matchedKeywords *
                            5

                    if (
                        matchedKeywords >=
                            2
                    ) {
                        score +=
                            6
                    }

                    faq to
                        score
                }
                .maxByOrNull {
                    it.second
                }

        return if (
            best != null &&
            best.second >=
                15
        ) {
            best.first.answer
        } else {
            val relevant =
                chunks
                    .map {
                        chunk ->

                        val keywordMatches =
                            chunk.keywords.count {
                                keyword ->

                                val key =
                                    normalize(
                                        keyword
                                    )

                                key in tokens ||
                                    (
                                        key.length >= 4 &&
                                            normalizedQuestion.contains(key)
                                    )
                            }

                        val titleMatches =
                            tokenize(
                                chunk.title
                            ).count {
                                it in tokens
                            }

                        chunk to
                            (
                                keywordMatches * 5 +
                                    titleMatches * 2
                            )
                    }
                    .filter {
                        it.second >= 5
                    }
                    .sortedByDescending {
                        it.second
                    }
                    .take(2)
                    .map {
                        it.first
                    }

            if (
                relevant.isEmpty()
            ) {
                null
            } else {
                relevant.joinToString(
                    separator = "\n\n"
                ) {
                    "${it.title}: ${it.text}"
                }
            }
        }
    }

    fun promptContext(
        question: String,
        draft: ProjectDraft,
        includeProjectContext: Boolean,
        runtimeContext: AssistantRuntimeContext? = null
    ): String =
        buildString {
            appendLine(
                AppForgeAssistantIntegration
                    .applicationMap()
            )

            appendLine()
            appendLine(
                "APPFORGE YEREL BİLGİ TABANI:"
            )

            retrieve(
                question
            )
                .forEach {
                    appendLine(
                        "- ${it.title}: ${it.text}"
                    )
                }

            if (
                includeProjectContext
            ) {
                appendLine()
                appendLine(
                    "MEVCUT PROJE ÖZETİ:"
                )
                append(
                    projectContext(
                        draft
                    )
                )
            }

            if (
                runtimeContext != null
            ) {
                appendLine()
                appendLine()
                append(
                    AppForgeAssistantIntegration
                        .runtimeSummary(
                            runtimeContext
                        )
                )
            }
        }

    fun projectContext(
        draft: ProjectDraft
    ): String =
        buildString {
            appendLine(
                "Uygulama adı: ${draft.appName.ifBlank { "(boş)" }}"
            )
            appendLine(
                "Package: ${draft.packageName.ifBlank { "(boş)" }}"
            )
            appendLine(
                "Kaynak: ${draft.sourceMode.name}"
            )

            if (
                draft.sourceMode ==
                    SourceMode.URL
            ) {
                appendLine(
                    "URL: ${draft.webUrl}"
                )
            } else {
                appendLine(
                    "Yerel dosya: ${draft.sourceLabel.ifBlank { draft.startPage ?: "(seçilmedi)" }}"
                )
            }

            appendLine(
                "Sürüm: ${draft.versionName} / ${draft.versionCode}"
            )
            appendLine(
                "Çıktı: ${draft.buildOutput}"
            )
            appendLine(
                "İmzalama: ${draft.signingMode.name}"
            )
            appendLine(
                "Native Bridge: ${draft.javascriptBridge}"
            )
            appendLine(
                "Remote Bridge: ${draft.remoteBridgeAllowed}"
            )
            appendLine(
                "Kamera: ${draft.camera}"
            )
            appendLine(
                "Konum: ${draft.location}"
            )
            appendLine(
                "QR: ${draft.qrScanner}"
            )
            appendLine(
                "Billing: ${draft.billingEnabled}"
            )
            appendLine(
                "AdMob: ${draft.admobEnabled}"
            )
            appendLine(
                "Firebase Analytics: ${draft.firebaseAnalyticsEnabled}"
            )
            appendLine(
                "Firebase Crashlytics: ${draft.firebaseCrashlyticsEnabled}"
            )
            appendLine(
                "Firebase Messaging: ${draft.firebaseMessagingEnabled}"
            )
            appendLine(
                "Bildirim: ${draft.notifications}"
            )
            appendLine(
                "Dosya yükleme / indirme: ${draft.fileUpload} / ${draft.downloads}"
            )
            appendLine(
                "Offline cache: ${draft.offlineCache}"
            )
            appendLine(
                "Deep Link: ${draft.deepLinkEnabled}"
            )
            appendLine(
                "Kaynak teknolojisi: ${draft.sourceTechnologyLabel}"
            )
            appendLine(
                "Kaynak build motoru: ${draft.sourceBuildEngine}"
            )
            appendLine(
                "Kaynak build-ready: ${draft.sourceBuildReady}"
            )
        }

    private fun normalize(
        input: String
    ): String =
        input
            .lowercase(
                Locale.ROOT
            )
            .replace(
                Regex(
                    "[^\\p{L}\\p{N}_]+"
                ),
                " "
            )
            .trim()
            .replace(
                Regex(
                    "\\s+"
                ),
                " "
            )

    private fun tokenize(
        input: String
    ): Set<String> =
        normalize(
            input
        )
            .split(" ")
            .map {
                it.trim()
            }
            .filter {
                it.length >=
                    2
            }
            .toSet()

    fun helpArticles(): List<HelpArticle> {
        val chunkArticles =
            chunks.map {
                chunk ->
                HelpArticle(
                    category =
                        helpCategory(
                            chunk.title,
                            chunk.text,
                            chunk.keywords
                        ),
                    title =
                        chunk.title,
                    text =
                        chunk.text,
                    keywords =
                        chunk.keywords
                )
            }

        val faqArticles =
            fastFaqs.map {
                faq ->
                HelpArticle(
                    category =
                        helpCategory(
                            faq.question,
                            faq.answer,
                            faq.keywords
                        ),
                    title =
                        faq.question,
                    text =
                        faq.answer,
                    keywords =
                        faq.keywords +
                            faq.aliases
                )
            }

        val languageSupport =
            HelpArticle(
                category =
                    "Kaynak & Diller",
                title =
                    "Hangi yazılım dilleri ve teknolojiler destekleniyor?",
                text =
                    LANGUAGE_SUPPORT_ANSWER,
                keywords =
                    setOf(
                        "html",
                        "css",
                        "javascript",
                        "typescript",
                        "react",
                        "vue",
                        "vite",
                        "bootstrap",
                        "kotlin",
                        "java",
                        "python",
                        "flutter",
                        "dart",
                        "c++",
                        "csharp",
                        "react native",
                        "node",
                        "php",
                        "dil",
                        "teknoloji"
                    )
            )

        return (
            listOf(
                languageSupport
            ) +
                faqArticles +
                chunkArticles
            )
            .distinctBy {
                normalize(
                    it.title
                )
            }
            .sortedWith(
                compareBy<HelpArticle> {
                    it.category
                }
                    .thenBy {
                        it.title
                    }
            )
    }

    fun helpCategories(): List<String> =
        helpArticles()
            .map {
                it.category
            }
            .distinct()
            .sorted()

    fun searchHelp(
        query: String,
        maxResults: Int = 60
    ): List<HelpArticle> {
        val normalizedQuery =
            normalize(
                query
            )

        if (
            normalizedQuery.isBlank()
        ) {
            return helpArticles()
                .take(
                    maxResults
                )
        }

        val queryTokens =
            tokenize(
                normalizedQuery
            )

        return helpArticles()
            .map {
                article ->
                val title =
                    normalize(
                        article.title
                    )

                val category =
                    normalize(
                        article.category
                    )

                val body =
                    normalize(
                        article.text
                    )

                val keywords =
                    article.keywords
                        .joinToString(
                            " "
                        ) {
                            normalize(
                                it
                            )
                        }

                var score =
                    0

                if (
                    title ==
                        normalizedQuery
                ) {
                    score +=
                        100
                }

                if (
                    title.contains(
                        normalizedQuery
                    )
                ) {
                    score +=
                        45
                }

                if (
                    category.contains(
                        normalizedQuery
                    )
                ) {
                    score +=
                        18
                }

                if (
                    body.contains(
                        normalizedQuery
                    )
                ) {
                    score +=
                        15
                }

                queryTokens
                    .forEach {
                        token ->
                        if (
                            title.contains(
                                token
                            )
                        ) {
                            score +=
                                12
                        }

                        if (
                            keywords.contains(
                                token
                            )
                        ) {
                            score +=
                                9
                        }

                        if (
                            body.contains(
                                token
                            )
                        ) {
                            score +=
                                3
                        }
                    }

                article to
                    score
            }
            .filter {
                it.second >
                    0
            }
            .sortedByDescending {
                it.second
            }
            .take(
                maxResults
            )
            .map {
                it.first
            }
    }

    private fun helpCategory(
        title: String,
        text: String,
        keywords: Set<String>
    ): String {
        val haystack =
            normalize(
                title +
                    " " +
                    text +
                    " " +
                    keywords.joinToString(
                        " "
                    )
            )

        return when {
            listOf(
                "yerel ai",
                "litertlm",
                "yapay zeka",
                "model"
            ).any {
                haystack.contains(
                    it
                )
            } ->
                "Yerel AI"

            listOf(
                "admob",
                "billing",
                "firebase",
                "abonelik",
                "satın"
            ).any {
                haystack.contains(
                    it
                )
            } ->
                "Para Kazanma"

            listOf(
                "play store",
                "google play",
                "aab",
                "upload key",
                "play integrity"
            ).any {
                haystack.contains(
                    it
                )
            } ->
                "Google Play"

            listOf(
                "native bridge",
                "media3",
                "qr",
                "barkod",
                "clipboard",
                "pano",
                "haptic"
            ).any {
                haystack.contains(
                    it
                )
            } ->
                "Native Özellikler"

            listOf(
                "keystore",
                "imza",
                "signing",
                "gizlilik",
                "hesap",
                "güvenlik"
            ).any {
                haystack.contains(
                    it
                )
            } ->
                "Güvenlik & Hesap"

            listOf(
                "build",
                "gradle",
                "preflight",
                "cache",
                "apk",
                "exe",
                "versioncode",
                "versionname"
            ).any {
                haystack.contains(
                    it
                )
            } ->
                "Build & Çıktı"

            listOf(
                "html",
                "zip",
                "webview",
                "url",
                "pwa",
                "şablon",
                "template",
                "deep link"
            ).any {
                haystack.contains(
                    it
                )
            } ->
                "Kaynak & Web"

            else ->
                "Başlangıç"
        }
    }
}
