package com.appforge.studio.build

import java.io.File
import java.nio.file.Files

/** Bounded independent atomic channels; parallel resolvers must not erase each other's counters. */
internal class ExternalTelemetryChannels(
    private val primary:File,
    module:OfflineModuleId,
    operation:String
) {
    private val observer=ExternalDownloadTelemetry(module,operation)
    private val previous=mutableMapOf<String,String>()
    fun poll(now:Long):List<ModuleProgress> {
        val candidates=mutableListOf(primary)
        for(suffix in listOf(".events",".apt-events")) {
            val directory=File(primary.path+suffix)
            if(Files.isSymbolicLink(directory.toPath()))continue
            candidates.addAll(directory.listFiles().orEmpty().filter {
                it.name.matches(Regex("[A-Za-z0-9_-]{1,160}[.]json"))
            }.sortedByDescending{it.lastModified()}.take(128))
        }
        val selected=candidates.filter{!Files.isSymbolicLink(it.toPath()) && it.isFile && it.length() in 1L..2048L}
            .sortedByDescending{it.lastModified()}.take(128).sortedBy{it.lastModified()}
        previous.keys.retainAll(selected.map{it.path}.toSet())
        val events=linkedSetOf<String>()
        for(file in selected)runCatching {
            val text=file.readText()
            if(text!=previous[file.path]){previous[file.path]=text;events.add(text)}
        }
        return events.mapNotNull{observer.accept(it,now)}
    }
    companion object {
        /** Only private generated telemetry, never sources/cache/keys. No recursive deletion. */
        fun clear(primary:File) {
            require(!Files.isSymbolicLink(primary.toPath()))
            Files.deleteIfExists(primary.toPath())
            for(suffix in listOf(".events",".apt-events")) {
                val directory=File(primary.path+suffix)
                require(!Files.isSymbolicLink(directory.toPath()))
                directory.listFiles().orEmpty().filter {
                    it.name.matches(Regex("[A-Za-z0-9_-]{1,160}[.]json([.]part)?")) && !it.isDirectory
                }.forEach{Files.deleteIfExists(it.toPath())}
            }
        }
    }
}
