package com.appforge.studio.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class V9R2AptInstallReuseTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun fixture(bytes: ByteArray): Triple<File,File,V9SelectiveR2ModuleCache.Package> {
        val cache = tmp.newFolder("cache")
        val stage = tmp.newFolder("stage")
        val sha = hash(bytes)
        File(cache, sha).writeBytes(bytes)
        return Triple(cache, stage, V9SelectiveR2ModuleCache.Package(sha, bytes.size.toLong(), "deb"))
    }

    private fun archived(root: File, sha: String): File = File(
        root, "opt/appforge-device/apt-snapshot-v1/arm64-${AptModuleLocks.SNAPSHOT}/archives/$sha.deb"
    )

    @Test fun seedsVerifiedBytesIntoIsolatedAptCAS() {
        val bytes = "pinned deb payload".toByteArray()
        val (cache, stage, record) = fixture(bytes)
        val result = V9R2AptInstallReuse.seedExisting(cache, stage, "arm64", listOf(record))
        assertEquals(1, result.seeded)
        assertEquals(0, result.missing)
        assertEquals(bytes.size.toLong(), result.verifiedBytes)
        assertTrue(archived(stage, record.sha256).readBytes().contentEquals(bytes))
    }

    @Test fun repeatedImportsReuseSameCASWithoutRewriting() {
        val (cache, stage, record) = fixture("stable reusable package".toByteArray())
        V9R2AptInstallReuse.seedExisting(cache, stage, "arm64", listOf(record))
        val old = archived(stage, record.sha256).lastModified()
        val again = V9R2AptInstallReuse.seedExisting(cache, stage, "arm64", listOf(record))
        assertEquals(0, again.seeded)
        assertEquals(1, again.alreadyPresent)
        assertEquals(old, archived(stage, record.sha256).lastModified())
    }

    @Test fun missingObjectsAreNeverFabricatedOrDownloaded() {
        val cache = tmp.newFolder("cache-empty")
        val stage = tmp.newFolder("stage-empty")
        val record = V9SelectiveR2ModuleCache.Package("a".repeat(64), 50, "deb")
        val result = V9R2AptInstallReuse.seedExisting(cache, stage, "arm64", listOf(record))
        assertEquals(1, result.missing)
        assertEquals(0, result.seeded)
        assertFalse(File(stage, "opt").exists())
    }

    @Test fun sameSizeTamperedSourceFailsClosed() {
        val (cache, stage, record) = fixture("valid-bytes".toByteArray())
        File(cache, record.sha256).writeBytes("badly-bytes".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            V9R2AptInstallReuse.seedExisting(cache, stage, "arm64", listOf(record))
        }
        assertFalse(archived(stage, record.sha256).exists())
    }

    @Test fun corruptExistingTargetIsNeverOverwritten() {
        val (cache, stage, record) = fixture("safe-content".toByteArray())
        val target = archived(stage, record.sha256)
        target.parentFile!!.mkdirs()
        target.writeBytes("evil-content".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            V9R2AptInstallReuse.seedExisting(cache, stage, "arm64", listOf(record))
        }
        assertEquals("evil-content", target.readText())
    }

    @Test fun rejectsSourceAndTargetSymlinks() {
        val (cache, stage, record) = fixture("safe-deb".toByteArray())
        val target = archived(stage, record.sha256)
        target.parentFile!!.mkdirs()
        Files.createSymbolicLink(target.toPath(), File(cache, record.sha256).toPath())
        assertThrows(IllegalArgumentException::class.java) {
            V9R2AptInstallReuse.seedExisting(cache, stage, "arm64", listOf(record))
        }
    }

    @Test fun rejectsSymlinkedCacheParentAndInvalidArchitecture() {
        val (cache, stage, record) = fixture("data".toByteArray())
        val alias = File(tmp.root, "alias")
        Files.createSymbolicLink(alias.toPath(), cache.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            V9R2AptInstallReuse.seedExisting(alias, stage, "arm64", listOf(record))
        }
        assertThrows(IllegalArgumentException::class.java) {
            V9R2AptInstallReuse.seedExisting(cache, stage, "arm32", listOf(record))
        }
    }

    @Test fun refusesNonDebObjectsInAptArchive() {
        val (cache, stage, record) = fixture("InRelease data".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            V9R2AptInstallReuse.seedExisting(cache, stage, "arm64", listOf(record.copy(kind="InRelease")))
        }
    }
}
