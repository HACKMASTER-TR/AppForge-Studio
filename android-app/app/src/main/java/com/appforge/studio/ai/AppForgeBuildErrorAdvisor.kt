package com.appforge.studio.ai

data class BuildErrorDiagnosis(
    val category: String,
    val title: String,
    val reason: String,
    val solution: String,
    val confidence: Int,
    val evidence: String?
)

object AppForgeBuildErrorAdvisor {

    private data class Rule(
        val category: String,
        val title: String,
        val needles: List<String>,
        val reason: String,
        val solution: String,
        val confidence: Int = 94
    )

    private val rules =
        listOf(
            Rule(
                category = "Proje türü",
                title = "Proje başlangıç sayfası algılanamadı",
                needles = listOf(
                    "unknown build motoru",
                    "bilinmeyen proje algılandı",
                    "güvenli bir build motoru seçilemedi",
                    "webview build motoruna yanlışlıkla gönderilmedi",
                    "projede html başlangıç dosyası bulunamadı",
                    "html başlangıç dosyası bulunamadı",
                    "windows exe çıktısı",
                    "windows_exe_source_incompatible"
                ),
                reason = "Seçilen ZIP içinde desteklenen proje imzası veya HTML başlangıç sayfası bulunamadı.",
                solution = "ZIP içinde index.html, main.html ya da desteklenen Android/Flutter/React Native proje dosyalarının bulunduğunu kontrol et. Geçerli HTML sayfası varsa AppForge onu artık otomatik başlangıç sayfasına dönüştürür.",
                confidence = 99
            ),
            Rule(
                category = "İmzalama",
                title = "Keystore / imza bilgisi hatası",
                needles = listOf(
                    "keystore was tampered",
                    "password was incorrect",
                    "cannot recover key",
                    "keytool error",
                    "keystore file",
                    "no key with alias",
                    "alias does not exist",
                    "signingconfig",
                    "keystore seç",
                    "key alias gerekli",
                    "store password gerekli",
                    "key password gerekli",
                    "özel keystore ile imzalama için build service https olmalı"
                ),
                reason = "Release keystore, alias veya parola bilgilerinden biri derleme sırasında doğrulanamadı.",
                solution = "İmzalama adımında doğru JKS/keystore dosyasını, key alias, store password ve key password bilgilerini kontrol et. Parolaları loga veya AI bağlamına kopyalama."
            ),
            Rule(
                category = "Firebase",
                title = "Firebase yapılandırması eksik veya uyumsuz",
                needles = listOf(
                    "google-services.json is missing",
                    "google-services.json",
                    "no matching client found for package name",
                    "processdebuggoogleservices",
                    "processreleasegoogleservices"
                ),
                reason = "Firebase etkin fakat google-services yapılandırması bulunamadı veya package name ile eşleşmiyor.",
                solution = "Firebase Console'daki Android uygulama package name değerini AppForge projesiyle eşleştir ve doğru google-services.json dosyasını yeniden seç."
            ),
            Rule(
                category = "Manifest",
                title = "AndroidManifest birleştirme hatası",
                needles = listOf(
                    "manifest merger failed",
                    "manifest merger",
                    "uses-sdk:minSdkVersion",
                    "android:exported needs to be explicitly specified"
                ),
                reason = "Android manifestindeki bir özellik, SDK gereksinimi veya bağımlılık manifesti birbiriyle çakışıyor.",
                solution = "Hata satırındaki manifest özelliğini kontrol et. Özellikle minSdk, exported, permission ve provider/authority çakışmalarını düzelt."
            ),
            Rule(
                category = "Package",
                title = "Package name / namespace hatası",
                needles = listOf(
                    "geçerli package name gir",
                    "package name geçersiz",
                    "geçersiz package name",
                    "namespace",
                    "package name is not valid",
                    "invalid package",
                    "applicationid",
                    "package attribute is not a valid java package name"
                ),
                reason = "Android package name veya Gradle namespace değeri geçerli biçimde değil ya da beklenen değerle uyuşmuyor.",
                solution = "com.sirket.uygulama biçiminde, boşluksuz ve geçerli bir package name kullan. Varsayılan com.example.myapp değerini production için değiştirmelisin."
            ),
            Rule(
                category = "Kaynak",
                title = "Android resource linking hatası",
                needles = listOf(
                    "android resource linking failed",
                    "aapt2",
                    "resource not found",
                    "error: resource",
                    "failed linking references"
                ),
                reason = "Bir Android kaynak dosyası, renk, ikon, XML değeri veya resource referansı çözümlenemedi.",
                solution = "Logdaki ilk 'resource ... not found' veya XML satırını bul. İkon/renk/XML adını düzelt veya eksik kaynağı projeye ekle."
            ),
            Rule(
                category = "Bağımlılık",
                title = "Gradle bağımlılığı çözümlenemedi",
                needles = listOf(
                    "could not resolve",
                    "could not find",
                    "failed to resolve",
                    "could not download",
                    "could not get resource",
                    "could not resolve all files"
                ),
                reason = "Gradle gerekli Android kütüphanesini depodan bulamadı veya indiremedi.",
                solution = "İnternet bağlantısını ve Maven/Google depolarını kontrol et. Kütüphane sürümü yanlışsa desteklenen sürüme dön. Geçici ağ hatasında yeniden derlemek yeterli olabilir."
            ),
            Rule(
                category = "Ağ",
                title = "Build ağı / DNS bağlantı hatası",
                needles = listOf(
                    "unable to resolve host",
                    "unknownhostexception",
                    "connection reset",
                    "connection refused",
                    "network is unreachable",
                    "no route to host",
                    "read timed out",
                    "connect timed out",
                    "sockettimeoutexception"
                ),
                reason = "Build Worker veya Gradle dış depolara erişirken geçici ağ/DNS problemi yaşadı.",
                solution = "Bağlantı düzeldikten sonra aynı ayarlarla tekrar derle. Sorun sürekli olursa Worker/Railway ağ durumunu ve DNS erişimini kontrol et.",
                confidence = 98
            ),
            Rule(
                category = "Depolama",
                title = "Build Worker disk alanı yetersiz",
                needles = listOf(
                    "no space left on device",
                    "disk quota exceeded",
                    "enospc"
                ),
                reason = "Worker veya derleme ortamında geçici dosyalar için yeterli boş alan kalmadı.",
                solution = "Worker cache/artifact temizliği yap veya disk alanını artır. Sonra build'i tekrar başlat.",
                confidence = 99
            ),
            Rule(
                category = "Bellek",
                title = "Gradle / JVM belleği yetersiz",
                needles = listOf(
                    "java heap space",
                    "outofmemoryerror",
                    "metaspace",
                    "gc overhead limit exceeded"
                ),
                reason = "Gradle, Kotlin veya Android araçları derleme sırasında kullanılabilir JVM belleğini tüketti.",
                solution = "Worker belleğini artır, büyük kaynakları küçült ve gereksiz bağımlılıkları azalt. Gerekirse Gradle JVM heap değerini yükselt."
            ),
            Rule(
                category = "SDK",
                title = "Android SDK / Build Tools eksik",
                needles = listOf(
                    "failed to find target with hash string",
                    "build tools revision",
                    "sdk location not found",
                    "license for package android sdk",
                    "failed to install the following android sdk packages",
                    "min sdk 26 ile 37 arasında olmalı",
                    "hedef sdk 26 ile 37 arasında olmalı",
                    "min sdk, hedef sdk değerinden büyük olamaz"
                ),
                reason = "Derleme ortamında gereken Android SDK platformu, Build Tools veya lisans kabulü eksik.",
                solution = "Worker imajında projenin compileSdk/targetSdk gereksinimine uygun Android SDK paketlerini kur ve lisansları kabul et."
            ),
            Rule(
                category = "Kotlin",
                title = "Kotlin derleme hatası",
                needles = listOf(
                    "compilation error",
                    "compiledebugkotlin",
                    "compilereleasekotlin",
                    "unresolved reference",
                    "type mismatch",
                    "overload resolution ambiguity"
                ),
                reason = "Üretilen Android/Kotlin kodunda derleyicinin kabul etmediği bir referans veya tip uyuşmazlığı var.",
                solution = "Logdaki ilk Kotlin hata satırını esas al. 'Unresolved reference', 'Type mismatch' veya dosya:satır bilgisini düzelt; sonraki hatalar çoğu zaman ilk hatanın devamıdır."
            ),
            Rule(
                category = "Gradle",
                title = "Gradle proje dizini kullanılamıyor",
                needles = listOf(
                    "configuring project with invalid directory",
                    "without an existing directory is not allowed",
                    "does not exist, can't be written to or is not a directory",
                    "configured projectdirectory",
                    "error resolving plugin [id: 'dev.flutter.flutter-plugin-loader'"
                ),
                reason = "Gradle'ın dahil etmeye çalıştığı projectDirectory mevcut değil veya yazılabilir değil.",
                solution = "Hata satırındaki included-build/projectDirectory yolunu kontrol et. Flutter build'lerinde AppForge writable workspace mirror kullanmalıdır.",
                confidence = 100
            ),
            Rule(
                category = "Java",
                title = "Java derleme / sürüm uyumsuzluğu",
                needles = listOf(
                    "invalid source release",
                    "unsupported class file major version",
                    "requires java runtime",
                ),
                reason = "JDK sürümü ile Gradle/Android Gradle Plugin veya kaynak kod hedefi uyuşmuyor.",
                solution = "AppForge Worker'ın kullandığı JDK sürümünü AGP/Gradle gereksinimiyle eşleştir. Gerekirse toolchain/sourceCompatibility ayarını düzelt."
            ),
            Rule(
                category = "Duplicate",
                title = "Çakışan sınıf veya kaynak",
                needles = listOf(
                    "duplicate class",
                    "duplicate resources",
                    "already defined",
                    "more than one file was found with os independent path"
                ),
                reason = "Aynı sınıf veya paketlenen kaynak birden fazla bağımlılık/proje parçasından geliyor.",
                solution = "Çakışan bağımlılığın birini kaldır, exclude kullan veya aynı kütüphanenin farklı sürümlerini tek sürümde birleştir."
            ),
            Rule(
                category = "R8/ProGuard",
                title = "R8 / shrinker hatası",
                needles = listOf(
                    "missing classes detected while running r8",
                    "r8: missing class",
                    "minifyreleasewithr8",
                    "proguard"
                ),
                reason = "Release küçültme sırasında R8 gerekli bir sınıfı bulamadı veya kural eksikliği tespit etti.",
                solution = "Eksik bağımlılığı ekle veya yalnız gerekli sınıflar için uygun keep/dontwarn kuralları tanımla. Önce logdaki 'Missing class' satırını kontrol et."
            ),
            Rule(
                category = "Sürüm",
                title = "Sürüm bilgisi geçersiz",
                needles = listOf(
                    "versioncode",
                    "version code",
                    "versionname",
                    "version name"
                ),
                reason = "Derleme veya yayın zinciri versionCode/versionName değerinde sorun bildirdi.",
                solution = "versionCode değerinin pozitif ve önceki Play sürümünden yüksek olduğundan emin ol. versionName boş olmamalı."
            ),
            Rule(
                category = "Genel Gradle",
                title = "Gradle derlemesi başarısız",
                needles = listOf(
                    "failure: build failed with an exception",
                    "execution failed for task",
                    "build failed"
                ),
                reason = "Gradle bir görev sırasında hata verdi; asıl neden genellikle bu satırdan hemen önceki ilk ERROR/Exception satırıdır.",
                solution = "Canlı logda en üstteki gerçek hata satırını bul. AppForge bu kartta mümkünse onu 'Log ipucu' olarak gösterir. Sorunu düzelttikten sonra tekrar derle.",
                confidence = 75
            )
        )

