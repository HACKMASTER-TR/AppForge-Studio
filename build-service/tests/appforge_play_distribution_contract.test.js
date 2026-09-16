import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const workflow = await readFile(
  new URL(
    "../../.github/workflows/android-play-release.yml",
    import.meta.url
  ),
  "utf8"
);

test("official GitHub releases publish to production Play track", () => {
  assert.ok(
    workflow.includes(
      "track: ${{ github.event_name == 'release' && 'production' || vars.APPFORGE_PLAY_TRACK || 'internal' }}"
    )
  );
  assert.match(workflow, /status:\s*completed/);
  assert.match(workflow, /types:\s*\n\s*-\s*published/);
});
