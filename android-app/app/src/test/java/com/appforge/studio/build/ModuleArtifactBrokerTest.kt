package com.appforge.studio.build
import java.io.File
import java.net.*
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class ModuleArtifactBrokerTest {
    private class Connection(url:URL,private val bytes:ByteArray,private val code:Int=200,private val location:String?=null):HttpURLConnection(url) {
        override fun getResponseCode()=code
        override fun getContentLengthLong()=bytes.size.toLong()
        override fun getHeaderField(name:String?)=if(name=="Location")location else null
        override fun getInputStream()=bytes.inputStream()
        override fun connect(){}
        override fun disconnect(){}
        override fun usingProxy()=false
    }
    @Test fun nativeBrokerPublishesOnlyHashVerifiedBytesAndReusesItsPrivateCache() = runBlocking {
        val base=Files.createTempDirectory("artifact-broker").toFile()
        try {
            val workspace=File(base,"work").apply{mkdir()};val directory=File(workspace,"native-downloads").apply{mkdir()}
            val source=File(base,"payload").apply{writeText("pinned sdk fixture")};val sha=VerifiedModuleDownload.sha(source);var opens=0
            val broker=ModuleArtifactBroker(workspace,File(base,"cache"),OfflineModuleId.ANDROID_CORE,"op",{},open={opens++;Connection(it,source.readBytes())})
            val service=launch(Dispatchers.IO){broker.serve()}
            try {
                repeat(2){n->val id=(if(n==0)"a" else "b").repeat(64);File(directory,"$id.request").writeText("https://dl.google.com/android/repository/fixture.zip\t$sha\n")
                    withTimeout(5000){while(!File(directory,"$id.ok").exists())delay(10)}
                    assertEquals(source.readText(),File(directory,"$id.archive").readText())
                }
                assertEquals(1,opens)
                val bad="c".repeat(64);File(directory,"$bad.request").writeText("https://example.invalid/arbitrary\t$sha\n")
                withTimeout(5000){while(!File(directory,"$bad.error").exists())delay(10)}
                assertFalse(File(directory,"$bad.ok").exists());assertEquals(1,opens)
            }finally{service.cancelAndJoin()}
        }finally{base.deleteRecursively()}
    }
    @Test fun redirectChainPreservesRangeHeadersAndRejectsInsecureOrUnlistedLocations() {
        var final:Connection?=null
        val url=URL("https://github.com/adoptium/temurin17-binaries/releases/download/test/jdk.tar.gz")
        val connection=ModuleArtifactBroker.Companion.RedirectConnection(url){address->
            if(address.host=="github.com")Connection(address,byteArrayOf(),302,"https://release-assets.githubusercontent.com/pinned")
            else Connection(address,byteArrayOf(1,2,3)).also{final=it}
        }
        connection.setRequestProperty("Range","bytes=7-");connection.setRequestProperty("If-Range","etag")
        assertEquals(200,connection.responseCode);assertEquals("bytes=7-",final!!.getRequestProperty("Range"));assertEquals("etag",final!!.getRequestProperty("If-Range"))
        val unsafe=ModuleArtifactBroker.Companion.RedirectConnection(url){address->Connection(address,byteArrayOf(),302,"http://example.invalid/file")}
        assertThrows(IllegalArgumentException::class.java){unsafe.responseCode}
    }
}
