package com.appforge.studio.build

import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.*

class R70SharedArchivesTest {
    @Test fun pinnedHostCopiesConvergeWithoutTouchingGuest() {
        val home=Files.createTempDirectory("r70").toFile().canonicalFile
        try {
            val a=home.resolve("isolated-modules-v1/node_web/downloads/a").apply{parentFile.mkdirs();writeText("payload")}
            val b=home.resolve("isolated-modules-v1/expo_react_native/downloads/a").apply{parentFile.mkdirs();writeText("payload")}
            val guest=home.resolve("isolated-modules-v1/node_web/rootfs/guest").apply{parentFile.mkdirs();writeText("payload")}
            val unknown=home.resolve("isolated-modules-v1/node_web/downloads/unknown").apply{writeText("payload")}
            val pin=R70SharedArchives.Pin("a",VerifiedModuleDownload.sha(a),a.length())
            val r=R70SharedArchives.migrate(home,linkedMapOf(a to pin,b to pin))
            assertEquals(1,r.moved)
            assertEquals(1,r.redundant)
            assertEquals(7L,r.logicalRemoved)
            assertFalse(a.exists())
            assertFalse(b.exists())
            assertEquals("payload",R70SharedArchives.objectFile(R70SharedArchives.directory(home),pin.sha).readText())
            assertEquals("payload",guest.readText())
            assertEquals("payload",unknown.readText())
        } finally {home.deleteRecursively()}
    }
    @Test fun corruptCentralObjectIsNeverOverwritten() {
        val home=Files.createTempDirectory("r70-bad").toFile().canonicalFile
        try {
            val a=home.resolve("isolated-modules-v1/node_web/downloads/a").apply{parentFile.mkdirs();writeText("payload")}
            val pin=R70SharedArchives.Pin("a",VerifiedModuleDownload.sha(a),a.length())
            val central=R70SharedArchives.objectFile(R70SharedArchives.directory(home),pin.sha).apply{writeText("damaged")}
            val r=R70SharedArchives.migrate(home,mapOf(a to pin))
            assertEquals(1,r.skipped)
            assertEquals(0L,r.logicalRemoved)
            assertEquals("payload",a.readText())
            assertEquals("damaged",central.readText())
        } finally {home.deleteRecursively()}
    }
}
