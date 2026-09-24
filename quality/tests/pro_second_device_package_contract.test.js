import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(
  new URL('../../' + path, import.meta.url),
  'utf8'
);

const gradle = read('android-app/app/build.gradle.kts');
const workflow = read('.github/workflows/android-debug.yml');

test('second device is an isolated package', () => {
  assert.ok(gradle.includes(
    'applicationIdSuffix = ".prodevice2"'
  ));
  assert.ok(gradle.includes(
    'appforgeProRecoveryTest && appforgeProSecondDevice'
  ));
});

test('second-device build keeps recovery interruption disabled', () => {
  assert.ok(gradle.includes(
    'appforgeProSecondDevice = providers.gradleProperty'
  ));
  assert.ok(gradle.includes(
    'appforgeProRecoveryTest.toString()'
  ));
  assert.ok(workflow.includes(
    'EXTRA=(-PappforgeProSecondDevice=true)'
  ));
});

test('CI checks the package and isolates its artifact', () => {
  assert.ok(workflow.includes(
    'PACKAGE="com.appforge.studio.prodevice2"'
  ));
  assert.ok(workflow.includes(
    'AppForgeStudio-Pro-Device2-${{ github.sha }}'
  ));
  assert.ok(workflow.includes(
    '!inputs.pro_recovery_test && !inputs.pro_second_device'
  ));
});
