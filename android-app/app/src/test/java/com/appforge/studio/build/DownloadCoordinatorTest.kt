package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test

class DownloadCoordinatorTest {
    @Test fun targetLocksRejectDuplicatesButAllowIndependentTransfers() {
        DownloadCoordinator.claim("target-a")
        try {
            assertThrows(IllegalStateException::class.java){DownloadCoordinator.claim("target-a")}
            DownloadCoordinator.claim("target-b");DownloadCoordinator.release("target-b")
        }finally{DownloadCoordinator.release("target-a")}
        DownloadCoordinator.claim("target-a");DownloadCoordinator.release("target-a")
    }
    @Test fun aggregationIsByteBasedAndStoppedRatesAreCleared() {
        val operation="aggregate-test"
        fun row(id:String,bytes:Long,total:Long?,phase:ModulePhase=ModulePhase.DOWNLOADING)=DownloadCoordinator.Transfer(id,"1",id,
            ModuleProgress(OfflineModuleId.NODE_WEB,operation,phase,bytes,total,speedBytesPerSecond=12.0,estimatedRemainingMs=100),
            DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE)
        DownloadCoordinator.observe(row("a",40,100));DownloadCoordinator.observe(row("a",50,100))
        DownloadCoordinator.observe(row("b",20,100))
        assertEquals(70L to 200L,DownloadCoordinator.aggregate(operation))
        DownloadCoordinator.observe(row("b",20,null,ModulePhase.PAUSED))
        assertEquals(70L to null,DownloadCoordinator.aggregate(operation))
        assertNull(DownloadCoordinator.snapshot(operation).first{it.artifactId=="b"}.progress.speedBytesPerSecond)
    }
    @Test fun stalledTransferDoesNotKeepAdvertisingPreviousRates() {
        val event=ModuleProgress(OfflineModuleId.NODE_WEB,"stalled",ModulePhase.DOWNLOADING,10,100,
            speedBytesPerSecond=9.0,estimatedRemainingMs=10000,averageSpeedBytesPerSecond=8.0)
        assertEquals(event,DownloadCoordinator.withoutStaleRates(event,4999))
        val expired=DownloadCoordinator.withoutStaleRates(event,5000)
        assertEquals(10L,expired.bytesDownloaded);assertNull(expired.speedBytesPerSecond)
        assertNull(expired.averageSpeedBytesPerSecond);assertNull(expired.estimatedRemainingMs)
    }

}
