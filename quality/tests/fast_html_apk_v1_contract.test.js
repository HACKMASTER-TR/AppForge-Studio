'use strict';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const source = 'android-app/app/src/main/java/com/appforge/studio/build/';

test('generated HTML template assets are Gradle 9 compatible', () => {
 const gradle = read('android-app/app/build.gradle.kts');
 assert.match(gradle, /assets\.srcDir\(layout\.buildDirectory\.dir\("generated\/html-template\/assets"\)\.get\(\)\.asFile\)/);
 assert.match(gradle, /dependsOn\(generateFastHtmlTemplate\)/);
 assert.doesNotMatch(gradle, /assets\.srcDir\(layout\.buildDirectory\.dir\("generated\/html-template\/assets"\)\)/);
 const shell = read('android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java');
 assert.match(shell, /setDomStorageEnabled\(true\)/);
 assert.doesNotMatch(shell, /setDomStorageEnabled\(false\)/);
});

test('fast HTML/URL APK bypasses rootfs and offline package without touching other engines', () => {
  const engine = read(source + 'DeviceBuildEngine.kt');
  const offline = read(source + 'OfflineBuildPackManager.kt');
  assert.match(engine, /FastHtmlApkPolicy\.isCandidate\(draft\)/);
  assert.match(engine, /FastHtmlApkPackager\.build\(context, draft, workspace\)/);
  assert.ok(engine.indexOf('FastHtmlApkPackager.build') < engine.indexOf('DeviceBuildRuntimeV3\n'));
  assert.match(offline, /if \(FastHtmlApkPolicy\.isCandidate\(draft\)\)/);
  assert.match(offline, /FastHtmlApkPolicy\.requireSupported\(draft\)/);
  assert.match(offline, /"node-web"/);
  assert.match(offline, /"expo"/);
});

test('only HTML/HTTPS URL, APK output, debug/local signing supported', () => {
  const eligibility = read(source + 'FastHtmlApkPolicy.kt');
  assert.match(eligibility, /engine != "webview-static"/);
  assert.match(eligibility, /setOf\("html", "web-static", "webview"\)/);
  assert.match(eligibility, /setOf\(DeviceArtifactKind\.APK\)/);
  assert.match(eligibility, /scheme\.equals\("https", true\)/);
  assert.match(eligibility, /SigningMode\.DEBUG/);
  assert.doesNotMatch(eligibility, /"node-web"\s*->/);
});

test('APK signing and output verifier fail closed', () => {
  const builder = read(source + 'FastHtmlApkPackager.kt');
  assert.match(builder, /setV2SigningEnabled\(true\)/);
  assert.match(builder, /setV3SigningEnabled\(true\)/);
  assert.match(builder, /verification\.isVerified/);
  assert.match(builder, /AndroidKeyStore/);
  assert.match(builder, /unsigned\.delete\(\)/);
  assert.match(builder, /resources\.arsc/);
  assert.match(builder, /require\(.*HTML/i);
});

test('baked HTML shell does not add JS native bridge or mixed HTTP navigation', () => {
  const shell = read('android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java');
  const manifest = read('android-app/html-apk-template/src/main/AndroidManifest.xml');
  assert.match(shell, /setJavaScriptEnabled\(true\)/);
  assert.match(shell, /setAllowUniversalAccessFromFileURLs\(false\)/);
  assert.doesNotMatch(shell, /addJavascriptInterface\(/);
  assert.match(manifest, /usesCleartextTraffic="false"/);
  assert.match(manifest, /allowBackup="false"/);
});

// A source-contract guard complements the runtime Kotlin output-selection matrix.
test('V15.3 Windows and combined output routing uses a shared Android-only fast gate', () => {
  const policy = read(source + 'FastHtmlApkPolicy.kt');
  const engine = read(source + 'DeviceBuildEngine.kt');
  const manager = read(source + 'OfflineBuildPackManager.kt');
  const kotlinTests = read('android-app/app/src/test/java/com/appforge/studio/build/FastHtmlAabRoutingTest.kt');
  assert.match(policy, /DeviceBuildCapabilities\.requestedOutputs\(draft\.buildOutput\)/);
  assert.match(policy, /outputs\.isNotEmpty\(\) && outputs\.all \{ kind ->/);
  assert.match(policy, /kind == DeviceArtifactKind\.APK \|\| kind == DeviceArtifactKind\.AAB/);
  assert.match(policy, /if \(!requestsAndroidOnlyOutputs\(draft\)\) return false/);
  assert.equal(manager.match(/FastHtmlApkPolicy\.isCandidate\(draft\)/g)?.length, 2);
  assert.equal(engine.match(/FastHtmlApkPolicy\.isCandidate\(draft\)/g)?.length, 2);
  assert.match(engine, /val sourceEngine\s*=\s*effectiveSourceEngine\s*\(\s*draft\s*\)/);
  assert.match(engine, /DeviceArtifactKind\.WINDOWS_EXE in\s+requestedOutputs/);
  assert.match(engine, /buildWindowsIfRequested\(/);
  assert.match(kotlinTests, /allOutputChoicesSelectAndroidFastOrNormalDeviceRoute/);
  for (const output of ['exe','windows-exe','portable-exe','windows-portable-exe',
    'all','apk+aab+exe','native-exe','windows-native-exe']) {
    assert.ok(kotlinTests.includes('"' + output + '"'), output);
  }
});
