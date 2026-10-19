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
    private val retryCounts=mutableMapOf<String,Int>()
    private val targets=mutableMapOf<String,String>()
    @Synchronized fun configure(store:PersistentDownloads) {
        if(journal!=null)return
        val recovered=store.recover();records.value=recovered;journal=store
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
    }
    @Synchronized fun prefixCheckpoint(operation:String,artifact:String,bytes:Long) {
        val rows=records.value.map{if(it.operationId==operation && it.artifactId==artifact)it.copy(verifiedPartialBytes=bytes,verificationState="PREFIX_VERIFIED",updatedAt=maxOf(it.updatedAt,System.currentTimeMillis()))else it}
        journal?.save(rows);records.value=rows
    }
    @Synchronized fun cancelRecord(operation:String,artifact:String) {
        check(active.isEmpty()){"Aktif aktarımda uygulama kontrolünü kullanın."}
        val rows=records.value.map{if(it.operationId==operation && it.artifactId==artifact)it.copy(downloadState=DownloadState.CANCELLED,updatedAt=System.currentTimeMillis())else it}
        journal?.save(rows);records.value=rows
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
        val row=ArtifactDownloadRecord(p.operationId,p.moduleId,artifact,value.artifactVersion.takeIf{it.matches(Regex("[A-Za-z0-9._+-]{1,100}"))}?:"observed",source,if(bound!=null)artifact else null,p.bytesTotal?:old?.expectedBytes,minOf(old?.verifiedPartialBytes?:0,p.bytesDownloaded),state,value.resumeCapability,DownloadNetwork.policy,retryCounts[p.operationId+":"+artifact]?:old?.retryCount?:0,old?.createdAt?:now,now,if(value.verificationStatus=="SHA256_VERIFIED")"SHA256_VERIFIED" else "UNVERIFIED",bound,p.errorCode?.uppercase()?.replace(Regex("[^A-Z0-9_]"),"_")?.take(100))
        val rows=records.value.filterNot{it.operationId==row.operationId && it.artifactId==row.artifactId}.toMutableList()
        if(rows.size>=128){val removable=rows.indexOfFirst{it.downloadState in setOf(DownloadState.COMPLETED,DownloadState.CANCELLED,DownloadState.FAILED)};check(removable>=0){"İndirme geçmişi dolu"};rows.removeAt(removable)}
        rows.add(row);store.save(rows);records.value=rows;persistedAt=now
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
