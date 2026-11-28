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
    @Test fun realArm64BrokerPathsContainExactShaForEveryModule() {
        val home=Files.createTempDirectory("r72-paths").toFile()
        try {
            val paths=R72BrokerArchives.candidatePaths(home,com.appforge.studio.terminal.LinuxArchitecture.ARM64)
            assertEquals(30,paths.size)
            val sha="adb1c01115c3b11d2a5cde43b17ce3c53fa7cc48f834b930f63910d2c7dc74f5"
            val file=File(home,"isolated-modules-v1/android_core/rootfs/ubuntu/arm64/.module-archive-cache-v5/"+sha+".archive")
            assertEquals(sha,paths.getValue(file).sha)
            assertTrue(paths.all{(f,p)->f.name==p.sha+".archive"})
            val other=File(home,"isolated-modules-v1/expo_react_native/rootfs/ubuntu/arm64/.module-archive-cache-v5/"+sha+".archive")
            assertTrue(other in paths)
            file.parentFile!!.mkdirs()
            file.writeText("deliberately wrong bytes")
            val result=R70SharedArchives.migrate(home,paths,storeName=R72BrokerArchives.DIRECTORY)
            assertEquals(30,result.planned);assertEquals(1,result.found)
            assertEquals(1,result.skipped);assertTrue(file.isFile)
        } finally {home.deleteRecursively()}
    }
    @Test fun missingCandidatesAreReportedSeparatelyFromSkipped() {
        val home=Files.createTempDirectory("r72-empty").toFile()
        try {
            val paths=R72BrokerArchives.candidatePaths(home,com.appforge.studio.terminal.LinuxArchitecture.ARM64)
            val result=R70SharedArchives.migrate(home,paths,storeName=R72BrokerArchives.DIRECTORY)
            assertEquals(30,result.planned);assertEquals(0,result.found)
            assertEquals(0,result.redundant);assertEquals(0L,result.logicalRemoved)
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
