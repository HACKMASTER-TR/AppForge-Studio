package com.appforge.studio.build

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VerifiedCacheTelemetryTest {
    @Test fun completedHttpTransferReportsBodyBytesButCacheHitReportsZero() {
        val dir=Files.createTempDirectory("cache-telemetry").toFile()
        try {
            val source=File(dir,"payload").apply{writeText("fixture payload")}
            val target=File(dir,"archive");val bytes=source.readBytes();var gets=0
            val sha=VerifiedModuleDownload.sha(source);val events=mutableListOf<ModuleProgress>()
            val open:(URL)->HttpURLConnection={url->gets++;object:HttpURLConnection(url){
                override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=bytes.size.toLong()
                override fun getInputStream()=bytes.inputStream()
            }}
            VerifiedModuleDownload.download("https://nodejs.org/dist/fixture",sha,target,OfflineModuleId.NODE_WEB,"first",
                open=open,progress={events.add(it)},expectedBytes=bytes.size.toLong())
            assertEquals(1,gets)
            assertTrue(events.any{it.networkGetCount==1L && it.networkBytes==bytes.size.toLong()})
            events.clear()
            VerifiedModuleDownload.download("https://nodejs.org/dist/fixture",sha,target,OfflineModuleId.NODE_WEB,"reuse",
                open={gets++;error("Must stay offline")},progress={events.add(it)},expectedBytes=bytes.size.toLong())
            assertEquals(1,gets)
            assertTrue(events.any{it.cacheHit && it.networkGetCount==0L && it.networkBytes==0L})
            assertFalse(events.any{it.phase==ModulePhase.CONNECTING || it.phase==ModulePhase.READY})
            println("CACHE_FIXTURE first_gets=1 first_body_bytes=${bytes.size} reuse_gets=0 reuse_bytes=0 hit_count=1 request_count=2")
        } finally {dir.deleteRecursively()}
    }
    @Test fun sameSignedObjectReusesLegacyCasLocationAndPreservesItAfterFailedInstall() {
        val dir=Files.createTempDirectory("cross-cache").toFile()
        try {
            val privateRoot=File(dir,"private").apply{mkdir()}
            val cas=File(privateRoot,"v9-signed-r2-canary").apply{mkdir()}
            val source=File(dir,"source").apply{writeText("signed object bytes")}
            val sha=VerifiedModuleDownload.sha(source);val old=File(cas,sha);source.copyTo(old)
            val runtime=File(dir,"runtime").apply{mkdir()};VerifiedArtifactCache.configure(privateRoot,runtime)
            val target=File(runtime,".module-archive-cache-v5/$sha.archive")
            var gets=0
            VerifiedModuleDownload.download("https://nodejs.org/dist/fixture",sha,target,OfflineModuleId.NODE_WEB,"cross-cache",expectedBytes=old.length(),open={gets++;error("No GET allowed")})
            assertEquals(0,gets);assertEquals(sha,VerifiedModuleDownload.sha(target));assertEquals(sha,VerifiedModuleDownload.sha(old))
            assertFalse(VerifiedArtifactCache.matches(target,sha,old.length()+1))
            val corrupt=File(runtime,"corrupt").apply{writeText("untrusted bytes")}
            assertFalse(VerifiedArtifactCache.reuse(corrupt,sha,old.length(),1024))
            assertEquals("untrusted bytes",corrupt.readText())
        } finally {dir.deleteRecursively()}
    }
}
