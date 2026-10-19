package com.appforge.studio.build
import org.junit.Assert.*
import org.junit.Test
class DownloadNetworkRulesTest {
    private val wifi=DownloadNetworkFacts(true,true,false,false,false)
    @Test fun onlyValidatedUnmeteredWifiPermittedWithoutExplicitMobileConsent() {
        for(p in listOf(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetworkPolicy.WIFI_PREFERRED)) {
            assertTrue(DownloadNetworkRules.permits(p,wifi))
            for(f in listOf(wifi.copy(validated=false),wifi.copy(cellular=true),wifi.copy(vpn=true),wifi.copy(metered=true),wifi.copy(wifi=false)))assertFalse(DownloadNetworkRules.permits(p,f))
        }
    }
    @Test fun explicitAnyNetworkConsentAllowsValidatedCellularAndVpnButNeverOffline() {
        assertTrue(DownloadNetworkRules.permits(DownloadNetworkPolicy.ALLOW_ANY,DownloadNetworkFacts(true,false,true,false,true)))
        assertTrue(DownloadNetworkRules.permits(DownloadNetworkPolicy.ALLOW_ANY,wifi.copy(vpn=true)))
        assertFalse(DownloadNetworkRules.permits(DownloadNetworkPolicy.ALLOW_ANY,wifi.copy(validated=false)))
    }
    @Test fun handoverAndUserPolicyChangeAreReevaluatedWithoutBroadeningOldRequest() {
        val mobile=DownloadNetworkFacts(true,false,true,false,true)
        assertTrue(DownloadNetworkRules.permits(DownloadNetworkPolicy.WIFI_ONLY,wifi))
        assertFalse(DownloadNetworkRules.permits(DownloadNetworkPolicy.WIFI_ONLY,mobile))
        assertTrue(DownloadNetworkRules.permits(DownloadNetworkPolicy.ALLOW_ANY,mobile))
        assertFalse(DownloadNetworkRules.permits(DownloadNetworkPolicy.WIFI_PREFERRED,mobile))
    }
}
