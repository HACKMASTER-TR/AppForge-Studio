package com.appforge.studio.build

import android.content.Context
import android.net.*
import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.ModuleDownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit source-execution consent and actual network-disconnected re-build are separate gates. */
internal object ProjectDependencyPreparation {
    val transfer=MutableStateFlow<ModuleProgress?>(null)
    val busy=MutableStateFlow(false)
    val status=MutableStateFlow("Proje bağımlılıkları henüz doğrulanmadı.")
    val rustProjectFolder=MutableStateFlow<String?>(null)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    private var control=DownloadControl()
    val stopPending=MutableStateFlow(false)
    private fun caches(context:Context,override:File?=null):List<File> {
        val root=override?:OfflineBuildPackManager.runtimeRootfs(context)?:error("Normal motor runtime yok")
        return listOf(File(root,"root/.gradle-appforge/caches"),File(root,"opt/appforge-device/npm-cache-v1"),File(root,"opt/appforge-device/npm-cache-expo54-v1"),File(root,"root/.cache/pip"))
    }
    private fun receipt(context:Context,plan:ProjectDependencyPlan)=File(context.noBackupFilesDir,"project-dependency-v4/${plan.fingerprint}.json")
    fun ready(context:Context,plan:ProjectDependencyPlan)=ProjectDependencyEvidence.ready(receipt(context,plan),plan,caches(context))
    @Synchronized fun start(context:Context,draft:ProjectDraft,sourceExecutionConsent:Boolean,offlineVerification:Boolean,rustOnly:Boolean=false) {
        check(!busy.value){"Başka proje hazırlığı devam ediyor; yeni işlem/ağ onayı alınmadı."}
        require(sourceExecutionConsent){"Proje build/lifecycle kodunu çalıştırma onayı gerekir."}
        if(RustProjectPolicy.sourceRoot(draft)!=null && (rustOnly || draft.sourceBuildEngine !in setOf("node-web","android-gradle"))){startRust(context,draft.copy(),offlineVerification);return}
        rustProjectFolder.value=null
        DownloadNetwork.configure(context)
        DownloadNetwork.prepareJournal()
        if(!offlineVerification)DownloadNetwork.requireExternalConsent()
        control=DownloadControl();stopPending.value=false
        transfer.value=null
        busy.value=true
        try{ModuleDownloadService.start(context.applicationContext)}catch(e:RuntimeException){busy.value=false;status.value="Android veri aktarım servisini başlatmadı; işlem başlamadı.";return}
        job=scope.launch {
            val preparationJob=currentCoroutineContext()[Job]!!
            val graphOperation="project-"+java.util.UUID.randomUUID().toString()
            fun phase(p:ModulePhase){DownloadCoordinator.modulePhase(ModuleProgress(OfflineModuleId.ANDROID_CORE,graphOperation,p))}
            var permit:BuildModuleUsage.PreparationPermit?=null
            var buildId:String?=null
            var generation:RuntimeGenerationStore.Prepared?=null
            var reviewedSource:File?=null
            var callback:ConnectivityManager.NetworkCallback?=null
            val connectivity=context.getSystemService(ConnectivityManager::class.java)
            val networkSeen=AtomicBoolean(false)
            try {
                phase(ModulePhase.RESOLVING)
                val plan=ProjectDependencyEvidence.analyze(draft)
                require(plan.blockers.isEmpty()){plan.blockers.joinToString("\n")}
                require(!FastHtmlApkPolicy.isCandidate(draft)){"Fast HTML ek proje bağımlılığı gerektirmez."}
                val requirements=OfflineBuildPackManager.checkBuildRequirements(context,draft)
                require(requirements.ready){requirements.missing.joinToString("\n")}
                if(offlineVerification) {
                    require(connectivity!=null && connectivity.allNetworks.isEmpty()){"İnternetsiz kabul için ağları kapatın; yalnız --offline bayrağı yeterli değildir."}
                    callback=object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network){networkSeen.set(true);buildId?.let{DeviceBuildEngine.cancel(it)}}}
                    connectivity.registerNetworkCallback(NetworkRequest.Builder().build(),callback!!)
                    require(connectivity.allNetworks.isEmpty()){"Ağ yeniden açıldı; doğrulama başlamadı."}
                }
                permit=BuildModuleUsage.preparationLease()
                reviewedSource=File(context.cacheDir,"dependency-review/${java.util.UUID.randomUUID()}/source")
                ProjectDependencyEvidence.copyReviewedSource(draft,reviewedSource!!)
                require(ProjectDependencyEvidence.analyze(draft.copy(importedFolder=reviewedSource!!.path)).fingerprint==plan.fingerprint)
                val activeRoot=DeviceBuildRuntimeV3.ensureReady(context)
                val protected=OfflineBuildPackManager.protectedTools(context)
                generation=RuntimeGenerationStore(activeRoot).prepareOwner("PROJECT_PREPARATION",canceled={if(!preparationJob.isActive)throw CancellationException("İptal edildi")})
                status.value=if(offlineVerification)"Gerçek bağlantısız build çalışıyor; kaynak ve cache yeniden doğrulanacak." else "Onaylı proje build/lifecycle kodu çalışıyor; bağımlılıklar hazırlanıyor. Boyut bilinmiyor."
                val runDraft=draft.copy(importedFolder=reviewedSource!!.path,allowDependencyDownloads=!offlineVerification,signingMode=SigningMode.DEBUG,keystoreUri=null,storePassword="",keyPassword="",keyAlias="")
                phase(if(offlineVerification)ModulePhase.OFFLINE_TEST else ModulePhase.PREPARING)
                buildId=DeviceBuildEngine.start(context,runDraft,null,permit,generation!!.root).id
                var channels:ExternalTelemetryChannels?=null
                var lastTelemetryAt=android.os.SystemClock.elapsedRealtime()
                while(DeviceBuildEngine.isExecuting(buildId!!)){
                    DeviceBuildEngine.transferFile(buildId!!)?.let{file->if(channels==null)channels=ExternalTelemetryChannels(file,OfflineModuleId.ANDROID_CORE,graphOperation);channels!!.poll(android.os.SystemClock.elapsedRealtime()).forEach{lastTelemetryAt=android.os.SystemClock.elapsedRealtime();transfer.value=it}}
                    transfer.value?.let{transfer.value=DownloadCoordinator.withoutStaleRates(it,android.os.SystemClock.elapsedRealtime()-lastTelemetryAt)}
currentCoroutineContext().ensureActive();if(offlineVerification && connectivity!!.allNetworks.isNotEmpty()){networkSeen.set(true);DeviceBuildEngine.cancel(buildId!!)};delay(500)}
                control.checkpoint()
                val result=DeviceBuildEngine.snapshot(buildId!!)?:error("Build sonucu yok")
                require(result.status=="success"){"Proje derlemesi başarısız: "+result.logs.takeLast(8).joinToString("\n").take(2000)}
                require(!offlineVerification || !networkSeen.get()){"Doğrulama sırasında ağ açıldı; offline hazır kaydı yazılmadı."}
                phase(ModulePhase.VERIFYING)
                val after=ProjectDependencyEvidence.analyze(draft);require(after.fingerprint==plan.fingerprint){"Hazırlama sırasında proje değişti; sonuç geçersiz."}
                OfflineBuildPackManager.verifyProtectedTools(generation!!.root,protected)
                require(AndroidRuntimeTrust.ready(generation!!.root)){"Proje TLS veya depo yapılandırmasını değiştirdi; çevrimdışı hazır kaydı yazılmadı."}
                val candidate=File(receipt(context,plan).path+".candidate")
                ProjectDependencyEvidence.record(candidate,plan,caches(context,generation!!.root),offlineVerification)
                if(offlineVerification)check(ProjectDependencyEvidence.ready(candidate,plan,caches(context,generation!!.root)))
                control.checkpoint()
                generation!!.commit(verify={OfflineBuildPackManager.verifyProtectedTools(it,protected);require(AndroidRuntimeTrust.ready(it))})
                require(!offlineVerification || !networkSeen.get()){"Ağ doğrulama bitmeden açıldı; hazır kaydı yayımlanmadı."}
                java.nio.file.Files.move(candidate.toPath(),receipt(context,plan).toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                if(offlineVerification)check(ready(context,plan)){"Cache bütünlüğü doğrulanamadı."}
                // Online preparation never claims the offline-probe/READY stage.
                phase(if(offlineVerification)ModulePhase.READY else ModulePhase.PREPARING)
                status.value=if(offlineVerification)"Proje çevrimdışı build ile doğrulandı; kaynak/cache değişirse hazır kaydı geçersizleşir." else "Online hazırlık bitti; çevrimdışı hazır değil. Ağları kapatıp açık onayla doğrulayın."
            }catch(e:CancellationException){phase(ModulePhase.PAUSED);status.value="Proje hazırlığı iptal edildi; cache hazır kabul edilmez.";throw e}
            catch(e:Exception){phase(ModulePhase.ERROR);status.value="Hazırlama engellendi: ${e.message}"}
            finally {
                callback?.let{runCatching{connectivity?.unregisterNetworkCallback(it)}}
                withContext(NonCancellable){
                    buildId?.let{id->if(DeviceBuildEngine.isExecuting(id))DeviceBuildEngine.cancel(id);while(DeviceBuildEngine.isExecuting(id))delay(250)}
                    try{generation?.close()}finally{
                        reviewedSource?.let{root->runCatching {java.nio.file.Files.walkFileTree(root.toPath(),object:java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                            override fun visitFile(file:java.nio.file.Path,attrs:java.nio.file.attribute.BasicFileAttributes):java.nio.file.FileVisitResult {java.nio.file.Files.delete(file);return java.nio.file.FileVisitResult.CONTINUE}
                            override fun postVisitDirectory(dir:java.nio.file.Path,error:java.io.IOException?):java.nio.file.FileVisitResult {if(error!=null)throw error;java.nio.file.Files.delete(dir);return java.nio.file.FileVisitResult.CONTINUE}
                        })}}
                        permit?.close();busy.value=false
                    }
                }
            }
        }
    }
    /** Shares this controller, FGS, cancellation and usage lease. No parallel installer.
     * All Android networks must remain absent: Cargo --offline alone cannot constrain build.rs. */
    private fun startRust(context:Context,draft:ProjectDraft,offlineVerification:Boolean) {
        require(draft.sourceBuildEngine in setOf("webview-static","node-web","android-gradle")){"Bu motor için Rust hazırlık adaptörü yok"}
        val native=draft.sourceBuildEngine=="android-gradle"
        require(draft.sourceBuildEngine in setOf("webview-static","node-web")){"Bu motorun Rust ABI adaptörü doğrulanmadı."}
        val connectivity=context.getSystemService(ConnectivityManager::class.java)?:error("Ağ durumu doğrulanamadı")
        if(offlineVerification)require(connectivity.allNetworks.isEmpty()){"Rust build.rs ağ erişimi için sandbox yok; bütün ağları kapatın."}
        DownloadNetwork.configure(context);DownloadNetwork.prepareJournal()
        rustProjectFolder.value=draft.importedFolder
        control=DownloadControl();stopPending.value=false;transfer.value=null;busy.value=true
        try{ModuleDownloadService.start(context.applicationContext)}catch(e:RuntimeException){busy.value=false;status.value="Android Rust hazırlık hizmetini başlatmadı.";return}
        val networkContext=DownloadNetwork.takeOperationConsent()
        job=scope.launch(networkContext) {
            val operation="rust-project-"+java.util.UUID.randomUUID().toString()
            val shell=com.appforge.studio.terminal.LinuxShellEngine(context.applicationContext)
            val networkSeen=AtomicBoolean(false)
            val callback=object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network){networkSeen.set(true);shell.cancel(operation)}}
            var registered=false
            var permit:BuildModuleUsage.PreparationPermit?=null
            var stage:RuntimeGenerationStore.Prepared?=null
            fun phase(value:ModulePhase,message:String) {
                status.value=message;val event=ModuleProgress(OfflineModuleId.RUST,operation,value,detail=message)
                transfer.value=event;DownloadCoordinator.modulePhase(event)
            }
            try {
                OfflineBuildPackManager.recoverPending(context)
                permit=BuildModuleUsage.preparationLease()
                require(!OfflineBuildPackManager.hasPendingLifecycle(context,OfflineModuleId.RUST) && OfflineBuildPackManager.moduleReady(context,OfflineModuleId.RUST)){"Rust modülü/kesilmiş generation yeniden doğrulanamadı."}
                if(offlineVerification){connectivity.registerNetworkCallback(NetworkRequest.Builder().build(),callback);registered=true
                require(connectivity.allNetworks.isEmpty() && !networkSeen.get()){"Ağ açıldı; Rust hazırlığı başlamadı."}}
                val plan=ProjectDependencyEvidence.analyze(draft)
                val storeRoot=RecoveryPathAnchor.androidPlatformPath(File(context.filesDir,"rust-prepared-v1")).apply{mkdirs()}
                val workspace=File(storeRoot,java.util.UUID.randomUUID().toString()).apply{mkdir()}
                val source=File(workspace,"source")
                phase(ModulePhase.PREPARING,"Rust kaynağı ayrı özel kopyaya alınıyor; özgün kaynak korunur.")
                ProjectDependencyEvidence.copyReviewedSource(draft,source)
                RustPreparedProjects(storeRoot).clearCopiedExport(workspace)
                val runtime=File(workspace,"runtime").apply{mkdir()}
                val helpers=listOf("rust-project.py","rust-vendor.py","rust-android.py","rust-android-target-v1.json","install-rust-module.py")
                for(name in helpers){context.assets.open("device-build/$name").use{input->File(runtime,name).outputStream().use{input.copyTo(it)}}}
                val root=OfflineBuildPackManager.runtimeRootfs(context)?:error("Rust runtime yok")
                val protected=OfflineBuildPackManager.protectedTools(context)
                val currentJob=currentCoroutineContext()[Job]!!
                stage=RuntimeGenerationStore(root).prepareOwner("RUST_PROJECT_PREPARATION",canceled={if(!currentJob.isActive)throw CancellationException("İptal edildi")})
                suspend fun trustedTool(command:String) {
                    control.checkpoint()
                    if(offlineVerification)require(!networkSeen.get() && connectivity.allNetworks.isEmpty()){"Ağ açıldı; Rust hazırlığı durdu."}
                    val result=shell.execute(operation,stage!!.root,workspace,AndroidRuntimeTrust.shellEnvironment+command,confirmed=true,timeoutMs=1_800_000L)
                    require(!result.timedOut && result.exitCode==0){"Rust bağımlılık verisi/cache doğrulanamadı (exit ${result.exitCode}). "+com.appforge.studio.terminal.TerminalSecretMasker.redact(result.output.takeLast(1500))}
                }
                if(native)trustedTool("/usr/bin/python3.12 -I -S /workspace/runtime/rust-android.py /workspace/source --check-project")
                phase(ModulePhase.RESOLVING,"Cargo.lock kaynakları çözülüyor; proje kodu çalıştırılmaz.")
                trustedTool("/usr/bin/python3.12 -I -S /workspace/runtime/rust-vendor.py plan /workspace/source --output /workspace/crate-plan.json")
                val planFile=File(workspace,"crate-plan.json")
                require(planFile.isFile && !java.nio.file.Files.isSymbolicLink(planFile.toPath()) && planFile.length() in 1..2L*1024*1024)
                val cratePlan=org.json.JSONObject(planFile.readText());require(cratePlan.getInt("schema")==1 && cratePlan.getString("lockSha256")==RustProjectPolicy.digest(File(source,"Cargo.lock")))
                val crates=cratePlan.getJSONArray("artifacts");require(crates.length()<=2048)
                val cache=RecoveryPathAnchor.androidPlatformPath(File(context.noBackupFilesDir,"rust-crates-v1")).apply{mkdirs()}
                RecoveryPathAnchor.check(cache)
                val stagedCache=File(workspace,"crate-cache").apply{mkdir()}
                for(index in 0 until crates.length()) {
                    control.checkpoint();currentCoroutineContext().ensureActive()
                    val item=crates.getJSONObject(index);val name=item.getString("name");val version=item.getString("version");val sha=item.getString("sha256")
                    require(name.matches(Regex("[A-Za-z0-9_-]{1,64}")) && version.length<=100 && version.matches(Regex("""[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?""")) && sha.matches(Regex("[a-f0-9]{64}")))
                    val url="https://static.crates.io/crates/$name/$name-$version.crate";require(item.getString("url")==url)
                    val archive=File(cache,"$sha.crate")
                    if(!offlineVerification)VerifiedModuleDownload.download(url,sha,archive,OfflineModuleId.RUST,operation,
                        canceled={!currentJob.isActive || control.request!=DownloadControl.Request.NONE},progress={transfer.value=it.copy(detail="Cargo $name $version • "+it.detail)},maxBytes=64L*1024*1024)
                    require(archive.isFile && !java.nio.file.Files.isSymbolicLink(archive.toPath()) && archive.length()<=64L*1024*1024 && VerifiedModuleDownload.sha(archive)==sha){"Cargo cache eksik/bozuk: $name $version. Önce kilitli bağımlılıkları ayrı onayla indirin."}
                    require(workspace.usableSpace>archive.length()+64L*1024*1024){"Rust cache kopyası için alan yetersiz."}
                    archive.inputStream().use{input->File(stagedCache,"$sha.crate").outputStream().use{input.copyTo(it)}}
                }
                if(native) {
                    val target=org.json.JSONObject(File(runtime,"rust-android-target-v1.json").readText())
                    require(target.getInt("schema")==1 && target.getString("version")=="1.85.1" && target.getString("target")=="aarch64-linux-android")
                    val item=target.getJSONObject("artifact");val name=item.getString("file");val sha=item.getString("sha256");val url=item.getString("url")
                    require(name=="rust-std-1.85.1-aarch64-linux-android.tar.xz" && sha.matches(Regex("[a-f0-9]{64}")) && url=="https://static.rust-lang.org/dist/2025-03-18/$name")
                    val archive=File(cache,name)
                    if(!offlineVerification)VerifiedModuleDownload.download(url,sha,archive,OfflineModuleId.RUST,operation,
                        canceled={!currentJob.isActive || control.request!=DownloadControl.Request.NONE},progress={transfer.value=it.copy(detail="Rust Android ARM64 standard library")},maxBytes=128L*1024*1024)
                    require(archive.isFile && !java.nio.file.Files.isSymbolicLink(archive.toPath()) && archive.length()<=128L*1024*1024 && VerifiedModuleDownload.sha(archive)==sha){"Android Rust target cache eksik/bozuk; açık indirme onayı gerekir"}
                    require(workspace.usableSpace>archive.length()+64L*1024*1024)
                    archive.inputStream().use{input->File(stagedCache,name).outputStream().use{input.copyTo(it)}}
                }
                phase(ModulePhase.VERIFYING,"SHA-256 doğrulanan crate arşivleri Cargo vendor biçimine hazırlanıyor; kurulum Hazır değildir.")
                trustedTool("/usr/bin/python3.12 -I -S /workspace/runtime/rust-vendor.py vendor /workspace/source --cache /workspace/crate-cache --output /workspace/vendor")
                if(!offlineVerification) {
                    phase(ModulePhase.PREPARING,"Kilitli Cargo cache hazırlandı; proje kodu çalıştırılmadı. Ağları kapatıp ayrıca Rust hedef derlemesini onaylayın.")
                    return@launch
                }
                if(native)trustedTool("/usr/bin/python3.12 -I -S /workspace/runtime/rust-android.py --prepare-target --prefix /workspace/rust-android-toolchain --base-prefix /opt/appforge-device/rust-1.85.1 --cache /workspace/crate-cache")
                val trustedRuntime=RuntimeGenerationStore(stage!!.root).snapshot()
                val compilerSnapshot=if(native)RuntimeGenerationStore(File(workspace,"rust-android-toolchain")).snapshot()else null
                val helperHashes=helpers.associateWith{RustProjectPolicy.digest(File(runtime,it))}
                phase(ModulePhase.OFFLINE_TEST,"Onaylı Cargo --frozen --offline hedef derlemesi; indirme yapılmaz. Toplam iş miktarı bilinmiyor.")
                val command=if(native)"/usr/bin/python3.12 -I -S /workspace/runtime/rust-android.py /workspace/source --prefix /workspace/rust-android-toolchain --source-execution-consent --vendor /workspace/vendor"
                    else "/usr/bin/python3.12 -I -S /workspace/runtime/rust-project.py /workspace/source --target wasm32-unknown-unknown --source-execution-consent --vendor /workspace/vendor"
                for(step in listOf(command+(if(native)" --export" else " --export-wasm"),command)) {
                    require(helperHashes.all{(name,sha)->RustProjectPolicy.digest(File(runtime,name))==sha}){"Rust hazırlık doğrulayıcısı değişti"}
                    if(native)require(compilerSnapshot==RuntimeGenerationStore(File(workspace,"rust-android-toolchain")).snapshot()){"Özel Rust derleyicisi değişti"}
                    RustPreparationIntegrity.requireUnchangedRuntime(trustedRuntime,RuntimeGenerationStore(stage!!.root).snapshot())
                    control.checkpoint();require(!networkSeen.get() && connectivity.allNetworks.isEmpty()){"Ağ açıldı; Rust çıktısı kabul edilmedi."}
                    val result=shell.execute(operation,stage!!.root,workspace,AndroidRuntimeTrust.shellEnvironment+step,confirmed=true,timeoutMs=1_800_000L)
                    require(!result.timedOut && result.exitCode==0){"Rust derlemesi başarısız; cache/target eksik olabilir. "+com.appforge.studio.terminal.TerminalSecretMasker.redact(result.output.takeLast(2000))}
                }
                control.checkpoint();currentCoroutineContext().ensureActive()
                require(!networkSeen.get() && connectivity.allNetworks.isEmpty()){"Ağ açıldı; Rust hazır kaydı yazılmadı."}
                RustPreparationIntegrity.requireUnchangedRuntime(trustedRuntime,RuntimeGenerationStore(stage!!.root).snapshot())
                require(helperHashes.all{(name,sha)->RustProjectPolicy.digest(File(runtime,name))==sha}){"Rust hazırlık doğrulayıcısı değişti"}
                if(native)require(compilerSnapshot==RuntimeGenerationStore(File(workspace,"rust-android-toolchain")).snapshot()){"Özel Rust derleyicisi değişti"}
                trustedTool("/usr/bin/python3.12 -I -S /workspace/runtime/rust-vendor.py verify /workspace/source --cache /workspace/crate-cache --output /workspace/vendor")
                OfflineBuildPackManager.verifyProtectedTools(stage!!.root,protected)
                require(AndroidRuntimeTrust.ready(stage!!.root)){"Rust hazırlığı runtime güven yapılandırmasını değiştirdi."}
                // This is intentionally not a runtime-generation commit: source scripts cannot alter active tools/cache.
                phase(ModulePhase.VERIFYING,"Rust hedef çıktısı ve kaynak kimliği doğrulanıyor; runtime stage etkinleştirilmeyecek.")
                RustPreparedProjects(storeRoot).publish(draft,workspace,plan.fingerprint){currentJob.isActive && control.request==DownloadControl.Request.NONE && !networkSeen.get() && connectivity.allNetworks.isEmpty()}
                phase(ModulePhase.PREPARING,"Rust çıktısı özel kopyada doğrulandı. Kopyayı ayrıca seçin; Android fiziksel çalışma kabulü yapılmadı.")
            }catch(e:CancellationException){phase(ModulePhase.PAUSED,"Rust hazırlığı durdu; yarım çıktı hazır kabul edilmez.");throw e}
            catch(e:Exception){phase(ModulePhase.ERROR,"Rust hazırlığı engellendi: "+(e.message?:"bilinmeyen hata"))}
            finally{if(registered)runCatching{connectivity.unregisterNetworkCallback(callback)};withContext(NonCancellable){try{stage?.close()}finally{permit?.close();busy.value=false;DownloadNetwork.restorePreference()}}}
        }
    }
    @Synchronized fun cancel(){stopPending.value=true;control.requestCancel();status.value="Durdurma istendi; çalışan build komutu güvenli kontrol noktasında bitecek."}
    @Synchronized fun pause(){stopPending.value=true;control.requestPause();status.value="Kontrol noktasında duraklatma bekleniyor; bayt düzeyinde pause desteklenmez."}
    @Synchronized fun serviceStopped(){job?.cancel()}
}
