import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root=new URL('../../',import.meta.url);
const read=p=>fs.readFileSync(new URL(p,root),'utf8');
const ndk=read('android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh');
const tool=read('android-app/app/src/main/assets/device-build/install-toolchain.sh');
const win=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
const ui=read('android-app/app/src/main/java/com/appforge/studio/V9R2CandidatePrefetchCard.kt');
const manager=read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
test('Expo and NDK use signed private ARM64 host; shared libc remains unchanged',()=>{
 assert.match(tool,/expo:aarch64\|expo:arm64/);
 assert.match(ndk,/RUST_ANDROID_NDK\|EXPO_REACT_NATIVE/);
 assert.match(ndk,/APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER/);
 assert.match(ndk,/APPFORGE_CMAKE_PROBE_MODE/);
 assert.match(ndk,/CMAKE_ROOT/);
 assert.doesNotMatch(ndk,/apt-get install|dpkg -i/);
});
test('Windows requires signed lock, only retries explicit pinned packages',()=>{
 assert.match(win,/have_windows_lock/);
 assert.match(win,/--reinstall/);
 assert.doesNotMatch(win,/apt-get[\s\\]+-f[\s\\]+install/);
 assert.match(manager,/APPFORGE_WINDOWS_R2_INCOMPLETE/);
});
test('module center does not expose editable R2 source',()=>{
 assert.match(ui,/İndir ve Kur/);
 assert.doesNotMatch(ui,/İmzalı R2 kaynağını değiştir/);
 assert.doesNotMatch(ui,/chooseManifest\.launch/);
 assert.doesNotMatch(ui,/chooseSignature\.launch/);
});
