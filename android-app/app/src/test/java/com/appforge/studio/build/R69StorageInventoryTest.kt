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
