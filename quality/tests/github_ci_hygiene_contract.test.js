import assert from "node:assert/strict";
import test from "node:test";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repo =
  path.resolve(
    path.dirname(
      fileURLToPath(
        import.meta.url
      )
    ),
    "../.."
  );

const workflowRoot =
  path.join(
    repo,
    ".github/workflows"
  );

const workflowFiles =
  fs
    .readdirSync(
      workflowRoot
    )
    .filter(
      name =>
        name.endsWith(".yml") ||
        name.endsWith(".yaml")
    );

const workflows =
  new Map(
    workflowFiles.map(
      name => [
        name,
        fs.readFileSync(
          path.join(
            workflowRoot,
            name
          ),
          "utf8"
        )
      ]
    )
  );

const linuxWorkflows = [
  "android-debug.yml",
  "android-play-release.yml",
  "appforge-stability-gate.yml",
  "cleanup-old-runs.yml",
  "pro-cloudflare-auth-preflight.yml",
  "pro-cloudflare-dry-run.yml",
  "pro-cloudflare-staging-deploy.yml",
  "pro-kotlin-feature.yml",
  "pro-staging-http-matrix.yml",
  "pro-staging-live-audit.yml"
];


test(
  "Linux GitHub workflows are pinned to Ubuntu 24.04",
  () => {
    for (
      const [
        name,
        source
      ] of workflows
    ) {
      assert.equal(
        source.includes(
          "runs-on: ubuntu-latest"
        ),
        false,
        `${name} still uses ubuntu-latest`
      );
    }

    for (
      const name of linuxWorkflows
    ) {
      assert.ok(
        workflows
          .get(
            name
          )
          .includes(
            "runs-on: ubuntu-24.04"
          ),
        `${name} is not pinned to Ubuntu 24.04`
      );
    }
  }
);


test(
  "Stability Gate uses setup-python v7",
  () => {
    const source =
      workflows.get(
        "appforge-stability-gate.yml"
      );

    assert.ok(
      source.includes(
        "actions/setup-python@v7"
      )
    );

    assert.equal(
      source.includes(
        "actions/setup-python@v5"
      ),
      false
    );

    assert.ok(
      source.includes(
        'python-version: "3.12"'
      )
    );
  }
);


test(
  "CI hygiene does not alter Windows runner ownership",
  () => {
    const source =
      workflows.get(
        "windows-portable-host.yml"
      );

    assert.ok(
      source.includes(
        "runs-on: windows-latest"
      )
    );
  }
);
