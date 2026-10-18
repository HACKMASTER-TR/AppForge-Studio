package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft

/** Manifest declarations do not enable native APIs or bypass runtime consent. */
internal object WebviewProjectSettings {
    private val permissionAliases = linkedMapOf(
        "BLUETOOTH" to listOf("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT"),
        "BIOMETRIC" to listOf("USE_BIOMETRIC"),
        "CALENDAR" to listOf("READ_CALENDAR", "WRITE_CALENDAR"),
        "CONTACTS" to listOf("READ_CONTACTS", "WRITE_CONTACTS"),
        "BACKGROUND_LOCATION" to listOf("ACCESS_BACKGROUND_LOCATION"),
        "EXACT_ALARM" to listOf("SCHEDULE_EXACT_ALARM"),
        "MEDIA_IMAGES" to listOf("READ_MEDIA_IMAGES"),
        "MEDIA_VIDEO" to listOf("READ_MEDIA_VIDEO"),
        "ACTIVITY_RECOGNITION" to listOf("ACTIVITY_RECOGNITION")
    )
    private val additionalPermissionAllowlist = permissionAliases.values.flatten().toSet()

    // Exact spelling is intentional: never repair malformed or dangerous imported input.
    private fun expandPermission(value: String): List<String>? {
        permissionAliases[value]?.let { return it }
        val canonical = value.removePrefix("android.permission.")
        return if (canonical in additionalPermissionAllowlist) listOf(canonical) else null
    }

    fun additionalPermissions(draft: ProjectDraft): List<String> {
        val unsupported = draft.additionalPermissions.filter { expandPermission(it) == null }
        require(unsupported.isEmpty()) {
            "Desteklenmeyen Android izinleri: ${unsupported.joinToString()}. Ek izinler yalnız manifest bildirimi sağlar; WebView API desteği sağlamaz."
        }
        return draft.additionalPermissions.flatMap { expandPermission(it)!! }.distinct().sorted()
    }

    // Partial canonical selections must remain visible so OFF can remove every equivalent.
    fun isAdditionalPermissionEnabled(values: Set<String>, alias: String): Boolean {
        val equivalents = permissionAliases.getValue(alias)
        return values.any { value -> expandPermission(value)?.any { it in equivalents } == true }
    }

    fun toggleAdditionalPermission(values: Set<String>, alias: String, enabled: Boolean): Set<String> {
        val equivalents = permissionAliases.getValue(alias)
        val retained = values.filterNot { value ->
            expandPermission(value)?.any { it in equivalents } == true
        }.toSet()
        return if (enabled) retained + equivalents else retained
    }

    fun validate(draft: ProjectDraft) {
        FeatureSupportMatrix.requireCompatible(draft)
        val failures = diagnostics(draft)
        require(failures.isEmpty()) { failures.joinToString("\n") { it.turkish() } }
    }

