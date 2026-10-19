package com.appforge.studio.build

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** A durable prefix checkpoint, never a final source-integrity receipt. */
internal object DownloadPartialCheckpoint {
    private fun safe(vararg files:File){files.forEach{require(!Files.isSymbolicLink(it.toPath()))}}
    fun write(part:File,bytes:Long,digest:String,source:String,expected:String) {
        require(bytes>0 && bytes<=part.length() && listOf(digest,source,expected).all{it.matches(Regex("[a-f0-9]{64}"))})
        val file=File(part.path+".checkpoint");val temp=File(file.path+".tmp");safe(part,file,temp)
        FileOutputStream(temp).use{it.write("$bytes\n$digest\n$source\n$expected".toByteArray(Charsets.UTF_8));it.fd.sync()}
        Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    fun recover(part:File,source:String,expected:String,maxBytes:Long,trim:Boolean=false):Long? {
        val file=File(part.path+".checkpoint");safe(part,file)
        if(!file.isFile || file.length() !in 1..256 || !part.isFile || part.length()>maxBytes)return null
        val fields=file.readText().split('\n');if(fields.size!=4 || fields[2]!=source || fields[3]!=expected || !fields[1].matches(Regex("[a-f0-9]{64}")))return null
        val bytes=fields[0].toLongOrNull()?.takeIf{it in 1..part.length() && it<=maxBytes}?:return null
        val digest=MessageDigest.getInstance("SHA-256")
        part.inputStream().use{input->var remaining=bytes;val buffer=ByteArray(65536);while(remaining>0){val n=input.read(buffer,0,minOf(remaining,buffer.size.toLong()).toInt());if(n<=0)return null;digest.update(buffer,0,n);remaining-=n}}
        if(digest.digest().joinToString(""){"%02x".format(it)}!=fields[1])return null
        if(trim && part.length()>bytes)RandomAccessFile(part,"rw").use{it.setLength(bytes);it.fd.sync()}
        return bytes
    }
}
