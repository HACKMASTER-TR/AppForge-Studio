import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const guest=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
const host=read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
test('W17 authenticated ELF needs an actual fresh PRoot after same-session failure',()=>{
  for(const witness of [
    'APPFORGE_WINDOWS_${kind}_SIGNED_BYTES_COMPARE=',
    'APPFORGE_WINDOWS_${kind}_DPKG_TARGET_OWNER=',
    'APPFORGE_WINDOWS_${kind}_RESTORED_SIGNED_BYTES=VERIFIED',
    'if ! "$target" --version >/dev/null;then',
    'APPFORGE_WINDOWS_NATIVE_PROROOT_REENTRY_REQUIRED',
    'return 75', 'rm -rf -- "$stage"',
    'APPFORGE_WINDOWS_${kind}_RESTORED_DRIVER_EXEC_PROBE_FAILED'
  ])assert.ok(guest.includes(witness),witness);
  assert.match(guest,/--library-path \/lib\/aarch64-linux-gnu:\/usr\/lib\/aarch64-linux-gnu/);
  assert.match(guest,/APPFORGE_WINDOWS_NATIVE_PROROOT_REENTRY_REQUIRED[\s\S]*?return 75/);
  for(const forbidden of ['dpkg --force','apt-get -f install','rm -rf /var/lib/dpkg'])
    assert.ok(!guest.includes(forbidden),forbidden);
});
test('W17 restart is bounded, Windows-only, same staged root, and never bypasses 0 gate',()=>{
  for(const witness of [
    'module == OfflineModuleId.WINDOWS_NATIVE && r2Authorized',
    'command.contains("/workspace/runtime/install-windows-native-toolchain.sh")',
    'result.exitCode == 75',
    'result.output.contains("APPFORGE_WINDOWS_NATIVE_PROROOT_REENTRY_REQUIRED")',
    'windowsProrootReentry < 2',
    'control.checkpoint()',
    'execute(shell,rootfs,workspace,"$suffix-reentry-${windowsProrootReentry + 1}"',
    'check(', 'result.exitCode ==', '0'
  ]) assert.ok(host.includes(witness),witness);
  assert.ok(!host.includes('exitCode == 75 || result.exitCode == 0'));
});
