import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {spawn} from 'node:child_process';
const hook=new URL('../../android-app/app/src/main/assets/device-build/python-telemetry/',import.meta.url).pathname;
test('Python stdlib observes read/readinto with known and unknown totals without changing bodies',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'af-python-bytes-'));const payload=Buffer.alloc(54321,90);
 const server=http.createServer((req,res)=>{if(req.url==='/known')res.setHeader('Content-Length',payload.length);res.write(payload.subarray(0,1000));setTimeout(()=>res.end(payload.subarray(1000)),100)});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 try{
  for(const known of [true,false])for(const into of [true,false]){
   const output=path.join(dir,'event.json');
   const code=`import http.client,json,os\nc=http.client.HTTPConnection('127.0.0.1',${server.address().port});c.request('GET','/${known?'known':'unknown'}');r=c.getresponse();n=0\nwhile True:\n b=bytearray(8192)\n chunk=r.readinto(b) if ${into?'True':'False'} else len(r.read(8192))\n if not chunk:break\n n+=chunk\nprint(json.dumps({'bytes':n,'event':json.load(open(os.environ['APPFORGE_DOWNLOAD_TELEMETRY']))}))\n`;
   const child=spawn('python3',['-c',code],{env:{...process.env,PYTHONPATH:hook,APPFORGE_DOWNLOAD_TELEMETRY:output}});let text='',err='';child.stdout.on('data',b=>text+=b);child.stderr.on('data',b=>err+=b);assert.equal(await new Promise(r=>child.on('exit',r)),0,err);
   const result=JSON.parse(text);assert.deepEqual(JSON.parse(fs.readFileSync(path.join(output+'.events',JSON.parse(fs.readFileSync(output)).transfer+'.json'))),JSON.parse(fs.readFileSync(output)));assert.equal(result.bytes,payload.length);assert.equal(result.event.bytes,payload.length);assert.equal(result.event.finished,true);assert.equal(result.event.total,known?payload.length:null);assert(!('ready'in result.event));
  }
 }finally{await new Promise(r=>server.close(r));fs.rmSync(dir,{recursive:true,force:true});}
});
