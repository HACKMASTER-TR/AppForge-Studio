package com.appforge.studio.build

import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.*

class R69StorageInventoryTest {
    @Test fun countsRealContentAndNeverFollowsLinks() {
        val root=Files.createTempDirectory("r69-scan").toFile()
        try {
            val node=root.resolve("no_backup/isolated-modules-v1/node_web/downloads").apply{mkdirs()}
            val rust=root.resolve("no_backup/isolated-modules-v1/rust/rootfs").apply{mkdirs()}
            val project=root.resolve("files/project").apply{mkdirs()}
            node.resolve("a").writeBytes(byteArrayOf(1,2,3))
            rust.resolve("b").writeBytes(byteArrayOf(1,2,3))
            rust.resolve("c").writeBytes(byteArrayOf(3,2,1))
            project.resolve("user").writeBytes(byteArrayOf(1,2,3))
            Files.createSymbolicLink(rust.resolve("link").toPath(),project.toPath())
            Files.createLink(rust.resolve("hardlink").toPath(),rust.resolve("b").toPath())
            val r=R69StorageInventory.scan(root,allocated={-1},minDuplicateBytes=1)
            assertEquals(15L,r.logicalBytes)
            assertEquals(5L,r.files)
            assertEquals(1L,r.links)
            assertEquals(1,r.duplicateGroups)
            assertEquals(3L,r.logicalExtraBytes)
            assertEquals(2,r.duplicates.single().copies)
            assertEquals(9L,r.hashedBytes)
            assertFalse(r.incomplete)
            assertNull(r.allocatedBlockSum)
            assertEquals(3L,r.byOwner.single{it.name=="NODE_WEB"}.bytes)
            assertEquals(9L,r.byOwner.single{it.name=="RUST"}.bytes)
            assertTrue(project.resolve("user").isFile)
        } finally {root.deleteRecursively()}
    }
    @Test fun r71EvidenceOnlyDisclosesReviewedToolPaths() {
        val root=Files.createTempDirectory("r71-scan").toFile()
        try {
            val a=root.resolve("no_backup/isolated-modules-v1/android_core/rootfs/opt/appforge-device/cache/gradle-9.3.1-bin.zip")
            val b=root.resolve("no_backup/isolated-modules-v1/expo_react_native/rootfs/opt/appforge-device/cache/gradle-9.3.1-bin.zip")
            val c=root.resolve("no_backup/isolated-modules-v1/rust/rootfs/workspace/private-project/secret.txt")
            for(f in listOf(a,b,c)){f.parentFile.mkdirs();f.writeText("same")}
            val r=R69StorageInventory.scan(root,allocated={0},minDuplicateBytes=1)
            val d=r.duplicates.single()
            assertEquals(3,d.copies)
            assertEquals(64,d.sha256.length)
            assertEquals(2,d.evidence.count{it.relativePath.endsWith("gradle-9.3.1-bin.zip")})
            assertEquals(1,d.evidence.count{it.relativePath=="[korumali yol]"})
            assertTrue(d.evidence.none{it.relativePath.contains("private-project")})
            assertTrue(d.evidence.none{it.relativePath.startsWith("/")})
            assertTrue(a.isFile && b.isFile && c.isFile)
        } finally {root.deleteRecursively()}
    }
    @Test fun boundedHashBudgetIsExplicitlyPartial() {
        val root=Files.createTempDirectory("r69-budget").toFile()
        try {
            val dir=root.resolve("no_backup/module-downloads").apply{mkdirs()}
            dir.resolve("a").writeText("same")
            dir.resolve("b").writeText("same")
            val r=R69StorageInventory.scan(root,allocated={0},minDuplicateBytes=1,maxHashBytes=4)
            assertTrue(r.incomplete)
            assertEquals(0,r.duplicateGroups)
            assertEquals(4L,r.hashedBytes)
            assertEquals(8L,r.logicalBytes)
        } finally {root.deleteRecursively()}
    }
}
