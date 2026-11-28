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
  assert.doesNotMatch(screen, /Kurulumları yeniden kontrol et/);
  assert.doesNotMatch(screen, /Scaffold\(topBar=/);
  assert.match(screen, /R66ModuleRow\(module=module/);
  const row = read('android-app/app/src/main/java/com/appforge/studio/R66ModuleRow.kt');
  assert.match(row,/animateFloatAsState/);
  assert.match(row,/animateColorAsState/);
  assert.match(row,/if\(installed\)return State\(1f/);
  assert.match(row,/10,14 GB/);
  assert.match(row,/9,42 GB/);
});

test('one-tap retry keeps guarded private recovery but no manual recovery UI', () => {
  assert.doesNotMatch(screen, /Başarısız kurulumu güvenli kurtar/);
  assert.doesNotMatch(screen, /failedBackupConfirm=true/);
  assert.match(screen, /geçici ortam temizlenerek/);
  assert.match(manager, /fun firstQuarantineForBackup/);
  assert.match(manager, /backupAndCleanQuarantine/);
  assert.match(budget, /fun requireAdmission/);
  assert.match(budget, /val abandoned=children.filter/);
});

test('module install and network consent protections remain intact', () => {
  assert.match(screen, /Bağımsız kur ve test et/);
  assert.doesNotMatch(screen, /Onayla ve kur • mevcut sistem/);
  assert.match(screen, /DownloadNetwork\.allowMobileOnce/);
  assert.match(screen, /check\(OfflineBuildPackManager\.moduleReady/);
  assert.match(screen, /hasPendingLifecycle/);
  assert.match(manager, /fun installedReadiness/);
});
