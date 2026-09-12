import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repoRoot =
  path.resolve(
    here,
    "..",
    ".."
  );

test(
  "Autopilot reports individual and total GitHub CI durations",
  async () => {
    const source =
      await fs.readFile(
        path.join(
          repoRoot,
          "scripts",
          "appforge"
        ),
        "utf8"
      );

    for (const marker of [
      "startedAt,updatedAt",
      "workflow_run_seconds",
      "ci_timing_summary",
      "=== PIPELINE TIMING ===",
      "FULL PIPELINE TOTAL:",
      'results["_timing"] = timing'
    ]) {
      assert.ok(
        source.includes(marker),
        marker
      );
    }
  }
);

test(
  "Source Worker workflow validates its own CI changes",
  async () => {
    const workflow =
      await fs.readFile(
        path.join(
          repoRoot,
          ".github",
          "workflows",
          "source-worker-image.yml"
        ),
        "utf8"
      );

    assert.ok(
      workflow.includes(
        '- ".github/workflows/source-worker-image.yml"'
      )
    );
  }
);
