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

    /** Every selected capability absent from Tiny must use the normal prereq-checked engine. */
    fun normalRouteReasons(draft: ProjectDraft): List<String> = buildList {
        if (draft.fileUpload) add("fileUpload requires normal file chooser")
        if (draft.clipboardBridge || draft.vibrationBridge) add("clipboard/haptics require normal native bridge")
        if (draft.networkState) add("network state permission requires normal manifest")
        if (draft.fullscreen) add("fullscreen requires normal window")
        if (draft.orientation != "unspecified") add("orientation requires normal manifest")
        if (draft.splashEnabled || draft.splashText.isNotBlank()) add("splash requires normal runtime")
        if (draft.backgroundColor != "#07101F" || draft.statusBarColor != "#07101F" ||
            draft.navigationBarColor != "#07101F") add("theme colors require normal runtime")
        if (draft.webMixedContentAllowed) add("mixed content requires normal security preflight")
        if (draft.signingMode != SigningMode.DEBUG && draft.buildOutput.trim().lowercase() == "apk")
            add("custom APK signing requires normal engine")
        if (!draft.iconUri.isNullOrBlank() || draft.camera || draft.microphone || draft.location ||
            draft.notifications || draft.nfc || draft.deepLinkEnabled || draft.wakeLock ||
            draft.additionalPermissions.isNotEmpty()) add("Android capabilities require normal manifest/runtime")
    }

    fun isCandidate(draft: ProjectDraft): Boolean {
        val engine = draft.sourceBuildEngine.trim().lowercase().ifBlank { "webview-static" }
        if (engine != "webview-static") return false
        if (normalRouteReasons(draft).isNotEmpty()) return false
        if (!requestsAndroidOnlyOutputs(draft)) return false
        if (!draft.iconUri.isNullOrBlank() || draft.camera || draft.microphone ||
            draft.location || draft.notifications || draft.nfc || draft.deepLinkEnabled ||
            draft.wakeLock || draft.additionalPermissions.isNotEmpty()) return false
        val tech = draft.sourceTechnology.trim().lowercase()
        return draft.sourceMode == SourceMode.URL ||
            tech in setOf("html", "web-static", "webview") &&
            (draft.startPage?.endsWith(".html", true) == true ||
                draft.importedFolder != null)
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
        require(draft.iconUri.isNullOrBlank()) {
            "Hızlı HTML/URL APK v1 özel ikon işleyemiyor; önce varsayılan simgeyi seçin."
        }
        require(draft.minSdk in 26..37 && draft.targetSdk in draft.minSdk..37)
        require(draft.packageName.matches(Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")))
        require(draft.appName.isNotBlank() && draft.appName.length <= 100)
        if (draft.sourceMode == SourceMode.URL) {
            val address = java.net.URI(draft.webUrl)
            require(address.scheme.equals("https", true) && !address.host.isNullOrBlank() && draft.webUrl.length <= 2048) {
                "Hızlı URL APK için geçerli HTTPS adresi gerekiyor."
            }
            require(address.userInfo.isNullOrBlank()) { "URL içine kullanıcı adı/parola koymayın." }
        } else {
            require(!draft.importedFolder.isNullOrBlank()) { "Yerel HTML proje klasörü bulunamadı." }
        }
        require(!draft.camera && !draft.microphone && !draft.location && !draft.notifications &&
            !draft.nfc && !draft.deepLinkEnabled && !draft.wakeLock && draft.additionalPermissions.isEmpty()) {
            "Hızlı HTML/URL APK v1, ek Android izinlerini ve derin bağlantıları desteklemez."
        }
    }
}
