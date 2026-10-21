package com.appforge.studio.build
import org.junit.Assert.*
import org.junit.Test

class RustNativeManifestTest {
    private fun number(n:Int):ByteArray {var v=n;val result=mutableListOf<Byte>();do{var b=v and 127;v=v ushr 7;if(v!=0)b=b or 128;result.add(b.toByte())}while(v!=0);return result.toByteArray()}
    private fun field(id:Int,data:ByteArray)=number((id shl 3) or 2)+number(data.size)+data
    private fun text(id:Int,value:String)=field(id,value.toByteArray())
    private fun manifest(min:Int=26,raw:String=min.toString(),duplicate:Boolean=false):ByteArray {
        val primitive=number(6 shl 3)+number(min)
        val attr=text(1,"http://schemas.android.com/apk/res/android")+text(2,"minSdkVersion")+text(3,raw)+field(6,field(7,primitive))
        val sdk=text(3,"uses-sdk")+field(4,attr)+(if(duplicate)field(4,attr)else byteArrayOf())
        return field(1,text(3,"manifest")+field(5,field(1,sdk)))
    }
    @Test fun actualCompiledSdkReturned(){assertEquals(26,FastHtmlAabProtoManifest.minimumSdkVersion(manifest()));assertEquals(37,FastHtmlAabProtoManifest.minimumSdkVersion(manifest(37)))}
    @Test fun lowRealSdkIsNotReplacedByDraft(){assertEquals(21,FastHtmlAabProtoManifest.minimumSdkVersion(manifest(21)))}
    @Test fun spoofedRawValueRejected(){assertThrows(IllegalArgumentException::class.java){FastHtmlAabProtoManifest.minimumSdkVersion(manifest(21,"26"))}}
    @Test fun duplicateSdkRejected(){assertThrows(IllegalArgumentException::class.java){FastHtmlAabProtoManifest.minimumSdkVersion(manifest(duplicate=true))}}
    @Test fun malformedProtoRejected(){assertThrows(IllegalArgumentException::class.java){FastHtmlAabProtoManifest.minimumSdkVersion(byteArrayOf(10,127,0))}}
}
