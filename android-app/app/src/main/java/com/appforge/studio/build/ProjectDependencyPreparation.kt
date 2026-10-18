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
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    private fun caches(context:Context,override:File?=null):List<File> {
        val root=override?:OfflineBuildPackManager.runtimeRootfs(context)?:error("Normal motor runtime yok")
        return listOf(File(root,"root/.gradle-appforge/caches"),File(root,"opt/appforge-device/npm-cache-v1"),File(root,"opt/appforge-device/npm-cache-expo54-v1"),File(root,"root/.cache/pip"))
    }
    private fun receipt(context:Context,plan:ProjectDependencyPlan)=File(context.noBackupFilesDir,"project-dependency-v4/${plan.fingerprint}.json")
    fun ready(context:Context,plan:ProjectDependencyPlan)=ProjectDependencyEvidence.ready(receipt(context,plan),plan,caches(context))
    @Synchronized fun start(context:Context,draft:ProjectDraft,sourceExecutionConsent:Boolean,offlineVerification:Boolean) {
        if(busy.value)return
        require(sourceExecutionConsent){"Proje build/lifecycle kodunu çalıştırma onayı gerekir."}
        transfer.value=null
        busy.value=true
        try{ModuleDownloadService.start(context.applicationContext)}catch(e:RuntimeException){busy.value=false;status.value="Android veri aktarım servisini başlatmadı; işlem başlamadı.";return}
        job=scope.launch {
            val preparationJob=currentCoroutineContext()[Job]!!
            var permit:BuildModuleUsage.PreparationPermit?=null
            var buildId:String?=null
            var generation:RuntimeGenerationStore.Prepared?=null
            var reviewedSource:File?=null
            var callback:ConnectivityManager.NetworkCallback?=null
            val connectivity=context.getSystemService(ConnectivityManager::class.java)
            val networkSeen=AtomicBoolean(false)
            try {
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
                val protected=OfflineBuildPackManager.protectedTools(context)
                val activeRoot=OfflineBuildPackManager.runtimeRootfs(context)?:error("Runtime yok")
                generation=RuntimeGenerationStore(activeRoot).prepareOwner("PROJECT_PREPARATION",canceled={if(!preparationJob.isActive)throw CancellationException("İptal edildi")})
                status.value=if(offlineVerification)"Gerçek bağlantısız build çalışıyor; kaynak ve cache yeniden doğrulanacak." else "Onaylı proje build/lifecycle kodu çalışıyor; bağımlılıklar hazırlanıyor. Boyut bilinmiyor."
                val runDraft=draft.copy(importedFolder=reviewedSource!!.path,allowDependencyDownloads=!offlineVerification,signingMode=SigningMode.DEBUG,keystoreUri=null,storePassword="",keyPassword="",keyAlias="")
                buildId=DeviceBuildEngine.start(context,runDraft,null,permit,generation!!.root).id
                val observer=ExternalDownloadTelemetry(OfflineModuleId.ANDROID_CORE,"project-$buildId")
                while(DeviceBuildEngine.isExecuting(buildId!!)){
                    DeviceBuildEngine.transferFile(buildId!!)?.let{file->if(file.isFile && file.length()<=2048)runCatching{observer.accept(file.readText(),android.os.SystemClock.elapsedRealtime())?.let{transfer.value=it}}}
currentCoroutineContext().ensureActive();if(offlineVerification && connectivity!!.allNetworks.isNotEmpty()){networkSeen.set(true);DeviceBuildEngine.cancel(buildId!!)};delay(500)}
                val result=DeviceBuildEngine.snapshot(buildId!!)?:error("Build sonucu yok")
                require(result.status=="success"){"Proje derlemesi başarısız: "+result.logs.takeLast(8).joinToString("\n").take(2000)}
                require(!offlineVerification || !networkSeen.get()){"Doğrulama sırasında ağ açıldı; offline hazır kaydı yazılmadı."}
                val after=ProjectDependencyEvidence.analyze(draft);require(after.fingerprint==plan.fingerprint){"Hazırlama sırasında proje değişti; sonuç geçersiz."}
                OfflineBuildPackManager.verifyProtectedTools(generation!!.root,protected)
                val candidate=File(receipt(context,plan).path+".candidate")
                ProjectDependencyEvidence.record(candidate,plan,caches(context,generation!!.root),offlineVerification)
                if(offlineVerification)check(ProjectDependencyEvidence.ready(candidate,plan,caches(context,generation!!.root)))
                generation!!.commit(verify={OfflineBuildPackManager.verifyProtectedTools(it,protected)})
                require(!offlineVerification || !networkSeen.get()){"Ağ doğrulama bitmeden açıldı; hazır kaydı yayımlanmadı."}
                java.nio.file.Files.move(candidate.toPath(),receipt(context,plan).toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                if(offlineVerification)check(ready(context,plan)){"Cache bütünlüğü doğrulanamadı."}
                status.value=if(offlineVerification)"Proje çevrimdışı build ile doğrulandı; kaynak/cache değişirse hazır kaydı geçersizleşir." else "Online hazırlık bitti; çevrimdışı hazır değil. Ağları kapatıp açık onayla doğrulayın."
            }catch(e:CancellationException){status.value="Proje hazırlığı iptal edildi; cache hazır kabul edilmez.";throw e}
            catch(e:Exception){status.value="Hazırlama engellendi: ${e.message}"}
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
    @Synchronized fun cancel(){job?.cancel()}
}
