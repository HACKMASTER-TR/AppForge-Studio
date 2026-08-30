package com.appforge.studio.i18n

data class LanguageOption(
    val code: String,
    val nativeLabel: String,
    val englishLabel: String
)

object StudioI18n {
    val languages = listOf(
        LanguageOption("system", "Sistem Varsayılanı", "System Default"),
        LanguageOption("tr", "Türkçe", "Turkish"),
        LanguageOption("en", "English", "English"),
        LanguageOption("de", "Deutsch", "German"),
        LanguageOption("ar", "العربية", "Arabic")
    )

    private val strings = mapOf(
        "settings" to mapOf(
            "tr" to "Ayarlar",
            "en" to "Settings",
            "de" to "Einstellungen",
            "ar" to "الإعدادات"
        ),
        "language" to mapOf(
            "tr" to "Dil",
            "en" to "Language",
            "de" to "Sprache",
            "ar" to "اللغة"
        ),
        "system_default" to mapOf(
            "tr" to "Sistem varsayılanı",
            "en" to "System default",
            "de" to "Systemstandard",
            "ar" to "لغة النظام"
        ),
        "keystore_manager" to mapOf(
            "tr" to "Keystore Yöneticisi",
            "en" to "Keystore Manager",
            "de" to "Keystore-Manager",
            "ar" to "مدير Keystore"
        ),
        "pro" to mapOf(
            "tr" to "Pro",
            "en" to "Pro",
            "de" to "Pro",
            "ar" to "برو"
        ),
        "active" to mapOf(
            "tr" to "Aktif",
            "en" to "Active",
            "de" to "Aktiv",
            "ar" to "نشط"
        ),
        "how_to_use" to mapOf(
            "tr" to "Nasıl Kullanılır",
            "en" to "How to Use",
            "de" to "Verwendung",
            "ar" to "كيفية الاستخدام"
        ),
        "play_guide" to mapOf(
            "tr" to "Play Store Rehberi",
            "en" to "Play Store Guide",
            "de" to "Play Store Anleitung",
            "ar" to "دليل متجر Play"
        ),
        "legal" to mapOf(
            "tr" to "Yasal",
            "en" to "Legal",
            "de" to "Rechtliches",
            "ar" to "قانوني"
        ),
        "send_feedback" to mapOf(
            "tr" to "Geri Bildirim Gönder",
            "en" to "Send Feedback",
            "de" to "Feedback senden",
            "ar" to "إرسال ملاحظات"
        ),
        "clear_cache" to mapOf(
            "tr" to "Önbelleği Temizle",
            "en" to "Clear Cache",
            "de" to "Cache leeren",
            "ar" to "مسح التخزين المؤقت"
        ),
        "legal_title" to mapOf(
            "tr" to "Yasal Merkez",
            "en" to "Legal Center",
            "de" to "Rechtszentrum",
            "ar" to "المركز القانوني"
        ),
        "privacy_policy" to mapOf(
            "tr" to "Gizlilik Politikası",
            "en" to "Privacy Policy",
            "de" to "Datenschutz",
            "ar" to "سياسة الخصوصية"
        ),
        "terms_of_use" to mapOf(
            "tr" to "Kullanım Koşulları",
            "en" to "Terms of Use",
            "de" to "Nutzungsbedingungen",
            "ar" to "شروط الاستخدام"
        ),
        "how_to_title" to mapOf(
            "tr" to "Nasıl Kullanılır",
            "en" to "How It Works",
            "de" to "So funktioniert es",
            "ar" to "كيف يعمل"
        ),
        "play_publish_title" to mapOf(
            "tr" to "Play Store Yayınlama Rehberi",
            "en" to "Play Store Publishing Guide",
            "de" to "Play Store Veröffentlichungsleitfaden",
            "ar" to "دليل النشر على Play Store"
        ),
        "pro_title" to mapOf(
            "tr" to "Pro'ya Yükselt",
            "en" to "Upgrade to Pro",
            "de" to "Auf Pro upgraden",
            "ar" to "الترقية إلى Pro"
        ),
        "choose_plan" to mapOf(
            "tr" to "İhtiyacına uygun planı seç",
            "en" to "Choose the plan that fits you",
            "de" to "Wähle den passenden Plan",
            "ar" to "اختر الخطة المناسبة لك"
        ),
        "pro_lifetime" to mapOf(
            "tr" to "Pro",
            "en" to "Pro",
            "de" to "Pro",
            "ar" to "Pro"
        ),
        "pro_lifetime_desc" to mapOf(
            "tr" to "Tek seferlik satın alma",
            "en" to "One-time purchase",
            "de" to "Einmaliger Kauf",
            "ar" to "شراء لمرة واحدة"
        ),
        "pro_monthly" to mapOf(
            "tr" to "Pro Aylık",
            "en" to "Pro Monthly",
            "de" to "Pro Monatlich",
            "ar" to "Pro شهري"
        ),
        "pro_monthly_desc" to mapOf(
            "tr" to "Otomatik yenilenen abonelik, istediğin zaman iptal et",
            "en" to "Auto-renewing subscription, cancel anytime",
            "de" to "Automatisch verlängerndes Abo, jederzeit kündbar",
            "ar" to "اشتراك يتجدد تلقائيًا، ويمكن إلغاؤه في أي وقت"
        ),
        "buy_once" to mapOf(
            "tr" to "TEK SEFERLİK PRO AL",
            "en" to "BUY PRO ONCE",
            "de" to "PRO EINMALIG KAUFEN",
            "ar" to "شراء PRO مرة واحدة"
        ),
        "subscribe_monthly" to mapOf(
            "tr" to "PRO AYLIK'A ABONE OL",
            "en" to "SUBSCRIBE TO PRO MONTHLY",
            "de" to "PRO MONATLICH ABONNIEREN",
            "ar" to "الاشتراك في PRO الشهري"
        ),
        "back" to mapOf(
            "tr" to "Geri",
            "en" to "Back",
            "de" to "Zurück",
            "ar" to "رجوع"
        ),
        "choose_language" to mapOf(
            "tr" to "Dil Seç",
            "en" to "Choose Language",
            "de" to "Sprache wählen",
            "ar" to "اختر اللغة"
        ),
        "settings_subtitle" to mapOf(
            "tr" to "Uygulama araçları, destek ve hesap tercihleri",
            "en" to "App tools, support, and account preferences",
            "de" to "App-Tools, Support und Kontoeinstellungen",
            "ar" to "أدوات التطبيق والدعم وتفضيلات الحساب"
        ),
        "cache_cleared" to mapOf(
            "tr" to "Önbellek temizlendi",
            "en" to "Cache cleared",
            "de" to "Cache geleert",
            "ar" to "تم مسح التخزين المؤقت"
        ),
        "find_backups" to mapOf(
            "tr" to "Cihazdaki yedekleri bul",
            "en" to "Find device backups",
            "de" to "Backups auf dem Gerät finden",
            "ar" to "العثور على النسخ الاحتياطية"
        ),
        "import_keystore" to mapOf(
            "tr" to "Keystore İçe Aktar",
            "en" to "Import Keystore",
            "de" to "Keystore importieren",
            "ar" to "استيراد Keystore"
        ),
        "copy_sha1" to mapOf(
            "tr" to "SHA-1 kopyala",
            "en" to "Copy SHA-1",
            "de" to "SHA-1 kopieren",
            "ar" to "نسخ SHA-1"
        ),
        "copy_sha256" to mapOf(
            "tr" to "SHA-256 kopyala",
            "en" to "Copy SHA-256",
            "de" to "SHA-256 kopieren",
            "ar" to "نسخ SHA-256"
        ),
        "delete" to mapOf(
            "tr" to "Sil",
            "en" to "Delete",
            "de" to "Löschen",
            "ar" to "حذف"
        ),
        "preview" to mapOf(
            "tr" to "Uygulama Önizleme",
            "en" to "App Preview",
            "de" to "App-Vorschau",
            "ar" to "معاينة التطبيق"
        ),
        "production_center" to mapOf(
            "tr" to "Üretim Merkezi",
            "en" to "Production Center",
            "de" to "Production Center",
            "ar" to "مركز الإنتاج"
        ),
        "appforge_check" to mapOf(
            "tr" to "AppForge Kontrol",
            "en" to "AppForge Check",
            "de" to "AppForge Check",
            "ar" to "AppForge Check"
        ),
        "export_backup" to mapOf(
            "tr" to "Proje Yedeğini Dışa Aktar",
            "en" to "Export Project Backup",
            "de" to "Projekt-Backup exportieren",
            "ar" to "تصدير نسخة المشروع"
        ),
        "import_backup" to mapOf(
            "tr" to "Proje Yedeğini İçe Aktar",
            "en" to "Import Project Backup",
            "de" to "Projekt-Backup importieren",
            "ar" to "استيراد نسخة المشروع"
        ),
        "auto_version" to mapOf(
            "tr" to "Build'de versionCode otomatik artır",
            "en" to "Auto-increment versionCode on build",
            "de" to "versionCode beim Build automatisch erhöhen",
            "ar" to "زيادة versionCode تلقائيًا عند البناء"
        ),
        "dashboard" to mapOf(
            "tr" to "Kontrol Paneli",
            "en" to "Dashboard",
            "de" to "Dashboard",
            "ar" to "لوحة المعلومات"
        ),
        "test_lab" to mapOf(
            "tr" to "Test Laboratuvarı",
            "en" to "Test Lab",
            "de" to "Test Lab",
            "ar" to "مختبر الاختبار"
        ),
        "security_center" to mapOf(
            "tr" to "Güvenlik Merkezi",
            "en" to "Security Center",
            "de" to "Sicherheitszentrum",
            "ar" to "مركز الأمان"
        ),
        "network_inspector" to mapOf(
            "tr" to "Ağ Denetleyicisi",
            "en" to "Network Inspector",
            "de" to "Netzwerk-Inspektor",
            "ar" to "مراقب الشبكة"
        ),
        "local_ai" to mapOf(
            "tr" to "Yerel AI Asistan",
            "en" to "Local AI Assistant",
            "de" to "Lokaler KI-Assistent",
            "ar" to "مساعد الذكاء الاصطناعي المحلي"
        ),
        "import_ai_model" to mapOf(
            "tr" to "Yerel Model İçe Aktar",
            "en" to "Import Local Model",
            "de" to "Lokales Modell importieren",
            "ar" to "استيراد نموذج محلي"
        ),
        "ask_ai" to mapOf(
            "tr" to "AppForge'a bir şey sor...",
            "en" to "Ask AppForge something...",
            "de" to "Frag AppForge etwas...",
            "ar" to "اسأل AppForge..."
        )
    )

    fun t(languageCode: String, key: String): String {
        val normalized =
            if (languageCode == "system") {
                val systemLanguage =
                    java.util.Locale.getDefault()
                        .language
                        .lowercase(java.util.Locale.ROOT)

                if (systemLanguage in setOf("tr", "en", "de", "ar")) {
                    systemLanguage
                } else {
                    "en"
                }
            } else {
                languageCode
            }

        return strings[key]?.get(normalized)
            ?: strings[key]?.get("tr")
            ?: key
    }
}
