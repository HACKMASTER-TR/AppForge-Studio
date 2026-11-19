package com.appforge.studio.build

import java.io.*
import java.net.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class V9R2ObjectTransferTest {
    private class Response(url:URL,val payload:ByteArray,val status:Int=206,
        val truncate:Boolean=false,val wrongRange:Boolean=false):HttpURLConnection(url) {
        private fun range()=getRequestProperty("Range").removePrefix("bytes=").split('-').map{it.toInt()}
        override fun getResponseCode()=status
        override fun getContentLengthLong():Long {val r=range();return (r[1]-r[0]+1).toLong()}
        override fun getHeaderField(name:String?):String? {
            val r=range()
            return if(name=="Content-Range")"bytes ${if(wrongRange)r[0]+1 else r[0]}-${r[1]}/${payload.size}" else null
        }
        override fun getInputStream():InputStream {
            val r=range();val data=payload.copyOfRange(r[0],r[1]+1)
            return data.copyOf(if(truncate)minOf(17,data.size) else data.size).inputStream()
        }
        override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
    }
    private fun fixture(body:(File,ByteArray,String)->Unit) {
        val dir=Files.createTempDirectory("r2-transfer").toFile()
        try {
            val bytes="real digest bytes for an immutable R2 object".toByteArray()
            val input=File(dir,"source").apply{writeBytes(bytes)}
            body(File(dir,"cache/object"),bytes,VerifiedModuleDownload.sha(input))
        } finally {dir.deleteRecursively()}
    }
    @Test fun verifiedCacheReinstallNeverOpensNetwork()=fixture {target,bytes,sha->
        V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={Response(it,bytes)})
        val before=target.lastModified()
        V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={error("offline cache must not open network")})
        assertArrayEquals(bytes,target.readBytes());assertEquals(before,target.lastModified())
    }
    @Test fun interruptedConnectionResumesFromExactDurablePrefix()=fixture {target,bytes,sha->
        var opens=0
        val requested=mutableListOf<String>()
        V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={url->
            val truncated=++opens==1
            object:HttpURLConnection(url) {
                private fun range():Pair<Int,Int> {
                    val parts=getRequestProperty("Range").removePrefix("bytes=").split('-')
                    return parts[0].toInt() to parts[1].toInt()
                }
                override fun getResponseCode():Int {requested+=getRequestProperty("Range");return 206}
                override fun getContentLengthLong():Long {val (a,b)=range();return (b-a+1).toLong()}
                override fun getHeaderField(name:String?):String? {val (a,b)=range()
                    return if(name=="Content-Range")"bytes $a-$b/${bytes.size}" else null}
                override fun getInputStream():InputStream {val (a,b)=range();val remaining=bytes.copyOfRange(a,b+1)
                    return remaining.copyOf(if(truncated)minOf(17,remaining.size) else remaining.size).inputStream()}
                override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
            }
        })
        assertEquals(2,opens)
        assertEquals(listOf("bytes=0-${bytes.size-1}","bytes=17-${bytes.size-1}"),requested)
        assertArrayEquals(bytes,target.readBytes())
    }
    @Test fun repeatedConnectionResetsHaveBoundedRetriesAndKeepPrefix()=fixture {target,bytes,sha->
        var opens=0
        assertThrows(SocketException::class.java) {
            V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={url->
                if(++opens==1) Response(url,bytes,truncate=true)
                else throw SocketException("Connection reset")
            })
        }
        assertEquals(4,opens)
        assertFalse(target.exists())
        assertEquals(17L,File(target.path+".part").length())
    }
    @Test fun digestFailureNeverPromotesOrPoisonsRetry()=fixture {target,bytes,sha->
        val bad=bytes.copyOf().apply{this[0]=(this[0].toInt() xor 1).toByte()}
        assertThrows(SecurityException::class.java){V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={Response(it,bad)})}
        assertFalse(target.exists());assertFalse(File(target.path+".part").exists())
        V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={Response(it,bytes)})
        assertArrayEquals(bytes,target.readBytes())
    }
    @Test fun corruptCompleteCacheIsPreservedAndRejected()=fixture {target,bytes,sha->
        target.parentFile.mkdirs();target.writeBytes(ByteArray(bytes.size))
        assertThrows(IllegalArgumentException::class.java){V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={error("cache rejection before network")})}
        assertArrayEquals(ByteArray(bytes.size),target.readBytes())
    }
    @Test fun missingObjectNeverFallsBackToSnapshot()=fixture {target,bytes,sha->
        var opens=0
        assertThrows(IOException::class.java){V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={opens++;assertEquals("appforge-v9-packages-readonly.28550040284a.workers.dev",it.host);Response(it,bytes,404)})}
        assertEquals(1,opens);assertFalse(target.exists())
    }
    @Test fun invalidRangeNeverWritesPayload()=fixture {target,bytes,sha->
        assertThrows(IllegalArgumentException::class.java){V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={Response(it,bytes,wrongRange=true)})}
        assertFalse(target.exists());assertFalse(File(target.path+".part").exists())
    }
    @Test fun lowStorageAndPausePreserveActiveFiles()=fixture {target,bytes,sha->
        assertThrows(IllegalArgumentException::class.java){V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),available={0},open={error("ENOSPC before request")})}
        assertThrows(java.util.concurrent.CancellationException::class.java){V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),canceled={true},open={error("pause before request")})}
        assertFalse(target.exists())
    }
    @Test fun targetSymlinkCannotOverwriteUserProject()=fixture {target,bytes,sha->
        target.parentFile.mkdirs();val user=File(target.parentFile,"project.js").apply{writeText("preserve")}
        Files.createSymbolicLink(target.toPath(),user.toPath())
        assertThrows(IllegalArgumentException::class.java){V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={error("symlink before network")})}
        assertEquals("preserve",user.readText())
    }
    @Test fun explicitRetryQuarantinesBadCacheButPreservesValidObjects()=fixture {target,bytes,sha->
        val addressed=File(target.parentFile,sha);addressed.parentFile.mkdirs();addressed.writeText("bad bytes")
        assertThrows(IllegalStateException::class.java){V9R2ObjectTransfer.quarantineDamaged(addressed,sha,bytes.size.toLong(),false)}
        assertEquals("bad bytes",addressed.readText())
        assertTrue(V9R2ObjectTransfer.quarantineDamaged(addressed,sha,bytes.size.toLong(),true))
        assertFalse(addressed.exists())
        assertEquals("bad bytes",File(addressed.parentFile,".r2-corrupt").listFiles()!!.single().readText())
        V9R2ObjectTransfer.download(addressed,sha,bytes.size.toLong(),open={Response(it,bytes)})
        assertFalse(V9R2ObjectTransfer.quarantineDamaged(addressed,sha,bytes.size.toLong(),true))
        assertArrayEquals(bytes,addressed.readBytes())
    }
    @Test fun gatewayRangesRemainBoundedAcrossMultipleChunks() {
        val dir=Files.createTempDirectory("r2-large").toFile()
        try {
            val bytes=ByteArray(8*1024*1024+3){(it%251).toByte()}
            val input=File(dir,"source").apply{writeBytes(bytes)}
            var requests=0
            V9R2ObjectTransfer.download(File(dir,"object"),VerifiedModuleDownload.sha(input),bytes.size.toLong(),open={requests++;Response(it,bytes)})
            assertEquals(2,requests);assertArrayEquals(bytes,File(dir,"object").readBytes())
        } finally {dir.deleteRecursively()}
    }
    @Test fun tlsFailureIsNeverRetriedWithWeakerTrust()=fixture {target,bytes,sha->
        var opens=0
        assertThrows(javax.net.ssl.SSLHandshakeException::class.java){V9R2ObjectTransfer.download(target,sha,bytes.size.toLong(),open={opens++;throw javax.net.ssl.SSLHandshakeException("fixture trust failure")})}
        assertEquals(1,opens);assertFalse(target.exists())
    }
    @Test fun snapshotAndForgedR2PathsAreNotNativeTransportOrigins() {
        assertFalse(ModuleArtifactBroker.allowed(URL("https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/pool/demo.deb")))
        assertFalse(ModuleArtifactBroker.allowed(URL("https://appforge-v9-packages-readonly.28550040284a.workers.dev/v9/objects/sha256/aa/"+"b".repeat(64))))
        assertTrue(ModuleArtifactBroker.allowed(URL(V9SignedR2CandidateDownload.urlForSha("a".repeat(64)))))
    }
}
