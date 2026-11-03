package com.appforge.studio.build

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Physical Android-only V9 signed R2 acceptance. No production installation.
 * The signed, PUBLIC candidate manifest and signature are injected locally with
 * adb run-as into the unique debug app. NO private key or cloud credential.
 * Download test requires a user-initiated instrumentation command and validated,
 * unmetered Wi-Fi. Reuse test deliberately works without network.
 */
@RunWith(AndroidJUnit4::class)
class V9SignedR2PhysicalAcceptanceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val manifestSha = "569f7f55cb5fa645935250acaf2e6551d697fee4cfd723799abc92f5b58f5cf5"
    private val signatureSha = "830033663d72419435b99a1e6ced8e51cc582b9f8fd72752f3593c538c316ca7"
    private val selected = setOf(OfflineModuleId.NODE_WEB)
    private val architecture = "arm64"
    private val releaseSequence = 2026101801L

    private fun hex(input: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(input).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun signedInputs(): Pair<ByteArray, ByteArray> {
        val parent = File(context.filesDir, "v9-r2-acceptance")
        require(parent.isDirectory && !java.nio.file.Files.isSymbolicLink(parent.toPath()))
        val mf = File(parent, "release-v1.json")
        val sf = File(parent, "release-v1.sig")
        require(mf.isFile && sf.isFile)
        require(!java.nio.file.Files.isSymbolicLink(mf.toPath()))
        require(!java.nio.file.Files.isSymbolicLink(sf.toPath()))
        val manifest = mf.readBytes()
        val signature = sf.readBytes()
        assertEquals(manifestSha, hex(manifest))
        assertEquals(signatureSha, hex(signature))
        assertEquals(64, signature.size)
        return manifest to signature
    }

    private fun plan(): V9SelectiveR2ModuleCache.SignedSelection {
        val (manifest, signature) = signedInputs()
        return V9SelectiveR2ModuleCache.signedPlan(
            context, manifest, signature, releaseSequence, selected, architecture
        )
    }

    @Test fun signedManifestAndSelectivePlan() {
        val current = plan()
        assertEquals(releaseSequence, current.sequence)
        assertEquals(21, current.selection.packages.size)
        assertEquals(25, current.objects.size)
        assertTrue(current.selection.totalBytes in 1..10_000_000)
        assertEquals(4, current.objects.count { it.kind == "InRelease" })
        assertTrue(current.objects.all { it.kind == "deb" || it.kind == "InRelease" })
        println("V9_PHYSICAL_SIGNED_PLAN=PASS;NODE_DEB=21;OBJECTS=25;PRODUCTION=DISABLED")
    }

    @Test fun forgedSignatureFailsClosed() {
        val (manifest, signature) = signedInputs()
        val forged = signature.copyOf().also { it[0] = (it[0].toInt() xor 0x80).toByte() }
        val accepted = runCatching {
            V9SelectiveR2ModuleCache.signedPlan(
                context, manifest, forged, releaseSequence, selected, architecture
            )
        }.isSuccess
        assertFalse("Tampered signature must never authorize download", accepted)
        println("V9_PHYSICAL_TAMPERED_SIGNATURE=REJECTED")
    }

    @Test fun verifiedCacheTamperFailsClosed() {
        val scratch = File(context.noBackupFilesDir, "v9-r2-acceptance-negative")
        require(scratch.exists() || scratch.mkdir())
        require(scratch.isDirectory && !java.nio.file.Files.isSymbolicLink(scratch.toPath()))
        val good = "appforge-v9-cache-negative-control".toByteArray()
        val sha = hex(good)
        val file = File(scratch, sha)
        require(!file.exists()) { "V9 scratch cache already contains data" }
        try {
            file.writeBytes(good)
            val item = V9SelectiveR2ModuleCache.Package(sha, good.size.toLong(), "deb")
            assertEquals(1, V9SelectiveR2ModuleCache.inspectCache(scratch, listOf(item)).existing.size)
            val corrupt = good.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
            file.writeBytes(corrupt)
            val accepted = runCatching {
                V9SelectiveR2ModuleCache.inspectCache(scratch, listOf(item))
            }.isSuccess
            assertFalse("Corrupted content-addressed bytes must fail", accepted)
            println("V9_PHYSICAL_TAMPERED_CACHE=REJECTED")
        } finally {
            file.delete()
            scratch.delete()
        }
    }

    @Test fun userApprovedNodeDownloadOnWifi() {
        val p = plan()
        val before = V9SelectiveR2ModuleCache.status(context, p)
        val (manifest, signature) = signedInputs()
        val files = DownloadNetwork.withPolicy(DownloadNetworkPolicy.WIFI_PREFERRED) {
            V9SelectiveR2ModuleCache.fetchSelectedWithConsent(
                context, manifest, signature, releaseSequence, selected,
                architecture, userApproved = true
            )
        }
        val after = V9SelectiveR2ModuleCache.status(context, p)
        assertEquals(25, files.size)
        assertEquals(25, after.existing.size)
        assertEquals(0L, after.networkBytes)
        assertEquals(0L, after.networkGetRequests)
        println("V9_PHYSICAL_NODE_DOWNLOAD=PASS;FILES=25;INITIAL_GET_ESTIMATE=${before.networkGetRequests};CACHE_FULL=YES")
    }

    @Test fun cachedNodeReuseWithoutNetworkRequest() {
        val p = plan()
        val before = V9SelectiveR2ModuleCache.status(context, p)
        assertEquals("Run download acceptance before offline reuse", 25, before.existing.size)
        assertEquals(0L, before.networkGetRequests)
        val (manifest, signature) = signedInputs()
        // No connection required: the real downloader verifies local SHA-256 before opening URL.
        val files = V9SelectiveR2ModuleCache.fetchSelectedWithConsent(
            context, manifest, signature, releaseSequence, selected,
            architecture, userApproved = true
        )
        assertEquals(25, files.size)
        assertTrue(files.values.all { it.isFile && it.length() > 0 })
        val after = V9SelectiveR2ModuleCache.status(context, p)
        assertEquals(0L, after.networkGetRequests)
        println("V9_PHYSICAL_NODE_CACHE_REUSE=PASS;PREDICTED_GETS=0;FILES=25")
    }
}
