import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const root = new URL('../../', import.meta.url);
const read = (name) => fs.readFileSync(new URL(name, root), 'utf8');
const screen = read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');
const manager = read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
const budget = read('android-app/app/src/main/java/com/appforge/studio/build/RuntimeStorageBudget.kt');

test('only module center is visible; old navigation and disk card are gone', () => {
  assert.match(screen, /val section="modules"/);
  assert.doesNotMatch(screen, /ModuleManagerNavigation\(/);
  assert.doesNotMatch(screen, /RuntimeStorageDashboard\(/);
  assert.doesNotMatch(screen, /if\(section=="network"\)item/);
  assert.doesNotMatch(screen, /if\(section=="history"\)item/);
  assert.doesNotMatch(screen, /if\(section=="diagnostics"\)item/);
  assert.match(screen, /Kurulumları yeniden kontrol et/);
});

test('blocking quarantine has a contextual, verified recovery path', () => {
  assert.match(screen, /STORAGE_BUDGET/);
  assert.match(screen, /Başarısız kurulumu güvenli kurtar/);
  assert.match(screen, /failedBackupConfirm=true/);
  assert.match(screen, /OfflineBuildPackManager\.backupAndCleanQuarantine/);
  assert.match(manager, /fun firstQuarantineForBackup/);
  assert.match(manager, /backupAndCleanQuarantine/);
  assert.match(budget, /fun requireAdmission/);
});

test('module install and network consent protections remain intact', () => {
  assert.match(screen, /Onayla ve kur/);
  assert.match(screen, /DownloadNetwork\.allowMobileOnce/);
  assert.match(screen, /check\(OfflineBuildPackManager\.moduleReady/);
  assert.match(screen, /hasPendingLifecycle/);
  assert.match(manager, /fun installedReadiness/);
});
