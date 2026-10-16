import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'../..');
const build=path.join(root,'android-app/app/src/main/java/com/appforge/studio/build');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function number(n){const out=[];do{let b=n&127;n>>>=7;if(n)b|=128;out.push(b);}while(n);return Buffer.from(out);}
function field(id,data){return Buffer.concat([number(id*8+2),number(data.length),data]);}
function parse(bytes){let p=0;function n(){let v=0,s=0,b;do{b=bytes[p++];v|=(b&127)<<s;s+=7;}while(b&128);return v;}const out=[];while(p<bytes.length){const start=p,t=n();let data;if((t&7)===2){const l=n();data=bytes.subarray(p,p+l);p+=l;}else if((t&7)===0)n();else throw Error('wire');out.push({id:t>>>3,data,raw:bytes.subarray(start,p)});}return out;}
const attr=(name,value)=>field(4,Buffer.concat([field(1,Buffer.from('http://schemas.android.com/apk/res/android')),field(2,Buffer.from(name)),field(3,Buffer.from(value))]));
function withProvider(original,{exported='false',duplicate=false,authority='com.appforge.htmlstub.appforge.documents'}={}){
 const provider=field(5,field(1,Buffer.concat([field(3,Buffer.from('provider')),attr('name','com.appforge.htmlstub.AppForgeShareProvider'),attr('authorities',authority),attr('exported',exported),attr('grantUriPermissions','true')])));
 function rewrite(node){return Buffer.concat(parse(node).map(f=>{if(f.id===1)return field(1,rewriteElement(f.data));return f.raw;}));}
 function rewriteElement(element){const f=parse(element);if(f.find(x=>x.id===3)?.data.toString()==='application')return Buffer.concat([element,provider,...(duplicate?[provider]:[])]);return Buffer.concat(f.map(x=>x.id===5?field(5,rewrite(x.data)):x.raw));}
 return rewrite(original);
}
function binary({exported=false,duplicate=false}={}){
 const strings=['manifest','package','com.appforge.htmlstub','versionCode','versionName','1.0.0','uses-sdk','minSdkVersion','targetSdkVersion','application','label','APPFORGE_HTML_APP','provider','name','com.appforge.htmlstub.AppForgeShareProvider','authorities','com.appforge.htmlstub.appforge.documents','exported','grantUriPermissions'];
 const index=s=>strings.indexOf(s), blocks=[],offsets=[];let size=0;
 for(const s of strings){offsets.push(size);const b=Buffer.from(s,'utf16le'),v=Buffer.alloc(b.length+4);v.writeUInt16LE(s.length);b.copy(v,2);blocks.push(v);size+=v.length;}
 const pool=Buffer.alloc(Math.ceil((28+strings.length*4+size)/4)*4);pool.writeUInt16LE(1);pool.writeUInt16LE(28,2);pool.writeUInt32LE(pool.length,4);pool.writeUInt32LE(strings.length,8);pool.writeUInt32LE(28+strings.length*4,20);offsets.forEach((v,i)=>pool.writeUInt32LE(v,28+i*4));Buffer.concat(blocks).copy(pool,28+strings.length*4);
 function element(name,attrs){const b=Buffer.alloc(36+attrs.length*20);b.writeUInt16LE(0x102);b.writeUInt16LE(16,2);b.writeUInt32LE(b.length,4);b.writeUInt32LE(index(name),20);b.writeUInt16LE(20,24);b.writeUInt16LE(20,26);b.writeUInt16LE(attrs.length,28);attrs.forEach(([key,type,value],i)=>{const a=36+i*20;b.writeUInt32LE(index(key),a+4);b.writeUInt16LE(8,a+12);b[a+15]=type;b.writeUInt32LE(type===3?index(value):value,a+16);});return b;}
 const provider=element('provider',[['name',3,'com.appforge.htmlstub.AppForgeShareProvider'],['authorities',3,'com.appforge.htmlstub.appforge.documents'],['exported',0x12,exported?1:0],['grantUriPermissions',0x12,1]]);
 const chunks=[pool,element('manifest',[['package',3,'com.appforge.htmlstub'],['versionCode',0x10,1],['versionName',3,'1.0.0']]),element('uses-sdk',[['minSdkVersion',0x10,26],['targetSdkVersion',0x10,37]]),element('application',[['label',3,'APPFORGE_HTML_APP']]),provider,...(duplicate?[provider]:[])];
 const out=Buffer.concat([Buffer.alloc(8),...chunks]);out.writeUInt16LE(3);out.writeUInt16LE(8,2);out.writeUInt32LE(out.length,4);return out;
}
test('production Kotlin writers isolate provider authority and reject insecure/colliding templates',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'template-rewrite-'));
 try{
  const fixture=fs.readFileSync(path.join(root,'android-app/app/src/test/java/com/appforge/studio/build/FastHtmlAabProtoManifestTest.kt'),'utf8').match(/PINNED_PROTO_B64 = """([\s\S]*?)"""/)[1].replace(/\s/g,'');
  const original=Buffer.from(fixture,'base64');
  for(const [name,b] of Object.entries({good:binary(),exported:binary({exported:true}),duplicate:binary({duplicate:true})}))fs.writeFileSync(path.join(dir,name+'.xml'),b);
  const protos={good:withProvider(original),exported:withProvider(original,{exported:'true'}),duplicate:withProvider(original,{duplicate:true}),collision:withProvider(original,{authority:'shared.authority'})};
  for(const [name,b] of Object.entries(protos))fs.writeFileSync(path.join(dir,name+'.pb'),b);
  fs.writeFileSync(path.join(dir,'BuildConfig.kt'),'package com.appforge.studio\nobject BuildConfig { const val DEBUG=true }');
  fs.writeFileSync(path.join(dir,'Check.kt'),`package com.appforge.studio.build
import java.io.File
import com.appforge.studio.model.ProjectDraft
fun main(args:Array<String>){
 val dir=File(args[0]); val base=ProjectDraft(appName="Share")
 fun edit(bytes:ByteArray,pkg:String)=HtmlBinaryManifest.rewrite(bytes,pkg,"Share",2,"2.0",26,37)
 val binary=File(dir,"good.xml").readBytes()
 check(!edit(binary,"com.test.first").contentEquals(edit(binary,"com.test.second")))
 check(runCatching {edit(binary,"com.appforge.htmlstub")}.isFailure)
 listOf("exported","duplicate").forEach { check(runCatching {edit(File(dir,"$it.xml").readBytes(),"com.test.first")}.isFailure) }
 val p=File(dir,"good.pb").readBytes()
 val first=FastHtmlAabProtoManifest.rewrite(p,base.copy(packageName="com.test.first"),"${sha(protos.good)}")
 val second=FastHtmlAabProtoManifest.rewrite(p,base.copy(packageName="com.test.second"),"${sha(protos.good)}")
 check(!first.contentEquals(second));check(first.toString(Charsets.UTF_8).contains("com.test.first.appforge.documents"))
 check(!first.toString(Charsets.UTF_8).contains("com.appforge.htmlstub.appforge.documents"))
 ${['exported','duplicate','collision'].map(name=>`check(runCatching {FastHtmlAabProtoManifest.rewrite(File(dir,"${name}.pb").readBytes(),base,"${sha(protos[name])}")}.isFailure)`).join('\n')}
 check(runCatching {FastHtmlAabProtoManifest.rewrite(p,base,"0".repeat(64))}.isFailure)
 println("template identity checks PASS")
}`);
  execFileSync('kotlinc',[path.join(root,'android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt'),path.join(build,'HtmlBinaryManifest.kt'),path.join(build,'FastHtmlAabProtoManifest.kt'),path.join(dir,'BuildConfig.kt'),path.join(dir,'Check.kt'),'-include-runtime','-d',path.join(dir,'check.jar')],{timeout:120000,stdio:'pipe'});
  assert.match(execFileSync('java',['-jar',path.join(dir,'check.jar'),dir],{encoding:'utf8'}),/checks PASS/);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
