import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const cliUrl =
  new URL(
    "../../scripts/appforge",
    import.meta.url
  );

test(
  "Autopilot resolves workflows from stable filenames",
  async () => {
    const source =
      await readFile(
        cliUrl,
        "utf8"
      );

    for (const file of [
      "android-debug.yml",
      "worker-image.yml",
      "source-worker-image.yml",
      "conversion-smoke.yml",
      "production-automation.yml"
    ]) {
      assert.ok(
        source.includes(`"${file}"`),
        `${file} missing`
      );
    }

    assert.match(
      source,
      /def workflow_display_name\(workflow_file\):/
    );

    assert.match(
      source,
      /def wait_required_main_ci\(/
    );

    assert.match(
      source,
      /min_count=deploy_count/
    );

    assert.match(
      source,
      /APPFORGE_RECOVERY_SHA/
    );

    assert.match(
      source,
      /def recover\(\):/
    );
  }
);
