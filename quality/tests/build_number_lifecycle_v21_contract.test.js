import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
const read = p => fs.readFileSync(new URL("../../" + p, import.meta.url), "utf8");
const numbers = read("android-app/app/src/main/java/com/appforge/studio/AppForgeBuildNumbers.kt");
const engine = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt");

test("Build No starts AF-0000001000 and uses ten persistent digits", () => {
  assert.match(numbers, /FIRST_BUILD_NO = 1_000L/);
  assert.match(numbers, /padStart\(10, '0'\)/);
  assert.match(numbers, /appforge_build_numbers_v1/);
});

test("Build No is synchronously persisted before build starts", () => {
  assert.match(numbers, /synchronized\(lock\)/);
  assert.match(numbers, /putLong\(KEY_LAST, next\)\.commit\(\)/);
  assert.match(engine, /AppForgeBuildNumberStore\.next\(context\.applicationContext\)/);
  assert.doesNotMatch(engine, /System\.currentTimeMillis\(\)\s*\/\s*1000L/);
  assert.doesNotMatch(engine, /AtomicLong/);
});
