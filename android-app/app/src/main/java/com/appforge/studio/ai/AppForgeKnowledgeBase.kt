package com.appforge.studio.ai

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import java.util.Locale

data class KnowledgeChunk(
    val title: String,
    val text: String,
    val keywords: Set<String>
)

object AppForgeKnowledgeBase {
    private val chunks =
        listOf(
            KnowledgeChunk(
                "Ücretsiz plan",
                "AppForge Studio Free hesabında toplam 5 farklı proje oluşturma deneme hakkı vardır. Proje silinse bile kullanılan hak geri gelmez. Aynı package name daha önce hakkı tüketmişse tekrar oluşturmak yeni hak tüketmez.",
                setOf("free", "ücretsiz", "5", "proje", "deneme", "sil", "hak")
            ),
            KnowledgeChunk(
                "Pro planları",
                "Pro tek seferlik satın almadır. Pro Aylık Google Play üzerinden otomatik yenilenen aboneliktir. İkisinde de proje sayısı sınırsızdır ve Free projelerdeki Built with AppForge watermark kaldırılır.",
                setOf("pro", "aylık", "abonelik", "watermark", "sınırsız")
            ),
            KnowledgeChunk(
                "Pro güvenliği",
                "Gerçek Pro yetkisi cihazdaki yerel bir boolean ile açılmaz. Resmi Build Service satın alımı ve gerektiğinde Play Integrity sonucunu doğrular. Pro-only build özellikleri sunucu tarafından korunur.",
                setOf("pro", "güvenlik", "integrity", "sunucu", "mod")
            ),
            KnowledgeChunk(
                "Önizleme",
                "Preview ekranında telefon, büyük telefon ve tablet boyutları ile dikey/yatay görünüm vardır. Console, Network, Performance ve Security sekmeleri build öncesi inceleme içindir.",
                setOf("preview", "önizleme", "console", "network", "performance", "security")
            ),
            KnowledgeChunk(
                "Test Lab",
                "Test Lab başarılı APK/AAB çıktılarının boyutlarını, dosya kategorilerini, en büyük dosyaları ve güvenlik kontrollerini analiz eder. İki build karşılaştırılabilir ve release notes üretilebilir.",
                setOf("test", "lab", "apk", "aab", "boyut", "release", "compare", "karşılaştır")
            ),
            KnowledgeChunk(
                "Proje yedeği",
                "Production Center proje ZIP yedeğini dışa veya içe aktarabilir. Yerel HTML ve ayarlar yedeklenir; keystore parolaları ve API anahtarları yedeğe yazılmaz.",
                setOf("backup", "yedek", "zip", "keystore", "api")
            ),
            KnowledgeChunk(
                "İmzalama",
                "Play Store üretim sürümünde release keystore gerekir. Debug signing test içindir. Play App Signing sertifikası ile upload key aynı şey değildir.",
                setOf("keystore", "signing", "imza", "release", "debug", "play")
            ),
            KnowledgeChunk(
                "Sürümleme",
                "Production Center versionCode +1 ve patch sürüm artırma araçları içerir. Otomatik versionCode açıksa her build başlatıldığında versionCode bir artırılır.",
                setOf("version", "versioncode", "versionname", "sürüm", "patch")
            ),
            KnowledgeChunk(
                "PWA Inspector",
                "PWA Inspector manifest.webmanifest veya uyumlu manifest.json, service worker, start_url, display modu ve ikon sayısını algılar.",
                setOf("pwa", "manifest", "service", "worker", "start_url")
            ),
            KnowledgeChunk(
                "Native Module Center",
                "Native Bridge, kamera, konum, QR/barcode, paylaşım, pano ve titreşim modülleri Production Center içinden yönetilebilir. Remote Native Bridge yalnız güvenilir HTTPS origin ile kullanılmalıdır.",
                setOf("native", "bridge", "kamera", "konum", "qr", "pano", "titreşim")
            ),
            KnowledgeChunk(
                "Build Service",
                "Gerçek APK/AAB compile sonucu resmi Build Service sonucudur. Production Center yerel hazırlık kontrolü yapar. Build Service canlı log, cache, worker ve artifact akışını yönetir.",
                setOf("build", "service", "compile", "worker", "log", "artifact")
            ),
            KnowledgeChunk(
                "Google Play Billing",
                "Pro satın alma tokenı resmi sunucu tarafından Google Play ile doğrulanmadan gerçek Pro yetkisi verilmez. Pro Aylık abonelik süresi server entitlement kaydında tutulur.",
                setOf("billing", "satın", "token", "play", "abonelik")
            ),
            KnowledgeChunk(
                "Yerel AI",
                "AppForge Yerel AI Asistan .litertlm modelini cihazda çalıştırır. AI yanıtı üretmek için soru AppForge sunucusuna gönderilmez. Model APK içine varsayılan olarak gömülmez; kullanıcı model dosyasını içe aktarır.",
                setOf("ai", "yapay", "zeka", "litertlm", "yerel", "offline", "gizlilik")
            )
        )

