package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DownloadHistoryV84Test {
    private fun row(n:Int,op:String="op$n",module:OfflineModuleId=OfflineModuleId.RUST,
        sha:String="%064x".format(n+1),source:String="f".repeat(64),target:String="private:pkg$n",
        state:DownloadState=DownloadState.PAUSED,verified:Long=0L,downloaded:Long=verified)=
        ArtifactDownloadRecord(op,module,sha,"1",source,sha,null,verified,state,
            DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE,DownloadNetworkPolicy.ALLOW_ANY,
            0,1,2,if(verified>0)"PREFIX_VERIFIED" else "RECHECK_REQUIRED",target,downloadedBytes=downloaded)

    @Test fun sameArchiveRetriedTenTimesRetainsOnlyOneCurrentRecord() {
        var rows=emptyList<ArtifactDownloadRecord>()
        for(n in 0 until 10) rows=DownloadHistoryV84.upsert(rows,row(1,op="run$n"))
        assertEquals(1,rows.size)
        assertEquals("run9",rows.single().operationId)
    }
    @Test fun distinctSourceTargetModuleAndShaDoNotMerge() {
        val entries=listOf(row(1),row(1,source="e".repeat(64)),row(1,target="private:other"),
            row(1,module=OfflineModuleId.NODE_WEB),row(2))
        var rows=emptyList<ArtifactDownloadRecord>()
        entries.forEach{rows=DownloadHistoryV84.upsert(rows,it)}
        assertEquals(5,rows.size)
    }
    @Test fun startupMigrationRetainsHigherRehashedCheckpoint() {
        val old=row(1,op="early",verified=15)
        val newest=row(1,op="latest",verified=2).copy(updatedAt=20)
        val best=DownloadHistoryV84.compactRecovered(listOf(old,newest))
        assertEquals(1,best.size)
        assertEquals("early",best.single().operationId)
        assertEquals(15L,best.single().verifiedPartialBytes)
    }
    @Test fun newerVerifiedProgressOverwritesOlderCheckpoint() {
        val old=row(1,op="early",verified=15)
        val next=row(1,op="later",verified=20)
        val best=DownloadHistoryV84.compactRecovered(listOf(old,next))
        assertEquals(20L,best.single().verifiedPartialBytes)
    }
    @Test fun hashCompletedDisappearsButPausedPartialStays() {
        val pending=row(1,verified=15)
        val completed=row(2,state=DownloadState.COMPLETED).copy(verificationState="SHA256_VERIFIED")
        val rows=DownloadHistoryV84.upsert(DownloadHistoryV84.upsert(listOf(pending),completed),completed)
        assertEquals(listOf(pending),rows)
        assertEquals(emptyList<ArtifactDownloadRecord>(),DownloadHistoryV84.afterReady(rows,OfflineModuleId.RUST))
    }
    @Test fun fullHistoryDropsOnlyZeroByteTerminalRows() {
        val old=(0 until 127).map{row(it,state=DownloadState.RECOVERY_REQUIRED)}
        val critical=row(127,verified=5)
        val rows=DownloadHistoryV84.upsert(old+critical,row(200))
        assertEquals(128,rows.size)
        assertTrue(rows.contains(critical))
        assertTrue(rows.any{it.artifactId==row(200).artifactId})
    }
    @Test fun cannotEvictVerifiedPartialsAtCapacity() {
        val old=(0 until 128).map{row(it,verified=8)}
        val fail=assertThrows(IllegalStateException::class.java){DownloadHistoryV84.upsert(old,row(200))}
        assertTrue(fail.message!!.contains("korunması gereken"))
    }
    @Test fun prefixRehashDoesNotTrustAJournalCounterAlone() {
        val dir=Files.createTempDirectory("v84-prefix").toFile()
        try {
            val store=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir))
            val r=row(1,target="private:pkg1",verified=20)
            assertEquals(0L,store.verifiedPrefixFor(r))
            val part=File(dir,"pkg1.part").apply{writeText("checked");}
            File(part.path+".sha256").writeText(VerifiedModuleDownload.sha(part))
            File(part.path+".identity").writeText(r.sourceIdentityHash+"\n"+r.expectedSha256)
            assertEquals(part.length(),store.verifiedPrefixFor(r))
            part.appendText("tampered")
            assertEquals(0L,store.verifiedPrefixFor(r))
        } finally {dir.deleteRecursively()}
    }
    @Test fun graphCompactionPreservesLiveInstallationParent() {
        val old=row(1,op="old",state=DownloadState.RECOVERY_REQUIRED)
        val now=row(1,op="new",state=DownloadState.PAUSED)
        val parent=PersistentOperationGraph.moduleId(old.operationId,old.moduleId)
        val root=DownloadOperationNode(parent,null,old.moduleId,OperationStage.MODULE,
            DownloadState.RECOVERY_REQUIRED,DownloadNetworkPolicy.ALLOW_ANY,1,2)
        val child=PersistentOperationGraph(File("unused")).artifact(old,parent)
        val output=PersistentOperationGraph.compactAfterJournalChange(listOf(root,child),listOf(old),listOf(now))
        assertTrue(output.isEmpty())
    }
}
