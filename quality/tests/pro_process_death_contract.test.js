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

test('process-death package is isolated and debug opt-in', () => {
  assert.ok(gradle.includes(
    'applicationIdSuffix = ".prodeath"'
  ));
  assert.ok(gradle.includes(
    'appforgeProProcessDeathTest.toString()'
  ));
  assert.ok(gradle.includes(
    '"PRO_PROCESS_DEATH_TEST",\n                "false"'
  ));
  assert.ok(gradle.includes(
    '!appforgeProRecoveryTest && !appforgeProSecondDevice'
  ));
});

test('actual process death occurs before persistence', () => {
  const start = client.indexOf('fun redeemAndVerify(');
  const end = client.indexOf(
    'fun testLiveStatusReplay()', start
  );
  const flow = client.slice(start, end);

  const accepted = flow.indexOf(
    'result.optBoolean("ok")'
  );
  const guard = flow.indexOf(
    'BuildConfig.PRO_PROCESS_DEATH_TEST'
  );
  const kill = flow.indexOf(
    'android.os.Process.killProcess('
  );
  const fallback = flow.indexOf(
    'PROCESS_DEATH_TEST_TERMINATION_NOT_CONFIRMED'
  );
  const save = flow.indexOf(
    'persistInstallation(id)'
  );

  assert.ok(accepted >= 0);
  assert.ok(guard > accepted);
  assert.ok(kill > guard);
  assert.ok(fallback > kill);
  assert.ok(save > fallback);

  assert.ok(flow.includes(
    'appContext.packageName =='
  ));
  assert.ok(flow.includes(
    '"com.appforge.studio.prodeath"'
  ));
  assert.ok(flow.includes(
    'BuildConfig.DEBUG'
  ));
});

test('CI artifact cannot replace a normal or recovery APK', () => {
  assert.ok(workflow.includes(
    'PACKAGE="com.appforge.studio.prodeath"'
  ));
  assert.ok(workflow.includes(
    'EXTRA=(-PappforgeProProcessDeathTest=true)'
  ));
  assert.ok(workflow.includes(
    'AppForgeStudio-Pro-Process-Death-${{ github.sha }}'
  ));
  assert.ok(workflow.includes(
    '!inputs.pro_second_device && !inputs.pro_process_death_test'
  ));
});
