package com.appforge.studio.build

import java.io.FilterInputStream
import java.net.HttpURLConnection

/** Counts body bytes read from a real response, including retries; excludes resume offsets/cache copies. */
internal class MeasuredDownloadConnection(private val delegate:HttpURLConnection,private val onResponse:()->Unit={},private val received:(Long)->Unit):HttpURLConnection(delegate.url) {
    override fun connect()=delegate.connect()
    override fun disconnect(){delegate.disconnect();DownloadNetwork.release(delegate)}
    override fun usingProxy()=delegate.usingProxy()
    override fun setConnectTimeout(value:Int){delegate.connectTimeout=value}
    override fun setReadTimeout(value:Int){delegate.readTimeout=value}
    override fun setInstanceFollowRedirects(value:Boolean){delegate.instanceFollowRedirects=value}
    override fun setRequestProperty(key:String,value:String){delegate.setRequestProperty(key,value)}
    private var reportedResponse=false
    override fun getResponseCode():Int {
        val code=delegate.responseCode
        if(!reportedResponse){reportedResponse=true;onResponse()}
        return code
    }
    override fun getHeaderField(name:String?)=delegate.getHeaderField(name)
    override fun getContentLengthLong()=delegate.contentLengthLong
    override fun getInputStream()=object:FilterInputStream(delegate.inputStream) {
        override fun read():Int=super.read().also{if(it>=0)received(1)}
        override fun read(bytes:ByteArray,offset:Int,length:Int):Int=`in`.read(bytes,offset,length).also{if(it>0)received(it.toLong())}
    }
}
