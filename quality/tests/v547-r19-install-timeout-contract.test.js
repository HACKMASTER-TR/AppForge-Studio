import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const root = new URL('../../', import.meta.url);
const read = p => fs.readFileSync(new URL(p, root), 'utf8');
const runner = read('android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt');
const manager = read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
const policy = read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedInstallTimeoutPolicy.kt');
const isolation = read('android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleInstallerV3.kt');

test('R19 extends only selected Android Core heavy steps with an absolute ceiling', () => {
  assert.match(runner, /1_000L\.\.4_500_000L/);
  assert.match(manager, /IsolatedInstallTimeoutPolicy\.forStep\(module, command\)/);
  assert.match(manager, /timeoutMs = timeoutBudgetMs/);
  assert.match(policy, /module != OfflineModuleId\.ANDROID_CORE/);
  assert.match(policy, /DEFAULT_TIMEOUT_MS = 1_800_000L/);
  assert.match(policy, /ANDROID_CORE_TIMEOUT_MS = 4_500_000L/);
  assert.match(policy, /install-toolchain\.sh webview-static/);
  assert.match(policy, /prepare-offline-pack\.sh android/);
});

test('R19 reports safe phase/step context without leaking command output', () => {
  assert.match(manager, /lastAptPhase\.set\(aptPhase\.phase\)/);
  assert.match(manager, /IsolatedInstallTimeoutPolicy\.timeoutMessage/);
  assert.match(policy, /lastAptPhase=\$phaseName/);
  assert.doesNotMatch(policy, /result\.output/);
  assert.match(isolation, /RuntimeGenerationStore\(root\)\.prepareOwner/);
  assert.match(isolation, /generation\.commit\(verify/);
  assert.match(isolation, /ISOLATED_FINAL_RECEIPT_INVALID/);
});
