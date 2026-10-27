package com.appforge.studio.build

/** History is a bounded projection, not an installation or source authorization. */
internal object DownloadHistoryV84 {
    /** Operation ids are attempts; a pinned archive identity survives retries. */
    fun sameArtifact(a:ArtifactDownloadRecord,b:ArtifactDownloadRecord)=
        a.moduleId==b.moduleId && a.artifactId==b.artifactId &&
        a.sourceIdentityHash==b.sourceIdentityHash &&
        a.expectedSha256==b.expectedSha256 && a.stagingIdentity==b.stagingIdentity

    private fun completed(r:ArtifactDownloadRecord)=
        r.downloadState==DownloadState.COMPLETED && r.verificationState=="SHA256_VERIFIED"

    private fun safelyDiscardable(r:ArtifactDownloadRecord)=
        r.verifiedPartialBytes==0L && r.downloadedBytes==0L &&
        (r.downloadState in setOf(DownloadState.CANCELLED,DownloadState.FAILED) ||
            r.downloadState==DownloadState.RECOVERY_REQUIRED && r.verificationState=="RECHECK_REQUIRED")

    /** Deduplicates attempts of the *same* pinned archive/target. Never evicts a
     * verified partial or an in-flight transfer simply to meet a capacity limit.
     * Files and SHA sidecars are not modified by this metadata transform. */
    fun upsert(old:List<ArtifactDownloadRecord>,candidate:ArtifactDownloadRecord,limit:Int=128):List<ArtifactDownloadRecord> {
        require(limit in 1..128)
        val result=old.filterNot { sameArtifact(it,candidate) || completed(it) }.toMutableList()
        if(!completed(candidate))result.add(candidate)
        while(result.size>limit) {
            val oldest=result.withIndex().filter{ safelyDiscardable(it.value) }
                .minWithOrNull(compareBy<IndexedValue<ArtifactDownloadRecord>>{it.value.updatedAt}
                    .thenBy{it.value.createdAt}) ?:error("İndirme geçmişi dolu: korunması gereken aktif/kısmi kayıtlar var.")
            result.removeAt(oldest.index)
        }
        return result
    }

    /** Startup migration uses rehashed recover() results only. If multiple
     * attempts point to the same physical archive, prefer the greatest valid
     * checkpoint; use recency only when the verified byte counts tie. */
    fun compactRecovered(rows:List<ArtifactDownloadRecord>):List<ArtifactDownloadRecord> {
        val kept=mutableListOf<ArtifactDownloadRecord>()
        for(row in rows) {
            val i=kept.indexOfFirst{sameArtifact(it,row)}
            if(i<0){kept.add(row);continue}
            val prior=kept[i]
            if(row.verifiedPartialBytes>prior.verifiedPartialBytes ||
                row.verifiedPartialBytes==prior.verifiedPartialBytes && row.updatedAt>=prior.updatedAt)
                kept[i]=row
        }
        return kept
    }

    /** READY is emitted after driver validation; metadata can then be dropped.
     * This function never makes a module ready and does not touch its files. */
    fun afterReady(rows:List<ArtifactDownloadRecord>,module:OfflineModuleId)=
        rows.filterNot{it.moduleId==module}
}
