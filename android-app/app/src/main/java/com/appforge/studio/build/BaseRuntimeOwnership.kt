package com.appforge.studio.build
import java.io.File
import java.nio.file.*
import org.json.JSONArray
import org.json.JSONObject

/** Captured only from a freshly verified vendor root; never bless an existing user-modified runtime. */
internal object BaseRuntimeOwnership {
    const val FILE=".appforge-base-owned-v5.json"
    fun captureFresh(root:File) {
        val target=File(root,FILE);require(!target.exists() && !Files.isSymbolicLink(target.toPath()))
        val paths=RuntimeGenerationStore(root).snapshot().keys.filter{it.isNotEmpty()}
        val bytes=JSONObject().put("schema",1).put("paths",JSONArray(paths)).toString().toByteArray();require(bytes.size<=16*1024*1024)
        val part=File(target.path+".part");require(!Files.isSymbolicLink(part.toPath()))
        java.io.FileOutputStream(part).use{it.write(bytes);it.fd.sync()}
        Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)
    }
    fun paths(root:File):Set<String> {
        val f=File(root,FILE);if(!f.exists())return emptySet()
        require(f.isFile && !Files.isSymbolicLink(f.toPath()) && f.length()<=16L*1024*1024)
        val j=JSONObject(f.readText());require(j.getInt("schema")==1);val entries=j.getJSONArray("paths")
        return (0 until entries.length()).map{entries.getString(it)}.onEach{require(it.isNotEmpty() && !File(it).isAbsolute && it.split('/').none{p->p==".."||p=="."})}.toSet()
    }
}
