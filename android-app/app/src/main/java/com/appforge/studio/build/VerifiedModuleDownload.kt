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
    fun download(url:String,expected:String,target:File,module:OfflineModuleId,operation:String,
        canceled:()->Boolean={false}, clock:()->Long={System.nanoTime()/1_000_000},
        open:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection},
        progress:(ModuleProgress)->Unit={}) {
        val address=URL(url);require(address.protocol=="https" && address.userInfo==null && address.ref==null)
        require(expected.matches(Regex("[0-9a-f]{64}")))
        val parent=requireNotNull(target.parentFile).apply{mkdirs()};require(parent.canonicalFile==parent.absoluteFile)
        val part=File(target.path+".part");val identity=File(target.path+".part.identity")
        for(file in listOf(target,part,identity))require(!Files.isSymbolicLink(file.toPath())){"İndirme yolu symlink olamaz."}
        if(target.isFile && sha(target)==expected)return
        val sourceIdentity="$url\n$expected"
        if(!identity.isFile || identity.readText()!=sourceIdentity){part.delete();identity.writeText(sourceIdentity)}
        if(part.isFile && sha(part)==expected) {
            progress(ModuleProgress(module,operation,ModulePhase.VERIFYING,part.length(),part.length(),detail="Tam kısmi dosya SHA-256 ile doğrulandı"))
            Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            identity.delete();return
        }
        var offset=if(part.isFile)part.length() else 0L
        if(canceled())throw java.util.concurrent.CancellationException("İndirme iptal edildi")
        val connection=open(address)
        try {
            connection.connectTimeout=20000;connection.readTimeout=20000;connection.instanceFollowRedirects=false
            if(offset>0)connection.setRequestProperty("Range","bytes=$offset-")
            val status=connection.responseCode
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
            total?.let{require(it<=1024L*1024*1024 && parent.usableSpace>it-offset+64L*1024*1024)}
            val start=clock();val meter=DownloadMeasurement(module,operation,start,offset)
            progress(meter.sample(offset,total,start))
            var bytes=offset;var reported=start
            connection.inputStream.use{input->java.io.FileOutputStream(part,offset>0).use{out->
                val b=ByteArray(65536)
                while(true) {
                    if(canceled())throw java.util.concurrent.CancellationException("İndirme iptal edildi")
                    val n=input.read(b);if(n<0)break
                    bytes+=n;require(bytes<=1024L*1024*1024 && (total==null||bytes<=total!!)){"İndirme boyutu sınırı"}
                    out.write(b,0,n)
                    val now=clock();if(now-reported>=250){progress(meter.sample(bytes,total,now));reported=now}
                }
                out.fd.sync()
            }}
            progress(meter.sample(bytes,total,clock()))
            require(total==null || bytes==total){"Eksik indirme; kısmi dosya korundu."}
            progress(ModuleProgress(module,operation,ModulePhase.VERIFYING,bytes,total,detail="SHA-256 doğrulanıyor"))
            if(sha(part)!=expected){part.delete();identity.delete();error("SHA-256 uyuşmazlığı; paket kurulmadı.")}
            Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            identity.delete()
        } finally {connection.disconnect()}
    }
}
