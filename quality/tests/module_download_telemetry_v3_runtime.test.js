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
  const child=spawn('sh',['-c','. "$1"; curl -fL https://example.invalid/test -o "$2"','test',helper,output],{env:{...process.env,PATH:bin+':'+process.env.PATH,APPFORGE_DOWNLOAD_TELEMETRY:telemetry},stdio:'ignore'});
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
