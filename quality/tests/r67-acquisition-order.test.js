import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root=new URL('../../',import.meta.url);
const read=p=>fs.readFileSync(new URL(p,root),'utf8');
const installer=read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleInstallerV3.kt');
test('R67 pinned tool acquisition precedes rootfs extraction and generation mutation',()=>{
 const source=installer.slice(installer.indexOf('suspend fun installSelected('));
 const guard=source.indexOf('check(!hasProtectedCandidate(app,target))');
 const signed=source.indexOf('V9SelectiveR2ModuleCache.fetchSelectedWithConsent');
 const acquire=source.indexOf('VerifiedModuleDownload.download(archive.url,archive.sha256');
 const baseline=source.indexOf('IsolatedModuleRuntimeV2.stageFreshBaseline');
 const mutate=source.indexOf('RuntimeGenerationStore(root).prepareOwner');
 assert(guard>=0 && signed>guard && acquire>signed && baseline>acquire && mutate>baseline);
 const seed=source.slice(source.indexOf('private fun seedToolArchive('),source.indexOf('private fun copyToStage('));
 assert.match(seed,/copyToStage/);
 assert.doesNotMatch(seed,/VerifiedModuleDownload\.download/);
 assert.match(source,/require\(ready\(app, target\)\)/);
 // The still-online SDK/Gradle boundary is explicit, not represented as offline.
 assert.match(source,/APPFORGE_DEVICE_OFFLINE=0/);
});
