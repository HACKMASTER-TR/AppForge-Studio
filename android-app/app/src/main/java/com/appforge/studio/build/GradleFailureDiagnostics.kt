package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Persist only safe error categories, never raw logs or URLs. */
internal object GradleFailureDiagnostics {
    fun classify(log: String): String = when {
        log.contains("PKIX",true) ||
            log.contains("certification path",true) ||
            log.contains("SSLHandshakeException",true) -> "TLS_TRUST"
        log.contains("checksum mismatch",true) ||
            log.contains("hash mismatch",true) ||
            log.contains("signature invalid",true) -> "INTEGRITY"
        log.contains("403 Forbidden",true) ||
            log.contains("401 Unauthorized",true) -> "HTTP_AUTH"
        log.contains("404 Not Found",true) -> "HTTP_NOT_FOUND"
        log.contains("429 Too Many Requests",true) -> "HTTP_RATE_LIMIT"
        log.contains("502 Bad Gateway",true) ||
            log.contains("503 Service Unavailable",true) ||
            log.contains("504 Gateway Timeout",true) -> "HTTP_UPSTREAM"
        log.contains("UnknownHostException",true) ||
            log.contains("name resolution",true) -> "DNS"
        log.contains("timed out",true) ||
            log.contains("TimeoutException",true) -> "NETWORK_TIMEOUT"
        log.contains("Connection reset",true) ||
            log.contains("ConnectException",true) -> "NETWORK_RESET"
        log.contains("Could not GET",true) ||
            log.contains("Could not HEAD",true) ||
            log.contains("Could not get resource",true) -> "RESOURCE_READ"
        else -> "UNKNOWN"
    }

    fun capture(
        privateRoot: File,
        workspace: File,
        id: OfflineModuleId
    ): String? {
        val kind=when(id) {
            OfflineModuleId.ANDROID_CORE -> "android"
            OfflineModuleId.PYTHON_ANDROID -> "python"
            else -> return null
        }

        val log=File(
            workspace,
            ".appforge-offline-prewarm/$kind-gradle.log"
        )

        if (!log.isFile ||
            Files.isSymbolicLink(log.toPath()) ||
            log.length() !in 1L..4194304L) return null

        val code=classify(log.readText())
        val root=privateRoot.canonicalFile
        val folder=File(root,"offline-module-v3")

        require(!Files.isSymbolicLink(folder.toPath()))
        if(!folder.exists())require(folder.mkdir())
        require(folder.canonicalFile.parentFile==root)

        val target=File(folder,"gradle-error-v1.json")
        val temp=File(folder,"gradle-error-v1.json.tmp")

        require(!Files.isSymbolicLink(target.toPath()))
        require(!Files.isSymbolicLink(temp.toPath()))

        val bytes=JSONObject()
            .put("schema",1)
            .put("module",id.name)
            .put("code",code)
            .put("timestampMs",System.currentTimeMillis())
            .toString().toByteArray(Charsets.UTF_8)

        require(bytes.size<=2048)

        FileOutputStream(temp).use {
            it.write(bytes)
            it.fd.sync()
        }

        Files.move(
            temp.toPath(),target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
        return code
    }
}
