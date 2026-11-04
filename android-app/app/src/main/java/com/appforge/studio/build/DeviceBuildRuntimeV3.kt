package com.appforge.studio.build

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRootfsManifest
import com.appforge.studio.terminal.LinuxRootfsMetadataCodec
import com.appforge.studio.terminal.LinuxRuntimeLayout
import com.appforge.studio.terminal.LinuxRuntimeManifestRegistry
import com.appforge.studio.terminal.PackagedLinuxEngine
import com.appforge.studio.terminal.PackagedLinuxEngineStatus
import com.appforge.studio.terminal.VerifiedLinuxRootfsInstaller
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Dedicated disposable/versioned Linux environment for project builds.
 *
 * IMPORTANT:
 *
 * - It does not reuse Terminal's Linux rootfs.
 * - User project source is mounted only through the build workspace.
 * - A runtime revision mismatch retains the previous dedicated root during replacement.
 * - Terminal files and Terminal package state are never repaired/purged here.
 */
internal object DeviceBuildRuntimeV3 {

    const val REVISION =
        "device-build-runtime-v3"

    private const val MARKER_NAME =
        ".appforge-device-build-runtime"

    private val installMutex =
        Mutex()

    fun platformError():String? {
        if(com.appforge.studio.terminal.ProrootPinnedRuntime.supportedAbi !in Build.SUPPORTED_ABIS)return "Bu APK içindeki Linux yürütücüsü yalnız ${com.appforge.studio.terminal.ProrootPinnedRuntime.supportedAbi} destekliyor; eksik paket değil, cihaz ABI uyumsuzluğu."
        val architecture=LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList())?:return "Bu cihaz mimarisi Linux derleme ortamını desteklemiyor; paket eksikliği değil."
        return if(LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU,architecture)==null)"Bu mimari için doğrulanmış Linux rootfs yok." else null
    }
    fun isReady(context:Context):Boolean = runCatching {
        val architecture=LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList())?:return@runCatching false
        val manifest=LinuxRuntimeManifestRegistry.find(LinuxDistribution.UBUNTU,architecture)?:return@runCatching false
        val layout=LinuxRuntimeLayout(runtimeBaseDirectory(context),LinuxDistribution.UBUNTU,architecture)
        PackagedLinuxEngine(context).inspect().status==PackagedLinuxEngineStatus.READY && isTrustedRuntime(layout,manifest) && AndroidRuntimeTrust.ready(layout.rootfsDirectory)
    }.getOrDefault(false)

    suspend fun ensureReady(
        context: Context,
        onBytes: (com.appforge.studio.terminal.LinuxInstallProgress) -> Unit = {},
        onProgress: (String) -> Unit = {}
    ): File =
        installMutex.withLock {

            val appContext =
                context.applicationContext

            RuntimeArchiveRemoval(runtimeBaseDirectory(appContext)).recover()

            val architecture =
                LinuxArchitecture
                    .fromAndroidAbis(
                        Build.SUPPORTED_ABIS.toList()
                    )
                    ?: error(
                        "Bu cihaz mimarisi Device Build Runtime V3 tarafından desteklenmiyor."
                    )

            val distribution =
                LinuxDistribution.UBUNTU

            val manifest =
                LinuxRuntimeManifestRegistry
                    .find(
                        distribution,
                        architecture
                    )
                    ?: error(
                        "Device Build Runtime V3 için doğrulanmış Ubuntu rootfs manifesti yok."
                    )

            val packagedEngine =
                PackagedLinuxEngine(
                    appContext
                ).inspect()

            check(
                packagedEngine.status ==
                    PackagedLinuxEngineStatus.READY
            ) {
                packagedEngine.detail
            }

            val layout =
                LinuxRuntimeLayout(
                    baseDirectory =
                        runtimeBaseDirectory(
                            appContext
                        ),
                    distribution =
                        distribution,
                    architecture =
                        architecture
                )

            RuntimeGenerationStore(layout.rootfsDirectory).recover()
            com.appforge.studio.terminal.RootfsActivationTransaction(layout.rootfsDirectory,layout.restoreDirectory,layout.metadataFile).recover()
            var freshVendorRoot=false
            if (
                !isTrustedRuntime(
                    layout,
                    manifest
                )
            ) {
                onProgress(
                    "🧼 Clean Device Build Runtime V3 hazırlanıyor."
                )

                val installedReceipts=File(layout.rootfsDirectory,"opt/appforge-device/modules-v3")
                require(!layout.rootfsDirectory.exists() || !installedReceipts.isDirectory || installedReceipts.listFiles().orEmpty().none{it.extension=="json"}) {
                    "Mevcut runtime doğrulanamadı ve kurulu modüller içeriyor. Dosyalar korunuyor; önce son doğrulanmış snapshot'ı geri alın veya bağımlılık sırasıyla güvenli kaldırmayı kullanın. Otomatik sıfırlama yapılmadı."
                }
                // Installer stages and journals root+metadata activation; never erase the active runtime first.
                VerifiedLinuxRootfsInstaller(
                    appContext
                ).install(
                    manifest =
                        manifest,
                    layout =
                        layout
                ) { progress ->
                    onBytes(progress)

                    val percent =
                        progress.percent
                            ?.let {
                                " • %$it"
                            }
                            .orEmpty()

                    onProgress(
                        "🐧 Build Runtime V3 • " +
                            progress.detail +
                            percent
                    )
                }

                freshVendorRoot=true
                val runtimeMarker=marker(layout)
                require(!Files.isSymbolicLink(runtimeMarker.toPath()))
                val temporaryMarker=File(runtimeMarker.path+".part-${java.util.UUID.randomUUID()}")
                java.io.FileOutputStream(temporaryMarker).use{it.write("$REVISION\n".toByteArray());it.fd.sync()}
                Files.move(temporaryMarker.toPath(),runtimeMarker.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } else {
                onProgress(
                    "✅ Clean Device Build Runtime V3 hazır."
                )
            }

            AndroidRuntimeTrust.install(layout.rootfsDirectory)
            if(freshVendorRoot){BaseRuntimeOwnership.captureFresh(layout.rootfsDirectory);AptRuntimeBaseline.captureFresh(layout.rootfsDirectory,manifest)}
            configureResolver(
                appContext,
                layout.rootfsDirectory
            )

            check(
                isTrustedRuntime(
                    layout,
                    manifest
                ) && AndroidRuntimeTrust.ready(layout.rootfsDirectory)
            ) {
                "Device Build Runtime V3 bütünlük kontrolünden geçmedi."
            }

            layout
                .rootfsDirectory
                .canonicalFile
        }


    /**
     * Prepares a verified, side-by-side Ubuntu Base candidate.
     *
     * Does NOT replace the active runtime, migrate module receipts,
     * or mark Python/Node as installed in the new environment.
     * Existing legacy APT provenance remains blocked.
     */
    suspend fun prepareIsolatedRecovery(
        context:Context,
        onProgress:(String)->Unit={}
    ):String = installMutex.withLock {
        val app=context.applicationContext
        check(platformError()==null) {
            platformError()?:"Unsupported platform"
        }
        val architecture=LinuxArchitecture.fromAndroidAbis(
            Build.SUPPORTED_ABIS.toList()
        ) ?: error("Desteklenmeyen mimari")

        val manifest=LinuxRuntimeManifestRegistry.find(
            LinuxDistribution.UBUNTU,architecture
        ) ?: error("Doğrulanmış Ubuntu manifesti yok")

        val current=LinuxRuntimeLayout(
            runtimeBaseDirectory(app),
            LinuxDistribution.UBUNTU,architecture
        ).rootfsDirectory

        RecoveryPathAnchor.check(current)
        check(current.isDirectory) {
            "Korunacak aktif rootfs bulunamadı."
        }
        check(AptSnapshotMigration.blockedReason(current)!=null) {
            "Legacy APT engeli yok; izole kurtarma gerekmiyor."
        }
        check(OfflineBuildPackManager.moduleReady(
            app,OfflineModuleId.NODE_WEB
        )) {
            "Node.js bütünlüğü doğrulanamadı; mevcut ortam korunuyor."
        }

        val base=RecoveryPathAnchor.androidPlatformPath(
            File(app.noBackupFilesDir,
                "device-build/runtime-v9-recovery-candidate")
        )
        RecoveryPathAnchor.check(base)
        check(base.canonicalFile !=
            runtimeBaseDirectory(app).canonicalFile)

        val layout=LinuxRuntimeLayout(
            base,LinuxDistribution.UBUNTU,architecture
        )

        if(layout.runtimeDirectory.exists()) {
            check(
                isTrustedRuntime(layout,manifest) &&
                AndroidRuntimeTrust.ready(layout.rootfsDirectory) &&
                BaseRuntimeOwnership.paths(
                    layout.rootfsDirectory
                ).isNotEmpty() &&
                File(
                    layout.rootfsDirectory,
                    "opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json"
                ).isFile
            ) {
                "Önceki izole hazırlık eksik veya doğrulanamıyor. "+
                "Mevcut ortam korunuyor; hiçbir dosya otomatik silinmedi."
            }
            return@withLock "ISOLATED_UBUNTU_ALREADY_VERIFIED"
        }

        val minimum=maxOf(
            2L*1024*1024*1024,
            manifest.maxArchiveBytes*3+
                512L*1024*1024
        )
        check(app.noBackupFilesDir.usableSpace>minimum) {
            "İzole Ubuntu hazırlığı için yeterli boş alan yok. "+
            "Aktif runtime ve Node.js değiştirilmedi."
        }

        check(PackagedLinuxEngine(app).inspect().status==
            PackagedLinuxEngineStatus.READY) {
            "Paketli Linux yürütücüsü doğrulanamadı."
        }

        onProgress("İzole Ubuntu indiriliyor; aktif Node.js korunuyor.")

        VerifiedLinuxRootfsInstaller(app).install(
            manifest=manifest,
            layout=layout
        ) { event ->
            onProgress(event.detail+
                (event.percent?.let{" • %$it"}?:""))
        }

        val mark=marker(layout)
        RecoveryPathAnchor.check(mark)
        check(!mark.exists())
        val part=File(mark.path+".part")
        RecoveryPathAnchor.check(part)

        java.io.FileOutputStream(part).use {
            it.write(REVISION.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        Files.move(
            part.toPath(),mark.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE
        )

        val clean=layout.rootfsDirectory
        AndroidRuntimeTrust.install(clean)
        BaseRuntimeOwnership.captureFresh(clean)
        AptRuntimeBaseline.captureFresh(clean,manifest)

        check(isTrustedRuntime(layout,manifest))
        check(AndroidRuntimeTrust.ready(clean))
        check(BaseRuntimeOwnership.paths(clean).isNotEmpty())
        check(File(
            clean,
            "opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json"
        ).isFile)

        check(AptSnapshotMigration.blockedReason(clean)==null)
        check(AptSnapshotMigration.blockedReason(current)!=null)
        check(OfflineBuildPackManager.moduleReady(
            app,OfflineModuleId.NODE_WEB
        )) {
            "Eski Node.js bütünlüğü değişmiş; geçiş engellendi."
        }

        onProgress(
            "Temiz Ubuntu doğrulandı. Aktif Node.js ve kullanıcı verileri "+
            "korundu. Yeni ortam henüz etkinleştirilmedi."
        )
        "ISOLATED_UBUNTU_PREPARED;ACTIVE_RUNTIME_UNCHANGED"
    }

    /**
     * Debug-only and offline: transfer SHA-256 pinned R2 .deb objects into
     * the isolated Ubuntu candidate's CAS. Never execute dpkg, activate
     * the candidate, change active receipts or mark modules READY.
     */
    suspend fun stageIsolatedR2Cache(context:Context):String =
        installMutex.withLock {
        check(com.appforge.studio.BuildConfig.DEBUG) {
            "İzole R2 hazırlama yalnız test sürümünde kullanılabilir."
        }
        val app=context.applicationContext
        check(platformError()==null) {
            platformError()?:"Desteklenmeyen platform"
        }
        val architecture=LinuxArchitecture.fromAndroidAbis(
            Build.SUPPORTED_ABIS.toList()
        ) ?: error("Desteklenmeyen mimari")
        val arch=architecture.distroArchitecture
        check(arch=="arm64" || arch=="amd64")
        val manifest=LinuxRuntimeManifestRegistry.find(
            LinuxDistribution.UBUNTU,architecture
        ) ?: error("Sabitlenmiş Ubuntu manifesti bulunamadı")

        val current=LinuxRuntimeLayout(
            runtimeBaseDirectory(app),
            LinuxDistribution.UBUNTU,architecture
        ).rootfsDirectory
        RecoveryPathAnchor.check(current)
        check(AptSnapshotMigration.blockedReason(current)!=null) {
            "Eski runtime güvenlik engeli bekleniyordu."
        }
        check(OfflineBuildPackManager.moduleReady(
            app,OfflineModuleId.NODE_WEB
        )) {
            "Aktif Node.js bütünlüğü doğrulanamıyor."
        }
        val oldReceipt=File(
            current,"opt/appforge-device/modules-v3/NODE_WEB.json"
        )
        check(oldReceipt.isFile &&
            !Files.isSymbolicLink(oldReceipt.toPath()))
        val oldHash=VerifiedModuleDownload.sha(oldReceipt)

        val candidateBase=RecoveryPathAnchor.androidPlatformPath(
            File(app.noBackupFilesDir,
                "device-build/runtime-v9-recovery-candidate")
        )
        val layout=LinuxRuntimeLayout(
            candidateBase,LinuxDistribution.UBUNTU,architecture
        )
        val root=layout.rootfsDirectory
        RecoveryPathAnchor.check(root)
        check(root.canonicalFile!=current.canonicalFile)
        check(isTrustedRuntime(layout,manifest)) {
            "İzole Ubuntu sürüm/metadata doğrulaması başarısız."
        }
        check(AndroidRuntimeTrust.ready(root)) {
            "İzole Ubuntu TLS kayıtları doğrulanamadı."
        }
        check(BaseRuntimeOwnership.paths(root).isNotEmpty()) {
            "İzole Ubuntu sahiplik envanteri bulunamadı."
        }
        val status=File(root,"var/lib/dpkg/status")
        val baseline=File(
            root,
            "opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json"
        )
        check(status.isFile && baseline.isFile)
        check(!Files.isSymbolicLink(status.toPath()) &&
            !Files.isSymbolicLink(baseline.toPath()))
        val vendor=org.json.JSONObject(baseline.readText())
        check(vendor.getInt("schema")==1 &&
            vendor.getString("release")==manifest.release &&
            vendor.getString("architecture")==architecture.id &&
            vendor.getString("rootfsSha256")==manifest.archiveSha256 &&
            vendor.getString("source")==manifest.archiveUrl &&
            vendor.getString("statusSha256")==
                VerifiedModuleDownload.sha(status)) {
            "İzole Ubuntu APT başlangıç envanteri uyuşmuyor."
        }
        val receipts=File(root,"opt/appforge-device/modules-v3")
        check(!Files.isSymbolicLink(receipts.toPath()) &&
            receipts.listFiles().orEmpty().none{it.extension=="json"}) {
            "İzole aday artık kurulum kayıtları içeriyor; işlem reddedildi."
        }
        check(AptSnapshotMigration.blockedReason(root)==null) {
            "İzole adayın APT geçiş güvenliği doğrulanmadı."
        }

        // Includes Android Core dependencies required by Python.
        val selected=setOf(
            OfflineModuleId.NODE_WEB,
            OfflineModuleId.PYTHON_ANDROID
        )
        val plan=V9SelectiveR2ModuleCache.preview(
            app,selected,arch
        )
        val cache=V9SignedR2CandidateDownload.privateCacheDirectory(
            app.noBackupFilesDir
        )
        val available=V9SelectiveR2ModuleCache.inspectCache(
            cache,plan.packages
        )
        check(app.noBackupFilesDir.usableSpace>
            available.existingBytes+256L*1024*1024) {
            "İzole R2 hazırlığı için yeterli boş alan yok."
        }

        val result=V9R2AptInstallReuse.seedExisting(
            cache,root,arch,plan.packages
        )
        check(result.available==available.existing.size &&
            result.missing==available.missing.size) {
            "R2 aktarım sayıları tutarsız."
        }

        check(isTrustedRuntime(layout,manifest) &&
            AndroidRuntimeTrust.ready(root) &&
            VerifiedModuleDownload.sha(status)==
                vendor.getString("statusSha256"))
        check(AptSnapshotMigration.blockedReason(root)==null)
        check(OfflineBuildPackManager.moduleReady(
            app,OfflineModuleId.NODE_WEB
        ))
        check(VerifiedModuleDownload.sha(oldReceipt)==oldHash) {
            "Eski Node.js kaydı değişti; işlem reddedildi."
        }
        check(receipts.listFiles().orEmpty().none{
            it.extension=="json"
        })

        "ISOLATED_R2_CACHE_STAGED;VERIFIED=${result.available};"+
        "MISSING=${result.missing};REUSED_BYTES=${result.verifiedBytes};"+
        "ACTIVE_RUNTIME_UNCHANGED;MODULES_NOT_INSTALLED"
    }

    /**
     * Debug-only official Ubuntu Snapshot package prefetch.
     * This is NOT Ed25519 R2 release authorization or APT installation.
     * All objects are pinned by the APK catalog and SHA-256.
     * No active runtime mutation, dpkg operation or READY receipt.
     */
    suspend fun prefetchIsolatedUbuntuPackages(
        context:Context,
        onProgress:(String)->Unit={}
    ):String {
        check(com.appforge.studio.BuildConfig.DEBUG) {
            "Ubuntu önbellek hazırlığı yalnız debug sürümünde."
        }

        // Recheck the candidate and the protected Node.js generation.
        stageIsolatedR2Cache(context)

        return installMutex.withLock {
            BuildModuleUsage.mutationLease().use {
                val app=context.applicationContext
                DownloadNetwork.configure(app)
                // User-selected permanent allowance for all validated networks.
                // This does not bypass TLS, SHA-256 or APT signature checks.
                DownloadNetwork.select(DownloadNetworkPolicy.ALLOW_ANY)
                DownloadNetwork.prepareJournal()

                check(DownloadNetwork.allowed(
                    DownloadNetworkPolicy.ALLOW_ANY
                )) {
                    "Doğrulanmış internet bağlantısı gerekli."
                }

                val architecture=LinuxArchitecture.fromAndroidAbis(
                    Build.SUPPORTED_ABIS.toList()
                ) ?: error("Desteklenmeyen mimari")
                val arch=architecture.distroArchitecture
                check(arch=="arm64" || arch=="amd64")

                val manifest=LinuxRuntimeManifestRegistry.find(
                    LinuxDistribution.UBUNTU,architecture
                ) ?: error("Ubuntu manifesti bulunamadı")

                val active=LinuxRuntimeLayout(
                    runtimeBaseDirectory(app),
                    LinuxDistribution.UBUNTU,architecture
                ).rootfsDirectory

                val oldReceipt=File(active,
                    "opt/appforge-device/modules-v3/NODE_WEB.json")
                check(oldReceipt.isFile &&
                    !Files.isSymbolicLink(oldReceipt.toPath()))
                val oldHash=VerifiedModuleDownload.sha(oldReceipt)

                check(AptSnapshotMigration.blockedReason(active)!=null)
                check(OfflineBuildPackManager.moduleReady(
                    app,OfflineModuleId.NODE_WEB
                ))

                val base=RecoveryPathAnchor.androidPlatformPath(
                    File(app.noBackupFilesDir,
                        "device-build/runtime-v9-recovery-candidate")
                )
                val layout=LinuxRuntimeLayout(
                    base,LinuxDistribution.UBUNTU,architecture
                )
                val root=layout.rootfsDirectory

                RecoveryPathAnchor.check(root)
                check(root.canonicalFile!=active.canonicalFile)
                check(isTrustedRuntime(layout,manifest) &&
                    AndroidRuntimeTrust.ready(root))
                check(BaseRuntimeOwnership.paths(root).isNotEmpty())

                val status=File(root,"var/lib/dpkg/status")
                val baseline=File(root,
                    "opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json")
                check(status.isFile && baseline.isFile)
                val baselineJson=org.json.JSONObject(
                    baseline.readText()
                )
                val originalStatusHash=
                    baselineJson.getString("statusSha256")
                check(originalStatusHash==
                    VerifiedModuleDownload.sha(status))

                val selected=setOf(
                    OfflineModuleId.NODE_WEB,
                    OfflineModuleId.PYTHON_ANDROID
                )
                val plan=V9SelectiveR2ModuleCache.preview(
                    app,selected,arch
                )
                val expected=plan.packages.associateBy{
                    it.sha256
                }
                val closure=OfflineModules.resolve(selected).toSet()

                val catalog=app.assets.open(
                    "device-build/apt-module-packages-v1.tsv"
                ).use{it.readBytes()}
                check(catalog.isNotEmpty() &&
                    catalog.size<=512*1024)

                val catalogHash=MessageDigest.getInstance(
                    "SHA-256"
                ).digest(catalog).joinToString("") {
                    "%02x".format(it.toInt() and 255)
                }
                check(catalogHash==AptModuleLocks.CATALOG) {
                    "APK Ubuntu katalog SHA-256 uyuşmuyor."
                }

                val urls=linkedMapOf<String,String>()

                for(line in catalog.toString(
                    Charsets.UTF_8
                ).lineSequence().filter{it.isNotBlank()}) {
                    val f=line.split('\t')
                    check(f.size in 8..10)
                    val id=OfflineModuleId.valueOf(f[0])
                    if(id !in closure || f[1]!=arch)continue

                    val sha=f[5]
                    val record=expected[sha]
                        ?: error("Beklenmeyen katalog paketi")
                    check(f[6].toLong()==record.bytes)
                    check(f[4]=="all" || f[4]==arch)

                    val url=f[7]
                    val uri=java.net.URI(url)
                    val raw=uri.rawPath.orEmpty()
                    val lower=raw.lowercase(java.util.Locale.ROOT)

                    check(uri.scheme=="https" &&
                        uri.host=="snapshot.ubuntu.com" &&
                        uri.port==-1 &&
                        uri.userInfo==null &&
                        uri.rawQuery==null &&
                        uri.rawFragment==null &&
                        uri.rawAuthority=="snapshot.ubuntu.com" &&
                        raw.startsWith(
                            "/ubuntu/${AptModuleLocks.SNAPSHOT}/pool/"
                        ) &&
                        raw.endsWith(".deb") &&
                        !raw.contains("//") &&
                        !raw.contains("/./") &&
                        !raw.contains("/../") &&
                        !raw.contains('\\') &&
                        !lower.contains("%2e") &&
                        !lower.contains("%2f") &&
                        !lower.contains("%5c") &&
                        !lower.contains("%00") &&
                        !lower.contains("%25") &&
                        uri.normalize().rawPath==raw) {
                        "Güvenilmeyen Ubuntu snapshot adresi."
                    }

                    val previous=urls.putIfAbsent(sha,url)
                    check(previous==null || previous==url) {
                        "Aynı SHA için farklı kaynak tanımlandı."
                    }
                }

                check(urls.keys==expected.keys) {
                    "Sabitlenmiş paket kataloğu eksik."
                }

                val cas=File(root,
                    "opt/appforge-device/apt-snapshot-v1/"+
                    "$arch-${AptModuleLocks.SNAPSHOT}/archives"
                )
                check(cas.isDirectory &&
                    !Files.isSymbolicLink(cas.toPath()) &&
                    cas.canonicalFile.toPath().startsWith(
                        root.canonicalFile.toPath()
                    ))

                val cacheRoot=RecoveryPathAnchor.androidPlatformPath(
                    File(app.noBackupFilesDir,
                        "v9-ubuntu-pinned-prefetch-v1")
                )
                RecoveryPathAnchor.check(cacheRoot)
                check(!Files.isSymbolicLink(cacheRoot.toPath()))
                if(!cacheRoot.exists())check(cacheRoot.mkdir())
                check(cacheRoot.isDirectory &&
                    cacheRoot.canonicalFile.parentFile==
                        app.noBackupFilesDir.canonicalFile)

                val missing=plan.packages.filter{item->
                    val f=File(cas,item.sha256+".deb")
                    check(!Files.isSymbolicLink(f.toPath()))
                    if(f.exists()) {
                        check(f.isFile &&
                            f.length()==item.bytes &&
                            VerifiedModuleDownload.sha(f)==item.sha256) {
                            "İzole APT önbelleği değişmiş."
                        }
                        false
                    } else true
                }

                val required=missing.sumOf{it.bytes}
                check(app.noBackupFilesDir.usableSpace>
                    required*2+256L*1024*1024) {
                    "Paket önbelleği için depolama yetersiz."
                }

                val job=kotlin.coroutines.coroutineContext[
                    kotlinx.coroutines.Job
                ]
                val operation="v9-ubuntu-prefetch-"+
                    java.util.UUID.randomUUID().toString()
                var downloaded=0
                var reused=0

                onProgress(
                    "Sabitlenmiş Ubuntu paketleri: "+
                    "${plan.packages.size-missing.size}/"+
                    "${plan.packages.size} mevcut; "+
                    "${missing.size} eksik."
                )

                for((index,item) in missing.withIndex()) {
                    check(job?.isActive!=false)
                    check(DownloadNetwork.allowed(
                        DownloadNetworkPolicy.ALLOW_ANY
                    )) {
                        "İnternet bağlantısı kesildi; doğrulanmış paketler korundu."
                    }

                    val target=File(cacheRoot,item.sha256)
                    check(!Files.isSymbolicLink(target.toPath()))

                    val cached=target.isFile &&
                        target.length()==item.bytes &&
                        VerifiedModuleDownload.sha(target)==item.sha256

                    check(!target.exists() || cached) {
                        "Bozuk önbellek nesnesi otomatik değiştirilmez."
                    }

                    if(cached) {
                        reused++
                    } else {
                        val source=urls.getValue(item.sha256)
                        onProgress(
                            "İndiriliyor: ${index+1}/${missing.size}"
                        )

                        VerifiedModuleDownload.download(
                            url=source,
                            expected=item.sha256,
                            target=target,
                            module=OfflineModuleId.PYTHON_ANDROID,
                            operation=operation,
                            canceled={job?.isActive==false},
                            open={address->
                                DownloadNetwork.withPolicy(
                                    DownloadNetworkPolicy.ALLOW_ANY
                                ){
                                    DownloadNetwork.open(address)
                                }
                            },
                            progress={event->
                                onProgress(
                                    "Paket ${index+1}/${missing.size} • "+
                                    "${event.phase} • "+
                                    "${event.bytesDownloaded} bayt"
                                )
                            },
                            maxBytes=item.bytes
                        )
                        downloaded++
                    }

                    check(target.length()==item.bytes &&
                        VerifiedModuleDownload.sha(target)==item.sha256)

                    val staged=V9R2AptInstallReuse.seedExisting(
                        cacheRoot,root,arch,listOf(item)
                    )
                    check(staged.available==1 &&
                        staged.missing==0)
                }

                for(item in plan.packages) {
                    val file=File(cas,item.sha256+".deb")
                    check(file.isFile &&
                        !Files.isSymbolicLink(file.toPath()) &&
                        file.length()==item.bytes &&
                        VerifiedModuleDownload.sha(file)==item.sha256) {
                        "İzole Ubuntu CAS tamlığı sağlanamadı."
                    }
                }

                check(VerifiedModuleDownload.sha(status)==
                    originalStatusHash)
                check(AndroidRuntimeTrust.ready(root))
                check(isTrustedRuntime(layout,manifest))
                check(AptSnapshotMigration.blockedReason(root)==null)
                check(AptSnapshotMigration.blockedReason(active)!=null)
                check(OfflineBuildPackManager.moduleReady(
                    app,OfflineModuleId.NODE_WEB
                ))
                check(VerifiedModuleDownload.sha(oldReceipt)==oldHash)

                val receipts=File(root,
                    "opt/appforge-device/modules-v3")
                check(receipts.listFiles().orEmpty().none{
                    it.extension=="json"
                })

                "ISOLATED_UBUNTU_CATALOG_CACHE_READY;"+
                "VERIFIED=${plan.packages.size};"+
                "DOWNLOADED=$downloaded;"+
                "REUSED=$reused;"+
                "APT_SIGNATURES_NOT_VERIFIED;"+
                "MODULES_NOT_INSTALLED;"+
                "ACTIVE_RUNTIME_UNCHANGED"
            }
        }
    }

    internal fun runtimeBaseDirectory(
        context: Context
    ): File = RecoveryPathAnchor.androidPlatformPath(
        File(
            context.noBackupFilesDir,
            "device-build/runtime-v3"
        ))


    private fun marker(
        layout: LinuxRuntimeLayout
    ): File =
        File(
            layout.runtimeDirectory,
            MARKER_NAME
        )


    private fun isTrustedRuntime(
        layout: LinuxRuntimeLayout,
        manifest: LinuxRootfsManifest
    ): Boolean {

        val rootfs =
            layout.rootfsDirectory

        if (
            !rootfs.isDirectory ||
            !File(rootfs, "etc").isDirectory ||
            !File(rootfs, "usr").isDirectory ||
            !File(rootfs, "bin").exists()
        ) {
            return false
        }

        val marker =
            marker(
                layout
            )

        if (
            !marker.isFile ||
            marker.length() >
                128L ||
            runCatching {
                marker
                    .readText(
                        Charsets.UTF_8
                    )
                    .trim()
            }.getOrNull() !=
                REVISION
        ) {
            return false
        }

        val metadata =
            LinuxRootfsMetadataCodec
                .read(
                    layout.metadataFile
                )
                ?: return false

        if (
            metadata.distribution !=
                manifest.distribution ||
            metadata.architecture !=
                manifest.architecture ||
            metadata.release !=
                manifest.release
        ) {
            return false
        }

        return MessageDigest.isEqual(
            metadata.archiveSha256
                .lowercase()
                .toByteArray(
                    Charsets.US_ASCII
                ),
            manifest.archiveSha256
                .lowercase()
                .toByteArray(
                    Charsets.US_ASCII
                )
        )
    }


    private fun configureResolver(
        context: Context,
        rootfs: File
    ) {
        val connectivity =
            context.getSystemService(
                ConnectivityManager::class.java
            )
                ?: return

        val network =
            connectivity.activeNetwork
                ?: return

        val dnsServers =
            connectivity
                .getLinkProperties(
                    network
                )
                ?.dnsServers
                .orEmpty()
                .mapNotNull {
                    it.hostAddress
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        if (
            dnsServers.isEmpty()
        ) {
            return
        }

        val resolv =
            File(
                rootfs,
                "etc/resolv.conf"
            )

        runCatching {

            if (
                Files.isSymbolicLink(
                    resolv.toPath()
                )
            ) {
                Files.deleteIfExists(
                    resolv.toPath()
                )
            }

            resolv
                .parentFile
                ?.mkdirs()

            resolv.writeText(
                dnsServers.joinToString(
                    separator = "\n",
                    postfix = "\n"
                ) {
                    "nameserver $it"
                },
                Charsets.UTF_8
            )
        }
    }
}
