package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.*
import java.nio.file.Files
import java.security.MessageDigest

class VerifiedModuleDownloadTest {
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
    private class Connection(private val body:ByteArray,private val code:Int,private val range:String?=null,private val known:Boolean=true):HttpURLConnection(URL("https://node.example/test")) {
        var closed=false
        override fun connect(){};override fun usingProxy()=false;override fun disconnect(){closed=true}
        override fun getResponseCode()=code
        override fun getContentLengthLong()=if(known)body.size.toLong() else -1L
        override fun getHeaderField(name:String)=if(name=="Content-Range")range else null
        override fun getInputStream():InputStream=ByteArrayInputStream(body)
    }
    @Test fun realBytesKnownAndUnknownTotalsAndAtomicVerifiedPublication() {
        val dir=Files.createTempDirectory("module-test").toFile()
        try {
            val data="actual archive bytes".toByteArray();val target=File(dir,"node.tar.xz")
            val states=ArrayList<ModuleProgress>();val c=Connection(data,200,known=false)
            VerifiedModuleDownload.download("https://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"test",open={c},progress={states.add(it)})
            assertArrayEquals(data,target.readBytes());assertTrue(c.closed)
            assertTrue(states.filter{it.phase==ModulePhase.DOWNLOADING}.all{it.percent==null})
            assertEquals(ModulePhase.VERIFYING,states.last().phase);assertFalse(states.any{it.phase==ModulePhase.READY})
            VerifiedModuleDownload.download("https://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"cached",open={error("cached artifact must not download")})
        } finally {dir.deleteRecursively()}
    }
    @Test fun partialResumeAndIgnoredRangeRestartAreVerified() {
        for(resume in listOf(true,false)) {
            val dir=Files.createTempDirectory("module-resume").toFile()
            try {
                val data="abcdefghij".toByteArray();val target=File(dir,"node.tar.xz")
                File(target.path+".part").writeBytes(data.copyOfRange(0,4))
                File(target.path+".part.identity").writeText("https://node.example/test\n"+hash(data))
                val c=if(resume)Connection(data.copyOfRange(4,10),206,"bytes 4-9/10")else Connection(data,200)
                VerifiedModuleDownload.download("https://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"resume",open={c})
                assertEquals("bytes=4-",c.getRequestProperty("Range"));assertArrayEquals(data,target.readBytes())
            } finally {dir.deleteRecursively()}
        }
    }
    @Test fun digestFailureCancelAndForeignRangesNeverPublish() {
        val dir=Files.createTempDirectory("module-denials").toFile()
        try {
            val data="archive".toByteArray();val target=File(dir,"node.tar.xz");target.writeText("old working file")
            assertThrows(IllegalStateException::class.java){VerifiedModuleDownload.download("https://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"bad",open={Connection("tamper".toByteArray(),200)})}
            assertEquals("old working file",target.readText());assertFalse(File(target.path+".part").exists())
            assertThrows(java.util.concurrent.CancellationException::class.java){VerifiedModuleDownload.download("https://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"cancel",canceled={true},open={error("must not connect")})}
            assertThrows(IllegalArgumentException::class.java){VerifiedModuleDownload.download("http://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"unsafe",open={error("must not connect")})}
            assertThrows(IllegalArgumentException::class.java){VerifiedModuleDownload.download("https://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"range",open={Connection(data,206,"bytes 3-9/10")})}
            assertEquals("old working file",target.readText())
        } finally {dir.deleteRecursively()}
    }
    @Test fun completePartialPromotesWithoutNetworkAndSymlinksAreRejected() {
        val dir=Files.createTempDirectory("module-paths").toFile()
        try {
            val data="already complete".toByteArray();val target=File(dir,"node.tar.xz")
            File(target.path+".part").writeBytes(data)
            File(target.path+".part.identity").writeText("https://node.example/test\n"+hash(data))
            VerifiedModuleDownload.download("https://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"complete",open={error("complete partial must not connect")})
            assertArrayEquals(data,target.readBytes())
            val link=File(dir,"link.tar.xz")
            Files.createSymbolicLink(link.toPath(),target.toPath())
            assertThrows(IllegalArgumentException::class.java){VerifiedModuleDownload.download("https://node.example/test",hash(data),link,OfflineModuleId.NODE_WEB,"link",open={error("must not connect")})}
            assertArrayEquals(data,target.readBytes())
        } finally {dir.deleteRecursively()}
    }
    @Test fun truncatedTransferCannotPublishOrDestroyWorkingFile() {
        val dir=Files.createTempDirectory("module-short").toFile()
        try {
            val data="abcdefghij".toByteArray();val target=File(dir,"node.tar.xz");target.writeText("working")
            val c=object:HttpURLConnection(URL("https://node.example/test")) {
                override fun connect(){};override fun usingProxy()=false;override fun disconnect(){}
                override fun getResponseCode()=200
                override fun getContentLengthLong()=10L
                override fun getInputStream():InputStream=ByteArrayInputStream(data.copyOf(4))
            }
            assertThrows(IllegalArgumentException::class.java){VerifiedModuleDownload.download("https://node.example/test",hash(data),target,OfflineModuleId.NODE_WEB,"short",open={c})}
            assertEquals("working",target.readText());assertEquals(4L,File(target.path+".part").length())
        } finally {dir.deleteRecursively()}
    }

}
