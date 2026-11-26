import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const root = new URL('../../', import.meta.url);
const read = path => fs.readFileSync(new URL(path, root), 'utf8');
const impl = read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleRuntimeV2.kt');
const oldManager = read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
const rootInstaller = read('android-app/app/src/main/java/com/appforge/studio/terminal/VerifiedLinuxRootfsInstaller.kt');
const tests = read('android-app/app/src/test/java/com/appforge/studio/build/IsolatedModuleRuntimeV2Test.kt');

test('each requested target has its own pinned Ubuntu rootfs and package-manager database', () => {
  assert.match(impl, /ModuleIsolationV1\.prepare\(privateData, target\)/);
  assert.match(impl, /LinuxRuntimeLayout\(namespace\.rootfs, LinuxDistribution\.UBUNTU, arch\)/);
  for (const part of ['var/lib/dpkg/status','var/lib/apt/lists','var/lib/dpkg/info','requireSeparated','baselineVerified']) {
    assert.ok(impl.includes(part), part);
  }
  assert.match(impl, /LinuxRuntimeManifestRegistry\.find\(LinuxDistribution\.UBUNTU, architecture\)/);
  assert.match(impl, /VerifiedLinuxRootfsInstaller\(app\)\.install\(manifest, isolated, onProgress\)/);
  assert.match(rootInstaller, /layout\.runtimeDirectory\.absolutePath/);
});

test('candidate cannot silently modify or replace current verified V540 runtime', () => {
  assert.match(impl, /check\(com\.appforge\.studio\.BuildConfig\.DEBUG\)/);
  assert.match(impl, /check\(userApproved\)/);
  assert.match(impl, /Existing isolated stage is protected/);
  assert.match(impl, /Existing isolated rollback is protected/);
  assert.match(impl, /Refusing to modify legacy runtime/);
  assert.match(impl, /!isolated\.rootfsDirectory\.exists\(\)/);
  assert.match(impl, /AptRuntimeBaseline\.captureFresh\(root, manifest\)/);
  assert.match(impl, /AptSnapshotMigration\.captureFreshRoot\(root,/);
  assert.doesNotMatch(impl, /\.deleteRecursively\(|\.removeRecursively\(|dpkg --force|apt-get install|ModulePhase\.READY/);
  assert.match(oldManager, /ModuleIsolationV1\.legacyDependencyOrder\(selected\)/);
  assert.doesNotMatch(oldManager, /IsolatedModuleRuntimeV2\.stageFreshBaseline/);
});

test('new isolation tests prevent cross-module path aliasing and premature ready', () => {
  for (const name of ['windowsNativeHasOwnUbuntuDpkgAndNeverAddsExpoOrAndroid',
    'eachModuleHasDistinctRuntimeAptDpkgAndDownloads', 'existingUserProjectsAndLegacyRootfsAreNotReadOrModified',
    'cannotDeclareBaselineVerifiedWithoutSignedRootfsDpkgAndSeal',
    'malformedOrSymlinkedModuleRuntimePathIsRejected',
    'portableAndBundledModulesCannotAllocateLinuxRootfs']) {
    assert.ok(tests.includes(name), name);
  }
});
