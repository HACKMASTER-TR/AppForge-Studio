import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../', import.meta.url);
const read = path => fs.readFileSync(new URL(path,root),'utf8');
const win=read('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
const agp=read('.github/workflows/v547-agp-offline-seed.yml');
const source=read('android-app/app/src/main/assets/device-build/prepare-offline-pack.sh');
test('Windows recovery requires exact SHA, package, version, arch and ownership',()=>{
  for (const s of ['APPFORGE_WINDOWS_${kind}_ARCHIVE_SHA256_MISMATCH',
    'APPFORGE_WINDOWS_${kind}_ARCHIVE_PACKAGE_MISMATCH',
    'APPFORGE_WINDOWS_${kind}_ARCHIVE_VERSION_MISMATCH',
    'APPFORGE_WINDOWS_${kind}_ARCHIVE_ARCH_MISMATCH',
    'APPFORGE_WINDOWS_${kind}_R2_ARCHIVE_DIRECTORY_MISSING',
    'APPFORGE_WINDOWS_${kind}_SIGNED_ARCHIVE_VERIFIED=PASS',
    'APPFORGE_WINDOWS_gcc_SIGNED_VERSIONED_DRIVER_VERIFIED',
    'APPFORGE_WINDOWS_${kind}_AUTHENTIC_DRIVER_MISSING_IN_SIGNED_DEB',
    'APPFORGE_WINDOWS_${kind}_ATOMIC_REPLACE_FAILED',
    'APPFORGE_WINDOWS_${kind}_OWNED_INTERRUPTED_PATH_VERIFIED',
    'dpkg-deb -x "$archive" "$stage"',
    'cmp -s "$signed_versioned" "$installed_versioned"'])
    assert.ok(win.includes(s),s);
  assert.doesNotMatch(win,/dpkg --force|chmod -R 777|rm -rf \/var\/lib\/dpkg/);
});
test('AGP CI seed is authentic online build, isolated cache, upload only',()=>{
  assert.match(agp,/java-version: '17'/);
  assert.match(agp,/gradle-version: '9\.3\.1'/);
  assert.match(agp,/version "9\.1\.1" apply false/);
  assert.match(agp,/gradle --no-daemon --no-configuration-cache --console plain :app:assembleDebug/);
  assert.match(agp,/v547_agp_seed_manifest.py verify/);
  assert.match(agp,/CI_AGP911_REAL_ASSEMBLE=PASS/);
  assert.match(agp,/NOT_SIGNED_NOT_DEPLOYED_TO_R2/);
  assert.doesNotMatch(agp,/CLOUDFLARE_API_TOKEN|--insecure|curl -k|workflow_dispatch:/);
  assert.match(source,/APPFORGE_AGP_CACHE_ENTRY=MISSING/);
});
