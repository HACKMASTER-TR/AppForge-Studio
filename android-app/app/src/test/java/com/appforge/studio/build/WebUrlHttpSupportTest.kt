package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import com.appforge.studio.io.ProjectLibrary
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class WebUrlHttpSupportTest {
    private fun draft(url:String)=ProjectDraft(appName="HTTP Test",sourceMode=SourceMode.URL,sourceBuildEngine="webview-static",sourceTechnology="html",webUrl=url)
    @Test fun httpHttpsIpLocalhostAndCustomPortsAreAcceptedWithoutRewriting() {
        for(url in listOf("http://example.com/path?q=1#part","https://example.com","http://192.168.1.7:8080/","http://127.0.0.1:8123/","http://localhost:3000/","http://10.0.2.2:8000/","HTTP://example.com:80/","http://[::1]:8080/")) {
            assertNotNull(url,WebviewProjectSettings.webSourceUri(url))
            val d=draft(url);WebviewProjectSettings.validate(d);FastHtmlApkPolicy.requireSupported(d)
            assertEquals(url,d.webUrl);assertEquals(url,JSONObject(String(FastHtmlApkPackager.runtimeConfig(d,""))).getString("url"))
        }
    }
    @Test fun malformedCredentialsUnsupportedSchemesAndBadPortsAreRejected() {
        for(url in listOf("ftp://example.com","javascript:alert(1)","file:///etc/passwd","data:text/html,x","https:///missing","http://","http://bad_host/","http://u:p@example.com/","http://@example.com/","http://example.com:0/","http://example.com:65536/","http://example.com:-1/","http://999.999.1.1/","http://example.com/a b"," http://example.com","http://example.com\\evil")) {
            assertNull(url,WebviewProjectSettings.webSourceUri(url))
            assertThrows(IllegalArgumentException::class.java){FastHtmlApkPolicy.requireSupported(draft(url))}
        }
    }
    @Test fun androidPolicyIsDefaultDenyExactHostNoSubdomainsAndNoPortInDomain() {
        val d=draft("http://192.168.1.7:8080/path")
        val root=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(WebviewProjectSettings.networkSecurityXml(d).byteInputStream())
        assertEquals("false",root.getElementsByTagName("base-config").item(0).attributes.getNamedItem("cleartextTrafficPermitted").nodeValue)
        assertEquals("true",root.getElementsByTagName("domain-config").item(0).attributes.getNamedItem("cleartextTrafficPermitted").nodeValue)
        val domain=root.getElementsByTagName("domain").item(0)
        assertEquals("192.168.1.7",domain.textContent);assertEquals("false",domain.attributes.getNamedItem("includeSubdomains").nodeValue)
        for(other in listOf(draft("https://example.com"),d.copy(sourceMode=SourceMode.LOCAL))) {
            assertNull(WebviewProjectSettings.cleartextHost(other))
            assertFalse(WebviewProjectSettings.networkSecurityXml(other).contains("cleartextTrafficPermitted=\"true\""))
        }
    }
    @Test fun actualSavedProjectSerializerPreservesExistingHttpAddress() {
        val url="http://localhost:8080/path?q=one#two";val d=draft(url)
        val method=ProjectLibrary::class.java.getDeclaredMethod("serializeProject",String::class.java,java.lang.Long.TYPE,ProjectDraft::class.java).apply{isAccessible=true}
        val saved=method.invoke(ProjectLibrary,"synthetic-http-project",1L,d) as JSONObject
        val reopened=JSONObject(saved.toString())
        assertEquals(url,reopened.getString("webUrl"))
        assertNotNull(WebviewProjectSettings.webSourceUri(reopened.getString("webUrl")))
    }
    @Test fun httpDoesNotGrantSecureNativeBridgeOrMicrophoneAndDoesNotToggleMixedContent() {
        val plain=draft("http://example.com")
        assertThrows(IllegalArgumentException::class.java){WebviewProjectSettings.validate(plain.copy(remoteBridgeAllowed=true))}
        assertThrows(IllegalArgumentException::class.java){WebviewProjectSettings.validate(plain.copy(microphone=true))}
        assertEquals(plain.webMixedContentAllowed,JSONObject(String(FastHtmlApkPackager.runtimeConfig(plain,""))).getBoolean("webMixedContentAllowed"))
        WebviewProjectSettings.validate(plain.copy(webUrl="https://example.com",remoteBridgeAllowed=true))
    }
    @Test fun localOutputsAndExplicitNormalUrlAabRoutingAreUnchanged() {
        for(output in listOf("apk","aab","both"))assertTrue(FastHtmlApkPolicy.isCandidate(ProjectDraft(appName="Local",sourceTechnology="html",importedFolder="/synthetic",startPage="index.html",buildOutput=output)))
        assertTrue(FastHtmlApkPolicy.isCandidate(draft("http://localhost:8080/")))
        assertFalse(FastHtmlApkPolicy.isCandidate(draft("http://localhost:8080/").copy(buildOutput="aab")))
        assertTrue(FastHtmlApkPolicy.normalRouteReasons(draft("http://localhost:8080/").copy(buildOutput="aab")).isNotEmpty())
    }
}
