package com.appforge.studio.build

import android.content.Context
import android.os.Build
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRuntimeLayout
import java.io.File

/**
 * R71 physical report: three exact host transport objects shared by seven modules.
 * Not package authorization: guest GPG/Ed25519, SHA and length checks stay unchanged.
 * No guest files, installed tools, APT lists, snapshots or project files are changed.
 */
internal object R72BrokerArchives {
    const val DIRECTORY="shared-r2-broker-archives-v1"
    internal val reviewed=setOf(
        "adb1c01115c3b11d2a5cde43b17ce3c53fa7cc48f834b930f63910d2c7dc74f5",
        "0a2216096deac1ac53cf030da9b91f33de396e44228db781c7899cc3c900ce3e",
        "1ea1410b138d0980dfda152adc183b1a95054d54943291767523d5cb1ca1478e")
    fun target(sharedPinnedCache:File?,sha:String,isR2:Boolean):File? {
        if(!isR2 || sha !in reviewed || sharedPinnedCache==null)return null
        require(sharedPinnedCache.name==R70SharedArchives.DIRECTORY)
        val home=requireNotNull(sharedPinnedCache.parentFile)
        return R70SharedArchives.objectFile(R70SharedArchives.directory(home,DIRECTORY),sha)
    }
    fun migrateInstalled(app:Context,canceled:()->Unit={},progress:(Int,Int)->Unit={_,_->}):R70SharedArchives.Migration {
        val home=RecoveryPathAnchor.androidPlatformPath(app.noBackupFilesDir)
        RecoveryPathAnchor.check(home)
        val arch=LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList()) ?:error("Unsupported architecture")
        val sources=linkedMapOf<File,R70SharedArchives.Pin>()
        for(id in OfflineModuleId.entries) {
            val namespace=File(home,"isolated-modules-v1/"+id.name.lowercase(java.util.Locale.ROOT))
            val layout=LinuxRuntimeLayout(File(namespace,"rootfs"),LinuxDistribution.UBUNTU,arch)
            val broker=File(layout.runtimeDirectory,".module-archive-cache-v5")
            for(sha in reviewed) {
                val name="$"+"sha.archive"
                sources[File(broker,name)]=R70SharedArchives.Pin(name,sha)
            }
        }
        return R70SharedArchives.migrate(home,sources,canceled,progress,storeName=DIRECTORY)
    }
}
