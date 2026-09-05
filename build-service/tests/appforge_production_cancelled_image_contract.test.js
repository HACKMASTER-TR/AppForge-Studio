import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const workflowUrl = new URL(
  "../../.github/workflows/production-automation.yml",
  import.meta.url
);

test("Production ignores superseded cancelled image workflows", async () => {
  const source =
    await readFile(workflowUrl, "utf8");

  assert.doesNotMatch(
    source,
    /workflow_run\.conclusion\s*!=\s*'success'/
  );

  assert.match(
    source,
    /workflow_run\.conclusion\s*==\s*'failure'/
  );

  assert.match(
    source,
    /workflow_run\.conclusion\s*==\s*'timed_out'/
  );

  assert.match(
    source,
    /workflow_run\.conclusion\s*==\s*'action_required'/
  );

  assert.doesNotMatch(
    source,
    /workflow_run\.conclusion\s*==\s*'cancelled'/
  );
});

test("Production still deploys only successful image workflows", async () => {
  const source =
    await readFile(workflowUrl, "utf8");

  assert.match(
    source,
    /github\.event\.workflow_run\.conclusion\s*==\s*'success'/
  );

  assert.match(
    source,
    /name:\s*Verified Worker deploy/
  );

  assert.match(
    source,
    /name:\s*Failed image build detector/
  );
});
