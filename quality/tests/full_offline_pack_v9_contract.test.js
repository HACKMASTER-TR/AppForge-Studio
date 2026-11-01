import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = path => readFileSync(new URL("../../" + path, import.meta.url), "utf8");
const ui = read("android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt");
const plan = read("android-app/app/src/main/java/com/appforge/studio/build/OfflineModulePlan.kt");
const manager = read("android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt");
const journal = read("android-app/app/src/main/java/com/appforge/studio/build/ModuleOperationJournal.kt");

test("full profile covers all declared modules and has no startup installation", () => {
  assert.match(plan, /fun fullBuildPack\(\):Set<OfflineModuleId> = all\.map \{ it\.id \}\.toSet\(\)/);
  assert.match(ui, /if\(fullPackChosen\)/);
  assert.match(ui, /onClick=\{fullPackChosen=true\}/);
  assert.doesNotMatch(ui, /LaunchedEffect\([^)]*\)\s*\{\s*ModuleInstallOperations\.startFullPack/);
});

test("single and full install use the same manager and per-module attestations", () => {
  assert.match(ui, /startSelection\(context,setOf\(selected\),consent\)/);
  assert.match(ui, /startSelection\(context,OfflineModules\.fullBuildPack\(\),consent\)/);
  assert.match(ui, /installModules\(context\.applicationContext,selected,consent,downloadControl\)/);
  assert.match(manager, /OfflineModules\.resolve\(selected\)/);
  assert.match(manager, /pending=plan\.filterNot\{moduleReady\(app,it\)\}/);
  assert.match(manager, /writeAttestation\(app,staged,id\)/);
});

test("full pack requires an explicit SDK license acceptance for NDK too", () => {
  assert.match(ui, /OfflineModuleId\.RUST_ANDROID_NDK in missing/);
  assert.match(ui, /needsSdk=missing\.any/);
  assert.match(ui, /Checkbox\(sdkConsent/);
  assert.match(ui, /Tam paketi kur/);
  assert.match(manager, /require\(sdkConsent\)/);
});

test("restore state is not READY, unrelated Linux terminal remains isolated", () => {
  assert.match(journal, /Önceki durum/);
  assert.match(manager, /DeviceBuildRuntimeV3/);
  assert.doesNotMatch(manager, /AndroidLinuxRuntimeManager/);
  assert.match(ui, /taşınabilir tek ZIP dosyası değildir/);
});
