import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const root =
  path.resolve(
    here,
    "../.."
  );

const read =
  relative =>
    fs.readFileSync(
      path.join(
        root,
        relative
      ),
      "utf8"
    );

const main = read(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
);

const pack = read(
  "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
);

test("fresh install routes onboarding into first-run offline pack gate", () => {
  assert.match(main, /OFFLINE_PACK_FIRST_RUN/);
  assert.match(main, /first_run_gate_required_v1/);
  assert.match(main, /first_run_gate_completed_v1/);

  assert.ok(
    main.includes(
      `screen =\n                                AppScreen.OFFLINE_PACK_FIRST_RUN`
    )
  );
});

test("existing installs are not blindly forced into the new gate", () => {
  assert.match(
    main,
    /if\s*\(\s*!onboardingCompleted\s*\)/
  );

  assert.ok(
    main.includes(
      `"first_run_gate_required_v1",\n                false`
    )
  );
});

test("first-run gate cannot be bypassed with Android back", () => {
  assert.match(
    main,
    /AppScreen\.ONBOARDING \|\|\s+screen ==\s+AppScreen\.OFFLINE_PACK_FIRST_RUN/
  );
});

test("first-run screen explains consequences and requires explicit skip", () => {
  assert.match(pack, /İlk açılışta araç indirme zorunluluğu yok/);
  assert.match(pack, /Araç indirmeden devam et/);
  assert.match(pack, /onFirstRunComplete\?\.invoke\(true\)/);
  assert.match(pack, /if\(firstRun\)item/);
  assert.match(pack, /Fast HTML gömülüdür/);
});

test("first-run pack defers installation and cleanup verification to their actions", () => {
  assert.doesNotMatch(pack, /OfflineModules\.all\.associate/);
  const confirmationPlan = pack.split("LaunchedEffect(chosen)")[1].split("LaunchedEffect(confirmPrune)")[0];
  assert.doesNotMatch(confirmationPlan, /moduleReady|verifyNamespace|sha256/);
  assert.match(pack, /active.phase==ModulePhase.READY -> "Kurulum devam ediyor"/);
  assert.match(pack, /!busy && completedInstall==selected/);
  assert.match(pack, /diagnosticOperation\.value=null[\s\S]*?busy\.value=true/);
  assert.match(pack, /it\.parentOperationId==null && it\.operationId in rootIds/);
  assert.match(pack, /LaunchedEffect\(chosen\)[\s\S]*?blocked to OfflineModules\.resolve\(setOf\(id\)\)/);
  assert.match(pack, /LaunchedEffect\(confirmPrune\) \{\s*if\(!confirmPrune\)return@LaunchedEffect\s*val result=.*prunableSnapshotBytes/);
  assert.match(pack, /LaunchedEffect\(section,selected,moduleDetails,recovered,busy,usageRevision\)[\s\S]*?if\(moduleDetails\) \{[\s\S]*?lifecycleStatus/);
  assert.match(pack, /"Araç indirmeden devam et"/);
});

test("normal Settings offline-pack route remains available", () => {
  assert.match(
    main,
    /AppScreen\.OFFLINE_PACK ->\s+OfflineBuildPackScreen/
  );
});

test("offline pack screen does not expose the owner Terminal UI", () => {
  assert.doesNotMatch(pack, /TerminalWorkspaceScreen/);
  assert.doesNotMatch(pack, /AppScreen\.TERMINAL/);
});
