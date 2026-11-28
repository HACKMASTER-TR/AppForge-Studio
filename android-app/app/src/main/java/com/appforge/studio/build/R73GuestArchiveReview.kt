package com.appforge.studio.build

import android.content.Context
import android.os.Build
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRuntimeLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Exact R71-observed guest paths. This is an audit, NOT authorization to remove or reseal. */
internal object R73GuestArchiveReview {
    private val androidNames=listOf("temurin-jdk17-aarch64.tar.gz","commandlinetools-linux-15859902_latest.zip",
        "gradle-8.14.3-bin.zip","gradle-9.3.1-bin.zip","platform-37.0_r02.zip","build-tools_r36_linux.zip")
    private val ndk="android-ndk-r27b-linux.zip"
    private val rust="rustc-1.85.1-aarch64-unknown-linux-gnu.tar.xz"
    private val androidOwners=listOf(OfflineModuleId.ANDROID_CORE,OfflineModuleId.EXPO_REACT_NATIVE,
        OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.RUST_ANDROID_NDK)
    internal fun plannedNames():Map<OfflineModuleId,List<String>> =
        (androidOwners+OfflineModuleId.RUST).associateWith { id ->
            buildList {
                if(id in androidOwners)addAll(androidNames)
                if(id in setOf(OfflineModuleId.EXPO_REACT_NATIVE,OfflineModuleId.RUST_ANDROID_NDK))add(ndk)
                if(id in setOf(OfflineModuleId.RUST,OfflineModuleId.RUST_ANDROID_NDK))add(rust)
            }
        }
    data class Copy(val name:String,val state:String,val bytes:Long)
    data class Module(val id:OfflineModuleId,val ready:Boolean,val seal:String,val copies:List<Copy>)
    data class Report(val modules:List<Module>) {
        val planned get()=modules.sumOf{it.copies.size}
        val verifiedBytes get()=modules.sumOf{m->m.copies.filter{it.state=="MATCH"}.sumOf{it.bytes}}
        val sealedBytes get()=modules.filter{it.ready && it.seal=="PASS"}.sumOf{m->m.copies.filter{it.state=="MATCH"}.sumOf{it.bytes}}
    }
    internal fun inspectCopy(guest:File,central:File,pin:R70SharedArchives.Pin):Copy {
        fun result(state:String,bytes:Long=0)=Copy(pin.name,state,bytes)
        return try {
            RecoveryPathAnchor.check(guest);RecoveryPathAnchor.check(central)
            if(!Files.exists(guest.toPath(),LinkOption.NOFOLLOW_LINKS))result("GUEST_MISSING")
            else if(!VerifiedArtifactCache.matches(guest,pin.sha,pin.bytes,2L*1024*1024*1024))result("GUEST_MISMATCH")
            else if(!Files.exists(central.toPath(),LinkOption.NOFOLLOW_LINKS))result("CENTRAL_MISSING")
            else if(!VerifiedArtifactCache.matches(central,pin.sha,guest.length(),2L*1024*1024*1024))result("CENTRAL_MISMATCH")
            else if(Files.isSameFile(guest.toPath(),central.toPath()))result("SAME_INODE")
            else result("MATCH",guest.length())
        } catch(_:Exception) { result("UNSAFE_OR_UNREADABLE") }
    }
    fun review(app:Context,canceled:()->Unit={},progress:(Int,Int)->Unit={_,_->}):Report {
        val home=RecoveryPathAnchor.androidPlatformPath(app.noBackupFilesDir)
        RecoveryPathAnchor.check(home)
        require(LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList())==LinuxArchitecture.ARM64){"R73_ARM64_ONLY"}
        val pins=R70SharedArchives.pins(app).associateBy{it.name}
        val plan=plannedNames()
        val modules=mutableListOf<Module>()
        for((id,names) in plan) {
            canceled()
            val root=LinuxRuntimeLayout(File(home,"isolated-modules-v1/"+id.name.lowercase(java.util.Locale.ROOT)+"/rootfs"),
                LinuxDistribution.UBUNTU,LinuxArchitecture.ARM64).rootfsDirectory
            val copies=names.map { name ->
                canceled()
                val pin=requireNotNull(pins[name]){"R73_PIN_MISSING:"+name}
                inspectCopy(File(root,"opt/appforge-device/cache/"+name),
                    File(home,R70SharedArchives.DIRECTORY+"/"+pin.sha+".archive"),pin)
            }
            canceled()
            val safeRoot=try { RecoveryPathAnchor.check(root);root.isDirectory } catch(_:Exception){false}
            val ready=safeRoot && IsolatedModuleInstallerV3.ready(app,id)
            val seal=if(!safeRoot)"ROOT_UNAVAILABLE" else if(!ready)"NOT_READY" else try {
                RuntimeGenerationStore(root).reviewArchiveSeal(canceled)
                "PASS"
            } catch(e:kotlinx.coroutines.CancellationException){throw e}
              catch(_:Exception){"BLOCKED"}
            canceled()
            modules.add(Module(id,ready,seal,copies))
            progress(modules.size,plan.size)
        }
        return Report(modules)
    }
}
