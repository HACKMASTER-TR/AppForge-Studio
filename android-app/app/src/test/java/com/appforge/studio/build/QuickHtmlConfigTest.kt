package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuickHtmlConfigTest {
    @Test fun sharedApkAabConfigConsumesEveryQuickSettingBothWays() {
        for (enabled in listOf(false,true)) {
            val draft=ProjectDraft(downloads=enabled,javascriptBridge=enabled,shareBridge=enabled,
                remoteBridgeAllowed=enabled,webJavaScriptEnabled=enabled,webDomStorageEnabled=enabled,
                webZoomEnabled=enabled,webWideViewPortEnabled=enabled,webOverviewModeEnabled=enabled,
                webMediaAutoplayEnabled=enabled,offlineCache=enabled)
            val config=JSONObject(FastHtmlApkPackager.runtimeConfig(draft,"index.html").toString(Charsets.UTF_8))
            for (key in listOf("downloads","nativeBridge","shareBridge","nativeBridgeAllowRemote",
                "webJavaScriptEnabled","webDomStorageEnabled","webZoomEnabled","webWideViewPortEnabled",
                "webOverviewModeEnabled","webMediaAutoplayEnabled","offlineCache")) assertEquals(key,enabled,config.getBoolean(key))
            assertEquals("LOCAL",config.getString("mode"))
            assertEquals("index.html",config.getString("entry"))
            assertEquals(draft.backgroundColor,config.getString("backgroundColor"))
            assertEquals(draft.statusBarColor,config.getString("statusBarColor"))
            assertEquals(draft.navigationBarColor,config.getString("navigationBarColor"))
            for (secret in listOf("storePassword","keyPassword","buildApiKey")) assertFalse(config.has(secret))
        }
    }
    @Test fun defaultWizardRequiresNormalEngineAndEveryMissingCapabilityHasReason() {
        assertFalse(FastHtmlApkPolicy.isCandidate(ProjectDraft(importedFolder="/test")))
        val supported=ProjectDraft(importedFolder="/test",startPage="index.html",fileUpload=false,
            clipboardBridge=false,vibrationBridge=false,networkState=false,splashEnabled=false)
        assertTrue(FastHtmlApkPolicy.isCandidate(supported))
        for (draft in listOf(supported.copy(fileUpload=true),supported.copy(clipboardBridge=true),
            supported.copy(vibrationBridge=true),supported.copy(networkState=true),supported.copy(splashEnabled=true),
            supported.copy(fullscreen=true),supported.copy(orientation="portrait"),supported.copy(camera=true),
            supported.copy(microphone=true),supported.copy(location=true),supported.copy(deepLinkEnabled=true))) {
            assertFalse(FastHtmlApkPolicy.isCandidate(draft))
            assertTrue(FastHtmlApkPolicy.normalRouteReasons(draft).isNotEmpty())
        }
    }
}
