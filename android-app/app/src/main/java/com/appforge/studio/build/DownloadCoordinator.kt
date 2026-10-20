package com.appforge.studio.build

/** Shared, bounded transfer observation. No entry in this registry can authorize installation. */
internal object DownloadCoordinator {
    enum class ResumeCapability { VERIFIED_HTTP_RANGE, PACKAGE_MANAGER_CHECKPOINT, SAFE_RESTART_ONLY }
    data class Transfer(
        val artifactId:String,val artifactVersion:String,val sourceIdentity:String,
        val progress:ModuleProgress,val resumeCapability:ResumeCapability,
        val verificationStatus:String="UNVERIFIED",val cancellationState:String="NONE"
    )
    fun withoutStaleRates(progress:ModuleProgress,ageMs:Long):ModuleProgress =
        if(ageMs>=5000 && progress.phase==ModulePhase.DOWNLOADING)progress.copy(
            speedBytesPerSecond=null,averageSpeedBytesPerSecond=null,estimatedRemainingMs=null)
        else progress
    val records=kotlinx.coroutines.flow.MutableStateFlow<List<ArtifactDownloadRecord>>(emptyList())
    private var journal:PersistentDownloads?=null
    val operations=kotlinx.coroutines.flow.MutableStateFlow<List<DownloadOperationNode>>(emptyList())
    private fun saveGraph(rows:List<DownloadOperationNode>) {
        val store=journal?:return
        val bounded=PersistentOperationGraph.pruneRetained(rows)
        store.operationGraph().save(bounded);operations.value=bounded
    }
    private fun upsertGraph(node:DownloadOperationNode) {
        saveGraph(operations.value.filterNot{it.operationId==node.operationId}+node)
    }
    private fun ensureModule(operation:String,module:OfflineModuleId):String {
        val id=PersistentOperationGraph.moduleId(operation,module)
        if(operations.value.none{it.operationId==id}) {
            val now=System.currentTimeMillis()
            upsertGraph(DownloadOperationNode(id,null,module,OperationStage.MODULE,DownloadState.QUEUED,DownloadNetwork.policy,now,now))
        }
        return id
    }
    /** Phase events come from the real driver. READY is only a historical observation. */
    @Synchronized fun modulePhase(event:ModuleProgress) {
        if(journal==null)return
        val parent=ensureModule(event.operationId,event.moduleId)
        val stage=when(event.phase){
            ModulePhase.RESOLVING->OperationStage.DEPENDENCY_RESOLUTION
            ModulePhase.VERIFYING->OperationStage.INTEGRITY_VERIFICATION
            ModulePhase.EXTRACTING,ModulePhase.PREPARING->OperationStage.CACHE_PREPARATION
            ModulePhase.INSTALLING->OperationStage.INSTALLATION
            ModulePhase.OFFLINE_TEST->OperationStage.OFFLINE_PROBE
            ModulePhase.REMOVING,ModulePhase.REMOVED->OperationStage.REMOVAL
            ModulePhase.ROLLING_BACK->OperationStage.ROLLBACK
            else->OperationStage.MODULE
        }
        val state=when(event.phase){
            ModulePhase.READY,ModulePhase.REMOVED->DownloadState.COMPLETED
            ModulePhase.ERROR->DownloadState.FAILED
            ModulePhase.CANCELED->DownloadState.CANCELLED
            ModulePhase.PAUSED->DownloadState.PAUSED
            ModulePhase.WAITING_CONNECTION,ModulePhase.RECONNECTING->DownloadState.WAITING_FOR_NETWORK
            ModulePhase.AWAITING_CONSENT->DownloadState.WAITING_FOR_CONSENT
            ModulePhase.INSTALLING->DownloadState.INSTALLING
            ModulePhase.DOWNLOADING->DownloadState.DOWNLOADING
            else->DownloadState.PREPARING
        }
        val root=operations.value.single{it.operationId==parent}
        val now=maxOf(System.currentTimeMillis(),root.updatedAt)
        val rows=operations.value.toMutableList()
        rows.removeAll{it.operationId==parent}
        rows.add(root.copy(operationState=state,updatedAt=now,historyClosed=false,failureReason=if(state==DownloadState.FAILED)"DRIVER_FAILED" else null))
        if(stage!=OperationStage.MODULE) {
            val id=PersistentOperationGraph.id(parent,stage.name)
            val old=rows.firstOrNull{it.operationId==id}
            rows.removeAll{it.operationId==id}
            rows.add(DownloadOperationNode(id,parent,event.moduleId,stage,state,root.networkPolicy,old?.createdAt?:now,now))
        }
        if(state==DownloadState.COMPLETED) {
            val children=rows.filter{it.parentOperationId==parent}
            val blocked=setOf(DownloadState.RECOVERY_REQUIRED,DownloadState.PAUSED,DownloadState.PAUSE_REQUESTED,
                DownloadState.FAILED,DownloadState.CANCELLED,DownloadState.WAITING_FOR_NETWORK,DownloadState.WAITING_FOR_CONSENT)
            val canClose=children.all{n->
                if(n.stage==OperationStage.ARTIFACT_DOWNLOAD)n.operationState==DownloadState.COMPLETED &&
                    n.expectedSha256!=null && n.expectedBytes!=null && n.verifiedBytes==n.expectedBytes
                else n.operationState !in blocked
            }
            if(canClose) {
                // Only the driver's verified terminal phase closes visited stages.
                // One atomic graph write prevents a closed parent/open child crash gap.
                rows.replaceAll{n->when {
                    n.operationId==parent->n.copy(historyClosed=true)
                    n.parentOperationId==parent && n.stage!=OperationStage.ARTIFACT_DOWNLOAD->n.copy(operationState=DownloadState.COMPLETED,updatedAt=now)
                    else->n
                }}
            }
        }
        saveGraph(rows)
    }
    private val operationParents=mutableMapOf<String,String>()
    @Synchronized fun adoptArtifacts(childOperation:String,parentOperation:String,module:OfflineModuleId) {
        if(journal==null || childOperation==parentOperation)return
        val parent=ensureModule(parentOperation,module)
        val key=childOperation+":"+module.name
        val previous=operationParents[key];require(previous==null || previous==parentOperation)
        operationParents[key]=parentOperation
        val rows=records.value.map{if(it.operationId==childOperation && it.moduleId==module)it.copy(parentOperationId=parent)else it}
        journal!!.save(rows);records.value=rows
        rows.filter{it.operationId==childOperation && it.moduleId==module}.forEach(::syncArtifact)
    }
    @Synchronized fun reconcileModules(events:Map<OfflineModuleId,ModuleProgress>) {
        if(journal==null)return
        // Module and artifact files are independently atomic, never a single transaction.
        // Reopening always requires real module validation and explicit source re-authorization.
        for((module,event) in events) {
            val parent=ensureModule(event.operationId,module)
            val old=operations.value.single{it.operationId==parent}
            if(old.historyClosed)continue // Historical graph closure does not authorize any install.
            upsertGraph(old.copy(operationState=DownloadState.RECOVERY_REQUIRED,updatedAt=maxOf(old.updatedAt,System.currentTimeMillis()),failureReason="MODULE_JOURNAL_RECONCILIATION"))
        }
    }
    private fun syncArtifact(row:ArtifactDownloadRecord) {
        val parent=row.parentOperationId?:ensureModule(row.operationId,row.moduleId)
        if(operations.value.none{it.operationId==parent})ensureModule(row.operationId,row.moduleId)
        upsertGraph(journal!!.operationGraph().artifact(row,parent))
    }
    private val retryCounts=mutableMapOf<String,Int>()
    private val targets=mutableMapOf<String,String>()
    @Synchronized fun configure(store:PersistentDownloads) {
        if(journal!=null)return
        val recovered=store.recover();val tree=store.operationGraph().recover(recovered)
        records.value=recovered;operations.value=tree;journal=store
    }
    @Synchronized fun bind(operation:String,artifact:String,target:java.io.File):Int {
        journal?.let{targets[operation+":"+artifact]=it.staging(target)}
        val count=records.value.filter{it.artifactId==artifact && it.stagingIdentity==targets[operation+":"+artifact] && it.downloadState!=DownloadState.COMPLETED}.maxOfOrNull{it.retryCount}?:0
        retryCounts[operation+":"+artifact]=count
        return count
    }
    @Synchronized fun retry(operation:String,artifact:String,count:Int) {
        retryCounts[operation+":"+artifact]=count
        val rows=records.value.map{if(it.operationId==operation && it.artifactId==artifact)it.copy(retryCount=count,updatedAt=System.currentTimeMillis())else it}
        journal?.save(rows);records.value=rows
        rows.filter{it.operationId==operation && it.artifactId==artifact}.forEach{if(journal!=null)syncArtifact(it)}
    }
    @Synchronized fun prefixCheckpoint(operation:String,artifact:String,bytes:Long) {
        val rows=records.value.map{if(it.operationId==operation && it.artifactId==artifact)it.copy(downloadedBytes=maxOf(it.downloadedBytes,bytes),verifiedPartialBytes=bytes,verificationState="PREFIX_VERIFIED",updatedAt=maxOf(it.updatedAt,System.currentTimeMillis()))else it}
        journal?.save(rows);records.value=rows
        rows.filter{it.operationId==operation && it.artifactId==artifact}.forEach{if(journal!=null)syncArtifact(it)}
    }
    @Synchronized fun cancelRecord(operation:String,artifact:String) {
        check(active.isEmpty()){"Aktif aktarımda uygulama kontrolünü kullanın."}
        val rows=records.value.map{if(it.operationId==operation && it.artifactId==artifact)it.copy(downloadState=DownloadState.CANCELLED,updatedAt=System.currentTimeMillis())else it}
        journal?.save(rows);records.value=rows
        rows.filter{it.operationId==operation && it.artifactId==artifact}.forEach{if(journal!=null)syncArtifact(it)}
    }
    private var persistedAt=0L
    private fun persist(value:Transfer) {
        val store=journal?:return
        val p=value.progress
        fun digest(text:String)=java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
        val artifact=value.artifactId.takeIf{it.matches(Regex("[a-f0-9]{64}"))}?:digest(value.artifactId)
        val source=value.sourceIdentity.takeIf{it.matches(Regex("[a-f0-9]{64}"))}?:digest(value.sourceIdentity)
        val old=records.value.firstOrNull{it.operationId==p.operationId && it.artifactId==artifact}
        val now=maxOf(System.currentTimeMillis(),old?.updatedAt?:0)
        val state=when(p.phase) {
            ModulePhase.RESOLVING,ModulePhase.CONNECTING,ModulePhase.STORAGE_CHECK->DownloadState.QUEUED
            ModulePhase.DOWNLOADING->DownloadState.DOWNLOADING
            ModulePhase.VERIFYING->DownloadState.VERIFYING
            ModulePhase.PAUSED->DownloadState.PAUSED
            ModulePhase.CANCELED->DownloadState.CANCELLED
            ModulePhase.ERROR->DownloadState.FAILED
            ModulePhase.WAITING_CONNECTION,ModulePhase.RECONNECTING->DownloadState.WAITING_FOR_NETWORK
            ModulePhase.AWAITING_CONSENT->DownloadState.WAITING_FOR_CONSENT
            ModulePhase.INSTALLING->DownloadState.INSTALLING
            else->if(value.verificationStatus=="SHA256_VERIFIED")DownloadState.COMPLETED else DownloadState.PREPARING
        }
        if(old?.downloadState==state && now-persistedAt<1000)return
        val bound=targets[p.operationId+":"+value.artifactId]
        // External observers have no pinned source identity; journal them as observation-only records.
        val completedBytes=if(value.verificationStatus=="SHA256_VERIFIED" && bound!=null)p.bytesTotal?:old?.expectedBytes?:p.bytesDownloaded else p.bytesDownloaded
        val row=ArtifactDownloadRecord(p.operationId,p.moduleId,artifact,value.artifactVersion.takeIf{it.matches(Regex("[A-Za-z0-9._+-]{1,100}"))}?:"observed",source,if(bound!=null)artifact else null,p.bytesTotal?:old?.expectedBytes,if(value.verificationStatus=="SHA256_VERIFIED")completedBytes else minOf(old?.verifiedPartialBytes?:0,p.bytesDownloaded),state,value.resumeCapability,DownloadNetwork.policy,retryCounts[p.operationId+":"+artifact]?:old?.retryCount?:0,old?.createdAt?:now,now,if(value.verificationStatus=="SHA256_VERIFIED")"SHA256_VERIFIED" else "UNVERIFIED",bound,p.errorCode?.uppercase()?.replace(Regex("[^A-Z0-9_]"),"_")?.take(100),PersistentOperationGraph.moduleId(operationParents[p.operationId+":"+p.moduleId.name]?:p.operationId,p.moduleId),completedBytes)
        val rows=records.value.filterNot{it.operationId==row.operationId && it.artifactId==row.artifactId}.toMutableList()
        if(rows.size>=128){val removable=rows.indexOfFirst{it.downloadState in setOf(DownloadState.COMPLETED,DownloadState.CANCELLED,DownloadState.FAILED)};check(removable>=0){"İndirme geçmişi dolu"};rows.removeAt(removable)}
        rows.add(row);store.save(rows);records.value=rows;syncArtifact(row);persistedAt=now
    }
    private val observedAt=mutableMapOf<String,Long>()
    private val transfers=linkedMapOf<String,Transfer>()
    private val active=hashSetOf<String>()
    @Synchronized fun claim(target:String){check(active.add(target)){"Bu hedef için bir aktarım zaten çalışıyor."}}
    @Synchronized fun release(target:String){active.remove(target)}
    @Synchronized fun observe(value:Transfer) {
        persist(value)
        val key=value.progress.operationId+":"+value.sourceIdentity+":"+value.artifactId
        if(key !in transfers && transfers.size>=256){val oldest=transfers.keys.first();transfers.remove(oldest);observedAt.remove(oldest)}
        observedAt[key]=System.nanoTime()/1_000_000
        val stopped=value.progress.phase!=ModulePhase.DOWNLOADING
        transfers[key]=if(stopped)value.copy(progress=value.progress.copy(speedBytesPerSecond=null,
            averageSpeedBytesPerSecond=null,estimatedRemainingMs=null))else value
    }
    @Synchronized fun snapshot(operation:String)=transfers.entries.filter{it.value.progress.operationId==operation}.map{(key,value)->value.copy(progress=withoutStaleRates(value.progress,System.nanoTime()/1_000_000-(observedAt[key]?:0)))}
    /** Unknown totals make the aggregate indeterminate; duplicate source artifacts count once. */
    @Synchronized fun aggregate(operation:String):Pair<Long,Long?> {
        val observed=snapshot(operation)
        val rows=observed.filter{it.resumeCapability==ResumeCapability.VERIFIED_HTTP_RANGE}.distinctBy{it.artifactId}
        val bytes=rows.sumOf{it.progress.bytesDownloaded}
        val total=if(rows.size!=observed.distinctBy{it.artifactId}.size || rows.isEmpty() || rows.any{it.progress.bytesTotal==null})null else rows.sumOf{it.progress.bytesTotal!!}
        return bytes to total
    }
}
