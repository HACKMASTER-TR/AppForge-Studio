package com.appforge.studio.build

import com.appforge.studio.model.*
import com.appforge.studio.io.HtmlCapabilityFindings
import org.junit.Assert.*
import org.junit.Test

class ModularOfflineV3Test {
    private fun html()=ProjectDraft(appName="Test",sourceTechnology="html",importedFolder="/fixture",startPage="index.html")
    @Test fun unknownFeaturesFailClosedWithoutErasingPreferences() {
        val d=html().copy(requiredCapabilities=setOf("futureNativeApi"))
        assertEquals(FeatureSupportState.UNSUPPORTED,FeatureSupportMatrix.feature(d,"futureNativeApi").state)
        assertFalse(FeatureSupportMatrix.recommend(d,"futureNativeApi",true,d.requiredCapabilities))
        assertFalse(WizardReadinessEvaluator.evaluate(d).ready)
        assertEquals(setOf("futureNativeApi"),d.requiredCapabilities)
    }
    @Test fun defaultsAndSupportedOutputsUseBundledOnly() {
        for(output in listOf("apk","aab","both")) {
            val d=html().copy(buildOutput=output)
            assertTrue(FastHtmlApkPolicy.isCandidate(d));assertTrue(WizardReadinessEvaluator.evaluate(d).ready)
            assertEquals(listOf(OfflineModuleId.FAST_HTML),OfflineModules.forProject(d).modules)
        }
    }
    @Test fun unsupportedControlsAndImportedPreferencesNeverSilentlyFallback() {
        val d=html();val camera=FeatureSupportMatrix.feature(d,"camera")
        assertFalse(camera.selectable);assertNotNull(camera.alternative)
        assertFalse(FeatureSupportMatrix.acceptUserChange(d,d.copy(camera=true)).camera)
        val imported=d.copy(camera=true);val before=imported.copy()
        assertFalse(WizardReadinessEvaluator.evaluate(imported).ready)
        assertNotNull(OfflineModules.forProject(imported).error);assertEquals(before,imported)
        assertThrows(IllegalArgumentException::class.java){WebviewProjectSettings.validate(imported)}
        assertFalse(FeatureSupportMatrix.feature(d,"admobEnabled").selectable)
        assertFalse(FeatureSupportMatrix.recommend(d,"location",true,setOf("watchPosition")))
        assertFalse(FeatureSupportMatrix.recommend(d.copy(sourceMode=SourceMode.URL),"camera",true,setOf("capture")))
        assertFalse(FeatureSupportMatrix.recommend(d,"nfc",true,setOf("webNfcApi")))
        assertFalse(WizardReadinessEvaluator.evaluate(d.copy(qrScanner=false,requiredCapabilities=setOf("qrScanner"))).ready)
        assertEquals(FeatureSupportState.MANIFEST_ONLY,FeatureSupportMatrix.feature(d,"additionalPermissions").state)
        assertFalse(FeatureSupportMatrix.feature(d,"additionalPermissions").autoEnable)
        assertEquals(FeatureSupportState.EXTERNAL_CONFIG_REQUIRED,FeatureSupportMatrix.feature(d,"billingEnabled").state)
    }
    @Test fun plainFileAndCaptureAndWebrtcAreDifferentFindings() {
        assertEquals(setOf("fileUpload"),HtmlCapabilityFindings.scan("<input type='file' multiple accept='image/*'>"))
        assertEquals(setOf("fileUpload"),HtmlCapabilityFindings.scan("<input data-capture='x' type='file'>"))
        assertEquals(setOf("fileUpload","capture"),HtmlCapabilityFindings.scan("<input capture type=file accept='image/*'>"))
        assertEquals(setOf("fileUpload","audioCapture"),HtmlCapabilityFindings.scan("<input accept='audio/*' type=file capture>"))
        assertEquals(setOf("microphone"),HtmlCapabilityFindings.scan("navigator.mediaDevices.getUserMedia({video:false, audio: true})"))
        assertEquals(setOf("camera"),HtmlCapabilityFindings.scan("navigator.mediaDevices.getUserMedia({video:{facingMode:'user'}, audio:false})"))
        assertEquals(setOf("webMediaUnknown"),HtmlCapabilityFindings.scan("navigator.mediaDevices.getUserMedia(options)"))
        assertEquals(setOf("watchPosition"),HtmlCapabilityFindings.scan("navigator.geolocation.watchPosition(ok)"))
        assertTrue(HtmlCapabilityFindings.scan("<!-- <input type=file capture> -->").isEmpty())
        val d=html().copy(requiredCapabilities=setOf("capture"));assertFalse(WizardReadinessEvaluator.evaluate(d).ready)
    }
    @Test fun moduleGraphInstallsOnlyRealDependenciesOnce() {
        fun modules(engine:String,output:String="apk")=OfflineModules.forProject(html().copy(sourceBuildEngine=engine,buildOutput=output)).modules.toSet()
        assertEquals(setOf(OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.ANDROID_CORE),modules("android-gradle"))
        assertEquals(setOf(OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.ANDROID_CORE,OfflineModuleId.NODE_WEB),modules("node-web"))
        assertEquals(setOf(OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.ANDROID_CORE,OfflineModuleId.PYTHON_ANDROID),modules("python-android"))
        assertEquals(setOf(OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.ANDROID_CORE,OfflineModuleId.NODE_WEB,OfflineModuleId.EXPO_REACT_NATIVE),modules("expo"))
        assertEquals(setOf(OfflineModuleId.WINDOWS_PORTABLE),modules("webview-static","exe"))
        assertEquals(setOf(OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.WINDOWS_NATIVE),modules("windows-native","native-exe"))
        assertFalse(OfflineModules.resolve(setOf(OfflineModuleId.NODE_WEB)).contains(OfflineModuleId.ANDROID_CORE))
        val union=OfflineModules.resolve(setOf(OfflineModuleId.EXPO_REACT_NATIVE,OfflineModuleId.NODE_WEB));assertEquals(union.distinct(),union)
        assertNotNull(OfflineModules.forProject(html().copy(sourceBuildEngine="invented")).error)
        assertFalse(FastHtmlApkPolicy.isCandidate(html().copy(normalHtmlEngine=true)))
        assertFalse(WizardReadinessEvaluator.evaluate(html().copy(buildOutput="invalid")).ready)
        assertNotNull(OfflineModules.forProject(html().copy(buildOutput="invalid")).error)
    }
    @Test fun newChildModulesReuseExistingToolsAndOnlyRepairsRefresh() {
        for(id in listOf(OfflineModuleId.NODE_WEB,OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.EXPO_REACT_NATIVE,OfflineModuleId.WINDOWS_NATIVE)) {
            assertFalse(OfflineModules.refreshExistingInstallation(id,false))
            assertTrue(OfflineModules.refreshExistingInstallation(id,true))
        }
        assertTrue(OfflineModules.refreshExistingInstallation(OfflineModuleId.ANDROID_CORE,false))
    }
    @Test fun byteProgressIsHonestAndDownloadCompletionIsNotReady() {
        val meter=DownloadMeasurement(OfflineModuleId.NODE_WEB,"test",0)
        val first=meter.sample(0,1000,0);assertEquals(0,first.percent);assertNull(first.estimatedRemainingMs)
        val half=meter.sample(500,1000,1000);assertEquals(50,half.percent);assertEquals(500.0,half.speedBytesPerSecond!!,0.01);assertEquals(500.0,half.averageSpeedBytesPerSecond!!,0.01);assertEquals(1000L,half.estimatedRemainingMs)
        val idle=meter.sample(500,1000,2000);assertNull(idle.estimatedRemainingMs)
        val done=meter.sample(1000,1000,3000);assertEquals(100,done.percent);assertEquals(ModulePhase.DOWNLOADING,done.phase)
        val unknown=DownloadMeasurement(OfflineModuleId.NODE_WEB,"unknown",0).sample(100,null,1000);assertNull(unknown.percent);assertNull(unknown.estimatedRemainingMs)
        assertThrows(IllegalArgumentException::class.java){meter.sample(999,1000,4000)}
    }
}
