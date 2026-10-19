package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExternalTelemetryChannelsTest {
    @Test fun curlShimDelegatesWithoutTelemetryInsteadOfRecursingThroughPath() {
        val root=Files.createTempDirectory("curl-shim-").toFile()
        try {
            val script=File(root,"curl").apply{writeText(DownloadInstrumentation.curlShim());setExecutable(true)}
            val process=ProcessBuilder("/bin/sh",script.path,"--version").redirectErrorStream(true)
            process.environment().remove("APPFORGE_DOWNLOAD_TELEMETRY")
            process.environment()["PATH"]=root.path+":"+System.getenv("PATH")
            val child=process.start()
            try {
                assertTrue("shim must not recursively invoke itself",child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(0,child.exitValue());assertTrue(child.inputStream.bufferedReader().readText().startsWith("curl "))
            } finally {if(child.isAlive)child.destroyForcibly()}
        } finally {root.deleteRecursively()}
    }
    @Test fun independentChannelsPreserveTransfersAndIgnoreDuplicateUnchangedOrForeignFiles() {
        val root=Files.createTempDirectory("channels-").toFile()
        try {
            val primary=File(root,"progress.json")
            val folder=File(primary.path+".events").apply{mkdir()}
            val a=File(folder,"transfer-a.json")
            val b=File(folder,"transfer-b.json")
            fun event(id:String,n:Int)="""{"schema":1,"transfer":"$id","bytes":$n,"total":1000}"""
            a.writeText(event("a",0));b.writeText(event("b",0))
            primary.writeText(a.readText())
            val poll=ExternalTelemetryChannels(primary,OfflineModuleId.ANDROID_CORE,"op")
            assertEquals(2,poll.poll(0).size);assertTrue(poll.poll(500).isEmpty())
            a.writeText(event("a",500));b.writeText(event("b",250))
            val updates=poll.poll(1000);assertEquals(2,updates.size)
            assertEquals(setOf(250L,500L),updates.map{it.bytesDownloaded}.toSet())
            assertTrue(updates.all{it.speedBytesPerSecond!=null})
            a.writeText(event("a",1000).dropLast(1)+",\"finished\":true}")
            assertEquals(ModulePhase.PREPARING,poll.poll(1500).single().phase)
            a.writeText(event("a",600)) // timer sample racing behind its terminal channel
            assertTrue(poll.poll(1600).isEmpty())
            val foreign=File(root,"foreign").apply{writeText("preserved")}
            Files.createSymbolicLink(File(folder,"foreign.json").toPath(),foreign.toPath())
            File(folder,"oversize.json").writeText("x".repeat(3000))
            assertTrue(poll.poll(2000).isEmpty())
            ExternalTelemetryChannels.clear(primary)
            assertEquals("preserved",foreign.readText());assertFalse(primary.exists());assertFalse(a.exists())
        } finally {root.deleteRecursively()}
    }
}
