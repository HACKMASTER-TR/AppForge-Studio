import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
const source=new URL('../../android-app/app/src/main/assets/device-build/apt-snapshot-lock.sh',import.meta.url);
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const suites=['noble','noble-updates','noble-security','noble-backports'];
const components=['main','restricted','universe','multiverse'];
const root=fs.mkdtempSync(path.join(os.tmpdir(),'v9-r2-signed-'));
const home=path.join(root,'gpg');fs.mkdirSync(home,{mode:0o700});
const exec=(cmd,args,options={})=>spawnSync(cmd,args,{encoding:'utf8',timeout:30000,...options});
const keygen=exec('gpg',['--homedir',home,'--batch','--pinentry-mode','loopback','--passphrase','','--quick-generate-key','V9 test only <test@example.invalid>','ed25519','sign','0']);
assert.equal(keygen.status,0,keygen.stderr);
const fpr=exec('gpg',['--homedir',home,'--batch','--with-colons','--fingerprint']).stdout.split('\n').find(s=>s.startsWith('fpr:')).split(':')[9];
const exported=spawnSync('gpg',['--homedir',home,'--batch','--export']);assert.equal(exported.status,0);
const keyring=path.join(root,'keyring.gpg');fs.writeFileSync(keyring,exported.stdout);
process.on('exit',()=>fs.rmSync(root,{recursive:true,force:true}));
function fixture(body,{expired=false,future=false,wrongArchitecture=false,empty=false,duplicate=false}={}) {
 const dir=fs.mkdtempSync(path.join(root,'case-'));const store=path.join(dir,'store'),lists=path.join(store,'lists');fs.mkdirSync(lists,{recursive:true});
 const profile=path.join(dir,'profile');const rows=[];
 const payload=empty?Buffer.alloc(0):Buffer.from('Package: test\nVersion: 1\nArchitecture: arm64\nFilename: pool/test.deb\nSize: 1\nSHA256: '+'a'.repeat(64)+'\n\n');
 const now=new Date();const date=new Date(now.valueOf()+(future?3600000:-3600000)).toUTCString();
 const until=new Date(now.valueOf()+(expired?-60000:3600000)).toUTCString();
 for(const suite of suites) {
  let release=`Origin: Ubuntu\nCodename: noble\nSuite: ${suite}\nDate: ${date}\nValid-Until: ${until}\nArchitectures: ${wrongArchitecture?'amd64':'arm64'}\nComponents: main restricted universe multiverse\nSHA256:\n`;
  for(const component of components)release+=` ${sha(payload)} ${payload.length} ${component}/binary-arm64/Packages\n`;
  if(duplicate)release+=` ${sha(payload)} ${payload.length} main/binary-arm64/Packages\n`;
  const plain=path.join(dir,'release');fs.writeFileSync(plain,release);
  const file=path.join(lists,`snapshot.ubuntu.com_ubuntu_20261018T000000Z_dists_${suite}_InRelease`);
  const sign=exec('gpg',['--homedir',home,'--batch','--yes','--armor','--clearsign','--output',file,plain]);assert.equal(sign.status,0,sign.stderr);
  rows.push(`${suite}\t${sha(fs.readFileSync(file))}`);
  for(const component of components)fs.writeFileSync(file.replace('InRelease',`${component}_binary-arm64_Packages`),payload);
 }
 fs.writeFileSync(profile,rows.join('\n')+'\n');
 const helper=path.join(dir,'helper');
 fs.writeFileSync(helper,fs.readFileSync(source,'utf8').replaceAll('/workspace/runtime/apt-snapshot-profile.tsv',profile).replaceAll('/usr/share/keyrings/ubuntu-archive-keyring.gpg',keyring).replaceAll('F6ECB3762474EDA9D21B7022871920D1991BC93C',fpr));
 function seal(){fs.writeFileSync(path.join(store,'indexes.sha256'),fs.readdirSync(lists).sort().map(n=>sha(fs.readFileSync(path.join(lists,n)))+'  '+n+'\n').join(''));}seal();
 function run(command='as_verify_indices') {return exec('sh',['-ec',`. "$1";as_store="$2";as_arch=arm64;as_stamp=20261018T000000Z; ${command}`,'fixture',helper,store]);}
 try{body({dir,store,lists,run,seal,profile,helper});}finally{fs.rmSync(dir,{recursive:true,force:true});}
}
test('real gpgv verifies complete 4-suite 16-index metadata chain',()=>fixture(({run})=>{const r=run();assert.equal(r.status,0,r.stderr);}));
test('missing Packages cannot pass by replacing local manifest with another file',()=>fixture(({lists,seal,run})=>{
 const f=fs.readdirSync(lists).find(n=>n.endsWith('main_binary-arm64_Packages'));fs.unlinkSync(path.join(lists,f));fs.writeFileSync(path.join(lists,'unrelated'),'not an index');seal();assert.notEqual(run().status,0);
}));
test('same-size corrupted index is rejected despite locally resealed hashes',()=>fixture(({lists,seal,run})=>{
 const f=path.join(lists,fs.readdirSync(lists).find(n=>n.endsWith('main_binary-arm64_Packages')));const b=fs.readFileSync(f);b[0]^=1;fs.writeFileSync(f,b);seal();assert.notEqual(run().status,0);
}));
test('forged InRelease and replaced local pin still fail Ubuntu GPG',()=>fixture(({lists,profile,seal,run})=>{
 const f=path.join(lists,fs.readdirSync(lists).find(n=>n.endsWith('noble_InRelease')));fs.writeFileSync(f,fs.readFileSync(f,'utf8').replace('Origin: Ubuntu','Origin: Forged'));
 fs.writeFileSync(profile,fs.readFileSync(profile,'utf8').replace(/^noble\t\w+/m,'noble\t'+sha(fs.readFileSync(f))));seal();assert.notEqual(run().status,0);
}));
test('expired genuinely signed metadata fails closed',()=>fixture(({run})=>assert.notEqual(run().status,0),{expired:true}));
test('future genuinely signed metadata fails closed',()=>fixture(({run})=>assert.notEqual(run().status,0),{future:true}));
test('wrong signed architecture fails closed',()=>fixture(({run})=>assert.notEqual(run().status,0),{wrongArchitecture:true}));
test('duplicate signed Packages record fails closed',()=>fixture(({run})=>assert.notEqual(run().status,0),{duplicate:true}));
test('authenticated zero-length Packages indexes are accepted with their real signed hash',()=>fixture(({run})=>{const r=run();assert.equal(r.status,0,r.stderr);},{empty:true}));
test('R2 zero-length cache reuse opens no network and rejects corruption',()=>fixture(({dir,run})=>{
 const file=path.join(dir,'empty');const digest=sha(Buffer.alloc(0));const cmd=`as_r2_object '${digest}' 0 '${file}'`;
 assert.equal(run(cmd).status,0);assert.equal(run(cmd).status,0);fs.writeFileSync(file,'x');assert.notEqual(run(cmd).status,0);assert.equal(fs.readFileSync(file,'utf8'),'x');
}));
test('missing R2 payload without native broker fails without Snapshot fallback',()=>fixture(({dir,run})=>{
 const r=run(`as_r2_object '${'a'.repeat(64)}' 1 '${path.join(dir,'missing')}'`);assert.notEqual(r.status,0);assert.match(r.stderr,/snapshot fallback prohibited/);
}));
test('legacy Node recipe history is byte-exact base data, never executed by installer',()=>{
 for(const name of ['apt-snapshot-lock.sh','native-download-broker.sh']) {
  const base=spawnSync('git',['show',`a9c1739b025a939b120888220d827a82f1538467:android-app/app/src/main/assets/device-build/${name}`]);assert.equal(base.status,0);
  const archived=fs.readFileSync(new URL(`../../android-app/app/src/main/assets/device-build/recipe-history/a9c1739/${name}`,import.meta.url));assert.equal(sha(archived),sha(base.stdout));
 }
});
