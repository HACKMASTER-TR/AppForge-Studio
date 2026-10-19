package com.appforge.studio.build

import java.io.Closeable

/** Non-blocking leases close the check/start race between builds and module mutations. */
internal object BuildModuleUsage {
    private val revisions=kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes:kotlinx.coroutines.flow.StateFlow<Long> get()=revisions
    private fun changed(){revisions.value=revisions.value+1}
    private var builds=0
    private var mutation=false
    private var preparation:PreparationPermit?=null
    internal class PreparationPermit internal constructor():Closeable {
        override fun close(){synchronized(BuildModuleUsage){if(preparation===this){preparation=null;changed()}}}
    }
    @Synchronized fun preparationLease():PreparationPermit {
        check(builds==0 && !mutation && preparation==null){"Başka derleme/modül işlemi var; proje hazırlığı başlamadı."}
        return PreparationPermit().also{preparation=it;changed()}
    }
    @Synchronized fun buildLease(permit:PreparationPermit?=null):Closeable {
        if(permit!=null){check(preparation===permit);return Closeable{}}
        check(!mutation && preparation==null){"Modül kurulumu/silme devam ediyor; derleme başlatılmadı."}
        builds++;changed()
        var closed=false
        return Closeable { synchronized(this){if(!closed){closed=true;builds--;changed()}} }
    }
    @Synchronized fun mutationBlockedReason():String?=when {
        builds>0->"Etkin derleme araçları kullanıyor."
        preparation!=null->"Proje bağımlılık hazırlığı devam ediyor."
        mutation->"Başka modül yaşam döngüsü işlemi devam ediyor."
        else->null
    }
    @Synchronized fun mutationLease():Closeable {
        check(builds==0 && !mutation && preparation==null){"Etkin derleme veya modül işlemi var; çalışan araçlar değiştirilmez."}
        mutation=true;changed()
        var closed=false
        return Closeable {synchronized(this){if(!closed){closed=true;mutation=false;changed()}}}
    }
}
