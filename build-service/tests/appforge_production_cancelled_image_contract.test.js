import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const workflowUrl = new URL(
  "../../.github/workflows/production-automation.yml",
  import.meta.url
);

test(
  "device-only cutover keeps remote Worker production workflows retired",
  async () => {
    for (const relative of [
      "../../.github/workflows/worker-image.yml",
      "../../.github/workflows/production-automation.yml"
    ]) {
      await assert.rejects(
        readFile(
          new URL(
            relative,
            import.meta.url
          ),
          "utf8"
        ),
        {
          code: "ENOENT"
        }
      );
    }
  }
);
