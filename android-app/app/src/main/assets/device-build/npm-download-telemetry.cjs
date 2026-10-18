'use strict';
// Observe original response events without adding data listeners or changing TLS/cache/backpressure.
const fs=require('node:fs');
const crypto=require('node:crypto');
const target=process.env.APPFORGE_DOWNLOAD_TELEMETRY;
if(target){
 const publish=e=>{try{const p=target+'.tmp-'+process.pid+'-'+e.transfer;fs.writeFileSync(p,JSON.stringify(e));fs.renameSync(p,target);}catch{}};
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
     return emit.call(this,name,...values);
    };
   });
   return request;
  };
  mod.get=function(...args){const request=mod.request(...args);request.end();return request;};
 }
}
