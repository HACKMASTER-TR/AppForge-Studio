package com.appforge.studio.build

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction

/** Edits only the single pinned shell color and package identity. Never synthesizes resources. */
internal object FastHtmlResourceColor {
    private const val STUB = "com.appforge.htmlstub"
    private const val KEY = "appforge_primary"
    private const val ORIGINAL = 0xff6b7cffL
    private const val LIMIT = 8 * 1024 * 1024

    fun argb(text: String): Long {
        require(text.matches(Regex("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?")))
        return text.substring(1).toLong(16).let { if(text.length == 7) it or 0xff000000L else it }
    }
    private fun packageName(name: String) {
        require(name.length in 3..127 && name != STUB && name.matches(Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")))
    }
    private fun bits(b: ByteArray, p: Int): Int {
        require(p >= 0 && p.toLong()+4 <= b.size)
        return ByteBuffer.wrap(b,p,4).order(ByteOrder.LITTLE_ENDIAN).int
    }
    private fun n(b: ByteArray,p: Int) = bits(b,p).also { require(it >= 0) }
    private fun s(b: ByteArray,p: Int): Int { require(p >= 0 && p.toLong()+2 <= b.size);return (b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8) }
    private fun put(b: ByteArray,p: Int,value: Int) { ByteBuffer.wrap(b,p,4).order(ByteOrder.LITTLE_ENDIAN).putInt(value) }
    private fun chunks(b: ByteArray,start: Int,end: Int,visit: (Int,Int,Int)->Unit) {
        var at=start
        while(at<end) {
            require(at.toLong()+8<=end)
            val header=s(b,at+2);val length=n(b,at+4)
            require(header>=8 && length>=header && at.toLong()+length<=end)
            visit(at,header,length);at+=length
        }
        require(at==end)
    }
    private fun pool(b: ByteArray, at: Int): List<String> {
        require(s(b,at)==1 && s(b,at+2)==28)
        val end=at+n(b,at+4);val count=n(b,at+8);val start=at+n(b,at+20)
        require(count in 1..4096 && n(b,at+12)==0 && start>=at+28+4*count && end<=b.size)
        val utf8=n(b,at+16) and 256 != 0
        return (0 until count).map { index ->
            var p=start+n(b,at+28+4*index);require(p in start until end)
            if(utf8) {
                fun len(): Int {require(p<end);val first=b[p++].toInt() and 255;return if(first and 128==0)first else {require(p<end);((first and 127) shl 8) or (b[p++].toInt() and 255)} }
                len();val length=len();require(p.toLong()+length<end && b[p+length]==0.toByte())
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b,p,length)).toString()
            } else {
                val length=s(b,p);p+=2;require(length<32768 && p.toLong()+length*2+2<=end && s(b,p+length*2)==0)
                String(b,p,length*2,Charsets.UTF_16LE)
            }
        }
    }
    fun binary(input: ByteArray, primary: String, packageName: String): ByteArray {
        packageName(packageName);val color=argb(primary);val b=input.clone()
        require(b.size in 64..LIMIT && s(b,0)==2 && s(b,2)==12 && n(b,4)==b.size && n(b,8)==1)
        var packages=0;var colors=0
        chunks(b,12,b.size) { pkg,header,length -> if(s(b,pkg)==0x200) {
            require(++packages==1 && header>=288 && n(b,pkg+8)==0x7f && n(b,pkg+284)==0)
            val old=String(b,pkg+12,256,Charsets.UTF_16LE).substringBefore('\u0000')
            require(old==STUB)
            val types=pool(b,pkg+n(b,pkg+268));val keys=pool(b,pkg+n(b,pkg+276))
            val encoded=packageName.toByteArray(Charsets.UTF_16LE)
            java.util.Arrays.fill(b,pkg+12,pkg+268,0.toByte());encoded.copyInto(b,pkg+12)
            chunks(b,pkg+header,pkg+length) { at,typeHeader,typeLength -> if(s(b,at)==0x201) {
                val type=b[at+8].toInt() and 255
                require(type in 1..types.size)
                if(types[type-1]=="color") {
                    require(b[at+9].toInt()==0) { "Unsupported sparse/compact type offsets" }
                    val count=n(b,at+12);val data=at+n(b,at+16)
                    require(count in 1..4096 && at.toLong()+typeHeader+4L*count<=data && data<=at+typeLength)
                    repeat(count) { index ->
                        val offset=bits(b,at+typeHeader+index*4)
                        if(offset != -1) {
                            require(offset>=0 && data.toLong()+offset+8<=at+typeLength)
                            val entry=data+offset;val size=s(b,entry);val flags=s(b,entry+2)
                            require(flags and 9==0 && size==8)
                            val key=n(b,entry+4);require(key in keys.indices)
                            if(keys[key]==KEY) {
                                require(++colors==1 && entry+16<=at+typeLength && s(b,entry+8)==8)
                                require((b[entry+11].toInt() and 255) in 0x1c..0x1f)
                                require((bits(b,entry+12).toLong() and 0xffffffffL)==ORIGINAL)
                                b[entry+11]=0x1c;put(b,entry+12,color.toInt())
                            }
                        }
                    }
                }
            } }
        } }
        require(packages==1 && colors==1) { "Missing/duplicate pinned primary resource" }
        return b
    }

