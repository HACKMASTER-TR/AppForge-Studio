package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

internal data class ProjectDependencyPlan(val fingerprint:String,val engine:String,val manifests:List<String>,val dependencies:List<String>,val blockers:List<String>,val downloadBytes:Long?=null)

/** Read-only analysis. Executing build/lifecycle scripts is a separate, explicit user action. */
internal object ProjectDependencyEvidence {
    private fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    fun analyze(d:ProjectDraft):ProjectDependencyPlan {
        val root=File(d.importedFolder?:error("Proje kaynak klasörü yok; önce kaynağı içe aktarın."))
        require(root.isDirectory && root.canonicalFile==root.absoluteFile)
        val files=sourceFiles(root)
        val manifests=files.filter{it.name in setOf("package.json","package-lock.json","build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts","gradle.lockfile","verification-metadata.xml","requirements.txt","requirements.lock","pyproject.toml","pom.xml")}.sortedBy{it.relativeTo(root).path}
        val sourceHash=digest(files.sortedBy{it.relativeTo(root).path}.joinToString("\n"){it.relativeTo(root).path+":"+VerifiedModuleDownload.sha(it)})
        val settings=listOf(d.sourceBuildEngine,d.sourceTechnology,d.buildOutput,d.packageName,d.minSdk,d.targetSdk,d.normalHtmlEngine,FeatureSupportMatrix.selections(d).toSortedMap()).joinToString("|")
        val fingerprint=digest("source-probe-v6|"+sourceHash+settings)
        val blockers=mutableListOf<String>();val dependencies=mutableListOf<String>()
        val engine=d.sourceBuildEngine.lowercase()
        if(engine in setOf("node-web","expo","react-native")) {
            val lock=File(root,"package-lock.json")
            if(!lock.isFile || lock.length()>16L*1024*1024)blockers.add("npm için boyutu sınırlı package-lock.json v2/v3 gerekir; kilitsiz hazırlama yapılmaz.")
            else {
                val json=JSONObject(lock.readText());val version=json.optInt("lockfileVersion",0)
                if(version !in 2..3)blockers.add("npm lockfile sürümü 2 veya 3 olmalı.")
                val packages=json.optJSONObject("packages")
                if(packages==null)blockers.add("npm packages envanteri yok.") else for(key in packages.keys()) {
                    if(key.isEmpty())continue
                    val p=packages.getJSONObject(key)
                    if(p.optBoolean("link",false)) {
                        val target=File(root,p.optString("resolved"))
                        if(!target.isDirectory || !target.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) || Files.isSymbolicLink(target.toPath()))blockers.add("npm workspace bağlantısı proje içinde doğrulanamadı: $key")
                        continue
                    }
                    val sri=p.optString("integrity")
                    val valid=sri.split(' ').any{token->val m=Regex("sha(256|384|512)-([A-Za-z0-9+/]+={0,2})").matchEntire(token);m!=null && runCatching{java.util.Base64.getDecoder().decode(m.groupValues[2]).size==m.groupValues[1].toInt()/8}.getOrDefault(false)}
                    if(!valid)blockers.add("npm paketi için SHA-256/384/512 integrity yok: $key")
                    val resolved=p.optString("resolved")
                    val safe=if(resolved.startsWith("file:"))runCatching{val f=File(root,java.net.URI(resolved).schemeSpecificPart);f.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())}.getOrDefault(false)
                        else runCatching{val uri=java.net.URI(resolved);uri.scheme=="https" && uri.userInfo==null && uri.host!=null}.getOrDefault(false)
                    if(!safe)blockers.add("npm kaynağı güvenli HTTPS veya proje içi dosya değil: $key")
                    val lockedVersion=p.optString("version","")
                    if(!lockedVersion.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][A-Za-z0-9.-]+)?")))blockers.add("npm paketi tam sürüme kilitlenmemiş: $key")
                    dependencies.add(key+" @ "+lockedVersion.takeIf{it.matches(Regex("[A-Za-z0-9.+-]{1,100}"))}.orEmpty())
                }
            }
        }
        val nativeGradle=engine in setOf("android-gradle","android-ndk","universal-cross-platform")
        if(nativeGradle) {
            val roots=if(engine=="universal-cross-platform")manifests.filter{it.name in setOf("settings.gradle","settings.gradle.kts")}.map{it.parentFile}.distinct() else listOf(root)
            if(roots.isEmpty())blockers.add("Gradle settings kökü bulunamadı.")
            for(project in roots) {
                val verification=File(project,"gradle/verification-metadata.xml")
                if(!verification.isFile)blockers.add("Gradle/Maven için gerçek proje kökünde güvenilen gradle/verification-metadata.xml gerekir.")
                else if(!validVerification(verification))blockers.add("Gradle checksum metadata geçersiz, boş veya SHA-256 güven kayıtları eksik; dış XML kaynakları açılmaz.")
                val locks=manifests.filter{it.name=="gradle.lockfile" && it.toPath().startsWith(project.toPath())}
                if(locks.isEmpty())blockers.add("Gradle bağımlılık kilidi yok; dinamik sürüm çözümlemesi çevrimdışı hazır sayılmaz.")
                for(lock in locks) {
                    val lines=lock.readLines().map{it.trim()}.filter{it.isNotEmpty() && !it.startsWith("#")}
                    if(lines.isEmpty() || lines.any{row->
                        val coordinate=row.substringBefore('=');val pieces=coordinate.split(':')
                        !row.startsWith("empty=") && (pieces.size!=3 || pieces.any{!it.matches(Regex("[A-Za-z0-9_.-]+"))} || pieces.last().endsWith("SNAPSHOT",true) || pieces.last().lowercase() in setOf("latest","release") || !row.contains('='))
                    })blockers.add("Gradle lockfile boş/dinamik veya geçersiz sürüm içeriyor: ${lock.relativeTo(root).path}")
                    dependencies.addAll(lines.filter{!it.startsWith("empty=")}.map{it.substringBefore('=')})
                }
            }
        }
        if(engine=="python-android" && !File(root,"requirements.txt").isFile)blockers.add("Python projesinde requirements.txt gerekir; bağımlılık yoksa açıkça boş bırakın.")
        for(file in manifests.filter{it.name in setOf("requirements.txt","requirements.lock")}) {
            val rows=file.readText().replace(Regex("\\\\\r?\n")," ").lineSequence().map{it.trim()}.filter{it.isNotBlank() && !it.startsWith("#")}.toList()
            if(rows.any{!it.contains("==") || !Regex("--hash=sha256:[0-9a-fA-F]{64}").containsMatchIn(it)})blockers.add("Python bağımlılıkları tam sürüm ve SHA-256 --hash ile kilitlenmeli.")
            dependencies.addAll(rows.map{Regex("^([A-Za-z0-9_.-]+)==([A-Za-z0-9_.+!-]+)").find(it)?.value?:"Python bağımlılığı (kaynak ayrıntısı gizlendi)"})
        }
        return ProjectDependencyPlan(fingerprint,engine,manifests.map{it.relativeTo(root).path},dependencies.sorted(),blockers.distinct())
    }
    private fun validVerification(file:File):Boolean=runCatching {
        require(file.length() in 1..8L*1024*1024)
        val text=file.readText();require(!text.contains('\u0000') && !Regex("<!\\s*(DOCTYPE|ENTITY)",RegexOption.IGNORE_CASE).containsMatchIn(text))
        val factory=javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware=true;factory.isValidating=false;factory.isExpandEntityReferences=false
        runCatching{factory.setFeature("http://xml.org/sax/features/external-general-entities",false)}
        runCatching{factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false)}
        val builder=factory.newDocumentBuilder()
        builder.setEntityResolver{_,_->throw SecurityException("Dış XML kaynakları kapalı")}
        builder.setErrorHandler(object:org.xml.sax.ErrorHandler {
            override fun warning(e:org.xml.sax.SAXParseException){throw e}
            override fun error(e:org.xml.sax.SAXParseException){throw e}
            override fun fatalError(e:org.xml.sax.SAXParseException){throw e}
        })
        val doc=builder.parse(org.xml.sax.InputSource(java.io.StringReader(text)))
        require(doc.documentElement.localName=="verification-metadata")
        require(doc.getElementsByTagNameNS("*","trust").length==0){"Checksumsuz artifact trust kuralları desteklenmiyor"}
        val verify=doc.getElementsByTagNameNS("*","verify-metadata")
        require((0 until verify.length).all{verify.item(it).textContent.trim()=="true"})
        val hashes=doc.getElementsByTagNameNS("*","sha256");require(hashes.length>0)
        (0 until hashes.length).all{index->val node=hashes.item(index) as org.w3c.dom.Element;node.parentNode.localName=="artifact" && (node.parentNode as org.w3c.dom.Element).getAttribute("name").isNotBlank() && node.getAttribute("value").matches(Regex("[a-fA-F0-9]{64}"))}
    }.getOrDefault(false)
    private fun sourceFiles(root:File):List<File> {
        val result=mutableListOf<File>();var bytes=0L
        root.walkTopDown().onEnter{dir->
            require(!Files.isSymbolicLink(dir.toPath())){"Kaynakta symlink desteklenmiyor."}
            val rel=dir.relativeTo(root).invariantSeparatorsPath
            dir==root || dir.name !in setOf(".git",".gradle","node_modules",".expo",".next")
        }.forEach{file->
            require(!Files.isSymbolicLink(file.toPath())){"Kaynakta symlink desteklenmiyor."}
            if(file.isFile){bytes+=file.length();require(bytes<=512L*1024*1024 && result.size<20000){"Kaynak analiz sınırı aşıldı."};result.add(file)}
        };return result
    }
    fun copyReviewedSource(d:ProjectDraft,destination:File) {
        require(!destination.exists());destination.mkdirs()
        val root=File(d.importedFolder?:error("Kaynak yok"))
        for(source in sourceFiles(root)) {
            val target=File(destination,source.relativeTo(root).path)
            target.parentFile.mkdirs();require(target.canonicalFile.toPath().startsWith(destination.canonicalFile.toPath()))
            Files.copy(source.toPath(),target.toPath(),StandardCopyOption.COPY_ATTRIBUTES)
        }
        require(analyze(d.copy(importedFolder=destination.path)).fingerprint==analyze(d).fingerprint){"Kaynak kopyalanırken değişti; kod çalıştırılmadı."}
    }
    fun record(file:File,plan:ProjectDependencyPlan,cacheRoots:List<File>,offline:Boolean) {
        require(plan.blockers.isEmpty())
        val entries=JSONObject();var count=0;var bytes=0L
        for((index,root) in cacheRoots.withIndex())if(root.isDirectory)root.walkTopDown().onEnter{!Files.isSymbolicLink(it.toPath())}.forEach{f->
            if(f.isFile && !Files.isSymbolicLink(f.toPath()) && !f.name.endsWith(".lock") && !f.name.endsWith(".tmp")) {
                require(f.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()));count++;bytes+=f.length();require(count<=50000 && bytes<=16L*1024*1024*1024){"Cache doğrulama sınırı aşıldı."}
                entries.put("$index/"+f.relativeTo(root).invariantSeparatorsPath,VerifiedModuleDownload.sha(f))
            }
        }
        val json=JSONObject().put("schema",1).put("source",plan.fingerprint).put("engine",plan.engine).put("offline",offline).put("cache",entries)
        file.parentFile?.mkdirs();require(!Files.isSymbolicLink(file.toPath()))
        val tmp=File(file.path+".tmp");require(!Files.isSymbolicLink(tmp.toPath()))
        FileOutputStream(tmp).use{it.write(json.toString().toByteArray());it.fd.sync()}
        Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    fun ready(file:File,plan:ProjectDependencyPlan,cacheRoots:List<File>):Boolean=runCatching {
        require(file.isFile && file.length()<=16L*1024*1024 && !Files.isSymbolicLink(file.toPath()) && plan.blockers.isEmpty())
        val json=JSONObject(file.readText());require(json.getInt("schema")==1 && json.getBoolean("offline") && json.getString("source")==plan.fingerprint && json.getString("engine")==plan.engine)
        val entries=json.getJSONObject("cache")
        val current=mutableSetOf<String>()
        for((index,root) in cacheRoots.withIndex())if(root.isDirectory)root.walkTopDown().onEnter{!Files.isSymbolicLink(it.toPath())}.forEach{f->if(f.isFile && !Files.isSymbolicLink(f.toPath()) && !f.name.endsWith(".lock") && !f.name.endsWith(".tmp"))current.add("$index/"+f.relativeTo(root).invariantSeparatorsPath)}
        require(current==entries.keys().asSequence().toSet())
        entries.keys().asSequence().all{key->
            val index=key.substringBefore('/').toInt();val relative=key.substringAfter('/');require(index in cacheRoots.indices && !File(relative).isAbsolute && relative.split('/').none{it==".."})
            val root=cacheRoots[index];val f=File(root,relative)
            f.isFile && !Files.isSymbolicLink(f.toPath()) && f.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && VerifiedModuleDownload.sha(f)==entries.getString(key)
        }
    }.getOrDefault(false)
}
