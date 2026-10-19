package com.appforge.studio.build
import java.io.File
import org.json.JSONObject
/** Opt-in real-root read-only proof; run inside the isolated network-none Ubuntu test container. */
fun main(args:Array<String>) {
    val result=JSONObject()
    for(id in listOf(OfflineModuleId.ANDROID_CORE,OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.WINDOWS_NATIVE)) {
        val summary=AptModuleLocks.inspect(File("/"),id)
        require(summary.transactions>0)
        result.put(id.name,JSONObject().put("transactions",summary.transactions).put("packages",summary.packages).put("archiveBytes",summary.archiveBytes).put("detail",summary.detail))
    }
    File(args.single()).writeText(result.toString(2))
    println("PRODUCTION_KOTLIN_APT_LOCK_PREFLIGHT_PASS")
}
