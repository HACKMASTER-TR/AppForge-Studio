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
                webMediaAutoplayEnabled=enabled,webMixedContentAllowed=enabled,offlineCache=enabled,fileUpload=enabled,clipboardBridge=enabled,
                vibrationBridge=enabled,mediaPlayerBridge=enabled,qrScanner=enabled,nfc=enabled,camera=enabled,microphone=enabled,location=enabled,wakeLock=enabled,notifications=enabled,fullscreen=enabled,splashEnabled=enabled,
                orientation=if(enabled) "portrait" else "unspecified",splashText=if(enabled) "Starting" else "")
            val config=JSONObject(FastHtmlApkPackager.runtimeConfig(draft,"index.html").toString(Charsets.UTF_8))
            for (key in listOf("downloads","nativeBridge","shareBridge","nativeBridgeAllowRemote",
                "webJavaScriptEnabled","webDomStorageEnabled","webZoomEnabled","webWideViewPortEnabled",
                "webOverviewModeEnabled","webMediaAutoplayEnabled","webMixedContentAllowed","offlineCache","fileUpload","clipboardBridge",
                "vibrationBridge","mediaPlayerBridge","qrScanner","nfc","camera","microphone","location","wakeLock","notifications","fullscreen","splashEnabled")) assertEquals(key,enabled,config.getBoolean(key))
            assertEquals("LOCAL",config.getString("mode"))
            assertEquals("index.html",config.getString("entry"))
            assertEquals(draft.orientation,config.getString("orientation"))
            assertEquals(draft.splashText,config.getString("splashText"))
            assertEquals(draft.backgroundColor,config.getString("backgroundColor"))
            assertEquals(draft.statusBarColor,config.getString("statusBarColor"))
            assertEquals(draft.navigationBarColor,config.getString("navigationBarColor"))
            for (secret in listOf("storePassword","keyPassword","buildApiKey")) assertFalse(config.has(secret))
        }
    }
    @Test fun defaultWizardAndV2SettingsStayFastWithoutSilentlyDroppingSelections() {
        val defaults=ProjectDraft(appName="V2 Test",importedFolder="/test")
        assertTrue(FastHtmlApkPolicy.isCandidate(defaults))
        assertTrue(FastHtmlApkPolicy.normalRouteReasons(defaults).isEmpty())
        val supported=defaults.copy(startPage="index.html")
        for (draft in listOf(supported.copy(billingEnabled=true,billingProductIds="one",purchaseVerificationUrl="https://verify.example/purchase"),supported.copy(location=true),supported.copy(mediaPlayerBridge=true),supported.copy(qrScanner=true),supported.copy(deepLinkEnabled=true,deepLinkHost="example.com"),supported.copy(nfc=true),supported.copy(additionalPermissions=setOf("READ_CONTACTS")),supported.copy(wakeLock=true),supported.copy(iconUri="content://icons/selected"),supported.copy(fullscreen=true),supported.copy(orientation="portrait"),
            supported.copy(notifications=true),supported.copy(appCategory="none",primaryColor="#BC9CF6"),
            supported.copy(appCategory="game",primaryColor="#80112233",webMixedContentAllowed=true),supported.copy(backgroundColor="#123456",splashText="Starting"))) {
            val before=draft.copy()
            assertTrue(FastHtmlApkPolicy.isCandidate(draft))
            assertTrue(FastHtmlApkPolicy.normalRouteReasons(draft).isEmpty())
            FastHtmlApkPolicy.requireSupported(draft)
            assertEquals(before,draft)
        }
        for (draft in listOf(supported.copy(sourceMode=com.appforge.studio.model.SourceMode.URL,webUrl="https://example.com",camera=true,microphone=true,location=true))) {
            assertTrue(FastHtmlApkPolicy.isCandidate(draft))
            FastHtmlApkPolicy.requireSupported(draft)
            assertTrue(FastHtmlApkPolicy.templatePermissions(draft).containsAll(setOf("android.permission.CAMERA","android.permission.RECORD_AUDIO","android.permission.ACCESS_COARSE_LOCATION")))
        }
        assertFalse(FastHtmlApkPolicy.isCandidate(supported.copy(mediaPlayerBridge=true,javascriptBridge=false)))
        assertFalse(FastHtmlApkPolicy.isCandidate(supported.copy(mediaPlayerBridge=true,sourceMode=com.appforge.studio.model.SourceMode.URL,webUrl="https://example.com")))
        assertTrue(FastHtmlApkPolicy.templatePermissions(supported.copy(mediaPlayerBridge=true,wakeLock=false,notifications=false)).containsAll(setOf("android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK","android.permission.WAKE_LOCK","android.permission.POST_NOTIFICATIONS")))
        assertTrue("android.permission.CAMERA" in FastHtmlApkPolicy.templatePermissions(supported.copy(qrScanner=true,camera=false)))
        assertFalse(FastHtmlApkPolicy.isCandidate(supported.copy(qrScanner=true,javascriptBridge=false)))
        assertFalse(FastHtmlApkPolicy.isCandidate(supported.copy(nfc=true,javascriptBridge=false)))
        for (draft in listOf(supported.copy(camera=true),supported.copy(microphone=true),
            supported.copy(notifications=true,javascriptBridge=false))) {
            assertFalse(FastHtmlApkPolicy.isCandidate(draft))
            assertTrue(FastHtmlApkPolicy.normalRouteReasons(draft).isNotEmpty())
        }
    }
}