    /** AAPT2 may shorten APK paths; resolve the single icon ID through the compiled table. */
    fun binaryIconPath(b: ByteArray): String {
        require(b.size in 64..LIMIT && s(b,0)==2 && s(b,2)==12 && n(b,4)==b.size && n(b,8)==1)
        var global: List<String>?=null;var found: String?=null;var packages=0
        chunks(b,12,b.size) { pkg,header,length ->
            if(s(b,pkg)==1) {require(global==null);global=pool(b,pkg)}
            if(s(b,pkg)==0x200) {
                require(++packages==1 && header>=288 && n(b,pkg+8)==0x7f)
                val types=pool(b,pkg+n(b,pkg+268));val keys=pool(b,pkg+n(b,pkg+276))
                chunks(b,pkg+header,pkg+length) { at,typeHeader,typeLength -> if(s(b,at)==0x201) {
                    val type=b[at+8].toInt() and 255;require(type in 1..types.size)
                    if(types[type-1]=="drawable") {
                        require(b[at+9].toInt()==0)
                        val count=n(b,at+12);val data=at+n(b,at+16)
                        require(count in 1..4096 && at.toLong()+typeHeader+4L*count<=data && data<=at+typeLength)
                        repeat(count) { index ->
                            val offset=bits(b,at+typeHeader+index*4)
                            if(offset!=-1) {
                                require(offset>=0 && data.toLong()+offset+16<=at+typeLength)
                                val entry=data+offset;require(s(b,entry)==8 && s(b,entry+2) and 9==0)
                                val key=n(b,entry+4);require(key in keys.indices)
                                if(keys[key]=="appforge_icon") {
                                    require(found==null && s(b,entry+8)==8 && b[entry+11].toInt()==3)
                                    val path=global?.getOrNull(n(b,entry+12)) ?: error("Icon path string missing")
                                    require(path.matches(Regex("res/[A-Za-z0-9_/.-]+\\.png")) && !path.contains(".."))
                                    found=path
                                }
                            }
                        }
                    }
                } }
            }
        }
        require(packages==1);return requireNotNull(found) { "Pinned drawable/appforge_icon missing." }
    }

    private data class Field(val id: Int,val wire: Int,val bytes: ByteArray,val raw: ByteArray,val number: Long=0)
    private fun fields(b: ByteArray): List<Field> {
        require(b.size<=LIMIT);var at=0;val out=ArrayList<Field>()
        fun number(): Long {var value=0L;for(i in 0..9) {require(at<b.size);val v=b[at++].toInt() and 255;require(i!=9 || v<=1);value=value or ((v and 127).toLong() shl (i*7));if(v and 128==0){require(i==0 || v!=0);return value}};error("varint")}
        fun take(size: Long): ByteArray {require(size>=0 && size<=b.size-at);return b.copyOfRange(at,at+size.toInt()).also {at+=size.toInt()} }
        while(at<b.size) {
            require(out.size<65536);val start=at;val tag=number();require(tag ushr 3 in 1L..536870911L);val id=(tag ushr 3).toInt();val wire=(tag and 7).toInt();require(id in 1..536870911)
            var value=0L
            val bytes=when(wire) {0->{value=number();byteArrayOf()};1->take(8);2->take(number());5->take(4);else->error("wire")}
            out+=Field(id,wire,bytes,b.copyOfRange(start,at),value)
        };return out
    }
    private fun number(value: Long): ByteArray = ByteArrayOutputStream().also {out->var v=value;while(v and -128L != 0L){out.write((v.toInt() and 127) or 128);v=v ushr 7};out.write(v.toInt())}.toByteArray()
    private fun field(id: Int, bytes: ByteArray)=number(id*8L+2)+number(bytes.size.toLong())+bytes
    private fun string(fs: List<Field>,id: Int): String {
        val f=fs.single {it.id==id};require(f.wire==2)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(f.bytes)).toString()
    }
    private fun update(input: ByteArray, transform: (Field)->ByteArray): ByteArray = ByteArrayOutputStream().also {out->fields(input).forEach {out.write(transform(it))}}.toByteArray()
    fun proto(input: ByteArray, primary: String, packageName: String): ByteArray {
        packageName(packageName);val color=argb(primary);var packages=0;var colors=0
        require(input.size in 1..LIMIT)
        val result=update(input) {pkg->if(pkg.id!=2)pkg.raw else {
            require(pkg.wire==2 && ++packages==1)
            val pf=fields(pkg.bytes);require(string(pf,2)==STUB)
            field(2,update(pkg.bytes) {type->when(type.id) {
                2->field(2,packageName.toByteArray())
                3->{require(type.wire==2);val tf=fields(type.bytes)
                    if(string(tf,2)!="color")type.raw else field(3,update(type.bytes) {entry->
                        if(entry.id!=3)entry.raw else {require(entry.wire==2);val ef=fields(entry.bytes)
                            if(string(ef,2)!=KEY)entry.raw else {
                                require(++colors==1 && ef.count {it.id==6}==1)
                                field(3,update(entry.bytes) {config->if(config.id!=6)config.raw else field(6,update(config.bytes) {value->
                                    if(value.id!=2)value.raw else field(2,update(value.bytes) {item->
                                        if(item.id!=4)item.raw else {
                                            val items=fields(item.bytes);require(items.size==1 && items[0].id==7 && items[0].wire==2)
                                            val prim=fields(items[0].bytes);require(prim.size==1 && prim[0].id in 9..12 && prim[0].wire==0 && prim[0].number==ORIGINAL)
                                            field(4,field(7,number(9*8L)+number(color)))
                                        }
                                    })
                                })})
                            }
                        }
                    })
                }
                else->type.raw
            }})
        }}
        require(packages==1 && colors==1 && result.size<=LIMIT) { "Missing/duplicate pinned primary resource" }
        return result
    }
}
