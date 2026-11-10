@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.appforge.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appforge.studio.build.*
import com.appforge.studio.model.ProjectDraft
import kotlinx.coroutines.*

/** App-scoped operation state survives navigation/rotation; process death is never reported READY. */
internal object ModuleInstallOperations {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val activeProgress=kotlinx.coroutines.flow.MutableStateFlow<ModuleProgress?>(null)
    val installTarget=kotlinx.coroutines.flow.MutableStateFlow<OfflineModuleId?>(null)
    val completedInstall=kotlinx.coroutines.flow.MutableStateFlow<OfflineModuleId?>(null)
    val diagnosticOperation=kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val startedAt=kotlinx.coroutines.flow.MutableStateFlow(0L)
    val updates=kotlinx.coroutines.flow.MutableStateFlow<Map<OfflineModuleId,ModuleProgress>>(emptyMap())
    val busy=kotlinx.coroutines.flow.MutableStateFlow(false)
    val stopPending=kotlinx.coroutines.flow.MutableStateFlow(false)
    private var job:Job?=null
    private val elapsedTracker=ModuleElapsedTracker()
    private var journal:ModuleOperationJournal?=null
    @Synchronized fun restore(context:android.content.Context) {
        if(journal!=null)return
        DownloadNetwork.configure(context)
        DownloadNetwork.prepareJournal()
        journal=ModuleOperationJournal(RecoveryPathAnchor.androidPlatformPath(java.io.File(context.noBackupFilesDir,"offline-module-v3/operation.json")))
        updates.value=journal!!.restoreStrict()
        DownloadCoordinator.reconcileModules(updates.value)
    }
    val journalError=kotlinx.coroutines.flow.MutableStateFlow("")
    fun history():List<String> = journal?.history().orEmpty()
    private fun persist(){try{journal?.save(updates.value);journalError.value=""}catch(e:Exception){journalError.value="İşlem günlüğü yazılamadı; güvenli devam garantisi yok. Depolamayı kontrol edin.";throw e}}
    @Volatile private var lastEventAt=0L
    @Synchronized private fun publish(event:ModuleProgress) {
        val now=android.os.SystemClock.elapsedRealtime()
        lastEventAt=now
        val timed=elapsedTracker.observe(event,now)
        activeProgress.value=timed;updates.value=updates.value+(timed.moduleId to timed);persist()
    }
    @Volatile private var pauseRequested=false
    private var downloadControl=DownloadControl()
    @Synchronized fun start(context:android.content.Context, selected:OfflineModuleId, consent:Boolean) {
        if(busy.value)return
        try{restore(context)}catch(e:Exception){journalError.value="İndirme kaydı reddedildi; kurulum başlamadı: ${e.message}";DownloadNetwork.restorePreference();return}
        installTarget.value=selected
        completedInstall.value=null
        diagnosticOperation.value=null
        startedAt.value=android.os.SystemClock.elapsedRealtime()
        busy.value=true;pauseRequested=false;downloadControl=DownloadControl();stopPending.value=false
        try {ModuleDownloadService.start(context.applicationContext)}catch(e:RuntimeException) {
            busy.value=false;DownloadNetwork.restorePreference()
            updates.value=updates.value+(selected to ModuleProgress(selected,"start-denied",ModulePhase.ERROR,detail="Android arka plan hizmetini başlatmadı; uygulama önplandayken yeniden deneyin."))
            runCatching{persist()};return
        }
        val operationNetworkContext=DownloadNetwork.takeOperationConsent()
        job=scope.launch(operationNetworkContext) {
            val stalledWatch=launch {
                while(isActive) {
                    delay(1000)
                    synchronized(this@ModuleInstallOperations) {
                        activeProgress.value?.let { event ->
                            val expired=DownloadCoordinator.withoutStaleRates(event,android.os.SystemClock.elapsedRealtime()-lastEventAt)
                            if(expired!=event)publish(expired)
                        }
                    }
                }
            }
            try {
                var retries=0
                while(true) {
                    val last=java.util.concurrent.atomic.AtomicReference<ModuleProgress?>(null)
                    try {
                        OfflineBuildPackManager.installModules(context.applicationContext,setOf(selected),consent,downloadControl){event->
                            last.set(event);diagnosticOperation.value=event.operationId;publish(event)
                        }
                        publish(ModuleProgress(selected,last.get()?.operationId?:"final-integrity",ModulePhase.VERIFYING,
                            detail="Etkin modül son kez kontrol ediliyor."))
                        check(OfflineBuildPackManager.moduleReady(context.applicationContext,selected) &&
                            !OfflineBuildPackManager.hasPendingLifecycle(context.applicationContext,selected)) {
                            "Otomatik son bütünlük kontrolü başarısız; modül hazır değil."
                        }
                        publish(ModuleProgress(selected,last.get()?.operationId?:"final-integrity",ModulePhase.READY,
                            detail="Doğrulanmış kurulum • paketler ve etkin modül bütünlüğü kontrol edildi."))
                        completedInstall.value=selected
                        break
                    }catch(e:CancellationException){throw e}
                    catch(e:Exception) {
                        val event=last.get()
                        if(event?.phase!=ModulePhase.WAITING_CONNECTION || retries>=5)throw e
                        retries++
                        val started=android.os.SystemClock.elapsedRealtime()
                        while(!OfflineBuildPackManager.hasValidatedInternet(context.applicationContext)) {
                            downloadControl.checkpoint()
                            currentCoroutineContext().ensureActive()
                            require(android.os.SystemClock.elapsedRealtime()-started<24L*60*1000){"Bağlantı bekleme süresi doldu; kısmi arşiv korunur. Devam et ile tekrar deneyin."}
                            val waiting=event.copy(phase=ModulePhase.WAITING_CONNECTION,bytesTotal=null,estimatedRemainingMs=null,speedBytesPerSecond=null,averageSpeedBytesPerSecond=null,detail="Bağlantı bekleniyor; onaylı işlem bağlantı gelince güvenle yeniden denenecek. Bekleme: ${(android.os.SystemClock.elapsedRealtime()-started)/1000} sn")
                            publish(waiting)
                            delay(1000)
                        }
                        val retry=event.copy(phase=ModulePhase.RECONNECTING,detail="Bağlantı geldi; kaynak kimliği, kısmi dosya ve hash yeniden doğrulanıyor. Deneme $retries/5")
                        publish(retry)
                        delay((1000L shl (retries-1)).coerceAtMost(30000L))
                    }
                }
            } catch(e:CancellationException) {
                val previous=activeProgress.value?.takeIf{it.moduleId==selected}
                publish(ModuleProgress(selected,previous?.operationId?:"canceled",if(e is DownloadNetwork.PolicyBlocked)ModulePhase.WAITING_CONNECTION else if(pauseRequested)ModulePhase.PAUSED else ModulePhase.CANCELED,errorCode=if(e is DownloadNetwork.PolicyBlocked)"NETWORK_POLICY" else null,detail=if(e is DownloadNetwork.PolicyBlocked)"Ağ politikası/onay bekleniyor; kısmi dosya korunur. Devam et ile tekrar doğrulayın." else if(pauseRequested)"Duraklatıldı; doğrulanmamış kısmi dosya devam için korunur." else "İptal edildi; çalışan modüller korunur."))
            } catch(e:Exception) {
                // Preserve the manager's connection-wait/error state for this operation.
                if(activeProgress.value?.takeIf { it.moduleId==selected }?.phase !in setOf(ModulePhase.WAITING_CONNECTION,ModulePhase.ERROR))
                publish(ModuleProgress(selected,activeProgress.value?.takeIf{it.moduleId==selected}?.operationId?:"failed",ModulePhase.ERROR,errorCode=e.javaClass.simpleName,detail=e.message?:"Kurulum başarısız."))
            } finally {stalledWatch.cancel();try{runCatching{persist()}}finally{activeProgress.value=null;busy.value=false;DownloadNetwork.restorePreference()}}
        }
    }
    @Synchronized fun lifecycle(context:android.content.Context,id:OfflineModuleId,action:String) {
        require(action in setOf("remove","rollback"));if(busy.value)return
        try{restore(context)}catch(e:Exception){journalError.value="İndirme kaydı reddedildi; yaşam döngüsü işlemi başlamadı: ${e.message}";return}
        installTarget.value=null
        completedInstall.value=null
        diagnosticOperation.value=null
        startedAt.value=android.os.SystemClock.elapsedRealtime()
        busy.value=true
        try{ModuleDownloadService.start(context.applicationContext)}catch(e:RuntimeException){busy.value=false;journalError.value="Android yaşam döngüsü hizmetini başlatmadı: ${e.message}";return}
        job=scope.launch {
            val operation=java.util.UUID.randomUUID().toString()
            fun recordLifecycle(event:ModuleProgress){diagnosticOperation.value=event.operationId;DownloadCoordinator.modulePhase(event);publish(event)}
            try {
                recordLifecycle(ModuleProgress(id,operation,if(action=="remove")ModulePhase.REMOVING else ModulePhase.ROLLING_BACK,detail="Onaylı işlem: bağımlılık, sahiplik, kullanım ve generation bütünlüğü yeniden doğrulanıyor."))
                if(action=="remove")OfflineBuildPackManager.removeOwnedModule(context.applicationContext,id)
                else OfflineBuildPackManager.rollbackModule(context.applicationContext,id)
                val resultPhase=if(action=="remove" || OfflineBuildPackManager.installedVersion(context,id)==null)ModulePhase.REMOVED else if(OfflineBuildPackManager.moduleReady(context,id))ModulePhase.READY else ModulePhase.PREPARING
                recordLifecycle(ModuleProgress(id,operation,resultPhase,
                    detail=if(action=="remove")"Modül kaydı güvenle kaldırıldı. Arşivlenen runtime/host dosyaları korunur; kullanıcı kaynakları silinmedi." else if(resultPhase==ModulePhase.READY)"Önceki generation SHA-256 envanteriyle doğrulanarak etkinleştirildi." else "Önceki ortam dosyaları geri getirildi; güncel motor önkontrolü geçmeden HAZIR sayılmaz."))
            } catch(e:CancellationException){recordLifecycle(ModuleProgress(id,operation,ModulePhase.PAUSED,detail="İşlem kesildi; yeniden açılışta generation günlüğü ve dosyalar doğrulanacak."));throw e}
            catch(e:Exception){recordLifecycle(ModuleProgress(id,operation,ModulePhase.ERROR,errorCode=e.javaClass.simpleName,detail="İşlem engellendi: ${e.message}"))}
            finally{activeProgress.value=null;busy.value=false}
        }
    }
    @Synchronized fun serviceStopped(){pauseRequested=true;job?.cancel()}
    @Synchronized fun cancel(){pauseRequested=false;downloadControl.requestCancel();showStopPending()}
    private fun showStopPending(){
        stopPending.value=true
        val event=activeProgress.value?:return
        publish(event.copy(speedBytesPerSecond=null,averageSpeedBytesPerSecond=null,estimatedRemainingMs=null,
            detail="Durdurma istendi; çalışan paket işlemi güvenli kontrol noktasına ulaşınca duracak. "+event.detail))
    }
    @Synchronized fun pause(){pauseRequested=true;downloadControl.requestPause();showStopPending()}
}

