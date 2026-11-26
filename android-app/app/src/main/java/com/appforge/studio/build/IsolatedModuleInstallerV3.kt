package com.appforge.studio.build

import android.content.Context
import android.os.Build
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRuntimeManifestRegistry
import com.appforge.studio.terminal.LinuxRootfsMetadataCodec
import com.appforge.studio.terminal.LinuxShellEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * V541 isolated acceptance installer. Each selected target owns a complete Ubuntu
 * runtime, dependency closure and APT/dpkg database. A signed object may be read
 * from the common download-only CAS, but installed files are never shared.
 *
 * This is opt-in/debug-only until physical Android acceptance. No implicit
 * migration, removal of legacy roots, or promotion based on downloads alone.
 */
internal object IsolatedModuleInstallerV3 {
    private val installMutex = Mutex()
    private const val RECEIPT = "opt/appforge-device/isolated-v3/receipt.json"
    private const val MAX_RECEIPT = 32 * 1024L

    private val evidenceFiles = mapOf(
        OfflineModuleId.LINUX_RUNTIME to listOf("var/lib/dpkg/status"),
        OfflineModuleId.ANDROID_CORE to listOf(
            "opt/appforge-device/jdk-17/bin/java",
            "opt/appforge-device/android-sdk/build-tools/36.0.0/aapt2"),
        OfflineModuleId.NODE_WEB to listOf("opt/appforge-device/node-22.23.3/bin/node"),
        OfflineModuleId.PYTHON_ANDROID to listOf("usr/bin/python3.12"),
        OfflineModuleId.RUST to listOf("opt/appforge-device/rust-1.85.1/bin/rustc",
            "opt/appforge-device/rust-1.85.1/bin/cargo"),
        OfflineModuleId.EXPO_REACT_NATIVE to listOf(
            "opt/appforge-device/android-sdk/cmake/3.22.1/bin/cmake",
            "opt/appforge-device/android-sdk/ndk/27.1.12297006/source.properties"),
        OfflineModuleId.RUST_ANDROID_NDK to listOf(
            "opt/appforge-device/ndk-host-v7/verified.tsv",
            "opt/appforge-device/android-sdk/ndk/27.1.12297006/source.properties"),
        OfflineModuleId.WINDOWS_NATIVE to listOf(
            "opt/appforge-device/windows-native-v1/.ready"),
    )

