package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class LocalNetworkPermissionTest {
    private fun draft(url:String,target:Int=37)=ProjectDraft(appName="LAN test",sourceMode=SourceMode.URL,sourceBuildEngine="webview-static",sourceTechnology="html",webUrl=url,targetSdk=target)
    @Test fun knownLanTargetsNeedConsentWithoutDnsAndPublicCustomPortDoesNot() {
        for(host in listOf("192.168.1.7","10.0.2.2","172.16.1.3","172.31.255.254","127.0.0.1","169.254.1.2","localhost","printer.local","[::1]","[fd00::1]","[fe80::1]","[::ffff:192.168.1.2]")) {
            val d=draft("http://$host:8080/path");assertTrue(host,WebviewProjectSettings.needsLocalNetwork(d))
            assertTrue(host,"android.permission.ACCESS_LOCAL_NETWORK" in FastHtmlApkPolicy.templatePermissions(d))
            assertTrue(JSONObject(String(FastHtmlApkPackager.runtimeConfig(d,""))).getBoolean("localNetworkRequired"))
        }
        for(host in listOf("example.com","unknown-private-dns.example","172.32.1.1","8.8.8.8","[2001:4860:4860::8888]")) {
            assertFalse(host,WebviewProjectSettings.needsLocalNetwork(draft("https://$host:8123/")))
        }
    }
    @Test fun lowerTargetNeverDeclaresOrRequestsEvenWhenExplicitlyOptedIn() {
        for(target in listOf(26,35,36))for(optIn in listOf(false,true)) {
            val d=draft("http://192.168.1.7:8080/",target).copy(localNetworkAccess=optIn)
            assertFalse(WebviewProjectSettings.needsLocalNetwork(d))
            assertFalse("android.permission.ACCESS_LOCAL_NETWORK" in FastHtmlApkPolicy.templatePermissions(d))
            assertFalse(JSONObject(String(FastHtmlApkPackager.runtimeConfig(d,""))).getBoolean("localNetworkRequired"))
        }
    }
    @Test fun localHtmlAndUnresolvedAliasesRequireExplicitOptInAndHttpsRemainsCleartextDenied() {
        val d=draft("https://printer.local:8443/")
        assertTrue(WebviewProjectSettings.needsLocalNetwork(d));assertNull(WebviewProjectSettings.cleartextHost(d))
        val local=d.copy(sourceMode=SourceMode.LOCAL,webUrl="")
        assertFalse(WebviewProjectSettings.needsLocalNetwork(local))
        assertTrue(WebviewProjectSettings.needsLocalNetwork(local.copy(localNetworkAccess=true)))
        assertTrue(WebviewProjectSettings.needsLocalNetwork(draft("https://alias.example").copy(localNetworkAccess=true)))
        assertFalse(ProjectDraft().localNetworkAccess)
    }
    @Test fun actualNormalManifestUsesSameDecisionAsFastManifestAndRetainsTarget() {
        val method=DeviceBuildEngine::class.java.getDeclaredMethod("webManifest",ProjectDraft::class.java).apply{isAccessible=true}
        val instance=DeviceBuildEngine
        for(d in listOf(draft("http://localhost:3000"),draft("https://example.com"),draft("http://localhost:3000",36))) {
            val manifest=method.invoke(instance,d) as String
            assertEquals(WebviewProjectSettings.needsLocalNetwork(d),manifest.contains("android.permission.ACCESS_LOCAL_NETWORK"))
        }
    }
    @Test fun explicitPreferenceSurvivesActualProjectSerialization() {
        val method=com.appforge.studio.io.ProjectLibrary::class.java.getDeclaredMethod("serializeProject",String::class.java,java.lang.Long.TYPE,ProjectDraft::class.java).apply{isAccessible=true}
        val saved=method.invoke(com.appforge.studio.io.ProjectLibrary,"synthetic-lan-project",1L,draft("https://alias.example").copy(localNetworkAccess=true)) as JSONObject
        assertTrue(JSONObject(saved.toString()).getBoolean("localNetworkAccess"))
    }
}
