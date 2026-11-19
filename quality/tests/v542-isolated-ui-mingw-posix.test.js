import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const ui=read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');
const win=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
test('existing system installation is removed only from module confirmation UI',()=>{
  assert.doesNotMatch(ui,/Onayla ve kur • mevcut sistem/);
  assert.match(ui,/Bağımsız kur ve test et/);
  assert.match(ui,/onClick=\{chosen=null;beginIsolated\(id\)\}/);
  assert.match(ui,/OfflineModuleId.FAST_HTML,OfflineModuleId.WINDOWS_PORTABLE/);
});
test('Windows repair pins both POSIX compiler drivers to signed ARM64 archives',()=>{
  assert.match(win,/restore_signed_posix_driver gcc/);
  assert.match(win,/restore_signed_posix_driver gxx/);
  assert.match(win,/de7ed842e1a166be9726952538cfe520d6e55ff07e4fae009cddbee74ca5de64/);
  assert.match(win,/8314d76080dc9979f8ebcfb2d70852804a6b765e38028990d8ff67ced712c99d/);
  assert.match(win,/sha256sum -c -/);
  assert.match(win,/APPFORGE_R2_RELEASE_AUTHORIZED/);
  assert.doesNotMatch(win,/dpkg --force|apt-get -f install|rm -rf \/var\/lib\/dpkg/);
});
