package com.appforge.studio.build

import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/** Admission only: never deletes, recovers, or changes readiness. Counts links without following them. */
internal object RuntimeStorageBudget {
    const val RESERVE = 256L * 1024 * 1024
    data class Usage(val logicalBytes:Long, val allocatedBytes:Long?, val files:Long)
    data class Report(val active:Usage, val rollback:Usage, val quarantine:Usage,
                      val unknown:Usage, val cache:Usage, val available:Long,
                      val required:Long, val blockedReason:String?, val retainedCopies:Int)
    fun measure(root:File, canceled:()->Unit={}):Usage {
        if(!Files.exists(root.toPath(),LinkOption.NOFOLLOW_LINKS))return Usage(0,0,0)
        RecoveryPathAnchor.check(root)
        var bytes=0L;var allocated=0L;var complete=true;var files=0L
        Files.walkFileTree(root.toPath(),object:SimpleFileVisitor<Path>() {
            private fun add(path:Path,attrs:BasicFileAttributes) {
                canceled();require(++files<=1000000){"Depolama envanteri sınırı; dosyalar korundu."}
                if(attrs.isRegularFile)bytes=Math.addExact(bytes,attrs.size())
                val blocks=RuntimeFileClone.allocatedBytes(path.toString())
                if(blocks<0)complete=false else allocated=Math.addExact(allocated,blocks)
            }
            override fun preVisitDirectory(dir:Path,attrs:BasicFileAttributes):FileVisitResult {add(dir,attrs);return FileVisitResult.CONTINUE}
            override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {add(file,attrs);return FileVisitResult.CONTINUE}
        })
        return Usage(bytes,if(complete)allocated else null,files)
    }
    private fun sum(items:List<Usage>)=Usage(items.sumOf{it.logicalBytes},
        if(items.all{it.allocatedBytes!=null})items.sumOf{it.allocatedBytes!!} else null,items.sumOf{it.files})
    fun inspect(active:File, expansion:Long=0, available:Long=requireNotNull(active.absoluteFile.parentFile).usableSpace,
                canceled:()->Unit={}):Report {
        require(expansion>=0){"Kurulumun genişleme boyutu bilinmiyor; işlem başlamadı."}
        RecoveryPathAnchor.check(active)
        val parent=requireNotNull(active.absoluteFile.parentFile)
        val root=measure(active,canceled)
        val children=parent.listFiles()?.toList()?:error("Depolama envanteri okunamadı")
        val rollback=children.filter{it.name.startsWith(".v3-previous-") || it.name=="restore-points"}
        val quarantine=children.filter{it.name.startsWith(".v3-discard-")}
        val stages=children.filter{it.name.startsWith(".v3-stage-") || it.name.startsWith(".clone-probe-") || it.name=="staging" && it.listFiles()?.isNotEmpty()!=false}
        val cache=children.filter{it.name==".module-archive-cache-v5"}
        val unknown=children.filter{it.name.startsWith(".v3-") && it !in rollback && it !in quarantine && it !in stages && it.isDirectory}
        val protected=ModuleOwnershipStore(active).protectedSnapshots().toMutableSet()
        val latest=File(active,"opt/appforge-device/generations-v4/latest.json")
        if(latest.isFile){RecoveryPathAnchor.check(latest);require(latest.length()<=4L*1024*1024);protected.add(org.json.JSONObject(latest.readText()).getString("previous"))}
        val obsolete=rollback.filter{it.name.startsWith(".v3-previous-") && it.name !in protected}
        val oldBudgetExceeded=obsolete.size>=16 || obsolete.sumOf{measure(it,canceled).logicalBytes}>RESERVE
        val required=Math.addExact(Math.addExact(root.logicalBytes,expansion),RESERVE)
        // A retained failed/stale transaction must be reviewed before creating another copy.
        // This also bounds legacy installations without claiming ownership of their contents.
        val reason=when {
            stages.isNotEmpty()->"Yarım kalmış staging korunuyor; kurtarma/inceleme gerekli."
            quarantine.isNotEmpty()->"Karantina bütçesi dolu; korunan kopyaları inceleyip güvenli temizliği onaylayın."
            unknown.isNotEmpty()->"Sahipliği belirsiz kopyalar korunuyor; yeni kurulum engellendi."
            oldBudgetExceeded->"Eski sürüm bütçesi dolu; korunan geri alma sürümleri dışındaki kopyaları inceleyin."
            available<=required->"Depolama bütçesi yetersiz; çalışan modüller ve çevrimdışı işlemler kullanılabilir."
            else->null
        }
        return Report(root,sum(rollback.map{measure(it,canceled)}),sum(quarantine.map{measure(it,canceled)}),
            sum((unknown+stages).map{measure(it,canceled)}),sum(cache.map{measure(it,canceled)}),available,required,reason,rollback.size)
    }
    // Explicit maximum transaction reservations, not measured download sizes or claimed savings.
    fun expansionLimit(id:OfflineModuleId):Long = when(id) {
        OfflineModuleId.FAST_HTML->0L
        OfflineModuleId.NODE_WEB->2L
        OfflineModuleId.RUST,OfflineModuleId.PYTHON_ANDROID->4L
        OfflineModuleId.LINUX_RUNTIME,OfflineModuleId.WINDOWS_NATIVE->8L
        OfflineModuleId.RUST_ANDROID_NDK->12L
        OfflineModuleId.ANDROID_CORE,OfflineModuleId.EXPO_REACT_NATIVE->16L
        OfflineModuleId.WINDOWS_PORTABLE->1L
    } * 1024 * 1024 * 1024
    fun requireAdmission(active:File, expansion:Long=0, available:Long=requireNotNull(active.absoluteFile.parentFile).usableSpace) {
        // Admission does not hash/crawl historical trees. An unreadable retained tree still blocks safely.
        RecoveryPathAnchor.check(active)
        val children=requireNotNull(active.absoluteFile.parentFile).listFiles()?:error("Depolama envanteri okunamadı")
        require(children.none{it.name.startsWith(".v3-stage-") || it.name.startsWith(".v3-discard-") || it.name.startsWith(".clone-probe-") || it.name=="staging" && it.listFiles()?.isNotEmpty()!=false}) {
            "STORAGE_BUDGET: Korunan staging/karantina var. Disk alanı panosundan inceleyin; mevcut modüller kullanılabilir."
        }
        require(children.none{it.name.startsWith(".v3-") && it.isDirectory && !it.name.startsWith(".v3-previous-")}){"STORAGE_BUDGET: Sahipsiz/şüpheli runtime konumu korunuyor."}
        val protected=ModuleOwnershipStore(active).protectedSnapshots().toMutableSet()
        val latest=File(active,"opt/appforge-device/generations-v4/latest.json")
        if(latest.isFile){RecoveryPathAnchor.check(latest);require(latest.length()<=4L*1024*1024);protected.add(org.json.JSONObject(latest.readText()).getString("previous"))}
        val obsolete=children.filter{it.name.startsWith(".v3-previous-") && it.name !in protected}
        require(obsolete.size<16 && obsolete.sumOf{measure(it).logicalBytes}<=RESERVE){"STORAGE_BUDGET: Eski sürüm bütçesi dolu; onaylı temizlik gerekli."}
        val required=Math.addExact(Math.addExact(measure(active).logicalBytes,expansion.also{require(it>=0)}),RESERVE)
        require(available>required){"STORAGE_BUDGET: Kullanılabilir $available bayt; gerekli $required bayt. Aktif ortam, cache ve geri alma korunur."}
    }
}
