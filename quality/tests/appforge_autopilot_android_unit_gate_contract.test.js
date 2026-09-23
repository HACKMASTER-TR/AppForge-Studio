import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const appforge = fs.readFileSync(
  new URL("../../scripts/appforge", import.meta.url),
  "utf8"
);

const androidDebug = fs.readFileSync(
  new URL("../../.github/workflows/android-debug.yml", import.meta.url),
  "utf8"
);

test("AppForge exposes Android/Kotlin unit-test inventory and requires CI execution", () => {
  assert.match(appforge, /def android_unit_test_inventory\(\):/);
  assert.match(appforge, /def android_unit_test_gate\(\):/);
  assert.match(appforge, /Registered:/);
  assert.match(appforge, /Agent tests:/);
  assert.match(appforge, /:app:testDebugUnitTest/);
  assert.match(androidDebug, /:app:testDebugUnitTest/);

  const gateReferences = appforge.match(/android_unit_test_gate\(\)/g) || [];
  assert.ok(gateReferences.length >= 3);
});
