'use strict';
// Observe original response events without adding data listeners or changing TLS/cache/backpressure.
const fs=require('node:fs');
const crypto=require('node:crypto');
const target=process.env.APPFORGE_DOWNLOAD_TELEMETRY;
if(target){
 const saveChannel=e=>{const d=target+'.events';fs.mkdirSync(d,{recursive:true});const p=d+'/'+e.transfer+'.json';fs.writeFileSync(p+'.part',JSON.stringify(e));fs.renameSync(p+'.part',p);};
 const publish=e=>{try{saveChannel(e);const p=target+'.tmp-'+process.pid+'-'+e.transfer;fs.writeFileSync(p,JSON.stringify(e));fs.renameSync(p,target);}catch{}};
 for(const protocol of ['http','https']){
  const mod=require('node:'+protocol),original=mod.request;
  mod.request=function(...args){
   const request=original.apply(this,args);
   request.prependListener('response',response=>{
    const id=crypto.randomUUID(),start=Date.now();let bytes=0,last=0;
    const length=Number(response.headers['content-length']);const total=Number.isSafeInteger(length)&&length>0?length:null;
    const raw=String(request.path||'').split('?')[0].split('/').pop()||'registry';
    const component=('npm: '+raw.replace(/[^A-Za-z0-9._@+-]/g,'_')).slice(0,150);
    const event=finished=>({schema:1,transfer:id,component,bytes,total:total!==null&&bytes<=total?total:null,finished,elapsedMs:Date.now()-start});
    publish(event(false));
    const emit=response.emit;
    response.emit=function(name,...values){
     if(name==='data'&&values[0]){bytes+=Buffer.byteLength(values[0]);const now=Date.now();if(now-last>=250){publish(event(false));last=now;}}
     if(name==='end')publish(event(true));
     if(name==='aborted'||name==='error')publish({...event(false),state:'error',errorCode:'HTTP_BODY_INTERRUPTED'});
     return emit.call(this,name,...values);
    };
   });
   return request;
  };
  mod.get=function(...args){const request=mod.request(...args);request.end();return request;};
 }
}

// Node fetch and undici do not use http.request. Observe documented diagnostics
// without consuming bodies, changing dispatchers, TLS, caching or backpressure.
if(target){
 const dc=require('node:diagnostics_channel'), transfers=new WeakMap();
 const write=(record,finished=false,error=false)=>{try{
  const e={schema:1,transfer:record.id,component:'Node fetch HTTP body',bytes:record.bytes,
   total:record.total!==null&&record.bytes<=record.total?record.total:null,
   ...(record.unavailable?{byteMetric:'unavailable'}:{}),
   elapsedMs:Date.now()-record.start,finished,state:error?'error':finished?'complete':'downloading',
   ...(error?{errorCode:'HTTP_BODY_INTERRUPTED'}:{})};
  const directory=target+'.events';fs.mkdirSync(directory,{recursive:true});const channel=directory+'/'+record.id+'.json';fs.writeFileSync(channel+'.part',JSON.stringify(e));fs.renameSync(channel+'.part',channel);
  const tmp=target+'.fetch-'+process.pid+'-'+record.id;fs.writeFileSync(tmp,JSON.stringify(e));fs.renameSync(tmp,target);
 }catch{}};
 dc.channel('undici:request:headers').subscribe(({request,response})=>{try{
  let total=null;const h=response.headers;
  for(let i=0;i<h.length;i+=2)if(String(h[i]).toLowerCase()==='content-length'){
   const n=Number(String(h[i+1]));if(Number.isSafeInteger(n)&&n>0)total=n;
  }
  const record={id:crypto.randomUUID(),bytes:0,total,start:Date.now(),last:0};
  transfers.set(request,record);write(record);
 }catch{}});
 const count=(request,chunk)=>{try{
  const r=transfers.get(request);if(!r)return;r.bytes+=chunk.byteLength;
  if(Date.now()-r.last>=250){r.last=Date.now();write(r);}
 }catch{}};
 // Node 22 bundled undici has no bodyChunkReceived diagnostic. Its existing
 // onData callback is observed in place; original return/backpressure/errors survive.
 const legacyObserved=new WeakSet();
 if(Number(process.versions.node.split('.')[0])===22)dc.channel('undici:request:create').subscribe(({request})=>{try{
  if(typeof request.onData!=='function')return;const original=request.onData;
  request.onData=function(chunk){count(request,chunk);return original.apply(this,arguments);};
  legacyObserved.add(request);
 }catch{}});
 dc.channel('undici:request:bodyChunkReceived').subscribe(({request,chunk})=>{if(!legacyObserved.has(request))count(request,chunk);});
 dc.channel('undici:request:trailers').subscribe(({request})=>{const r=transfers.get(request);if(r){if(r.bytes===0&&r.total!==null&&request.method!=='HEAD'){r.total=null;r.unavailable=true;write(r);}else write(r,true);transfers.delete(request);}});
 dc.channel('undici:request:error').subscribe(({request})=>{const r=transfers.get(request);if(r){write(r,false,true);transfers.delete(request);}});
}
