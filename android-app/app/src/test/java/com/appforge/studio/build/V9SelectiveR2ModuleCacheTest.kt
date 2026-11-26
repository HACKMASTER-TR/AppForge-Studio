package com.appforge.studio.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/** Offline-only regression. No Android device, Cloudflare calls, downloads, APK or signing. */
class V9SelectiveR2ModuleCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private val c = "c".repeat(64)
    private val d = "d".repeat(64)
    private val e = "e".repeat(64)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun row(m: String, arch: String, sum: String, size: Long = 1024, pkgArch: String = arch) =
        "$m\t$arch\tpackage\t1.0\t$pkgArch\t$sum\t$size\thttps://snapshot.ubuntu.com/ubuntu/${AptModuleLocks.SNAPSHOT}/pool/main/p/package.deb"
    private fun raw(vararg rows: String) = (rows.joinToString("\n") + "\n").toByteArray()
    private fun select(bytes: ByteArray, modules: Set<OfflineModuleId>, arch: String = "arm64") =
        V9SelectiveR2ModuleCache.selectRows(bytes, modules, arch, sha(bytes))

    @Test fun selectsOnlyRequestedArchitectureAndModule() {
        val data = raw(row("ANDROID_CORE", "arm64", a), row("ANDROID_CORE", "amd64", b), row("NODE_WEB", "arm64", c))
        val plan = select(data, setOf(OfflineModuleId.ANDROID_CORE))
        assertEquals(listOf(a), plan.packages.map { it.sha256 })
        assertEquals(1L, plan.estimatedGetRequests)
        assertEquals(1024L, plan.totalBytes)
    }

    @Test fun pythonAutomaticallyIncludesAndroidCoreDependency() {
        val data = raw(row("ANDROID_CORE", "arm64", a), row("PYTHON_ANDROID", "arm64", b))
        assertEquals(setOf(a, b), select(data, setOf(OfflineModuleId.PYTHON_ANDROID)).packages.map { it.sha256 }.toSet())
    }

    @Test fun expoIncludesCoreAndNodeDependencies() {
        val data = raw(row("ANDROID_CORE", "arm64", a), row("NODE_WEB", "arm64", b), row("EXPO_REACT_NATIVE", "arm64", c))
        assertEquals(setOf(a, b, c), select(data, setOf(OfflineModuleId.EXPO_REACT_NATIVE)).packages.map { it.sha256 }.toSet())
    }

    @Test fun sameShaAcrossModulesCountsOnlyOnce() {
        val data = raw(row("ANDROID_CORE", "arm64", a, 80), row("NODE_WEB", "arm64", a, 80))
        val plan = select(data, setOf(OfflineModuleId.ANDROID_CORE, OfflineModuleId.NODE_WEB))
        assertEquals(1, plan.packages.size)
        assertEquals(80L, plan.totalBytes)
        assertEquals(1L, plan.estimatedGetRequests)
    }

    @Test fun rejectsConflictingByteLengthForSameSha() {
        val data = raw(row("ANDROID_CORE", "arm64", a, 80), row("NODE_WEB", "arm64", a, 81))
        assertThrows(IllegalArgumentException::class.java) {
            select(data, setOf(OfflineModuleId.ANDROID_CORE, OfflineModuleId.NODE_WEB))
        }
    }

    @Test fun rejectsMissingPackagesForRequestedHostArchitecture() {
        val data = raw(row("EXPO_REACT_NATIVE", "arm64", a), row("ANDROID_CORE", "amd64", b))
        assertThrows(IllegalArgumentException::class.java) {
            select(data, setOf(OfflineModuleId.EXPO_REACT_NATIVE), "amd64")
        }
    }

    @Test fun rejectsUnsupportedModuleAndEmptySet() {
        val data = raw(row("ANDROID_CORE", "arm64", a))
        for (modules in listOf(emptySet(), setOf(OfflineModuleId.RUST), setOf(OfflineModuleId.FAST_HTML))) {
            assertThrows(IllegalArgumentException::class.java) { select(data, modules) }
        }
    }

    @Test fun rejectsCatalogDigestDrift() {
        val data = raw(row("ANDROID_CORE", "arm64", a))
        assertThrows(IllegalArgumentException::class.java) {
            V9SelectiveR2ModuleCache.selectRows(data, setOf(OfflineModuleId.ANDROID_CORE), "arm64", "0".repeat(64))
        }
    }

    @Test fun rejectsUntrustedArchitectureAndMalformedRows() {
        val data = raw(row("ANDROID_CORE", "arm64", a))
        assertThrows(IllegalArgumentException::class.java) {
            select(data, setOf(OfflineModuleId.ANDROID_CORE), "x86")
        }
        val malformed = raw("ANDROID_CORE\tarm64\tinvalid")
        assertThrows(IllegalArgumentException::class.java) {
            select(malformed, setOf(OfflineModuleId.ANDROID_CORE))
        }
    }

    @Test fun rejectsInvalidSourceAndUnsafePackageSize() {
        val other = raw(row("ANDROID_CORE", "arm64", a).replace("snapshot.ubuntu.com", "example.invalid"))
        assertThrows(IllegalArgumentException::class.java) { select(other, setOf(OfflineModuleId.ANDROID_CORE)) }
        val giant = raw(row("ANDROID_CORE", "arm64", a, 100_000_000))
        assertThrows(IllegalArgumentException::class.java) { select(giant, setOf(OfflineModuleId.ANDROID_CORE)) }
    }

    @Test fun chunkRequestsAreRoundedWithoutHeadOverhead() {
        val block = V9SignedR2CandidateDownload.CHUNK_BYTES
        assertEquals(1L, V9SelectiveR2ModuleCache.chunkCount(1))
        assertEquals(1L, V9SelectiveR2ModuleCache.chunkCount(block))
        assertEquals(2L, V9SelectiveR2ModuleCache.chunkCount(block + 1))
        assertEquals(5L, V9SelectiveR2ModuleCache.chunkCount(35_401_298))
    }

    @Test fun cachedObjectIsVerifiedAndNoNetworkRequestsRemain() {
        val cache = temp.newFolder("cached")
        val bytes = "trusted package bytes".toByteArray()
        val digest = sha(bytes)
        java.io.File(cache, digest).writeBytes(bytes)
        val obj = V9SelectiveR2ModuleCache.Package(digest, bytes.size.toLong(), "deb")
        val result = V9SelectiveR2ModuleCache.inspectCache(cache, listOf(obj))
        assertEquals(1, result.existing.size)
        assertTrue(result.missing.isEmpty())
        assertEquals(0L, result.networkBytes)
        assertEquals(0L, result.networkGetRequests)
    }

    @Test fun missingObjectRequiresOnlyItsOwnRequests() {
        val cache = temp.newFolder("empty")
        val obj = V9SelectiveR2ModuleCache.Package(a, 1024L, "deb")
        val result = V9SelectiveR2ModuleCache.inspectCache(cache, listOf(obj))
        assertTrue(result.existing.isEmpty())
        assertEquals(1, result.missing.size)
        assertEquals(1024L, result.networkBytes)
        assertEquals(1L, result.networkGetRequests)
    }

    @Test fun rejectsTamperedCachedObjectEvenIfLengthMatches() {
        val cache = temp.newFolder("tamper")
        val orig = "trusted package bytes".toByteArray()
        val digest = sha(orig)
        java.io.File(cache, digest).writeBytes("untrusted package data".toByteArray().copyOf(orig.size))
        val obj = V9SelectiveR2ModuleCache.Package(digest, orig.size.toLong(), "deb")
        assertThrows(SecurityException::class.java) { V9SelectiveR2ModuleCache.inspectCache(cache, listOf(obj)) }
    }

    @Test fun rejectsOversizedOrNonexistentCacheRoot() {
        val invalid = java.io.File(temp.root, "absent")
        val obj = V9SelectiveR2ModuleCache.Package(a, 1024L, "deb")
        assertThrows(IllegalArgumentException::class.java) { V9SelectiveR2ModuleCache.inspectCache(invalid, listOf(obj)) }
        assertFalse(invalid.exists())
    }

    @Test fun platformParentAliasResolvesInsideCanonicalPrivateRoot() {
        val realParent = temp.newFolder("data-real")
        val realApp = java.io.File(realParent, "app-no-backup")
        assertTrue(realApp.mkdir())
        val alias = java.io.File(temp.root, "data-alias")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), realParent.toPath())
        val platformRoot = java.io.File(alias, "app-no-backup")
        assertFalse(java.nio.file.Files.isSymbolicLink(platformRoot.toPath()))
        val cache = V9SignedR2CandidateDownload.privateCacheDirectory(platformRoot)
        assertEquals(java.io.File(realApp, "v9-signed-r2-canary").canonicalFile, cache)
        assertEquals(realApp.canonicalFile, cache.parentFile.canonicalFile)
    }

    @Test fun rejectsDirectSymlinkForPrivateRoot() {
        val realRoot = temp.newFolder("private-root")
        val alias = java.io.File(temp.root, "root-symlink")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), realRoot.toPath())
        assertThrows(IllegalStateException::class.java) {
            V9SignedR2CandidateDownload.privateCacheDirectory(alias)
        }
    }

    @Test fun rejectsSymlinkedCanaryCacheLeaf() {
        val realRoot = temp.newFolder("cache-root")
        val outside = temp.newFolder("outside")
        java.nio.file.Files.createSymbolicLink(
            java.io.File(realRoot, "v9-signed-r2-canary").toPath(), outside.toPath())
        assertThrows(IllegalStateException::class.java) {
            V9SignedR2CandidateDownload.privateCacheDirectory(realRoot)
        }
    }
}
