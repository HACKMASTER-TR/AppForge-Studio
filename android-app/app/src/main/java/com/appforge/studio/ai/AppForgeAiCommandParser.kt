package com.appforge.studio.ai

import com.appforge.studio.model.ProjectDraft
import java.util.Locale

data class AiDraftCommand(
    val draft: ProjectDraft,
    val answer: String,
    val destination: AssistantDestination
)

/**
 * Küçük ve açık proje komutlarını LLM başlatmadan cihaz üzerinde uygular.
 * Yalnız allowlist'teki non-secret alanlar değiştirilebilir.
 */
object AppForgeAiCommandParser {
    fun parse(question: String, current: ProjectDraft): AiDraftCommand? {
        val text = question.lowercase(Locale.forLanguageTag("tr-TR"))
        val isCommand = listOf(" aç", "aç ", " kapat", "kapat ", " yap", "yap ", " seç", "seç ", "uygula").any(text::contains)
        if (!isCommand) return null

        theme(text, current)?.let { return it }
        output(text, current)?.let { return it }
        permission(text, current)?.let { return it }
        security(text, current)?.let { return it }
        return null
    }

    private fun theme(text: String, current: ProjectDraft): AiDraftCommand? {
        val colors = when {
            "oled" in text -> listOf("#7C5CFF", "#000000", "#000000", "#000000")
            "açık tema" in text || "temayı açık" in text -> listOf("#536DFE", "#F7F8FC", "#F7F8FC", "#F7F8FC")
            "koyu tema" in text || "temayı koyu" in text -> listOf("#6B7CFF", "#07101F", "#07101F", "#07101F")
            else -> return null
        }
        return AiDraftCommand(
            current.copy(primaryColor = colors[0], backgroundColor = colors[1], statusBarColor = colors[2], navigationBarColor = colors[3]),
            "Tema proje ayarlarına uygulandı. İstersen Görünüm adımında renkleri ince ayarlayabilirsin.",
            AssistantDestination.APPEARANCE
        )
    }

    private fun output(text: String, current: ProjectDraft): AiDraftCommand? {
        val output = when {
            "apk ve aab" in text || "apk+aab" in text || "ikisini" in text -> "both"
            Regex("\\baab\\b").containsMatchIn(text) -> "aab"
            Regex("\\bapk\\b").containsMatchIn(text) -> "apk"
            else -> return null
        }
        if ("seç" !in text && "yap" !in text) return null
        return AiDraftCommand(current.copy(buildOutput = output), "Build çıktısı ${output.uppercase()} olarak ayarlandı.", AssistantDestination.BUILD_SETTINGS)
    }

    private fun permission(text: String, current: ProjectDraft): AiDraftCommand? {
        val enabled = when {
            "kapat" in text || "devre dışı" in text -> false
            "aç" in text || "etkinleştir" in text -> true
            else -> return null
        }

        val updated = when {
            "kamera" in text -> current.copy(camera = enabled)
            "mikrofon" in text -> current.copy(microphone = enabled, fileUpload = current.fileUpload || enabled)
            "konum" in text && "arka plan" !in text -> current.copy(location = enabled)
            "bildirim" in text -> current.copy(notifications = enabled)
            "nfc" in text -> current.copy(nfc = enabled)
            "wake lock" in text || "uyanık tut" in text -> current.copy(wakeLock = enabled)
            else -> {
                val key = additionalPermissionKey(text) ?: return null
                current.copy(additionalPermissions = if (enabled) current.additionalPermissions + key else current.additionalPermissions - key)
            }
        }
        return AiDraftCommand(updated, "İzin ayarı güncellendi. Hassas izinleri yalnız gerçek özellik ihtiyacında kullan.", AssistantDestination.PERMISSIONS)
    }

    private fun additionalPermissionKey(text: String): String? = when {
        "bluetooth" in text || "yakın cihaz" in text -> "BLUETOOTH"
        "biyometr" in text || "parmak izi" in text -> "BIOMETRIC"
        "takvim" in text -> "CALENDAR"
        "kişi" in text || "rehber" in text -> "CONTACTS"
        "arka plan konum" in text -> "BACKGROUND_LOCATION"
        "kesin alarm" in text -> "EXACT_ALARM"
        "fotoğraf eriş" in text || "galeri görsel" in text -> "MEDIA_IMAGES"
        "video eriş" in text || "galeri video" in text -> "MEDIA_VIDEO"
        "aktivite" in text || "adım say" in text -> "ACTIVITY_RECOGNITION"
        else -> null
    }

    private fun security(text: String, current: ProjectDraft): AiDraftCommand? {
        if ("güvenli ayar" !in text) return null
        val updated = current.copy(
            versionCode = current.versionCode.coerceAtLeast(1),
            versionName = current.versionName.ifBlank { "1.0.0" },
            webMixedContentAllowed = false,
            remoteBridgeAllowed = false
        )
        return AiDraftCommand(updated, "Güvenli varsayılanlar uygulandı: mixed content ve uzak bridge kapatıldı, sürüm değerleri doğrulandı.", AssistantDestination.PRODUCTION)
    }
}
