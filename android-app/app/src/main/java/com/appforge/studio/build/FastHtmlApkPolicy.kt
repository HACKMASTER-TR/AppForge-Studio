package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode

/** Fast HTML/HTTPS Android-only route (APK, local AAB and APK+AAB).
 * Any Windows output (including mixed Android+Windows) uses the normal build engine.
 */
internal object FastHtmlApkPolicy {
    // Reuse this output gate in UI preflight, DeviceBuildEngine and cleanup.
    // Never allow a Windows Portable/Native EXE to be swallowed by Fast HTML.
    private fun requestsAndroidOnlyOutputs(draft: ProjectDraft): Boolean {
        val outputs = DeviceBuildCapabilities.requestedOutputs(draft.buildOutput)
        return outputs.isNotEmpty() && outputs.all { kind ->
            kind == DeviceArtifactKind.APK || kind == DeviceArtifactKind.AAB
        }
    }

    /** Routing explanations apply only when this source/output could otherwise use Tiny. */
    private fun hasFastRouteSource(draft: ProjectDraft): Boolean {
        val engine = draft.sourceBuildEngine.trim().lowercase().ifBlank { "webview-static" }
        val tech = draft.sourceTechnology.trim().lowercase()
        return !draft.normalHtmlEngine && engine == "webview-static" && (draft.sourceMode == SourceMode.URL ||
            tech in setOf("html", "web-static", "webview") &&
            (draft.startPage?.endsWith(".html", true) == true || draft.importedFolder != null))
    }

    /** Every selected capability absent from Tiny must use the normal prereq-checked engine. */
    fun normalRouteReasons(draft: ProjectDraft): List<String> = buildList {
        if (!requestsAndroidOnlyOutputs(draft) || !hasFastRouteSource(draft)) return@buildList
        val outputs = DeviceBuildCapabilities.requestedOutputs(draft.buildOutput)
        if (DeviceArtifactKind.AAB in outputs) {
            if (draft.sourceMode == SourceMode.URL) add("URL kaynağından AAB için normal Android motoru gerekir.")
            if (draft.minSdk != 26 || draft.targetSdk != 37) add("Seçilen AAB SDK aralığı normal Android motorunu gerektirir.")
            if (draft.buildOutput.trim().lowercase() !in setOf("aab", "both", "apk+aab", "android", ""))
                add("Seçilen çıktı birleşimi normal Android motorunu gerektirir.")
        }

        if (draft.billingEnabled && (!draft.javascriptBridge || !draft.webJavaScriptEnabled || draft.sourceMode == SourceMode.URL && !draft.remoteBridgeAllowed))
            add("Play Billing için güvenilir JavaScript köprüsü gerekir.")
        if (draft.mediaPlayerBridge && (draft.sourceMode != SourceMode.LOCAL || !draft.javascriptBridge || !draft.webJavaScriptEnabled))
            add("Arka plan ses köprüsü için yerel paketlenmiş medya ve JavaScript köprüsü gerekir; uzak akış desteklenmez.")
        if (draft.location && draft.sourceMode == SourceMode.LOCAL && (!draft.javascriptBridge || !draft.webJavaScriptEnabled))
            add("Yerel HTML konumu için güvenilir JavaScript köprüsü gerekir.")
        if (draft.qrScanner && (!draft.javascriptBridge || !draft.webJavaScriptEnabled ||
                draft.sourceMode == SourceMode.URL && !draft.remoteBridgeAllowed)) add("QR tarayıcı için güvenilir JavaScript köprüsü gerekir.")
        if (draft.nfc && (!draft.javascriptBridge || !draft.webJavaScriptEnabled ||
                draft.sourceMode == SourceMode.URL && !draft.remoteBridgeAllowed)) add("NFC için güvenilir JavaScript köprüsü gerekir.")
        if (draft.notifications && (!draft.javascriptBridge || !draft.webJavaScriptEnabled ||
                draft.sourceMode == SourceMode.URL && !draft.remoteBridgeAllowed)) add("Yerel bildirim için JavaScript köprüsü gerekir.")
        if (draft.signingMode != SigningMode.DEBUG && draft.buildOutput.trim().lowercase() == "apk")
            add("Özel APK imzası için normal Android motoru gerekir.")
        if ((draft.sourceMode != SourceMode.URL && (draft.camera || draft.microphone))) add("Seçilen Android özellikleri normal manifest ve çalışma zamanı gerektirir.")
    }

