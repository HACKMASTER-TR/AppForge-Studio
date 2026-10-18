package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode

internal enum class FeatureSupportState { SUPPORTED, MANIFEST_ONLY, UNSUPPORTED, EXTERNAL_CONFIG_REQUIRED, NORMAL_ENGINE_REQUIRED }
internal data class FeatureSupport(val state: FeatureSupportState, val reason: String, val alternative: String? = null) {
    val selectable get() = state in setOf(FeatureSupportState.SUPPORTED, FeatureSupportState.MANIFEST_ONLY, FeatureSupportState.EXTERNAL_CONFIG_REQUIRED)
    val autoEnable get() = state == FeatureSupportState.SUPPORTED
}

/** Product support authority. It never erases imported preferences or grants Android permissions. */
internal object FeatureSupportMatrix {
    fun fastRequested(d: ProjectDraft): Boolean = runCatching { !d.normalHtmlEngine &&
        d.sourceBuildEngine.trim().lowercase().ifBlank { "webview-static" } == "webview-static" &&
        (d.sourceMode == SourceMode.URL || d.sourceTechnology.lowercase() in setOf("html","web-static","webview")) &&
        DeviceBuildCapabilities.requestedOutputs(d.buildOutput).let{ outputs -> outputs.isNotEmpty() && outputs.all { it in setOf(DeviceArtifactKind.APK,DeviceArtifactKind.AAB) }} }.getOrDefault(false)

