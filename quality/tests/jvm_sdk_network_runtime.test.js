import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {spawn,spawnSync} from 'node:child_process';
const source=new URL('../../android-app/app/src/main/assets/device-build/jvm-telemetry/NetworkByteAgent.java',import.meta.url).pathname;
test('SDK JFR observer measures real socket reads, keeps unknown total and exits with the JVM',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'appforge-jfr-')),payload=Buffer.alloc(123456,80);
 const server=http.createServer((req,res)=>{res.setHeader('Content-Length',payload.length);res.write(payload.subarray(0,1000));setTimeout(()=>res.end(payload.subarray(1000)),250)});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 try {
  const classes=path.join(dir,'classes');fs.mkdirSync(classes);
  const client=path.join(dir,'Client.java');fs.writeFileSync(client,`public class Client {public static void main(String[] args)throws Exception {java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(args[0]).openConnection();try(java.io.InputStream in=c.getInputStream()){System.out.println("PAYLOAD="+in.readAllBytes().length);}Thread.sleep(2500);}}`);
  let result=spawnSync('javac',['--release','17','-d',classes,source,client],{encoding:'utf8'});assert.equal(result.status,0,result.stderr);
  const manifest=path.join(dir,'MANIFEST.MF');fs.writeFileSync(manifest,'Premain-Class: com.appforge.runtime.NetworkByteAgent\n\n');const jar=path.join(dir,'observer.jar');result=spawnSync('jar',['--create','--file',jar,'--manifest',manifest,'-C',classes,'com'],{encoding:'utf8'});assert.equal(result.status,0,result.stderr);
  const output=path.join(dir,'progress.json');const child=spawn('java',[`-javaagent:${jar}`,'-cp',classes,'Client',`http://127.0.0.1:${server.address().port}/metadata`],{env:{...process.env,APPFORGE_DOWNLOAD_TELEMETRY:output}});let stdout='',stderr='';child.stdout.on('data',b=>stdout+=b);child.stderr.on('data',b=>stderr+=b);const timeout=setTimeout(()=>child.kill('SIGKILL'),20000);
  const code=await new Promise(r=>child.on('exit',r));clearTimeout(timeout);assert.equal(code,0,stderr);assert(stdout.includes(`PAYLOAD=${payload.length}`));const event=JSON.parse(fs.readFileSync(output));assert(event.bytes>=payload.length,JSON.stringify(event));assert.equal(event.total,null);assert.equal(event.byteMetric,'wire-bytes');assert.equal(event.finished,true);assert(!('percent'in event));
 }finally{await new Promise(r=>server.close(r));fs.rmSync(dir,{recursive:true,force:true});}
});
