package com.appforge.studio.build

import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRuntimeLayout
import com.appforge.studio.terminal.LinuxShellEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

internal data class OfflineBuildPackStatus(
    val androidCoreReady: Boolean,
    val nodeToolchainReady: Boolean,
    val pythonAndroidReady: Boolean,
    val expoToolchainReady: Boolean,
    val windowsNativeToolchainReady: Boolean,
    val windowsHostReady: Boolean,
    val windowsExeReady: Boolean
) {
    val currentAndroidEnginesReady: Boolean
        get() =
            androidCoreReady &&
                nodeToolchainReady &&
                pythonAndroidReady &&
                expoToolchainReady

    val completeTargetReady: Boolean
        get() =
            currentAndroidEnginesReady &&
                windowsNativeToolchainReady &&
                windowsExeReady
}

internal data class OfflineBuildRequirementCheck(
    val ready: Boolean,
    val engine: String,
    val missing: List<String>,
    val missingModules:Set<OfflineModuleId> = emptySet()
)

internal object OfflineBuildPackManager {

    const val REVISION =
        "offline-build-pack-v1"

    // Aggregate size is unknown: APT/Gradle/npm depend on device and project.
    const val ESTIMATED_DOWNLOAD_BYTES =
        0L

    const val ESTIMATED_INSTALLED_BYTES =
        0L

    private const val PACK_DIRECTORY =
        "opt/appforge-device/offline-pack-v1"

    /*
     * Acceptance completed on 2026-09-22:
     * Windows host CI -> Android offline packager -> physical Windows 11
     * smoke -> normal Studio web-project EXE -> JavaScript runtime PASS.
     */
    private const val WINDOWS_EXE_ACCEPTED =
        true

    private val installMutex =
        Mutex()

    fun status(
        context: Context
    ): OfflineBuildPackStatus {

        val appContext =
            context.applicationContext

        val windowsHostReady =
            WindowsPortableHostStore
                .isInstalled(
                    appContext
                )

        val rootfs =
            runtimeRootfs(
                appContext
            )

        if (
            rootfs == null ||
            !rootfs.isDirectory
        ) {
            return OfflineBuildPackStatus(
                androidCoreReady = false,
                nodeToolchainReady = false,
                pythonAndroidReady = false,
                expoToolchainReady = false,
                windowsNativeToolchainReady = false,
                windowsHostReady =
                    windowsHostReady,
                windowsExeReady =
                    windowsHostReady &&
                        WINDOWS_EXE_ACCEPTED
            )
        }

        return OfflineBuildPackStatus(
            androidCoreReady = moduleReady(appContext, OfflineModuleId.ANDROID_CORE),
            nodeToolchainReady = moduleReady(appContext, OfflineModuleId.NODE_WEB),
            pythonAndroidReady = moduleReady(appContext, OfflineModuleId.PYTHON_ANDROID),
            expoToolchainReady = moduleReady(appContext, OfflineModuleId.EXPO_REACT_NATIVE),
            windowsNativeToolchainReady = moduleReady(appContext, OfflineModuleId.WINDOWS_NATIVE),
            windowsHostReady = windowsHostReady,
            windowsExeReady = windowsHostReady && WINDOWS_EXE_ACCEPTED
        )
    }

    fun checkBuildRequirements(
        context: Context,
        draft: ProjectDraft
    ): OfflineBuildRequirementCheck {

        val readiness = WizardReadinessEvaluator.evaluate(draft)
        if (!readiness.ready) return OfflineBuildRequirementCheck(
            ready = false, engine = "webview-settings",
            missing = readiness.diagnostics.filter { it.blocking }.map { it.turkish() }
        )
        val settingsError = runCatching { WebviewProjectSettings.validate(draft) }
            .exceptionOrNull()?.message
        if (settingsError != null) return OfflineBuildRequirementCheck(
            ready = false, engine = "webview-settings", missing = listOf(settingsError)
        )
        // Supported Fast HTML V2 combinations use bundled templates; remaining features need the normal engine.
        // A dedicated, bundled template produces and signs their APKs locally.
        // Shared output-aware policy: only Android outputs use Fast HTML.
        // Portable EXE and mixed Android+Windows use normal toolchain checks.
        if (FastHtmlApkPolicy.isCandidate(draft)) {
            val reason = runCatching { requireFastStaticOutput(context, draft) }
                .exceptionOrNull()?.message
            return OfflineBuildRequirementCheck(
                ready = reason == null,
                engine = "fast-html-apk",
                missing = listOfNotNull(reason)
            )
        }

        recoverPending(context)
        val plan = OfflineModules.forProject(draft)
        if(plan.error != null)return OfflineBuildRequirementCheck(false,"webview-settings",listOf(plan.error))
        if(plan.modules.any{it !in setOf(OfflineModuleId.FAST_HTML,OfflineModuleId.WINDOWS_PORTABLE)}) {
            val unsupported=DeviceBuildRuntimeV3.platformError()
            if(unsupported!=null)return OfflineBuildRequirementCheck(false,"platform-unsupported",listOf(unsupported))
        }
        val missing=plan.modules.filterNot{moduleReady(context,it)}
        return OfflineBuildRequirementCheck(missing.isEmpty(),draft.sourceBuildEngine,
            missing.filter{it!=OfflineModuleId.LINUX_RUNTIME}.map{OfflineModules.spec(it).title},missing.toSet())
    }

    /** Only local static AAB requests bypass optional Android/Gradle toolchains. */
    private fun requireFastStaticOutput(context: Context, draft: ProjectDraft) {
        val selected = DeviceBuildCapabilities.requestedOutputs(draft.buildOutput)
        TemplateIntegrity.requireBundled(context, buildSet {
            if (DeviceArtifactKind.APK in selected) add("html-apk-template.apk")
            if (DeviceArtifactKind.AAB in selected) add("html-aab-template.aab")
        })
        FastHtmlIcon.read(context, draft)
        if (draft.sourceMode == com.appforge.studio.model.SourceMode.LOCAL) FastHtmlApkPackager.readHtmlProject(draft)
        if (DeviceArtifactKind.AAB in selected) {
            FastHtmlAabUnsignedPackager.requireSource(draft)
            if (draft.signingMode == com.appforge.studio.model.SigningMode.CUSTOM) {
                FastHtmlAabUploadKey.requireSelection(context, draft)
            }
        } else {
            FastHtmlApkPolicy.requireSupported(draft)
        }
    }

