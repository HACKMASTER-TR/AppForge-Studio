package com.appforge.studio.ai

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode

object AppForgeSmartSuggestions {

    fun forProject(
        draft: ProjectDraft,
        maxItems: Int = 6
    ): List<String> {
        val suggestions =
            mutableListOf<String>()

        if (
            draft.sourceMode ==
                SourceMode.URL
        ) {
            suggestions +=
                "Bu web projesinin WebView ayarları güvenli mi?"

            if (
                !draft.webUrl
                    .startsWith(
                        "https://",
                        ignoreCase = true
                    )
            ) {
                suggestions +=
                    "Bu URL için neden HTTPS kullanmalıyım?"
            }

            if (
                draft.remoteBridgeAllowed
            ) {
                suggestions +=
                    "Remote Native Bridge bu proje için güvenli mi?"
            }
        } else {
            suggestions +=
                "Bu HTML/ZIP projesinde hangi özellikleri açmalıyım?"

            suggestions +=
                "Bu proje PWA özellikleri içeriyor mu?"
        }

        if (
            draft.camera ||
            draft.qrScanner
        ) {
            suggestions +=
                "Kamera ve QR izinleri doğru ayarlanmış mı?"
        }

        if (
            draft.location
        ) {
            suggestions +=
                "Konum izni Play Store için nasıl açıklanmalı?"
        }

        if (
            draft.mediaPlayerBridge
        ) {
            suggestions +=
                "Media3 ve arka plan oynatma ayarlarım doğru mu?"
        }

        if (
            draft.billingEnabled
        ) {
            suggestions +=
                "Google Play Billing ayarlarımda eksik var mı?"
        }

        if (
            draft.admobEnabled
        ) {
            suggestions +=
                "AdMob için Play Console'da hangi beyanlar gerekir?"
        }

        if (
            draft.firebaseAnalyticsEnabled ||
            draft.firebaseCrashlyticsEnabled
        ) {
            suggestions +=
                "Firebase yapılandırmam bu projeye uygun mu?"
        }

        if (
            draft.signingMode ==
                SigningMode.DEBUG
        ) {
            suggestions +=
                "Play Store için Release Keystore'a nasıl geçerim?"
        }

        if (
            draft.buildOutput in
                setOf(
                    "aab",
                    "both"
                )
        ) {
            suggestions +=
                "Bu proje Play Store'a hazır mı?"
        } else {
            suggestions +=
                "APK yerine AAB ne zaman seçmeliyim?"
        }

        suggestions +=
            "Projeyi analiz et"

        return suggestions
            .distinct()
            .take(
                maxItems
            )
    }
}