    private fun arch(): LinuxArchitecture? =
        LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList())

    /** Read-only: a module is never READY merely because rootfs or cached debs exist. */
    fun ready(context: Context, target: OfflineModuleId): Boolean = runCatching {
        if (target in setOf(OfflineModuleId.FAST_HTML, OfflineModuleId.WINDOWS_PORTABLE))
            return@runCatching false
        val architecture = arch() ?: return@runCatching false
        val home = context.applicationContext.noBackupFilesDir
        val base = File(home, "isolated-modules-v1/${target.name.lowercase()}")
        if (!base.isDirectory || Files.isSymbolicLink(base.toPath())) return@runCatching false
        val layout = IsolatedModuleRuntimeV2.layout(home, target, architecture)
        val root = layout.linux.rootfsDirectory
        val manifest = LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU, architecture)
            ?: return@runCatching false
        val meta = LinuxRootfsMetadataCodec.read(layout.linux.metadataFile)
            ?: return@runCatching false
        require(meta.archiveSha256.equals(manifest.archiveSha256, ignoreCase = true) &&
            meta.distribution == manifest.distribution && meta.architecture == manifest.architecture &&
            meta.release == manifest.release)
        require(AndroidRuntimeTrust.ready(root))
        val file = File(root, RECEIPT)
        require(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.length() in 1..MAX_RECEIPT)
        val obj = JSONObject(file.readText())
        require(obj.getInt("schema") == 3 && obj.getString("target") == target.name &&
            obj.getString("ubuntuSha256") == manifest.archiveSha256)
        val order = ModuleIsolationV1.request(target).dependencyOrder
        val deps = obj.getJSONArray("dependencies")
        require(deps.length() == order.size &&
            (0 until deps.length()).map { deps.getString(it) } == order.map { it.name })
        val status = File(root, "var/lib/dpkg/status")
        require(status.isFile && !Files.isSymbolicLink(status.toPath()) &&
            obj.getString("dpkgSha256") == VerifiedModuleDownload.sha(status))
        val files = obj.getJSONObject("files")
        val expected = pathsFor(order)
        require(files.length() == expected.size && expected.isNotEmpty())
        for (relative in expected) {
            val candidate = File(root, relative)
            // Authentic distro symlinks are permitted only if fully contained
            // and resolved to an existing target inside this private root.
            require(candidate.isFile && candidate.canonicalFile.toPath()
                .startsWith(root.canonicalFile.toPath()))
            require(files.getString(relative) == VerifiedModuleDownload.sha(candidate))
        }
        true
    }.getOrDefault(false)

    /** Read-only fail-closed admission. A valid baseline MAY resume a failed
     *  installation; an interrupted/untrusted candidate NEVER triggers another
     *  network acquisition, stage clone, or implicit wipe.
     */
    fun hasProtectedCandidate(context: Context, target: OfflineModuleId): Boolean = runCatching {
        if (target in setOf(OfflineModuleId.FAST_HTML, OfflineModuleId.WINDOWS_PORTABLE,
                OfflineModuleId.LINUX_RUNTIME) || ready(context,target)) return@runCatching false
        val architecture = arch() ?: return@runCatching true
        val manifest = LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU,architecture)
            ?: return@runCatching true
        val home = context.applicationContext.noBackupFilesDir
        val base = File(home, "isolated-modules-v1/${target.name.lowercase()}")
        if (Files.isSymbolicLink(base.toPath())) return@runCatching true
        if (!base.exists()) return@runCatching false
        require(base.isDirectory)
        val layout = IsolatedModuleRuntimeV2.layout(home,target,architecture)
        val runtime = layout.linux.runtimeDirectory
        val root = layout.linux.rootfsDirectory
        val protected = listOf(root,layout.linux.metadataFile,layout.seal,
            File(runtime,".module-generation.json"))
        val present = protected.any {
            Files.exists(it.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS)
        }
        val unsafeLinks = protected.any { Files.isSymbolicLink(it.toPath()) }
        val journalExists = Files.exists(File(runtime,".module-generation.json").toPath(),
            java.nio.file.LinkOption.NOFOLLOW_LINKS)
        val interrupted = listOf(layout.linux.stagingDirectory,layout.linux.restoreDirectory)
            .any { dir -> Files.isSymbolicLink(dir.toPath()) ||
                dir.listFiles().orEmpty().isNotEmpty() }
        val generation = runtime.listFiles().orEmpty().any {
            it.name.startsWith(".v3-stage-") || it.name.startsWith(".v3-discard-") ||
                it.name.startsWith(".v3-previous-")
        }
        unsafeLinks || journalExists || interrupted || generation ||
            (present && !IsolatedModuleRuntimeV2.baselineVerified(layout,manifest)) ||
            (File(root,RECEIPT).exists() && !ready(context,target))
    }.getOrDefault(true)

    /** Exactly one single-target project may route to an isolated build runtime. */
    internal fun targetForProject(draft: ProjectDraft): OfflineModuleId? {
        val engine = draft.sourceBuildEngine.trim().lowercase()
        return when (engine) {
            "windows-native" -> OfflineModuleId.WINDOWS_NATIVE
            "expo", "react-native" -> OfflineModuleId.EXPO_REACT_NATIVE
            "android-ndk" -> OfflineModuleId.RUST_ANDROID_NDK
            "python-android" -> if (RustProjectPolicy.hasRust(draft))
                OfflineModuleId.RUST_ANDROID_NDK else OfflineModuleId.PYTHON_ANDROID
            "node-web" -> OfflineModuleId.NODE_WEB
            "android-gradle" -> if (RustProjectPolicy.hasRust(draft))
                OfflineModuleId.RUST_ANDROID_NDK else OfflineModuleId.ANDROID_CORE
            "webview-static" -> OfflineModuleId.ANDROID_CORE
            else -> null // Mixed/universal outputs must not borrow an unrelated root.
        }
    }

    internal fun rootForProject(context: Context, draft: ProjectDraft,
                                plan: OfflineModulePlan = OfflineModules.forProject(draft)): File? {
        if (!com.appforge.studio.BuildConfig.DEBUG) return null
        if (plan.error != null) return null
        val target = targetForProject(draft) ?: return null
        // Ensure this root actually contains every required tool for this build.
        if (!ModuleIsolationV1.request(target).dependencyOrder.toSet().containsAll(plan.modules)) return null
        if (!ready(context, target)) return null
        val a = arch() ?: return null
        return IsolatedModuleRuntimeV2.layout(context.applicationContext.noBackupFilesDir,
            target, a).linux.rootfsDirectory
    }

    private fun pathsFor(order: List<OfflineModuleId>): List<String> =
        order.flatMap { evidenceFiles[it].orEmpty() }.distinct().sorted()

    /** The latest signed release is required for ANY externally acquired APT object. */
    suspend fun installSelected(context: Context, target: OfflineModuleId,
        sdkConsent: Boolean, control: DownloadControl = DownloadControl(),
        onProgress: (ModuleProgress) -> Unit = {}) = installMutex.withLock {
        check(com.appforge.studio.BuildConfig.DEBUG) {
            "Physical acceptance of per-module isolation requires a debug APK"
        }
        val request = ModuleIsolationV1.request(target)
        require(request.requiresLinux && !request.bundledOnly)
        require(!request.requiresSdkLicense || sdkConsent) {
            "Android SDK license consent required"
        }
        val app = context.applicationContext
        val operation = UUID.randomUUID().toString()
        fun phase(id: OfflineModuleId, p: ModulePhase, message: String) {
            onProgress(ModuleProgress(id, operation, p, detail = message))
        }
        control.checkpoint()
        if (ready(app, target)) {
            phase(target, ModulePhase.READY, "Bağımsız modülün SHA-256 kanıtları tekrar doğrulandı.")
            return@withLock
        }
        // Before ANY R2 prefetch, network access or generation copy, refuse
        // an unverified root or interrupted stage. A valid baseline may resume.
        check(!hasProtectedCandidate(app,target)) {
            "ISOLATED_CANDIDATE_REPAIR_REQUIRED:${target.name}: " +
                "Korunan yarım kurulum var; indirme ve kopyalama engellendi."
        }
        require(DeviceBuildRuntimeV3.platformError() == null) {
            DeviceBuildRuntimeV3.platformError() ?: "Unsupported platform"
        }
        DownloadNetwork.configure(app)
        DownloadNetwork.prepareJournal()
        VerifiedArtifactCache.configure(app.noBackupFilesDir,
            OfflineBuildPackManager.runtimeRootfs(app)?.parentFile)
        val architecture = arch() ?: error("Unsupported Android ABI")
        val manifest = LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU, architecture)
            ?: error("Signed Ubuntu rootfs not pinned")
        val selectedApt = request.dependencyOrder.flatMap { id ->
            when (id) {
                OfflineModuleId.RUST -> listOf(OfflineModuleId.PYTHON_ANDROID)
                OfflineModuleId.RUST_ANDROID_NDK -> listOf(OfflineModuleId.EXPO_REACT_NATIVE)
                OfflineModuleId.ANDROID_CORE, OfflineModuleId.NODE_WEB,
                OfflineModuleId.PYTHON_ANDROID, OfflineModuleId.EXPO_REACT_NATIVE,
                OfflineModuleId.WINDOWS_NATIVE -> listOf(id)
                else -> emptyList()
            }
        }.toSet()
        val release = if (selectedApt.isNotEmpty()) V9R2ReleaseStore.load(app) else null
        if (selectedApt.isNotEmpty()) {
            val bundle = requireNotNull(release)
            onProgress(ModuleProgress(target,operation,ModulePhase.VERIFYING,
                detail="Yalnız seçilen modülün imzalı Ubuntu paketleri ve bağımlılıkları doğrulanıyor.",
                acquisitionPending=true))
            val job = currentCoroutineContext()
            V9SelectiveR2ModuleCache.fetchSelectedWithConsent(app, bundle.manifest,
                bundle.signature, bundle.sequence, selectedApt,
                architecture.distroArchitecture, true,
                canceled = { !job.isActive || control.request != DownloadControl.Request.NONE },
                onProgress = { i, total, done, bytes ->
                    onProgress(ModuleProgress(target, operation, ModulePhase.DOWNLOADING,
                        bytesDownloaded = done, bytesTotal = bytes,
                        detail = "İmzalı R2 paketi $i/$total",acquisitionPending=true))
                })
        }
        control.checkpoint()
        val layout = IsolatedModuleRuntimeV2.layout(app.noBackupFilesDir, target, architecture)
        // R68: also acquire pinned SDK, JDK, Gradle and NDK distribution files
        // before extracting the base or creating a mutable tool generation.
        // Dynamic Maven/npm/SDK metadata resolution remains online-capable.
        val installOrder = request.dependencyOrder.filter { it != OfflineModuleId.LINUX_RUNTIME }
        val archives = installOrder.associateWith { id -> toolArchives(app,id) }
        val androidArchives=R68PinnedAndroidArchives.plan(
            app.assets.open("device-build/verified-toolchain-sources-v4.json").bufferedReader().use { it.readText() },
            installOrder,Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        for (archive in androidArchives) {
            currentCoroutineContext().ensureActive()
            control.checkpoint()
            val job=currentCoroutineContext()
            VerifiedModuleDownload.download(archive.url,archive.sha256,
                File(layout.namespace.downloads,archive.name),target,operation,
                canceled={ !job.isActive || control.request!=DownloadControl.Request.NONE },
                open={ ModuleArtifactBroker.Companion.RedirectConnection(it) },
                progress={ onProgress(it.copy(acquisitionPending=true)) },
                maxBytes=2L*1024*1024*1024,expectedBytes=archive.bytes)
        }
        for (id in installOrder) {
            for (archive in archives.getValue(id)) {
                currentCoroutineContext().ensureActive()
                control.checkpoint()
                val job = currentCoroutineContext()
                VerifiedModuleDownload.download(archive.url,archive.sha256,
                    File(layout.namespace.downloads,archive.name),id,operation,
                    canceled = { !job.isActive || control.request != DownloadControl.Request.NONE },
                    progress = { onProgress(it.copy(acquisitionPending=true)) })
            }
        }
        control.checkpoint()
        if (!IsolatedModuleRuntimeV2.baselineVerified(layout, manifest)) {
            // Existing/partial or modified roots are never reset or force-overwritten.
            onProgress(ModuleProgress(target,operation,ModulePhase.CONNECTING,
                detail="İmzalı Ubuntu arşivi hazırlanıyor.",acquisitionPending=true))
            IsolatedModuleRuntimeV2.stageFreshBaseline(app, target, true) { progress ->
                // Do not interrupt the baseline's atomic activation/seal window.
                if(progress.stage==com.appforge.studio.terminal.LinuxInstallStage.DOWNLOADING)
                    control.checkpoint()
                val transfer=progress.transfer
                if(transfer!=null) {
                    onProgress(transfer.copy(moduleId=target,operationId=operation,acquisitionPending=true))
                } else {
                    val extracting=progress.stage==com.appforge.studio.terminal.LinuxInstallStage.EXTRACTING ||
                        progress.stage==com.appforge.studio.terminal.LinuxInstallStage.FINALIZING ||
                        progress.stage==com.appforge.studio.terminal.LinuxInstallStage.COMPLETE
                    onProgress(ModuleProgress(target,operation,
                        if(extracting)ModulePhase.INSTALLING else ModulePhase.VERIFYING,
                        detail=progress.detail,acquisitionPending=!extracting))
                }
            }
        }
        require(IsolatedModuleRuntimeV2.baselineVerified(layout, manifest)) {
            "Separate Ubuntu rootfs baseline failed verification"
        }
        val root = layout.linux.rootfsDirectory
        check(!File(root, RECEIPT).exists()) {
            "Existing candidate receipt is not trusted; preserved for inspection"
        }
        val workspace = File(app.cacheDir, "isolated-acceptance/${UUID.randomUUID()}")
        check(workspace.mkdirs()) { "Cannot create owned acceptance workspace" }
        try {
            copyAssetTree(app, "device-build", File(workspace, "runtime"))
            phase(target, ModulePhase.PREPARING, "Ayrı rootfs üzerinde tek kurulum işlemi hazırlanıyor.")
            val job = currentCoroutineContext()
            RuntimeGenerationStore(root).prepareOwner("ISOLATED:${target.name}",
                canceled = { job.ensureActive(); control.checkpoint() }).use { generation ->
                val staged = generation.root
                DownloadInstrumentation.install(app, staged)
                // Use the exact filenames consumed by the unchanged installer.
                // Each file is SHA-256 checked again before the private stage copy.
                if(androidArchives.isNotEmpty()) {
                    val cache=File(staged,"opt/appforge-device/cache").apply { mkdirs() }
                    for (archive in androidArchives) {
                        job.ensureActive();control.checkpoint()
                        val source=File(layout.namespace.downloads,archive.name)
                        val destination=File(cache,archive.name)
                        if(destination.exists()) {
                            require(VerifiedArtifactCache.matches(destination,archive.sha256,archive.bytes)) {
                                "R68_STAGE_ARCHIVE_MISMATCH:${archive.name}"
                            }
                        } else copyToStage(source,destination,archive.sha256)
                    }
                }
                var completedUnits=0
                val totalUnits=installOrder.size.coerceAtLeast(1)
                fun observed(event:ModuleProgress):ModuleProgress = event.copy(
                    installUnitsCompleted=completedUnits,installUnitsTotal=totalUnits)
                for (id in installOrder) {
                    job.ensureActive(); control.checkpoint()
                    onProgress(observed(ModuleProgress(id,operation,ModulePhase.INSTALLING,
                        detail="${OfflineModules.spec(id).title} yalnız $target ortamında hazırlanıyor.")))
                    if (id in setOf(OfflineModuleId.RUST, OfflineModuleId.RUST_ANDROID_NDK,
                            OfflineModuleId.ANDROID_CORE, OfflineModuleId.EXPO_REACT_NATIVE,
                            OfflineModuleId.PYTHON_ANDROID, OfflineModuleId.WINDOWS_NATIVE,
                            OfflineModuleId.NODE_WEB)) {
                        val result = V9R2AptInstallReuse.seedForDebug(app, staged, id)
                        require(result.missing == 0) {
                            "ISOLATED_R2_MISSING:${id.name}:${result.missing}; signed cache preserved"
                        }
                    }
                    seedToolArchive(layout.namespace, staged, workspace, id, archives.getValue(id))
                    for (command in commands(id, File(staged,
                            "opt/appforge-device/rust-1.85.1/bin/rustc").isFile)) {
                        job.ensureActive(); control.checkpoint()
                        if (OfflineBuildPackManager.hasValidatedInternet(app))
                            DownloadNetwork.requireExternalConsent()
                        // Android's validated active-network resolver must be provided to the
                        // isolated, copy-on-write generation before guest SDK/Gradle executes.
                        // The active rootfs, R2 cache and other modules are never changed.
                        if (id == OfflineModuleId.ANDROID_CORE) {
                            IsolatedResolverBridge.prepare(app, staged)
                        }
                        onProgress(observed(ModuleProgress(id,operation,ModulePhase.INSTALLING,
                            detail="${id.name}: $command")))
                        val prefix = "APPFORGE_MODULE_REFRESH=0 APPFORGE_MODULE_ID=${id.name} " +
                            "APPFORGE_ANDROID_CORE_PREPARED=${if (id in setOf(OfflineModuleId.PYTHON_ANDROID, OfflineModuleId.EXPO_REACT_NATIVE, OfflineModuleId.RUST_ANDROID_NDK)) 1 else 0} " +
                            "APPFORGE_ANDROID_SDK_LICENSE_ACCEPTED=${if (sdkConsent) 1 else 0} " +
                            "APPFORGE_DEVICE_OFFLINE=0 "
                        val exec = if (id == OfflineModuleId.RUST && command.startsWith("install-rust-module.py"))
                            "/usr/bin/python3.12 -I -S" else "/bin/sh"
                        OfflineBuildPackManager.executeIsolatedForAcceptance(app, staged,
                            workspace, id, operation, prefix + exec + " /workspace/runtime/$command",
                            { onProgress(observed(it)) }, control)
                    }
                    onProgress(observed(ModuleProgress(id,operation,ModulePhase.OFFLINE_TEST,
                        detail="${id.name}: gerçek çevrimdışı fonksiyon testi çalıştırılıyor.")))
                    // Verify the actual prepared Android/Python Gradle cache with a
                    // fresh warmup project and Gradle --offline before acceptance.
                    // This is a Gradle offline resolution gate, not a network sandbox
                    // or a promise that arbitrary future project dependencies exist.
                    if(id in setOf(OfflineModuleId.ANDROID_CORE,OfflineModuleId.PYTHON_ANDROID)) {
                        val mode=if(id==OfflineModuleId.ANDROID_CORE)"android" else "python"
                        OfflineBuildPackManager.executeIsolatedForAcceptance(app,staged,
                            workspace,id,operation,
                            "APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/prepare-offline-pack.sh $mode",
                            { onProgress(observed(it)) },control)
                    }
                    OfflineBuildPackManager.executeIsolatedForAcceptance(app, staged,
                        workspace, id, operation,
                        (if (id == OfflineModuleId.EXPO_REACT_NATIVE)
                            "/bin/sh /workspace/runtime/expo-functional-test-r64.sh && "
                        else "/bin/sh /workspace/runtime/module-functional-test.sh ${id.name} && ") +
                            "/bin/sh /workspace/runtime/isolated-physical-acceptance.sh ${id.name}",
                        { onProgress(observed(it)) }, control)
                    if (id in setOf(OfflineModuleId.ANDROID_CORE, OfflineModuleId.PYTHON_ANDROID,
                            OfflineModuleId.RUST, OfflineModuleId.WINDOWS_NATIVE)) {
                        check(AptModuleLocks.inspect(staged, id).transactions > 0) {
                            "ISOLATED_APT_ADMISSION_NOT_VERIFIED:${id.name}"
                        }
                    }
                    completedUnits++
                    onProgress(observed(ModuleProgress(target,operation,ModulePhase.INSTALLING,
                        detail="Doğrulanmış bağımlılık: $completedUnits/$totalUnits")))
                }
                job.ensureActive(); control.checkpoint()
                if (target == OfflineModuleId.LINUX_RUNTIME) {
                    phase(target, ModulePhase.OFFLINE_TEST,
                        "Ayrı Ubuntu /bin/sh ve dpkg çalıştırma kontrolü.")
                    val result = LinuxShellEngine(app).execute(
                        sessionId = "isolated-linux-$operation", rootfs = staged,
                        workspace = workspace,
                        command = "/bin/sh -c 'test -x /bin/sh && test -f /var/lib/dpkg/status && dpkg-query --version'",
                        confirmed = true, timeoutMs = 120_000L)
                    check(result.exitCode == 0 && !result.timedOut) {
                        "ISOLATED_LINUX_RUNTIME_PROBE_FAILED:" + result.output.takeLast(1000)
                    }
                }
                val seal = File(staged, RECEIPT)
                check(!seal.exists() && !Files.isSymbolicLink(seal.toPath()))
                val versioned = pathsFor(request.dependencyOrder)
                val hashes = JSONObject()
                for (relative in versioned) {
                    val f = File(staged, relative)
                    require(f.isFile && f.canonicalFile.toPath()
                        .startsWith(staged.canonicalFile.toPath())) {
                        "ISOLATED_WITNESS_MISSING:$relative"
                    }
                    hashes.put(relative, VerifiedModuleDownload.sha(f))
                }
                val dpkgStatus = File(staged, "var/lib/dpkg/status")
                require(dpkgStatus.isFile && !Files.isSymbolicLink(dpkgStatus.toPath()))
                val record = JSONObject().put("schema", 3)
                    .put("target", target.name)
                    .put("ubuntuSha256", manifest.archiveSha256)
                    .put("dpkgSha256", VerifiedModuleDownload.sha(dpkgStatus))
                    .put("dependencies", JSONArray(request.dependencyOrder.map { it.name }))
                    .put("files", hashes)
                    .put("functionalTests", "PASS_DEVICE_LOCAL")
                require(record.toString().toByteArray().size <= MAX_RECEIPT)
                seal.parentFile.mkdirs()
                val tmp = File(seal.parentFile, ".receipt-${UUID.randomUUID()}.part")
                FileOutputStream(tmp).use { it.write(record.toString().toByteArray()); it.fd.sync() }
                Files.move(tmp.toPath(), seal.toPath(), StandardCopyOption.ATOMIC_MOVE)
                onProgress(ModuleProgress(target,operation,ModulePhase.VERIFYING,
                    detail="Yalnız özel rootfs ve doğrulanmış araçlar etkinleştiriliyor.",
                    installUnitsCompleted=totalUnits,installUnitsTotal=totalUnits))
                generation.commit(verify = { candidate ->
                    require(File(candidate, RECEIPT).isFile && AndroidRuntimeTrust.ready(candidate))
                    require(File(candidate, "var/lib/dpkg/status").isFile)
                })
            }
            require(ready(app, target)) {
                "ISOLATED_FINAL_RECEIPT_INVALID: installation retained, not marked READY"
            }
            phase(target, ModulePhase.READY,
                "Bağımsız rootfs • APT/dpkg ve gerçek fonksiyon testleri PASS.")
        } finally {
            // Only this operation's disposable workspace; no runtime/cache deletion.
            if (workspace.isDirectory && workspace.parentFile.canonicalFile ==
                    File(app.cacheDir, "isolated-acceptance").canonicalFile)
                workspace.deleteRecursively()
        }
    }

    private data class ToolArchive(val name:String,val url:String,val sha256:String)

    private fun toolArchives(app:Context,id:OfflineModuleId):List<ToolArchive> = when(id) {
        OfflineModuleId.NODE_WEB -> {
            val architecture = if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "arm64" else "x64"
            val name = "node-v22.23.3-linux-$architecture.tar.xz"
            val hash = if (architecture == "arm64")
                "a44aeb94849a299b22df10b9e622ec2f605c2183501bc40590705131de7c740f"
                else "df450af89261115ef9f9e3830c3eeb2cc9213b63c720b1af623cb5dcbe2e02de"
            listOf(ToolArchive(name,"https://nodejs.org/dist/v22.23.3/$name",hash))
        }
        OfflineModuleId.RUST -> {
            val manifest = JSONObject(app.assets.open("device-build/rust-toolchain-v1.json")
                .bufferedReader().use { it.readText() })
            val host = if (Build.SUPPORTED_ABIS.contains("arm64-v8a"))
                "aarch64-unknown-linux-gnu" else "x86_64-unknown-linux-gnu"
            val packages=manifest.getJSONArray("artifacts")
            buildList {
                for(i in 0 until packages.length()) {
                    val item=packages.getJSONObject(i)
                    if(item.getString("host")!=host)continue
                    val name=item.getString("file")
                    require(name.matches(Regex("[A-Za-z0-9_.-]+")))
                    add(ToolArchive(name,item.getString("url"),item.getString("sha256")))
                }
            }.also { require(it.isNotEmpty()) }
        }
        else -> emptyList()
    }

    // This stage is strictly local: if a required archive is missing or changed,
    // copyToStage rejects it. It never silently starts a new network download.
    private fun seedToolArchive(namespace: ModuleIsolationV1.Layout,
        staged: File, workspace: File, id: OfflineModuleId, archives:List<ToolArchive>) {
        val destination = File(staged, "opt/appforge-device/cache").apply { mkdirs() }
        for (archive in archives)
            copyToStage(File(namespace.downloads,archive.name),File(destination,archive.name),archive.sha256)
        if (id == OfflineModuleId.RUST) {
            val original = File(workspace, "runtime/apt-snapshot-lock.sh").readText()
            require(original.split("as_configure() {").size == 2)
            File(workspace, "runtime/rust-apt-base.sh")
                .writeText(original.replace("as_configure() {", "as_configure_original() {"))
        }
    }

    private fun copyToStage(source: File, target: File, expectedSha: String) {
        require(source.isFile && !Files.isSymbolicLink(source.toPath()) &&
            VerifiedModuleDownload.sha(source) == expectedSha)
        require(!target.exists() && !Files.isSymbolicLink(target.toPath()))
        val temp = File(target.parentFile, ".copy-${UUID.randomUUID()}")
        try {
            FileOutputStream(temp).use { out ->
                source.inputStream().use { it.copyTo(out, 65536) }; out.fd.sync()
            }
            require(VerifiedModuleDownload.sha(temp) == expectedSha)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { if (temp.isFile && !Files.isSymbolicLink(temp.toPath())) temp.delete() }
    }

    private fun commands(id: OfflineModuleId, rustAlreadyThere: Boolean): List<String> = when (id) {
        OfflineModuleId.ANDROID_CORE -> listOf("install-toolchain.sh webview-static",
            "prepare-offline-pack.sh android")
        OfflineModuleId.NODE_WEB -> listOf("install-node-module.sh")
        OfflineModuleId.PYTHON_ANDROID -> listOf("install-toolchain.sh python-android",
            "prepare-offline-pack.sh python")
        OfflineModuleId.EXPO_REACT_NATIVE -> listOf("install-toolchain.sh expo")
        OfflineModuleId.RUST -> listOf("install-rust-prerequisites.sh",
            if (rustAlreadyThere) "install-rust-module.py --verify" else "install-rust-module.py")
        OfflineModuleId.RUST_ANDROID_NDK -> listOf("install-toolchain.sh rust-native")
        OfflineModuleId.WINDOWS_NATIVE -> listOf("install-windows-native-toolchain.sh")
        else -> error("Unsupported isolated module: $id")
    }

    private fun copyAssetTree(app: Context, asset: String, target: File) {
        val children = app.assets.list(asset).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            app.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            require(target.isDirectory || target.mkdirs())
            children.forEach { child -> copyAssetTree(app, "$asset/$child", File(target, child)) }
        }
    }
}
