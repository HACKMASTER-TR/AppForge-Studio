package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DownloadHistoryReclaimTest {
    private fun row(n:Int,state:DownloadState,verified:Long=0,downloaded:Long=0,verification:String="RECHECK_REQUIRED")=
        ArtifactDownloadRecord("op$n",OfflineModuleId.RUST,"%064x".format(n+1),"1","b".repeat(64),"c".repeat(64),null,verified,state,
            DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE,DownloadNetworkPolicy.ALLOW_ANY,0,1,2,verification,"private:archive$n",downloadedBytes=downloaded)

    @Test fun onlyUnverifiedRecoveryMetadataIsReleased() {
        val rows=listOf(
            row(0,DownloadState.RECOVERY_REQUIRED),
            row(1,DownloadState.RECOVERY_REQUIRED,verified=15,downloaded=15,verification="PREFIX_VERIFIED"),
            row(2,DownloadState.PAUSED,verified=4,downloaded=4,verification="PREFIX_VERIFIED"),
            row(3,DownloadState.COMPLETED,verified=20,downloaded=20,verification="SHA256_VERIFIED"),
            row(4,DownloadState.RECOVERY_REQUIRED,downloaded=2),
            row(5,DownloadState.FAILED)
        )
        val retained=PersistentDownloads.retainAfterUserCleanup(rows)
        assertEquals(5,retained.size)
        assertFalse(retained.contains(rows[0]))
        assertTrue(retained.containsAll(rows.drop(1)))
        assertEquals(retained,PersistentDownloads.retainAfterUserCleanup(retained))
    }
    @Test fun boundedHistoryCanAcceptNewPackageWithoutDeletingCacheOrVerifiedPartial() {
        val root=Files.createTempDirectory("appforge-reclaim").toFile()
        try {
            val cache=File(root,"cached-package.deb").apply{writeText("keep user cache")}
            val store=PersistentDownloads(File(root,"downloads"),mapOf("private" to root))
            val rows=(0 until 127).map{row(it,DownloadState.RECOVERY_REQUIRED)} +
                row(127,DownloadState.PAUSED,verified=8,downloaded=8,verification="PREFIX_VERIFIED")
            store.save(rows)
            val retained=PersistentDownloads.retainAfterUserCleanup(store.read())
            store.save(retained)
            assertEquals(1,store.read().size)
            assertEquals(DownloadState.PAUSED,store.read().single().downloadState)
            assertEquals("keep user cache",cache.readText())
            assertFalse(store.read().any{it.downloadState==DownloadState.RECOVERY_REQUIRED})
        } finally { root.deleteRecursively() }
    }
    @Test fun cleanupPrunesOrphanRecoveryTreesAndPreservesLiveAndClosedHistory() {
        fun root(record:ArtifactDownloadRecord,state:DownloadState,closed:Boolean=false)=
            DownloadOperationNode(PersistentOperationGraph.moduleId(record.operationId,record.moduleId),null,record.moduleId,
                OperationStage.MODULE,state,record.networkPolicy,1,2,historyClosed=closed)
        val oldRecord=row(850,DownloadState.RECOVERY_REQUIRED)
        val liveRecord=row(851,DownloadState.PAUSED,verified=8,downloaded=8,verification="PREFIX_VERIFIED")
        val oldRoot=root(oldRecord,DownloadState.RECOVERY_REQUIRED)
        val liveRoot=root(liveRecord,DownloadState.RECOVERY_REQUIRED)
        val oldChild=PersistentOperationGraph(File("unused")).artifact(oldRecord,oldRoot.operationId)
        val liveChild=PersistentOperationGraph(File("unused")).artifact(liveRecord,liveRoot.operationId)
        val oldStage=DownloadOperationNode(PersistentOperationGraph.id(oldRoot.operationId,"OFFLINE_PROBE"),oldRoot.operationId,
            OfflineModuleId.RUST,OperationStage.OFFLINE_PROBE,DownloadState.RECOVERY_REQUIRED,DownloadNetworkPolicy.ALLOW_ANY,1,2)
        val result=PersistentOperationGraph.retainAfterUserCleanup(
            listOf(oldRoot,oldChild,oldStage,liveRoot,liveChild),listOf(oldRecord),listOf(liveRecord))
        assertEquals(listOf(liveRoot,liveChild),result)
        assertEquals(result,PersistentOperationGraph.retainAfterUserCleanup(result,emptyList(),listOf(liveRecord)))
    }
    @Test fun newNetworkDefaultsToAnyButSavedOrInvalidPreferenceIsHandledSafely() {
        assertEquals(DownloadNetworkPolicy.ALLOW_ANY,DownloadNetwork.initialPolicy(null))
        assertEquals(DownloadNetworkPolicy.WIFI_ONLY,DownloadNetwork.initialPolicy("WIFI_ONLY"))
        assertEquals(DownloadNetworkPolicy.WIFI_PREFERRED,DownloadNetwork.initialPolicy("WIFI_PREFERRED"))
        assertEquals(DownloadNetworkPolicy.WIFI_PREFERRED,DownloadNetwork.initialPolicy("unrecognized"))
    }
}
