package com.appforge.studio.build

/** Measures per-operation wall time on the Android monotonic clock; no timestamps or paths are persisted.
 * Durations are diagnostic only and never authorize a module's READY state.
 */
internal class ModuleElapsedTracker {
    private data class Clock(
        val operation:String,
        var phase:ModulePhase,
        var lastMs:Long,
        var elapsed:Long=0L,
        var downloading:Long=0L,
        var verifying:Long=0L,
        var installing:Long=0L
    )
    private val clocks=mutableMapOf<OfflineModuleId,Clock>()
    private val stopped=setOf(ModulePhase.READY,ModulePhase.ERROR,ModulePhase.CANCELED,
        ModulePhase.REMOVED,ModulePhase.PAUSED)

    @Synchronized fun observe(event:ModuleProgress, nowMs:Long):ModuleProgress {
        require(nowMs>=0)
        val previous=clocks[event.moduleId]
        val clock=if(previous==null || previous.operation!=event.operationId) {
            Clock(event.operationId,event.phase,nowMs).also{clocks[event.moduleId]=it}
        } else previous
        val difference=(nowMs-clock.lastMs).coerceAtLeast(0L)
        if(clock.phase !in stopped) {
            clock.elapsed+=difference
            when(clock.phase) {
                ModulePhase.DOWNLOADING -> clock.downloading+=difference
                ModulePhase.VERIFYING,ModulePhase.OFFLINE_TEST -> clock.verifying+=difference
                ModulePhase.INSTALLING,ModulePhase.EXTRACTING,ModulePhase.PREPARING -> clock.installing+=difference
                else -> Unit
            }
        }
        clock.phase=event.phase
        clock.lastMs=nowMs
        return event.copy(elapsedMs=clock.elapsed,downloadElapsedMs=clock.downloading,
            verificationElapsedMs=clock.verifying,installationElapsedMs=clock.installing)
    }
}
