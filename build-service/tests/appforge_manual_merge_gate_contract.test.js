import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const source = fs.readFileSync(
  new URL("../../scripts/appforge", import.meta.url),
  "utf8"
);

function functionBlock(name, nextName) {
  const start = source.indexOf(`def ${name}(`);
  assert.notEqual(start, -1, `${name} function missing`);

  const end = source.indexOf(`\ndef ${nextName}(`, start);
  assert.notEqual(end, -1, `${nextName} boundary missing`);

  return source.slice(start, end);
}

test("submit pipeline stops after required PR CI and never auto-merges", () => {
  const submitContinuation = functionBlock(
    "continue_after_push",
    "merge"
  );

  assert.match(
    submitContinuation,
    /READY_FOR_MERGE/
  );

  assert.match(
    submitContinuation,
    /AUTO MERGE: DISABLED/
  );

  assert.doesNotMatch(
    submitContinuation,
    /\bmerge_pr\s*\(/
  );
});

test("manual merge is a separate explicit admin command", () => {
  assert.match(
    source,
    /"submit", "resume"/
  );

  assert.match(
    source,
    /"merge"/
  );

  assert.match(
    source,
    /elif args\.cmd == "merge":\s+merge\(\)/
  );

  const adminStart = source.indexOf("admin_commands = {");
  const adminEnd = source.indexOf("}", adminStart);
  const adminBlock = source.slice(adminStart, adminEnd + 1);

  assert.match(
    adminBlock,
    /"merge"/
  );
});

test("manual merge revalidates CI before merge", () => {
  const manualMerge = functionBlock(
    "merge",
    "recover"
  );

  assert.match(
    manualMerge,
    /local_gate\(\)/
  );

  assert.match(
    manualMerge,
    /ci_watch\(\)/
  );

  assert.match(
    manualMerge,
    /merge_pr\(num\)/
  );

  assert.ok(
    manualMerge.indexOf("ci_watch()") <
      manualMerge.indexOf("merge_pr(num)")
  );
});

test("manual merge follows origin main without switching worktrees", () => {
  const manualMerge = functionBlock(
    "merge",
    "recover"
  );

  assert.match(
    manualMerge,
    /"origin\/main"/
  );

  assert.doesNotMatch(
    manualMerge,
    /"switch",\s*"main"/
  );

  assert.match(
    manualMerge,
    /wait_required_main_ci\(/
  );
});
