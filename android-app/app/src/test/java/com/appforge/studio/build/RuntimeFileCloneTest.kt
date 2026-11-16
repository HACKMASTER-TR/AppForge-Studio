package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RuntimeFileCloneTest {
    @Test fun capabilityProbeOrIndependentFallbackNeverSharesWritableInodes() {
        val dir=Files.createTempDirectory("reflink-capability").toFile()
        try {
            val available=RuntimeFileClone.supported(dir)
            val source=File(dir,"source").apply{writeText("original")}
            val target=File(dir,"clone")
            RuntimeFileClone.copy(source.toPath(),target.toPath(),available)
            assertFalse(Files.isSameFile(source.toPath(),target.toPath()))
            source.writeText("active");assertEquals("original",target.readText())
            target.writeText("staged");assertEquals("active",source.readText())
            println("CLONE_CAPABILITY reflink=$available allocated_bytes=${RuntimeFileClone.allocatedBytes(target.path)}")
        } finally {dir.deleteRecursively()}
    }
}
