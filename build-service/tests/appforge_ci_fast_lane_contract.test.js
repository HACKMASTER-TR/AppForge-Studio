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
  "heavy workflows ignore tests/control-plane changes",
  async () => {
    const worker =
      await read(
        ".github/workflows/worker-image.yml"
      );

    const sourceWorker =
      await read(
        ".github/workflows/source-worker-image.yml"
      );

    const conversion =
      await read(
        ".github/workflows/conversion-smoke.yml"
      );

    const android =
      await read(
        ".github/workflows/android-debug.yml"
      );

    for (const text of [
      worker,
      sourceWorker,
      conversion
    ]) {
      assert.match(
        text,
        /!build-service\/tests\/\*\*/
      );
    }

    for (const text of [
      worker,
      sourceWorker,
      conversion,
      android
    ]) {
      assert.doesNotMatch(
        text,
        /\.github\/scripts\/railway_production\.py/
      );

      assert.doesNotMatch(
        text,
        /\.github\/workflows\/production-automation\.yml/
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
      /event_filter="workflow_run"/
    );

    assert.match(
      source,
      /display_title=title/
    );
  }
);
