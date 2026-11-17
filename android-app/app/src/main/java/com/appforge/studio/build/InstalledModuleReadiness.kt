package com.appforge.studio.build

/** One read-only inspection per module per refresh. No journal phase can grant readiness. */
internal object InstalledModuleReadiness {
    fun <T> inspect(
        modules: List<T>,
        dependencies: (T) -> Set<T>,
        verified: (T) -> Boolean,
        pending: (T) -> Boolean
    ): Map<T, Boolean> {
        val result = linkedMapOf<T, Boolean>()
        val visiting = hashSetOf<T>()
        fun visit(id: T): Boolean {
            result[id]?.let { return it }
            if (!visiting.add(id)) return false
            val ready = try {
                !pending(id) && verified(id) && dependencies(id).all { visit(it) }
            } catch (_: Exception) { false }
            finally { visiting.remove(id) }
            result[id] = ready
            return ready
        }
        modules.forEach { visit(it) }
        return result
    }
}

/** Process-scoped UI snapshot only. Build/installation authorization always checks disk.
 *  Never persists READY across process death, reinstall or application restart.
 */
internal object InstalledReadinessPresentationCache {
    data class Snapshot(
        val ready:Map<OfflineModuleId,Boolean>,
        val existingFiles:Set<OfflineModuleId>,
        val downloadedModules:Set<OfflineModuleId>,
        val mutationRevision:Long,
        val eventReadiness:Map<OfflineModuleId,Boolean>
    )
    @Volatile private var snapshot:Snapshot?=null
    fun peek():Snapshot?=snapshot
    @Synchronized fun cached(mutationRevision:Long,eventReadiness:Map<OfflineModuleId,Boolean>):Snapshot? =
        snapshot?.takeIf{it.mutationRevision==mutationRevision && it.eventReadiness==eventReadiness}
    @Synchronized fun publish(value:Snapshot){snapshot=value}
    @Synchronized fun reset(){snapshot=null}
}
