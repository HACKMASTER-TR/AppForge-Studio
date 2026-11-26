package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RecoveryPathAnchorAliasTest {
    @Test fun onlyExplicitTrustedAliasCanBeNormalizedForStaging() {
        val dir=Files.createTempDirectory("appforge-alias").toFile()
        val root=File(dir,"user0").apply{mkdirs()}
        val alias=File(dir,"data")
        val foreign=File(dir,"foreign").apply{mkdirs()}
        try {
            Files.createSymbolicLink(alias.toPath(),root.toPath())
            val requested=File(alias,"com.appforge.studio/no_backup/cache.tar.gz")
            assertThrows(IllegalArgumentException::class.java) { RecoveryPathAnchor.check(requested) }
            val normalized=RecoveryPathAnchor.knownPlatformAlias(requested,alias,root)
            assertEquals(File(root,"com.appforge.studio/no_backup/cache.tar.gz").absolutePath,normalized.absolutePath)
            val store=PersistentDownloads(File(root,"downloads-v5"),mapOf("private" to root))
            assertEquals("private:com.appforge.studio/no_backup/cache.tar.gz",store.staging(normalized))
            Files.createSymbolicLink(File(root,"nested").toPath(),foreign.toPath())
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPathAnchor.knownPlatformAlias(File(alias,"nested/escape"),alias,root)
            }
            Files.delete(alias.toPath())
            Files.createSymbolicLink(alias.toPath(),foreign.toPath())
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPathAnchor.knownPlatformAlias(requested,alias,root)
            }
        } finally {
            Files.deleteIfExists(alias.toPath())
            dir.deleteRecursively()
        }
    }
}
