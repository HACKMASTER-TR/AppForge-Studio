import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const base = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = file => fs.readFileSync(path.join(base,file),'utf8');
const gradle = read('android-app/app/build.gradle.kts');
const model = read('android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt');
const main = read('android-app/app/src/main/java/com/appforge/studio/MainActivity.kt');
const offline = read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
const policy = read('android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPolicy.kt');

test('new projects default to APK; AAB and both options remain available', () => {
  assert.match(model, /var buildOutput: String = "apk"/);
  assert.match(main, /add\("apk"\)/);
  assert.match(main, /add\("aab"\)/);
  assert.match(main, /add\("both"\)/);
});
test('Quick Create defaults are accepted by fast HTML policy', () => {
  const i=main.indexOf('private fun createQuickDraft(');
  const end=main.indexOf('\n@Composable',i);
  assert.ok(i>=0 && end>i);
  const quick=main.slice(i,end);
  assert.match(quick,/buildOutput\s*=\s*"apk"/);
  assert.match(quick,/signingMode\s*=\s*SigningMode\.DEBUG/);
  assert.match(quick,/notifications\s*=\s*false/);
  // V2 adds real opt-in notifications; Quick remains OFF and unsafe bridge combinations remain normal.
  assert.match(policy,/draft\.notifications && \(!draft\.javascriptBridge \|\| !draft\.webJavaScriptEnabled/);
  assert.match(policy,/if \(draft\.notifications \|\| draft\.mediaPlayerBridge\) add\("android\.permission\.POST_NOTIFICATIONS"\)/);
});
test('generated local HTML templates choose APK', () => {
  const i=main.indexOf('AppScreen.TEMPLATES -> TemplatesScreen(');
  const end=main.indexOf('AppScreen.SETTINGS ->',i);
  assert.ok(i>=0 && end>i);
  assert.match(main.slice(i,end),/appName\s*=\s*template.name,\s*buildOutput\s*=\s*"apk"/);
});
test('fast HTML invalid options stay in Builder, never redirect to offline toolchain', () => {
  const i=main.indexOf('val offlinePackCheck =',main.indexOf('val startBuildWithDraft:'));
  const end=main.indexOf('buildBusy =',i);
  assert.ok(i>=0 && end>i);
  const gate=main.slice(i,end);
  assert.match(gate,/offlinePackCheck\.engine == "fast-html-apk"/);
  assert.match(gate,/offlinePackCheck\.missing\.firstOrNull\(\)/);
  assert.match(gate,/offlinePackCheck\.engine == "webview-settings" -> "settings-invalid"/);
  assert.match(gate,/offlinePackCheck\.engine == "fast-html-apk" -> "fast-output-invalid"/);
  assert.match(gate,/PREFLIGHT_FAILURE=\$preflightFailure/);
  assert.match(gate,/WEBVIEW_SETTINGS_INVALID=YES/);
  assert.match(gate,/if \(preflightFailure == "dependency-missing"\)\s*\{\s*openOfflinePack\(\)/);
  assert.doesNotMatch(gate,/openWorkspaceScreen\(\s*AppScreen\.OFFLINE_PACK/);
  assert.match(gate,/FAST_HTML_APK_SETTINGS_REQUIRED=YES/);
  assert.match(offline,/engine = "fast-html-apk"/);
  assert.match(offline,/"Android SDK \+ JDK \+ Gradle"/);
});
test('CI builds AAB shell template without marking runtime packaging complete', () => {
  assert.match(gradle,/val generateFastHtmlAabTemplate = tasks.register<Copy>\("generateFastHtmlAabTemplate"\)/);
  assert.match(gradle,/dependsOn\(":html-apk-template:bundleRelease"\)/);
  assert.match(gradle,/include\("\*\.aab"\)/);
  assert.match(gradle,/rename \{ "html-aab-template.aab" \}/);
  assert.match(gradle,/dependsOn\(generateFastHtmlAabTemplate\)/);
  const engine=read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
  assert.doesNotMatch(engine,/FastHtmlAabPackager\.build\(/);
});
