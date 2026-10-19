package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PersistentDownloadsTest {
    private fun record(identity:String="private:archive")=ArtifactDownloadRecord("operation-one",OfflineModuleId.NODE_WEB,"a".repeat(64),"1","b".repeat(64),"c".repeat(64),100,0,DownloadState.DOWNLOADING,DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE,DownloadNetworkPolicy.WIFI_ONLY,2,1,2,"UNVERIFIED",identity)
    private fun fixture(test:(File,PersistentDownloads)->Unit) {
        val dir=Files.createTempDirectory("persistent-download").toFile()
        try{test(dir,PersistentDownloads(File(dir,"journal"),mapOf("private" to dir)))}finally{dir.deleteRecursively()}
    }
    @Test fun atomicRoundTripRetainsRetryAndNeverStoresSourceUrl()=fixture{dir,store->
        val row=record();store.save(listOf(row));assertEquals(listOf(row),store.read())
        val text=File(dir,"journal/artifacts.json").readText();assertFalse(text.contains("https:"));assertFalse(File(dir,"journal/artifacts.json.tmp").exists())
        assertEquals(2,PersistentDownloads(File(dir,"journal"),mapOf("private" to dir)).read().single().retryCount)
    }
    @Test fun unknownSchemaMalformedAndOversizedRecordsFailClosed()=fixture{dir,store->
        val journal=File(dir,"journal/artifacts.json").apply{parentFile.mkdirs()}
        for(text in listOf("not-json","{\"schema\":99,\"artifacts\":[]}","{\"schema\":0,\"artifacts\":[]}","x".repeat(262145))) {
            journal.writeText(text);assertThrows(Exception::class.java){store.read()}
        }
    }
    @Test fun traversalSymlinkAndMissingStagingCannotBecomeComplete()=fixture{dir,store->
        assertThrows(IllegalArgumentException::class.java){store.save(listOf(record("private:../foreign")))}
        val outside=Files.createTempDirectory("foreign").toFile()
        try {
            Files.createSymbolicLink(File(dir,"link").toPath(),outside.toPath())
            assertThrows(IllegalArgumentException::class.java){store.staging(File(dir,"link/file"))}
            store.save(listOf(record().copy(downloadState=DownloadState.COMPLETED,verificationState="SHA256_VERIFIED")))
            assertEquals(DownloadState.RECOVERY_REQUIRED,store.recover().single().downloadState)
        }finally{outside.deleteRecursively()}
    }
    @Test fun interruptedPrefixIsRehashedAndDamageRemainsRecoveryRequired()=fixture{dir,store->
        val target=File(dir,"archive");val part=File(target.path+".part").apply{writeText("prefix")}
        val source="b".repeat(64);val expected="c".repeat(64)
        File(part.path+".identity").writeText(source+"\n"+expected)
        File(part.path+".sha256").writeText(VerifiedModuleDownload.sha(part))
        store.save(listOf(record().copy(verifiedPartialBytes=99)))
        val recovered=store.recover().single();assertEquals(DownloadState.PAUSED,recovered.downloadState);assertEquals(6L,recovered.verifiedPartialBytes)
        part.appendText("tamper");assertEquals(DownloadState.RECOVERY_REQUIRED,store.recover().single().downloadState)
    }
    @Test fun diskFailureAndTemporarySymlinkPreservePreviousRecord()=fixture{dir,store->
        store.save(listOf(record()))
        val before=File(dir,"journal/artifacts.json").readBytes()
        val full=PersistentDownloads(File(dir,"journal"),mapOf("private" to dir),availableBytes={0})
        assertThrows(IllegalArgumentException::class.java){full.save(listOf(record().copy(retryCount=3)))}
        assertArrayEquals(before,File(dir,"journal/artifacts.json").readBytes())
        Files.createSymbolicLink(File(dir,"journal/artifacts.json.tmp").toPath(),File(dir,"archive").toPath())
        assertThrows(IllegalArgumentException::class.java){store.save(listOf(record()))}
        assertArrayEquals(before,File(dir,"journal/artifacts.json").readBytes())
    }
    @Test fun duplicateArtifactAndUnknownRootsRejected()=fixture{_,store->
        assertThrows(IllegalArgumentException::class.java){store.save(listOf(record(),record()))}
        assertThrows(IllegalStateException::class.java){store.save(listOf(record("unknown:archive")))}
    }
}
