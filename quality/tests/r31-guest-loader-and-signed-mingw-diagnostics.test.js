import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=(p)=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const ndk=read('android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh');
const win=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
test('CMake candidate loaders require actual private CMAKE_ROOT -P success',()=>{
  for(const v of ['loader-argv0-no-guest','loader-plain-no-guest','direct-no-guest',
    'unset PROROOT_GUEST_EXE','APPFORGE_NDK_CMAKE_SCRIPT_SHELL_VISIBLE=PASS',
    'APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',
    'APPFORGE_NDK_HOST_CMAKE_ROOT_PROBE_FAILED',
    'APPFORGE_NDK_HOST_CMAKE_MODULES=PASS']) assert.ok(ndk.includes(v),v);
  assert.ok(ndk.includes('APPFORGE_CMAKE_PROBE_MODE="$cmake_mode" "$BIN/cmake" -P "$cmake_probe_dir/check.cmake"'));
});
test('MinGW signed temporary file and guarded restoration retain strict ownership',()=>{
  for(const v of ['APPFORGE_WINDOWS_${kind}_SIGNED_TMP_MODE=VERIFIED',
    'cmp -s "$source_driver" "$tmp"','APPFORGE_WINDOWS_${kind}_AFTER_MOVE_TYPE=',
    'APPFORGE_WINDOWS_${kind}_SIGNED_BYTES_COMPARE=',
    'APPFORGE_WINDOWS_${kind}_DPKG_TARGET_OWNER=',
    'APPFORGE_WINDOWS_${kind}_RESTORED_BYTES_OR_OWNERSHIP_MISMATCH',
    '[ "$installed_owner" = "$pkg: $target" ]',
    '[ "$target_absent_before_copy" = 1 ]']) assert.ok(win.includes(v),v);
  for(const bad of ['dpkg --force','rm -rf /var/lib/dpkg','apt-get -f install']) assert.ok(!win.includes(bad),bad);
});
