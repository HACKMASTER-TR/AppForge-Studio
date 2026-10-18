package com.appforge.studio.build

import android.content.Context
import java.io.File
import java.nio.file.Files

internal object DownloadInstrumentation {
    fun install(context:Context,root:File) {
        val target=File(root,"root/.gradle-appforge/init.d/appforge-resource-bytes.gradle")
        require(target.parentFile.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        target.parentFile.mkdirs()
        require(!Files.isSymbolicLink(target.toPath()) && target.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        val bytes=context.assets.open("device-build/gradle-download-telemetry.init.gradle").use{it.readBytes()}
        val bin=File(root,"opt/appforge-device/telemetry-bin")
        require(bin.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        bin.mkdirs()
        val apt=File(bin,"apt-get");require(!Files.isSymbolicLink(apt.toPath()))
        val aptContent="#!/bin/sh\n. /workspace/runtime/apt-download-telemetry.sh\nappforge_apt_get \"\$@\"\n"
        val aptTmp=File(bin,"apt-get.tmp-${java.util.UUID.randomUUID()}")
        aptTmp.writeText(aptContent);check(aptTmp.setExecutable(true,false))
        Files.move(aptTmp.toPath(),apt.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        if(target.isFile && target.readBytes().contentEquals(bytes))return
        val tmp=File(target.path+".tmp-${java.util.UUID.randomUUID()}");tmp.writeBytes(bytes)
        Files.move(tmp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
