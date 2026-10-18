package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft

internal enum class OfflineModuleId { FAST_HTML, LINUX_RUNTIME, ANDROID_CORE, NODE_WEB, PYTHON_ANDROID, EXPO_REACT_NATIVE, WINDOWS_PORTABLE, WINDOWS_NATIVE }
internal data class OfflineModuleSpec(val id: OfflineModuleId,val title: String,val version: String,val dependencies:Set<OfflineModuleId>,val source: String,val downloadBytes:Long?=null,val diskBytes:Long?=null)
internal data class OfflineModulePlan(val modules:List<OfflineModuleId>,val error:String?=null)
internal object OfflineModules {
    val all=listOf(
        OfflineModuleSpec(OfflineModuleId.FAST_HTML,"Fast HTML","CI APK/AAB 26/37",emptySet(),"Gömülü ve SHA-256 doğrulanan şablonlar",0,0),
        OfflineModuleSpec(OfflineModuleId.LINUX_RUNTIME,"Linux derleme ortamı","Device Build Runtime V3",emptySet(),"LinuxRuntimeManifestRegistry / doğrulanmış Ubuntu"),
        OfflineModuleSpec(OfflineModuleId.ANDROID_CORE,"Android SDK + JDK + Gradle","API 37.0 / Build Tools 36 / JDK 17.0.20.1 / Gradle 9.3.1 + 8.14.3",setOf(OfflineModuleId.LINUX_RUNTIME),"Google / Adoptium / services.gradle.org"),
        OfflineModuleSpec(OfflineModuleId.NODE_WEB,"Node.js + npm","22.23.3",setOf(OfflineModuleId.LINUX_RUNTIME),"nodejs.org / sabit SHA-256"),
        OfflineModuleSpec(OfflineModuleId.PYTHON_ANDROID,"Python + Chaquopy","Python 3.12 / Chaquopy 17.0.0",setOf(OfflineModuleId.ANDROID_CORE),"Ubuntu APT + Maven; proje bağımlılıkları ayrıca doğrulanır"),
        OfflineModuleSpec(OfflineModuleId.EXPO_REACT_NATIVE,"Expo / React Native","SDK 54 / RN 0.81 / NDK 27.1.12297006 / CMake 3.22.1",setOf(OfflineModuleId.ANDROID_CORE,OfflineModuleId.NODE_WEB),"Google SDK / Ubuntu ARM64 host / npm proje kilidi"),
        OfflineModuleSpec(OfflineModuleId.WINDOWS_PORTABLE,"Windows Portable Host","windows-host-v1-c7e4b2a",emptySet(),"GitHub doğrulanmış host",375039759L,375039759L),
        OfflineModuleSpec(OfflineModuleId.WINDOWS_NATIVE,"Windows Native C/C++ • EXPERIMENTAL","windows-native-v1 / Ubuntu çözümlemesi",setOf(OfflineModuleId.LINUX_RUNTIME),"Ubuntu MinGW-w64 POSIX / CMake / Ninja; EXPERIMENTAL")
    )
    fun spec(id:OfflineModuleId)=all.single{it.id==id}
    fun resolve(selected:Set<OfflineModuleId>):List<OfflineModuleId> {
        val result=linkedSetOf<OfflineModuleId>();val active=HashSet<OfflineModuleId>()
        fun visit(id:OfflineModuleId){if(id in result)return;check(active.add(id)){"Modül bağımlılık döngüsü"};spec(id).dependencies.forEach{visit(it)};active.remove(id);result.add(id)}
        selected.forEach{visit(it)};return result.toList()
    }
    fun forProject(d:ProjectDraft):OfflineModulePlan {
        val conflicts=FeatureSupportMatrix.conflicts(d)
        if(conflicts.isNotEmpty())return OfflineModulePlan(emptyList(),conflicts.joinToString("\n"){it.second})
        val outputs=runCatching{DeviceBuildCapabilities.requestedOutputs(d.buildOutput)}.getOrElse{return OfflineModulePlan(emptyList(),"Desteklenmeyen çıktı; kurulum gereksinimi değildir.")}
        if(FastHtmlApkPolicy.isCandidate(d))return OfflineModulePlan(listOf(OfflineModuleId.FAST_HTML))
        val wanted=linkedSetOf<OfflineModuleId>()
        val android=outputs.any{it==DeviceArtifactKind.APK||it==DeviceArtifactKind.AAB}
        val engine=d.sourceBuildEngine.trim().lowercase().ifBlank{"webview-static"}
        if(android) {
            if(engine !in setOf("webview-static","node-web","android-gradle","python-android","expo","react-native","android-ndk","universal-cross-platform"))
                return OfflineModulePlan(emptyList(),"Bu kaynak motoru desteklenmiyor; paket eksikliği değildir: $engine")
            wanted.add(OfflineModuleId.ANDROID_CORE)
        }
        if(engine=="node-web" && (android||DeviceArtifactKind.WINDOWS_EXE in outputs))wanted.add(OfflineModuleId.NODE_WEB)
        if(engine=="python-android"&&android)wanted.add(OfflineModuleId.PYTHON_ANDROID)
        if(engine in setOf("expo","react-native")&&android)wanted.add(OfflineModuleId.EXPO_REACT_NATIVE)
        if(DeviceArtifactKind.WINDOWS_EXE in outputs)wanted.add(OfflineModuleId.WINDOWS_PORTABLE)
        if(DeviceArtifactKind.WINDOWS_NATIVE_EXE in outputs)wanted.add(OfflineModuleId.WINDOWS_NATIVE)
        return OfflineModulePlan(resolve(wanted))
    }
}

