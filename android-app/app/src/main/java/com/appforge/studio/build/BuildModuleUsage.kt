package com.appforge.studio.build

import java.io.Closeable

/** Non-blocking leases close the check/start race between builds and module mutations. */
internal object BuildModuleUsage {
    private var builds=0
    private var mutation=false
    private var preparation:PreparationPermit?=null
    internal class PreparationPermit internal constructor():Closeable {
        override fun close(){synchronized(BuildModuleUsage){if(preparation===this)preparation=null}}
    }
    @Synchronized fun preparationLease():PreparationPermit {
        check(builds==0 && !mutation && preparation==null){"Başka derleme/modül işlemi var; proje hazırlığı başlamadı."}
        return PreparationPermit().also{preparation=it}
    }
    @Synchronized fun buildLease(permit:PreparationPermit?=null):Closeable {
        if(permit!=null){check(preparation===permit);return Closeable{}}
        check(!mutation && preparation==null){"Modül kurulumu/silme devam ediyor; derleme başlatılmadı."}
        builds++
        var closed=false
        return Closeable { synchronized(this){if(!closed){closed=true;builds--}} }
    }
    @Synchronized fun mutationLease():Closeable {
        check(builds==0 && !mutation && preparation==null){"Etkin derleme veya modül işlemi var; çalışan araçlar değiştirilmez."}
        mutation=true
        var closed=false
        return Closeable {synchronized(this){if(!closed){closed=true;mutation=false}}}
    }
}
