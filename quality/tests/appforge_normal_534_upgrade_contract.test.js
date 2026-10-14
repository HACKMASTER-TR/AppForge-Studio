import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const gradle = fs.readFileSync(path.join(root, 'android-app/app/build.gradle.kts'), 'utf8');
const workflow = fs.readFileSync(path.join(root, '.github/workflows/android-debug.yml'), 'utf8');

test('normal AppForge package identity is unchanged and 534 exceeds installed 533', () => {
  assert.match(gradle, /applicationId\s*=\s*"com\.appforge\.studio"/);
  assert.match(gradle, /val appforgeBaseVersionCode\s*=\s*534\b/);
  assert.match(gradle, /versionCode\s*=\s*if\s*\(\s*appforgePlayIntegrityAcceptance\s*\)/);
  assert.match(gradle, /versionName\s*=\s*"5\.0\.34"/);
  assert.ok(534 > 533);
});

test('existing CI debug signing mechanism is still used', () => {
  assert.match(gradle, /signingConfigs\.create\("ciDebug"\)/);
  assert.match(gradle, /ciDebugSigning\?\.let \{ signingConfig = it \}/);
  assert.match(workflow, /APPFORGE_DEBUG_KEYSTORE_B64/);
  assert.match(workflow, /APPFORGE_DEBUG_KEYSTORE_PATH/);
  assert.match(workflow, /:app:signingReport/);
  assert.match(workflow, /:app:assembleDebug/);
});

test('GitHub Release publishing remains strictly opt-in', () => {
  assert.match(workflow, /publish_latest_release:\s*\n\s*description:/);
  assert.match(workflow, /github\.event_name == 'workflow_dispatch' && inputs\.publish_latest_release == true && github\.ref == 'refs\/heads\/main'/);
});
