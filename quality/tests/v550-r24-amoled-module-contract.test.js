import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read = p => fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const theme=read('android-app/app/src/main/java/com/appforge/studio/AppForgeUiTokens.kt');
const main=read('android-app/app/src/main/java/com/appforge/studio/MainActivity.kt');
const manifest=read('android-app/app/src/main/AndroidManifest.xml');
const screen=read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');
const history=read('android-app/app/src/main/java/com/appforge/studio/build/DownloadHistoryV84.kt');
const recorder=read('android-app/app/src/main/java/com/appforge/studio/build/DownloadCoordinator.kt');
const win=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
test('all AppForgeTheme screens use absolute black and fixed font scale',()=>{
  assert.match(theme,/Color\(0xFF000000\)/);
  assert.match(theme,/LocalDensity provides pinnedDensity/);
  assert.match(read('android-app/app/src/main/res/values/themes.xml'),/windowBackground">#000000/);
});
test('APK special access is requested through Android settings, never forced',()=>{
  assert.match(manifest,/android.permission.REQUEST_INSTALL_PACKAGES/);
  assert.match(main,/ACTION_MANAGE_UNKNOWN_APP_SOURCES/);
  assert.match(main,/canRequestPackageInstalls\(\)/);
  assert.doesNotMatch(main,/pm grant.*REQUEST_INSTALL_PACKAGES/);
});
test('storage figures come from measured StatFs, not fabricated estimates',()=>{
  assert.match(screen,/android.os.StatFs\(context.filesDir.absolutePath\)/);
  assert.match(screen,/availableBytes/);
  assert.match(screen,/totalBytes/);
  assert.match(screen,/Henüz hesaplanmadı/);
});
test('telemetry-only entries do not fill persisted pinned transfer history',()=>{
  assert.match(recorder,/if \(bound == null && state != DownloadState\.FAILED\) return/);
  assert.match(recorder,/val completedBytes=if\(bound==null\)0L/);
  assert.match(history,/row.stagingIdentity == null && row.expectedSha256 == null/);
  assert.match(history,/limit:Int=512/);
});
test('Windows signed POSIX restore requires package ownership and ARM64 ELF verification',()=>{
  for(const anchor of ['SIGNED_VERSIONED_DRIVER_RESTORED=PASS','VERSIONED_OWNER_UNTRUSTED',
    'VERSIONED_ELF_UNTRUSTED','SIGNED_ARCHIVE_VERIFIED=PASS'])assert.ok(win.includes(anchor),anchor);
  assert.doesNotMatch(win,/dpkg --force|apt-get -f install|rm -rf \/var\/lib\/dpkg/);
});
