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
        val engine = draft.sourceBuildEngine.trim().lowercase().ifBlank { "webview-static" }
        if (engine !in setOf("webview-static", "node-web")) return
        val outputs = DeviceBuildCapabilities.requestedOutputs(draft.buildOutput)
        if (outputs.none { it == DeviceArtifactKind.APK || it == DeviceArtifactKind.AAB }) return

        require(draft.appCategory == "auto") { "App category injection is unsupported; select auto." }
        require(draft.primaryColor == "#6B7CFF") { "Primary color injection is unsupported; retain default." }
        require(!draft.umpConsentEnabled || draft.admobEnabled) { "UMP consent injection is unsupported." }
        require(!draft.qrScanner && !draft.mediaPlayerBridge && !draft.admobEnabled && !draft.billingEnabled &&
            !draft.firebaseAnalyticsEnabled && !draft.firebaseCrashlyticsEnabled && !draft.firebaseMessagingEnabled) {
            "Automatic QR/Media3/AdMob/Billing/Firebase integration is unsupported; build blocked."
        }

        // Advanced settings must not relax the Fast HTML URL security boundary.
        // Keep node-web compatibility independent, apart from its existing microphone check.
        if (engine == "webview-static" && draft.sourceMode == com.appforge.studio.model.SourceMode.URL) {
            val url = runCatching { java.net.URI(draft.webUrl) }.getOrNull()
            require(url != null && url.scheme.equals("https", true) &&
                !url.host.isNullOrBlank() && draft.webUrl.length <= 2048) {
                "HTML/URL Android için geçerli HTTPS adresi gerekiyor."
            }
            require(url.userInfo == null) { "URL içine kullanıcı adı/parola koymayın." }
        }
        additionalPermissions(draft)
        if (draft.microphone && draft.sourceMode == com.appforge.studio.model.SourceMode.URL) {
            val url = runCatching { java.net.URI(draft.webUrl) }.getOrNull()
            require(url != null && url.scheme.equals("https", true) &&
                !url.host.isNullOrBlank() && url.userInfo == null) {
                "WebView mikrofon kullanımı için geçerli, kimlik bilgisi içermeyen HTTPS URL gerekir."
            }
        }
        if (draft.deepLinkEnabled) {
            require(draft.deepLinkScheme.matches(Regex("[a-z][a-z0-9+.-]*")) &&
                draft.deepLinkScheme !in setOf("file", "javascript", "data", "content", "intent")) {
                "Geçersiz deep link scheme."
            }
            require(draft.deepLinkHost.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?"))) {
                "Geçersiz deep link host."
            }
            require(draft.deepLinkPathPrefix.startsWith("/") &&
                draft.deepLinkPathPrefix.none { it.isWhitespace() || it.isISOControl() || it in "?#\\" }) {
                "Geçersiz deep link path prefix."
            }
        }
    }
}
