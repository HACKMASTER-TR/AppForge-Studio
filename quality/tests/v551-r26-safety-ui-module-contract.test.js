import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read = x => fs.readFileSync(new URL('../../'+x,import.meta.url),'utf8');
const ndk=read('android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh');
const win=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
const theme=read('android-app/app/src/main/java/com/appforge/studio/AppForgeUiTokens.kt');
const settings=read('android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt');
const storage=read('android-app/app/src/main/java/com/appforge/studio/AppForgeStorageDetailsScreen.kt');
const moduleUi=read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');
test('NDK uses private signed libraries and a real loader probe before READY',()=>{
  for(const x of ['sha256sum -c -','as_verify_indices','APPFORGE_NDK_HOST_CMAKE_PRIVATE_ELF_DEPENDENCY_FAILED',
    'APPFORGE_NDK_HOST_CMAKE_PRIVATE_LIBARCHIVE_MISSING','APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',
    'for cmake_mode in loader-argv0 direct;do','APPFORGE_NDK_HOST_SIGNED_PACKAGES=69'])assert.ok(ndk.includes(x),x);
  for(const bad of ['LD_PRELOAD=','dpkg --force','apt-get install'])assert.ok(!ndk.includes(bad),bad);
});
test('MinGW signed symlink is accepted only with exact target and dpkg ownership',()=>{
  for(const x of ['APPFORGE_WINDOWS_gcc_SIGNED_SOURCE_LINK_UNTRUSTED','APPFORGE_WINDOWS_gcc_SIGNED_VERSIONED_DRIVER_VERIFIED',
    'APPFORGE_WINDOWS_gcc_VERSIONED_OWNER_UNTRUSTED','APPFORGE_WINDOWS_gcc_VERSIONED_ELF_UNTRUSTED','sha256sum -c -'])assert.ok(win.includes(x),x);
  assert.ok(!win.includes('dpkg --force'));
});
test('Samsung reference width and all black palette are applied globally',()=>{
  assert.match(theme,/LocalDensity provides pinnedDensity/);
  assert.match(theme,/Density\(physicalDensity\.density, 1f\)/);
  assert.doesNotMatch(theme,/screenWidthDp = targetWidthDp|displayScale/);
  assert.match(theme,/Color\(0xFF000000\)/);
});
test('Material 3 typography pins all 15 font families without unsupported constructor parameter',()=>{
  assert.doesNotMatch(theme,/Typography\(defaultFontFamily/);
  assert.match(theme,/Typography\(\)\.let \{ base ->/);
  for(const style of ['displayLarge','displayMedium','displaySmall',
    'headlineLarge','headlineMedium','headlineSmall','titleLarge','titleMedium','titleSmall',
    'bodyLarge','bodyMedium','bodySmall','labelLarge','labelMedium','labelSmall']) {
    assert.ok(theme.includes(style+' = base.'+style+'.copy(fontFamily = FontFamily.SansSerif)'),style);
  }
});
test('Android own-package storage stats and module total elapsed are read-only',()=>{
  assert.match(settings,/AppForgeStorageDetailsScreen/);
  assert.match(storage,/queryStatsForPackage\(StorageManager.UUID_DEFAULT/);
  assert.match(storage,/context.packageName,Process.myUserHandle\(\)/);
  assert.ok(!storage.includes('deleteRecursively'));
  assert.match(moduleUi,/saveTotalElapsed\(context,selected\)/);
  assert.match(moduleUi,/Son denemenin toplam süresi/);
});
