import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../scripts/appforge",
    import.meta.url
  ),
  "utf8"
);

test("appforge exposes preflight command", () => {
  assert.match(
    source,
    /"preflight"/
  );

  assert.match(
    source,
    /elif args\.cmd == "preflight":/
  );

  assert.match(
    source,
    /delivery_preflight\(\)/
  );
});

test("preflight delegates to isolated read only implementation", () => {
  assert.match(
    source,
    /appforge-delivery-preflight/
  );

  assert.match(
    source,
    /def delivery_preflight\(\):/
  );

  assert.match(
    source,
    /sys\.executable/
  );
});

test("preflight remains separate from autopilot execution", () => {
  const start = source.indexOf(
    "def delivery_preflight():"
  );

  const end = source.indexOf(
    "\ndef status():",
    start
  );

  assert.ok(start >= 0);
  assert.ok(end > start);

  const block = source.slice(
    start,
    end
  );

  assert.doesNotMatch(
    block,
    /autopilot\(/
  );

  assert.doesNotMatch(
    block,
    /merge_pr\(/
  );

  assert.doesNotMatch(
    block,
    /ensure_versioned_release\(/
  );

  assert.doesNotMatch(
    block,
    /wait_play_publish\(/
  );
});