@Composable internal fun OfflineBuildPackScreen(onBack:()->Unit,firstRun:Boolean=false,onFirstRunComplete:((Boolean)->Unit)?=null,project:ProjectDraft?=null,onRustPrepared:((ProjectDraft)->Unit)?=null) {
    var section by rememberSaveable { mutableStateOf("modules") }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val journalError by ModuleInstallOperations.journalError.collectAsState()
    val events by ModuleInstallOperations.updates.collectAsState()
    val busy by ModuleInstallOperations.busy.collectAsState()
    val stopPending by ModuleInstallOperations.stopPending.collectAsState()
    val artifacts by DownloadCoordinator.records.collectAsState()
    val operationTree by DownloadCoordinator.operations.collectAsState()
    val networkStatus by DownloadNetwork.status.collectAsState()
    val preparing by ProjectDependencyPreparation.busy.collectAsState()
    var networkPolicy by remember{mutableStateOf(DownloadNetwork.policy)}
    var confirmAnyNetwork by remember{mutableStateOf(false)}
    var mobileModule by remember{mutableStateOf<OfflineModuleId?>(null)}
    val usageRevision by BuildModuleUsage.changes.collectAsState()
    var recovered by remember{mutableStateOf(false)}
    var pendingLifecycle by remember { mutableStateOf<Pair<OfflineModuleId,String>?>(null) }
    var history by remember{mutableStateOf<List<String>>(emptyList())}
    var chosen by remember { mutableStateOf<OfflineModuleId?>(null) }
    var selectedName by rememberSaveable { mutableStateOf(OfflineModuleId.NODE_WEB.name) }
    var moduleMenuOpen by remember { mutableStateOf(false) }
    var prefetchBusy by remember { mutableStateOf(false) }
    var moduleDetails by rememberSaveable { mutableStateOf(false) }
    var recoveryConfirm by remember { mutableStateOf(false) }
    var stageConfirm by remember { mutableStateOf(false) }
    var ubuntuFetchConfirm by remember { mutableStateOf(false) }
    var ubuntuFetchStatus by remember { mutableStateOf("") }
    var stageMessage by remember { mutableStateOf("") }
    var recoveryBusy by remember { mutableStateOf(false) }
    var recoveryMessage by remember { mutableStateOf("") }
    val liveProgress by ModuleInstallOperations.activeProgress.collectAsState()
    val installTarget by ModuleInstallOperations.installTarget.collectAsState()
    val completedInstall by ModuleInstallOperations.completedInstall.collectAsState()
    val diagnosticOperation by ModuleInstallOperations.diagnosticOperation.collectAsState()
    val startedAt by ModuleInstallOperations.startedAt.collectAsState()
    var elapsedSeconds by remember(startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(busy,startedAt) {
        while(busy && startedAt>0L) {
            elapsedSeconds=(android.os.SystemClock.elapsedRealtime()-startedAt)/1000
            delay(1000)
        }
    }
    var sdkConsent by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf("") }
    var confirmPrune by remember{mutableStateOf(false)}
    var confirmHistoryCleanup by remember{mutableStateOf(false)}
    var historyDetails by rememberSaveable { mutableStateOf(false) }
    var pruneBytes by remember(confirmPrune){mutableStateOf<Long?>(null)}
    var pruneReason by remember(confirmPrune){mutableStateOf<String?>(null)}
    val selected=OfflineModuleId.entries.firstOrNull { it.name==selectedName } ?: OfflineModuleId.NODE_WEB
    var recordedVersion by remember(selected) { mutableStateOf<String?>(null) }
    var updateRequired by remember(selected) { mutableStateOf(false) }
    var lifecycle by remember(selected) { mutableStateOf<ModuleLifecycleStatus?>(null) }
    var aptState by remember(selected) { mutableStateOf("") }
    var migrationReason by remember(selected) { mutableStateOf<String?>(null) }
    var portableReady by remember { mutableStateOf(false) }
    var installPlan by remember(chosen) { mutableStateOf<List<OfflineModuleId>?>(null) }
    val plan=remember(project){project?.let{OfflineModules.forProject(it)}}

    LaunchedEffect(Unit){withContext(Dispatchers.IO){runCatching{ModuleInstallOperations.restore(context)}.onFailure{detail="İndirme günlüğü reddedildi: ${it.message}"};runCatching{OfflineBuildPackManager.recoverPending(context)}.onFailure{detail="Kurtarma güvenli biçimde durdu: ${it.message}"}};recovered=true}
    LaunchedEffect(selected,recovered,busy,events[selected]?.phase) {
        if(!recovered || busy)return@LaunchedEffect
        val recorded=withContext(Dispatchers.IO) {
            OfflineBuildPackManager.installedVersion(context,selected) to
                OfflineBuildPackManager.updateAvailable(context,selected)
        }
        recordedVersion=recorded.first
        updateRequired=recorded.second
    }
    LaunchedEffect(section,recovered,busy) {
        if(section in setOf("history","diagnostics") && recovered && !busy)
            history=withContext(Dispatchers.IO){ModuleInstallOperations.history()}
    }
    LaunchedEffect(section,selected,moduleDetails,recovered,busy,usageRevision) {
        lifecycle=null
        aptState=""
        portableReady=false
        if(section!="modules" || !recovered || busy)return@LaunchedEffect
        if(moduleDetails) {
            val result=withContext(Dispatchers.IO) {
                runCatching { OfflineBuildPackManager.lifecycleStatus(context,selected) } to
                    runCatching { OfflineBuildPackManager.aptSummary(context,selected) }
            }
            lifecycle=result.first.getOrNull()
            aptState=result.second.fold(
                { it?.let { summary -> summary.detail+"\n"+summary.versions.joinToString("\n") }.orEmpty() },
                { "APT kilidi doğrulanamadı: ${it.message}" }
            )
        }
        if(selected==OfflineModuleId.WINDOWS_PORTABLE)
            portableReady=withContext(Dispatchers.IO){OfflineBuildPackManager.moduleReady(context,selected)}
    }
    LaunchedEffect(chosen) {
        val id=chosen ?: return@LaunchedEffect
        val result=withContext(Dispatchers.IO) {
            runCatching {
                val blocked=OfflineBuildPackManager.aptMigrationBlockedReason(context,id)
                blocked to OfflineModules.resolve(setOf(id))
            }
        }
        result.fold({ (blocked,modules) ->
            if(blocked!=null) {
                if(selected==id && section=="modules")migrationReason=blocked else detail=blocked
                chosen=null
            } else {
                migrationReason=null
                installPlan=modules
            }
        }, {
            detail="Kurulum ön kontrolü başarısız: ${it.message}"
            chosen=null
        })
    }
    LaunchedEffect(confirmPrune) {
        if(!confirmPrune)return@LaunchedEffect
        val result=withContext(Dispatchers.IO){runCatching{OfflineBuildPackManager.prunableSnapshotBytes(context)}}
        pruneBytes=result.getOrNull()
        pruneReason=result.exceptionOrNull()?.message
    }
    fun begin(id:OfflineModuleId) {
        if(DownloadNetwork.policy==DownloadNetworkPolicy.WIFI_PREFERRED && !DownloadNetwork.allowed()){mobileModule=id;return}
        ModuleInstallOperations.start(context,id,sdkConsent)
    }
    if(recoveryConfirm) AlertDialog(
        onDismissRequest={recoveryConfirm=false},
        title={Text("İzole Ubuntu hazırlığını onayla")},
        text={Text(
            "Doğrulanmış Ubuntu arşivi indirilebilir ve ek depolama kullanılır. "+
            "Yeni ortam ayrı dizine kurulur. Mevcut Node.js, R2 paketleri, "+
            "modül kayıtları ve projeler değiştirilmez. "+
            "Bu işlem Python kurulumunu henüz etkinleştirmez."
        )},
        confirmButton={
            TextButton(
                enabled=!busy && !preparing && !recoveryBusy,
                onClick={
                    recoveryConfirm=false
                    recoveryBusy=true
                    recoveryMessage="İzole Ubuntu hazırlığı başladı."
                    scope.launch {
                        try {
                            val result=withContext(Dispatchers.IO) {
                                DeviceBuildRuntimeV3.prepareIsolatedRecovery(
                                    context
                                ) { progress ->
                                    scope.launch {
                                        recoveryMessage=progress
                                    }
                                }
                            }
                            recoveryMessage=result+
                                " • Eski Node.js çalışmaya devam ediyor."
                        } catch(e:Exception) {
                            recoveryMessage=
                                "İzole hazırlık engellendi: ${e.message}. "+
                                "Mevcut ortam değiştirilmedi."
                        } finally {
                            recoveryBusy=false
                        }
                    }
                }
            ){Text("İzole ortamı hazırla")}
        },
        dismissButton={
            TextButton(onClick={recoveryConfirm=false}){
                Text("Vazgeç")
            }
        }
    )
    if(ubuntuFetchConfirm) AlertDialog(
        onDismissRequest={ubuntuFetchConfirm=false},
        title={Text("Eksik Ubuntu paketlerini indir")},
        text={Text(
            "Kaydedilmiş ağ politikanız uygulanır. "+
            "Ed25519 sürümü ve APK SHA-256 kataloğuyla yalnız eksik R2 paketleri alınacak. "+
            "Ubuntu Snapshot ağına başvurulmaz. "+
            "İndirmeden sonra imzalı Ubuntu APT indeksleri de "+
            "doğrulanacak. Modül kurulumu veya runtime geçişi "+
            "yapılmayacak."
        )},
        confirmButton={
            TextButton(
                enabled=!busy && !preparing &&
                    !recoveryBusy && !prefetchBusy,
                onClick={
                    ubuntuFetchConfirm=false
                    recoveryBusy=true
                    ubuntuFetchStatus="Ubuntu indirmesi hazırlanıyor..."
                    scope.launch {
                        try {
                            ubuntuFetchStatus=withContext(Dispatchers.IO) {
                                val packageResult=DeviceBuildRuntimeV3
                                    .prefetchIsolatedUbuntuPackages(context){status->
                                        scope.launch{ubuntuFetchStatus=status}
                                    }
                                val signedResult=DeviceBuildRuntimeV3
                                    .verifyIsolatedAptMetadata(context){status->
                                        scope.launch{ubuntuFetchStatus=status}
                                    }
                                packageResult+"\n"+signedResult
                            }
                        } catch(e:Exception) {
                            ubuntuFetchStatus=
                                "Ubuntu paket/İmzalı APT doğrulaması engellendi: "+
                                (e.message?:"Bilinmeyen hata")+
                                " • Aktif Node.js korunuyor."
                        } finally {
                            recoveryBusy=false
                        }
                    }
                }
            ){Text("Paketleri ve APT imzalarını doğrula")}
        },
        dismissButton={
            TextButton(onClick={ubuntuFetchConfirm=false}){
                Text("Vazgeç")
            }
        }
    )
    if(stageConfirm) AlertDialog(
        onDismissRequest={stageConfirm=false},
        title={Text("İzole R2 paket aktarımı")},
        text={Text(
            "Mevcut SHA-256 doğrulanmış R2 paketleri temiz Ubuntu "+
            "önbelleğine kopyalanacak. Ağ indirmesi, dpkg kurulumu, "+
            "aktif runtime değişikliği ve modül READY işlemi yapılmaz."
        )},
        confirmButton={
            TextButton(
                enabled=!busy && !preparing && !recoveryBusy &&
                    !prefetchBusy,
                onClick={
                    stageConfirm=false
                    recoveryBusy=true
                    stageMessage="R2 paketleri doğrulanıyor..."
                    scope.launch {
                        try {
                            stageMessage=withContext(Dispatchers.IO) {
                                DeviceBuildRuntimeV3.stageIsolatedR2Cache(
                                    context
                                )
                            }
                        } catch(e:Exception) {
                            stageMessage="İzole R2 hazırlığı engellendi: "+
                                (e.message?:"Bilinmeyen hata")+
                                " • Aktif runtime değiştirilmedi."
                        } finally {
                            recoveryBusy=false
                        }
                    }
                }
            ){Text("Paketleri doğrula ve aktar")}
        },
        dismissButton={
            TextButton(onClick={stageConfirm=false}){
                Text("Vazgeç")
            }
        }
    )
    if(confirmAnyNetwork || mobileModule!=null)AlertDialog(onDismissRequest={confirmAnyNetwork=false;mobileModule=null},title={Text("Mobil veri kullanım onayı")},text={
        Text("Mobil veri ve ölçümlü bağlantı kullanılabilir. Toplam kalan boyut bilinmiyor; yeni alt bağımlılıklar gerekebilir. Dosya bazındaki doğrulanmış miktarlar indirme geçmişinde gösterilir.")
    },confirmButton={TextButton(enabled=!busy && !preparing,onClick={
        val id=mobileModule;mobileModule=null;confirmAnyNetwork=false
        if(id!=null){DownloadNetwork.allowMobileOnce();ModuleInstallOperations.start(context,id,sdkConsent)}
        else {networkPolicy=DownloadNetworkPolicy.ALLOW_ANY;DownloadNetwork.select(networkPolicy)}
    }){Text(if(mobileModule!=null)"Yalnız bu işlem için izin ver" else "Bu ağ tercihini kaydet")}},dismissButton={TextButton(onClick={confirmAnyNetwork=false;mobileModule=null}){Text("Reddet; kaydı koru")}})
    pendingLifecycle?.let{(id,action)->
        val permission=if(id!=selected)null else if(action=="remove")lifecycle?.remove else lifecycle?.rollback
        AlertDialog(onDismissRequest={pendingLifecycle=null},title={Text(if(action=="remove")"Modülü kaldırmayı onayla" else "Önceki sürüme dönmeyi onayla")},
            text={Text("${OfflineModules.spec(id).title}: sahiplik, bağımlılar, aktif kullanım ve bütünlük tekrar kontrol edilir. Kullanıcı kaynakları/anahtarları korunur. Rootfs son geçişten sonra değişmişse bütün ortam geri alma engellenir. Runtime ve Portable kaldırılması dosyaları arşivler; disk boşalttığı varsayılmaz.")},
            confirmButton={TextButton(enabled=!busy && permission?.allowed==true,onClick={pendingLifecycle=null;ModuleInstallOperations.lifecycle(context,id,action)}){Text("Onayla")}},
            dismissButton={TextButton(onClick={pendingLifecycle=null}){Text("Vazgeç")}})
    }
    if(confirmPrune)AlertDialog(
        onDismissRequest={confirmPrune=false},
        title={Text("Kullanılmayan eski sürümleri temizle")},
        text={Text(pruneReason ?: pruneBytes?.let {
            "${it/1024/1024} MiB doğrulanmış dosya. Etkin ortam ve korunan geri alma sürümleri saklanır."
        } ?: "Eski ortam kopyaları denetleniyor…")},
        confirmButton={TextButton(enabled=!busy && pruneBytes!=null,onClick={
            confirmPrune=false
            scope.launch {
                detail=withContext(Dispatchers.IO){runCatching{OfflineBuildPackManager.pruneUnusedSnapshots(context)}
                    .fold({"${it/1024/1024} MiB eski snapshot temizlendi."},{"Temizleme engellendi: ${it.message}"})}
            }
        }){Text("Onayla ve temizle")}},
        dismissButton={TextButton(onClick={confirmPrune=false}){Text("Vazgeç")}}
    )
    if(confirmHistoryCleanup)AlertDialog(
        onDismissRequest={confirmHistoryCleanup=false},
        title={Text("Eski indirme kayıtlarını bırak")},
        text={Text("Yalnız kurtarma gerektiren ve doğrulanmış kısmi baytı olmayan eski geçmiş kayıtları kaldırılır. İndirilen dosyalar, paket önbelleği ve kullanıcı projeleri silinmez. Bu kayıtlar üzerinden devam etme bilgisi kaybolur; sonraki kurulum yeniden açık onay ister.")},
        confirmButton={TextButton(enabled=!busy && !preparing && !stopPending,onClick={
            confirmHistoryCleanup=false
            scope.launch {
                detail=withContext(Dispatchers.IO) {
                    runCatching { DownloadCoordinator.releaseUnverifiedRecoveryHistory() }
                        .fold({"Geçmişten $it doğrulanmamış kayıt bırakıldı. Paket dosyaları korunuyor."},
                              {"İndirme geçmişi temizliği engellendi: ${it.message}"})
                }
            }
        }){Text("Kayıtları bırak • dosyaları koru")}},
        dismissButton={TextButton(onClick={confirmHistoryCleanup=false}){Text("Vazgeç")}}
    )
    if(chosen!=null) {
        val id=chosen!!
        val modules=installPlan
        val needsSdk=modules?.any {
            it==OfflineModuleId.ANDROID_CORE || it==OfflineModuleId.EXPO_REACT_NATIVE ||
                it==OfflineModuleId.RUST_ANDROID_NDK
        }==true
        AlertDialog(onDismissRequest={chosen=null},title={Text("Kurulumu onayla")},text={Column {
            Text(if(modules==null)"Kurulum planı hazırlanıyor…" else
                "Seçilen modül: "+OfflineModules.spec(id).title)
            if(needsSdk){
                Text("Bu işlemde Android SDK kurulumu veya güncellemesi gerekirse lisans onayı kullanılır.")
                TextButton(onClick={android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://developer.android.com/studio/terms")).also{context.startActivity(it)}}){Text("Android SDK lisansını incele")}
                Row{Checkbox(sdkConsent,{sdkConsent=it});Text("SDK lisansını kabul ediyorum")}
            }
        }},confirmButton={TextButton(
            enabled=!busy && !preparing && modules!=null && (!needsSdk||sdkConsent),
            onClick={chosen=null;begin(id)}
        ){Text("Onayla ve kur")}},dismissButton={TextButton(onClick={chosen=null}){Text("Vazgeç")}})
    }
    Scaffold(topBar={TopAppBar(title={Text("Birleşik Modül Merkezi V9")},navigationIcon={TextButton(onClick=onBack){Text("Geri")}})}){padding->
        LazyColumn(modifier=Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { ModuleManagerNavigation(section) { section=it } }
            if(section=="modules")item {
                val spec=OfflineModules.spec(selected)
                val unsupported=if(selected !in setOf(
                    OfflineModuleId.FAST_HTML,
                    OfflineModuleId.WINDOWS_PORTABLE
                )) DeviceBuildRuntimeV3.platformError() else null
                val active=liveProgress?.takeIf{
                    busy && it.moduleId in OfflineModules.resolve(setOf(selected))
                }
                val installingSelected=busy && installTarget==selected
                val event=if(installingSelected)active else active ?: events[selected]
                val canInstall=recovered && chosen==null &&
                    !busy && !preparing && !prefetchBusy && !recoveryBusy && unsupported==null

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Birleşik Modül Merkezi V9",
                            style=MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Tüm modüller tek seçimden yönetilir. İndirme, SHA-256, kurulum ve son bütünlük kontrolü aynı alandadır.",
                            style=MaterialTheme.typography.bodySmall
                        )
                        Text("Modül",style=MaterialTheme.typography.labelLarge)
                        Box {
                            OutlinedButton(
                                enabled=!busy && !prefetchBusy,
                                onClick={moduleMenuOpen=true},
                                modifier=Modifier.fillMaxWidth()
                            ) {
                                Text(spec.title+"  ▾")
                            }
                            DropdownMenu(
                                expanded=moduleMenuOpen,
                                onDismissRequest={moduleMenuOpen=false}
                            ) {
                                OfflineModules.all.forEach { item ->
                                    DropdownMenuItem(
                                        text={Text(item.title)},
                                        onClick={
                                            selectedName=item.id.name
                                            moduleMenuOpen=false
                                            moduleDetails=false
                                        }
                                    )
                                }
                            }
                        }

                        if(unsupported!=null)
                            Text(unsupported,color=MaterialTheme.colorScheme.error)
                        migrationReason?.let { reason ->
                            Text("Güvenli runtime geçişi gerekli",color=MaterialTheme.colorScheme.error)
                            Text(reason,style=MaterialTheme.typography.bodySmall)
                            Text("Mevcut Node.js ortamı ve R2 önbelleği korunuyor. Kurulum engeli kaldırılmadı.",style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(
                                enabled=!recoveryBusy && !busy && !preparing &&
                                    !prefetchBusy,
                                onClick={recoveryConfirm=true}
                            ) {
                                Text("İzole temiz Ubuntu hazırla")
                            }
                            if(recoveryBusy)
                                LinearProgressIndicator(
                                    modifier=Modifier.fillMaxWidth()
                                )
                            if(recoveryMessage.isNotBlank())
                                Text(
                                    recoveryMessage,
                                    style=MaterialTheme.typography.bodySmall
                                )
                            if(moduleDetails && selected==OfflineModuleId.PYTHON_ANDROID) {
                                OutlinedButton(
                                    enabled=!recoveryBusy && !busy &&
                                        !preparing && !prefetchBusy,
                                    onClick={stageConfirm=true}
                                ) {
                                    Text("R2 önbelleğini izole Ubuntu'ya aktar")
                                }
                                if(stageMessage.isNotBlank())
                                    Text(
                                        stageMessage,
                                        style=MaterialTheme.typography.bodySmall
                                    )
                                OutlinedButton(
                                    enabled=!recoveryBusy && !busy &&
                                        !preparing && !prefetchBusy,
                                    onClick={ubuntuFetchConfirm=true}
                                ) {
                                    Text("Eksik Ubuntu paketlerini indir")
                                }
                                if(ubuntuFetchStatus.isNotBlank())
                                    Text(
                                        ubuntuFetchStatus,
                                        style=MaterialTheme.typography.bodySmall
                                    )
                                Text(
                                    "Aşağıdaki R2 sayacı ayrı özel önbelleğe aittir; "+
                                    "izole Ubuntu paket sayısını göstermez.",
                                    style=MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Text(
                            "Durum: "+when {
                                installingSelected -> when {
                                    active==null -> "Kurulum hazırlanıyor"
                                    active.phase==ModulePhase.READY -> "Kurulum devam ediyor"
                                    active.moduleId!=selected ->
                                        "${OfflineModules.spec(active.moduleId).title} · ${active.phase.turkishLabel()}"
                                    else -> active.phase.turkishLabel()
                                }
                                busy && active!=null -> active.phase.turkishLabel()
                                !recovered -> "Önceki işlemler kontrol ediliyor"
                                migrationReason!=null -> "Güvenli runtime geçişi gerekli"
                                event?.phase==ModulePhase.READY -> "Doğrulanmış kurulum"
                                updateRequired -> "Güncelleme mevcut"
                                recordedVersion!=null -> "Kurulum kaydı var"
                                event!=null -> event.phase.turkishLabel()
                                else -> "Kurulum kaydı bulunamadı"
                            },
                            style=MaterialTheme.typography.titleSmall
                        )

                        recordedVersion?.let {
                            Text("Sürüm: $it",style=MaterialTheme.typography.bodySmall)
                        }

                        if(installingSelected) {
                            LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                            Text("Geçen süre: ${elapsedSeconds/60} dk ${elapsedSeconds%60} sn")
                        } else if(!busy && completedInstall==selected) {
                            LinearProgressIndicator(progress={1f},modifier=Modifier.fillMaxWidth())
                            Text("Kurulum tamamlandı · %100")
                        }
                        if(!installingSelected && event!=null && event.elapsedMs>0L) {
                            val total=event.elapsedMs/1000L
                            Text("Son işlem süresi: ${total/60} dk ${total%60} sn",style=MaterialTheme.typography.bodySmall)
                            Text("İndirme: ${event.downloadElapsedMs/1000L} sn • "+
                                "Doğrulama: ${event.verificationElapsedMs/1000L} sn • "+
                                "Kurulum: ${event.installationElapsedMs/1000L} sn",
                                style=MaterialTheme.typography.bodySmall)
                            val other=(event.elapsedMs-event.downloadElapsedMs-
                                event.verificationElapsedMs-event.installationElapsedMs).coerceAtLeast(0L)
                            Text("Hazırlık / bekleme: ${other/1000L} sn",style=MaterialTheme.typography.bodySmall)
                            Text("Süreler işlem günlüğünde saklanır; geçmiş kayıt tek başına modülün hazır olduğunu kanıtlamaz.",
                                style=MaterialTheme.typography.bodySmall)
                        }

                        if(event!=null) {
                            if(moduleDetails || (recordedVersion==null || active!=null) &&
                                event.phase in setOf(ModulePhase.ERROR,ModulePhase.AWAITING_CONSENT,ModulePhase.WAITING_CONNECTION))
                                Text(event.detail,style=MaterialTheme.typography.bodySmall)
                            if(moduleDetails && event.phase==ModulePhase.ERROR)
                                Text(
                                    "Hata: "+(event.errorCode?:"Bilinmiyor"),
                                    color=MaterialTheme.colorScheme.error
                                )

                            if(busy && event.errorCode=="APT_DOWNLOAD_CONSENT") {
                                Button(
                                    enabled=!stopPending,
                                    onClick={
                                        runCatching{
                                            AptDownloadConsent.approve(event.operationId)
                                        }.onFailure{
                                            detail=it.message?:"APT onayı başarısız"
                                        }
                                    }
                                ) { Text("APT paket listesini onayla") }
                            }

                            if(busy && event.phase==ModulePhase.DOWNLOADING) {
                                val pct=event.percent
                                Text(pct?.let { "Dosya indiriliyor · %$it" } ?: "Dosya indiriliyor")
                                if(pct!=null)
                                    LinearProgressIndicator(
                                        progress={pct/100f},
                                        modifier=Modifier.fillMaxWidth()
                                    )
                                else
                                    LinearProgressIndicator(
                                        modifier=Modifier.fillMaxWidth()
                                    )
                                Text(
                                    "İndirilen: ${event.bytesDownloaded/1024/1024} MiB / "+
                                    (event.bytesTotal?.let{
                                        "${it/1024/1024} MiB"
                                    }?:"toplam bilinmiyor")
                                )
                                Text("Hız: "+(
                                    event.speedBytesPerSecond?.let{
                                        "%.1f MiB/sn".format(it/1024/1024)
                                    }?:"hesaplanıyor"
                                ))
                                event.averageSpeedBytesPerSecond?.let { average ->
                                    Text("Ortalama hız: %.1f MiB/sn".format(average/1024/1024),style=MaterialTheme.typography.bodySmall)
                                }
                            } else if(busy && !installingSelected && event.phase !in setOf(
                                ModulePhase.READY,ModulePhase.ERROR,ModulePhase.CANCELED
                            )) {
                                LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                            }
                        }

                        V9R2CandidatePrefetchCard(
                            selected=selected,
                            installEnabled=canInstall,
                            showCacheDetails=moduleDetails,
                            onBusyChange={prefetchBusy=it},
                            onInstall={chosen=it}
                        )

                        if(busy && active!=null && active.phase !in setOf(
                            ModulePhase.READY,ModulePhase.ERROR,ModulePhase.CANCELED
                        )) {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled=!stopPending,
                                    onClick={ModuleInstallOperations.pause()}
                                ){Text("Duraklat")}
                                OutlinedButton(
                                    enabled=!stopPending,
                                    onClick={ModuleInstallOperations.cancel()}
                                ){Text("İptal iste")}
                            }
                        }

                        TextButton(onClick={moduleDetails=!moduleDetails}) {
                            Text(if(moduleDetails)
                                "Teknik ayrıntıları gizle"
                                else "Teknik ayrıntıları göster")
                        }
                        if(moduleDetails) {
                            Text("Bağımlılıklar: "+
                                spec.dependencies.joinToString{
                                    OfflineModules.spec(it).title
                                }.ifBlank{"Yok"})
                            Text("Disk: "+(
                                spec.diskBytes?.let{
                                    "${it/1024/1024} MiB"
                                }?:"toplam bilinmiyor"
                            ))
                            aptState.takeIf{it.isNotBlank()}?.let{
                                Text(it,style=MaterialTheme.typography.bodySmall)
                            }
                            lifecycle?.let{lc->
                                Text(
                                    "Sahiplik: ${lc.ownedFiles} dosya • generation: ${lc.generation?:"yok"}"
                                )
                                OutlinedButton(
                                    enabled=!busy && !prefetchBusy && lc.remove.allowed,
                                    onClick={pendingLifecycle=selected to "remove"}
                                ){Text("Güvenli kaldır")}
                                OutlinedButton(
                                    enabled=!busy && !prefetchBusy && lc.rollback.allowed,
                                    onClick={pendingLifecycle=selected to "rollback"}
                                ){Text("Önceki doğrulanmış sürüme dön")}
                            }
                        }
                    }
                }
            }
            if(section=="network")item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("İndirme ağı",style=MaterialTheme.typography.titleMedium);Text(networkStatus)
                for(policy in DownloadNetworkPolicy.entries)OutlinedButton(enabled=!busy && !preparing,onClick={if(policy==DownloadNetworkPolicy.ALLOW_ANY)confirmAnyNetwork=true else {networkPolicy=policy;DownloadNetwork.select(policy)}}){Text(when(policy){DownloadNetworkPolicy.WIFI_ONLY->"Yalnız Wi-Fi";DownloadNetworkPolicy.WIFI_PREFERRED->"Wi-Fi tercih et; mobil veri için onay iste";DownloadNetworkPolicy.ALLOW_ANY->"Wi-Fi ve mobil veriye izin ver"})}
                Text("Varsayılan politika: ${DownloadNetwork.policy}. VPN veya ölçümlü Wi-Fi, kısıtlı politikada engellenir. Dış paket yöneticilerinde ağ geçişi kısıtı garanti edilemediğinden açık ağ izni gerekir.")
            }}}
            if(section=="diagnostics")item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Son işlem",style=MaterialTheme.typography.titleMedium)
                Text("Geçmiş kayıtlar komut veya kurulum yetkisi değildir. Dosya tamamlanması, modül Hazır ve proje Çevrimdışı Hazır ayrı doğrulanır.")
                val rootIds=diagnosticOperation?.let { operation ->
                    OfflineModuleId.entries.map { PersistentOperationGraph.moduleId(operation,it) }.toSet()
                }.orEmpty()
                val currentRoots=operationTree.filter { it.parentOperationId==null && it.operationId in rootIds }
                if(currentRoots.isEmpty())Text(if(busy)"İşlem hazırlanıyor…" else "Bu oturumda işlem kaydı yok.")
                currentRoots.asReversed().forEach{root->
                    Text("${OfflineModules.spec(root.moduleId).title} • ${root.operationState} • ${root.operationId.take(12)}")
                    operationTree.filter{it.parentOperationId==root.operationId}.takeLast(12).forEach{child->
                        Text("  ${child.stage} • ${child.operationState}"+(if(child.stage==OperationStage.ARTIFACT_DOWNLOAD)" • ${child.downloadCapability}" else ""))
                    }
                }
                Text("APT/npm/Gradle/pip/sdkmanager: kısıtlı ağda BLOCKED. Çevrimdışı bayrağı, alt süreç soketlerini engelleyen bir izolasyon değildir.")
            }}}
            if(section=="history")item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("İndirme geçmişi: ${artifacts.size}/128 kayıt")
                val eligible=PersistentDownloads.retainAfterUserCleanup(artifacts)
                val count=artifacts.size-eligible.size
                Text("Güvenli olarak bırakılabilecek doğrulanmamış kurtarma kaydı: $count")
                Button(enabled=!busy && !preparing && !stopPending && count>0,onClick={confirmHistoryCleanup=true}) {
                    Text("Eski kayıtları bırak • dosyaları koru")
                }
            }}}
            if(section=="history")item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Güncel indirmeler — modül başına tek durum",style=MaterialTheme.typography.titleMedium)
                Text("Görünen durum bir kurulum/READY kanıtı değildir. Her paketin doğrulanmış kaldığı yer arka planda ayrıca korunur.")
                val modules=artifacts.groupBy{it.moduleId}
                if(modules.isEmpty())Text("Bekleyen indirme kaydı yok.")
                OfflineModules.all.filter{it.id in modules}.forEach{spec->
                    val rows=modules.getValue(spec.id)
                    val latest=rows.maxByOrNull{it.updatedAt}!!
                    val verified=rows.sumOf{it.verifiedPartialBytes}
                    Text("${spec.title} • ${rows.size} bekleyen paket • son durum: ${latest.downloadState}")
                    Text("Doğrulanmış paket kontrol noktaları: ${verified/1024/1024} MiB (paketler arasında toplam yüzde hesaplanmaz)")
                }
                TextButton(onClick={historyDetails=!historyDetails}) {
                    Text(if(historyDetails)"Paket ayrıntılarını gizle" else "Paket ayrıntılarını göster")
                }
            }}}
            if(section=="history" && historyDetails)items(artifacts.takeLast(32).reversed(),key={"artifact-"+it.operationId+it.artifactId}){record->
                Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(OfflineModules.spec(record.moduleId).title,style=MaterialTheme.typography.titleMedium)
                    Text("Dosya: ${record.artifactId.take(16)} • ${record.downloadState}")
                    record.parentOperationId?.let{Text("Üst işlem: ${it.take(12)}")}
                    val live=DownloadCoordinator.snapshot(record.operationId).firstOrNull{it.artifactId==record.artifactId}?.progress?.takeIf{busy || preparing}
                    live?.let{event->
                        Text("${event.bytesDownloaded/1024/1024} MiB / "+(event.bytesTotal?.let{"${it/1024/1024} MiB • %${event.percent}"}?:"Boyut bilinmiyor"))
                        Text("Hız: "+(event.speedBytesPerSecond?.let{"%.1f MiB/sn".format(it/1024/1024)}?:"Hesaplanıyor")+" • Kalan: "+(event.estimatedRemainingMs?.let{"${it/1000} sn"}?:"Hesaplanıyor"))
                    }

                    Text("${record.verifiedPartialBytes/1024/1024} MiB doğrulanmış kısmi / "+(record.expectedBytes?.let{"${it/1024/1024} MiB"}?:"Boyut bilinmiyor"))
                    Text("Ağ: ${record.networkPolicy} • ${record.resumeCapability} • ${record.verificationState} • retry ${record.retryCount}")
                    record.failureReason?.let{Text("Hata: $it")}
                    if(record.downloadState==DownloadState.COMPLETED)Text("Dosya SHA-256 ile tamamlandı; modülün Hazır kontrolü ayrıdır.")
                    else {
                        Button(enabled=!busy && !preparing && record.stagingIdentity!=null && recovered && chosen==null,onClick={chosen=record.moduleId}){Text(if(record.downloadState==DownloadState.PAUSED)"Devam et • onayla" else "Güvenli yeniden dene • onayla")}
                        if(record.stagingIdentity==null)Text("Paket yöneticisi kaydı: aynı projeden tekrar açık kod çalıştırma onayı gerekir; dosya düzeyinde devam sunulmaz.")
                        OutlinedButton(enabled=!busy && !preparing && record.downloadState!=DownloadState.CANCELLED,onClick={scope.launch{runCatching{withContext(Dispatchers.IO){DownloadCoordinator.cancelRecord(record.operationId,record.artifactId)}}.onFailure{detail=it.message?:"Kayıt güncellenemedi"}}}){Text("Geçmiş kaydını iptal et • dosyaları koru")}
                    }
                }}
            }
            plan?.let{p->item{Text(p.error?:"Bu proje için: "+p.modules.joinToString{OfflineModules.spec(it).title})}}
            if(section=="diagnostics" && recovered)item {
                OutlinedButton(
                    enabled=!busy && !preparing && BuildModuleUsage.mutationBlockedReason()==null,
                    onClick={confirmPrune=true}
                ){Text("Eski sürümleri temizle")}
            }
            if(section=="modules" && project!=null && (!FastHtmlApkPolicy.isCandidate(project) || RustProjectPolicy.hasRust(project)))item { ProjectDependencyPreparationCard(project,onRustPrepared) }
            if(section=="modules" && selected==OfflineModuleId.WINDOWS_PORTABLE && portableReady)item { WindowsModuleSmokeCard() }
            item { Text("Kullanıcının başlattığı kurulum Android veri aktarımı servisiyle izlenir. Android zaman sınırı veya süreç ölümü işlemi durdurabilir. Yeniden açınca kayıt geri yüklenir; devam için onay gerekir, dosyalar yeniden doğrulanır.") }
            if(journalError.isNotBlank())item{Text(journalError)}
            if(section in setOf("diagnostics","history") && history.isNotEmpty())item{Column {Text("Son işlem kayıtları (hazır olma kanıtı değildir)");history.takeLast(8).forEach{Text(it)}}}
            if(detail.isNotBlank())item{Text(detail)}
            if(firstRun)item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("İlk açılışta araç indirme zorunluluğu yok. Fast HTML gömülüdür; diğer modüller ihtiyaç olduğunda kurulur.")
                Button(onClick={onFirstRunComplete?.invoke(true)}){Text("Araç indirmeden devam et")}}}
        }
    }
}


