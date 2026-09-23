import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

async function read(path) {
  return readFile(
    new URL(
      `../../${path}`,
      import.meta.url
    ),
    "utf8"
  );
}

test(
  "heavy remote Worker workflows stay retired",
  async () => {
    for (const relative of [
      ".github/workflows/worker-image.yml",
      ".github/workflows/source-worker-image.yml",
      ".github/workflows/worker-autoscale.yml"
    ]) {
      await assert.rejects(
        read(relative),
        {
          code: "ENOENT"
        }
      );
    }
  }
);

test(
  "Autopilot supports FAST and FULL lanes",
  async () => {
    const source =
      await read("scripts/appforge");

    assert.match(
      source,
      /def is_fast_lane_file\(path\):/
    );

    assert.match(
      source,
      /def pipeline_mode\(files\):/
    );

    assert.match(
      source,
      /PIPELINE MODE:/
    );

    assert.match(
      source,
      /FAST LANE:/
    );

    assert.match(
      source,
      /quality\/tests\//
    );
  }
);