    fun requireReadyForBuild(
        context: Context,
        draft: ProjectDraft
    ) {
        // Shared output-aware policy: only Android outputs use Fast HTML.
        // Portable EXE and mixed Android+Windows use normal toolchain checks.
        if (FastHtmlApkPolicy.isCandidate(draft)) {
            requireFastStaticOutput(context, draft)
            return
        }
        val check =
            checkBuildRequirements(
                context,
                draft
            )

        require(
            check.ready
        ) {
            "APPFORGE_OFFLINE_BUILD_PACK_REQUIRED: " +
                check.missing.joinToString(
                    ", "
                ) +
                ". Ayarlar > Modüler Çevrimdışı Derleme V3 bölümünden " +
                "gerekli bileşenleri indir."
        }
    }

    /** Explicit selection only: there is no install-everything default. */
    suspend fun installReadyComponents(
        context: Context, androidSdkLicenseAccepted: Boolean,
        modules: Set<OfflineModuleId>, onProgress: (String) -> Unit = {}
    ): OfflineBuildPackStatus {
        installModules(context,modules,androidSdkLicenseAccepted){onProgress(it.detail)}
        return status(context)
    }

    suspend fun installModules(context:Context, selected:Set<OfflineModuleId>, sdkConsent:Boolean, control:DownloadControl=DownloadControl(),
        progress:(ModuleProgress)->Unit = {}) = installMutex.withLock {
        BuildModuleUsage.mutationLease().use {
        control.checkpoint()
        require(selected.isNotEmpty()){ "Önce kurulacak modülü seçin." }
        val app=context.applicationContext
        DownloadNetwork.configure(app);DownloadNetwork.prepareJournal()
        val plan=OfflineModules.resolve(selected)
        val operation=UUID.randomUUID().toString()
        if(plan.any{it !in setOf(OfflineModuleId.FAST_HTML,OfflineModuleId.WINDOWS_PORTABLE)})require(DeviceBuildRuntimeV3.platformError()==null){DeviceBuildRuntimeV3.platformError()!!}
        val pending=plan.filterNot{moduleReady(app,it)}
        if(pending.isEmpty())return@withLock
        if(pending.all{it==OfflineModuleId.FAST_HTML}) {
            TemplateIntegrity.requireBundled(app,setOf("html-apk-template.apk","html-aab-template.aab"))
            progress(ModuleProgress(OfflineModuleId.FAST_HTML,operation,ModulePhase.READY,detail="Gömülü şablonlar doğrulandı; indirme veya SDK yok."))
            return@withLock
        }
        if(pending.any{it==OfflineModuleId.ANDROID_CORE || it==OfflineModuleId.EXPO_REACT_NATIVE})
            require(sdkConsent){"Yalnız Android SDK gerektiren modüller için SDK lisansı onayı gerekir."}
        require(hasValidatedInternet(app)){"Kurulum için doğrulanmış internet bağlantısı gerekiyor; mevcut modüller korunur."}
        val minimum=selected.mapNotNull{OfflineModules.spec(it).diskBytes}.sum()
        require(app.noBackupFilesDir.usableSpace>minimum+256L*1024*1024){"Kurulum için yeterli boş alan yok."}
        val workspace=File(app.cacheDir,"offline-module-v3/${UUID.randomUUID()}").apply{mkdirs()}
        var active=pending.first()
        fun phase(id:OfflineModuleId,p:ModulePhase,detail:String) {
            val event=ModuleProgress(id,operation,p,detail=detail)
            DownloadCoordinator.modulePhase(event);progress(event)
        }
        try {
            phase(active,ModulePhase.RESOLVING,"Yalnız gerekli modüller: "+pending.joinToString{OfflineModules.spec(it).title})
            if(OfflineModuleId.WINDOWS_PORTABLE in pending) {
                active=OfflineModuleId.WINDOWS_PORTABLE
                phase(active,ModulePhase.CONNECTING,"Windows Portable: Android SDK lisansı veya Linux kurulumu gerekmez.")
                val hostSource=java.security.MessageDigest.getInstance("SHA-256").digest(WindowsPortableHostStore.HOST_URL.toByteArray()).joinToString(""){"%02x".format(it)}
                DownloadCoordinator.bind(operation,WindowsPortableHostStore.HOST_SHA256,WindowsPortableHostStore.journalTarget(app))
                WindowsPortableHostStore.install(app, onProgress={message->phase(OfflineModuleId.WINDOWS_PORTABLE,ModulePhase.INSTALLING,message)}, onBytes={bytes->
                    control.checkpoint()
                    val event=bytes.copy(operationId=operation)
                    DownloadCoordinator.observe(DownloadCoordinator.Transfer(WindowsPortableHostStore.HOST_SHA256,WindowsPortableHostStore.REVISION,hostSource,event,DownloadCoordinator.ResumeCapability.SAFE_RESTART_ONLY))
                    progress(event)
                })
                DownloadCoordinator.observe(DownloadCoordinator.Transfer(WindowsPortableHostStore.HOST_SHA256,WindowsPortableHostStore.REVISION,hostSource,ModuleProgress(active,operation,ModulePhase.PREPARING,bytesTotal=WindowsPortableHostStore.HOST_BYTES),DownloadCoordinator.ResumeCapability.SAFE_RESTART_ONLY,verificationStatus="SHA256_VERIFIED"))
                check(moduleReady(app,active)){"Windows host doğrulaması başarısız."}
                phase(active,ModulePhase.READY,"SHA-256 doğrulandı; Windows Portable hazır.")
            }
            control.checkpoint()
            val linux=pending.filter{it !in setOf(OfflineModuleId.WINDOWS_PORTABLE,OfflineModuleId.FAST_HTML)}
            if(linux.isNotEmpty()) {
                phase(OfflineModuleId.LINUX_RUNTIME,ModulePhase.INSTALLING,"Ortak Linux ortamı hazırlanıyor; toplam boyut henüz bilinmiyor.")
                var rootMeter:DownloadMeasurement?=null
                var rootReported=0L
                var adoptedRootOperation:String?=null
                val root=DeviceBuildRuntimeV3.ensureReady(app,onBytes=rootBytes@{e->
                    e.transfer?.let { transfer ->
                        if(adoptedRootOperation!=transfer.operationId){DownloadCoordinator.adoptArtifacts(transfer.operationId,operation,OfflineModuleId.LINUX_RUNTIME);adoptedRootOperation=transfer.operationId}
                        if(transfer.phase==ModulePhase.DOWNLOADING)control.checkpoint()
                        progress(transfer.copy(operationId=operation));return@rootBytes
                    }
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(e.stage==com.appforge.studio.terminal.LinuxInstallStage.DOWNLOADING) {
                        val m=rootMeter?:DownloadMeasurement(OfflineModuleId.LINUX_RUNTIME,operation,now,e.completedBytes).also{rootMeter=it}
                        if(now-rootReported>=250 || e.totalBytes==e.completedBytes){
                            progress(m.sample(e.completedBytes,e.totalBytes?.takeIf{it>0},now).copy(detail=e.detail));rootReported=now
                        }
                    } else phase(OfflineModuleId.LINUX_RUNTIME,when(e.stage){
                        com.appforge.studio.terminal.LinuxInstallStage.VERIFYING->ModulePhase.VERIFYING
                        com.appforge.studio.terminal.LinuxInstallStage.EXTRACTING->ModulePhase.EXTRACTING
                        else->ModulePhase.INSTALLING},e.detail)
                }){}
                control.checkpoint()
                phase(OfflineModuleId.LINUX_RUNTIME,ModulePhase.READY,"Ortak ortam doğrulandı.")
                copyAssetTree(app,"device-build",File(workspace,"runtime"))
                val shell=LinuxShellEngine(app)
                for(id in linux.filter{it!=OfflineModuleId.LINUX_RUNTIME}) {
                    control.checkpoint()
                    active=id
                    if(moduleReady(app,id))continue
                    if(installedVersion(app,id)!=null) {
                        val permission=lifecycleStatus(app,id,insideMutation=true).update
                        require(permission.allowed){permission.reason?:"Güncelleme güvenle doğrulanamadı."}
                    }
                    require(AptSnapshotMigration.blockedReason(root)==null){AptSnapshotMigration.blockedReason(root)!!}
                    val protectedModules=witnesses.keys.filter{it!=id && moduleReady(app,it)}
                    val installJob=kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                    phase(id,ModulePhase.PREPARING,"Çalışan ortam korunuyor; izole sürüm kopyalanıyor ve doğrulanıyor.")
                    RuntimeGenerationStore(root).prepare(id,canceled={if(installJob?.isActive==false)throw kotlinx.coroutines.CancellationException("İptal edildi")}).use { generation ->
                    val staged=generation.root
                    DownloadInstrumentation.install(app,staged)
                    phase(id,ModulePhase.INSTALLING,"${OfflineModules.spec(id).title}: araç kurulumu; ölçülemeyen aşamada yüzde gösterilmez.")
                    if(id==OfflineModuleId.NODE_WEB) {
                        val arch=if(Build.SUPPORTED_ABIS.any{it=="arm64-v8a"})"arm64" else if(Build.SUPPORTED_ABIS.any{it=="x86_64"})"x64" else error("Bu platform için Node arşivi desteklenmiyor; paket eksikliği değil.")
                        val name="node-v22.23.3-linux-$arch.tar.xz"
                        val sha=if(arch=="arm64")"a44aeb94849a299b22df10b9e622ec2f605c2183501bc40590705131de7c740f" else "df450af89261115ef9f9e3830c3eeb2cc9213b63c720b1af623cb5dcbe2e02de"
                        val operationJob=kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                        VerifiedModuleDownload.download("https://nodejs.org/dist/v22.23.3/$name",sha,File(app.noBackupFilesDir,"module-downloads/node/$name"),id,operation,
                            canceled={operationJob?.isActive==false || control.request!=DownloadControl.Request.NONE},progress=progress)
                        val cache=File(staged,"opt/appforge-device/cache/$name")
                        cache.parentFile.mkdirs();require(cache.canonicalFile.toPath().startsWith(staged.canonicalFile.toPath()))
                        File(app.noBackupFilesDir,"module-downloads/node/$name").inputStream().use{input->cache.outputStream().use{input.copyTo(it)}}
                    }
                    if(id==OfflineModuleId.RUST) {
                        val original=File(workspace,"runtime/apt-snapshot-lock.sh").readText()
                        require(original.split("as_configure() {").size==2){"Rust APT uyarlama noktası değişti; güvenli biçimde duruldu."}
                        File(workspace,"runtime/rust-apt-base.sh").writeText(original.replace("as_configure() {","as_configure_original() {"))
                        val manifest=org.json.JSONObject(app.assets.open("device-build/rust-toolchain-v1.json").bufferedReader().use{it.readText()})
                        val host=if(Build.SUPPORTED_ABIS.contains("arm64-v8a"))"aarch64-unknown-linux-gnu" else "x86_64-unknown-linux-gnu"
                        val artifacts=manifest.getJSONArray("artifacts")
                        for(index in 0 until artifacts.length()) {
                            val artifact=artifacts.getJSONObject(index)
                            if(artifact.getString("host")!=host)continue
                            control.checkpoint()
                            val name=artifact.getString("file")
                            require(name.matches(Regex("[A-Za-z0-9_.-]+")))
                            val transferJob=kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                            val cached=RecoveryPathAnchor.androidPlatformPath(File(app.noBackupFilesDir,"module-downloads/rust/$name")).also{RecoveryPathAnchor.check(it)}
                            VerifiedModuleDownload.download(artifact.getString("url"),artifact.getString("sha256"),cached,id,operation,
                                canceled={transferJob?.isActive==false || control.request!=DownloadControl.Request.NONE},progress=progress)
                            val destination=File(staged,"opt/appforge-device/cache/$name")
                            destination.parentFile.mkdirs()
                            require(destination.canonicalFile.toPath().startsWith(staged.canonicalFile.toPath()))
                            cached.inputStream().use{input->destination.outputStream().use{input.copyTo(it)}}
                        }
                    }
                    val commands=when(id) {
                        OfflineModuleId.RUST -> listOf("install-rust-prerequisites.sh",if(File(staged,"opt/appforge-device/rust-1.85.1/bin/rustc").isFile)"install-rust-module.py --verify" else "install-rust-module.py")
                        OfflineModuleId.ANDROID_CORE -> listOf("install-toolchain.sh webview-static","prepare-offline-pack.sh android")
                        OfflineModuleId.NODE_WEB -> listOf("install-node-module.sh")
                        OfflineModuleId.PYTHON_ANDROID -> listOf("install-toolchain.sh python-android","prepare-offline-pack.sh python")
                        OfflineModuleId.EXPO_REACT_NATIVE -> listOf("install-toolchain.sh expo")
                        OfflineModuleId.WINDOWS_NATIVE -> listOf("install-windows-native-toolchain.sh")
                        else -> error("Bilinmeyen modül")
                    }
                    for(command in commands) {
                        DownloadNetwork.requireExternalConsent()
                        control.checkpoint()
                        execute(shell,staged,workspace,id.name.lowercase(),
                        "APPFORGE_MODULE_REFRESH=${if(OfflineModules.refreshExistingInstallation(id,installedVersion(app,id)!=null))1 else 0} APPFORGE_MODULE_ID=${id.name} APPFORGE_ANDROID_CORE_PREPARED=${if(id in setOf(OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.EXPO_REACT_NATIVE))1 else 0} APPFORGE_ANDROID_SDK_LICENSE_ACCEPTED="+(if(sdkConsent)"1" else "0")+" APPFORGE_DEVICE_OFFLINE=0 ${if(id==OfflineModuleId.RUST && command.startsWith("install-rust-module.py"))"/usr/bin/python3.12 -I -S" else "/bin/sh"} /workspace/runtime/$command",id,operation,progress,control)
                    }
                    control.checkpoint()
                    phase(id,ModulePhase.OFFLINE_TEST,"Gerçek dosya/sürüm ve çevrimdışı araç kontrolü yapılıyor.")
                    val probe=when(id) {
                        OfflineModuleId.RUST -> "/usr/bin/python3.12 -I -S /workspace/runtime/install-rust-module.py --verify"
                        OfflineModuleId.ANDROID_CORE -> "APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/install-toolchain.sh webview-static && APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/prepare-offline-pack.sh android"
                        OfflineModuleId.NODE_WEB -> "/opt/appforge-device/node-22.23.3/bin/node --version && /opt/appforge-device/node-22.23.3/bin/node /opt/appforge-device/node-22.23.3/lib/node_modules/npm/bin/npm-cli.js cache verify"
                        OfflineModuleId.PYTHON_ANDROID -> "/usr/bin/python3.12 -c 'import sys; assert sys.version_info[:2] == (3,12)' && APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/prepare-offline-pack.sh python"
                        OfflineModuleId.EXPO_REACT_NATIVE -> "APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/install-toolchain.sh expo"
                        OfflineModuleId.WINDOWS_NATIVE -> "APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/install-windows-native-toolchain.sh"
                        else -> error("Geçersiz modül")
                    }
                    execute(shell,staged,workspace,"verify-${id.name.lowercase()}",probe)
                    phase(id,ModulePhase.VERIFYING,"Dosya SHA-256 envanteri atomik kaydediliyor.")
                    val apt=AptModuleLocks.inspect(staged,id)
                    check(apt.transactions>0 || id !in setOf(OfflineModuleId.ANDROID_CORE,OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.WINDOWS_NATIVE,OfflineModuleId.RUST)){"Doğrulanmış APT snapshot kilidi eksik; eski kurulum hazır kabul edilmez."}
                    writeAttestation(app,staged,id)
                    ModuleOwnershipStore(staged).record(id,generation.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(staged).snapshot(),root)
                    for(existing in protectedModules) {
                        check(recordMatches(app,staged,existing)){"Kurulum çalışan modülün bütünlüğünü bozdu: ${existing.name}"}
                        execute(shell,staged,workspace,"protect-${existing.name.lowercase()}",runtimeProbe(existing))
                    }
                    control.checkpoint()
                    generation.commit(verify={ candidate -> check(recordMatches(app,candidate,id) && AndroidRuntimeTrust.ready(candidate)){"Yeni modül sürüm kaydı doğrulanamadı."} })
                    }
                    check(moduleReady(app,id)){"Etkinleştirilen modül doğrulanamadı."}
                    phase(id,ModulePhase.READY,"${OfflineModules.spec(id).title} doğrulandı. Projeye özel bağımlılıklar ayrıca kontrol edilir.")
                }
            }
            if(OfflineModuleId.FAST_HTML in pending) {
                TemplateIntegrity.requireBundled(app,setOf("html-apk-template.apk","html-aab-template.aab"))
                phase(OfflineModuleId.FAST_HTML,ModulePhase.READY,"Gömülü şablonlar doğrulandı; indirme yok.")
            }
        } catch(e:kotlinx.coroutines.CancellationException) {
            phase(active,if(e is DownloadNetwork.PolicyBlocked)ModulePhase.WAITING_CONNECTION else ModulePhase.CANCELED,if(e is DownloadNetwork.PolicyBlocked)"Ağ politikası bekleniyor; doğrulanmış modüller korunur." else "İptal edildi; mevcut doğrulanmış modüller korunur.");throw e
        } catch(e:Exception) {
            progress(ModuleProgress(active,operation,if(!hasValidatedInternet(app))ModulePhase.WAITING_CONNECTION else ModulePhase.ERROR,errorCode=e.javaClass.simpleName,detail=if(!hasValidatedInternet(app))"Bağlantı bekleniyor; kısmi indirme hazır sayılmaz. Bağlantı gelince Devam et seçin." else e.message?:"Kurulum başarısız."));throw e
        } finally { workspace.deleteRecursively() }
        }
    }

