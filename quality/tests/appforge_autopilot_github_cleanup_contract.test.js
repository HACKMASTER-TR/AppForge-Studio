import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const appforgeUrl = new URL(
  "../../scripts/appforge",
  import.meta.url
);

const cleanupUrl = new URL(
  "../../scripts/github-cleanup",
  import.meta.url
);

test("cleanup stage is after main CI and before shipping blocker", async () => {
  const source = await readFile(appforgeUrl, "utf8");

  assert.match(
    source,
    /POST-MERGE MAIN CI: SUCCESS[\s\S]{0,500}run_github_cleanup[\s\S]{0,700}require_no_runtime_blockers/
  );

  assert.match(
    source,
    /GITHUB_CLEANUP/
  );
});

test("cleanup protects evidence and emits report", async () => {
  const source = await readFile(cleanupUrl, "utf8");

  // Protect the latest successful main run by behavior, not variable name.
  assert.match(
    source,
    /head_branch['"]?\)\s*==\s*['"]main['"][\s\S]{0,160}conclusion['"]?\)\s*==\s*['"]success['"]/
  );

  // Protect the latest failing run by behavior, not variable name.
  assert.match(
    source,
    /conclusion['"]?\)\s*not in\s*\{[\s\S]{0,120}success[\s\S]{0,120}neutral[\s\S]{0,120}skipped/
  );

  // Protect evidence for the currently validated main SHA.
  assert.match(
    source,
    /head_sha['"]?\)\s*==\s*sha/
  );

  // Never remove the canonical latest APK.
  assert.match(
    source,
    /AppForgeStudio-latest\.apk/
  );

  // Open PR dependency check must exist before branch deletion.
  assert.match(
    source,
    /state=open/
  );

  // Remote tracking refs are pruned.
  assert.match(
    source,
    /fetch['"],['"]--prune/
  );

  for (const label of [
    "GITHUB CLEANUP:",
    "Deleted merged branches:",
    "Pruned remote refs:",
    "Removed obsolete files:",
    "Removed stale artifacts:",
    "Removed obsolete release assets:",
    "Protected items:",
    "Errors:",
  ]) {
    assert.ok(
      source.includes(label),
      `cleanup report label missing: ${label}`
    );
  }
});
