import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const win=fs.readFileSync(new URL('../../android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh',import.meta.url),'utf8');
test('MinGW PRoot fallback requires GNU lstat regular type, exact 0755, signed bytes and package owner',()=>{
  for(const text of [
    'stat -c %F -- "$target"',
    'stat -c %a -- "$target"',
    '"$target_stat_kind" = \'regular file\'',
    '"$target_stat_mode" = 755',
    'cmp -s "$source_driver" "$target"',
    '"$independent_owner" != "$pkg: $target"',
    'APPFORGE_WINDOWS_${kind}_PROROOT_STAT_REGULAR_SIGNED=PASS',
    'APPFORGE_WINDOWS_${kind}_EXEC_REQUIRES_PHYSICAL_PROBE=PASS',
    'APPFORGE_WINDOWS_${kind}_RESTORED_DRIVER_EXEC_PROBE_FAILED',
    '"$target" --version'
  ]) assert.ok(win.includes(text),text);
  for(const forbidden of ['dpkg --force','rm -rf /var/lib/dpkg','apt-get -f install'])
    assert.ok(!win.includes(forbidden),forbidden);
});
