package com.appforge.studio.io

import com.appforge.studio.net.RemoteTemplate

/** Recover the 18 existing on-device HTML templates in TemplateProjectFactory. */
internal object LocalTemplateCatalog {
    val templates: List<RemoteTemplate> = listOf(
        entry("interaction-toolkit", "Etkileşim Araçları", "Toast, titreşim ve bildirim örnekleri", "interaction"),
        entry("html-game", "HTML5 Mini Oyun", "Canvas üzerinde oynanabilir oyun başlangıcı", "starters"),
        entry("bootstrap-starter", "Bootstrap Başlangıç", "Mobil uyumlu sayfa başlangıcı", "libraries"),
        entry("admob-starter", "Reklam Başlangıcı", "Reklam yerleşimi örnek arayüzü", "ads"),
        entry("device-api-kit", "Cihaz API Araçları", "Cihaz özellikleri ve WebView API örnekleri", "device"),
        entry("sensor-dashboard", "Sensör Paneli", "Sensör verisi örnek ekranı", "sensors"),
        entry("system-info", "Sistem Bilgileri", "Cihaz ve sistem bilgileri görünümü", "system"),
        entry("native-api-dashboard", "Native API Paneli", "Native API işlemleri için örnek panel", "panels"),
        entry("task-manager", "Görev Yöneticisi", "Görev ve kontrol listesi başlangıcı", "productivity"),
        entry("inventory-panel", "Stok Paneli", "Stok takibi örnek arayüzü", "business"),
        entry("booking-form", "Randevu Formu", "Randevu alma başlangıç ekranı", "business"),
        entry("restaurant-menu", "Restoran Menüsü", "Mobil restoran menüsü", "commerce"),
        entry("event-invitation", "Etkinlik Davetiyesi", "Etkinlik ve davetiye görünümü", "events"),
        entry("visual-designer", "Görsel Tasarımcı", "Tasarım düzeni örnek ekranı", "panels"),
        entry("personnel-tracker", "Personel Takip", "Personel listesi örnek ekranı", "business"),
        entry("qr-menu", "QR Menü", "Dijital menü örneği", "commerce"),
        entry("education-quiz", "Eğitim Testi", "Soru ve cevap başlangıcı", "starters"),
        entry("firebase-login", "Giriş Arayüzü", "Giriş ekranı örneği; Firebase bağlantısı otomatik kurulmaz", "starters")
    )

    private fun entry(slug: String, name: String, description: String, category: String) = RemoteTemplate(
        slug = slug,
        name = name,
        description = description,
        category = category,
        configJson = "{}"
    )
}
