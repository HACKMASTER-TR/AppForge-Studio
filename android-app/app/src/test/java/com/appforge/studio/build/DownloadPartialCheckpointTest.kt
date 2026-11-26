package com.appforge.studio.build
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class DownloadPartialCheckpointTest {
    @Test fun hardDeathTailIsNotTrustedAndOnlyVerifiedPrefixMayBeResumed() {
        val dir=Files.createTempDirectory("kill-checkpoint").toFile()
        try {
            val part=File(dir,"archive.part").apply{writeText("verified")};val hash=VerifiedModuleDownload.sha(part)
            DownloadPartialCheckpoint.write(part,8,hash,"a".repeat(64),"b".repeat(64))
            part.appendText("unsealed tail")
            assertEquals(8L,DownloadPartialCheckpoint.recover(part,"a".repeat(64),"b".repeat(64),100))
            assertTrue(part.length()>8) // journal reconciliation does not mutate files based only on a record
            assertEquals(8L,DownloadPartialCheckpoint.recover(part,"a".repeat(64),"b".repeat(64),100,trim=true))
            assertEquals("verified",part.readText())
            part.writeText("tampered");assertNull(DownloadPartialCheckpoint.recover(part,"a".repeat(64),"b".repeat(64),100,trim=true))
            assertEquals("tampered",part.readText())
        }finally{dir.deleteRecursively()}
    }
    @Test fun sourceMismatchAndSymlinkCheckpointCannotAuthorizeResume() {
        val dir=Files.createTempDirectory("checkpoint-source").toFile()
        try {
            val part=File(dir,"archive.part").apply{writeText("verified")}
            DownloadPartialCheckpoint.write(part,8,VerifiedModuleDownload.sha(part),"a".repeat(64),"b".repeat(64))
            assertNull(DownloadPartialCheckpoint.recover(part,"c".repeat(64),"b".repeat(64),100))
            val file=File(part.path+".checkpoint");file.delete();Files.createSymbolicLink(file.toPath(),part.toPath())
            assertThrows(IllegalArgumentException::class.java){DownloadPartialCheckpoint.recover(part,"a".repeat(64),"b".repeat(64),100)}
        }finally{dir.deleteRecursively()}
    }
}
