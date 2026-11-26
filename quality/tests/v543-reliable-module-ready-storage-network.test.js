import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root=new URL('../../',import.meta.url);
const read=p=>fs.readFileSync(new URL(p,root),'utf8');
const ui=read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');
const inst=read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleInstallerV3.kt');
const transfer=read('android-app/app/src/main/java/com/appforge/studio/build/V9R2ObjectTransfer.kt');
const cache=read('android-app/app/src/main/java/com/appforge/studio/build/V9SelectiveR2ModuleCache.kt');
test('module center unifies strict independent receipts and legacy installed checks',()=>{
 assert.match(ui,/OfflineBuildPackManager\.installedReadiness\(context\)\.toMutableMap\(\)/);
 assert.match(ui,/IsolatedModuleInstallerV3\.ready\(context,module\.id\)/);
 assert.match(ui,/val installed=!running && ready\[id\]==true/);
 assert.match(ui,/val canInstall=recovered && inspectionComplete && chosen==null &&\s*!installed && !protected/);
});
test('READY hides installation action completely; blocked candidate shows no install action',()=>{
 const row=read('android-app/app/src/main/java/com/appforge/studio/R66ModuleRow.kt');
 assert.match(row,/if\(installed\)return State\(1f,"HAZIR ✓"/);
 assert.match(row,/if\(blocked\)return State\(0f,"Kurulum engellendi"/);
 assert.match(ui,/!installed && !protected && !busy/);
 assert.match(ui,/require\(!IsolatedModuleInstallerV3\.hasProtectedCandidate\(context,id\)\)/);
});
test('shared Linux runtime is explicitly distinguishable from module-owned Ubuntu',()=>{
 assert.match(ui,/OfflineModuleId\.LINUX_RUNTIME\)\)/);
 assert.match(ui,/if\(id==OfflineModuleId\.LINUX_RUNTIME\) "Linux temel ortamını kur"/);
 assert.match(ui,/Linux temel ortamını kur/);
 assert.doesNotMatch(ui,/Onayla ve kur • mevcut sistem/);
});
test('interrupted/unverified isolated candidates cannot trigger new R2 download or clone',()=>{
 assert.match(inst,/fun hasProtectedCandidate\(context: Context, target: OfflineModuleId\)/);
 assert.match(inst,/IsolatedModuleRuntimeV2\.baselineVerified\(layout,manifest\)/);
 assert.match(inst,/java\.nio\.file\.LinkOption\.NOFOLLOW_LINKS/);
 const start=inst.indexOf('suspend fun installSelected(');
 const guard=inst.indexOf('check(!hasProtectedCandidate(app,target))',start);
 const fetch=inst.indexOf('V9SelectiveR2ModuleCache.fetchSelectedWithConsent(',start);
 const clone=inst.indexOf('RuntimeGenerationStore(root).prepareOwner(',start);
 assert(start>=0 && guard>start && fetch>guard && clone>fetch);
 assert.doesNotMatch(inst,/dpkg --force|apt-get --allow-unauthenticated/);
});
test('R2 connection reset uses bounded Range resume without bypassing SHA-256 or SSL',()=>{
 assert.match(transfer,/var transientFailures=0/);
 assert.match(transfer,/transientFailures >= 3/);
 assert.match(transfer,/e is javax\.net\.ssl\.SSLException/);
 assert.match(transfer,/e !is java\.net\.SocketException/);
 assert.match(transfer,/connection\.setRequestProperty\("Range", "bytes=\$start-\$end"\)/);
 assert.match(transfer,/VerifiedModuleDownload\.sha\(partial\) != sha256/);
 assert.match(transfer,/Files\.move\(partial\.toPath\(\), target\.toPath\(\), StandardCopyOption\.ATOMIC_MOVE\)/);
 assert.match(ui,/ModulePhase\.WAITING_CONNECTION/);
});
test('signed cache remains reusable and validated',()=>{
 assert.match(cache,/inspectCache\(cache, plan.objects\)/);
 assert.match(cache,/VerifiedArtifactCache\.reuse/);
});

test('V544 pins the signed R2 object source to the verified custom domain',()=>{
 const source=read('android-app/app/src/main/java/com/appforge/studio/build/V9SignedR2CandidateDownload.kt');
 assert.match(source,/private const val ORIGIN = \"https:\/\/packages\.appforgecloud\.com\"/);
 assert.doesNotMatch(source,/appforge-v9-packages-readonly\.28550040284a\.workers\.dev/);
 assert.match(source,/V9ReleaseCandidatePolicy\.verify\(manifest, signature, persistedFloor\)/);
});
