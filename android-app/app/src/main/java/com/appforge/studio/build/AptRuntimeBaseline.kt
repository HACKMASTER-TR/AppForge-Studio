package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject
import com.appforge.studio.terminal.LinuxRootfsManifest

/** Only called immediately after a freshly verified vendor archive activation. No legacy blessing. */
internal object AptRuntimeBaseline {
    fun captureFresh(root:File,manifest:LinuxRootfsManifest) {
        require(manifest.release=="24.04.4")
        val path=File(root,"opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json")
        require(path.canonicalFile==path.absoluteFile && !path.exists())
        val status=File(root,"var/lib/dpkg/status")
        require(status.canonicalFile==status.absoluteFile && status.isFile)
        val packages=JSONObject()
        AptModuleLocks.installedVersions(status.readText()).forEach{(name,value)->packages.put(name,"${value.first} (${value.second})")}
        require(packages.length()>0)
        path.parentFile.mkdirs()
        val part=File(path.path+".part");require(!Files.isSymbolicLink(part.toPath()))
        part.writeText(JSONObject().put("schema",1).put("release",manifest.release).put("architecture",manifest.architecture.id).put("rootfsSha256",manifest.archiveSha256).put("source",manifest.archiveUrl).put("statusSha256",VerifiedModuleDownload.sha(status)).put("packages",packages).toString())
        Files.move(part.toPath(),path.toPath(),StandardCopyOption.ATOMIC_MOVE)
    }
}