    /** Only these declarations exist in the pinned V2 shell. No invented native APIs. */
    fun applicationCategory(draft: ProjectDraft): Int = when (draft.appCategory) {
        "game" -> 0
        "auto", "none" -> -1
        else -> error("Unsupported application category")
    }

    fun templatePermissions(draft: ProjectDraft): Set<String> = buildSet {
        add("android.permission.INTERNET")
        if (WebviewProjectSettings.needsLocalNetwork(draft)) add("android.permission.ACCESS_LOCAL_NETWORK")
        addAll(WebviewProjectSettings.additionalPermissions(draft).map { "android.permission.$it" })
        if (draft.nfc) add("android.permission.NFC")
        if (draft.camera || draft.qrScanner) add("android.permission.CAMERA")
        if (draft.microphone) add("android.permission.RECORD_AUDIO")
        if (draft.location) {
            add("android.permission.ACCESS_COARSE_LOCATION")
            if (draft.sourceMode == SourceMode.LOCAL) add("android.permission.ACCESS_FINE_LOCATION")
        }
        if (draft.wakeLock) add("android.permission.WAKE_LOCK")
        if (draft.billingEnabled) {
            add("com.android.vending.BILLING")
            add("${draft.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        }
        if (draft.networkState || draft.billingEnabled) add("android.permission.ACCESS_NETWORK_STATE")
        if (draft.mediaPlayerBridge) {
            add("android.permission.FOREGROUND_SERVICE")
            add("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
            add("android.permission.WAKE_LOCK")
        }
        if (draft.notifications || draft.mediaPlayerBridge) add("android.permission.POST_NOTIFICATIONS")
        if (draft.javascriptBridge && draft.vibrationBridge) add("android.permission.VIBRATE")
    }

    fun isCandidate(draft: ProjectDraft): Boolean {
        if (!hasFastRouteSource(draft)) return false
        if (normalRouteReasons(draft).isNotEmpty()) return false
        if (!requestsAndroidOnlyOutputs(draft)) return false
        if ((draft.sourceMode != SourceMode.URL && (draft.camera || draft.microphone))) return false
        return true
    }

    fun requireSupported(draft: ProjectDraft) {
        WebviewProjectSettings.validate(draft)
        require(isCandidate(draft)) { "Hızlı APK yalnız .html ve URL projelerinde kullanılabilir." }
        require(DeviceBuildCapabilities.requestedOutputs(draft.buildOutput) == setOf(DeviceArtifactKind.APK)) {
            "Hızlı HTML/URL motoru yalnız APK üretir. Lütfen çıktı türünü APK olarak seçin."
        }
        require(draft.signingMode == SigningMode.DEBUG) {
            "Hızlı HTML/URL APK v1 cihazda kalıcı anahtarla imzalanır; özel keystore henüz desteklenmiyor."
        }
        require(draft.minSdk in 26..37 && draft.targetSdk in draft.minSdk..37)
        require(draft.packageName.matches(Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")))
        require(draft.appName.isNotBlank() && draft.appName.length <= 100)
        if (draft.sourceMode == SourceMode.URL) {
            val address = WebviewProjectSettings.webSourceUri(draft.webUrl)
            require(address != null) {
                "Hızlı URL APK için geçerli HTTP veya HTTPS adresi gerekiyor."
            }
            require(address!!.userInfo.isNullOrBlank()) { "URL içine kullanıcı adı/parola koymayın." }
        } else {
            require(!draft.importedFolder.isNullOrBlank()) { "Yerel HTML proje klasörü bulunamadı." }
        }
        require((draft.sourceMode == SourceMode.URL || !draft.camera && !draft.microphone)) {
            "Hızlı HTML/URL APK v1, ek Android izinlerini ve derin bağlantıları desteklemez."
        }
    }
}
