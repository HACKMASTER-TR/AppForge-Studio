package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PortableLifecycleRecoveryTest {
    private fun fixture(test: (File,File,String)->Unit) {
        val base=Files.createTempDirectory("portable-lifecycle").toFile()
        try {
            val active=File(base,"offline-build-pack/windows-host-v1").apply{mkdirs()}
            val host=File(active,"AppForge-Windows-Host-v1.exe").apply{writeText("host fixture")}
            test(base,active,VerifiedModuleDownload.sha(host))
        } finally { base.deleteRecursively() }
    }
    @Test fun failedRemovalRestoresHostAndUnexpectedUserFile()=fixture { base,active,sha ->
        assertThrows(IllegalStateException::class.java) {
            OwnedModuleFiles.removePortable(base,sha) {
                val saved=active.parentFile.listFiles()!!.single{it.name.startsWith(".portable-removal-")}
                File(saved,"user.txt").writeText("preserve")
                error("interrupted")
            }
        }
        assertEquals("preserve",File(active,"user.txt").readText())
        assertEquals(sha,VerifiedModuleDownload.sha(File(active,"AppForge-Windows-Host-v1.exe")))
        assertNotNull(OwnedModuleFiles.portableRemovalReason(base,sha))
    }
    @Test fun completedRemovalArchivesBytesAndDoesNotTouchProjects()=fixture { base,active,sha ->
        val project=File(base,"projects/source.html").apply{parentFile.mkdirs();writeText("user source")}
        OwnedModuleFiles.removePortable(base,sha)
        assertFalse(active.exists())
        val saved=active.parentFile.listFiles()!!.single{it.name.startsWith(".portable-removal-")}
        assertEquals(sha,VerifiedModuleDownload.sha(File(saved,"AppForge-Windows-Host-v1.exe")))
        assertEquals("user source",project.readText())
        OwnedModuleFiles.recoverPortable(base)
        assertFalse(active.exists())
    }
    @Test fun processRestartRecoversPreparedArchive()=fixture { base,active,sha ->
        val saved=File(active.parentFile,".portable-removal-00000000-0000-0000-0000-000000000000")
        assertTrue(active.renameTo(saved))
        File(active.parentFile,".portable-lifecycle.json").writeText(JSONObject().put("schema",1).put("archive",saved.name).put("sha256",sha).put("state","PREPARED").toString())
        OwnedModuleFiles.recoverPortable(base)
        assertTrue(active.isDirectory)
        assertFalse(saved.exists())
    }
    @Test fun symlinkAndForeignOwnershipFailClosed()=fixture { base,active,sha ->
        val outside=File(base,"key").apply{writeText("private fixture")}
        Files.createSymbolicLink(File(active,"windows-host.ready").toPath(),outside.toPath())
        assertNotNull(OwnedModuleFiles.portableRemovalReason(base,sha))
        assertThrows(IllegalArgumentException::class.java){OwnedModuleFiles.removePortable(base,sha)}
        assertEquals("private fixture",outside.readText())
    }
}
