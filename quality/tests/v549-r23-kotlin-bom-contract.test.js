import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../', import.meta.url);
const read = p => fs.readFileSync(new URL(p, root), 'utf8');
const workflow = read('.github/workflows/v549-r23-signed-apk-ci.yml');
const settings = read('android-app/settings.gradle.kts');
const tool = read('quality/tools/v549_r23_kotlin_bom.py');

test('R23 pins Kotlin 2.4.10 BOM in CI-only temp; never skips TLS or signer verification', () => {
  assert.match(workflow, /Verify pinned Kotlin 2\.4\.10 Gradle plugins BOM/);
  assert.match(workflow, /-PappforgeCIKotlinBomRepo="\$RUNNER_TEMP\/v549-r23-kotlin-bom"/);
  assert.match(workflow, /test "\$CERT" = dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082/);
  assert.match(workflow, /44601343974/);
  assert.match(workflow, /sha256:36e710281550c0949ca95a95ee76c84fa5be4d4d259044beb471fa4c1db78d6b/);
  assert.match(workflow, /testDebugUnitTest :app:assembleDebug/);
  assert.match(settings, /System\.getenv\("GITHUB_ACTIONS"\) == "true"/);
  assert.match(settings, /includeModule\("org\.jetbrains\.kotlin", "kotlin-gradle-plugins-bom"\)/);
  assert.match(settings, /appforgeCIKotlinBomRepo/);
  assert.match(tool, /733fccda03ae34773e7b5c85d0e278c897df283370059cb39825f7c0381e49b6/);
  assert.match(tool, /48eb4756284d1d7ea24639d22861f15ff0cb685a15331a6c6a3674ff44baf029/);
  assert.doesNotMatch(tool + settings, /trustAll|disableSsl|insecure=true|8\.8\.8\.8|1\.1\.1\.1/);
});
