package com.appforge.studio.build

import android.content.Context
import android.os.Build
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRootfsManifest
import com.appforge.studio.terminal.LinuxRootfsMetadataCodec
import com.appforge.studio.terminal.LinuxRuntimeLayout
import com.appforge.studio.terminal.LinuxRuntimeManifestRegistry
import com.appforge.studio.terminal.LinuxInstallProgress
import com.appforge.studio.terminal.PackagedLinuxEngine
import com.appforge.studio.terminal.PackagedLinuxEngineStatus
import com.appforge.studio.terminal.VerifiedLinuxRootfsInstaller
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * V541 phase 2: a real, target-owned Ubuntu baseline, NOT a module installation.
 * 
 * The old V540 rootfs and its receipts are never opened for writing. A request
 * prepares exactly one target's private Linux root, with a distinct dpkg and
 * APT database. An installed module must still undergo dependency installation,
 * runtime probes and signed ownership attestation before it can become READY.
 *
 * Not yet wired to the production installer. Explicit debug-only staging is a
 * prerequisite for physical acceptance and the later controlled switchover.
 */
internal object IsolatedModuleRuntimeV2 {
    private val stagingMutex = Mutex()
    private const val SEAL = "vendor-baseline-v2.json"

    internal data class Layout(
        val request: ModuleIsolationV1.Request,
        val namespace: ModuleIsolationV1.Layout,
        val linux: LinuxRuntimeLayout,
        val dpkgStatus: File,
        val aptLists: File,
        val dpkgInfo: File,
        val seal: File
    )

    /** Pure path construction except the narrowly owned namespace creation. */
    fun layout(privateData: File, target: OfflineModuleId, arch: LinuxArchitecture): Layout {
        val request = ModuleIsolationV1.request(target)
        require(request.requiresLinux && !request.bundledOnly) {
            "Bundled or portable module does not need a Linux rootfs"
        }
        val namespace = ModuleIsolationV1.prepare(privateData, target)
        val linux = LinuxRuntimeLayout(namespace.rootfs, LinuxDistribution.UBUNTU, arch)
        val root = linux.rootfsDirectory
        val namespacePath = namespace.rootfs.canonicalFile.toPath()
        require(root.absoluteFile.toPath().normalize().startsWith(namespacePath))
        // Refuse an inherited symlink at any point, including a not-yet-created root.
        RecoveryPathAnchor.check(linux.runtimeDirectory)
        RecoveryPathAnchor.check(root)
        val dpkg = File(root, "var/lib/dpkg/status")
        val apt = File(root, "var/lib/apt/lists")
        val info = File(root, "var/lib/dpkg/info")
        listOf(dpkg, apt, info).forEach { RecoveryPathAnchor.check(it) }
        val seal = File(namespace.state, SEAL)
        RecoveryPathAnchor.check(seal)
        return Layout(request, namespace, linux, dpkg, apt, info, seal)
    }

    /** No module's install root, dpkg, APT or cache is another module's root. */
    fun requireSeparated(first: Layout, second: Layout) {
        require(first.request.target != second.request.target)
        val a = listOf(first.linux.rootfsDirectory, first.dpkgStatus, first.aptLists,
            first.dpkgInfo, first.namespace.downloads, first.namespace.apt, first.namespace.state)
        val b = listOf(second.linux.rootfsDirectory, second.dpkgStatus, second.aptLists,
            second.dpkgInfo, second.namespace.downloads, second.namespace.apt, second.namespace.state)
        for (left in a) for (right in b) {
            val lp = left.absoluteFile.toPath().normalize()
            val rp = right.absoluteFile.toPath().normalize()
            require(lp != rp && !lp.startsWith(rp) && !rp.startsWith(lp)) {
                "Module namespaces overlap"
            }
        }
    }

    /** Baseline verification is not readiness of Rust, Expo, Windows or any tool. */
    fun baselineVerified(layout: Layout, manifest: LinuxRootfsManifest): Boolean = runCatching {
        require(manifest.distribution == layout.linux.distribution &&
            manifest.architecture == layout.linux.architecture)
        require(layout.dpkgStatus.isFile && !Files.isSymbolicLink(layout.dpkgStatus.toPath()))
        require(layout.seal.isFile && !Files.isSymbolicLink(layout.seal.toPath()) &&
            layout.seal.length() in 1..8192)
        val meta = LinuxRootfsMetadataCodec.read(layout.linux.metadataFile)
        require(meta != null && meta.distribution == manifest.distribution &&
            meta.architecture == manifest.architecture && meta.release == manifest.release &&
            meta.archiveSha256.equals(manifest.archiveSha256, ignoreCase = true))
        val evidence = JSONObject(layout.seal.readText())
        require(evidence.getInt("schema") == 2 &&
            evidence.getString("target") == layout.request.target.name &&
            evidence.getString("architecture") == manifest.architecture.id &&
            evidence.getString("sourceSha256") == manifest.archiveSha256 &&
            evidence.getString("dpkgStatusSha256") == VerifiedModuleDownload.sha(layout.dpkgStatus))
        require(AndroidRuntimeTrust.ready(layout.linux.rootfsDirectory))
        val vendor = File(layout.linux.rootfsDirectory,
            "opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json")
        require(vendor.isFile && !Files.isSymbolicLink(vendor.toPath()))
        require(JSONObject(vendor.readText()).getString("statusSha256") ==
            VerifiedModuleDownload.sha(layout.dpkgStatus))
        true
    }.getOrDefault(false)

