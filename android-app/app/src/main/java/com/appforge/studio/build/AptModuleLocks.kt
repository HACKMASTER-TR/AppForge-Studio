package com.appforge.studio.build

import java.io.File
import java.nio.file.Files

/** Read-only Android preflight. A marker cannot replace locked bytes, metadata and installed state. */
internal object AptModuleLocks {
    const val SNAPSHOT="20261018T000000Z"
    const val CATALOG="77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f"
    const val PROFILE="356b70db7922a69651eac67811f59c78a612709e134b00b324721dd93b6c3ea2"
    private val releases=mapOf("noble" to "cdb2f31d809f589719a53c6ad15f255b27569c4059542ada282aaa21b8e164b0","noble-updates" to "f8db4261891aad2963b51de65b6f2a7f52250df02109b8a16fcfe5692d2187c3","noble-security" to "f444994ac2ed9f6a64365d5baee6f9540f2cad3320d7f574b0efd4c0d514c32c","noble-backports" to "9bb3c4bace14bf88014e5a66c8d431e65da66fd055dc7fdb45423305cf132cce")
    private val required=setOf("identity","profile.sha256","catalog.sha256","requests.txt","baseline.tsv","plan.tsv","packages.tsv","dependencies.tsv","installed.tsv","installed-files.sha256","verification","metadata.sha256")
    data class Summary(val transactions:Int,val packages:Int,val archiveBytes:Long,val versions:List<String>,val detail:String)
    fun inspect(root:File,id:OfflineModuleId):Summary {
        if(id==OfflineModuleId.LINUX_RUNTIME) {
            val vendor=File(root,"opt/appforge-device/apt-locks-v5/LINUX_RUNTIME/vendor-v1.json")
            if(!vendor.exists())return Summary(0,0,0,emptyList(),"Eski runtime: özgün vendor APT envanteri yok; migration zorlanmaz.")
            val j=org.json.JSONObject(read(root,vendor.relativeTo(root).invariantSeparatorsPath))
            require(j.getInt("schema")==1 && j.getString("release")=="24.04.4" && j.getString("rootfsSha256").matches(Regex("[a-f0-9]{64}")))
            val packages=j.getJSONObject("packages")
            return Summary(0,packages.length(),0,packages.keys().asSequence().map{"$it ${packages.getString(it)}"}.toList(),"Ubuntu Base 24.04.4 vendor envanteri • ${j.getString("architecture")} • ek APT indirmesi yok; bu, sonradan değişmiş rootfs için kurulum kanıtı değildir.")
        }
        val directory=safe(root,"opt/appforge-device/apt-locks-v5/${id.name}/snapshot-v1",false)
        if(!directory.exists())return Summary(0,0,0,emptyList(),"APT snapshot kilidi yok; eski kayıt çevrimdışı kurulum kanıtı değildir.")
        require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
        val os=read(root,"usr/lib/os-release").lineSequence().filter{it.contains('=')}.associate{it.substringBefore('=') to it.substringAfter('=').trim('"')}
        require(os["ID"]=="ubuntu" && os["VERSION_CODENAME"]=="noble"){"APT kilidi yalnız Ubuntu noble için geçerli."}
        val locks=directory.listFiles().orEmpty().toList()
        require(locks.size<=100 && locks.all{it.name.matches(Regex("[a-f0-9]{64}")) && it.isDirectory && !Files.isSymbolicLink(it.toPath())}){"Yarım veya bozuk APT kilidi var."}
        val packages=linkedMapOf<String,Pair<Long,String>>();val versions=linkedSetOf<String>()
        val installed=installedVersions(read(root,"var/lib/dpkg/status",32L*1024*1024))
        for(lock in locks) {
            val prefix=lock.relativeTo(root).invariantSeparatorsPath+"/"
            val manifest=checksumRecords(read(root,prefix+"manifest.sha256"))
            require(manifest.keys==required){"APT kilit manifesti eksik veya bilinmeyen alan içeriyor."}
            require(VerifiedModuleDownload.sha(safe(root,prefix+"requests.txt"))==lock.name){"APT istek kimliği uyuşmuyor."}
            manifest.forEach{(path,hash)->require(VerifiedModuleDownload.sha(safe(root,prefix+path))==hash){"APT kilit bütünlüğü: $path"}}
            val identity=read(root,prefix+"identity").trim()
            val match=requireNotNull(Regex("schema=1 module=${id.name} ubuntu=noble arch=(amd64|arm64) snapshot=$SNAPSHOT").matchEntire(identity)){"APT dağıtım/mimari/snapshot uyuşmuyor."}
            val arch=match.groupValues[1]
            require(installed.values.any{it.second==arch}){"Kurulu APT mimarisi uyuşmuyor."}
            require(read(root,prefix+"profile.sha256").trim()==PROFILE && read(root,prefix+"catalog.sha256").trim()==CATALOG)
            require(read(root,prefix+"verification").trim()=="APT_NO_DOWNLOAD_INSTALLED")
            val store="opt/appforge-device/apt-snapshot-v1/$arch-$SNAPSHOT/"
            require(read(root,prefix+"metadata.sha256").trim()==VerifiedModuleDownload.sha(safe(root,store+"indexes.sha256")))
            for((suite,hash) in releases)require(VerifiedModuleDownload.sha(safe(root,store+"lists/snapshot.ubuntu.com_ubuntu_${SNAPSHOT}_dists_${suite}_InRelease"))==hash){"İmzalı Ubuntu repository kimliği uyuşmuyor."}
            for((path,hash) in checksumRecords(read(root,store+"indexes.sha256")))require(VerifiedModuleDownload.sha(safe(root,store+"lists/"+path))==hash){"APT metadata değişmiş."}
            for(line in read(root,prefix+"installed.tsv").lineSequence().filter{it.isNotBlank()}) {
                val f=line.split('\t');require(f.size==3 && installed[f[0]]==Pair(f[1],f[2])){"Ortak APT sürüm çakışması: ${f.firstOrNull()}"}
            }
            for(line in read(root,prefix+"packages.tsv").lineSequence().filter{it.isNotBlank()}) {
                val f=line.split('\t');require(f.size==6 && f[0].matches(Regex("[a-z0-9][a-z0-9+.-]+")) && f[2] in setOf(arch,"all") && f[3].matches(Regex("[a-f0-9]{64}")))
                val bytes=f[4].toLong();require(bytes>0 && f[5].startsWith("https://snapshot.ubuntu.com/ubuntu/$SNAPSHOT/pool/") && !f[5].contains(".."))
                val file=safe(root,store+"archives/"+f[3]+".deb")
                require(file.length()==bytes && VerifiedModuleDownload.sha(file)==f[3]){"APT arşivi eksik veya SHA-256 bozuk: ${f[0]}"}
                packages[f[3]]=bytes to f[0];versions.add("${f[0]} ${f[1]} (${f[2]})")
            }
            for((path,hash) in checksumRecords(read(root,prefix+"installed-files.sha256")))require(VerifiedModuleDownload.sha(safe(root,path.removePrefix("/")))==hash){"Kurulu APT dosyası değişmiş: $path"}
        }
        return Summary(locks.size,packages.size,packages.values.sumOf{it.first},versions.toList(),"Snapshot $SNAPSHOT • ${locks.size} kilit • ${packages.size} tekil .deb • APT --no-download kurulum ve dosya bütünlüğü doğrulandı. Tam motor kabulü ayrıca gerekir.")
    }
    internal fun installedVersions(status:String):Map<String,Pair<String,String>> = status.split(Regex("\\r?\\n\\r?\\n")).mapNotNull {paragraph->
        val fields=paragraph.lineSequence().filter{it.contains(": ") && !it.startsWith(" ")}.associate{it.substringBefore(": ") to it.substringAfter(": ")}
        if(fields["Status"]!="install ok installed")null else fields.getValue("Package") to (fields.getValue("Version") to fields.getValue("Architecture"))
    }.toMap()
    private fun checksumRecords(text:String):Map<String,String> {
        val result=linkedMapOf<String,String>()
        text.lineSequence().filter{it.isNotBlank()}.forEach{line->require(line.length>66 && line.substring(0,64).matches(Regex("[a-f0-9]{64}")) && line.substring(64,66)=="  ");val path=line.substring(66);require(result.put(path,line.substring(0,64))==null)}
        return result
    }
    private fun read(root:File,path:String,max:Long=16L*1024*1024):String=safe(root,path).also{require(it.length()<=max)}.readText()
    private fun safe(root:File,path:String,file:Boolean=true):File {
        require(path.isNotBlank() && !File(path).isAbsolute && path.split('/').none{it in setOf("",".","..")})
        val base=root.canonicalFile;val target=File(base,path)
        require(target.canonicalFile.toPath().startsWith(base.toPath()) && !Files.isSymbolicLink(target.toPath()) && target.absoluteFile==target.canonicalFile){"Güvensiz APT sahiplik yolu."}
        if(file)require(target.isFile);return target
    }
}