    fun retrieve(
        question: String,
        maxChunks: Int = 3
    ): List<KnowledgeChunk> {
        val tokens =
            tokenize(
                question
            )

        return chunks
            .map {
                chunk ->
                val haystack =
                    (
                        chunk.title +
                        " " +
                        chunk.text
                    ).lowercase(
                        Locale.ROOT
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
                            key.contains(token) ||
                            token.contains(key)
                        }
                    ) {
                        score +=
                            5
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
                    3
                )
            }
    }

    /*
     * Sık sorulan ve bilgi tabanında cevabı kesin olan AppForge
     * sorularında küçük LLM'i gereksiz yere çalıştırmayız.
     *
     * Sonuç:
     * - cevap anında gelir
     * - Türkçe garanti edilir
     * - hallucination oluşmaz
     */
    fun directTurkishAnswer(
        question: String
    ): String? {

        val tokens =
            tokenize(
                question
            )

        fun has(
            vararg values: String
        ): Boolean =
            values.any {
                it in tokens
            }

        /*
         * FREE vs PRO
         */
        if (
            has(
                "free",
                "ücretsiz"
            ) &&
            has(
                "pro"
            )
        ) {
            return """
                Free planda toplam 5 farklı proje oluşturma deneme hakkı vardır. Bir proje silinse bile kullanılan hak geri gelmez.

                Pro planında proje sayısı sınırsızdır ve Free projelerde bulunan "Built with AppForge" filigranı kaldırılır.

                Pro tek seferlik satın almadır. Pro Aylık ise Google Play üzerinden otomatik yenilenen aboneliktir. Her iki Pro seçeneğinde de proje sınırı kaldırılır.
            """.trimIndent()
        }

        /*
         * PRO / ABONELİK
         */
        if (
            has(
                "pro"
            ) &&
            has(
                "aylık",
                "abonelik",
                "satın",
                "fiyat",
                "plan"
            )
        ) {
            return """
                AppForge'da iki Pro seçeneği vardır:

                Pro: Tek seferlik satın almadır.

                Pro Aylık: Google Play üzerinden otomatik yenilenen aboneliktir ve istenildiğinde iptal edilebilir.

                Her iki seçenekte de proje sayısı sınırsızdır ve "Built with AppForge" filigranı kaldırılır.
            """.trimIndent()
        }

        /*
         * İMZALAMA / KEYSTORE
         */
        if (
            has(
                "keystore",
                "imza",
                "imzalama",
                "signing"
            )
        ) {
            return """
                Play Store için üretim sürümünde Release Keystore kullanmalısın. Debug signing yalnızca test amaçlıdır.

                Play App Signing sertifikası ile uygulamanın Upload Key'i aynı şey değildir. Release keystore dosyanı ve parolalarını güvenli şekilde yedekle.
            """.trimIndent()
        }

        /*
         * BUILD SERVICE
         */
        if (
            has(
                "build",
                "derleme",
                "compile",
                "worker"
            )
        ) {
            return """
                AppForge Build Service gerçek APK ve AAB derlemesini yapar.

                Production ekranındaki ön-kontroller projeyi build öncesinde doğrular. Build Service ise canlı logları, worker işlemlerini, cache sistemini ve oluşan APK/AAB çıktılarını yönetir.
            """.trimIndent()
        }

        /*
         * PREVIEW
         */
        if (
            has(
                "preview",
                "önizleme"
            )
        ) {
            return """
                Önizleme ekranında uygulamayı farklı telefon ve tablet boyutlarında, dikey veya yatay olarak kontrol edebilirsin.

                Console, Network, Performance ve Security bölümleri build almadan önce olası sorunları incelemek için kullanılır.
            """.trimIndent()
        }

        /*
         * TEST LAB
         */
        if (
            has(
                "test"
            ) &&
            has(
                "lab"
            )
        ) {
            return """
                Test Lab, oluşturulan APK ve AAB dosyalarını analiz eder.

                Dosya boyutlarını, en büyük dosyaları ve güvenlik kontrollerini inceleyebilir; iki farklı build'i karşılaştırabilir ve release notes oluşturabilirsin.
            """.trimIndent()
        }

        /*
         * PWA
         */
        if (
            has(
                "pwa",
                "manifest"
            )
        ) {
            return """
                PWA Inspector; manifest.webmanifest veya uygun manifest.json dosyasını, service worker'ı, start_url değerini, display modunu ve ikonları kontrol eder.
            """.trimIndent()
        }

        /*
         * NATIVE BRIDGE
         */
        if (
            has(
                "native",
                "bridge",
                "kamera",
                "konum",
                "qr",
                "barcode",
                "pano",
                "titreşim"
            )
        ) {
            return """
                Native Module Center üzerinden Native Bridge, kamera, konum, QR/Barkod, paylaşım, pano ve titreşim özelliklerini yönetebilirsin.

                Remote Native Bridge yalnızca güvenilir HTTPS originleriyle kullanılmalıdır.
            """.trimIndent()
        }

        /*
         * YEDEK
         */
        if (
            has(
                "yedek",
                "backup"
            )
        ) {
            return """
                Production Center üzerinden proje ZIP yedeğini dışa veya içe aktarabilirsin.

                Yerel HTML dosyaları ve proje ayarları yedeklenir. Keystore parolaları ve API anahtarları güvenlik nedeniyle yedeğe yazılmaz.
            """.trimIndent()
        }

        /*
         * YEREL AI
         */
        if (
            has(
                "ai",
                "yapay",
                "zeka"
            ) &&
            has(
                "yerel",
                "offline",
                "çevrimdışı"
            )
        ) {
            return """
                AppForge Yerel AI Asistan cihazdaki .litertlm modeliyle çalışır.

                Soruların cevap oluşturmak için AppForge Build Service'e veya başka bir bulut LLM API'sine gönderilmez. Model cihaz üzerinde çalışır.
            """.trimIndent()
        }

        return null
    }


    fun promptContext(
        question: String,
        draft: ProjectDraft,
        includeProjectContext: Boolean
    ): String =
        buildString {
            appendLine(
                "APPFORGE YEREL BİLGİ TABANI:"
            )

            retrieve(question)
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
        }

    private fun tokenize(
        input: String
    ): Set<String> =
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
            .split(" ")
            .map {
                it.trim()
            }
            .filter {
                it.length >=
                2
            }
            .toSet()
}
