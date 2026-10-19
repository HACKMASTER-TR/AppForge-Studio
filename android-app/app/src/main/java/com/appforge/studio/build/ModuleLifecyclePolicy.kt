package com.appforge.studio.build

internal enum class LifecycleSupport { SAFE_LIFECYCLE_SUPPORTED, LIFECYCLE_BLOCKED }
internal data class LifecyclePermission(val reason:String?) {
    val allowed:Boolean get()=reason==null
    val support:LifecycleSupport get()=if(allowed)LifecycleSupport.SAFE_LIFECYCLE_SUPPORTED else LifecycleSupport.LIFECYCLE_BLOCKED
}
internal data class ModuleLifecycleStatus(val update:LifecyclePermission,val remove:LifecyclePermission,val rollback:LifecyclePermission,
    val installedVersion:String?,val generation:String?,val previousVersion:String?,val dependents:Set<OfflineModuleId>,val ownedFiles:Int=0,val minimumStagingBytes:Long?=null)

/** UI and mutation entry points share these action-specific, fail-closed decisions. */
internal object ModuleLifecyclePolicy {
    data class Facts(val id:OfflineModuleId,val installed:Boolean,val trustedUpdate:Boolean=false,val ownershipError:String?="Sahiplik bütünlüğü kanıtı yok.",
        val removalError:String?="Bağımsız kaldırma kanıtı yok.",val rollbackError:String?="Doğrulanmış geri alma generation yok.",val busy:String?=null,val platformError:String?=null,
        val dependents:Set<OfflineModuleId> = emptySet(),val version:String?=null,val generation:String?=null,val previousVersion:String?=null,val ownedFiles:Int=0,val storageError:String?=null,val minimumStagingBytes:Long?=null,val updateError:String?=null)
    fun evaluate(f:Facts):ModuleLifecycleStatus {
        val embedded=if(f.id==OfflineModuleId.FAST_HTML)"Fast HTML uygulamaya gömülüdür; ayrı güncellenemez, kaldırılamaz veya geri alınamaz." else null
        val global=f.busy?:f.platformError?:embedded?:f.storageError
        val missing=if(!f.installed)"Kurulu ve doğrulanmış modül kaydı yok." else null
        val dependent=f.dependents.takeIf{it.isNotEmpty()}?.let{"Bağımlı modüller kullanıyor: "+it.joinToString{d->OfflineModules.spec(d).title}}
        val update=global?:missing?:f.ownershipError?:f.updateError?:if(!f.trustedUpdate)"Doğrulanmış yeni paket/tarif yok." else null
        val remove=global?:missing?:dependent?:f.ownershipError?:f.removalError
        val rollback=global?:missing?:dependent?:f.ownershipError?:f.rollbackError
        return ModuleLifecycleStatus(LifecyclePermission(update),LifecyclePermission(remove),LifecyclePermission(rollback),f.version,f.generation,f.previousVersion,f.dependents,f.ownedFiles,f.minimumStagingBytes)
    }
}
