package com.appforge.studio.build
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
internal object AptPhaseProgress {
    fun poll(workspace:File,module:OfflineModuleId,operation:String):ModuleProgress? {
        val file=File(workspace,"apt-phase.json");if(!file.exists())return null
        require(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.length()<=4096)
        val j=JSONObject(file.readText());require(j.getInt("schema")==1 && j.getString("module")==module.name)
        val phase=ModulePhase.valueOf(j.getString("phase"));require(phase in setOf(ModulePhase.RESOLVING,ModulePhase.DOWNLOADING,ModulePhase.VERIFYING,ModulePhase.INSTALLING,ModulePhase.OFFLINE_TEST))
        if(phase==ModulePhase.DOWNLOADING)return null
        return ModuleProgress(module,operation,phase,detail=j.getString("detail").take(1000))
    }
}
