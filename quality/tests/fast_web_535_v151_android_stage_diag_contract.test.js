import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const key = fs.readFileSync(path.join(root,'android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabUploadKey.kt'),'utf8');
const engine = fs.readFileSync(path.join(root,'android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt'),'utf8');
test('V15.1 diagnostics isolate Android PKCS12 load, key access and signer setup', () => {
  for (const stage of ['PKCS12_PROVIDER','PKCS12_LOAD','PKCS12_ALIAS','PKCS12_PRIVATE_KEY','PKCS12_CERTIFICATE','PKCS12_KEY_PAIR','PKCS12_SIGNER_SETUP']) {
    assert.match(key,new RegExp('Stage\\.'+stage));
  }
  assert.match(key,/catch \(_: Exception\)/);
  assert.match(key,/IllegalStateException\("APPFORGE_FAST_AAB_STAGE_\$\{stage\.name\}"\)/);
  assert.doesNotMatch(key,/throw IllegalStateException\("APPFORGE_FAST_AAB_STAGE_\$\{stage\.name\}",/);
  assert.match(key,/storePwd\.fill/);
  assert.match(key,/keyPwd\.fill/);
});
test('V15.1 diagnostic milestones are custom-key-only and leave fail-closed cleanup', () => {
  for (const stage of ['KEY_OPEN_OK','UNSIGNED_OK','SIGNED_OK']) {
    assert.match(engine,new RegExp('APPFORGE_FAST_AAB_STAGE_'+stage));
  }
  assert.match(engine,/if \(uploadIdentity != null\)/);
  assert.match(engine,/candidateApk\?\.delete\(\)/);
  assert.match(engine,/candidateSignedAab\?\.delete\(\)/);
  assert.match(engine,/state\.apk = null/);
  assert.match(engine,/state\.aab = null/);
});
