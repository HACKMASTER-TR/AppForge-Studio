package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.*

class R72BrokerArchivesTest {
    @Test fun routesOnlyReviewedR2ObjectsToSeparateHostStore() {
        val home=Files.createTempDirectory("r72-routing").toFile()
        try {
            val r70=R70SharedArchives.directory(home)
            val sha=R72BrokerArchives.reviewed.first()
            val target=R72BrokerArchives.target(r70,sha,true)!!
            assertEquals(R72BrokerArchives.DIRECTORY,target.parentFile!!.name)
            assertEquals(sha+".archive",target.name)
            assertNull(R72BrokerArchives.target(r70,sha,false))
            assertNull(R72BrokerArchives.target(null,sha,true))
            assertNull(R72BrokerArchives.target(r70,"a".repeat(64),true))
        } finally {home.deleteRecursively()}
    }
    @Test fun hostMigrationPreservesGuestAndCorruptCopies() {
        val home=Files.createTempDirectory("r72-migration").toFile()
        try {
            val a=File(home,"isolated-modules-v1/android_core/broker").apply{mkdirs()}
            val b=File(home,"isolated-modules-v1/expo_react_native/broker").apply{mkdirs()}
            val c=File(home,"isolated-modules-v1/rust/broker").apply{mkdirs()}
            val guest=File(home,"isolated-modules-v1/rust/rootfs/opt/cache").apply{mkdirs()}
            val raw=File(a,"fixture.archive").apply{writeText("verified bytes")}
            val sha=VerifiedModuleDownload.sha(raw)
            val x=File(a,sha+".archive");assertTrue(raw.renameTo(x))
            val y=File(b,x.name).apply{writeText("verified bytes")}
            val z=File(c,x.name).apply{writeText("different")}
            val g=File(guest,x.name).apply{writeText("verified bytes")}
            val pin=R70SharedArchives.Pin(x.name,sha)
            val result=R70SharedArchives.migrate(home,linkedMapOf(x to pin,y to pin,z to pin),storeName=R72BrokerArchives.DIRECTORY)
            assertEquals(1,result.moved);assertEquals(1,result.redundant);assertEquals(1,result.skipped)
            assertFalse(x.exists());assertFalse(y.exists());assertTrue(z.isFile);assertTrue(g.isFile)
            assertTrue(File(home,R72BrokerArchives.DIRECTORY+"/"+x.name).isFile)
        } finally {home.deleteRecursively()}
    }
}
