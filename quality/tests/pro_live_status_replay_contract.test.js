import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(
  new URL('../../' + path, import.meta.url),
  'utf8'
);

const client = read(
  'android-app/app/src/main/java/com/appforge/studio/' +
  'security/ProCodeClient.kt'
);

const ui = read(
  'android-app/app/src/main/java/com/appforge/studio/' +
  'ProCodePanels.kt'
);

test('live replay is isolated to debug APK', () => {
  assert.match(
    client,
    /BuildConfig.DEBUG\s*&&\s*BuildConfig.PRO_RECOVERY_TEST/
  );
  assert.match(
    client,
    /com\.appforge\.studio\.prorecovery/
  );
  assert.match(
    ui,
    /if \(BuildConfig.DEBUG && BuildConfig.PRO_RECOVERY_TEST\)/
  );
});

test('same payload is replayed before fresh verify', () => {
  const start = client.indexOf('fun testLiveStatusReplay()');
  const end = client.indexOf('fun verifyStatus()', start);
  const method = client.slice(start, end);

  assert.ok(start >= 0 && end > start);
  assert.match(method, /val payload = JSONObject\(\)/);
  assert.match(
    method,
    /post\(\s*"\/api\/pro\/code\/status",\s*payload\s*\)/
  );
  assert.match(
    method,
    /post\("\/api\/pro\/code\/status", payload\)/
  );
  assert.match(method, /challenge_unavailable/);
  assert.match(method, /return verifyStatus\(\)/);
});

test('replay requires an explicit UI action', () => {
  assert.match(ui, /STAGING CHALLENGE REPLAY TEST/);
  assert.match(ui, /client\.testLiveStatusReplay\(\)/);
});
