import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const get=p=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const n=get('android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh');
const w=get('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
test('CMake probes in isolated prefix and requires real module root',()=>{
  for(const v of ['APPFORGE_NDK_CMAKE_PROBE_PRIVATE_PREFIX=PASS',
    'mktemp -d "$PREFIX/.cmake-select.', 'mktemp -d "$PREFIX/.cmake-functional.',
    'APPFORGE_NDK_CMAKE_PRIVATE_PREFIX_LAYOUT=VERIFIED',
    'private-prefix-argv0)',
    'APPFORGE_NDK_HOST_PRIVATE_PREFIX_CMAKE_PROBE=PASS',
    'APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',
    'APPFORGE_NDK_HOST_CMAKE_MODULES=PASS']) assert.ok(n.includes(v),v);
  for(const forbidden of ['LD_PRELOAD=','dpkg --force','apt-get install'])assert.ok(!n.includes(forbidden),forbidden);
});
test('MinGW only repairs authenticated dpkg-owned identical bytes',()=>{
  for(const v of ['APPFORGE_WINDOWS_${kind}_RESTORED_SIGNED_BYTES=VERIFIED',
    '[ "$installed_owner" = "$pkg: $target" ]',
    'cmp -s "$source_driver" "$target"',
    'chmod 0755 "$target"',
    'APPFORGE_WINDOWS_${kind}_RESTORED_DRIVER_EXEC_PROBE_FAILED',
    'APPFORGE_WINDOWS_${kind}_RESTORED_BYTES_OR_OWNERSHIP_MISMATCH',
    'SIGNED_ARCHIVE_VERIFIED=PASS'])assert.ok(w.includes(v),v);
  for(const forbidden of ['dpkg --force','rm -rf /var/lib/dpkg','apt-get -f install'])assert.ok(!w.includes(forbidden),forbidden);
});
