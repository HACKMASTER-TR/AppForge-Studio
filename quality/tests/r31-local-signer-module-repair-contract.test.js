import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=(p)=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const win=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
const ndk=read('android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh');
test('Windows: only absent target or verified dpkg-owned target is restorable',()=>{
  for(const x of ['target_absent_before_copy=0','target_absent_before_copy=1','[ "$installed_owner" = "$pkg: $target" ] ||','[ "$target_absent_before_copy" = 1 ]','cmp -s "$source_driver" "$target"','APPFORGE_WINDOWS_${kind}_RESTORED_BYTES_OR_OWNERSHIP_MISMATCH','APPFORGE_WINDOWS_${kind}_SIGNED_NEW_TARGET']) assert.ok(win.includes(x),x);
  for(const bad of ['dpkg --force','rm -rf /var/lib/dpkg','apt-get -f install']) assert.ok(!win.includes(bad),bad);
});
test('CMake: private resource hint is not a bypass of physical root probe',()=>{
  for(const x of ['CMAKE_ROOT="$sysroot/usr/share/cmake-3.28"','APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER','APPFORGE_NDK_HOST_CMAKE_ROOT_PROBE_FAILED','APPFORGE_NDK_HOST_CMAKE_MODULES=PASS','APPFORGE_NDK_HOST_CMAKE_PRIVATE_ELF_DEPENDENCY_FAILED']) assert.ok(ndk.includes(x),x);
  for(const bad of ['LD_PRELOAD=','dpkg --force','apt-get install']) assert.ok(!ndk.includes(bad),bad);
});
