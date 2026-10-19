import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
const assets=new URL('../../android-app/app/src/main/assets/device-build/',import.meta.url).pathname;
const resources=new URL('../../android-app/app/src/test/resources/apt-snapshot/',import.meta.url).pathname;
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function fixture(body){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'apt-signed-snapshot-'));
 try{
  const store=path.join(dir,'store');fs.mkdirSync(path.join(store,'lists'),{recursive:true});
  for(const suite of ['noble','noble-updates','noble-security','noble-backports'])fs.copyFileSync(path.join(resources,`${suite}_InRelease.asc`),path.join(store,'lists',`snapshot.ubuntu.com_ubuntu_20261018T000000Z_dists_${suite}_InRelease`));
  const profile=path.join(dir,'profile.tsv');fs.copyFileSync(path.join(assets,'apt-snapshot-profile.tsv'),profile);
  const helper=path.join(dir,'helper.sh');let source=fs.readFileSync(path.join(assets,'apt-snapshot-lock.sh'),'utf8').replaceAll('/workspace/runtime/apt-snapshot-profile.tsv',profile).replaceAll('/workspace/runtime/apt-module-packages-v1.tsv',path.join(assets,'apt-module-packages-v1.tsv')).replaceAll('/usr/share/keyrings/ubuntu-archive-keyring.gpg',path.join(resources,'ubuntu-archive-keyring.gpg'));
  // Minimal host images may provide gpg but not gpgv. Verify the same real
  // OpenPGP signatures without importing keys or modifying the user's keyring.
  if(spawnSync('gpgv',['--version']).error?.code==='ENOENT'){
   const home=path.join(dir,'gpg-home');fs.mkdirSync(home,{mode:0o700});
   source=source.replace('gpgv --status-fd=1',`gpg --batch --no-default-keyring --no-auto-key-retrieve --homedir '${home}' --verify --status-fd=1`);
  }
  fs.writeFileSync(helper,source);
  function seal(){fs.writeFileSync(path.join(store,'indexes.sha256'),fs.readdirSync(path.join(store,'lists')).sort().map(n=>`${sha(fs.readFileSync(path.join(store,'lists',n)))}  ${n}\n`).join(''));}seal();
  function run(command){return spawnSync('sh',['-ec',`. "$1"; as_store="$2";as_arch=amd64;as_stamp=20261018T000000Z; ${command}`,'fixture',helper,store],{encoding:'utf8'});}
  body({dir,store,helper,profile,seal,run});
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
}
test('fixed Ubuntu signed snapshot verifies all four real InRelease signatures',()=>fixture(({run})=>{const r=run('as_verify_indices');assert.equal(r.status,0,r.stderr);}));
test('signed metadata hash mismatch fails closed',()=>fixture(({store,run})=>{fs.appendFileSync(path.join(store,'lists','snapshot.ubuntu.com_ubuntu_20261018T000000Z_dists_noble_InRelease'),'corrupt');assert.notEqual(run('as_verify_indices').status,0);}));
test('locally replacing checksum cannot make an invalid repository signature trusted',()=>fixture(({store,profile,seal,run})=>{
 const file=path.join(store,'lists','snapshot.ubuntu.com_ubuntu_20261018T000000Z_dists_noble_InRelease');
 fs.writeFileSync(file,fs.readFileSync(file,'utf8').replace('Origin: Ubuntu','Origin: Forged'));
 fs.writeFileSync(profile,fs.readFileSync(profile,'utf8').replace(/^noble\t[a-f0-9]+/m,`noble\t${sha(fs.readFileSync(file))}`));seal();
 assert.notEqual(run('as_verify_indices').status,0,'gpgv must reject even when the local test checksum was replaced');
}));
test('missing signed suite is rejected',()=>fixture(({store,seal,run})=>{fs.unlinkSync(path.join(store,'lists','snapshot.ubuntu.com_ubuntu_20261018T000000Z_dists_noble-security_InRelease'));seal();assert.notEqual(run('as_verify_indices').status,0);}));
test('shared archive cache verifies content and reuses the exact object',()=>fixture(({dir,store,run})=>{
 fs.mkdirSync(path.join(store,'archives'));const input=path.join(dir,'input.deb');fs.writeFileSync(input,'archive fixture');const digest=sha(fs.readFileSync(input));
 const cmd=`as_cache_archive '${input}' '${digest}'`;
 assert.equal(run(cmd).status,0);const file=path.join(store,'archives',`${digest}.deb`),inode=fs.statSync(file).ino;
 assert.equal(run(cmd).status,0);assert.equal(fs.statSync(file).ino,inode);
 fs.writeFileSync(file,'tampered');assert.notEqual(run(cmd).status,0,'tampered shared cache must be preserved and rejected');assert.equal(fs.readFileSync(file,'utf8'),'tampered');
}));
test('wrong SHA256 never creates a committed cache object',()=>fixture(({dir,store,run})=>{
 fs.mkdirSync(path.join(store,'archives'));const input=path.join(dir,'input.deb');fs.writeFileSync(input,'wrong bytes');const bad='a'.repeat(64);
 assert.notEqual(run(`as_cache_archive '${input}' '${bad}'`).status,0);assert(!fs.existsSync(path.join(store,'archives',`${bad}.deb`)));
}));
test('symlink cache cannot overwrite a user file',()=>fixture(({dir,store,run})=>{
 fs.mkdirSync(path.join(store,'archives'));const input=path.join(dir,'input.deb');fs.writeFileSync(input,'archive fixture');const digest=sha(fs.readFileSync(input)),outside=path.join(dir,'user-key');fs.writeFileSync(outside,'user fixture');
 fs.symlinkSync(outside,path.join(store,'archives',`${digest}.deb`));assert.notEqual(run(`as_cache_archive '${input}' '${digest}'`).status,0);assert.equal(fs.readFileSync(outside,'utf8'),'user fixture');
}));
test('snapshot adapter keeps approval, no-download, exact version and staged phase boundaries',()=>{
 const s=fs.readFileSync(path.join(assets,'apt-snapshot-lock.sh'),'utf8');
 assert(s.includes('APPFORGE_APT_REQUIRE_CONSENT'));assert(s.includes('apt-consent.approved'));assert(s.includes('as_free_bytes'));assert(s.includes('--no-download --no-remove'));assert(s.includes('"$as_pkg:$as_cpu=$as_ver"'));
 assert(s.includes('as_phase INSTALLING'));assert(s.includes('as_phase OFFLINE_TEST'));assert(s.includes('dependency version conflict'));assert(!s.includes('--allow-unauthenticated'));assert(!s.includes('Check-Valid-Until=false'));
 const fast=fs.readFileSync(new URL('../../android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt',import.meta.url),'utf8');assert(fast.indexOf('if (FastHtmlApkPolicy.isCandidate(draft))')<fast.indexOf('recoverPending(context)'));
});