    /**
     * Physical baseline staging is deliberately opt-in and debug-only.
     * No legacy rootfs read, no shared dpkg writes, no symlink to installed files.
     * Incomplete or existing candidates are retained and rejected, never reset.
     */
    suspend fun stageFreshBaseline(
        context: Context,
        target: OfflineModuleId,
        userApproved: Boolean,
        onProgress: (LinuxInstallProgress) -> Unit = {}
    ): Layout = stagingMutex.withLock {
        check(com.appforge.studio.BuildConfig.DEBUG) {
            "Isolated baseline staging is available only in debug acceptance builds"
        }
        check(userApproved) { "User must explicitly approve isolated baseline installation" }
        val app = context.applicationContext
        val architecture = LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList())
            ?: error("Unsupported Android architecture")
        require(com.appforge.studio.terminal.ProrootPinnedRuntime.supportedAbi in Build.SUPPORTED_ABIS)
        val manifest = LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU, architecture)
            ?: error("Pinned Ubuntu manifest missing")
        val planned = layout(app.noBackupFilesDir, target, architecture)
        val isolated = planned.linux
        check(PackagedLinuxEngine(app).inspect().status == PackagedLinuxEngineStatus.READY) {
            "Signed PRoot engine is not ready"
        }
        check(!isolated.rootfsDirectory.exists() && !isolated.metadataFile.exists() &&
            !planned.seal.exists()) {
            "An isolated baseline exists or was interrupted: preserve it for inspection"
        }
        // Reject stale/unowned work; the installer will use its own journaled stage.
        check(!isolated.stagingDirectory.exists() ||
            isolated.stagingDirectory.listFiles().orEmpty().isEmpty()) {
            "Existing isolated stage is protected"
        }
        check(!isolated.restoreDirectory.exists() ||
            isolated.restoreDirectory.listFiles().orEmpty().isEmpty()) {
            "Existing isolated rollback is protected"
        }
        val legacy = DeviceBuildRuntimeV3.runtimeBaseDirectory(app).canonicalFile.toPath()
        val candidate = isolated.rootfsDirectory.absoluteFile.toPath().normalize()
        require(!candidate.startsWith(legacy) && !legacy.startsWith(candidate)) {
            "Refusing to modify legacy runtime"
        }
        VerifiedLinuxRootfsInstaller(app).install(manifest, isolated, onProgress)
        val root = isolated.rootfsDirectory
        check(planned.dpkgStatus.isFile && !Files.isSymbolicLink(planned.dpkgStatus.toPath())) {
            "Vendor dpkg baseline missing"
        }
        // APT trust and provenance are written ONLY into this new target root.
        AndroidRuntimeTrust.install(root)
        BaseRuntimeOwnership.captureFresh(root)
        AptRuntimeBaseline.captureFresh(root, manifest)
        AptSnapshotMigration.captureFreshRoot(root, manifest.architecture.id, manifest.archiveSha256)
        val evidence = JSONObject()
            .put("schema", 2)
            .put("target", target.name)
            .put("architecture", manifest.architecture.id)
            .put("sourceSha256", manifest.archiveSha256)
            .put("dpkgStatusSha256", VerifiedModuleDownload.sha(planned.dpkgStatus))
            .toString().toByteArray(Charsets.UTF_8)
        require(evidence.size <= 8192)
        val temp = File(planned.namespace.state, ".$SEAL.part")
        RecoveryPathAnchor.check(temp)
        require(!Files.exists(temp.toPath()) && !Files.exists(planned.seal.toPath()))
        FileOutputStream(temp).use { out -> out.write(evidence); out.fd.sync() }
        Files.move(temp.toPath(), planned.seal.toPath(), StandardCopyOption.ATOMIC_MOVE)
        check(baselineVerified(planned, manifest)) { "Isolated baseline verification failed" }
        planned
    }
}
