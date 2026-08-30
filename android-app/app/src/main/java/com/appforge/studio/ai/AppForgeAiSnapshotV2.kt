package com.appforge.studio.ai

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode

object AppForgeAiSnapshotV2 {
    fun build(
        draft: ProjectDraft,
        runtime: AssistantRuntimeContext?
    ): String = buildString {
        appendLine("APPFORGE STUDIO COPILOT V2 - GÜVENLİ PROJE BAĞLAMI")
        appendLine("- Uygulama: ${draft.appName.ifBlank { "Adsız" }}")
        appendLine("- Paket: ${draft.packageName}")
        appendLine("- Sürüm: ${draft.versionName} (${draft.versionCode})")
        appendLine("- Otomatik versionCode: ${draft.autoVersionCode}")
        appendLine("- Kaynak modu: ${draft.sourceMode}")
        appendLine("- Kaynak teknolojisi: ${draft.sourceTechnologyLabel}")
        appendLine("- Derleme motoru sınıfı: ${draft.sourceBuildEngine}")
        appendLine("- Kaynak derlemeye hazır: ${draft.sourceBuildReady}")
        appendLine(
            "- Kaynak: " +
                if (draft.sourceMode == SourceMode.URL) {
                    draft.webUrl.ifBlank { "URL girilmedi" }
                } else {
                    if (draft.importedFolder.isNullOrBlank()) "Yerel kaynak seçilmedi"
                    else "Yerel proje seçili"
                }
        )
        appendLine("- Çıktı tercihi: ${draft.buildOutput}")
        appendLine("- Android SDK: min=${draft.minSdk}, target=${draft.targetSdk}")
        appendLine("- Ekran yönü: ${draft.orientation}")
        appendLine("- Uygulama kategorisi: ${draft.appCategory}")
        appendLine("- Splash: ${draft.splashEnabled}")
        appendLine("- Özel ikon seçili: ${!draft.iconUri.isNullOrBlank()}")
        appendLine(
            "- İzinler: kamera=${draft.camera}, mikrofon=${draft.microphone}, " +
                "konum=${draft.location}, bildirim=${draft.notifications}, ağ=${draft.networkState}, " +
                "wakeLock=${draft.wakeLock}, nfc=${draft.nfc}"
        )
        appendLine(
            "- WebView: JavaScript=${draft.webJavaScriptEnabled}, DOM=${draft.webDomStorageEnabled}, " +
                "zoom=${draft.webZoomEnabled}, viewport=${draft.webWideViewPortEnabled}, " +
                "offline=${draft.offlineCache}, mixedContent=${draft.webMixedContentAllowed}"
        )
        appendLine(
            "- Native Bridge: ana=${draft.javascriptBridge}, uzak=${draft.remoteBridgeAllowed}, " +
                "paylaş=${draft.shareBridge}, pano=${draft.clipboardBridge}, titreşim=${draft.vibrationBridge}, " +
                "Media3=${draft.mediaPlayerBridge}, QR=${draft.qrScanner}"
        )
        appendLine(
            "- Servisler: AdMob=${draft.admobEnabled}, UMP=${draft.umpConsentEnabled}, Billing=${draft.billingEnabled}, " +
                "Analytics=${draft.firebaseAnalyticsEnabled}, Crashlytics=${draft.firebaseCrashlyticsEnabled}, " +
                "FCM=${draft.firebaseMessagingEnabled}"
        )
        appendLine(
            "- Deep Link: aktif=${draft.deepLinkEnabled}, scheme=${draft.deepLinkScheme}, " +
                "host=${draft.deepLinkHost.ifBlank { "yok" }}"
        )
        appendLine("- İmzalama: ${draft.signingMode}; keystore seçili=${!draft.keystoreUri.isNullOrBlank()}")

        if (runtime != null) {
            appendLine("ANLIK STUDIO DURUMU")
            appendLine("- Çalışma alanı: ${runtime.workspace}")
            appendLine("- Builder adımı: ${runtime.builderStep}/10")
            appendLine("- Derleme durumu: ${runtime.buildStatus}")
            appendLine("- Derleme ilerlemesi: %${runtime.buildProgress}")
            appendLine("- Oturum açık: ${runtime.signedIn}")
            appendLine("- Pro aktif: ${runtime.proActive}")
            appendLine("- Hazır çıktılar: APK=${runtime.hasApk}, AAB=${runtime.hasAab}, EXE=${runtime.hasExe}")
            runtime.buildDiagnosis
                ?.takeIf { it.isNotBlank() }
                ?.let { appendLine("- Güvenli hata tanısı: $it") }
        }

        appendLine(
            "KURAL: Gizli kimlik bilgisi isteme veya uydurma. API anahtarı, oturum tokenı, " +
                "keystore parolası ve teknik UUID kullanıcıya gösterilmez."
        )
    }.trim()
}
