import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../', import.meta.url);
const read = name => fs.readFileSync(new URL(name,root),'utf8');
const sdk=read('android-app/app/src/main/assets/device-build/install-toolchain.sh');
const win=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
const cache=read('android-app/app/src/main/java/com/appforge/studio/build/V9SelectiveR2ModuleCache.kt');
const isolated=read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleInstallerV3.kt');
test('Offline SDK license is only created after explicit terms consent and pinned Google archive verification',()=>{
  assert.match(sdk,/if \[ "\$SDK_LICENSE_ACCEPTED" != "1" \]/);
  assert.match(sdk,/\[ "\$SDK_LICENSE_ACCEPTED" = "1" \]/);
  assert.match(sdk,/APPFORGE_ANDROID_SDK_LICENSE_EXPLICIT_CONSENT_REQUIRED/);
  assert.match(sdk,/cmd_archive="\$ROOT\/cache\/commandlinetools-linux-\$\{CMDLINE_TOOLS_VERSION\}_latest\.zip"/);
  assert.match(sdk,/\$CMDLINE_TOOLS_SHA256" "\$cmd_archive" \| sha256sum -c -/);
  assert.match(sdk,/known_license='24333f8a63b6825ea9c5514f83c2829b004d1fee'/);
  assert.match(sdk,/APPFORGE_ANDROID_SDK_OFFLINE_LICENSE_PINNED=PASS/);
  assert.match(sdk,/APPFORGE_ANDROID_SDK_LICENSE_PATH_UNTRUSTED/);
  assert.match(sdk,/APPFORGE_ANDROID_SDK_LICENSE_FILE_MISSING/);
});
test('MinGW recovery accepts only exact dpkg-owned version-pinned existing driver paths',()=>{
  assert.match(win,/owner=\$\(dpkg-query -S "\$target"/);
  assert.match(win,/\[ "\$owner" = "\$pkg: \$target" \]/);
  assert.match(win,/installed_version=\$\(dpkg-query -W -f='\$\{Version\}'/);
  assert.match(win,/\[ "\$installed_version" = '13\.2\.0-6ubuntu1\+26\.1' \]/);
  assert.match(win,/APPFORGE_WINDOWS_\$\{kind\}_EXISTING_PATH_UNTRUSTED/);
  assert.match(win,/APPFORGE_WINDOWS_\$\{kind\}_EXISTING_VERSION_UNTRUSTED/);
  assert.match(win,/printf '%s  %s\\n' "\$hash" "\$archive" \| sha256sum -c -/);
  assert.match(win,/dpkg-deb -x "\$archive" "\$stage"/);
  assert.match(win,/mv -T "\$tmp" "\$target"/);
  assert.doesNotMatch(win,/dpkg --force/);
});
test('Existing signed-cache reuse and atomic isolated installation unchanged',()=>{
  assert.match(cache,/VerifiedArtifactCache\.reuse\(target,obj.sha256,obj.bytes,V9R2ObjectTransfer.MAX_BYTES\)/);
  assert.match(isolated,/generation\.commit\(verify = \{ candidate ->/);
  assert.match(isolated,/hasProtectedCandidate\(app,target\)/);
});
