import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../', import.meta.url);
const read = p => fs.readFileSync(new URL(p, root), 'utf8');

const planner = read('android-app/app/src/main/java/com/appforge/studio/build/ModuleIsolationV1.kt');
const manager = read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
const unit = read('android-app/app/src/test/java/com/appforge/studio/build/ModuleIsolationV1Test.kt');

test('existing installer routes its explicit user selection through demand-only planner', () => {
  assert.match(manager, /val plan=ModuleIsolationV1\.legacyDependencyOrder\(selected\)/);
  assert.match(planner, /requests\(selected\)\.flatMap \{ it\.dependencyOrder \}\.distinct\(\)/);
});

test('private module namespaces never borrow installed files or mark READY', () => {
  for (const token of ['isolated-modules-v1', 'directory(base, "rootfs")', 'directory(base, "downloads")', 'directory(base, "apt")', 'directory(base, "state")', 'directory(base, "stages")', 'StandardCopyOption.ATOMIC_MOVE', 'FileOutputStream(temporary)', 'Files.isSymbolicLink']) {
    assert.ok(planner.includes(token), `missing isolation guard ${token}`);
  }
  assert.doesNotMatch(planner, /hardLink|createLink\(|getRuntimeRootfs|writeAttestation|ModulePhase\.READY|RuntimeGenerationStore\(root\)\.prepare/);
});

test('on-demand and symlink tests are present', () => {
  for (const token of ['windowsNativeDoesNotPullExpoRustOrAndroidSdk', 'expoPullsOnlyItsExplicitDependencyClosure', 'rustAndroidDoesNotInstallExpoOrWindowsNative', 'bundledFastHtmlDoesNotAllocateLinuxOrDownloadFolders', 'moduleLocationsAreDisjointAndDoNotTouchExistingUserData', 'signedArchiveCopyIsIndependentAndNeverHardlinked', 'symlinkedModuleNamespaceCannotBeEntered']) {
    assert.ok(unit.includes(token), `missing test ${token}`);
  }
});
