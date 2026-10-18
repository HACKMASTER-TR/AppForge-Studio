#!/usr/bin/env node
// Local generated package only. No external scripts, TLS changes or user projects.
const fs=require('fs'),os=require('os'),path=require('path'),http=require('http'),crypto=require('crypto'),assert=require('assert'),{spawn}=require('child_process');
const root=fs.mkdtempSync(path.join(process.env.APPFORGE_TMPDIR||os.tmpdir(),'appforge-npm-offline-'));
const run=(args,cwd,extra={})=>new Promise((resolve,reject)=>{const child=spawn('npm',args,{cwd,env:{...process.env,...extra}});let out='';child.stdout.on('data',b=>out+=b);child.stderr.on('data',b=>out+=b);child.on('error',reject);child.on('exit',code=>resolve({code,out}));});
(async()=>{
 const source=path.join(root,'source'),project=path.join(root,'project');fs.mkdirSync(source);fs.mkdirSync(project);
 fs.writeFileSync(path.join(source,'package.json'),JSON.stringify({name:'appforge-offline-fixture',version:'1.0.0',main:'index.js'}));fs.writeFileSync(path.join(source,'index.js'),'module.exports=42;');
 assert.equal((await run(['pack','--ignore-scripts'],source)).code,0);
 const tar=fs.readFileSync(path.join(source,'appforge-offline-fixture-1.0.0.tgz')),sri='sha512-'+crypto.createHash('sha512').update(tar).digest('base64');
 const server=http.createServer((req,res)=>{
  if(req.url.includes('.tgz')){res.setHeader('Content-Length',tar.length);res.end(tar);return;}
  if(req.url==='/appforge-offline-fixture'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({name:'appforge-offline-fixture','dist-tags':{latest:'1.0.0'},versions:{'1.0.0':{name:'appforge-offline-fixture',version:'1.0.0',dist:{tarball:`http://127.0.0.1:${server.address().port}/fixture.tgz`,integrity:sri}}}}));return;}
  res.statusCode=404;res.end();
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));
 fs.writeFileSync(path.join(project,'package.json'),JSON.stringify({name:'reviewed-project',version:'1.0.0',dependencies:{'appforge-offline-fixture':'1.0.0'}}));
 const cache=path.join(root,'cache'),event=path.join(root,'transfer.json');
 const env={APPFORGE_DOWNLOAD_TELEMETRY:event,NODE_OPTIONS:`--require=${path.resolve(__dirname,'../../android-app/app/src/main/assets/device-build/npm-download-telemetry.cjs')}`};
 try{
  const online=await run(['install','--ignore-scripts','--no-audit','--no-fund','--cache',cache,'--registry',`http://127.0.0.1:${server.address().port}`],project,env);fs.writeFileSync(path.join(root,'online.log'),online.out);assert.equal(online.code,0,online.out);
  const observed=JSON.parse(fs.readFileSync(event));assert(observed.bytes>0 && observed.finished);assert(!('ready'in observed));
 }finally{await new Promise(r=>server.close(r));}
 fs.rmSync(path.join(project,'node_modules'),{recursive:true});
 const offline=await run(['ci','--offline','--ignore-scripts','--no-audit','--no-fund','--cache',cache],project,env);fs.writeFileSync(path.join(root,'offline.log'),offline.out);assert.equal(offline.code,0,offline.out);assert.equal(require(path.join(project,'node_modules/appforge-offline-fixture')),42);
 fs.rmSync(path.join(project,'node_modules'),{recursive:true});
 const empty=await run(['ci','--offline','--ignore-scripts','--no-audit','--no-fund','--cache',path.join(root,'empty-cache')],project,env);fs.writeFileSync(path.join(root,'empty-offline.log'),empty.out);assert.notEqual(empty.code,0,'empty cache must fail offline');
 console.log('PASS real npm install + SRI, original transport byte observer, server-stopped npm ci --offline, empty-cache rejection');console.log('Evidence:',root);
})().catch(e=>{console.error(e);process.exitCode=1;});
