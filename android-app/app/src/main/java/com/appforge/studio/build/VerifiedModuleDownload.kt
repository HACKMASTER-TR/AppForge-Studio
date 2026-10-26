package com.appforge.studio.build

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Pinned HTTPS downloads: partial files never count as a verified artifact. */
internal object VerifiedModuleDownload {
    fun sha(file:File):String=MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;update(b,0,n)}}
        digest().joinToString(""){"%02x".format(it)}
    }
    private class TransientHttp(val status:Int,val retryAfterMs:Long?):java.io.IOException("HTTP $status")
    fun download(url:String,expected:String,target:File,module:OfflineModuleId,operation:String,
        canceled:()->Boolean={false}, clock:()->Long={System.nanoTime()/1_000_000},
        open:(URL)->HttpURLConnection={DownloadNetwork.open(it)},
        progress:(ModuleProgress)->Unit={}, availableBytes:(File)->Long={it.usableSpace},maxBytes:Long=1024L*1024*1024,
        waitForRetry:(Long)->Unit={Thread.sleep(it)}, maxRetries:Int=3) {
        require(maxRetries in 0..5)
        DownloadNetwork.prepareJournal()
        val requestPolicy=DownloadNetwork.policy
        // Normalize only the verified Android system alias, not arbitrary links.
        val nativeTarget=RecoveryPathAnchor.androidPlatformPath(target)
        RecoveryPathAnchor.check(nativeTarget)
        val key=nativeTarget.canonicalPath
        DownloadCoordinator.claim(key)
        val identity=MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        val report:(ModuleProgress)->Unit={ event ->
            DownloadCoordinator.observe(DownloadCoordinator.Transfer(expected,expected,identity,event,
                DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE))
            progress(event)
        }
        try {
            var attempt=DownloadCoordinator.bind(operation,expected,nativeTarget)
            require(attempt<=maxRetries){"Kalıcı yeniden deneme sınırı doldu; otomatik devam engellendi."}
            report(ModuleProgress(module,operation,ModulePhase.CONNECTING,detail="Sabit kaynaklı dosya kaydı kuyruğa alındı; henüz aktarım tamamlanmadı."))
            while(true) {
                try {
                    DownloadNetwork.withPolicy(requestPolicy){transfer(url,expected,nativeTarget,module,operation,canceled,clock,{address->DownloadNetwork.check(requestPolicy);open(address)},report,availableBytes,maxBytes,requestPolicy)}
                    val last=DownloadCoordinator.snapshot(operation).lastOrNull{it.artifactId==expected && it.sourceIdentity==identity}
                    DownloadCoordinator.observe(DownloadCoordinator.Transfer(expected,expected,identity,
                        last?.progress?.copy(phase=ModulePhase.PREPARING,bytesDownloaded=nativeTarget.length(),bytesTotal=nativeTarget.length())?:ModuleProgress(module,operation,ModulePhase.PREPARING,bytesDownloaded=nativeTarget.length(),bytesTotal=nativeTarget.length()),
                        DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE,verificationStatus="SHA256_VERIFIED"))
                    return
                }
                catch(e:java.io.IOException) {
                    // TLS trust/identity failures are permanent. Never retry integrity or policy failures.
                    if(e is javax.net.ssl.SSLException || e !is TransientHttp && e !is java.net.SocketException && e !is java.net.SocketTimeoutException && e !is java.net.UnknownHostException && e !is java.io.EOFException || attempt>=maxRetries || canceled())throw e
                    DownloadNetwork.check(requestPolicy)
                    val delay=(e as? TransientHttp)?.retryAfterMs ?: (1000L shl attempt)
                    if(delay>60000L)throw e // do not retry earlier than the server's requested delay
                    attempt++
                    DownloadCoordinator.retry(operation,expected,attempt)
                    report(ModuleProgress(module,operation,ModulePhase.RECONNECTING,errorCode="TRANSFER_RETRY",detail="Geçici aktarım hatası; güvenli yeniden deneme $attempt/$maxRetries"))
                    var remaining=delay
                    while(remaining>0) {
                        if(canceled())throw java.util.concurrent.CancellationException("İndirme durduruldu")
                        val step=minOf(remaining,250L);waitForRetry(step);remaining-=step
                    }
                }
            }
        } catch(e:Exception) {
            val phase=if(e is DownloadNetwork.PolicyBlocked)ModulePhase.WAITING_CONNECTION else if(e is java.util.concurrent.CancellationException)ModulePhase.PAUSED else ModulePhase.ERROR
            DownloadCoordinator.observe(DownloadCoordinator.Transfer(expected,expected,identity,
                ModuleProgress(module,operation,phase,bytesDownloaded=File(nativeTarget.path+".part").takeIf{it.isFile}?.length()?:0,bytesTotal=DownloadCoordinator.snapshot(operation).lastOrNull{it.artifactId==expected}?.progress?.bytesTotal,errorCode=e.javaClass.simpleName),DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE))
            throw e
        } finally {DownloadCoordinator.release(key)}
    }
    private fun transfer(url:String,expected:String,target:File,module:OfflineModuleId,operation:String,
        canceled:()->Boolean={false}, clock:()->Long={System.nanoTime()/1_000_000},
        open:(URL)->HttpURLConnection={DownloadNetwork.open(it)},
        progress:(ModuleProgress)->Unit={}, availableBytes:(File)->Long={it.usableSpace},maxBytes:Long=1024L*1024*1024,requestPolicy:DownloadNetworkPolicy=DownloadNetwork.policy) {
        require(maxBytes in 1..(2L*1024*1024*1024))
        val address=URL(url);require(address.protocol=="https" && address.userInfo==null && address.ref==null)
        require(expected.matches(Regex("[0-9a-f]{64}")))
        val parent=requireNotNull(target.parentFile).apply{mkdirs()};require(parent.canonicalFile==parent.absoluteFile)
        val part=File(target.path+".part");val identity=File(target.path+".part.identity")
        val validator=File(target.path+".part.validator")
        val seal=File(target.path+".part.sha256")
        val checkpoint=File(part.path+".checkpoint")
        for(file in listOf(target,part,identity,validator,seal,checkpoint,File(checkpoint.path+".tmp")))require(!Files.isSymbolicLink(file.toPath())){"İndirme yolu symlink olamaz."}
        if(target.isFile && target.length()<=maxBytes && sha(target)==expected)return
        val sourceHash=MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        val sourceIdentity="$sourceHash\n$expected"
        require(!identity.isFile || identity.length()<=4096){"Kısmi kimlik kaydı boyut sınırı"}
        val legacyIdentity=identity.isFile && identity.length()<=4096 && identity.readText()=="$url\n$expected"
        if(legacyIdentity)identity.writeText(sourceIdentity) // V4 sidecar migration; no URL retained
        if(!identity.isFile || identity.readText()!=sourceIdentity){part.delete();validator.delete();seal.delete();checkpoint.delete();identity.writeText(sourceIdentity)}
        if(part.isFile && (!seal.isFile || seal.length()!=64L || seal.readText()!=sha(part))) {
            DownloadPartialCheckpoint.recover(part,sourceHash,expected,maxBytes,trim=true)?.let{seal.writeText(sha(part))}
        }
        if(part.isFile && !seal.isFile && !legacyIdentity && sha(part)!=expected) {
            part.delete();validator.delete();checkpoint.delete()
        }
        if(seal.isFile && (seal.length()!=64L || !part.isFile || seal.readText()!=sha(part))) {
            part.delete();validator.delete();seal.delete()
        }
        if(part.isFile && part.length()<=maxBytes && sha(part)==expected) {
            progress(ModuleProgress(module,operation,ModulePhase.VERIFYING,part.length(),part.length(),detail="Tam kısmi dosya SHA-256 ile doğrulandı"))
            Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            identity.delete();validator.delete();seal.delete();checkpoint.delete();checkpoint.delete();return
        }
        var offset=if(part.isFile)part.length() else 0L
        require(offset<=maxBytes){"Kısmi dosya boyut sınırını aşıyor."}
        val previousValidator=validator.takeIf{it.isFile && it.length()<=512}?.readText()?.takeIf{it.isNotBlank() && !it.contains('\r') && !it.contains('\n')}
        if(canceled())throw java.util.concurrent.CancellationException("İndirme iptal edildi")
        DownloadNetwork.check(requestPolicy)
        val connection=open(address)
        try {
            connection.connectTimeout=20000;connection.readTimeout=20000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept-Encoding","identity")
            if(offset>0){connection.setRequestProperty("Range","bytes=$offset-");if(previousValidator!=null)connection.setRequestProperty("If-Range",previousValidator)}
            val status=connection.responseCode
            if(status==408 || status==429 || status in 500..599) {
                val retry=connection.getHeaderField("Retry-After")?.trim()?.let { value ->
                    value.toLongOrNull()?.takeIf{it>=0}?.let{if(it>60)60001L else it*1000L}
                        ?: runCatching{(java.time.ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis()).coerceAtLeast(0)}.getOrNull()
                }
                throw TransientHttp(status,retry)
            }
            val responseValidator=(connection.getHeaderField("ETag")?.takeIf{!it.startsWith("W/")}?:connection.getHeaderField("Last-Modified"))
                ?.takeIf{it.length<=512 && !it.contains('\r') && !it.contains('\n')}
            if(status==206 && previousValidator!=null && previousValidator!=responseValidator) {
                part.delete();validator.delete();error("Kaynak doğrulayıcısı değişti; kısmi dosya silindi, yeniden deneyin.")
            }
            var total:Long?=null
            if(status==206) {
                val range=Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(connection.getHeaderField("Content-Range")?:"")?:error("Geçersiz Content-Range")
                val start=range.groupValues[1].toLong();val end=range.groupValues[2].toLong();val size=range.groupValues[3].toLong()
                require(start==offset && end>=start && end<size);total=size
                if(connection.contentLengthLong>=0)require(connection.contentLengthLong==end-start+1)
            } else {
                require(status==200){"HTTP $status; kısmi dosya hazır sayılmadı."}
                offset=0;total=connection.contentLengthLong.takeIf{it>0}
            }
            if(responseValidator!=null)validator.writeText(responseValidator)
            total?.let{require(it<=maxBytes && availableBytes(parent)>it-offset+64L*1024*1024)}
            val start=clock();val meter=DownloadMeasurement(module,operation,start,offset)
            progress(meter.sample(offset,total,start))
            var bytes=offset;var reported=start;var checkpointBytes=offset;var checkpointAt=start
            val prefixDigest=MessageDigest.getInstance("SHA-256")
            if(offset>0)part.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;prefixDigest.update(b,0,n)}}
            connection.inputStream.use{input->java.io.FileOutputStream(part,offset>0).use{out->
                val b=ByteArray(65536)
                while(true) {
                    if(canceled())throw java.util.concurrent.CancellationException("İndirme iptal edildi")
                    DownloadNetwork.check(requestPolicy)
                    val n=input.read(b);if(n<0)break
                    bytes+=n;require(bytes<=maxBytes && (total==null||bytes<=total!!)){"İndirme boyutu sınırı"}
                    require(availableBytes(parent)>n+64L*1024*1024){"Yetersiz depolama; çalışan hedef korunur."}
                    out.write(b,0,n);prefixDigest.update(b,0,n)
                    val now=clock()
                    if(checkpointBytes==offset || bytes-checkpointBytes>=8L*1024*1024 || now-checkpointAt>=1500) {
                        out.fd.sync()
                        val digest=runCatching{(prefixDigest.clone() as MessageDigest).digest().joinToString(""){"%02x".format(it)}}.getOrElse{sha(part)}
                        DownloadPartialCheckpoint.write(part,bytes,digest,sourceHash,expected)
                        DownloadCoordinator.prefixCheckpoint(operation,expected,bytes)
                        checkpointBytes=bytes;checkpointAt=now
                    }
                    if(now-reported>=250){progress(meter.sample(bytes,total,now));reported=now}
                }
                out.fd.sync()
            }}
            progress(meter.sample(bytes,total,clock()))
            require(total==null || bytes==total){"Eksik indirme; kısmi dosya korundu."}
            progress(ModuleProgress(module,operation,ModulePhase.VERIFYING,bytes,total,detail="SHA-256 doğrulanıyor"))
            if(sha(part)!=expected){part.delete();identity.delete();validator.delete();error("SHA-256 uyuşmazlığı; paket kurulmadı.")}
            Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            identity.delete();validator.delete();seal.delete();checkpoint.delete()
        } finally {
            connection.disconnect();DownloadNetwork.release(connection)
            // A persistent prefix digest detects damage before the next Range request. It is not a READY receipt.
            if(part.isFile && part.length()<=maxBytes) {
                val pending=File(seal.path+".new")
                require(!Files.isSymbolicLink(pending.toPath()))
                pending.writeText(sha(part))
                Files.move(pending.toPath(),seal.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            } else seal.delete()
        }
    }
}
