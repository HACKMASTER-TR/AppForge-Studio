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
    private val transfers=linkedMapOf<String,Transfer>()
    private val active=hashSetOf<String>()
    @Synchronized fun claim(target:String){check(active.add(target)){"Bu hedef için bir aktarım zaten çalışıyor."}}
    @Synchronized fun release(target:String){active.remove(target)}
    @Synchronized fun observe(value:Transfer) {
        val key=value.progress.operationId+":"+value.sourceIdentity+":"+value.artifactId
        if(key !in transfers && transfers.size>=256)transfers.remove(transfers.keys.first())
        val stopped=value.progress.phase!=ModulePhase.DOWNLOADING
        transfers[key]=if(stopped)value.copy(progress=value.progress.copy(speedBytesPerSecond=null,
            averageSpeedBytesPerSecond=null,estimatedRemainingMs=null))else value
    }
    @Synchronized fun snapshot(operation:String)=transfers.values.filter{it.progress.operationId==operation}.toList()
    /** Unknown totals make the aggregate indeterminate; duplicate source artifacts count once. */
    @Synchronized fun aggregate(operation:String):Pair<Long,Long?> {
        val observed=snapshot(operation)
        val rows=observed.filter{it.resumeCapability==ResumeCapability.VERIFIED_HTTP_RANGE}.distinctBy{it.artifactId}
        val bytes=rows.sumOf{it.progress.bytesDownloaded}
        val total=if(rows.size!=observed.distinctBy{it.artifactId}.size || rows.isEmpty() || rows.any{it.progress.bytesTotal==null})null else rows.sumOf{it.progress.bytesTotal!!}
        return bytes to total
    }
}
