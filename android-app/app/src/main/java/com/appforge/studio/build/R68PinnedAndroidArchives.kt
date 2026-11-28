package com.appforge.studio.build

import org.json.JSONArray
import java.net.URL

/**
 * Exact archive inputs consumed by the unchanged install-toolchain.sh.
 * Existing vendor pins, architecture and license admission are retained.
 * This catalogue does not claim completeness for Maven/npm dependency graphs.
 */
internal object R68PinnedAndroidArchives {
    data class Archive(val name:String,val url:String,val sha256:String,val bytes:Long?=null)

    fun plan(catalogue:String,order:List<OfflineModuleId>,arm64:Boolean):List<Archive> {
        if(OfflineModuleId.ANDROID_CORE !in order)return emptyList()
        val raw=JSONArray(catalogue)
        val records=buildMap<String,Archive> {
            for(i in 0 until raw.length()) {
                val item=raw.getJSONObject(i)
                val name=item.getString("file")
                val value=Archive(name,item.getString("url"),item.getString("sha256"),
                    if(item.isNull("bytes"))null else item.getLong("bytes"))
                require(put(name,value)==null) { "Duplicate pinned archive: $name" }
            }
        }
        val result=buildList {
            fun pinned(name:String) { add(requireNotNull(records[name]) { "Missing pin: $name" }) }
            pinned("platform-37.0_r02.zip")
            pinned("build-tools_r36_linux.zip")
            pinned("gradle-9.3.1-bin.zip")
            pinned("gradle-8.14.3-bin.zip")
            add(Archive("commandlinetools-linux-15859902_latest.zip",
                "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip",
                "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"))
            val arch=if(arm64)"aarch64" else "x86_64"
            val vendorArch=if(arm64)"aarch64" else "x64"
            add(Archive("temurin-jdk17-$arch.tar.gz",
                "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_"+vendorArch+"_linux_hotspot_17.0.20.1_1.tar.gz",
                if(arm64)"457b57af8f9c93ec39080bb8c764f559dc8c89a6da1a39d718a400b7890d3e41"
                else "3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e"))
            if(arm64) {
                val tools=mapOf(
                    "aapt2" to "7512ff7e381bea6fd310b6f6e347422c8fda21e07c6e3f0162742e95eb9d7f98",
                    "aidl" to "8c97356b8bba8f7aad44cfd408e3ee24c66a1258244b5d08af1c9249f50dd659",
                    "zipalign" to "e8856fb24b10095eb6e940c577ce96d89ddbfc45aa0f7eeaef1597ef68f11a12",
                    "split-select" to "fb7f0c3c87dbd4243d7e1277ba64ded2399389244058b6a4c969cc615360766c")
                for((tool,hash) in tools)
                    add(Archive("linux-glibc-arm64-36.0.0-$tool",
                        "https://github.com/Commit451/android-arm-build-tools/releases/download/platform-tools-36.0.0/$tool",hash))
            }
            if(OfflineModuleId.EXPO_REACT_NATIVE in order)pinned("platform-36_r02.zip")
            if(order.any { it==OfflineModuleId.RUST_ANDROID_NDK || it==OfflineModuleId.EXPO_REACT_NATIVE }) {
                pinned("android-ndk-r27b-linux.zip")
                if(!arm64)pinned("cmake-3.22.1-linux.zip")
            }
        }
        require(result.map { it.name }.distinct().size==result.size)
        for(archive in result) {
            require(archive.name.matches(Regex("[A-Za-z0-9_.-]+")))
            require(archive.sha256.matches(Regex("[a-f0-9]{64}")))
            require(archive.bytes==null || archive.bytes in 1..(2L*1024*1024*1024))
            require(ModuleArtifactBroker.allowed(URL(archive.url))) { "Unapproved pinned source" }
        }
        return result
    }
}