@Composable private fun WindowsModuleSmokeCard() {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var smoke by remember{mutableStateOf<java.io.File?>(null)}
    var busy by remember{mutableStateOf(false)}
    var detail by remember{mutableStateOf("")}
    val save=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        if(uri!=null && smoke?.isFile==true)scope.launch {
            detail=withContext(Dispatchers.IO){runCatching {context.contentResolver.openOutputStream(uri,"w")!!.use{output->smoke!!.inputStream().use{it.copyTo(output)}}}.fold({"Windows test dosyası kaydedildi; fiziksel Windows'ta açın."},{"Kaydetme hatası: ${it.message}"})}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(enabled=!busy && !ModuleInstallOperations.busy.value,onClick={busy=true;scope.launch {
            runCatching {WindowsPortableExePackager.createAcceptanceSmoke(context){}}.onSuccess{smoke=it;detail="Cihaz-local test EXE'si oluşturuldu; Windows çalışması henüz doğrulanmadı."}.onFailure{detail=it.message?:"EXE testi başarısız"};busy=false
        }}){Text("CİHAZ EXE KABUL DOSYASI OLUŞTUR")}
        if(smoke?.isFile==true)OutlinedButton(onClick={save.launch("AppForge-Windows-Smoke.exe")}){Text("WINDOWS TEST EXE'SİNİ KAYDET")}
        if(detail.isNotBlank())Text(detail)
    }
}

