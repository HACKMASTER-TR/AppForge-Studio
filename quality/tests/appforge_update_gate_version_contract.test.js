import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt",
    import.meta.url
  ),
  "utf8"
);

test("Android update gate requires a numeric minimum before forcing", () => {
  assert.match(source, /effectiveStudioUpdateState/);
  assert.match(source, /currentVersionCode < minimum/);
  assert.match(source, /minSupportedVersionCode",\s*1/);
});

test("forced update is relaxed when Play cannot deliver the minimum", () => {
  assert.match(source, /availableVersionCode\(\)/);
  assert.match(source, /playCanSatisfyMinimum/);
  assert.match(
    source,
    /rolloutPolicy[\s\S]{0,300}?StudioUpdateState\.OPTIONAL/
  );
});

test("stale cached forced policy is not an offline hard lock", () => {
  assert.doesNotMatch(
    source,
    /cached\?\.state == StudioUpdateState\.FORCED\s*\|\|/
  );
});
