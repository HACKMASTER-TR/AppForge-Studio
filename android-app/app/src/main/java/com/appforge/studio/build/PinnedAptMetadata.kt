package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream

/** Read-only readiness check. Pins must be the compiled Ubuntu InRelease anchors.
 * The installer additionally executes Ubuntu gpgv before every APT mutation.
 */
internal object PinnedAptMetadata {
    private const val MAX = 128L * 1024 * 1024
    private val components=listOf("main","restricted","universe","multiverse")
    private val dateFormat=DateTimeFormatter.ofPattern("EEE, d MMM uuuu HH:mm:ss z",Locale.US)
    fun verify(lists:File,arch:String,pins:Map<String,String>,now:Long=System.currentTimeMillis()) {
        require(arch in setOf("amd64","arm64") && pins.keys==setOf("noble","noble-updates","noble-security","noble-backports"))
        require(now>=1792281600000L) { "Device clock precedes pinned snapshot" }
        for((suite,pin) in pins) {
            val prefix="snapshot.ubuntu.com_ubuntu_${AptModuleLocks.SNAPSHOT}_dists_${suite}_"
            val release=safe(lists,prefix+"InRelease")
            require(release.length()<=2L*1024*1024 && VerifiedModuleDownload.sha(release)==pin) { "Pinned Ubuntu release changed" }
            val lines=release.readLines()
            fun field(name:String):String {
                val values=lines.filter{it.startsWith("$name: ")}
                require(values.size==1){"Missing/duplicate signed $name"}
                return values.single().substringAfter(": ")
            }
            require(field("Origin")=="Ubuntu" && field("Codename")=="noble" && field("Suite")==suite)
            require(arch in field("Architectures").split(' ') && components.all{it in field("Components").split(' ')})
            val date=ZonedDateTime.parse(field("Date"),dateFormat).toInstant().toEpochMilli()
            require(date>0 && date<=now+600000) { "Future signed metadata" }
            if(lines.any{it.startsWith("Valid-Until:")}) {
                val until=ZonedDateTime.parse(field("Valid-Until"),dateFormat).toInstant().toEpochMilli()
                require(until>=date && now<=until) { "Expired signed metadata" }
            }
            require(lines.count{it=="SHA256:"}==1)
            val table=lines.dropWhile{it!="SHA256:"}.drop(1).takeWhile{it.startsWith(" ")}
            for(component in components) {
                val name="$component/binary-$arch/Packages"
                val records=table.map{it.trim().split(Regex("\\s+"))}.filter{it.size==3 && it[2]==name}
                require(records.size==1){"Missing/duplicate signed Packages entry"}
                val record=records.single()
                require(record[0].matches(Regex("[a-f0-9]{64}")))
                val bytes=record[1].toLong();require(bytes in 0..MAX)
                val raw=File(lists,prefix+"${component}_binary-${arch}_Packages")
                val compressed=File(raw.path+".lz4")
                require(raw.exists() xor compressed.exists()) { "Missing or ambiguous signed Packages index" }
                val file=safe(lists,if(raw.exists())raw.name else compressed.name)
                require(file.length()<=MAX)
                val digest=MessageDigest.getInstance("SHA-256")
                var count=0L
                file.inputStream().let{if(raw.exists())it else FramedLZ4CompressorInputStream(it,true)}.use { input ->
                    val buffer=ByteArray(65536)
                    while(true) {
                        val n=input.read(buffer);if(n<0)break
                        count+=n;require(count<=bytes) { "Signed Packages size exceeded" }
                        digest.update(buffer,0,n)
                    }
                }
                require(count==bytes && digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}==record[0]) {
                    "Signed Packages size/SHA-256 mismatch"
                }
            }
        }
    }
    private fun safe(parent:File,name:String):File {
        val file=File(parent,name)
        require(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.canonicalFile.parentFile==parent.canonicalFile)
        return file
    }
}
