package com.appforge.studio.build

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/** Writes current Android-network DNS only inside a journal-owned isolated generation.
 * No public DNS fallback, TLS downgrade, APT trust override or active-rootfs modification.
 */
internal object IsolatedResolverBridge {
    private val STAGE = Regex("\\.v3-stage-[0-9a-f-]{36}")
    private val NUMERIC_ADDRESS = Regex("[0-9a-fA-F:.]+")

    internal fun configuration(servers: List<InetAddress>): String {
        val addresses = servers.asSequence()
            .filterNot { it.isAnyLocalAddress || it.isLoopbackAddress ||
                it.isLinkLocalAddress || it.isMulticastAddress }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .filter { NUMERIC_ADDRESS.matches(it) }
            .distinct()
            .take(3)
            .toList()
        check(addresses.isNotEmpty()) { "APPFORGE_ISOLATED_DNS_UNAVAILABLE" }
        return addresses.joinToString(separator = "\n", postfix = "\n") { "nameserver $it" }
    }

    /** Caller passes only RuntimeGenerationStore.Prepared.root, never an installed rootfs. */
    internal fun writeStaged(staged: File, servers: List<InetAddress>) {
        require(STAGE.matches(staged.name) && staged.isDirectory &&
            !Files.isSymbolicLink(staged.toPath())) { "APPFORGE_DNS_NOT_STAGED" }
        val etc = File(staged, "etc")
        require(etc.isDirectory && !Files.isSymbolicLink(etc.toPath()) &&
            etc.canonicalFile.parentFile == staged.canonicalFile) {
            "APPFORGE_ISOLATED_DNS_ETC_UNTRUSTED"
        }
        val resolver = File(etc, "resolv.conf")
        require(!resolver.isDirectory) { "APPFORGE_ISOLATED_DNS_TARGET_UNTRUSTED" }
        val content = configuration(servers).toByteArray(Charsets.US_ASCII)
        val temp = File.createTempFile(".appforge-resolver-", ".tmp", etc)
        try {
            require(!Files.isSymbolicLink(temp.toPath()))
            FileOutputStream(temp).use { out -> out.write(content); out.fd.sync() }
            // Atomic replacement does not follow a pre-existing resolv.conf symlink.
            // Never write in place: even a clone must not mutate its source tree.
            Files.move(temp.toPath(), resolver.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (Files.exists(temp.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(temp.toPath())) Files.deleteIfExists(temp.toPath())
        }
    }

    fun prepare(context: Context, staged: File) {
        val networkManager = context.getSystemService(ConnectivityManager::class.java)
            ?: error("APPFORGE_ISOLATED_DNS_NO_CONNECTIVITY_SERVICE")
        val active = networkManager.activeNetwork
            ?: error("APPFORGE_ISOLATED_DNS_NO_ACTIVE_NETWORK")
        val capabilities = networkManager.getNetworkCapabilities(active)
        check(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            "APPFORGE_ISOLATED_DNS_NETWORK_NOT_VALIDATED"
        }
        val servers = networkManager.getLinkProperties(active)?.dnsServers.orEmpty()
        writeStaged(staged, servers)
    }
}
