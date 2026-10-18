package com.appforge.studio.build

import java.io.File
import java.nio.file.*
import java.security.KeyStore
import java.security.cert.X509Certificate
import org.json.JSONObject

/** Bootstrap APT HTTPS from public Android system roots, retaining signature checks. */
internal object AndroidRuntimeTrust {
    private const val CERT="etc/ssl/certs/appforge-system-roots-v5.pem"
    private const val JAVA_CERT="etc/ssl/certs/appforge-system-roots-v5.p12"
    // This store contains public certificates only; its standard integrity password is not a credential.
    val shellEnvironment = "if [ -f /$JAVA_CERT ]; then export JAVA_TOOL_OPTIONS=\"\${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/$JAVA_CERT -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=changeit\"; fi; "
    private const val APT="etc/apt/apt.conf.d/99appforge-system-tls"
    private const val RECEIPT=".appforge-system-tls-v5.json"
    private fun file(root:File,path:String):File {
        require(!File(path).isAbsolute && path.split('/').none{it==".."})
        return File(root,path).also{require(it.parentFile.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && !Files.isSymbolicLink(it.toPath()))}
    }
    private fun write(target:File,bytes:ByteArray) {
        target.parentFile.mkdirs();val tmp=File(target.path+".part");require(!Files.isSymbolicLink(tmp.toPath()))
        java.io.FileOutputStream(tmp).use{it.write(bytes);it.fd.sync()}
        Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    private fun systemRoots():List<X509Certificate> {
        val store=KeyStore.getInstance("AndroidCAStore");store.load(null,null)
        return store.aliases().toList().filter{it.startsWith("system:")}.sorted().mapNotNull{store.getCertificate(it) as? X509Certificate}
    }
    fun install(root:File,certificates:List<X509Certificate> = systemRoots()) {
        require(certificates.isNotEmpty()){"Android sistem CA kökleri bulunamadı; TLS kapatılmadı."}
        val pem=certificates.joinToString("\n"){cert->"-----BEGIN CERTIFICATE-----\n"+java.util.Base64.getMimeEncoder(64,byteArrayOf(10)).encodeToString(cert.encoded)+"\n-----END CERTIFICATE-----\n"}
        require(pem.length<=16*1024*1024)
        write(file(root,CERT),pem.toByteArray())
        val javaStore=KeyStore.getInstance("PKCS12");javaStore.load(null,null)
        certificates.forEachIndexed{index,cert->javaStore.setCertificateEntry("system-$index",cert)}
        val publicStore=java.io.ByteArrayOutputStream().apply{javaStore.store(this,"changeit".toCharArray())}.toByteArray()
        write(file(root,JAVA_CERT),publicStore)
        write(file(root,APT),"Acquire::https::CaInfo \"/$CERT\";\nAcquire::https::Verify-Peer \"true\";\nAcquire::https::Verify-Host \"true\";\n".toByteArray())
        val tracked=mutableListOf(CERT,JAVA_CERT,APT)
        val sources=mutableListOf<File>();val traditional=file(root,"etc/apt/sources.list")
        if(traditional.isFile)sources.add(traditional)
        val directory=file(root,"etc/apt/sources.list.d")
        if(directory.isDirectory)sources.addAll(directory.listFiles().orEmpty().filter{it.extension in setOf("sources","list")})
        for(source in sources) {
            val relative=source.relativeTo(root).invariantSeparatorsPath;file(root,relative)
            require(source.isFile && source.length()<=1024*1024)
            val original=source.readText();var updated=original
            for(host in listOf("archive.ubuntu.com","security.ubuntu.com","ports.ubuntu.com","snapshot.ubuntu.com"))updated=updated.replace("http://$host/","https://$host/")
            if(updated!=original)write(source,updated.toByteArray())
            tracked.add(relative)
        }
        val hashes=JSONObject();tracked.forEach{hashes.put(it,VerifiedModuleDownload.sha(file(root,it)))}
        write(file(root,RECEIPT),JSONObject().put("schema",1).put("files",hashes).toString().toByteArray())
    }
    fun ready(root:File):Boolean=runCatching {
        val receipt=file(root,RECEIPT);require(receipt.isFile && receipt.length()<=65536)
        val json=JSONObject(receipt.readText());require(json.getInt("schema")==1)
        val files=json.getJSONObject("files");require(files.has(CERT) && files.has(JAVA_CERT) && files.has(APT) && files.length()<=100)
        files.keys().asSequence().all{path->val f=file(root,path);f.isFile && VerifiedModuleDownload.sha(f)==files.getString(path)}
    }.getOrDefault(false)
}
