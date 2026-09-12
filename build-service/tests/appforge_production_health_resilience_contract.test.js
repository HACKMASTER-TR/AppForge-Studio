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
  "production health survives temporary startup failures",
  async () => {
    const source =
      await readFile(
        productionUrl,
        "utf8"
      );

    assert.match(
      source,
      /def wait_json_gate\(/
    );

    assert.match(
      source,
      /consecutive=3/
    );

    assert.match(
      source,
      /warmup_seconds=30/
    );

    assert.match(
      source,
      /timeout=180/
    );

    assert.match(
      source,
      /Production health gate SUCCESS/
    );
  }
);

test(
  "production workflow identifies upstream workflow",
  async () => {
    const source =
      await readFile(
        workflowUrl,
        "utf8"
      );

    assert.match(
      source,
      /run-name:/
    );

    assert.match(
      source,
      /github\.event\.workflow_run\.name/
    );
  }
);
