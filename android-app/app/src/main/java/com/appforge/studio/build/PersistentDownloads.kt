package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

internal enum class DownloadState { QUEUED, WAITING_FOR_NETWORK, WAITING_FOR_CONSENT, DOWNLOADING, PAUSE_REQUESTED, PAUSED, VERIFYING, PREPARING, INSTALLING, COMPLETED, FAILED, CANCELLED, RECOVERY_REQUIRED }
internal enum class DownloadNetworkPolicy { WIFI_ONLY, WIFI_PREFERRED, ALLOW_ANY }
internal data class ArtifactDownloadRecord(
    val operationId:String,val moduleId:OfflineModuleId,val artifactId:String,val artifactVersion:String,
    val sourceIdentityHash:String,val expectedSha256:String?,val expectedBytes:Long?,val verifiedPartialBytes:Long,
    val downloadState:DownloadState,val resumeCapability:DownloadCoordinator.ResumeCapability,
    val networkPolicy:DownloadNetworkPolicy,val retryCount:Int,val createdAt:Long,val updatedAt:Long,
    val verificationState:String,val stagingIdentity:String?,val failureReason:String?=null
)

/** Private bounded journal. Records never authorize installation or contain executable source URLs. */
internal class PersistentDownloads(private val directory:File,private val roots:Map<String,File>,private val availableBytes:(File)->Long={it.usableSpace}) {
    private val file=File(directory,"artifacts.json")
    private fun safe(path:File) {
        var current:File?=path.absoluteFile
        while(current!=null){require(!Files.isSymbolicLink(current.toPath())){"İndirme kaydı/staging symlink olamaz."};current=current.parentFile}
    }
    fun staging(target:File):String {
        safe(target)
        val entry=roots.entries.firstOrNull{target.absoluteFile.toPath().startsWith(it.value.absoluteFile.toPath())}?:error("Özel staging kökü dışında hedef")
        val rel=entry.value.absoluteFile.toPath().relativize(target.absoluteFile.toPath()).toString()
        require(rel.isNotEmpty() && rel.split('/').none{it==".." || it=="." || it.isEmpty()})
        return entry.key+":"+rel
    }
    private fun resolve(identity:String):File {
        require(identity.length<=1024)
        val root=roots[identity.substringBefore(':')]?:error("Tanınmayan staging kökü")
        val rel=identity.substringAfter(':',"")
        require(rel.isNotEmpty() && !rel.startsWith('/') && !rel.contains('\\') && rel.split('/').none{it==".." || it=="." || it.isEmpty()})
        return File(root,rel).also{safe(it);require(it.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))}
    }
    private fun validate(r:ArtifactDownloadRecord) {
        require(r.operationId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        require(listOf(r.artifactId,r.sourceIdentityHash).all{it.matches(Regex("[a-f0-9]{64}"))})
        require(r.expectedSha256==null || r.expectedSha256.matches(Regex("[a-f0-9]{64}")))
        require(r.artifactVersion.matches(Regex("[A-Za-z0-9._+-]{1,100}")))
        require(r.verifiedPartialBytes in 0..(2L*1024*1024*1024))
        require(r.expectedBytes==null || r.expectedBytes in r.verifiedPartialBytes..(2L*1024*1024*1024))
        require(r.retryCount in 0..5 && r.createdAt>0 && r.updatedAt>=r.createdAt)
        require(r.verificationState in setOf("UNVERIFIED","PREFIX_VERIFIED","SHA256_VERIFIED","RECHECK_REQUIRED"))
        require(r.failureReason==null || r.failureReason.matches(Regex("[A-Z0-9_]{1,100}")))
        r.stagingIdentity?.let{resolve(it)}
    }
    @Synchronized fun read():List<ArtifactDownloadRecord> {
        safe(file);if(!file.exists())return emptyList()
        require(file.isFile && file.length() in 1..262144){"Bozuk veya büyük indirme günlüğü"}
        val json=JSONObject(file.readText());require(json.getInt("schema")==1){"Desteklenmeyen indirme günlüğü; otomatik migration yapılmadı."}
        val rows=json.getJSONArray("artifacts");require(rows.length()<=128)
        val result=(0 until rows.length()).map{n->val o=rows.getJSONObject(n)
            ArtifactDownloadRecord(o.getString("operationId"),OfflineModuleId.valueOf(o.getString("moduleId")),o.getString("artifactId"),o.getString("artifactVersion"),o.getString("sourceIdentityHash"),o.optString("expectedSha256").takeIf{it.isNotBlank()},if(o.isNull("expectedBytes"))null else o.getLong("expectedBytes"),o.getLong("verifiedPartialBytes"),DownloadState.valueOf(o.getString("downloadState")),DownloadCoordinator.ResumeCapability.valueOf(o.getString("resumeCapability")),DownloadNetworkPolicy.valueOf(o.getString("networkPolicy")),o.getInt("retryCount"),o.getLong("createdAt"),o.getLong("updatedAt"),o.getString("verificationState"),o.optString("stagingIdentity").takeIf{it.isNotBlank()},o.optString("failureReason").takeIf{it.isNotBlank()}).also(::validate)
        }
        require(result.map{it.operationId+":"+it.artifactId}.distinct().size==result.size)
        return result
    }
    @Synchronized fun save(rows:List<ArtifactDownloadRecord>) {
        require(rows.size<=128);rows.forEach(::validate)
        require(rows.map{it.operationId+":"+it.artifactId}.distinct().size==rows.size)
        safe(directory);directory.mkdirs();safe(file)
        val array=JSONArray()
        rows.forEach{r->array.put(JSONObject().put("operationId",r.operationId).put("moduleId",r.moduleId.name).put("artifactId",r.artifactId).put("artifactVersion",r.artifactVersion).put("sourceIdentityHash",r.sourceIdentityHash).put("expectedSha256",r.expectedSha256?:"").put("expectedBytes",r.expectedBytes?:JSONObject.NULL).put("verifiedPartialBytes",r.verifiedPartialBytes).put("downloadState",r.downloadState.name).put("resumeCapability",r.resumeCapability.name).put("networkPolicy",r.networkPolicy.name).put("retryCount",r.retryCount).put("createdAt",r.createdAt).put("updatedAt",r.updatedAt).put("verificationState",r.verificationState).put("stagingIdentity",r.stagingIdentity?:"").put("failureReason",r.failureReason?:""))}
        val bytes=JSONObject().put("schema",1).put("artifacts",array).toString().toByteArray(Charsets.UTF_8);require(bytes.size<=262144)
        require(availableBytes(directory)>bytes.size+65536L){"İndirme günlüğü için yetersiz alan; önceki kayıt korunur."}
        val part=File(file.path+".tmp");safe(part)
        FileOutputStream(part).use{it.write(bytes);it.fd.sync()}
        Files.move(part.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    /** Re-read bytes, never trust persisted COMPLETE/partial counters or READY markers. */
    @Synchronized fun recover():List<ArtifactDownloadRecord> = read().map{r->
        if(r.downloadState==DownloadState.CANCELLED)return@map r
        var bytes=0L;var state=DownloadState.RECOVERY_REQUIRED;var verification="RECHECK_REQUIRED"
        r.stagingIdentity?.let { identity ->
            val target=resolve(identity)
            if(target.isFile && target.length()<=2L*1024*1024*1024 && r.expectedSha256!=null && (r.expectedBytes==null || target.length()==r.expectedBytes) && VerifiedModuleDownload.sha(target)==r.expectedSha256) {
                state=DownloadState.RECOVERY_REQUIRED;verification="RECHECK_REQUIRED" // Source manifest is re-authorized only by a fresh explicit module request.
            } else {
                val part=File(target.path+".part");val seal=File(part.path+".sha256");val source=File(part.path+".identity")
                listOf(part,seal,source).forEach(::safe)
                if(part.isFile && part.length()<=2L*1024*1024*1024 && (r.expectedBytes==null || part.length()<=r.expectedBytes) && seal.isFile && seal.length()==64L && source.isFile && source.length()<=200 && source.readText()==r.sourceIdentityHash+"\n"+r.expectedSha256 && seal.readText()==VerifiedModuleDownload.sha(part)) {
                    bytes=part.length();state=DownloadState.PAUSED;verification="PREFIX_VERIFIED"
                } else if(r.expectedSha256!=null && source.isFile && source.length()<=200 && source.readText()==r.sourceIdentityHash+"\n"+r.expectedSha256) {
                    DownloadPartialCheckpoint.recover(part,r.sourceIdentityHash,r.expectedSha256,r.expectedBytes?:2L*1024*1024*1024)?.let { verified->bytes=verified;state=DownloadState.PAUSED;verification="PREFIX_VERIFIED" }
                }
            }
        }
        r.copy(downloadState=state,verifiedPartialBytes=bytes,verificationState=verification,updatedAt=maxOf(r.updatedAt,System.currentTimeMillis()))
    }.also(::save)
}
