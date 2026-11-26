import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn} from 'node:child_process';
const helper=new URL('../../android-app/app/src/main/assets/device-build/module-download-telemetry.sh',import.meta.url).pathname;
test('curl adapter measures bytes and completion without inventing READY',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'module-telemetry-'));
 try {
  const bin=path.join(dir,'bin');fs.mkdirSync(bin);
  fs.writeFileSync(path.join(bin,'curl'),`#!/bin/sh
while [ "$#" -gt 0 ]; do case "$1" in --dump-header) shift; header="$1";; -o) shift; out="$1";; esac; shift; done
printf 'HTTP/1.1 200 OK\\r\\nContent-Length: 200\\r\\n\\r\\n' > "$header"
head -c 100 /dev/zero > "$out"
sleep 2
head -c 100 /dev/zero >> "$out"
`,{mode:0o700});
  const telemetry=path.join(dir,'progress.json'),output=path.join(dir,'out');
  const fixtureHelper=path.join(dir,'observer.sh');fs.writeFileSync(fixtureHelper,fs.readFileSync(helper,'utf8').replaceAll('/usr/bin/curl',path.join(bin,'curl')));
  const child=spawn('sh',['-c','. "$1"; curl -fL https://example.invalid/test -o "$2"','test',fixtureHelper,output],{env:{...process.env,PATH:bin+':'+process.env.PATH,APPFORGE_DOWNLOAD_TELEMETRY:telemetry},stdio:'ignore'});
  const done=new Promise((resolve,reject)=>{child.on('error',reject);child.on('exit',resolve);});
  let sawPartial=false;
  for(let n=0;n<16;n++){
   await new Promise(r=>setTimeout(r,200));
   if(fs.existsSync(telemetry)){const e=JSON.parse(fs.readFileSync(telemetry,'utf8'));if(e.bytes===100){assert.equal(e.total,200);assert.equal(e.finished,false);sawPartial=true;}}
  }
  assert.equal(await done,0);assert.equal(sawPartial,true);
  const final=JSON.parse(fs.readFileSync(telemetry,'utf8'));
  assert.equal(final.bytes,fs.statSync(output).size);assert.equal(final.total,200);assert.equal(final.finished,true);
  assert.equal('percent' in final,false);assert.equal('ready' in final,false);assert.equal('url' in final,false);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});

test('curl telemetry uses full Content-Range total and never marks failed download complete',async()=>{
 const http=await import('node:http');
 const server=http.createServer((req,res)=>{
  if(req.url==='/range'){res.writeHead(206,{'Content-Length':5,'Content-Range':'bytes 5-9/10'});res.end('56789');}
  else{res.writeHead(200,{'Content-Length':100});res.write('short');setTimeout(()=>res.destroy(),100);}
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'module-curl-real-'));
 try{for(const resume of [true,false]){
  const event=path.join(dir,'event.json'),out=path.join(dir,'out');fs.writeFileSync(out,resume?'01234':'');
  const child=spawn('sh',['-c','. "$1"; if [ "$4" = yes ];then curl -f --continue-at - "$2" -o "$3";else curl -f "$2" -o "$3";fi','test',helper,`http://127.0.0.1:${server.address().port}/${resume?'range':'broken'}`,out,resume?'yes':'no'],{env:{...process.env,APPFORGE_DOWNLOAD_TELEMETRY:event},stdio:'ignore'});
  const exit=await new Promise(r=>child.on('exit',r));const e=JSON.parse(fs.readFileSync(event));
  if(resume){assert.equal(exit,0);assert.equal(e.bytes,10);assert.equal(e.total,10);assert.equal(e.finished,true);}
  else{assert.notEqual(exit,0);assert.equal(e.finished,false);assert.equal(e.state,'error');assert.match(e.errorCode,/CURL_EXIT_/);assert(e.bytes<100);}
 }}finally{await new Promise(r=>server.close(r));fs.rmSync(dir,{recursive:true,force:true});}
});
