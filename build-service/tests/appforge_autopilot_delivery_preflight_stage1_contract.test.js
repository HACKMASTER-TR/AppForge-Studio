import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../scripts/appforge-delivery-preflight",
    import.meta.url
  ),
  "utf8"
);

test("delivery preflight is read only", () => {
  assert.doesNotMatch(
    source,
    /git\s+(?:commit|push)/
  );

  assert.doesNotMatch(
    source,
    /pulls\/.*\/merge/
  );

  assert.doesNotMatch(
    source,
    /release\s+create/
  );

  assert.doesNotMatch(
    source,
    /workflow_dispatch/
  );
});

test("preflight reports required delivery capabilities", () => {
  for (const contract of [
    "GitHub REST",
    "GitHub gh transport",
    "PR write",
    "Auto-merge",
    "Pipeline mode",
    "Play production",
    "GitHub versioned release",
    "Play publish",
    "Android device test",
  ]) {
    assert.ok(
      source.includes(contract),
      `missing ${contract}`
    );
  }
});

test("auto merge has safe fallback", () => {
  assert.match(
    source,
    /FALLBACK: MERGE AFTER CI/
  );

  assert.match(
    source,
    /allow_auto_merge/
  );
});

test("blocked Play state skips Android distribution", () => {
  assert.match(
    source,
    /versioned_release = "SKIP"/
  );

  assert.match(
    source,
    /play_publish = "SKIP"/
  );
});

test("GitHub token is never printed by the preflight", () => {
  assert.doesNotMatch(
    source,
    /print\s*\(\s*token/
  );

  assert.doesNotMatch(
    source,
    /Authorization.*print/
  );
});
