package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Uses separate JVM processes and the actual OS lock/journal, never the user's runtime. */
class RuntimeProcessRecoveryTest {
    private fun child(mode:String,path:File):Process {
        val classpath=listOf(RuntimeProcessProbe::class.java,RuntimeMutationLock::class.java,
            Unit::class.java,JSONObject::class.java).map {
            File(it.protectionDomain.codeSource.location.toURI()).absolutePath
        }.distinct().joinToString(File.pathSeparator)
        val java=File(System.getProperty("java.home"),"bin/java").absolutePath
        return ProcessBuilder(java,"-cp",classpath,RuntimeProcessProbe::class.java.name,mode,path.absolutePath)
            .redirectErrorStream(true).start()
    }
    @Test fun separateProcessCannotAcquireHeldRuntimeLock() {
        val parent=Files.createTempDirectory("process-lock").toFile()
        try {
            RuntimeMutationLock.acquire(parent).use {
                val process=child("probe",parent)
                val output=process.inputStream.bufferedReader().readText()
                assertEquals(output,0,process.waitFor());assertEquals("LOCKED",output.trim())
            }
            val process=child("probe",parent);val output=process.inputStream.bufferedReader().readText()
            assertEquals(output,0,process.waitFor());assertEquals("ACQUIRED",output.trim())
        } finally {parent.deleteRecursively()}
    }
    @Test fun abruptProcessDeathReleasesLockAndRecoversWithoutLosingActiveFiles() {
        val parent=Files.createTempDirectory("process-crash").toFile()
        try {
            val root=File(parent,"rootfs").apply{mkdir()};File(root,"vendor").writeText("keep")
            val store=RuntimeGenerationStore(root);val before=store.snapshot()
            val process=child("crash-stage",root);val output=process.inputStream.bufferedReader().readText()
            assertEquals(output,17,process.waitFor())
            assertTrue(File(parent,".module-generation.json").isFile)
            store.recover();store.recover()
            assertEquals(before,store.snapshot())
            assertEquals(1,store.quarantinedDirectories().size)
            assertFalse(File(parent,".module-generation.json").exists())
            assertThrows(IllegalArgumentException::class.java){store.prepare(OfflineModuleId.NODE_WEB)}
        } finally {parent.deleteRecursively()}
    }
}

internal object RuntimeProcessProbe {
    @JvmStatic fun main(args:Array<String>) {
        if(args[0]=="probe") {
            try {RuntimeMutationLock.acquire(File(args[1])).use{println("ACQUIRED")}}
            catch(_:IllegalStateException){println("LOCKED")}
            return
        }
        val tx=RuntimeGenerationStore(File(args[1])).prepare(OfflineModuleId.NODE_WEB)
        File(tx.root,"interrupted-install").writeText("preserved partial installation")
        Runtime.getRuntime().halt(17) // Deliberately bypasses finally/close to exercise real recovery.
    }
}