    fun diagnose(
        logs: List<String>,
        preflight: List<String>,
        status: String
    ): BuildErrorDiagnosis {
        /*
         * Başarılı ön kontrol satırları hata teşhisinin kanıtı değildir.
         *
         * Örnek:
         * "✅ versionName dolu."
         *
         * Bu satır eski yapıda "versionName" kuralını tetikleyip
         * gerçek build hatasını gizleyebiliyordu.
         */
        val diagnosticPreflight =
            preflight
                .filterNot {
                    line ->
                    val normalized =
                        line.trim()

                    normalized.startsWith(
                        "✅"
                    ) ||
                        normalized.startsWith(
                            "✓"
                        )
                }

        val combinedLines =
            (
                diagnosticPreflight +
                    logs.takeLast(160) +
                    status
            )
                .map {
                    redact(
                        it
                    )
                }
                .filter {
                    it.isNotBlank()
                }

        val haystack =
            combinedLines
                .joinToString(
                    "\n"
                )
                .lowercase()

        for (
            rule in rules
        ) {
            if (
                rule.needles.any {
                    haystack.contains(
                        it.lowercase()
                    )
                }
            ) {
                return BuildErrorDiagnosis(
                    category =
                        rule.category,
                    title =
                        rule.title,
                    reason =
                        rule.reason,
                    solution =
                        rule.solution,
                    confidence =
                        rule.confidence,
                    evidence =
                        findEvidence(
                            combinedLines,
                            rule.needles
                        )
                )
            }
        }

        /*
         * Android Studio tarafındaki validateDraft() gibi
         * yerel kontroller API çağrısından önce hata verebilir.
         *
         * Böyle bir kullanıcı doğrulama hatası henüz özel bir
         * kuralla eşleşmediyse "bilinmeyen build hatası" demek
         * yerine gerçek mesajı tek seferde kullanıcıya açıkla.
         */
        val localValidationEvidence =
            combinedLines
                .asReversed()
                .firstOrNull {
                    line ->
                    val lower =
                        line
                            .trim()
                            .lowercase()

                    lower.startsWith(
                        "hata:"
                    ) ||
                        lower.contains(
                            " gerekli."
                        ) ||
                        lower.contains(
                            " gerekli"
                        ) ||
                        lower.contains(
                            " olmalı."
                        ) ||
                        lower.contains(
                            " olamaz."
                        ) ||
                        lower.contains(
                            " geçersiz"
                        )
                }

        if (
            localValidationEvidence !=
            null
        ) {
            val cleanReason =
                localValidationEvidence
                    .replaceFirst(
                        Regex(
                            "^\\s*Hata:\\s*",
                            RegexOption.IGNORE_CASE
                        ),
                        ""
                    )
                    .trim()

            return BuildErrorDiagnosis(
                category =
                    "Doğrulama",
                title =
                    "Girilen bilgiler geçersiz veya eksik",
                reason =
                    cleanReason.ifBlank {
                        "Bir proje ayarı AppForge doğrulamasından geçemedi."
                    },
                solution =
                    "Hata mesajında belirtilen alanı düzelt ve aynı ayarlarla tekrar dene.",
                confidence =
                    90,
                evidence =
                    localValidationEvidence
            )
        }

        val fallbackEvidence =
            combinedLines
                .asReversed()
                .firstOrNull {
                    line ->
                    val lower =
                        line.lowercase()

                    lower.contains(
                        "error"
                    ) ||
                        lower.contains(
                            "exception"
                        ) ||
                        lower.contains(
                            "failed"
                        ) ||
                        lower.contains(
                            "hata"
                        )
                }
                ?: combinedLines
                    .lastOrNull()

        return BuildErrorDiagnosis(
            category =
                "Bilinmeyen",
            title =
                "Build hatası otomatik sınıflandırılamadı",
            reason =
                "Logda bilinen AppForge hata kalıplarından net bir eşleşme bulunamadı.",
            solution =
                "Canlı logdaki ilk ERROR/Exception satırını kontrol et. Aynı hatayı tekrar alırsan bu satırı Yerel AI'ya sorabilir veya Build Service/Worker logunu inceleyebilirsin.",
            confidence =
                45,
            evidence =
                fallbackEvidence
        )
    }

    private fun findEvidence(
        lines: List<String>,
        needles: List<String>
    ): String? {
        val matched =
            lines
                .asReversed()
                .firstOrNull {
                    line ->
                    val lower =
                        line.lowercase()

                    needles.any {
                        lower.contains(
                            it.lowercase()
                        )
                    }
                }

        return matched
            ?.take(
                420
            )
    }

    /*
     * Build loglarında yanlışlıkla hassas değer görünürse
     * kullanıcıya gösterilen tanı kartına taşımamaya çalış.
     */
    private fun redact(
        raw: String
    ): String {
        var value =
            raw

        value =
            value.replace(
                Regex(
                    "(?i)(authorization\\s*[:=]\\s*)([^\\s]+)"
                ),
                "$1[REDACTED]"
            )

        value =
            value.replace(
                Regex(
                    "(?i)((?:api[_-]?key|token|password|storepassword|keypassword)\\s*[:=]\\s*)([^\\s,;]+)"
                ),
                "$1[REDACTED]"
            )

        return value
    }
}