    fun feature(d: ProjectDraft, key: String): FeatureSupport {
        fun state(s: FeatureSupportState, reason: String, alternative: String? = null) = FeatureSupport(s,reason,alternative)
        val known=setOf("customSigning","additionalPermissions","capture","audioCapture","watchPosition","webMediaUnknown","bluetoothApi","contactsApi","calendarApi","webNfcApi",
            "camera","microphone","location","fileUpload","notifications","networkState","wakeLock","nfc","qrScanner","mediaPlayerBridge","billingEnabled",
            "admobEnabled","firebaseAnalyticsEnabled","firebaseCrashlyticsEnabled","firebaseMessagingEnabled","umpConsentEnabled","javascriptBridge","webJavaScriptEnabled",
            "remoteBridgeAllowed","clipboardBridge","vibrationBridge","shareBridge","normalHtmlEngine","webDomStorageEnabled","webZoomEnabled","webWideViewPortEnabled",
            "webOverviewModeEnabled","webMediaAutoplayEnabled","webMixedContentAllowed","downloads","offlineCache","fullscreen","splashEnabled","deepLinkEnabled")
        if(key !in known)return state(FeatureSupportState.UNSUPPORTED,"Tanınmayan özellik: $key. Doğrulanmış destek kaydı yok; otomatik etkinleştirilemez.")
        val fast=fastRequested(d)
        if(key=="customSigning" && fast && DeviceBuildCapabilities.requestedOutputs(d.buildOutput)==setOf(DeviceArtifactKind.APK))return state(FeatureSupportState.NORMAL_ENGINE_REQUIRED,"Özel APK keystore bu hızlı yolda yok; normal motoru açıkça seçin veya uygun AAB/BOTH kullanın.","Normal Android motoru")
        if(key in setOf("admobEnabled","firebaseAnalyticsEnabled","firebaseCrashlyticsEnabled","firebaseMessagingEnabled","umpConsentEnabled"))
            return state(FeatureSupportState.UNSUPPORTED,"Bu motorda gerçek SDK entegrasyonu yok; kimlik eklemek yeterli değildir.")
        if(key=="additionalPermissions")return state(FeatureSupportState.MANIFEST_ONLY,"Yalnız manifest izni; kişiler/takvim/Bluetooth/biyometri için yerel API eklenmez.")
        if(key in setOf("capture","audioCapture","watchPosition","webMediaUnknown","bluetoothApi","contactsApi","calendarApi","webNfcApi"))return state(FeatureSupportState.UNSUPPORTED,
            "Bu çalışma zamanında $key uygulanmadı. Kaynak bulgusu derlemeden önce çözülmeli; seçimler korunur.")
        if(fast && key in setOf("camera","microphone") && d.sourceMode==SourceMode.LOCAL)
            return state(FeatureSupportState.NORMAL_ENGINE_REQUIRED,"Yerel Fast HTML $key/WebRTC sağlamıyor. Dosya seçici kamera izni gerektirmez.","HTTPS APK veya uygun normal Android kaynak projesi")
        if(key=="billingEnabled")return if(fast)state(FeatureSupportState.EXTERNAL_CONFIG_REQUIRED,
            "Gerçek Billing 9.1.0: Play ürünleri ve HTTPS receipt doğrulama sunucusu gerekir; offline pasiftir.") else state(FeatureSupportState.UNSUPPORTED,"Normal motora Billing SDK enjeksiyonu uygulanmadı.")
        if(key in setOf("qrScanner","mediaPlayerBridge") && !fast)return state(FeatureSupportState.UNSUPPORTED,"Bu köprü yalnız desteklenen Fast HTML kombinasyonunda entegredir.")
        if(key=="mediaPlayerBridge" && d.sourceMode!=SourceMode.LOCAL)return state(FeatureSupportState.UNSUPPORTED,"Arka plan ses yalnız paketlenmiş yerel dosyaları destekler; uzak akış yok.")
        if(key in setOf("qrScanner","mediaPlayerBridge","notifications","nfc","location","clipboardBridge","vibrationBridge") &&
            (!d.javascriptBridge || !d.webJavaScriptEnabled || d.sourceMode==SourceMode.URL && !d.remoteBridgeAllowed && key !in setOf("location")))
            return state(FeatureSupportState.UNSUPPORTED,"Güvenilir JavaScript köprüsü, JS ve uzak içerikte ayrı köprü onayı gerekir.")
        if(!fast && key in setOf("nfc","wakeLock","notifications","networkState"))return state(FeatureSupportState.MANIFEST_ONLY,"Normal motor bu seçim için yalnız izin bildirir; yerel API sağlamaz.")
        return state(FeatureSupportState.SUPPORTED,"Çalışma zamanı entegre; özellik anahtarı, kullanıcı onayı ve Android izinleri geçerlidir.")
    }
    fun selections(d: ProjectDraft): Map<String,Boolean> = linkedMapOf(
        "camera" to d.camera,"microphone" to d.microphone,"location" to d.location,"fileUpload" to d.fileUpload,
        "notifications" to d.notifications,"networkState" to d.networkState,"wakeLock" to d.wakeLock,"nfc" to d.nfc,
        "qrScanner" to d.qrScanner,"mediaPlayerBridge" to d.mediaPlayerBridge,"billingEnabled" to d.billingEnabled,
        "admobEnabled" to d.admobEnabled,"firebaseAnalyticsEnabled" to d.firebaseAnalyticsEnabled,
        "firebaseCrashlyticsEnabled" to d.firebaseCrashlyticsEnabled,"firebaseMessagingEnabled" to d.firebaseMessagingEnabled,"umpConsentEnabled" to d.umpConsentEnabled)
    fun conflicts(d: ProjectDraft): List<Pair<String,String>> = buildList {
        for((key,on) in selections(d))if(on && !feature(d,key).selectable)add(key to feature(d,key).reason)
        for(key in d.requiredCapabilities) {
            if(!feature(d,key).autoEnable)add(key to ("Kaynak gereksinimi: "+feature(d,key).reason))
            else if(selections(d)[key]==false)add(key to "Kaynak $key gerektiriyor fakat seçeneği kapalı; açıkça etkinleştirin veya kaynağı düzeltin.")
        }
        if(fastRequested(d))FastHtmlApkPolicy.normalRouteReasons(d).forEach { add("engine" to (it+" Normal motora sessiz geçiş yapılmaz; motoru açıkça seçin.")) }
    }.distinct()
    fun requireCompatible(d: ProjectDraft) {
        val problems=conflicts(d);require(problems.isEmpty()){problems.joinToString("\n"){it.second}}
    }
    fun recommend(d:ProjectDraft,key:String,detected:Boolean,requirements:Set<String>):Boolean {
        if(!detected || !feature(d,key).autoEnable)return false
        if(key=="camera" && "capture" in requirements || key=="microphone" && "audioCapture" in requirements || key=="nfc" && "webNfcApi" in requirements || key=="location" && "watchPosition" in requirements)return false
        return true
    }
    fun acceptUserChange(before:ProjectDraft,next:ProjectDraft):ProjectDraft {
        fun allowed(key:String,old:Boolean,value:Boolean)=if(!old && value && !feature(next,key).selectable)false else value
        return next.copy(camera=allowed("camera",before.camera,next.camera),microphone=allowed("microphone",before.microphone,next.microphone),
            qrScanner=allowed("qrScanner",before.qrScanner,next.qrScanner),mediaPlayerBridge=allowed("mediaPlayerBridge",before.mediaPlayerBridge,next.mediaPlayerBridge),
            notifications=allowed("notifications",before.notifications,next.notifications),nfc=allowed("nfc",before.nfc,next.nfc),
            admobEnabled=allowed("admobEnabled",before.admobEnabled,next.admobEnabled),billingEnabled=allowed("billingEnabled",before.billingEnabled,next.billingEnabled),
            firebaseAnalyticsEnabled=allowed("firebaseAnalyticsEnabled",before.firebaseAnalyticsEnabled,next.firebaseAnalyticsEnabled),
            firebaseCrashlyticsEnabled=allowed("firebaseCrashlyticsEnabled",before.firebaseCrashlyticsEnabled,next.firebaseCrashlyticsEnabled),
            firebaseMessagingEnabled=allowed("firebaseMessagingEnabled",before.firebaseMessagingEnabled,next.firebaseMessagingEnabled),umpConsentEnabled=allowed("umpConsentEnabled",before.umpConsentEnabled,next.umpConsentEnabled))
    }
    /** This UI name adapter maps presentation to keys only; support is always evaluated above. */
    fun keyForLabel(label: String): String? {
        val t=label.lowercase(java.util.Locale.ROOT)
        return when {
            "crashlytics" in t -> "firebaseCrashlyticsEnabled"; "analytics" in t -> "firebaseAnalyticsEnabled"
            "messaging" in t -> "firebaseMessagingEnabled"; "admob" in t -> "admobEnabled"; "billing" in t -> "billingEnabled"
            "ump" in t || "consent" in t -> "umpConsentEnabled"; "qr" in t || "barkod" in t -> "qrScanner"
            "mikrofon" in t -> "microphone"; "kamera" in t -> "camera"; "konum" in t -> "location"
            "arka plan ses" in t -> "mediaPlayerBridge"; "nfc" in t -> "nfc"; "pano" in t -> "clipboardBridge"
            "titreşim" in t -> "vibrationBridge"; "bildirim" in t -> "notifications"
            "bluetooth" in t || "takvim" in t || "kişiler" in t || "biyometri" in t || "kesin alarm" in t -> "additionalPermissions"
            else -> null
        }
    }
}
