import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../', import.meta.url);
const read = p => fs.readFileSync(new URL(p, root), 'utf8');
const installer = read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleInstallerV3.kt');
const bridge = read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedResolverBridge.kt');
const testFile = read('android-app/app/src/test/java/com/appforge/studio/build/IsolatedResolverBridgeTest.kt');

test('R21 configures DNS only in Android Core isolated generation, never active module', () => {
  assert.match(installer, /val staged = generation\.root[\s\S]*?if \(id == OfflineModuleId\.ANDROID_CORE\) \{\s*IsolatedResolverBridge\.prepare\(app, staged\)/);
  assert.match(bridge, /STAGE\.matches\(staged\.name\)/);
  assert.match(bridge, /networkManager\.getLinkProperties\(active\)\?\.dnsServers/);
  assert.match(bridge, /StandardCopyOption\.ATOMIC_MOVE, StandardCopyOption\.REPLACE_EXISTING/);
  assert.doesNotMatch(bridge, /8\.8\.8\.8|1\.1\.1\.1|allow-unauthenticated|trustAll|DISABLE_TLS/);
  assert.match(testFile, /onlyJournalStageIsWritableAndExistingRootIsPreserved/);
});