    internal fun diagnostics(draft: ProjectDraft): List<WizardDiagnostic> = buildList {
        val engine = draft.sourceBuildEngine.trim().lowercase().ifBlank { "webview-static" }
        if (engine !in setOf("webview-static", "node-web")) return@buildList
        val outputs = runCatching { DeviceBuildCapabilities.requestedOutputs(draft.buildOutput) }.getOrDefault(emptySet())
        if (outputs.none { it == DeviceArtifactKind.APK || it == DeviceArtifactKind.AAB }) return@buildList
        fun reject(selected: Boolean, step: Int, field: String, message: String) {
            if (selected) add(WizardDiagnostic(step, field, WizardCapabilityClass.UNSUPPORTED_BLOCKED, message))
        }
        reject(draft.packageName == "com.appforge.htmlstub" || draft.packageName.length > 127, 1, "packageName",
            "Şablon paket kimliği kullanılamaz; farklı ve en çok 127 karakterlik bir uygulama kimliği seçin.")
        reject(draft.appCategory !in setOf("auto", "none", "game"), 4, "appCategory",
            "Uygulama kategorisi Otomatik, Standart uygulama veya Oyun olmalı.")
        for ((field, color) in listOf("primaryColor" to draft.primaryColor, "backgroundColor" to draft.backgroundColor,
            "statusBarColor" to draft.statusBarColor, "navigationBarColor" to draft.navigationBarColor)) {
            reject(!color.matches(Regex("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?")), 4, field,
                "Rengi #RRGGBB veya #AARRGGBB biçiminde girin.")
        }
        reject(draft.orientation !in setOf("unspecified", "portrait", "landscape"), 4, "orientation",
            "Ekran yönü Otomatik, Dikey veya Yatay olmalı.")
        for ((field, selected) in listOf("qrScanner" to draft.qrScanner, "mediaPlayerBridge" to draft.mediaPlayerBridge,
            "admobEnabled" to draft.admobEnabled, "billingEnabled" to draft.billingEnabled,
            "firebaseAnalyticsEnabled" to draft.firebaseAnalyticsEnabled,
            "firebaseCrashlyticsEnabled" to draft.firebaseCrashlyticsEnabled,
            "firebaseMessagingEnabled" to draft.firebaseMessagingEnabled, "umpConsentEnabled" to draft.umpConsentEnabled)) {
            reject(selected && (field !in setOf("qrScanner", "mediaPlayerBridge", "billingEnabled") || !FastHtmlApkPolicy.isCandidate(draft)), if (field in setOf("qrScanner", "mediaPlayerBridge")) 5 else 6, field,
                "Bu motor otomatik entegrasyonu desteklemiyor; yapılandırma dosyası veya kimlik eklemek yeterli değildir. Seçeneği kapatın veya destekleyen kaynak proje kullanın.")
        }
        if (engine == "webview-static" && draft.sourceMode == com.appforge.studio.model.SourceMode.URL) {
            val url = runCatching { java.net.URI(draft.webUrl) }.getOrNull()
            reject(url == null || !url.scheme.equals("https", true) || url.host.isNullOrBlank() ||
                draft.webUrl.length > 2048 || url.port !in -1..65535, 1, "webUrl",
                "HTML/URL Android için geçerli HTTPS adresi gerekiyor (en çok 2048 karakter).")
            reject(url?.userInfo != null, 1, "webUrl", "URL içine kullanıcı adı/parola koymayın.")
        }
        runCatching { additionalPermissions(draft) }.exceptionOrNull()?.let {
            // Do not echo untrusted imported permission strings into diagnostics/logs.
            add(WizardDiagnostic(2, "additionalPermissions", WizardCapabilityClass.UNSUPPORTED_BLOCKED,
                "Desteklenmeyen Android izinleri var. İzin listesini gözden geçirin; ek izinler yalnız manifest bildirimi sağlar."))
        }
        if (draft.billingEnabled) {
            fun ids(value: String): List<String> = value.trim().split(Regex("""[,;\s]+""")).filter { it.isNotEmpty() }
            val one=ids(draft.billingProductIds); val subscriptions=ids(draft.billingSubscriptionIds); val consumables=ids(draft.consumableProductIds)
            val all=one+subscriptions
            val validIds=listOf(draft.billingProductIds,draft.billingSubscriptionIds,draft.consumableProductIds).all{it.length<=8192} &&
                all.isNotEmpty() && all.size<=64 && all.distinct().size==all.size && consumables.distinct().size==consumables.size &&
                (all+consumables).all{it.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}"))} && one.containsAll(consumables) &&
                (draft.removeAdsProductId.isEmpty() || draft.removeAdsProductId in one && draft.removeAdsProductId !in consumables)
            val endpoint=runCatching{java.net.URI(draft.purchaseVerificationUrl)}.getOrNull()
            val validEndpoint=endpoint!=null && draft.purchaseVerificationUrl.length<=2048 && endpoint.scheme.equals("https",true) &&
                !endpoint.host.isNullOrBlank() && endpoint.userInfo==null && endpoint.query==null && endpoint.fragment==null && endpoint.port in setOf(-1,443)
            if(!validIds || !validEndpoint)add(WizardDiagnostic(6,"billingConfiguration",WizardCapabilityClass.REQUIRES_EXTERNAL_CONFIGURATION,
                "Play Billing için 1–64 benzersiz ürün/abonelik ID, doğru tüketilebilir alt kümesi ve HTTPS sunucu doğrulama URL'si gerekir. Sunucu Google Play üzerinden paket/ürün/token/quantity doğrulamalıdır; Play hesabı, ürün yayını ve dağıtım ayrıca gerekir."))
        }
        if (draft.microphone && draft.sourceMode == com.appforge.studio.model.SourceMode.URL) {
            val url = runCatching { java.net.URI(draft.webUrl) }.getOrNull()
            reject(url == null || !url.scheme.equals("https", true) || url.host.isNullOrBlank() ||
                url.userInfo != null, 2, "microphone", "WebView mikrofon kullanımı için kimlik bilgisi içermeyen HTTPS URL gerekir.")
        }
        if (draft.deepLinkEnabled) {
            reject(!draft.deepLinkScheme.matches(Regex("[a-z][a-z0-9+.-]*")) ||
                draft.deepLinkScheme in setOf("file", "javascript", "data", "content", "intent"),
                7, "deepLinkScheme", "Geçersiz deep link scheme; güvenli bir bağlantı şeması girin.")
            reject(!draft.deepLinkHost.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")),
                7, "deepLinkHost", "Geçersiz deep link host; yalnız alan adını girin.")
            reject(!draft.deepLinkPathPrefix.startsWith("/") || draft.deepLinkPathPrefix.any {
                it.isWhitespace() || it.isISOControl() || it in "?#\\"
            }, 7, "deepLinkPathPrefix", "Geçersiz deep link path prefix; / ile başlayan güvenli bir yol girin.")
        }
    }
}

