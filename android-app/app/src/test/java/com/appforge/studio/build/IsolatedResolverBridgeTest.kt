package com.appforge.studio.build

import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class IsolatedResolverBridgeTest {
    @Test fun currentNetworkAddressesOnlyAndDeduplicated() {
        val config = IsolatedResolverBridge.configuration(listOf(
            InetAddress.getByAddress(byteArrayOf(10, 22, 33, 44)),
            InetAddress.getByAddress(byteArrayOf(10, 22, 33, 44)),
            InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))))
        assertEquals("nameserver 10.22.33.44\n", config)
    }

    @Test fun emptyOrLoopbackIsRejected() {
        val err = runCatching { IsolatedResolverBridge.configuration(listOf(
            InetAddress.getByAddress(byteArrayOf(127, 0, 0, 53)))) }.exceptionOrNull()
        assertNotNull(err)
        assertTrue(err!!.message!!.contains("APPFORGE_ISOLATED_DNS_UNAVAILABLE"))
    }

    @Test fun onlyJournalStageIsWritableAndExistingRootIsPreserved() {
        val root = Files.createTempDirectory("appforge-r21-dns").toFile()
        val staged = File(root, ".v3-stage-12345678-1234-1234-1234-123456789abc")
        val active = File(root, "rootfs")
        try {
            File(staged, "etc").mkdirs()
            File(active, "etc").mkdirs()
            File(active, "etc/resolv.conf").writeText("nameserver 192.0.2.10\n")
            val external = File(root, "outside").apply { writeText("stay-intact") }
            Files.createSymbolicLink(File(staged, "etc/resolv.conf").toPath(), external.toPath())
            IsolatedResolverBridge.writeStaged(staged,
                listOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 2))))
            assertEquals("nameserver 10.0.0.2\n", File(staged, "etc/resolv.conf").readText())
            assertEquals("stay-intact", external.readText())
            assertEquals("nameserver 192.0.2.10\n", File(active, "etc/resolv.conf").readText())
            assertTrue(runCatching { IsolatedResolverBridge.writeStaged(active,
                listOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 2)))) }.isFailure)
        } finally { root.deleteRecursively() }
    }
}
