// Opt-in dependency behavior probes; never loaded into the app or a network server.
// node this-file <SDK54 fixture> <parser fixture> <Windows host> <CLI candidate>
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');
const {spawnSync} = require('node:child_process');
const [sdk, parser, windows, cli, mode] = process.argv.slice(2).map((v, i) => i < 4 ? path.resolve(v) : v);
if (!mode) {
  for (const part of ['merge', 'cache', 'cache-consumer', 'xml', 'consumer']) {
    const r = spawnSync(process.execPath, ['--max-old-space-size=128', __filename, sdk, parser, windows, cli, part], {encoding:'utf8', timeout:10000, killSignal:'SIGKILL'});
    process.stdout.write(r.stdout || ''); process.stderr.write(r.stderr || '');
    assert.ifError(r.error); assert.equal(r.status, 0, part);
  }
} else if (mode === 'merge') {
  const merge = require(path.join(sdk, 'node_modules/deepmerge'));
  assert.equal(require(path.join(sdk, 'node_modules/deepmerge/package.json')).version, '4.3.1');
  assert.deepEqual(merge({a:{b:1}, list:[1]}, {a:{c:2}, list:[2]}), {a:{b:1,c:2},list:[1,2]});
  const hostile = JSON.parse('{"__proto__":{"phase10Inherited":true}}');
  const normal = merge({}, hostile);
  assert.equal(normal.phase10Inherited, undefined, 'normal source guard');
  const poisoned = merge(hostile, {});
  assert.equal(poisoned.phase10Inherited, true, 'target-side poisoning reproduced');
  assert.equal(Object.hasOwn(poisoned, 'phase10Inherited'), false);
  const nullTarget = merge(Object.create(null), hostile);
  assert.equal(nullTarget.phase10Inherited, true, 'null target bypass reproduced');
  for (const payload of [JSON.parse('{"constructor":{"prototype":{"phase10Inherited":true}}}'), hostile]) merge({}, payload);
  assert.equal({}.phase10Inherited, undefined, 'global Object.prototype unchanged');
  console.log('deepmerge valid merges, guarded source, target/null-target poisoning and global negative control PASS; NO mitigation shipped');
} else if (mode === 'cache') {
  const Policy = require(path.join(windows, 'node_modules/http-cache-semantics'));
  assert.equal(require(path.join(windows, 'node_modules/http-cache-semantics/package.json')).version, '4.2.0');
  const req = cookie => ({url:'https://phase10.invalid/resource',method:'GET',headers:{host:'phase10.invalid',cookie}});
  const response = cc => ({status:200,headers:{date:new Date().toUTCString(),'cache-control':cc,'set-cookie':'synthetic=principalA'}});
  const a=req('principal=A'), b=req('principal=B'); b.headers['cache-control']='max-stale=999999';
  const shared = new Policy(a,response('public, max-age=0'));
  assert.equal(shared.storable(),true);
  assert.equal(shared.satisfiesWithoutRevalidation(b),true,'cross-principal public response reused');
  assert.equal(shared.responseHeaders()['set-cookie'],'synthetic=principalA');
  for (const cc of ['private, max-age=0','no-store','public, max-age=0, must-revalidate']) {
    const p=new Policy(a,response(cc));
    assert.equal(p.storable() && p.satisfiesWithoutRevalidation(b),false,cc+' consumer storage gate');
  }
  const varied=response('public, max-age=0'); varied.headers.vary='Cookie';
  assert.equal(new Policy(a,varied).satisfiesWithoutRevalidation(b),false);
  assert.equal(require(path.join(windows,'node_modules/got')).defaults.options.cache, undefined,'actual downloader got defaults disable HTTP cache');
  console.log('cache cross-principal public reuse reproduced; private/no-store/must-revalidate/Vary controls PASS; got default cache absent; no network used');
 } else if (mode === 'cache-consumer') {
  const CacheableRequest = require(path.join(windows, 'node_modules/cacheable-request'));
  const {PassThrough} = require('node:stream');
  const {EventEmitter} = require('node:events');
  async function scenario(cc, vary, sameStore) {
    let calls=0;
    const transport=(opts, callback) => {
      const request=new EventEmitter(); calls++;
      process.nextTick(() => {
        const response=new PassThrough(); response.statusCode=200;
        response.url='https://phase10.invalid/resource';
        response.headers={date:new Date().toUTCString(),'cache-control':cc,'set-cookie':'synthetic='+opts.headers.cookie};
        if(vary)response.headers.vary='Cookie';
        callback(response); response.end('synthetic body for '+opts.headers.cookie);
      });
      return request;
    };
    const first=new CacheableRequest(transport,new Map());
    const second=sameStore ? first : new CacheableRequest(transport,new Map());
    const request=(cache,cookie,stale) => new Promise((resolve,reject) => {
      cache({protocol:'https:',hostname:'phase10.invalid',path:'/resource',headers:{cookie,...(stale ? {'cache-control':'max-stale=999999'} : {})}}, res => {
        let body='';res.on('data',x=>body+=x);res.on('end',()=>resolve({body,cached:res.fromCache,headers:res.headers}));res.on('error',reject);
      }).on('error',reject);
    });
    await request(first,'A',false);
    await new Promise(resolve=>setImmediate(resolve));
    const result=await request(second,'B',true);
    const reused=cc==='public, max-age=0' && !vary && sameStore;
    assert.equal(result.cached,reused);
    assert.equal(calls,reused ? 1 : 2);
    assert.equal(result.body,'synthetic body for '+(reused ? 'A' : 'B'));
    assert.equal(result.headers['set-cookie'],'synthetic='+(reused ? 'A' : 'B'));
  }
  (async()=>{
    for(const [cc,vary,sameStore] of [['public, max-age=0',false,true],['private, max-age=0',false,true],['no-store',false,true],['public, max-age=0',true,true],['public, max-age=0',false,false]]) await scenario(cc,vary,sameStore);
    console.log('actual cacheable-request cross-principal shared cache and private/no-store/Vary/separate-store controls PASS; synthetic in-memory transport only');
  })().catch(e=>{console.error(e);process.exitCode=1;});
} else if (mode === 'xml') {
  for (const [name, patched] of [['fxp-vulnerable',false],['fast-xml-parser',true],['fxp-patched',true],[path.join(cli,'node_modules/fast-xml-parser'),true]]) {
    const {XMLBuilder,XMLParser} = require(path.isAbsolute(name) ? name : path.join(parser,'node_modules',name));
    const builder = new XMLBuilder({commentPropName:'#comment',cdataPropName:'#cdata'});
    const positive = builder.build({root:{'#comment':'valid comment','#cdata':'valid < & data',value:'A & B'}});
    assert.equal(new XMLParser({commentPropName:'#comment',cdataPropName:'#cdata'}).parse(positive).root.value,'A & B');
    for (const [prop,text] of [['#comment','--> <injected>yes</injected><!--'],['#cdata',']]><injected>yes</injected><![CDATA[']]) {
      let xml;
      try { xml=builder.build({root:{[prop]:text}}); } catch (e) { if(patched) continue; throw e; }
      const parsed = new XMLParser().parse(xml);
      assert.equal(parsed.root?.injected === 'yes',!patched, name+' '+prop);
    }
  }
  console.log('XMLBuilder 4.5.3 injection negative control; 4.5.7 backport behavior and 5.7.0 valid/hostile controls PASS; OSV retained for 4.5.7');
} else if (mode === 'consumer') {
  const getOld=require(path.join(parser,'node_modules/@react-native-community/cli-config-android/build/config/getMainActivity.js')).default;
  const getNew=require(path.join(cli,'node_modules/@react-native-community/cli-config-android/build/config/getMainActivity.js')).default;
  const temp=fs.mkdtempSync(path.join(os.tmpdir(),'phase10-manifest-'));
  try {
    const file=path.join(temp,'AndroidManifest.xml');
    for (const [xml,expected] of [
      ['<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name=".MainActivity"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>','.MainActivity'],
      ['<manifest><application/></manifest>',null], ['invalid<',null]]) {
      fs.writeFileSync(file,xml); assert.equal(getOld(file),expected); assert.equal(getNew(file),expected);
    }
  } finally { fs.rmSync(temp,{recursive:true}); }
  const schemeRoot=fs.mkdtempSync(path.join(os.tmpdir(),'phase10-scheme-'));
  try {
    const schemeDir=path.join(schemeRoot,'Example.xcodeproj/xcshareddata/xcschemes');
    fs.mkdirSync(schemeDir,{recursive:true});
    const baseline=path.join(path.dirname(cli),'rn-cli-baseline');
    for(const parent of [baseline,cli]) {
      const getScheme=require(path.join(parent,'node_modules/@react-native-community/cli-platform-apple/build/tools/getBuildConfigurationFromXcScheme.js')).getBuildConfigurationFromXcScheme;
      const file=path.join(schemeDir,'Example.xcscheme');
      fs.writeFileSync(file,'<Scheme><LaunchAction buildConfiguration="Debug"/></Scheme>');
      assert.equal(getScheme('Example','Release',schemeRoot,{schemes:['Example']}),'Debug');
      fs.writeFileSync(file,'<invalid/>');
      assert.throws(()=>getScheme('Example','Release',schemeRoot,{schemes:['Example']}));
      assert.throws(()=>getScheme('Missing','Release',schemeRoot,{schemes:['Example']}));
    }
  } finally { fs.rmSync(schemeRoot,{recursive:true}); }
  console.log('actual CLI Android manifest and Apple scheme consumers old/new positive/malformed/missing controls PASS; no Xcode execution');
}
