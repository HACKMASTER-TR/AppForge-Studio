package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class ModuleLifecycleV3Test {
    @Test fun moduleMutationAndBuildAreMutuallyExclusiveWithIdempotentRelease() {
        val build=BuildModuleUsage.buildLease()
        try {assertThrows(IllegalStateException::class.java){BuildModuleUsage.mutationLease()}}
        finally {build.close();build.close()}
        val mutation=BuildModuleUsage.mutationLease()
        try {assertThrows(IllegalStateException::class.java){BuildModuleUsage.buildLease()}}
        finally {mutation.close();mutation.close()}
        BuildModuleUsage.buildLease().close()
    }
    @Test fun processDeathRestoresPausedAndNeverTrustsPersistedReady() {
        val dir=Files.createTempDirectory("module-journal").toFile()
        try {
            val file=File(dir,"journal.json");val journal=ModuleOperationJournal(file)
            journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"op",ModulePhase.READY,100,100)))
            val restored=ModuleOperationJournal(file).restore().getValue(OfflineModuleId.NODE_WEB)
            assertEquals(ModulePhase.PAUSED,restored.phase);assertEquals(100L,restored.bytesDownloaded)
            assertNull(restored.estimatedRemainingMs);assertNull(restored.speedBytesPerSecond)
            assertFalse(file.readText().contains("sdkConsent"))
            file.writeText("{broken");assertTrue(journal.restore().isEmpty())
        } finally {dir.deleteRecursively()}
    }
    @Test fun telemetryUsesRealBytesUnknownTotalsAndDownloadCompletionIsNotReady() {
        val adapter=ExternalDownloadTelemetry(OfflineModuleId.ANDROID_CORE,"op")
        fun event(n:Int,total:String="1000",done:Boolean=false)="""{"schema":1,"transfer":"t","bytes":$n,"total":$total,"finished":$done}"""
        assertEquals(0,adapter.accept(event(0),0)!!.percent)
        val half=adapter.accept(event(500),1000)!!;assertEquals(50,half.percent);assertEquals(1000L,half.estimatedRemainingMs)
        assertNull(adapter.accept(event(500),2000)!!.estimatedRemainingMs)
        assertEquals(ModulePhase.PREPARING,adapter.accept(event(1000,done=true),3000)!!.phase)
        assertNull(adapter.accept(event(1001),4000))
        assertNull(adapter.accept("random 75%",5000))
        assertNull(adapter.accept(event(1000,"null"),6000)!!.percent)
    }
    @Test fun opaqueFileSamplesCannotClaimNetworkSpeedAndInterruptedTransfersCannotComplete() {
        val adapter=ExternalDownloadTelemetry(OfflineModuleId.ANDROID_CORE,"op")
        val opaque=adapter.accept("""{"schema":1,"transfer":"apt","bytes":500,"total":1000,"byteMetric":"staged-files","elapsedMs":1000}""",1000)!!
        assertNull(opaque.percent);assertNull(opaque.speedBytesPerSecond);assertNull(opaque.averageSpeedBytesPerSecond)
        assertNull(opaque.estimatedRemainingMs);assertEquals(ModulePhase.PREPARING,opaque.phase)
        assertTrue(opaque.detail.contains("ölçülemiyor"))
        val interrupted=adapter.accept("""{"schema":1,"transfer":"curl","bytes":500,"total":1000,"finished":true,"state":"error","errorCode":"CURL_EXIT_18"}""",2000)!!
        assertEquals(ModulePhase.ERROR,interrupted.phase);assertEquals("CURL_EXIT_18",interrupted.errorCode)
        assertNull(interrupted.estimatedRemainingMs);assertNull(interrupted.speedBytesPerSecond)
    }
    @Test fun ownedRemovalPreservesForeignFilesAndRestoresOnPredeleteFault() {
        val base=Files.createTempDirectory("owned-host").toFile()
        try {
            val root=File(base,"offline-build-pack/windows-host-v1").apply{mkdirs()}
            val host=File(root,"AppForge-Windows-Host-v1.exe").apply{writeText("verified host fixture")}
            val digest=VerifiedModuleDownload.sha(host)
            File(root,"windows-host.ready").writeText("ready")
            val foreign=File(root,"user-project.txt").apply{writeText("preserve")}
            assertThrows(IllegalArgumentException::class.java){OwnedModuleFiles.removePortable(base,digest)}
            assertEquals("preserve",foreign.readText());assertTrue(host.isFile)
            foreign.delete()
            assertThrows(IllegalStateException::class.java){OwnedModuleFiles.removePortable(base,digest){error("fault before destructive boundary")}}
            assertEquals("verified host fixture",host.readText())
            val project=File(base,"projects/source.html").apply{parentFile.mkdirs();writeText("source")}
            OwnedModuleFiles.removePortable(base,digest)
            assertFalse(root.exists());assertEquals("source",project.readText())
        } finally {base.deleteRecursively()}
    }

    @Test fun historyIsBoundedAndDiskWriteFailuresCannotPretendPersistence() {
        val dir=Files.createTempDirectory("journal-history").toFile()
        try {
            val file=File(dir,"journal.json");val journal=ModuleOperationJournal(file)
            repeat(50){n->journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"op-$n",ModulePhase.DOWNLOADING,n.toLong(),100,detail="bytes only")))}
            assertEquals(32,journal.history().size);assertTrue(file.length()<32768)
            val count=journal.history().size
            journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"op-49",ModulePhase.DOWNLOADING,80,100)))
            assertEquals(count,journal.history().size)
            val blocked=ModuleOperationJournal(File(file,"cannot-write"))
            assertThrows(java.io.IOException::class.java){blocked.save(emptyMap())}
            assertEquals(ModulePhase.PAUSED,journal.restore().getValue(OfflineModuleId.NODE_WEB).phase)
        }finally{dir.deleteRecursively()}
    }

}