internal enum class WizardCapabilityClass {
    QUICK_SUPPORTED, NORMAL_SUPPORTED, REQUIRES_EXTERNAL_CONFIGURATION, UNSUPPORTED_BLOCKED
}

internal data class WizardDiagnostic(
    val step: Int,
    val field: String,
    val capability: WizardCapabilityClass,
    val message: String,
    val blocking: Boolean = true
) {
    fun turkish(): String = "Adım $step • $field: $message"
}

internal data class WizardReadiness(
    val capability: WizardCapabilityClass,
    val diagnostics: List<WizardDiagnostic>,
    val normalRouteReasons: List<String>
) {
    val ready: Boolean get() = diagnostics.none { it.blocking }
    val affectedSteps: Set<Int> get() = diagnostics.filter { it.blocking }.map { it.step }.toSet()
    fun requireReady() {
        require(ready) { diagnostics.filter { it.blocking }.joinToString("\n") { it.turkish() } }
    }
}

/** Pure settings evidence only. Files, toolchains, templates and signing are checked at use. */
internal object WizardReadinessEvaluator {
    fun evaluate(draft: ProjectDraft): WizardReadiness {
        val diagnostics = WebviewProjectSettings.diagnostics(draft).toMutableList()
        fun reject(selected: Boolean, step: Int, field: String, message: String,
                   capability: WizardCapabilityClass = WizardCapabilityClass.UNSUPPORTED_BLOCKED) {
            if (selected) diagnostics.add(WizardDiagnostic(step, field, capability, message))
        }
        reject(draft.appName.isBlank() || draft.appName.length > 100 || draft.appName.any { it.isISOControl() },
            1, "appName", "Uygulama adını girin (1–100 karakter).")
        reject(!draft.packageName.matches(Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")),
            1, "packageName", "Geçerli bir Android paket adı girin.")
        reject(draft.versionCode < 1 || draft.versionName.isBlank() || draft.versionName.any { it.isISOControl() },
            1, "versionCode/versionName", "Sürüm adı boş olamaz; sürüm kodu pozitif olmalı.")
        reject(draft.minSdk !in 26..37 || draft.targetSdk !in draft.minSdk..37,
            9, "minSdk/targetSdk", "Android SDK aralığı 26–37 olmalı; hedef minimumdan küçük olamaz.")
        val outputs = runCatching { DeviceBuildCapabilities.requestedOutputs(draft.buildOutput) }.getOrDefault(emptySet())
        reject(outputs.isEmpty(), 9, "buildOutput", "Desteklenen çıktı türlerinden birini seçin.")
        val engine = draft.sourceBuildEngine.trim().lowercase().ifBlank { "webview-static" }
        val capability = DeviceBuildCapabilities.forEngine(engine)
        reject(capability == null || capability.support !in setOf(DeviceBuildSupport.READY, DeviceBuildSupport.EXPERIMENTAL) ||
            outputs.any { it !in capability.readyOutputs }, 9, "sourceBuildEngine/buildOutput",
            "Seçilen motor bu çıktı için hazır değil; desteklenen motor ve çıktı eşleşmesini seçin.")
        if (capability?.support == DeviceBuildSupport.EXPERIMENTAL && outputs.isNotEmpty() &&
            outputs.all { it in capability.readyOutputs }) {
            diagnostics.add(WizardDiagnostic(9, "sourceBuildEngine", WizardCapabilityClass.NORMAL_SUPPORTED,
                "Deneysel motor seçildi; üretime hazır değildir. Yerel araç zinciri, kaynak ve imza uygunluğu derleme sırasında ayrıca doğrulanır.", false))
        }
        reject(engine == "expo" && !draft.sourceBuildReady, 1, "sourceBuildReady",
            "Expo yolu yalnız kabul edilmiş SDK 54 / React Native 0.81 kaynak yapılandırmasıyla kullanılabilir.",
            WizardCapabilityClass.REQUIRES_EXTERNAL_CONFIGURATION)
        // Match the engine's global injection guard, including imported native/Windows drafts.
        for ((field, selected) in listOf("qrScanner" to draft.qrScanner, "mediaPlayerBridge" to draft.mediaPlayerBridge,
            "admobEnabled" to draft.admobEnabled, "billingEnabled" to draft.billingEnabled,
            "firebaseAnalyticsEnabled" to draft.firebaseAnalyticsEnabled,
            "firebaseCrashlyticsEnabled" to draft.firebaseCrashlyticsEnabled,
            "firebaseMessagingEnabled" to draft.firebaseMessagingEnabled)) {
            if (selected && (field !in setOf("qrScanner", "mediaPlayerBridge", "billingEnabled") || !FastHtmlApkPolicy.isCandidate(draft)) && diagnostics.none { it.field == field }) reject(true,
                if (field in setOf("qrScanner", "mediaPlayerBridge")) 5 else 6, field,
                "Cihaz motoru bu otomatik entegrasyonu desteklemiyor; kaynak projenin kendi entegrasyonu ayrıca doğrulanmalıdır.")
        }
        if (engine == "webview-static") {
            reject(draft.sourceMode == com.appforge.studio.model.SourceMode.LOCAL && draft.importedFolder.isNullOrBlank(),
                1, "importedFolder", "Yerel kaynak klasörünü seçin.")
        }
        if (draft.signingMode == com.appforge.studio.model.SigningMode.CUSTOM) {
            reject(draft.keystoreUri.isNullOrBlank() || draft.keyAlias.isBlank() ||
                draft.storePassword.isEmpty() || draft.keyPassword.isEmpty(), 8, "signingMode",
                "Özel imza için keystore, takma ad ve parolaları tamamlayın. Anahtar ayrıca derleme sırasında doğrulanır.",
                WizardCapabilityClass.REQUIRES_EXTERNAL_CONFIGURATION)
        }
        for ((key, reason) in FeatureSupportMatrix.conflicts(draft)) diagnostics.add(
            WizardDiagnostic(if(key in setOf("camera","microphone","location","capture","audioCapture","watchPosition"))2 else 9,
                key,WizardCapabilityClass.UNSUPPORTED_BLOCKED,reason))
        val fastCandidate = outputs.isNotEmpty() && FastHtmlApkPolicy.isCandidate(draft)
        val reasons = if (outputs.isEmpty()) mutableListOf() else FastHtmlApkPolicy.normalRouteReasons(draft).toMutableList()
        if (engine in setOf("webview-static", "node-web") && outputs.any { it == DeviceArtifactKind.APK || it == DeviceArtifactKind.AAB }) {
            for ((field, selected) in listOf("networkState" to draft.networkState, "wakeLock" to draft.wakeLock,
                "nfc" to draft.nfc, "notifications" to draft.notifications, "additionalPermissions" to draft.additionalPermissions.isNotEmpty())) {
                if (selected && !(field in setOf("notifications", "networkState", "wakeLock", "nfc") && fastCandidate)) diagnostics.add(WizardDiagnostic(2, field, WizardCapabilityClass.NORMAL_SUPPORTED,
                    "İzin bildirimi yerel API sağlamaz. NFC, uyandırma kilidi, bildirim gönderimi ve ağ köprüsü otomatik eklenmez; Android izin onayı ayrıca gerekir.", false))
            }
            if (!draft.webJavaScriptEnabled) diagnostics.add(WizardDiagnostic(3, "webJavaScriptEnabled",
                WizardCapabilityClass.NORMAL_SUPPORTED, "JavaScript kapalıyken belge ve yerel özellik köprüleri çalışmaz.", false))
            if (draft.deepLinkEnabled && draft.deepLinkScheme == "https") diagnostics.add(WizardDiagnostic(7, "deepLinkHost",
                WizardCapabilityClass.REQUIRES_EXTERNAL_CONFIGURATION, "Doğrulanmış HTTPS uygulama bağlantıları için sunucuda assetlinks.json yapılandırması gerekir.", false))
        }
        if (fastCandidate) {
            diagnostics.add(WizardDiagnostic(9, "fastHtmlV2", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML V2: seçili Android çıktıları gömülü APK/AAB şablonundan cihazda üretilir; SDK/JDK/Gradle/Node gerekmez. Şablon bütünlüğü, kaynak ve imza derlemede doğrulanır.", false))
            if (draft.sourceMode == com.appforge.studio.model.SourceMode.URL) diagnostics.add(WizardDiagnostic(1, "offlineScope",
                WizardCapabilityClass.REQUIRES_EXTERNAL_CONFIGURATION,
                "HTTPS sayfasını açmak internet gerektirir. Çevrimdışı derleme, uzaktaki içeriği çevrimdışı kullanılabilir yapmaz.", false))
            if (draft.sourceMode == com.appforge.studio.model.SourceMode.URL && !draft.remoteBridgeAllowed &&
                draft.javascriptBridge && (draft.clipboardBridge || draft.vibrationBridge || draft.shareBridge))
                diagnostics.add(WizardDiagnostic(3, "remoteBridgeAllowed", WizardCapabilityClass.QUICK_SUPPORTED,
                    "Uzak sayfanın yerel köprü erişimi kapalıdır. Pano/titreşim/dosya paylaşımı yalnız güvenilir başlangıç origin'i için ayrıca izin verirseniz çalışır; sistem dosya seçimi bağımsızdır.", false))
            if (draft.networkState) diagnostics.add(WizardDiagnostic(2, "networkState", WizardCapabilityClass.QUICK_SUPPORTED,
                "Ağ durumu: AppForgeFastNative.networkState() ve appforge-connectivity olayı; yerel köprü izni gerekir. URL sayfası bağlantı yokken uyarır, bağlantı gelince yeniden yüklenir. Derleme için internet gerekmez.", false))
            if (draft.camera || draft.microphone || draft.location) diagnostics.add(WizardDiagnostic(2, "webPermissions", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML HTTPS: kamera/mikrofon ve yaklaşık konum yalnız güvenilir başlangıç origin'i, son dokunuş, açık onay ve Android izniyle çalışır. Yerel file kamera/mikrofon WebRTC için normal motor gerektirir; tek seferlik yerel konum ayrı güvenli köprü kullanır. Kamera capture dosyası bu API değildir.", false))
            if (draft.deepLinkEnabled) diagnostics.add(WizardDiagnostic(7, "deepLinkEnabled", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML: Seçili scheme/host/pathPrefix manifeste yazılır. Web uygulaması appforge-deeplink olayındaki url alanını işler; otomatik WebView gezinmesi veya autoVerify yoktur. HTTPS App Links doğrulaması ayrıca sunucu yapılandırması ister.", false))
            if (draft.location && draft.sourceMode == com.appforge.studio.model.SourceMode.LOCAL) diagnostics.add(WizardDiagnostic(2, "location", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML yerel konumu: AppForgeLocation.getCurrentPosition(success,error) tek seferlik konum alır; mümkünse navigator.geolocation.getCurrentPosition buna bağlanır. Dokunuş, açık onay, Android yaklaşık/hassas izin ve açık sağlayıcı gerekir. GPS hassas izinle çevrimdışı çalışabilir; bulunamayan konum hata döndürür. Watch/background yoktur; file storage origin değişmez.", false))
            if (draft.billingEnabled) diagnostics.add(WizardDiagnostic(6,"billingEnabled",WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML Play Billing 9.1: derleme çevrimdışı; çalışma için internet/Google Play ve ürün yapılandırması gerekir. AppForgeBilling API kullanılır. FLOW_OPENED ödeme değildir; sunucu doğrulaması ve Play acknowledge/consume onayı olmadan COMPLETE/verified true dönmez. İnternet yokken pasif kalır; otomatik satın alma yapılmaz.",false))
            if (draft.mediaPlayerBridge) diagnostics.add(WizardDiagnostic(5, "mediaPlayerBridge", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML: AppForgeMedia.play(relativePath,title) paketlenmiş yerel sesi Android mediaPlayback servisiyle oynatır. Bildirim izni, ses odağı, duraklat/devam/durdur kontrolleri vardır. STARTING başarı değildir; appforge-media olayı gerçek durumu bildirir. Uzak URL ve otomatik HTML audio devri yoktur.", false))
            if (draft.qrScanner) diagnostics.add(WizardDiagnostic(5, "qrScanner", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML: AppForgeFastNative.scanCode() kamera ve Android izniyle çevrimdışı QR/barkod okur. SCANNING yalnız başlangıç durumudur; sonuç appforge-scan olayının text alanında gelir. Kamera önplanla sınırlıdır; sonuç URL olarak açılmaz.", false))
            if (draft.nfc) diagnostics.add(WizardDiagnostic(2, "nfc", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML: AppForgeFastNative.scanNfc() önplanda bir NFC etiket kimliğini okur; appforge-nfc olayında id gelir. Donanım ve sistem NFC açık olmalı; NDEF yazma/ödeme/arka plan tarama yoktur.", false))
            if (draft.wakeLock) diagnostics.add(WizardDiagnostic(2, "wakeLock", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML: ekran yalnız uygulama önplandayken açık tutulur; CPU kilidi en çok 10 dakika sürer ve pause/destroy sırasında bırakılır. Arka plan servisi sağlamaz.", false))
            if (!draft.iconUri.isNullOrBlank()) diagnostics.add(WizardDiagnostic(4, "iconUri", WizardCapabilityClass.QUICK_SUPPORTED,
                "Fast HTML: statik PNG ikon (en çok 1024×1024, 1 MiB). Okuma/PNG doğrulaması başarısızsa derleme durur; varsayılan ikonla devam edilmez.", false))
            if (draft.notifications) diagnostics.add(WizardDiagnostic(2, "notifications", WizardCapabilityClass.QUICK_SUPPORTED,
                "Yerel bildirim: sayfa açıkken kullanıcı dokunuşuyla AppForgeFastNative.notify(title,text). Android 13+ izin ister; PERMISSION_REQUESTED bildirim gönderildi anlamına gelmez. Zamanlanmış/uzak bildirim desteklenmez.", false))
            if (draft.fileUpload) diagnostics.add(WizardDiagnostic(2, "fileUpload", WizardCapabilityClass.QUICK_SUPPORTED,
                "Dosya seçimi sistem belge seçicisini kullanır (en çok 32 dosya); kamera çekimi eklenmez. Kullanıcının seçtiği içerik proje sayfasına verilir.", false))
            if (draft.orientation != "unspecified" || draft.statusBarColor != "#07101F" || draft.navigationBarColor != "#07101F")
                diagnostics.add(WizardDiagnostic(4, "systemWindow", WizardCapabilityClass.QUICK_SUPPORTED,
                    "Android'in yeni uçtan uca pencere ve büyük ekran kuralları sistem çubuğu renklerini veya yön kilidini geçersiz kılabilir.", false))
        }
        val prerequisites = buildList {
            if (outputs.any { it == DeviceArtifactKind.APK || it == DeviceArtifactKind.AAB } && !fastCandidate)
                add(when (engine) {
                    "node-web" -> "Node web: Node.js/npm ve Android SDK + JDK + Gradle gerekir."
                    "expo" -> "Expo: desteklenen SDK 54 ailesi, Node.js/npm, Android SDK + JDK + Gradle ve NDK/CMake gerekir."
                    "android-gradle" -> "Native Android: kaynak projenin Gradle yapılandırması, Android SDK ve JDK gerekir."
                    "python-android" -> "Python Android: Python/Chaquopy ve Android SDK + JDK + Gradle gerekir."
                    else -> "Android çıktısı için motorun yerel araç zinciri ve kaynak yapılandırması ayrıca doğrulanır."
                })
            if (DeviceArtifactKind.WINDOWS_EXE in outputs)
                add("Windows Portable: doğrulanmış Portable EXE host paketi ve yayıncı/imza önkoşulları ayrıca denetlenir.")
            if (DeviceArtifactKind.WINDOWS_NATIVE_EXE in outputs)
                add("Windows Native deneysel: C/C++ kaynakları, CMake ve MinGW-w64 araç zinciri gerekir; üretime hazır değildir.")
        }
        if (prerequisites.isNotEmpty()) diagnostics.add(WizardDiagnostic(9, "toolchain",
            WizardCapabilityClass.REQUIRES_EXTERNAL_CONFIGURATION, prerequisites.joinToString(" "), false))
        diagnostics.add(WizardDiagnostic(10, "runtimeChecks", WizardCapabilityClass.REQUIRES_EXTERNAL_CONFIGURATION,
            "Bu kontrol yalnız ayarları inceler. Kaynak/ZIP ve HTML giriş dosyası, ikon okunabilirliği, SDK/araçlar, şablon özeti ve imza anahtarı/sertifikası derleme sırasında ayrıca doğrulanır.", false))
        val classification = when {
            diagnostics.any { it.blocking && it.capability == WizardCapabilityClass.UNSUPPORTED_BLOCKED } -> WizardCapabilityClass.UNSUPPORTED_BLOCKED
            diagnostics.any { it.blocking } -> WizardCapabilityClass.REQUIRES_EXTERNAL_CONFIGURATION
            outputs.isNotEmpty() && reasons.isEmpty() && fastCandidate -> WizardCapabilityClass.QUICK_SUPPORTED
            else -> WizardCapabilityClass.NORMAL_SUPPORTED
        }
        return WizardReadiness(classification, diagnostics.toList(), reasons.distinct())
    }
}
