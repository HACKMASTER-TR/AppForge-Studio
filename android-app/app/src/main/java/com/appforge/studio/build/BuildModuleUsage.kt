package com.appforge.studio.build

import java.io.Closeable

/** Non-blocking leases close the check/start race between builds and module mutations. */
internal object BuildModuleUsage {
    private var builds=0
    private var mutation=false
    @Synchronized fun buildLease():Closeable {
        check(!mutation){"Modül kurulumu/silme devam ediyor; derleme başlatılmadı."}
        builds++
        var closed=false
        return Closeable { synchronized(this){if(!closed){closed=true;builds--}} }
    }
    @Synchronized fun mutationLease():Closeable {
        check(builds==0 && !mutation){"Etkin derleme veya modül işlemi var; çalışan araçlar değiştirilmez."}
        mutation=true
        var closed=false
        return Closeable {synchronized(this){if(!closed){closed=true;mutation=false}}}
    }
}