    private fun runtimeProbe(id:OfflineModuleId):String=when(id) {
        OfflineModuleId.RUST->"/opt/appforge-device/rust-1.85.1/bin/rustc --version && /opt/appforge-device/rust-1.85.1/bin/cargo --version"
        OfflineModuleId.ANDROID_CORE->"/opt/appforge-device/jdk-17/bin/java -version && /opt/appforge-device/android-sdk/build-tools/36.0.0/aapt2 version && JAVA_HOME=/opt/appforge-device/jdk-17 /opt/appforge-device/gradle-9.3.1/bin/gradle --offline --version"
        OfflineModuleId.NODE_WEB->"/opt/appforge-device/node-22.23.3/bin/node --version"
        OfflineModuleId.PYTHON_ANDROID->"/usr/bin/python3.12 --version"
        OfflineModuleId.EXPO_REACT_NATIVE->"/opt/appforge-device/android-sdk/cmake/3.22.1/bin/cmake --version && /opt/appforge-device/android-sdk/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin/clang --version"
        OfflineModuleId.WINDOWS_NATIVE->"/usr/bin/x86_64-w64-mingw32-gcc-posix --version && /usr/bin/cmake --version && /usr/bin/ninja --version"
        else->error("Bu modül paylaşılan native ortama ait değil")
    }

