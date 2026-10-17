import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertFastHtmlEngineIsolation } from './helpers/fast_html_policy_regression.js';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const main = read('android-app/app/src/main/java/com/appforge/studio/MainActivity.kt');
const factory = read('android-app/app/src/main/java/com/appforge/studio/io/TemplateProjectFactory.kt');
const local = read('android-app/app/src/main/java/com/appforge/studio/io/LocalTemplateCatalog.kt');

test('quick app name follows package slug unless package manually overridden', () => {
  assert.match(main, /var quickPackageManualOverride by rememberSaveable/);
  assert.match(main, /val shouldSyncPackage = !quickPackageManualOverride/);
  assert.match(main, /quickPackageName\(entered\)/);
  assert.match(main, /quickPackageManualOverride = true/);
  assert.match(main, /packageName = if \(shouldSyncPackage\)/);
});

test('exactly the built-in factory template slugs are listed locally', () => {
  const actual = [...factory.matchAll(/^\s*"([a-z][a-z0-9-]+)"\s*->/gm)].map(m => m[1]).sort();
  const catalog = [...local.matchAll(/entry\("([a-z][a-z0-9-]+)"/g)].map(m => m[1]).sort();
  assert.equal(catalog.length, 18);
  assert.deepEqual(catalog, actual);
});

test('templates remain available with no session and remote errors', () => {
  assert.match(main, /val local = com\.appforge\.studio\.io\.LocalTemplateCatalog\.templates/);
  assert.match(main, /templates = local/);
  assert.match(main, /val current = session \?: run/);
  assert.match(main, /\(local \+ remote\)\.distinctBy \{ it\.slug \}/);
  assert.doesNotMatch(main, /templates = emptyList\(\)/);
});

test('account status labels removed but privilege logic untouched', () => {
  const settings = read('android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt');
  assert.doesNotMatch(main, /"AppForge Hesapsız Kullanım"|value = "Hesapsız"|"Hesapsız kullanım aktif/);
  assert.doesNotMatch(settings, /Normal AppForge kullanımı hesapsızdır/);
});

test('HTML URL bypass stays limited to APK without touching native or Expo build engines', () => {
  const policy = read('android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPolicy.kt');
  const engine = read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
  assert.match(policy, /setOf\(DeviceArtifactKind\.APK\)/);
  assertFastHtmlEngineIsolation(root);
  assert.match(engine, /FastHtmlApkPackager\.build\(context, draft, workspace\)/);
  assert.match(engine, /"expo" -> buildExpoProject/);
  assert.match(engine, /"android-gradle" -> buildAndroidProject/);
});

test('fast HTML APK bundles relative CSS/JS and static assets without npm or Gradle', () => {
  const packager = read('android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPackager.kt');
  const shell = read('android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java');
  assert.match(packager, /readHtmlProject\(draft\)/);
  assert.match(packager, /"html", "css", "js", "mjs"/);
  assert.match(packager, /assets\["assets\/site\/\$relative"\] = file\.readBytes\(\)/);
  assert.match(packager, /MAX_PROJECT_FILES = 250/);
  assert.match(packager, /MAX_PROJECT_BYTES = 24L/);
  assert.match(packager, /Files\.isSymbolicLink/);
  assert.match(packager, /"assets\/site\/\$entry" in assets/);
  assert.match(shell, /setJavaScriptEnabled\(true\)/);
  assert.match(shell, /setDomStorageEnabled\(true\)/);
  assert.match(shell, /setAllowUniversalAccessFromFileURLs\(false\)/);
  assert.match(shell, /MIXED_CONTENT_NEVER_ALLOW/);
  assert.match(shell, /new Uri\.Builder\(\)\.scheme\("file"\)/);
  // Opaque file origins use one capability-checked document transport. Executable
  // hostile-origin/subframe/replay/settings/byte contracts live in
  // html_document_bridge_runtime.test.js and execute the production helper.
  assert.match(shell, /addJavascriptInterface\(new LocalDocuments\(\), "AppForgeLocalDocuments"\)/);
  assert.equal([...shell.matchAll(/@(?:android\.webkit\.)?JavascriptInterface/g)].length, 1);
  assert.match(shell, /current\.matchesCompatToken\(token\)/);
  assert.match(shell, /current\.receiveLocal\(token,raw\)/);
  assert.match(shell, /setAllowFileAccess\(false\)/);
  assert.match(shell, /setAllowFileAccessFromFileURLs\(false\)/);
  assert.match(shell, /String localPath = "\/android_asset\/site\/" \+ entry/);
});

test('local template recovery never deletes existing project folders', () => {
  assert.doesNotMatch(factory, /projectDir\.deleteRecursively\(\)/);
  assert.match(factory, /UUID\.randomUUID\(\)/);
});
