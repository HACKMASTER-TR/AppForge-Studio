import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../',import.meta.url);
const read = p => fs.readFileSync(new URL(p,root),'utf8');
const impl = read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleInstallerV3.kt');
const screen = read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');
const manager = read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
const engine = read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
const probe = read('android-app/app/src/main/assets/device-build/isolated-physical-acceptance.sh');

test('single selected module is isolated with its own complete dependency closure and signed APT',()=>{
  assert.match(impl,/ModuleIsolationV1\.request\(target\)/);
  assert.match(impl,/IsolatedModuleRuntimeV2\.stageFreshBaseline\(app, target, true\)/);
  assert.match(impl,/V9R2ReleaseStore\.load\(app\)/);
  assert.match(impl,/V9SelectiveR2ModuleCache\.fetchSelectedWithConsent/);
  assert.match(impl,/V9R2AptInstallReuse\.seedForDebug\(app, staged, id\)/);
  assert.match(impl,/RuntimeGenerationStore\(root\)\.prepareOwner\("ISOLATED:/);
  assert.match(impl,/generation\.commit\(verify/);
  assert.doesNotMatch(impl,/System\.loadLibrary|adb install|dpkg --force|apt-get --allow-unauthenticated/);
});

test('debug-only acceptance never changes current working modules before actual test',()=>{
  assert.match(impl,/check\(com\.appforge\.studio\.BuildConfig\.DEBUG\)/);
  assert.match(impl,/module-functional-test\.sh/);
  assert.match(impl,/isolated-physical-acceptance\.sh/);
  assert.match(impl,/ISOLATED_FINAL_RECEIPT_INVALID/);
  assert.match(impl,/ubuntuSha256/);
  assert.match(impl,/dpkgSha256/);
  assert.match(impl,/functionalTests/);
  assert.match(impl,/AndroidRuntimeTrust\.ready/);
  assert.match(impl,/!job\.isActive/);
  assert.match(impl,/workspace\.deleteRecursively\(\)/);
  assert.doesNotMatch(impl,/legacy\.deleteRecursively\(|DeviceBuildRuntimeV3\.ensureReady\(/);
});

test('physically executed CMake loader and Windows C++ PE32 checks deny unproven READY',()=>{
  assert.match(probe,/cmake_minimum_required\(VERSION 3\.22\)/);
  assert.match(probe,/CMAKE_ROOT/);
  assert.match(probe,/APPFORGE_NDK_HOST_CMAKE_MODULES=PASS/);
  assert.match(probe,/x86_64-w64-mingw32-g\+\+-posix/);
  assert.match(probe,/PE32\+ executable/);
  assert.match(probe,/APPFORGE_ISOLATED_FUNCTIONAL_PASS/);
});

test('new build routing is only available after real acceptance and in debug builds',()=>{
  assert.match(engine,/BuildConfig\.DEBUG[\s\S]{0,120}IsolatedModuleInstallerV3\.rootForProject/);
  assert.match(manager,/BuildConfig\.DEBUG[\s\S]{0,140}IsolatedModuleInstallerV3\.rootForProject/);
  assert.match(manager,/internal suspend fun executeIsolatedForAcceptance/);
  assert.match(screen,/fun startIsolated\(/);
  assert.match(screen,/IsolatedModuleInstallerV3\.installSelected/);
  assert.match(screen,/Bağımsız kur ve test et/);
  assert.match(screen,/Onayla ve kur • mevcut sistem/);
});
