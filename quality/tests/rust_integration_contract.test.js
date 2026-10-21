import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {spawnSync} from 'node:child_process';
const base=new URL('../../',import.meta.url);
const read=p=>fs.readFileSync(new URL(p,base),'utf8');
test('Rust safety tests exercise lock, consent, archives and artifact protection',()=>{
 const result=spawnSync('python3',['quality/rust_integration_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(result.status,0,result.stdout+result.stderr);
});
test('Rust native module reuses verified downloader and existing generation ownership',()=>{
 const manager=read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
 assert.match(manager,/id==OfflineModuleId.RUST/);assert.match(manager,/install-rust-module.py/);
 assert.match(manager,/VerifiedModuleDownload.download/);assert.match(manager,/ModuleOwnershipStore\(staged\).record/);
});
test('Compact module screen keeps technical controls behind details and distinct navigation',()=>{
 const ui=read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');
 assert.match(ui,/ModuleCompactHeader/);assert.match(ui,/if\(expanded && lifecycle!=null\)/);
 for(const section of ['network','history','diagnostics'])assert.ok(ui.includes(`section=="${section}"`));
 assert.match(ui,/rememberSaveable/);assert.match(ui,/pendingLifecycle=module.id to "rollback"/);
});
test('Cargo vendor adapter validates real directory-source checksums and fails closed',()=>{
 const result=spawnSync('python3',['quality/rust_vendor_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(result.status,0,result.stdout+result.stderr);
});
test('In-app Rust execution separates managed downloads from network-disconnected source execution',()=>{
 const prep=read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyPreparation.kt');
 const card=read('android-app/app/src/main/java/com/appforge/studio/ProjectDependencyPreparationCard.kt');
 assert.match(prep,/VerifiedModuleDownload.download\(url,sha,archive,OfflineModuleId.RUST/);
 assert.match(prep,/connectivity.allNetworks.isEmpty/);assert.match(prep,/networkSeen.get/);
 assert.match(prep,/RustPreparedProjects\(storeRoot\).publish/);assert.match(prep,/stage\?\.close\(\)/);
 assert.match(prep,/CARGO|Cargo/);assert.match(card,/allowMobileOnce/);assert.match(card,/store\(\).restore\(draft\)/);
});

test('Rust Python prerequisite uses existing pinned catalog without Android SDK installation',()=>{
 const script=read('android-app/app/src/main/assets/device-build/install-rust-prerequisites.sh');
 const catalog=read('android-app/app/src/main/assets/device-build/apt-module-packages-v1.tsv');
 assert.match(script,/APPFORGE_MODULE_ID:-.*RUST/);assert.match(script,/as_configure_original/);
 assert.match(script,/as_owner=RUST/);assert.match(script,/python3=3.12.3-0ubuntu2.1/);
 assert.match(script,/appforge_snapshot_apt_get install/);assert.ok(catalog.includes('python3\t3.12.3-0ubuntu2.1'));
 assert.doesNotMatch(script,/install-toolchain|sdkmanager|install-node|allow-unauthenticated|--insecure/);
});
