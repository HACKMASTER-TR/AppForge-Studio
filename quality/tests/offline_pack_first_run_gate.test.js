import fs from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

const main = fs.readFileSync(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  "utf8"
);

const pack = fs.readFileSync(
  "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt",
  "utf8"
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
  assert.match(pack, /Çevrimdışı paketleri atla\?/);
  assert.match(pack, /YİNE DE DEVAM ET/);
  assert.match(pack, /GERİ DÖN VE İNDİR/);
  assert.match(pack, /ŞİMDİLİK ATLA/);
  assert.match(pack, /Device \/ Offline Build/);
});

test("ready first-run pack exposes continue action", () => {
  assert.match(pack, /status\.completeTargetReady/);
  assert.match(pack, /status\.windowsNativeToolchainReady/);
  assert.match(pack, /"DEVAM ET"/);
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
