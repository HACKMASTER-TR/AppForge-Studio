import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {spawn,spawnSync} from 'node:child_process';
const assets=new URL('../../android-app/app/src/main/assets/device-build/',import.meta.url).pathname;
test('APT original acquire worker measures live known/unknown HTTP bodies and interruption without parsing console percentages',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'apt-acquire-body-'));
 const events=path.join(dir,'events');fs.mkdirSync(events);
 const observer=path.join(dir,'observer.sh');fs.writeFileSync(observer,fs.readFileSync(path.join(assets,'apt-acquire-observer.sh'),'utf8').replaceAll('/workspace/runtime/',assets));
 const body=Buffer.alloc(65536,65),ranges=new Map();
 const server=http.createServer((req,res)=>{
  ranges.set(req.url.split('?')[0],req.headers.range);
  if(req.url.startsWith('/cached')){res.writeHead(304);res.end();return;}
  if(req.url.startsWith('/resume')){res.writeHead(206,{'Content-Length':body.length-5,'Content-Range':`bytes 5-${body.length-1}/${body.length}`});res.write(body.subarray(5,1000));setTimeout(()=>res.end(body.subarray(1000)),1100);return;}
  if(req.url.startsWith('/known')||req.url.startsWith('/broken')||req.url.startsWith('/restart'))res.setHeader('Content-Length',body.length);
  res.write(body.subarray(0,1000));setTimeout(()=>req.url.startsWith('/broken')?res.destroy():res.end(body.subarray(1000)),1100);
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 try{for(const mode of ['known','unknown','broken','resume','restart','cached']){
  const file=path.join(dir,mode),uri=`http://127.0.0.1:${server.address().port}/${mode}?private=test-value`;
  if(mode==='resume'||mode==='restart')fs.writeFileSync(file,Buffer.alloc(5,mode==='resume'?65:66));
  if(mode==='cached')fs.writeFileSync(file,body);
  const child=spawn('sh',[observer,'http'],{env:{...process.env,APPFORGE_APT_EVENTS:events}});
  let output='',errors='',closed=false;const samples=[];
  child.stderr.on('data',b=>errors+=b);
  child.stdout.on('data',b=>{
   output+=b;
   if(output.includes('100 Capabilities')&&!closed){closed=true;child.stdin.write(`601 Configuration\nConfig-Item: Acquire::http::Timeout=5\n\n600 URI Acquire\nURI: ${uri}\nFilename: ${file}\n${mode==='cached'?'Last-Modified: Mon, 01 Jan 2024 00:00:00 GMT\n':''}\n`);}
   if(output.includes('201 URI Done')||output.includes('400 URI Failure'))child.stdin.end();
  });
  const poll=setInterval(()=>{for(const n of fs.readdirSync(events)){if(!n.endsWith('.json'))continue;try{const e=JSON.parse(fs.readFileSync(path.join(events,n)));if(e.component.endsWith(mode+'_')||e.component.endsWith(mode))samples.push(e);}catch{}}},50);
  const timeout=setTimeout(()=>child.kill('SIGTERM'),15000);
  const exit=await new Promise(r=>child.on('exit',r));clearTimeout(timeout);clearInterval(poll);
  for(const n of fs.readdirSync(events).filter(n=>n.endsWith('.json'))){const e=JSON.parse(fs.readFileSync(path.join(events,n)));if(e.component.includes(mode))samples.push(e);}
  // The original standalone APT method returns 100 when its command pipe closes.
  assert.equal(exit,100,errors+'\n'+output);assert(samples.length>0,'real worker should emit payload events');
  const live=samples.find(e=>e.bytes>0&&!e.finished);if(mode!=='cached')assert(live,'intermediate payload bytes required');
  if(live)assert.equal(live.byteMetric,'payload-written');assert(!JSON.stringify(samples).includes('test-value'));
  if(mode==='known')assert.equal(live.total,body.length);
  if(mode==='resume'){assert.equal(ranges.get('/resume'),'bytes=5-');assert.equal(live.initialBytes,5);assert.equal(live.total,body.length);}
  if(mode==='restart')assert.equal(ranges.get('/restart'),'bytes=5-');
  if(mode==='unknown')assert.equal(live.total,null);
  const last=samples.findLast(e=>e.finished||e.state==='error');assert(last,'terminal lifecycle event is retained independently of live samples');
  if(mode==='broken'){assert.equal(last.state,'error');assert.equal(last.finished,false);}
  else{assert.equal(last.finished,true);if(mode==='cached'){assert.equal(last.byteMetric,'cached');assert(!samples.some(e=>e.state==='downloading'));}else{assert.equal(last.bytes,body.length);assert.deepEqual(fs.readFileSync(file),body);}}
 }}finally{await new Promise(r=>server.close(r));fs.rmSync(dir,{recursive:true,force:true});}
});

test('APT index observer remains compatible with fail-fast installer shells',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'apt-acquire-failfast-'));
 try{
  const backend=path.join(dir,'apt-get');fs.writeFileSync(backend,'#!/bin/sh\nexit 0\n',{mode:0o700});
  const helper=path.join(dir,'helper.sh');fs.writeFileSync(helper,fs.readFileSync(path.join(assets,'apt-download-telemetry.sh'),'utf8').replaceAll('/usr/bin/apt-get',backend).replaceAll('/workspace/runtime/',assets));
  const run=spawnSync('sh',['-ec','. "$1"; appforge_apt_get update; printf "APT_UPDATE_RETURNED_SUCCESS\\n"','fixture',helper],{env:{...process.env,APPFORGE_DOWNLOAD_TELEMETRY:path.join(dir,'event.json')},encoding:'utf8'});
  assert.equal(run.status,0,run.stderr);assert(run.stdout.includes('APT_UPDATE_RETURNED_SUCCESS'));
  assert(fs.existsSync(path.join(dir,'event.json.apt-events')),'the acquire adapter must actually be enabled');
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
