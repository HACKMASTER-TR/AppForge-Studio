import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const shell=fs.readFileSync(new URL('../../android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh',import.meta.url),'utf8');
test('Windows Native cannot bypass the authenticated R2 APT wrapper',()=>{
  assert.match(shell,/managed_apt update/);
  assert.match(shell,/managed_apt install/);
  assert.match(shell,/APPFORGE_APT_MANAGED_INSTALL/);
  assert.match(shell,/APPFORGE_R2_RELEASE_AUTHORIZED/);
  assert.match(shell,/telemetry-bin\/apt-get/);
  assert.match(shell,/apt-snapshot-lock\.sh/);
  assert.doesNotMatch(shell,/^apt-get update$/m);
  assert.doesNotMatch(shell,/^\s+apt-get install /m);
});
test('signed MinGW ARM64 C++ driver check does not depend on not-yet-installed file(1)',()=>{
  assert.match(shell,/APPFORGE_WINDOWS_\$\{kind\}_SIGNED_ELF_ARCH_MISMATCH/);
  assert.match(shell,/restore_signed_posix_driver gcc/);
  assert.match(shell,/restore_signed_posix_driver gxx/);
  assert.match(shell,/\[ "\$header" = 7f454c460201 \]/);
  assert.match(shell,/\[ "\$machine" = b700 \]/);
  assert.match(shell,/dpkg-deb -f "\$archive" Architecture/);
  assert.match(shell,/sha256sum -c/);
  assert.doesNotMatch(shell,/file -L "\$source_driver"/);
});
