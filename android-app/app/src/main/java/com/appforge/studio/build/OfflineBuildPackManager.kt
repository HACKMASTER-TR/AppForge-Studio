package com.appforge.studio.build

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

    suspend fun installModules(context:Context, selected:Set<OfflineModuleId>, sdkConsent:Boolean,
        progress:(ModuleProgress)->Unit = {}) = installMutex.withLock {
        require(selected.isNotEmpty()){ "Önce kurulacak modülü seçin." }
        val app=context.applicationContext
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
        fun phase(id:OfflineModuleId,p:ModulePhase,detail:String)=progress(ModuleProgress(id,operation,p,detail=detail))
        try {
            phase(active,ModulePhase.RESOLVING,"Yalnız gerekli modüller: "+pending.joinToString{OfflineModules.spec(it).title})
            if(OfflineModuleId.WINDOWS_PORTABLE in pending) {
                active=OfflineModuleId.WINDOWS_PORTABLE
                phase(active,ModulePhase.CONNECTING,"Windows Portable: Android SDK lisansı veya Linux kurulumu gerekmez.")
                WindowsPortableHostStore.install(app, onProgress={message->phase(OfflineModuleId.WINDOWS_PORTABLE,ModulePhase.INSTALLING,message)}, onBytes={bytes->progress(bytes.copy(operationId=operation))})
                check(moduleReady(app,active)){"Windows host doğrulaması başarısız."}
                phase(active,ModulePhase.READY,"SHA-256 doğrulandı; Windows Portable hazır.")
            }
            val linux=pending.filter{it !in setOf(OfflineModuleId.WINDOWS_PORTABLE,OfflineModuleId.FAST_HTML)}
            if(linux.isNotEmpty()) {
                phase(OfflineModuleId.LINUX_RUNTIME,ModulePhase.INSTALLING,"Ortak Linux ortamı hazırlanıyor; toplam boyut henüz bilinmiyor.")
                val root=DeviceBuildRuntimeV3.ensureReady(app){phase(OfflineModuleId.LINUX_RUNTIME,ModulePhase.INSTALLING,it)}
                phase(OfflineModuleId.LINUX_RUNTIME,ModulePhase.READY,"Ortak ortam doğrulandı.")
                copyAssetTree(app,"device-build",File(workspace,"runtime"))
                val shell=LinuxShellEngine(app)
                for(id in linux.filter{it!=OfflineModuleId.LINUX_RUNTIME}) {
                    active=id
                    if(moduleReady(app,id))continue
                    phase(id,ModulePhase.INSTALLING,"${OfflineModules.spec(id).title}: araç kurulumu; ölçülemeyen aşamada yüzde gösterilmez.")
                    if(id==OfflineModuleId.NODE_WEB) {
                        val arch=if(Build.SUPPORTED_ABIS.any{it=="arm64-v8a"})"arm64" else if(Build.SUPPORTED_ABIS.any{it=="x86_64"})"x64" else error("Bu platform için Node arşivi desteklenmiyor; paket eksikliği değil.")
                        val name="node-v22.23.3-linux-$arch.tar.xz"
                        val sha=if(arch=="arm64")"a44aeb94849a299b22df10b9e622ec2f605c2183501bc40590705131de7c740f" else "df450af89261115ef9f9e3830c3eeb2cc9213b63c720b1af623cb5dcbe2e02de"
                        val operationJob=kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                        VerifiedModuleDownload.download("https://nodejs.org/dist/v22.23.3/$name",sha,File(root,"opt/appforge-device/cache/$name"),id,operation,
                            canceled={operationJob?.isActive==false},progress=progress)
                    }
                    val commands=when(id) {
                        OfflineModuleId.ANDROID_CORE -> listOf("install-toolchain.sh webview-static","prepare-offline-pack.sh android")
                        OfflineModuleId.NODE_WEB -> listOf("install-node-module.sh")
                        OfflineModuleId.PYTHON_ANDROID -> listOf("install-toolchain.sh python-android","prepare-offline-pack.sh python")
                        OfflineModuleId.EXPO_REACT_NATIVE -> listOf("install-toolchain.sh expo")
                        OfflineModuleId.WINDOWS_NATIVE -> listOf("install-windows-native-toolchain.sh")
                        else -> error("Bilinmeyen modül")
                    }
                    for(command in commands)execute(shell,root,workspace,id.name.lowercase(),
                        "APPFORGE_ANDROID_SDK_LICENSE_ACCEPTED="+(if(sdkConsent)"1" else "0")+" APPFORGE_DEVICE_OFFLINE=0 /bin/sh /workspace/runtime/$command")
                    phase(id,ModulePhase.OFFLINE_TEST,"Gerçek dosya/sürüm ve çevrimdışı araç kontrolü yapılıyor.")
                    val probe=when(id) {
                        OfflineModuleId.ANDROID_CORE -> "APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/install-toolchain.sh webview-static && APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/prepare-offline-pack.sh android"
                        OfflineModuleId.NODE_WEB -> "/opt/appforge-device/node-22.23.3/bin/node --version && /opt/appforge-device/node-22.23.3/bin/node /opt/appforge-device/node-22.23.3/lib/node_modules/npm/bin/npm-cli.js cache verify"
                        OfflineModuleId.PYTHON_ANDROID -> "/usr/bin/python3.12 -c 'import sys; assert sys.version_info[:2] == (3,12)' && APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/prepare-offline-pack.sh python"
                        OfflineModuleId.EXPO_REACT_NATIVE -> "APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/install-toolchain.sh expo"
                        OfflineModuleId.WINDOWS_NATIVE -> "APPFORGE_DEVICE_OFFLINE=1 /bin/sh /workspace/runtime/install-windows-native-toolchain.sh"
                        else -> error("Geçersiz modül")
                    }
                    execute(shell,root,workspace,"verify-${id.name.lowercase()}",probe)
                    phase(id,ModulePhase.VERIFYING,"Dosya SHA-256 envanteri atomik kaydediliyor.")
                    writeAttestation(root,id)
                    check(moduleReady(app,id)){"Kurulum bütünlüğü doğrulanamadı."}
                    phase(id,ModulePhase.READY,"${OfflineModules.spec(id).title} doğrulandı. Projeye özel bağımlılıklar ayrıca kontrol edilir.")
                }
            }
            if(OfflineModuleId.FAST_HTML in pending) {
                TemplateIntegrity.requireBundled(app,setOf("html-apk-template.apk","html-aab-template.aab"))
                phase(OfflineModuleId.FAST_HTML,ModulePhase.READY,"Gömülü şablonlar doğrulandı; indirme yok.")
            }
        } catch(e:kotlinx.coroutines.CancellationException) {
            phase(active,ModulePhase.CANCELED,"İptal edildi; mevcut doğrulanmış modüller korunur.");throw e
        } catch(e:Exception) {
            progress(ModuleProgress(active,operation,if(!hasValidatedInternet(app))ModulePhase.WAITING_CONNECTION else ModulePhase.ERROR,errorCode=e.javaClass.simpleName,detail=if(!hasValidatedInternet(app))"Bağlantı bekleniyor; kısmi indirme hazır sayılmaz. Bağlantı gelince Devam et seçin." else e.message?:"Kurulum başarısız."));throw e
        } finally { workspace.deleteRecursively() }
    }

    private val witnesses=mapOf(
        OfflineModuleId.ANDROID_CORE to listOf("opt/appforge-device/android-sdk/platforms/android-37.0/android.jar","opt/appforge-device/android-sdk/build-tools/36.0.0/aapt2","opt/appforge-device/jdk-17/bin/java","opt/appforge-device/jdk-17/bin/javac","opt/appforge-device/gradle-9.3.1/lib/gradle-launcher-9.3.1.jar"),
        OfflineModuleId.NODE_WEB to listOf("opt/appforge-device/node-22.23.3/bin/node","opt/appforge-device/node-22.23.3/lib/node_modules/npm/bin/npm-cli.js"),
        OfflineModuleId.PYTHON_ANDROID to listOf("usr/bin/python3.12"),
        OfflineModuleId.EXPO_REACT_NATIVE to listOf("opt/appforge-device/expo-sdk-36/platforms/android-36/android.jar","opt/appforge-device/node-22.23.3/bin/node","opt/appforge-device/android-sdk/cmake/3.22.1/bin/cmake","opt/appforge-device/android-sdk/ndk/27.1.12297006/source.properties"),
        OfflineModuleId.WINDOWS_NATIVE to listOf("usr/bin/cmake","usr/bin/ninja","usr/bin/x86_64-w64-mingw32-gcc-posix","usr/bin/x86_64-w64-mingw32-g++-posix","usr/bin/x86_64-w64-mingw32-windres","opt/appforge-device/windows-native-v1/.ready")
    )
    private fun digest(file:File):String=java.security.MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;update(b,0,n)}}
        digest().joinToString(""){"%02x".format(it)}
    }
    private fun receipt(root:File,id:OfflineModuleId)=File(root,"opt/appforge-device/modules-v3/${id.name}.json")
    private fun writeAttestation(root:File,id:OfflineModuleId) {
        val paths=witnesses.getValue(id)
        val files=org.json.JSONObject()
        paths.forEach{path->val file=File(root,path);require(file.isFile && file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())){"Modül dosyası eksik veya güvensiz: $path"};files.put(path,digest(file))}
        val target=receipt(root,id).apply{parentFile?.mkdirs()};val part=File(target.path+".part")
        part.writeText(org.json.JSONObject().put("revision",3).put("version",OfflineModules.spec(id).version).put("files",files).toString())
        java.nio.file.Files.move(part.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    fun moduleReady(context:Context,id:OfflineModuleId):Boolean = runCatching {
        when(id) {
            OfflineModuleId.FAST_HTML -> {TemplateIntegrity.requireBundled(context,setOf("html-apk-template.apk","html-aab-template.aab"));true}
            OfflineModuleId.WINDOWS_PORTABLE -> WindowsPortableHostStore.isInstalled(context)
            OfflineModuleId.LINUX_RUNTIME -> DeviceBuildRuntimeV3.isReady(context)
            else -> {
                val root=runtimeRootfs(context)?:return@runCatching false
                val record=receipt(root,id);if(!record.isFile || record.length()>16384)return@runCatching false
                val json=org.json.JSONObject(record.readText());val files=json.getJSONObject("files")
                json.getInt("revision")==3 && json.getString("version")==OfflineModules.spec(id).version && files.length()==witnesses.getValue(id).size &&
                    witnesses.getValue(id).all{path->val f=File(root,path);f.isFile && f.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && digest(f)==files.optString(path)} &&
                    OfflineModules.spec(id).dependencies.all{moduleReady(context,it)}
            }
        }
    }.getOrDefault(false)

    private suspend fun execute(
        shell: LinuxShellEngine,
        rootfs: File,
        workspace: File,
        suffix: String,
        command: String
    ) {
        val sessionId =
            "offline-pack-$suffix-${UUID.randomUUID()}"

        val result =
            shell.execute(
                sessionId = sessionId,
                rootfs = rootfs,
                workspace = workspace,
                command = command,
                confirmed = true,
                timeoutMs = 1_800_000L
            )

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

    private fun runtimeRootfs(
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

    private fun hasValidatedInternet(
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
