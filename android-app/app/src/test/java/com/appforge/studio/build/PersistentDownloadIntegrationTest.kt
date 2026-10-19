package com.appforge.studio.build

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PersistentDownloadIntegrationTest {
    @Test fun interruptedRealDownloaderReconcilesJournalWithSealedBytesAndRetainsRetryAcrossReopen() {
        val dir=Files.createTempDirectory("native-persistent").toFile()
        val journalField=DownloadCoordinator::class.java.getDeclaredField("journal").apply{isAccessible=true}
        val previous=journalField.get(DownloadCoordinator);val oldRows=DownloadCoordinator.records.value;val oldTree=DownloadCoordinator.operations.value
        try {
            journalField.set(DownloadCoordinator,null)
            val store=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir));DownloadCoordinator.configure(store)
            val payload="abcdefghij".toByteArray();val source=File(dir,"source").apply{writeBytes(payload)};val sha=VerifiedModuleDownload.sha(source)
            val target=File(dir,"artifact");var reads=0;var opens=0
            fun connection(body:ByteArray,code:Int=200,range:String?=null)=object:HttpURLConnection(URL("https://fixture.example/artifact")) {
                override fun connect(){};override fun usingProxy()=false;override fun disconnect(){}
                override fun getResponseCode()=code
                override fun getHeaderField(name:String?)=if(name=="Content-Range")range else null
                override fun getContentLengthLong()=if(code==200)payload.size.toLong()else body.size.toLong()
                override fun getInputStream()=object:java.io.ByteArrayInputStream(body){override fun read(b:ByteArray,off:Int,len:Int):Int{reads++;return super.read(b,off,minOf(len,4))}}
            }
            assertThrows(java.util.concurrent.CancellationException::class.java){
                VerifiedModuleDownload.download("https://fixture.example/artifact",sha,target,OfflineModuleId.NODE_WEB,"persistent-one",canceled={reads>=1},open={opens++;connection(payload)})
            }
            assertEquals(1,opens);assertFalse(target.exists())
            val reopened=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir))
            val partial=reopened.recover().single();assertEquals(4L,partial.verifiedPartialBytes);assertEquals(DownloadState.PAUSED,partial.downloadState)
            reopened.save(listOf(partial.copy(retryCount=2)))
            journalField.set(DownloadCoordinator,null);DownloadCoordinator.configure(reopened)
            assertEquals(2,DownloadCoordinator.bind("persistent-two",sha,target))
            VerifiedModuleDownload.download("https://fixture.example/artifact",sha,target,OfflineModuleId.NODE_WEB,"persistent-two",open={connection(payload.copyOfRange(4,10),206,"bytes 4-9/10")})
            assertArrayEquals(payload,target.readBytes())
            val complete=reopened.read().first{it.operationId=="persistent-two"}
            assertEquals(DownloadState.COMPLETED,complete.downloadState);assertEquals(2,complete.retryCount)
            assertEquals(10L,complete.downloadedBytes)
            val tree=reopened.operationGraph().read()
            val node=tree.single{it.operationId==PersistentOperationGraph.artifactNodeId(complete)}
            assertEquals(complete.parentOperationId,node.parentOperationId)
            assertEquals(10L,node.verifiedBytes)
            assertTrue(tree.single{it.operationId==node.parentOperationId}.operationState!=DownloadState.COMPLETED)
            assertFalse(File(target.path+".part.identity").exists())
            assertTrue(reopened.recover().all{it.downloadState!=DownloadState.COMPLETED}) // source pin must be re-authorized, not journal-only READY
        }finally{journalField.set(DownloadCoordinator,previous);DownloadCoordinator.records.value=oldRows;DownloadCoordinator.operations.value=oldTree;dir.deleteRecursively()}
    }
}