internal enum class ModulePhase { RESOLVING, STORAGE_CHECK, AWAITING_CONSENT, CONNECTING, DOWNLOADING, VERIFYING, EXTRACTING, INSTALLING, PREPARING, OFFLINE_TEST, READY, PAUSED, WAITING_CONNECTION, RECONNECTING, CANCELED, ERROR }
internal data class ModuleProgress(val moduleId:OfflineModuleId,val operationId:String,val phase:ModulePhase,val bytesDownloaded:Long=0,val bytesTotal:Long?=null,val speedBytesPerSecond:Double?=null,val elapsedMs:Long=0,val estimatedRemainingMs:Long?=null,val errorCode:String?=null,val detail:String="",val averageSpeedBytesPerSecond:Double?=null) {
    val percent:Int? get()=bytesTotal?.takeIf{it>0}?.let{((bytesDownloaded.coerceIn(0,it).toDouble()/it)*100).toInt()}
}
/** Monotonic-clock byte telemetry only. A completed download is not a READY module. */
internal class DownloadMeasurement(private val module:OfflineModuleId,private val operation:String,private val startedMs:Long,private val initialBytes:Long=0) {
    private var previousMs=startedMs;private var previousBytes=initialBytes;private var smoothed:Double?=null
    fun sample(bytes:Long,total:Long?,nowMs:Long):ModuleProgress {
        require(bytes>=0 && nowMs>=previousMs && bytes>=previousBytes && (total==null||total>0 && bytes<=total))
        val moved=bytes>previousBytes
        val dt=nowMs-previousMs
        if(dt>0){val instant=(bytes-previousBytes)*1000.0/dt;smoothed=smoothed?.let{it*0.7+instant*0.3}?:instant}
        previousMs=nowMs;previousBytes=bytes
        val speed=smoothed?.takeIf{moved && it>0 && it.isFinite()}
        val eta=if(total!=null && speed!=null)((total-bytes)*1000.0/speed).toLong() else null
        return ModuleProgress(module,operation,ModulePhase.DOWNLOADING,bytes,total,speed,nowMs-startedMs,eta,averageSpeedBytesPerSecond=if(nowMs>startedMs && bytes>initialBytes)(bytes-initialBytes)*1000.0/(nowMs-startedMs) else null)
    }
}
