package com.appforge.studio.build

import android.content.Context
import android.os.Build
import com.appforge.studio.terminal.LinuxArchitecture
import com.appforge.studio.terminal.LinuxDistribution
import com.appforge.studio.terminal.LinuxRuntimeLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * Host-side verified content-addressed archives. Never mounts or hard-links
 * the central object into a guest; a guest may modify its own independent copy.
 * Migration is restricted to exact reviewed artifact pins in known host caches,
 * and requires the caller's exclusive runtime mutation lease.
 */
internal object R70SharedArchives {
    const val DIRECTORY="shared-tool-archives-v1"
    private const val MAX=2L*1024*1024*1024
    data class Pin(val name:String,val sha:String,val bytes:Long?=null)
    data class Migration(val moved:Int,val redundant:Int,val logicalRemoved:Long,
        val skipped:Int,val freeBefore:Long,val freeAfter:Long,val reflink:Boolean?,
        val planned:Int=0,val found:Int=0)
    fun directory(privateData:File,storeName:String=DIRECTORY):File {
        require(storeName in setOf(DIRECTORY,R72BrokerArchives.DIRECTORY))
        val home=RecoveryPathAnchor.androidPlatformPath(privateData)
        RecoveryPathAnchor.check(home)
        val root=File(home,storeName)
        RecoveryPathAnchor.check(root)
        require(root.isDirectory || root.mkdirs())
        return root
    }
    fun objectFile(root:File,sha:String):File {
        require(root.name in setOf(DIRECTORY,R72BrokerArchives.DIRECTORY) && sha.matches(Regex("[a-f0-9]{64}")))
        RecoveryPathAnchor.check(root)
        return File(root,"$sha.archive").also { RecoveryPathAnchor.check(it) }
    }
    private fun pins(app:Context):List<Pin> {
        val android=R68PinnedAndroidArchives.plan(
            app.assets.open("device-build/verified-toolchain-sources-v4.json").bufferedReader().use { it.readText() },
            listOf(OfflineModuleId.ANDROID_CORE,OfflineModuleId.EXPO_REACT_NATIVE,OfflineModuleId.RUST_ANDROID_NDK),
            Build.SUPPORTED_ABIS.contains("arm64-v8a")).map{Pin(it.name,it.sha256,it.bytes)}
        val language=listOf(OfflineModuleId.NODE_WEB,OfflineModuleId.RUST).flatMap { id ->
            IsolatedModuleInstallerV3.toolArchives(app,id).map { Pin(it.name,it.sha256) }
        }
        return (android+language).distinctBy{it.name}
    }
    /** Known directories only, not a recursive search, no guest/rootfs files. */
    fun migrateInstalled(app:Context,canceled:()->Unit={},progress:(Int,Int)->Unit={_,_->}):Migration {
        val home=RecoveryPathAnchor.androidPlatformPath(app.noBackupFilesDir)
        RecoveryPathAnchor.check(home)
        val arch=LinuxArchitecture.fromAndroidAbis(Build.SUPPORTED_ABIS.toList())
            ?:error("Unsupported architecture")
        val approved=pins(app)
        val sources=linkedMapOf<File,Pin>()
        for(id in OfflineModuleId.entries) {
            val namespace=File(home,"isolated-modules-v1/"+id.name.lowercase(java.util.Locale.ROOT))
            val downloads=File(namespace,"downloads")
            val linux=LinuxRuntimeLayout(File(namespace,"rootfs"),LinuxDistribution.UBUNTU,arch)
            val broker=File(linux.runtimeDirectory,".module-archive-cache-v5")
            for(pin in approved) {
                sources[File(downloads,pin.name)]=pin
                sources[File(broker,pin.sha+".archive")]=pin
            }
        }
        return migrate(home,sources,canceled,progress,probeReflink=true)
    }
    /** Only paths enumerated from reviewed pins by migrateInstalled are used in production. */
    internal fun migrate(home:File,sources:Map<File,Pin>,canceled:()->Unit={},
        progress:(Int,Int)->Unit={_,_->},probeReflink:Boolean=false,storeName:String=DIRECTORY):Migration {
        val root=directory(home,storeName)
        val base=RecoveryPathAnchor.androidPlatformPath(home)
        val before=root.usableSpace
        var moved=0;var redundant=0;var removed=0L;var skipped=0
        val candidates=sources.entries.filter{Files.exists(it.key.toPath(),LinkOption.NOFOLLOW_LINKS)}
        // No automatic cleanup of corrupt, partial, unknown, project or guest files.
        for((index,entry) in candidates.withIndex()) {
            canceled()
            val source=entry.key
            val pin=entry.value
            val normalized=source.absoluteFile.toPath()
            require(normalized.startsWith(File(base,"isolated-modules-v1").toPath()))
            require(source.name==pin.name || source.name==pin.sha+".archive")
            try { RecoveryPathAnchor.check(source) }
            catch(_:IllegalArgumentException){skipped++;progress(index+1,candidates.size);continue}
            if(!VerifiedArtifactCache.matches(source,pin.sha,pin.bytes,MAX)) {
                skipped++;progress(index+1,candidates.size);continue
            }
            val destination=objectFile(root,pin.sha)
            RuntimeMutationLock.acquire(root,".artifact-"+pin.sha+".lock").use {
                canceled()
                require(VerifiedArtifactCache.matches(source,pin.sha,pin.bytes,MAX))
                if(Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS)) {
                    // Never overwrite a damaged/unowned object. Retain the legacy copy.
                    if(!VerifiedArtifactCache.matches(destination,pin.sha,pin.bytes,MAX)) {
                        skipped++
                    } else {
                        RuntimeFileClone.sync(destination)
                        RuntimeFileClone.sync(root,true)
                        val size=source.length()
                        Files.delete(source.toPath())
                        RuntimeFileClone.sync(source.parentFile,true)
                        redundant++;removed=Math.addExact(removed,size)
                    }
                } else {
                    // Same-volume atomic rename, no extra disk space or destructive overwrite.
                    RuntimeFileClone.sync(source)
                    Files.move(source.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE)
                    require(VerifiedArtifactCache.matches(destination,pin.sha,pin.bytes,MAX))
                    RuntimeFileClone.sync(root,true)
                    RuntimeFileClone.sync(source.parentFile,true)
                    moved++
                }
            }
            progress(index+1,candidates.size)
        }
        canceled()
        val reflink=if(probeReflink)runCatching{RuntimeFileClone.supported(root)}.getOrNull() else null
        return Migration(moved,redundant,removed,skipped,before,root.usableSpace,reflink,sources.size,candidates.size)
    }
}
