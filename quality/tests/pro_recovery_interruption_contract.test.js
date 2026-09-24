import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(
  new URL('../../' + path, import.meta.url),
  'utf8'
);

const gradle = read('android-app/app/build.gradle.kts');
const client = read(
  'android-app/app/src/main/java/com/appforge/studio/' +
  'security/ProCodeClient.kt'
);
const workflow = read('.github/workflows/android-debug.yml');

test('recovery test is isolated and debug opt-in', () => {
  assert.match(gradle, /appforgeProRecoveryTest/);
  assert.match(gradle, /applicationIdSuffix = "\.prorecovery"/);
  assert.match(gradle, /"PRO_RECOVERY_TEST",\s*"false"/);
});

test('interruption occurs before local persistence', () => {
  const start = client.indexOf('fun redeemAndVerify(');
  const end = client.indexOf('fun verifyStatus()', start);
  const flow = client.slice(start, end);

  const success = flow.indexOf('result.optBoolean("ok")');
  const stop = flow.indexOf('if (BuildConfig.PRO_RECOVERY_TEST)');
  const save = flow.indexOf('persistInstallation(id)');

  assert.ok(success >= 0);
  assert.ok(stop > success);
  assert.ok(save > stop);
  assert.match(flow, /return verifyStatus\(\)/);
});

test('isolated artifact cannot replace normal APK', () => {
  assert.match(workflow, /AppForgeStudio-Pro-Recovery-Test-/);
  assert.match(
    workflow,
    /github.ref == 'refs\/heads\/main' && !inputs.pro_recovery_test/
  );
  assert.match(workflow, /com\.appforge\.studio\.prorecovery/);
});
