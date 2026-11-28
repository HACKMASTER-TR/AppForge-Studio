package com.appforge.studio.build

import java.io.File
import java.io.IOException
import java.nio.channels.Channels
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/**
 * Read-only diagnostic, not an ownership or cleanup plan.
 * No symlink traversal, no project-content hashing, no filesystem mutation.
 * Size totals are logical file bytes. Allocated block counts may overlap for
 * hard links or reflinks; duplicate content is never a physical savings claim.
 */
internal object R69StorageInventory {
    data class Bucket(val name:String,val bytes:Long,val files:Long)
    data class Progress(val entries:Long,val hashedBytes:Long,val hashing:Boolean)
    data class Duplicate(val bytesPerFile:Long,val copies:Int,val logicalExtraBytes:Long,val owners:List<String>)
    data class Report(
        val logicalBytes:Long,val allocatedBlockSum:Long?,val files:Long,val links:Long,
        val byOwner:List<Bucket>,val byCategory:List<Bucket>,
        val duplicates:List<Duplicate>,val duplicateGroups:Int,val logicalExtraBytes:Long,
        val hashedBytes:Long,val duplicateMinimumBytes:Long,val incomplete:Boolean,val skipped:Long,
        val identityUnknown:Boolean
    )
    private data class Location(val owner:String,val category:String,val managed:Boolean)
    private data class Candidate(val path:Path,val size:Long,val modified:java.nio.file.attribute.FileTime,
        val key:String?,val owner:String)
    private data class Count(var bytes:Long=0,var files:Long=0) {
        fun add(size:Long){bytes=Math.addExact(bytes,size);files++}
    }
    private fun location(parts:List<String>):Location {
        val root=parts.firstOrNull().orEmpty()
        val privateArea=parts.getOrNull(1).orEmpty()
        val isolated=root=="no_backup" && privateArea=="isolated-modules-v1"
        val id=parts.getOrNull(2)?.uppercase(java.util.Locale.ROOT).orEmpty()
        val owner=when {
            isolated -> if(id in OfflineModuleId.entries.map { it.name }) id else "İzole diğer"
            root=="no_backup" && privateArea=="device-build" && parts.getOrNull(2)=="runtime-v3" -> "Ortak Linux"
            root=="no_backup" && privateArea=="offline-build-pack" && parts.getOrNull(2)=="windows-host-v1" -> "Windows Portable Host"
            root=="no_backup" && privateArea in setOf("module-downloads","v9-signed-r2-canary","verified-rootfs-downloads",R70SharedArchives.DIRECTORY) -> "Ortak arşiv deposu"
            root=="cache" || root=="code_cache" -> "Android önbelleği"
            else -> "Diğer özel uygulama verileri"
        }
        val managed=isolated || owner in setOf("Ortak Linux","Windows Portable Host","Ortak arşiv deposu")
        val category=when {
            !managed -> owner
            parts.any { it.startsWith(".v3-previous-") || it=="restore-points" } -> "Geri dönüş kopyaları"
            parts.any { it.startsWith(".v3-discard-") } -> "Karantina"
            parts.any { it.startsWith(".v3-stage-") || it=="staging" || it=="stages" } -> "Geçici kurulum alanları"
            parts.any { it==".gradle-appforge" || it==".gradle" } -> "Gradle / Maven"
            parts.any { it=="npm-cache-v1" || it==".npm" } -> "npm önbelleği"
            parts.any { it=="downloads" || it==".module-archive-cache-v5" || it=="cache" || it=="apt" } ||
                owner=="Ortak arşiv deposu" -> "Arşiv / paket önbelleği"
            owner=="Windows Portable Host" -> "Windows Host"
            parts.any { it=="rootfs" } -> "Kurulu çalışma ortamı"
            else -> "Diğer araç verileri"
        }
        return Location(owner,category,managed)
    }
    fun scan(dataDir:File,canceled:()->Unit={},progress:(Progress)->Unit={},
        allocated:(String)->Long={RuntimeFileClone.allocatedBytes(it)},
        minDuplicateBytes:Long=1024L*1024,maxHashBytes:Long=64L*1024*1024*1024,
        maxEntries:Long=1_000_000L,maxCandidates:Int=40_000):Report {
        require(minDuplicateBytes>0 && maxHashBytes>=0 && maxEntries>0 && maxCandidates>0)
        val root=RecoveryPathAnchor.androidPlatformPath(dataDir.absoluteFile)
        RecoveryPathAnchor.check(root)
        require(root.isDirectory)
        val owners=linkedMapOf<String,Count>()
        val categories=linkedMapOf<String,Count>()
        val bySize=hashMapOf<Long,MutableList<Candidate>>()
        var entries=0L;var files=0L;var links=0L;var bytes=0L;var blocks=0L
        var blockKnown=true;var incomplete=false;var skipped=0L;var candidateCount=0
        var identityUnknown=false;var hashed=0L
        val rootPath=root.toPath()
        fun visit(path:Path,attrs:BasicFileAttributes):FileVisitResult {
            canceled()
            if(entries>=maxEntries){incomplete=true;return FileVisitResult.TERMINATE}
            entries++
            if(entries%512L==0L)progress(Progress(entries,0,false))
            if(attrs.isSymbolicLink){links++;return FileVisitResult.CONTINUE}
            val n=allocated(path.toString())
            if(n<0)blockKnown=false else blocks=Math.addExact(blocks,n)
            if(!attrs.isRegularFile)return FileVisitResult.CONTINUE
            val loc=location(rootPath.relativize(path).map { it.toString() })
            val size=attrs.size()
            files++;bytes=Math.addExact(bytes,size)
            owners.getOrPut(loc.owner){Count()}.add(size)
            categories.getOrPut(loc.category){Count()}.add(size)
            if(loc.managed && size>=minDuplicateBytes) {
                if(candidateCount>=maxCandidates){incomplete=true;skipped++}
                else {
                    val key=attrs.fileKey()?.toString()
                    if(key==null)identityUnknown=true
                    bySize.getOrPut(size){mutableListOf()}.add(Candidate(path,size,attrs.lastModifiedTime(),key,loc.owner))
                    candidateCount++
                }
            }
            return FileVisitResult.CONTINUE
        }
        Files.walkFileTree(rootPath,object:SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir:Path,attrs:BasicFileAttributes)=visit(dir,attrs)
            override fun visitFile(file:Path,attrs:BasicFileAttributes)=visit(file,attrs)
            override fun visitFileFailed(file:Path,exc:IOException):FileVisitResult {
                canceled();incomplete=true;skipped++;return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir:Path,exc:IOException?):FileVisitResult {
                canceled();if(exc!=null){incomplete=true;skipped++};return FileVisitResult.CONTINUE
            }
        })
        val result=mutableListOf<Duplicate>()
        progress(Progress(entries,hashed,true))
        val buffer=ByteArray(128*1024)
        fun stable(c:Candidate):Boolean {
            val a=Files.readAttributes(c.path,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
            return a.isRegularFile && a.size()==c.size && a.lastModifiedTime()==c.modified &&
                (c.key==null || a.fileKey()?.toString()==c.key)
        }
        for((size,items) in bySize.entries.sortedByDescending { it.key }) {
            canceled()
            // Known hard-link aliases are not separate copies.
            val unique=items.distinctBy { it.key ?: it.path.toString() }
            if(unique.size<2)continue
            val hashes=hashMapOf<String,MutableList<Candidate>>()
            for(c in unique) {
                canceled()
                if(c.size>maxHashBytes-hashed){incomplete=true;skipped++;continue}
                try {
                    RecoveryPathAnchor.check(c.path.toFile())
                    if(!stable(c)){incomplete=true;skipped++;continue}
                    val digest=MessageDigest.getInstance("SHA-256")
                    var read=0L
                    Files.newByteChannel(c.path,setOf<OpenOption>(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)).use { channel ->
                        val input=Channels.newInputStream(channel)
                        while(true) {
                            canceled()
                            val n=input.read(buffer)
                            if(n<0)break
                            read+=n;hashed+=n
                            if(read>c.size)throw IOException("File changed during scan")
                            digest.update(buffer,0,n)
                            if(hashed%(64L*1024*1024)<n)progress(Progress(entries,hashed,true))
                        }
                    }
                    if(read!=c.size || !stable(c)){incomplete=true;skipped++;continue}
                    val key=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
                    hashes.getOrPut(key){mutableListOf()}.add(c)
                } catch(e:IOException){incomplete=true;skipped++}
                  catch(e:IllegalArgumentException){incomplete=true;skipped++}
                  catch(e:SecurityException){incomplete=true;skipped++}
            }
            for(group in hashes.values.filter { it.size>1 }) {
                result.add(Duplicate(size,group.size,Math.multiplyExact(size,(group.size-1).toLong()),
                    group.map { it.owner }.distinct().sorted()))
            }
        }
        canceled()
        fun rows(map:Map<String,Count>)=map.map { (name,c)->Bucket(name,c.bytes,c.files) }.sortedByDescending { it.bytes }
        return Report(bytes,if(blockKnown && !incomplete)blocks else null,files,links,
            rows(owners),rows(categories),result.sortedByDescending { it.logicalExtraBytes }.take(20),result.size,
            result.sumOf { it.logicalExtraBytes },hashed,minDuplicateBytes,incomplete,skipped,identityUnknown)
    }
}
