package com.appforge.studio.build

import java.io.File
import java.net.*
import java.nio.file.*
import kotlinx.coroutines.*

/** Pinned archive requests contain no host paths. The native cache survives staging rollback. */
internal class ModuleArtifactBroker(private val workspace:File,private val cache:File,private val module:OfflineModuleId,private val operation:String,private val progress:(ModuleProgress)->Unit,private val open:(URL)->HttpURLConnection={RedirectConnection(it)}) {
    @Volatile var transferring=false;private set
    companion object {
        internal fun allowed(url:URL):Boolean {
            if(url.protocol!="https" || url.userInfo!=null || url.ref!=null || url.query!=null || url.port !in setOf(-1,443))return false
            return when(url.host.lowercase()) {
                "dl.google.com"->url.path.startsWith("/android/repository/")
                "ports.ubuntu.com"->url.path.startsWith("/ubuntu-ports/")
                "archive.ubuntu.com","security.ubuntu.com"->url.path.startsWith("/ubuntu/")
                "snapshot.ubuntu.com"->url.path.startsWith("/ubuntu/")
                "nodejs.org"->url.path.startsWith("/dist/")
                "services.gradle.org","downloads.gradle.org"->url.path.startsWith("/distributions/")
                "github.com"->listOf("/adoptium/temurin17-binaries/releases/download/","/Commit451/android-arm-build-tools/releases/download/","/gradle/gradle-distributions/releases/download/").any{url.path.startsWith(it)}
                else->false
            }
        }
        private fun redirectAllowed(url:URL)=allowed(url) || url.protocol=="https" && url.userInfo==null && url.ref==null && url.port in setOf(-1,443) && url.host=="release-assets.githubusercontent.com"
        internal class RedirectConnection(address:URL,private val opener:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection}):HttpURLConnection(address) {
            private var actual:HttpURLConnection?=null
            private fun response():HttpURLConnection {
                actual?.let{return it}
                var destination=url
                repeat(8) {
                    require(if(it==0)allowed(destination) else redirectAllowed(destination)){"Arşiv yönlendirme kaynağı izinli değil."}
                    val connection=opener(destination)
                    connection.instanceFollowRedirects=false;connection.connectTimeout=connectTimeout;connection.readTimeout=readTimeout
                    requestProperties.forEach{(key,values)->values.forEach{connection.addRequestProperty(key,it)}}
                    val status=connection.responseCode
                    if(status in setOf(301,302,303,307,308)) {
                        val location=connection.getHeaderField("Location")?:error("Yönlendirme konumu yok")
                        destination=URL(destination,location);connection.disconnect()
                    } else {actual=connection;return connection}
                }
                error("Arşiv yönlendirme sınırı")
            }
            override fun getResponseCode()=response().responseCode
            override fun getHeaderField(name:String?)=response().getHeaderField(name)
            override fun getContentLengthLong()=response().contentLengthLong
            override fun getInputStream()=response().inputStream
            override fun connect(){response()}
            override fun disconnect(){actual?.disconnect()}
            override fun usingProxy()=actual?.usingProxy()?:false
        }
    }
    suspend fun serve() {
        val directory=File(workspace,"native-downloads");require(directory.canonicalFile.toPath().startsWith(workspace.canonicalFile.toPath()));directory.mkdirs()
        while(currentCoroutineContext().isActive) {
            for(request in directory.listFiles().orEmpty().filter{it.name.matches(Regex("[a-f0-9]{64}\\.request"))}) {
                currentCoroutineContext().ensureActive()
                require(!Files.isSymbolicLink(request.toPath()) && request.length()<=4096)
                val id=request.name.removeSuffix(".request")
                try {
                    val fields=request.readText().trimEnd('\n').split('\t');require(fields.size==2)
                    val url=URL(fields[0]);require(allowed(url));val sha=fields[1];require(sha.matches(Regex("[a-f0-9]{64}")))
                    transferring=true
                    val component=url.path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._+-]"),"_").take(150)
                    progress(ModuleProgress(module,operation,ModulePhase.CONNECTING,detail="Doğrulanmış native aktarım • $component"))
                    val job=currentCoroutineContext()[Job]!!
                    val target=File(cache,"$sha.archive")
                    VerifiedModuleDownload.download(url.toString(),sha,target,module,operation,canceled={!job.isActive},open=open,progress={progress(it.copy(detail="Doğrulanmış native aktarım • $component • "+it.detail))},maxBytes=2L*1024*1024*1024)
                    job.ensureActive();require(directory.usableSpace>target.length()+64L*1024*1024){"Arşiv aktarımı için boş alan yetersiz."}
                    val result=File(directory,"$id.archive");require(!Files.isSymbolicLink(result.toPath()))
                    val part=File(directory,"$id.archive.part");require(!Files.isSymbolicLink(part.toPath()))
                    target.inputStream().use{input->java.io.FileOutputStream(part).use{output->val buffer=ByteArray(65536);while(true){job.ensureActive();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n)};output.fd.sync()}}
                    require(VerifiedModuleDownload.sha(part)==sha)
                    Files.move(part.toPath(),result.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                    progress(ModuleProgress(module,operation,ModulePhase.PREPARING,detail="Doğrulanmış native arşiv • $component • aktarım bitti; kurulum sürüyor."))
                    val done=File(directory,"$id.ok");require(!Files.isSymbolicLink(done.toPath()));done.writeText("SHA256_VERIFIED")
                }catch(e:CancellationException){throw e}
                catch(e:Exception){val error=File(directory,"$id.error");require(!Files.isSymbolicLink(error.toPath()));error.writeText("Doğrulanmış arşiv aktarılamadı: "+(e.message?:e.javaClass.simpleName).take(200))}
                finally{transferring=false;Files.deleteIfExists(request.toPath())}
            }
            delay(100)
        }
    }
}
