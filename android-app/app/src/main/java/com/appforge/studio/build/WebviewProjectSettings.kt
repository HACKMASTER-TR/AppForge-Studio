package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft

/** Manifest declarations do not enable native APIs or bypass runtime consent. */
internal object WebviewProjectSettings {
    private val additionalPermissionAllowlist =
        setOf(
            "BLUETOOTH_SCAN",
            "BLUETOOTH_CONNECT",
            "USE_BIOMETRIC",
            "READ_CALENDAR",
            "WRITE_CALENDAR",
            "READ_CONTACTS",
            "WRITE_CONTACTS",
            "ACCESS_BACKGROUND_LOCATION",
            "SCHEDULE_EXACT_ALARM",
            "READ_MEDIA_IMAGES",
            "READ_MEDIA_VIDEO",
            "ACTIVITY_RECOGNITION"
        )

    fun additionalPermissions(draft: ProjectDraft): List<String> {
        val requested = draft.additionalPermissions.map {
            it.trim().removePrefix("android.permission.").uppercase()
        }.distinct()
        val unsupported = requested.filter { it !in additionalPermissionAllowlist }
        require(unsupported.isEmpty()) {
            "Desteklenmeyen Android izinleri: ${unsupported.joinToString()}. Ek izinler yalnız manifest bildirimi sağlar; WebView API desteği sağlamaz."
        }
        return requested
    }

    fun validate(draft: ProjectDraft) {
        val engine = draft.sourceBuildEngine.trim().lowercase().ifBlank { "webview-static" }
        if (engine !in setOf("webview-static", "node-web")) return
        val outputs = DeviceBuildCapabilities.requestedOutputs(draft.buildOutput)
        if (outputs.none { it == DeviceArtifactKind.APK || it == DeviceArtifactKind.AAB }) return

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
