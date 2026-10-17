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
        return engine == "webview-static" && (draft.sourceMode == SourceMode.URL ||
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
            if (draft.buildOutput.trim().lowercase() !in setOf("aab", "both", "apk+aab"))
                add("Seçilen çıktı birleşimi normal Android motorunu gerektirir.")
        }

        if (draft.fileUpload) add("Dosya yükleme için normal Android dosya seçicisi gerekir.")
        if (draft.clipboardBridge || draft.vibrationBridge) add("Pano ve titreşim için normal Android köprüsü gerekir.")
        if (draft.networkState) add("Ağ durumu izin bildirimi normal Android manifestini gerektirir.")
        if (draft.fullscreen) add("Tam ekran için normal Android pencere ayarları gerekir.")
        if (draft.orientation != "unspecified") add("Ekran yönü için normal Android manifesti gerekir.")
        if (draft.splashEnabled || draft.splashText.isNotBlank()) add("Açılış ekranı için normal Android çalışma zamanı gerekir.")
        if (draft.backgroundColor != "#07101F" || draft.statusBarColor != "#07101F" ||
            draft.navigationBarColor != "#07101F") add("Tema renkleri için normal Android çalışma zamanı gerekir.")
        if (draft.webMixedContentAllowed) add("Karma içerik için normal Android güvenlik kontrolü gerekir.")
        if (draft.signingMode != SigningMode.DEBUG && draft.buildOutput.trim().lowercase() == "apk")
            add("Özel APK imzası için normal Android motoru gerekir.")
        if (!draft.iconUri.isNullOrBlank() || draft.camera || draft.microphone || draft.location ||
            draft.notifications || draft.nfc || draft.deepLinkEnabled || draft.wakeLock ||
            draft.additionalPermissions.isNotEmpty()) add("Seçilen Android özellikleri normal manifest ve çalışma zamanı gerektirir.")
    }

    fun isCandidate(draft: ProjectDraft): Boolean {
        if (!hasFastRouteSource(draft)) return false
        if (normalRouteReasons(draft).isNotEmpty()) return false
        if (!requestsAndroidOnlyOutputs(draft)) return false
        if (!draft.iconUri.isNullOrBlank() || draft.camera || draft.microphone ||
            draft.location || draft.notifications || draft.nfc || draft.deepLinkEnabled ||
            draft.wakeLock || draft.additionalPermissions.isNotEmpty()) return false
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
