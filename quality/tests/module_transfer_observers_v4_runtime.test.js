import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {spawn,spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const assets=new URL('../../android-app/app/src/main/assets/device-build/',import.meta.url);
const temporary=()=>fs.mkdtempSync(path.join(os.tmpdir(),'appforge-v4-observer-'));
test('Node preload preserves response bytes and reports known/unknown size without leaking URL query',async()=>{
 const dir=temporary(),body=Buffer.alloc(65536,65);
 const server=http.createServer((req,res)=>{if(req.url.startsWith('/known'))res.setHeader('Content-Length',body.length);res.write(body.subarray(0,1000));setTimeout(()=>res.end(body.subarray(1000)),300);});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 try{
  for(const known of [true,false]){
   const file=path.join(dir,'event.json');
   const code=`const http=require('http'),fs=require('fs');http.get(process.argv[1],r=>{let bytes=0;r.on('data',c=>bytes+=c.length);r.on('end',()=>console.log(JSON.stringify({bytes,event:JSON.parse(fs.readFileSync(process.env.APPFORGE_DOWNLOAD_TELEMETRY))})));});`;
   const child=spawn(process.execPath,['--require',new URL('npm-download-telemetry.cjs',assets).pathname,'-e',code,`http://127.0.0.1:${server.address().port}/${known?'known':'unknown'}?private-query=test`],{env:{...process.env,APPFORGE_DOWNLOAD_TELEMETRY:file}});
   let output='',error='';child.stdout.on('data',b=>output+=b);child.stderr.on('data',b=>error+=b);
   assert.equal(await new Promise(r=>child.on('exit',r)),0,error);
   const result=JSON.parse(output);assert.equal(result.bytes,body.length);assert.equal(result.event.bytes,body.length);assert.equal(result.event.finished,true);assert.equal(result.event.total,known?body.length:null);assert(!JSON.stringify(result).includes('private-query'));assert(!('ready'in result.event));
  }
 }finally{await new Promise(r=>server.close(r));fs.rmSync(dir,{recursive:true,force:true});}
});
test('APT observer measures real fixture archive files, verifies SHA256 and preserves backend errors',()=>{
 const dir=temporary();
 try{
  const archives=path.join(dir,'archives');fs.mkdirSync(path.join(archives,'partial'),{recursive:true});const payload='verified-debian-fixture',sha=createHash('sha256').update(payload).digest('hex');
  const backend=path.join(dir,'apt-fixture');fs.writeFileSync(backend,`#!/bin/sh\ncase " $* " in *' --print-uris '*) printf "'https://example.invalid/a.deb' a.deb ${payload.length} SHA256:${sha}\\n";exit 0;; esac\nprintf '${payload}' > '${archives}/partial/a.deb'\nsleep 1\nmv '${archives}/partial/a.deb' '${archives}/a.deb'\nexit 17\n`,{mode:0o700});
  // Redirect only the immutable OS boundary in this test copy; never invoke or mutate host APT.
  const helper=path.join(dir,'observer.sh');fs.writeFileSync(helper,fs.readFileSync(new URL('apt-download-telemetry.sh',assets),'utf8').replaceAll('/usr/bin/apt-get',backend).replaceAll('/var/cache/apt/archives',archives));
  const event=path.join(dir,'event.json'),run=spawnSync('sh',['-c','. "$1"; appforge_apt_get install a','fixture',helper],{env:{...process.env,APPFORGE_DOWNLOAD_TELEMETRY:event},encoding:'utf8'});
  assert.equal(run.status,17,run.stderr);const result=JSON.parse(fs.readFileSync(event));assert.equal(result.bytes,payload.length);assert.equal(result.total,payload.length);assert.equal(result.finished,true);assert(!('ready'in result));
  fs.writeFileSync(path.join(archives,'a.deb'),'tampered');const noDownload=fs.readFileSync(backend,'utf8').replace(`printf '${payload}' > '${archives}/partial/a.deb'\nsleep 1\nmv '${archives}/partial/a.deb' '${archives}/a.deb'\n`,'');fs.writeFileSync(backend,noDownload);fs.rmSync(event);spawnSync('sh',['-c','. "$1"; appforge_apt_get install a','fixture',helper],{env:{...process.env,APPFORGE_DOWNLOAD_TELEMETRY:event}});assert(!fs.existsSync(event),'wrong SHA must not publish completed archive');
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
test('reviewed Google archives have fixed SHA256 and every legacy call is allowlisted',()=>{
 const pins=JSON.parse(fs.readFileSync(new URL('verified-toolchain-sources-v4.json',assets)));const script=fs.readFileSync(new URL('install-toolchain.sh',assets),'utf8');assert.equal(pins.length,4);
 for(const p of pins){assert.match(p.url,/^https:\/\/dl\.google\.com\/android\/repository\//);assert.match(p.sha256,/^[a-f0-9]{64}$/);assert(p.bytes>0);assert(script.includes(`${p.url}) compat_sha256="${p.sha256}"`));}
 assert(script.includes('download_sha256 "$1" "$compat_sha256" "$compat_out"'));
});
