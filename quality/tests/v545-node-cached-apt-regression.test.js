import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../', import.meta.url);
const read = p => fs.readFileSync(new URL(p,root),'utf8');
const node = read('android-app/app/src/main/assets/device-build/install-node-module.sh');
const apt = read('android-app/app/src/main/assets/device-build/apt-snapshot-lock.sh');
const cache = read('android-app/app/src/main/java/com/appforge/studio/build/V9SelectiveR2ModuleCache.kt');
const reuse = read('android-app/app/src/main/java/com/appforge/studio/build/V9R2AptInstallReuse.kt');
const isolated = read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleInstallerV3.kt');
test('Node prereqs use only explicit signed-snapshot APT shim, never live Ubuntu fallback',()=>{
 assert.match(node,/APPFORGE_PINNED_APT=\/opt\/appforge-device\/telemetry-bin\/apt-get/);
 assert.match(node,/"\$APPFORGE_PINNED_APT" update/);
 assert.match(node,/"\$APPFORGE_PINNED_APT" install -y --no-install-recommends --no-upgrade/);
 assert.doesNotMatch(node,/(^|\n)\s*apt-get (update|install)/);
 assert.match(node,/APPFORGE_NODE_PINNED_APT_REQUIRED/);
});
test('snapshot broker uses approved, content-addressed HTTPS R2 custom domain',()=>{
 assert.match(apt,/https:\/\/packages\.appforgecloud\.com\/v9\/objects\/sha256\/\$as_prefix\/\$as_object_sha/);
 assert.doesNotMatch(apt,/workers\.dev/);
 assert.match(apt,/sha256sum -c -/);
 assert.match(apt,/APT_NO_DOWNLOAD_INSTALLED/);
});
test('verified signed packages are reused by exact SHA and missing only are fetched',()=>{
 assert.match(cache,/VerifiedArtifactCache\.reuse\(target,obj.sha256,obj.bytes,V9R2ObjectTransfer.MAX_BYTES\)/);
 assert.match(cache,/onCacheHit\(index\+1,count,obj.bytes\)/);
 assert.match(cache,/inspectCache\(cache, plan.objects\)/);
 assert.match(reuse,/if \(target.exists\(\)\)/);
 assert.match(reuse,/sha256\(target\) == item.sha256/);
 assert.match(isolated,/generation\.commit\(verify = \{ candidate ->/);
});
