import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read = p => fs.readFileSync(new URL('../../' + p, import.meta.url), 'utf8');
const base = 'android-app/app/src/main/';
test('V3 UI support decisions disable and guard switches through central matrix', () => {
  const ui = read(base+'java/com/appforge/studio/MainActivity.kt');
  assert.match(ui, /FeatureSupportMatrix\.feature/);
  assert.match(ui, /enabled\s*=\s*(?:support|decision)[^\n]*selectable/);
  assert.match(ui, /FeatureSupportMatrix\.acceptUserChange/);
  assert.match(ui, /requiredCapabilities\s*=/);
  assert.match(ui, /additionalPermissions\s*=\s*baseDraft\.additionalPermissions/);
});
test('V3 installer scopes SDK consent and never defaults to installing every module', () => {
  const manager=read(base+'java/com/appforge/studio/build/OfflineBuildPackManager.kt');
  const screen=read(base+'java/com/appforge/studio/OfflineBuildPackScreen.kt');
  assert.match(manager,/OfflineModules\.resolve\(selected\)/);
  assert.match(manager,/pending\.filterNot|plan\.filterNot/);
  assert.match(screen,/val needsSdk=modules\?\.any \{\s*it==OfflineModuleId\.ANDROID_CORE \|\| it==OfflineModuleId\.EXPO_REACT_NATIVE \|\|\s*it==OfflineModuleId\.RUST_ANDROID_NDK/);
  assert.match(screen,/Onayla ve kur/);
  assert.match(screen,/Araç indirmeden devam et/);
});
test('standalone Node module pins and verifies archive without Android toolchain install', () => {
  const node=read(base+'assets/device-build/install-node-module.sh');
  assert.match(node,/sha256sum -c/);
  assert.match(node,/--no-same-owner --no-same-permissions/);
  assert.doesNotMatch(node,/sdkmanager|install-toolchain\.sh|prepare-offline-pack\.sh|apt-get[^\n]*(?:python|mingw|openjdk)/);
  assert.doesNotMatch(node,/curl[^\n]*(?:--insecure| -k\b)/);
});
test('module UI uses byte measurements and does not equate download completion to READY', () => {
  const screen=read(base+'java/com/appforge/studio/OfflineBuildPackScreen.kt');
  const downloader=read(base+'java/com/appforge/studio/build/VerifiedModuleDownload.kt');
  assert.match(screen,/event\.percent/);
  assert.match(screen,/event\.averageSpeedBytesPerSecond/);
  assert.match(screen,/toplam bilinmiyor/);
  assert.match(screen,/check\(OfflineBuildPackManager\.moduleReady/);
  assert.match(screen,/!OfflineBuildPackManager\.hasPendingLifecycle/);
  assert.doesNotMatch(downloader,/ModulePhase\.READY/);
  assert.match(downloader,/StandardCopyOption\.ATOMIC_MOVE/);
});
