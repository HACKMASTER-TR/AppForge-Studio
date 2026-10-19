package com.appforge.studio.build
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OperationNetworkConsentTest {
    @Test fun mobileApprovalIsClaimedOnceAndDoesNotWidenSiblingOperations()=runBlocking {
        val old=DownloadNetwork.policy
        try {
            DownloadNetwork.select(DownloadNetworkPolicy.WIFI_ONLY)
            DownloadNetwork.allowMobileOnce()
            assertEquals(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetwork.policy)
            val context=DownloadNetwork.takeOperationConsent()
            val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
            val operation=async(Dispatchers.Default+context) {
                assertEquals(DownloadNetworkPolicy.ALLOW_ANY,DownloadNetwork.policy)
                entered.complete(Unit);finish.await()
                withContext(Dispatchers.IO){assertEquals(DownloadNetworkPolicy.ALLOW_ANY,DownloadNetwork.policy)}
                DownloadNetwork.restorePreference()
                assertEquals(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetwork.policy)
            }
            entered.await()
            assertEquals(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetwork.policy)
            withContext(Dispatchers.IO+DownloadNetwork.takeOperationConsent()){assertEquals(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetwork.policy)}
            finish.complete(Unit);operation.await()
        }finally{DownloadNetwork.restorePreference();DownloadNetwork.select(old)}
    }
    @Test fun rejectedOrFailedStartClearsPendingApproval()=runBlocking {
        val old=DownloadNetwork.policy
        try {
            DownloadNetwork.select(DownloadNetworkPolicy.WIFI_PREFERRED)
            DownloadNetwork.allowMobileOnce();DownloadNetwork.restorePreference()
            withContext(DownloadNetwork.takeOperationConsent()){assertEquals(DownloadNetworkPolicy.WIFI_PREFERRED,DownloadNetwork.policy)}
        }finally{DownloadNetwork.restorePreference();DownloadNetwork.select(old)}
    }
}
