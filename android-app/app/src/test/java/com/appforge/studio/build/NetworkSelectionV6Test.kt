package com.appforge.studio.build
import org.junit.Assert.*
import org.junit.Test

class NetworkSelectionV6Test {
    private val wifi=DownloadNetworkFacts(true,true,false,false,false)
    private val mobile=DownloadNetworkFacts(true,false,true,false,true)
    @Test fun simultaneousMobileDefaultAndWifiSelectsWifiOnly() {
        assertEquals(1,DownloadNetworkRules.choose(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetworkPolicy.WIFI_ONLY,0,listOf(mobile,wifi)))
        assertNull(DownloadNetworkRules.choose(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetworkPolicy.WIFI_ONLY,0,listOf(mobile)))
    }
    @Test fun wifiLossCannotInheritLaterAnyNetworkConsent() {
        assertNull(DownloadNetworkRules.choose(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetworkPolicy.ALLOW_ANY,0,listOf(mobile)))
        assertNull(DownloadNetworkRules.choose(DownloadNetworkPolicy.WIFI_PREFERRED,DownloadNetworkPolicy.ALLOW_ANY,0,listOf(mobile)))
        assertEquals(0,DownloadNetworkRules.choose(DownloadNetworkPolicy.ALLOW_ANY,DownloadNetworkPolicy.ALLOW_ANY,0,listOf(mobile)))
    }
    @Test fun activeVpnMeteredAndUnvalidatedWifiFailClosed() {
        assertNull(DownloadNetworkRules.choose(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetworkPolicy.WIFI_ONLY,0,listOf(mobile.copy(vpn=true),wifi)))
        assertNull(DownloadNetworkRules.choose(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetworkPolicy.WIFI_ONLY,0,listOf(wifi.copy(metered=true),wifi.copy(validated=false))))
    }
    @Test fun externalManagerCannotUseOfflineFlagAsNetworkIsolationProof() {
        assertEquals(DownloadCapability.BLOCKED,DownloadNetworkRules.externalCapability(DownloadNetworkPolicy.WIFI_ONLY))
        assertEquals(DownloadCapability.BLOCKED,DownloadNetworkRules.externalCapability(DownloadNetworkPolicy.WIFI_PREFERRED))
        assertEquals(DownloadCapability.CHECKPOINT_RESUME,DownloadNetworkRules.externalCapability(DownloadNetworkPolicy.ALLOW_ANY))
    }
}