test('partial cache symlink is rejected without modifying the target',()=>fixture(({dir,store,run})=>{
 fs.mkdirSync(path.join(store,'archives'));const input=path.join(dir,'input.deb');fs.writeFileSync(input,'archive fixture');const digest=sha(fs.readFileSync(input)),outside=path.join(dir,'private-source');fs.writeFileSync(outside,'preserve');
 fs.symlinkSync(outside,path.join(store,'archives',`${digest}.deb.part`));assert.notEqual(run(`as_cache_archive '${input}' '${digest}'`).status,0);assert.equal(fs.readFileSync(outside,'utf8'),'preserve');
}));

test('reviewed module catalog rejects unknown versions, source, architecture and module',()=>fixture(({run})=>{
 const row=fs.readFileSync(path.join(assets,'apt-module-packages-v1.tsv'),'utf8').trim().split('\n').map(x=>x.split('\t')).find(x=>x[0]==='ANDROID_CORE'&&x[1]==='amd64');
 const command=`as_owner=ANDROID_CORE;as_catalog_allows '${row[5]}' '${row[6]}' '${row[7]}'`;
 assert.equal(run(command).status,0);
 assert.notEqual(run(command.replace(row[5],'0'.repeat(64))).status,0);
 assert.notEqual(run(command.replace(row[7],'https://untrusted.invalid/fixture.deb')).status,0);
 assert.notEqual(run(command.replace('as_owner=ANDROID_CORE','as_owner=UNKNOWN')).status,0);
 assert.notEqual(run(command.replace('as_owner=ANDROID_CORE','as_owner=ANDROID_CORE;as_arch=armhf')).status,0);
}));

test('actual snapshot transaction rejects insufficient download space before acquisition',()=>fixture(({dir,helper})=>{
 const row=fs.readFileSync(path.join(assets,'apt-module-packages-v1.tsv'),'utf8').trim().split('\n').map(x=>x.split('\t')).find(x=>x[0]==='ANDROID_CORE'&&x[1]==='amd64');
 const backend=path.join(dir,'apt-get');const filename=row[7].split('/').at(-1);
 fs.writeFileSync(backend,`#!/bin/sh\nprintf '%s\\n' "'${row[7]}' ${filename} ${row[6]} SHA256:${row[5]}"\n`,{mode:0o700});
 const df=path.join(dir,'df');fs.writeFileSync(df,'#!/bin/sh\nprintf "Filesystem 1024-blocks Used Available Capacity Mounted\\nfixture 1 1 0 100%% /fixture\\n"\n',{mode:0o700});
 const variant=path.join(dir,'disk-transaction.sh');fs.writeFileSync(variant,fs.readFileSync(helper,'utf8').replaceAll('/usr/bin/apt-get',backend));
 const locks=path.join(dir,'locks');fs.mkdirSync(locks);const user=path.join(dir,'source.html');fs.writeFileSync(user,'preserve user source');
 const r=spawnSync('sh',['-ec',`. "$1"; test_store="$2";test_locks="$3";mkdir -p "$test_store/archives";as_configure(){ as_store="$test_store";as_locks="$test_locks";as_owner=ANDROID_CORE;as_arch=amd64;as_stamp=20261018T000000Z;as_repo=https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/; };as_verify_indices(){ return 0; };as_phase(){ return 0; };as_safe_dir(){ mkdir -p "$1"; };as_inventory(){ printf 'baseline-fixture\\n'; };appforge_apt_get_legacy(){ echo UNEXPECTED_ACQUISITION;return 99; };appforge_snapshot_apt_get install -y --no-install-recommends fixture-request`,'fixture',variant,path.join(dir,'store'),locks],{encoding:'utf8',env:{...process.env,PATH:dir+path.delimiter+process.env.PATH}});
 assert.equal(r.status,60,r.stderr);assert(r.stderr.includes('download/cache disk space insufficient'),r.stderr);assert(!r.stdout.includes('UNEXPECTED_ACQUISITION'));
 assert.equal(fs.readFileSync(user,'utf8'),'preserve user source');assert(!fs.readdirSync(locks).some(n=>!n.endsWith('.part')));
}));

test('APT mutation outside the modular installer is rejected before touching the root',()=>fixture(({run})=>{
 const result=run('APPFORGE_APT_MANAGED_INSTALL=0;as_configure');assert.equal(result.status,60);assert(result.stderr.includes('approved modular installer'));
}));
