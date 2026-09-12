import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const cliUrl =
  new URL(
    "../../scripts/appforge",
    import.meta.url
  );

test(
  "Autopilot supports idempotent submit and resume",
  async () => {
    const source =
      await readFile(
        cliUrl,
        "utf8"
      );

    assert.match(
      source,
      /def resume\(\):/
    );

    assert.match(
      source,
      /def continue_after_push\(branch\):/
    );

    assert.match(
      source,
      /def require_remote_sync\(branch\):/
    );

    assert.match(
      source,
      /COMMIT\/PUSH: ALREADY COMPLETE/
    );

    assert.match(
      source,
      /RESUME DETECTED/
    );

    assert.match(
      source,
      /PR REQUIRED CI: SUCCESS/
    );

    assert.match(
      source,
      /APPFORGE PIPELINE COMPLETE/
    );

    assert.match(
      source,
      /required_main_workflows/
    );

    assert.match(
      source,
      /"resume"/
    );
  }
);
