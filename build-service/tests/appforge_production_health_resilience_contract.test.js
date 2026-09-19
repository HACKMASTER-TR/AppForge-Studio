import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const productionUrl =
  new URL(
    "../../.github/scripts/railway_production.py",
    import.meta.url
  );

const workflowUrl =
  new URL(
    "../../.github/workflows/production-automation.yml",
    import.meta.url
  );

test(
  "device-only cutover keeps Railway production deployment retired",
  async () => {
    for (const relative of [
      "../../.github/workflows/production-automation.yml",
      "../../.github/scripts/railway_production.py"
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