    private val witnesses=mapOf(
        OfflineModuleId.ANDROID_CORE to listOf("opt/appforge-device/android-sdk/platforms/android-37.0/android.jar","opt/appforge-device/android-sdk/build-tools/36.0.0/aapt2","opt/appforge-device/jdk-17/bin/java","opt/appforge-device/jdk-17/bin/javac","opt/appforge-device/gradle-9.3.1/lib/gradle-launcher-9.3.1.jar"),
        OfflineModuleId.RUST to listOf("usr/bin/python3.12","opt/appforge-device/rust-1.85.1/bin/rustc","opt/appforge-device/rust-1.85.1/bin/cargo","opt/appforge-device/rust-1.85.1/.offline-probe.json"),
        OfflineModuleId.NODE_WEB to listOf("opt/appforge-device/node-22.23.3/bin/node","opt/appforge-device/node-22.23.3/lib/node_modules/npm/bin/npm-cli.js"),
        OfflineModuleId.PYTHON_ANDROID to listOf("usr/bin/python3.12"),
        OfflineModuleId.EXPO_REACT_NATIVE to listOf("opt/appforge-device/expo-sdk-36/platforms/android-36/android.jar","opt/appforge-device/node-22.23.3/bin/node","opt/appforge-device/android-sdk/cmake/3.22.1/bin/cmake","opt/appforge-device/android-sdk/ndk/27.1.12297006/source.properties"),
        OfflineModuleId.WINDOWS_NATIVE to listOf("usr/bin/cmake","usr/bin/ninja","usr/bin/x86_64-w64-mingw32-gcc-posix","usr/bin/x86_64-w64-mingw32-g++-posix","usr/bin/x86_64-w64-mingw32-windres","opt/appforge-device/windows-native-v1/.ready")
    )
    private fun digest(file:File):String=java.security.MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;update(b,0,n)}}
        digest().joinToString(""){"%02x".format(it)}
    }
    private fun recipeDigest(context:Context,id:OfflineModuleId):String {
        val names=when(id) {
            OfflineModuleId.RUST->listOf("install-rust-module.py","install-rust-prerequisites.sh","rust-toolchain-v1.json")
            OfflineModuleId.NODE_WEB->listOf("install-node-module.sh")
            OfflineModuleId.WINDOWS_NATIVE->listOf("install-windows-native-toolchain.sh")
            else->listOf("install-toolchain.sh","prepare-offline-pack.sh","platform-37.0-package.xml")
        }+listOf("apt-acquire-observer.sh","apt-acquire-observer.awk","apt-acquire-emit.sh","apt-download-telemetry.sh","apt-snapshot-lock.sh","apt-snapshot-profile.tsv","apt-module-packages-v1.tsv","module-download-telemetry.sh","native-download-broker.sh","jvm-telemetry/network-byte-agent.jar","verified-toolchain-sources-v4.json")
        val hash=java.security.MessageDigest.getInstance("SHA-256")
        hash.update("ownership-probe-v8-apt-snapshot".toByteArray())
        for(name in names.sorted()){hash.update(name.toByteArray());context.assets.open("device-build/$name").use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;hash.update(b,0,n)}}}
        return hash.digest().joinToString(""){"%02x".format(it)}
    }
    fun installedVersion(context:Context,id:OfflineModuleId):String?=runCatching {
        val root=runtimeRootfs(context)?:return@runCatching null
        val f=receipt(root,id);require(f.isFile && f.length()<=16384 && !java.nio.file.Files.isSymbolicLink(f.toPath()))
        org.json.JSONObject(f.readText()).getString("version").take(200)
    }.getOrNull()
    fun updateAvailable(context:Context,id:OfflineModuleId):Boolean=runCatching {
        if(id !in witnesses.keys)return@runCatching false
        val root=runtimeRootfs(context)?:return@runCatching false
        val f=receipt(root,id);require(f.isFile && f.length()<=16384 && !java.nio.file.Files.isSymbolicLink(f.toPath()))
        val j=org.json.JSONObject(f.readText())
        j.optString("version")!=OfflineModules.spec(id).version || j.optString("recipe")!=recipeDigest(context,id)
    }.getOrDefault(false)
    private fun receipt(root:File,id:OfflineModuleId)=File(root,"opt/appforge-device/modules-v3/${id.name}.json")
    private fun writeAttestation(context:Context,root:File,id:OfflineModuleId) {
        val paths=witnesses.getValue(id)
        val files=org.json.JSONObject()
        paths.forEach{path->val file=File(root,path);require(file.isFile && file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())){"Modül dosyası eksik veya güvensiz: $path"};files.put(path,digest(file))}
        val target=receipt(root,id).apply{parentFile?.mkdirs()};val part=File(target.path+".part")
        part.writeText(org.json.JSONObject().put("revision",3).put("version",OfflineModules.spec(id).version).put("recipe",recipeDigest(context,id)).put("files",files).toString())
        java.nio.file.Files.move(part.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    fun aptSummary(context:Context,id:OfflineModuleId):AptModuleLocks.Summary? = runtimeRootfs(context)?.let{AptModuleLocks.inspect(it,id)}
    private fun recordMatches(context:Context,root:File,id:OfflineModuleId):Boolean = runCatching {
        val record=receipt(root,id);require(record.isFile && record.length()<=16384 && !java.nio.file.Files.isSymbolicLink(record.toPath()))
        val json=org.json.JSONObject(record.readText());val files=json.getJSONObject("files")
        json.getInt("revision")==3 && json.optString("recipe")==recipeDigest(context,id) && json.getString("version")==OfflineModules.spec(id).version && files.length()==witnesses.getValue(id).size &&
            witnesses.getValue(id).all{path->val f=File(root,path);f.isFile && f.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && digest(f)==files.optString(path)} && ModuleOwnershipStore(root).verifyNamespace(id) && AptModuleLocks.inspect(root,id).let{it.transactions>0 || id !in setOf(OfflineModuleId.ANDROID_CORE,OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.WINDOWS_NATIVE,OfflineModuleId.RUST)}
    }.getOrDefault(false)
    fun recoverPending(context:Context) {
        if(File(context.noBackupFilesDir,"offline-build-pack/.portable-lifecycle.json").exists())
            BuildModuleUsage.mutationLease().use{OwnedModuleFiles.recoverPortable(context.noBackupFilesDir)}
        val root=runtimeRootfs(context)?:return
        val removal=RuntimeArchiveRemoval(DeviceBuildRuntimeV3.runtimeBaseDirectory(context))
        if(File(DeviceBuildRuntimeV3.runtimeBaseDirectory(context).parentFile,".runtime-removal-v5.json").exists())BuildModuleUsage.mutationLease().use{removal.recover()}
        val journal=File(root.parentFile,".module-generation.json")
        if(journal.isFile || File(root.parentFile,".rootfs-activation.json").isFile)runCatching{BuildModuleUsage.mutationLease().use{RuntimeGenerationStore(root).recover();com.appforge.studio.terminal.RootfsActivationTransaction(root,File(root.parentFile,"restore-points"),File(root.parentFile,".appforge-rootfs.properties")).recover()}}.getOrElse {
            if(it is IllegalStateException && it.message?.contains("Etkin derleme")==true)return
            throw it
        }
    }
    internal fun protectedTools(context:Context):Map<String,String> {
        val root=runtimeRootfs(context)?:error("Runtime yok")
        val expected=witnesses.filterKeys{moduleReady(context,it)}.values.flatten().distinct().associateWith{digest(File(root,it))}.toMutableMap()
        val metadata=File(root,"opt/appforge-device/ownership-v5").listFiles().orEmpty().filter{it.isFile && it.extension=="json"}.toList()+listOf(File(root,BaseRuntimeOwnership.FILE),File(root,"root/.gradle-appforge/init.d/appforge-resource-bytes.gradle"))
        for(file in metadata.filter{it.isFile}){require(file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && !java.nio.file.Files.isSymbolicLink(file.toPath()));expected[file.relativeTo(root).invariantSeparatorsPath]=digest(file)}
        return expected
    }
    internal fun verifyProtectedTools(root:File,expected:Map<String,String>) {
        for(id in witnesses.keys.filter{witnesses.getValue(it).all(expected::containsKey)})require(ModuleOwnershipStore(root).verifyNamespace(id)){"Proje, kurulu modülün sahipli sistem dosyalarını değiştirdi: ${id.name}"}
        require(expected.all{(path,hash)->val f=File(root,path);f.isFile && f.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && digest(f)==hash}){"Proje derlemesi korunan araçları değiştirdi; sürüm etkinleştirilmedi."}
    }
    fun hasPendingLifecycle(context:Context,id:OfflineModuleId):Boolean = if(id==OfflineModuleId.FAST_HTML)false else if(id==OfflineModuleId.WINDOWS_PORTABLE)
        File(context.noBackupFilesDir,"offline-build-pack/.portable-lifecycle.json").exists()
    else runtimeRootfs(context)?.let{File(it.parentFile,".module-generation.json").exists() || File(it.parentFile,".rootfs-activation.json").exists()}==true
    fun lifecycleStatus(context:Context,id:OfflineModuleId,insideMutation:Boolean=false):ModuleLifecycleStatus {
        val root=runtimeRootfs(context)
        val native=id in witnesses.keys
        val version=installedVersion(context,id)?:if(!native && moduleReady(context,id))OfflineModules.spec(id).version else null
        val dependents=OfflineModules.all.filter{spec->spec.id!=id && id in OfflineModules.resolve(spec.dependencies) &&
            (installedVersion(context,spec.id)!=null || moduleReady(context,spec.id) || root?.let{r->witnesses[spec.id].orEmpty().any{File(r,it).exists()}}==true)
        }.map{it.id}.toSet()
        val inventory=if(native && root!=null)runCatching{ModuleOwnershipStore(root).lifecycleInventory(id)}.getOrNull() else null
        val ownershipError=if(native && (root==null || !ModuleOwnershipStore(root).verifyNamespace(id)))"Modülün dosya sahipliği/bütünlüğü doğrulanamadı." else null
        val removeError=when(id) {
            OfflineModuleId.FAST_HTML->"Gömülü modül kaldırılamaz."
            OfflineModuleId.WINDOWS_PORTABLE->OwnedModuleFiles.portableRemovalReason(context.noBackupFilesDir,WindowsPortableHostStore.HOST_SHA256)
            OfflineModuleId.LINUX_RUNTIME->if(DeviceBuildRuntimeV3.isReady(context))null else "Runtime doğrulanamadı."
            else->root?.let{ModuleOwnershipStore(it).safeRemovalReason(id)}?:"Runtime yok."
        }
        val rollbackError=when {
            !native->"Bu modül için doğrulanmış generation geri alma yolu yok."
            root==null->"Runtime yok."
            else->RuntimeGenerationStore(root).rollbackReason(id)
        }
        val minimum=if(native && root?.isDirectory==true)runCatching{RuntimeGenerationStore(root).minimumStagingBytes()}.getOrNull() else 1024L*1024
        val spaceError=if(native && minimum==null)"İzole sürüm alanı hesaplanamadı." else if(minimum!=null && context.noBackupFilesDir.usableSpace<=minimum)"İzole işlem için en az ${minimum/1024/1024} MiB boş alan gerekli." else null
        return ModuleLifecyclePolicy.evaluate(ModuleLifecyclePolicy.Facts(id,version!=null,updateAvailable(context,id),ownershipError,
            removeError,rollbackError,if(insideMutation)null else BuildModuleUsage.mutationBlockedReason(),
            if(!insideMutation && hasPendingLifecycle(context,id))"Kesilmiş yaşam döngüsü işlemi önce güvenle kurtarılmalı." else if(id in setOf(OfflineModuleId.FAST_HTML,OfflineModuleId.WINDOWS_PORTABLE))null else DeviceBuildRuntimeV3.platformError(),
            dependents,version,inventory?.generation,inventory?.previousVersion,inventory?.fileCount?:0,spaceError,minimum,if(native && root!=null)AptSnapshotMigration.blockedReason(root) else null))
    }
    fun canRollback(context:Context,id:OfflineModuleId):Boolean=lifecycleStatus(context,id).rollback.allowed
    suspend fun rollbackModule(context:Context,id:OfflineModuleId) = installMutex.withLock {
        BuildModuleUsage.mutationLease().use {
            require(lifecycleStatus(context,id,insideMutation=true).rollback.allowed){"Bağımlılık kullanımı, sürüm sırası veya snapshot bütünlüğü geri almaya izin vermiyor."}
            val root=runtimeRootfs(context)?:error("Runtime yok")
            val job=kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            RuntimeGenerationStore(root).rollback(id){if(job?.isActive==false)throw kotlinx.coroutines.CancellationException("İptal edildi")}
        }
    }
    fun removalReason(context:Context,id:OfflineModuleId):String?=lifecycleStatus(context,id).remove.reason
    suspend fun removeOwnedModule(context:Context,id:OfflineModuleId) = installMutex.withLock {
        BuildModuleUsage.mutationLease().use {
            val permission=lifecycleStatus(context,id,insideMutation=true).remove
            require(permission.allowed){permission.reason?:"Kaldırma engellendi."}
            if(id==OfflineModuleId.WINDOWS_PORTABLE){OwnedModuleFiles.removePortable(context.noBackupFilesDir,WindowsPortableHostStore.HOST_SHA256);return@withLock}
            if(id==OfflineModuleId.LINUX_RUNTIME){RuntimeArchiveRemoval(DeviceBuildRuntimeV3.runtimeBaseDirectory(context)).remove();return@withLock}
            val root=runtimeRootfs(context)?:error("Runtime yok")
            val protected=witnesses.filterKeys{it!=id && moduleReady(context,it)}.values.flatten().distinct().associateWith{digest(File(root,it))}
            val job=kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            RuntimeGenerationStore(root).prepareOwner("REMOVE:${id.name}",canceled={if(job?.isActive==false)throw kotlinx.coroutines.CancellationException()}).use{tx->
                ModuleOwnershipStore(root).restoreInto(id,tx.root)
                val attestation=receipt(tx.root,id)
                require(!java.nio.file.Files.isSymbolicLink(attestation.toPath()) && attestation.canonicalFile.toPath().startsWith(tx.root.canonicalFile.toPath()))
                java.nio.file.Files.deleteIfExists(attestation.toPath())
                val workspace=File(context.cacheDir,"lifecycle-remove/${UUID.randomUUID()}").apply{mkdirs()}
                try {
                    copyAssetTree(context,"device-build",File(workspace,"runtime"))
                    for(other in witnesses.keys.filter{it!=id && moduleReady(context,it)}) {
                        check(recordMatches(context,tx.root,other)){"Kaldırma başka modülü etkiledi: ${other.name}"}
                        execute(LinuxShellEngine(context),tx.root,workspace,"remove-protect-${other.name}",runtimeProbe(other))
                    }
                    tx.commit(verify={verifyProtectedTools(it,protected)})
                } finally {workspace.deleteRecursively()}
            }
        }
    }
    fun prunableSnapshotBytes(context:Context):Long {
        require(BuildModuleUsage.mutationBlockedReason()==null){"Etkin işlem varken temizleme hesabı yapılmaz."}
        val root=runtimeRootfs(context)?:error("Runtime yok.")
        return RuntimeGenerationStore(root).prunableBytes(ModuleOwnershipStore(root).protectedSnapshots())
    }
    suspend fun pruneUnusedSnapshots(context:Context):Long=installMutex.withLock {
        BuildModuleUsage.mutationLease().use {
            val root=runtimeRootfs(context)?:error("Runtime yok")
            val job=kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            RuntimeGenerationStore(root).pruneUnused(ModuleOwnershipStore(root).protectedSnapshots(),retain=1,canceled={if(job?.isActive==false)throw kotlinx.coroutines.CancellationException()})
        }
    }
    fun moduleReady(context:Context,id:OfflineModuleId):Boolean = runCatching {
        when(id) {
            OfflineModuleId.FAST_HTML -> {TemplateIntegrity.requireBundled(context,setOf("html-apk-template.apk","html-aab-template.aab"));true}
            OfflineModuleId.WINDOWS_PORTABLE -> {WindowsPortableHostStore.requireVerifiedHost(context);true}
            OfflineModuleId.LINUX_RUNTIME -> DeviceBuildRuntimeV3.isReady(context)
            else -> {
                val root=runtimeRootfs(context)?:return@runCatching false
                recordMatches(context,root,id) &&
                    OfflineModules.spec(id).dependencies.all{moduleReady(context,it)}
            }
        }
    }.getOrDefault(false)

    private suspend fun execute(
        shell: LinuxShellEngine,
        rootfs: File,
        workspace: File,
        suffix: String,
        command: String,
        module:OfflineModuleId?=null,
        operation:String="",
        progress:(ModuleProgress)->Unit={},
        control:DownloadControl=DownloadControl()
    ) = kotlinx.coroutines.coroutineScope {
        val sessionId =
            "offline-pack-$suffix-${UUID.randomUUID()}"

        val telemetry=File(workspace,"module-download.json")
        ExternalTelemetryChannels.clear(telemetry)
        File(workspace,"apt-phase.json").let{require(!java.nio.file.Files.isSymbolicLink(it.toPath()));it.delete()}
        val channels=module?.let{ExternalTelemetryChannels(telemetry,it,operation)}
        val broker=module?.let{ModuleArtifactBroker(workspace,File(rootfs.parentFile,".module-archive-cache-v5"),it,operation,progress,stopRequested={control.request!=DownloadControl.Request.NONE})}
        val brokerJob=broker?.let{launch(kotlinx.coroutines.Dispatchers.IO){it.serve()}}
        val poller=launch(kotlinx.coroutines.Dispatchers.IO) {
            while(kotlinx.coroutines.currentCoroutineContext().isActive) {
                val consent=module?.let{AptDownloadConsent.poll(workspace,it,operation)}
                val aptPhase=module?.let{AptPhaseProgress.poll(workspace,it,operation)}
                if(consent!=null) {
                    if(control.request!=DownloadControl.Request.NONE)AptDownloadConsent.reject(operation)
                    progress(consent)
                }
                else if(aptPhase!=null)progress(aptPhase)
                else if(broker?.transferring!=true)channels?.poll(android.os.SystemClock.elapsedRealtime())?.forEach(progress)
                kotlinx.coroutines.delay(500)
            }
        }
        val result = try {
            shell.execute(
                sessionId = sessionId,
                rootfs = rootfs,
                workspace = workspace,
                command = AndroidRuntimeTrust.shellEnvironment + "export APPFORGE_APT_MANAGED_INSTALL=${if(module!=null)1 else 0}; export APPFORGE_APT_REQUIRE_CONSENT=${if(module!=null)1 else 0}; export APPFORGE_NATIVE_DOWNLOAD_BROKER=${if(broker!=null)"/workspace/native-downloads" else ""}; export PYTHONPATH=/workspace/runtime/python-telemetry:\${PYTHONPATH:-}; export PATH=/opt/appforge-device/telemetry-bin:\$PATH; export APPFORGE_DOWNLOAD_TELEMETRY=/workspace/module-download.json; "+command,
                confirmed = true,
                timeoutMs = 1_800_000L
            )
        } finally {poller.cancel();brokerJob?.cancel();AptDownloadConsent.clear(operation)}
        control.checkpoint()
        if(broker?.networkBlocked==true)throw DownloadNetwork.PolicyBlocked()

        check(
            !result.timedOut
        ) {
            "Çevrimdışı paket kurulumu zaman aşımına uğradı: $suffix"
        }

        check(
            result.exitCode ==
                0
        ) {
            result.output
                .takeLast(
                    4_000
                )
                .ifBlank {
                    "Çevrimdışı paket bileşeni kurulamadı: $suffix"
                }
        }
    }

    private fun writeMarker(
        file: File
    ) {
        file.parentFile?.mkdirs()

        file.writeText(
            "$REVISION\n",
            Charsets.UTF_8
        )
    }

    private fun markerReady(
        file: File
    ): Boolean =
        file.isFile &&
            file.length() <=
                128L &&
            runCatching {
                file.readText(
                    Charsets.UTF_8
                ).trim()
            }.getOrNull() ==
                REVISION

    internal fun runtimeRootfs(
        context: Context
    ): File? {

        val architecture =
            LinuxArchitecture
                .fromAndroidAbis(
                    Build.SUPPORTED_ABIS.toList()
                )
                ?: return null

        return LinuxRuntimeLayout(
            baseDirectory =
                DeviceBuildRuntimeV3
                    .runtimeBaseDirectory(
                        context
                    ),
            distribution =
                LinuxDistribution.UBUNTU,
            architecture =
                architecture
        ).rootfsDirectory
    }

    internal fun hasValidatedInternet(
        context: Context
    ): Boolean {

        val manager =
            context.getSystemService(
                ConnectivityManager::class.java
            )
                ?: return false

        val network =
            manager.activeNetwork
                ?: return false

        val capabilities =
            manager.getNetworkCapabilities(
                network
            )
                ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) &&
            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
    }

    private fun copyAssetTree(
        context: Context,
        assetPath: String,
        target: File
    ) {
        val children =
            context.assets
                .list(
                    assetPath
                )
                .orEmpty()

        if (
            children.isEmpty()
        ) {
            target.parentFile?.mkdirs()

            context.assets
                .open(
                    assetPath
                )
                .use {
                    input ->

                    target.outputStream()
                        .use {
                            output ->

                            input.copyTo(
                                output
                            )
                        }
                }

            return
        }

        target.mkdirs()

        children.forEach {
            child ->

            copyAssetTree(
                context,
                "$assetPath/$child",
                File(
                    target,
                    child
                )
            )
        }
    }
}
