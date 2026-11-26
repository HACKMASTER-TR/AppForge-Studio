package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PersistentOperationGraphTest {
    private fun root()=DownloadOperationNode("a".repeat(64),null,OfflineModuleId.NODE_WEB,OperationStage.MODULE,DownloadState.INSTALLING,DownloadNetworkPolicy.WIFI_ONLY,1,2)
    private fun child()=root().copy(operationId="b".repeat(64),parentOperationId=root().operationId,stage=OperationStage.INSTALLATION)
    private fun fixture(test:(File,PersistentOperationGraph)->Unit){val dir=Files.createTempDirectory("graph").toFile();try{test(dir,PersistentOperationGraph(File(dir,"operations.json")))}finally{dir.deleteRecursively()}}
    @Test fun parentChildRoundtripNeverContainsExecutableSources()=fixture{dir,store->
        store.save(listOf(root(),child()));assertEquals(listOf(root(),child()),store.read())
        val text=File(dir,"operations.json").readText();assertFalse(text.contains("https://"));assertFalse(File(dir,"operations.json.tmp").exists())
    }
    @Test fun orphanCycleCrossModuleAndDuplicateIdsRejected()=fixture{_,store->
        for(rows in listOf(listOf(child()),listOf(root(),root()),listOf(root(),child().copy(moduleId=OfflineModuleId.ANDROID_CORE)),listOf(root().copy(parentOperationId=child().operationId),child())))
            assertThrows(Exception::class.java){store.save(rows)}
    }
    @Test fun corruptedUnknownSchemaAndOversizedFileRejected()=fixture{dir,store->
        val file=File(dir,"operations.json")
        for(text in listOf("not-json","{\"schema\":99,\"nodes\":[]}","x".repeat(1048577))){file.writeText(text);assertThrows(Exception::class.java){store.read()}}
    }
    @Test fun diskFullAndSymlinkPreserveOldGraphAndForeignData()=fixture{dir,store->
        store.save(listOf(root()));val before=File(dir,"operations.json").readBytes()
        val full=PersistentOperationGraph(File(dir,"operations.json"),availableBytes={0})
        assertThrows(Exception::class.java){full.save(listOf(root(),child()))};assertArrayEquals(before,File(dir,"operations.json").readBytes())
        val foreign=File(dir,"user-key-fixture").apply{writeText("user-owned")}
        Files.createSymbolicLink(File(dir,"operations.json.tmp").toPath(),foreign.toPath())
        assertThrows(Exception::class.java){store.save(listOf(root()))};assertEquals("user-owned",foreign.readText())
    }
    @Test fun crashBetweenJournalsRebuildsArtifactChildWithoutTrustingComplete()=fixture{_,store->
        store.save(listOf(root().copy(operationState=DownloadState.COMPLETED),child()))
        val record=ArtifactDownloadRecord("transfer",OfflineModuleId.NODE_WEB,"c".repeat(64),"1","d".repeat(64),"e".repeat(64),100,7,DownloadState.PAUSED,DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE,DownloadNetworkPolicy.WIFI_ONLY,1,1,3,"PREFIX_VERIFIED",null,parentOperationId=root().operationId,downloadedBytes=7)
        val recovered=store.recover(listOf(record));assertTrue(recovered.filter{it.stage!=OperationStage.ARTIFACT_DOWNLOAD}.all{it.operationState==DownloadState.RECOVERY_REQUIRED})
        val artifact=recovered.single{it.stage==OperationStage.ARTIFACT_DOWNLOAD};assertEquals(7L,artifact.verifiedBytes);assertEquals(root().operationId,artifact.parentOperationId)
        assertEquals(recovered,store.read())
    }
    @Test fun missingGraphCreatesRecoveryParentAndMismatchedModuleIsRejected()=fixture{_,store->
        val r=ArtifactDownloadRecord("transfer",OfflineModuleId.NODE_WEB,"c".repeat(64),"1","d".repeat(64),null,null,0,DownloadState.RECOVERY_REQUIRED,DownloadCoordinator.ResumeCapability.SAFE_RESTART_ONLY,DownloadNetworkPolicy.WIFI_ONLY,0,1,2,"RECHECK_REQUIRED",null,parentOperationId=root().operationId)
        assertEquals(2,store.recover(listOf(r)).size)
        store.save(listOf(root().copy(moduleId=OfflineModuleId.ANDROID_CORE)))
        assertThrows(Exception::class.java){store.recover(listOf(r))}
    }
    @Test fun schemaOneArtifactMigrationDoesNotInventTransferredBytesOrReady()=fixture{dir,_->
        val store=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir))
        val r=ArtifactDownloadRecord("transfer",OfflineModuleId.NODE_WEB,"c".repeat(64),"1","d".repeat(64),null,null,0,DownloadState.COMPLETED,DownloadCoordinator.ResumeCapability.SAFE_RESTART_ONLY,DownloadNetworkPolicy.WIFI_ONLY,0,1,2,"UNVERIFIED",null)
        store.save(listOf(r));val file=File(dir,"journal/artifacts.json");val json=JSONObject(file.readText()).put("schema",1)
        json.getJSONArray("artifacts").getJSONObject(0).apply{remove("parentOperationId");remove("downloadedBytes")};file.writeText(json.toString())
        assertNull(store.read().single().parentOperationId);val recovered=store.recover().single()
        assertEquals(0L,recovered.downloadedBytes);assertEquals(DownloadState.RECOVERY_REQUIRED,recovered.downloadState)
        assertEquals(2,JSONObject(file.readText()).getInt("schema"))
    }
    @Test fun forgedCountersAndSecretTextRejected()=fixture{_,store->
        for(n in listOf(root().copy(downloadedBytes=10,verifiedBytes=11),root().copy(expectedBytes=2,downloadedBytes=3),root().copy(failureReason="https://secret"),root().copy(artifactVersion="../../secret")))
            assertThrows(Exception::class.java){store.save(listOf(n))}
    }
    @Test fun corruptModuleJournalCannotSilentlyResetHistory()=fixture{dir,_->
        val file=File(dir,"module.json");val journal=ModuleOperationJournal(file)
        for(text in listOf("not-json","{\"schema\":99}","{\"schema\":1,\"NODE_WEB\":{\"phase\":\"INVENTED\"}}")){
            file.writeText(text);assertThrows(Exception::class.java){journal.restoreStrict()};assertEquals(text,file.readText())
        }
    }

}