private fun ModulePhase.turkishLabel():String = when(this) {
    ModulePhase.RESOLVING -> "Bağımlılıklar hesaplanıyor"
    ModulePhase.STORAGE_CHECK -> "Depolama kontrol ediliyor"
    ModulePhase.AWAITING_CONSENT -> "Kullanıcı onayı bekleniyor"
    ModulePhase.CONNECTING -> "Bağlantı hazırlanıyor"
    ModulePhase.DOWNLOADING -> "İndiriliyor"
    ModulePhase.VERIFYING -> "Bütünlük doğrulanıyor"
    ModulePhase.EXTRACTING -> "Arşiv açılıyor"
    ModulePhase.INSTALLING -> "Kuruluyor"
    ModulePhase.PREPARING -> "Bağımlılıklar hazırlanıyor"
    ModulePhase.OFFLINE_TEST -> "Çevrimdışı kontrol ediliyor"
    ModulePhase.READY -> "Hazır"
    ModulePhase.REMOVING -> "Kaldırma doğrulanıyor"
    ModulePhase.REMOVED -> "Kaldırıldı"
    ModulePhase.ROLLING_BACK -> "Önceki generation'a dönülüyor"
    ModulePhase.PAUSED -> "Duraklatıldı"
    ModulePhase.WAITING_CONNECTION -> "Bağlantı bekleniyor; yeniden deneyin"
    ModulePhase.RECONNECTING -> "Yeniden bağlanıyor"
    ModulePhase.CANCELED -> "İptal edildi"
    ModulePhase.ERROR -> "Hata oluştu"
}

/** Compact navigation preserves operation state; network consent stays in its own section. */
@Composable private fun ModuleManagerNavigation(selected:String,onSelect:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("modules" to "Modüller","network" to "Ağ ayarları").forEach { (id,label) ->
                OutlinedButton(onClick={onSelect(id)},modifier=Modifier.weight(1f),enabled=selected!=id){Text(label)}
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("history" to "İndirme geçmişi","diagnostics" to "Gelişmiş / Tanılama").forEach { (id,label) ->
                OutlinedButton(onClick={onSelect(id)},modifier=Modifier.weight(1f),enabled=selected!=id){Text(label)}
            }
        }
    }
}
@Composable private fun ModuleCompactHeader(title:String,verified:Boolean,expanded:Boolean,onDetails:()->Unit) {
    Column {
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(if(verified)"Doğrulanmış kurulum" else "Kurulum doğrulanmadı",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick=onDetails,modifier=Modifier.heightIn(min=48.dp)){Text(if(expanded)"Ayrıntıları kapat" else "Ayrıntıları aç")}
    }
}
