package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

internal enum class OperationStage { MODULE, DEPENDENCY_RESOLUTION, ARTIFACT_DOWNLOAD, INTEGRITY_VERIFICATION, CACHE_PREPARATION, INSTALLATION, OFFLINE_PROBE, REMOVAL, ROLLBACK }
internal data class DownloadOperationNode(
    val operationId:String,val parentOperationId:String?,val moduleId:OfflineModuleId,
    val stage:OperationStage,val operationState:DownloadState,val networkPolicy:DownloadNetworkPolicy,
    val createdAt:Long,val updatedAt:Long,val artifactId:String?=null,val artifactVersion:String?=null,
    val sourceIdentityHash:String?=null,val expectedSha256:String?=null,val expectedBytes:Long?=null,
    val downloadedBytes:Long=0,val verifiedBytes:Long=0,val retryCount:Int=0,
    val downloadCapability:DownloadCapability=DownloadCapability.BLOCKED,val failureReason:String?=null
)

/** Observational task tree, not an execution queue or installation receipt.
 * Independent artifact/module journals are reconciled conservatively after a crash. */
internal class PersistentOperationGraph(private val file:File,private val availableBytes:(File)->Long={it.usableSpace}) {
    companion object {
        private val TERMINAL_STATES=setOf(DownloadState.COMPLETED,DownloadState.CANCELLED,DownloadState.FAILED)
        fun id(vararg parts:String)=java.security.MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("\u0000").toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        fun moduleId(operation:String,module:OfflineModuleId)=id("module",operation,module.name)
        fun artifactNodeId(r:ArtifactDownloadRecord)=id("artifact",r.operationId,r.artifactId)
        /** Evicts complete terminal root subtrees oldest-first while preserving active and RECOVERY_REQUIRED trees. */
        fun pruneRetained(rows:List<DownloadOperationNode>,maxNodes:Int=512):List<DownloadOperationNode> {
            require(maxNodes in 1..512)
            if(rows.size<=maxNodes)return rows
            val working=rows.toMutableList()
            while(working.size>maxNodes) {
                val childrenByParent=working.filter{it.parentOperationId!=null}.groupBy{it.parentOperationId!!}
                fun subtree(rootId:String):Set<String> {
                    val visited=linkedSetOf<String>()
                    val queue=ArrayDeque<String>().apply{add(rootId)}
                    while(queue.isNotEmpty()) {
                        val current=queue.removeFirst()
                        if(visited.add(current))childrenByParent[current].orEmpty().forEach{queue.addLast(it.operationId)}
                    }
                    return visited
                }
                val byId=working.associateBy{it.operationId}
                val candidate=working.asSequence().filter{it.parentOperationId==null && it.stage==OperationStage.MODULE}.mapNotNull{root->
                    val ids=subtree(root.operationId)
                    val nodes=ids.mapNotNull(byId::get)
                    if(nodes.all{it.operationState in TERMINAL_STATES})Triple(root,ids,nodes.maxOf{it.updatedAt}) else null
                }.minWithOrNull(compareBy<Triple<DownloadOperationNode,Set<String>,Long>>{it.third}.thenBy{it.first.createdAt}.thenBy{it.first.operationId})?:break
                working.removeAll{it.operationId in candidate.second}
            }
            return working
        }
    }
    private fun safe(path:File){var p:File?=path.absoluteFile;while(p!=null){require(!Files.isSymbolicLink(p.toPath())){"İşlem günlüğü symlink olamaz"};p=p.parentFile}}
    private fun validate(rows:List<DownloadOperationNode>){
        require(rows.size<=512 && rows.map{it.operationId}.distinct().size==rows.size)
        val byId=rows.associateBy{it.operationId}
        for(n in rows){
            require(n.operationId.matches(Regex("[a-f0-9]{64}")))
            require(n.createdAt>0 && n.updatedAt>=n.createdAt && n.retryCount in 0..5)
            require(n.downloadedBytes in 0..(2L*1024*1024*1024) && n.verifiedBytes in 0..n.downloadedBytes)
            require(n.expectedBytes==null || n.expectedBytes in n.downloadedBytes..(2L*1024*1024*1024))
            listOf(n.artifactId,n.sourceIdentityHash,n.expectedSha256).filterNotNull().forEach{require(it.matches(Regex("[a-f0-9]{64}")))}
            require(n.artifactVersion==null || n.artifactVersion.matches(Regex("[A-Za-z0-9._+-]{1,100}")))
            require(n.failureReason==null || n.failureReason.matches(Regex("[A-Z0-9_]{1,100}")))
            if(n.parentOperationId==null)require(n.stage==OperationStage.MODULE)
            else {
                val parent=byId[n.parentOperationId]?:error("Eksik üst işlem kaydı")
                require(parent.moduleId==n.moduleId)
                val seen=hashSetOf(n.operationId);var current:DownloadOperationNode?=parent
                while(current!=null){require(seen.add(current.operationId)){"İşlem döngüsü"};current=current.parentOperationId?.let{byId[it]?:error("Eksik üst işlem")}}
            }
        }
    }
    @Synchronized fun read():List<DownloadOperationNode>{
        safe(file);if(!file.exists())return emptyList()
        require(file.isFile && file.length() in 1..1048576)
        val root=JSONObject(file.readText());require(root.getInt("schema")==1){"Tanınmayan işlem ağacı şeması"}
        val arr=root.getJSONArray("nodes");require(arr.length()<=512)
        return (0 until arr.length()).map{index->val o=arr.getJSONObject(index)
            fun optional(key:String)=o.optString(key).takeIf{it.isNotEmpty()}
            DownloadOperationNode(o.getString("operationId"),optional("parentOperationId"),OfflineModuleId.valueOf(o.getString("moduleId")),OperationStage.valueOf(o.getString("stage")),DownloadState.valueOf(o.getString("operationState")),DownloadNetworkPolicy.valueOf(o.getString("networkPolicy")),o.getLong("createdAt"),o.getLong("updatedAt"),optional("artifactId"),optional("artifactVersion"),optional("sourceIdentityHash"),optional("expectedSha256"),if(o.isNull("expectedBytes"))null else o.getLong("expectedBytes"),o.getLong("downloadedBytes"),o.getLong("verifiedBytes"),o.getInt("retryCount"),DownloadCapability.valueOf(o.getString("downloadCapability")),optional("failureReason"))
        }.also(::validate)
    }
    @Synchronized fun save(rows:List<DownloadOperationNode>){
        validate(rows);safe(file);val parent=requireNotNull(file.absoluteFile.parentFile);parent.mkdirs()
        val arr=JSONArray();rows.forEach{n->arr.put(JSONObject().put("operationId",n.operationId).put("parentOperationId",n.parentOperationId?:"").put("moduleId",n.moduleId.name).put("stage",n.stage.name).put("operationState",n.operationState.name).put("networkPolicy",n.networkPolicy.name).put("createdAt",n.createdAt).put("updatedAt",n.updatedAt).put("artifactId",n.artifactId?:"").put("artifactVersion",n.artifactVersion?:"").put("sourceIdentityHash",n.sourceIdentityHash?:"").put("expectedSha256",n.expectedSha256?:"").put("expectedBytes",n.expectedBytes?:JSONObject.NULL).put("downloadedBytes",n.downloadedBytes).put("verifiedBytes",n.verifiedBytes).put("retryCount",n.retryCount).put("downloadCapability",n.downloadCapability.name).put("failureReason",n.failureReason?:""))}
        val bytes=JSONObject().put("schema",1).put("nodes",arr).toString().toByteArray(Charsets.UTF_8);require(bytes.size<=1048576)
        require(availableBytes(parent)>bytes.size+65536L){"İşlem günlüğü için disk alanı yetersiz"}
        val tmp=File(file.path+".tmp");safe(tmp)
        try {
            FileOutputStream(tmp).use{it.write(bytes);it.fd.sync()}
            Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } catch(e:Throwable) {
            if(!Files.isSymbolicLink(tmp.toPath()))Files.deleteIfExists(tmp.toPath())
            throw e
        }
    }
    /** Rebuild artifact children from rehashed records. Missing/mismatched two-file updates never grant READY. */
    @Synchronized fun recover(artifacts:List<ArtifactDownloadRecord>):List<DownloadOperationNode>{
        val now=System.currentTimeMillis()
        val rows=read().map{n->n.copy(operationState=if(n.operationState==DownloadState.CANCELLED)DownloadState.CANCELLED else DownloadState.RECOVERY_REQUIRED,
            downloadedBytes=0,verifiedBytes=0,updatedAt=maxOf(now,n.updatedAt),failureReason="REAUTHORIZE_REQUIRED")}.toMutableList()
        for(r in artifacts) {
            val parent=r.parentOperationId?:moduleId(r.operationId,r.moduleId)
            if(rows.none{it.operationId==parent})rows.add(DownloadOperationNode(parent,null,r.moduleId,OperationStage.MODULE,DownloadState.RECOVERY_REQUIRED,r.networkPolicy,r.createdAt,maxOf(now,r.updatedAt),failureReason="JOURNAL_RECONCILIATION"))
            val p=rows.single{it.operationId==parent};require(p.moduleId==r.moduleId && p.stage==OperationStage.MODULE)
            val child=artifact(r,parent)
            rows.removeAll{it.operationId==child.operationId};rows.add(child)
        }
        val bounded=pruneRetained(rows)
        save(bounded);return bounded
    }
    fun artifact(r:ArtifactDownloadRecord,parent:String)=DownloadOperationNode(artifactNodeId(r),parent,r.moduleId,OperationStage.ARTIFACT_DOWNLOAD,r.downloadState,r.networkPolicy,r.createdAt,r.updatedAt,r.artifactId,r.artifactVersion,r.sourceIdentityHash,r.expectedSha256,r.expectedBytes,r.downloadedBytes,minOf(r.verifiedPartialBytes,r.downloadedBytes),r.retryCount,
        when(r.resumeCapability){DownloadCoordinator.ResumeCapability.VERIFIED_HTTP_RANGE->DownloadCapability.BYTE_RESUME;DownloadCoordinator.ResumeCapability.PACKAGE_MANAGER_CHECKPOINT->DownloadCapability.CHECKPOINT_RESUME;else->DownloadCapability.SAFE_RESTART},r.failureReason)
}
