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
