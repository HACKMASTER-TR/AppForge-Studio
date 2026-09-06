import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const clientUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/ExternalConnectionsClient.kt",
  import.meta.url
);

test(
  "Stage 11L discovers Railway personal and workspace projects",
  async () => {
    const source =
      await readFile(clientUrl, "utf8");

    assert.match(
      source,
      /AppForgePersonalProjects/
    );

    assert.match(
      source,
      /AppForgeWorkspaces/
    );

    assert.match(
      source,
      /AppForgeWorkspaceProjects/
    );

    assert.match(
      source,
      /workspace\(workspaceId:/
    );

    assert.match(
      source,
      /services \{ edges/
    );

    assert.match(
      source,
      /environments \{ edges/
    );
  }
);

test(
  "Stage 11L merges Railway project sources without duplicates",
  async () => {
    const source =
      await readFile(clientUrl, "utf8");

    assert.match(
      source,
      /projectsById/
    );

    assert.match(
      source,
      /externalWorkspaces/
    );

    assert.match(
      source,
      /MAX_RAILWAY_DISCOVERY_WORKSPACES/
    );

    assert.match(
      source,
      /MAX_RAILWAY_DISCOVERY_PROJECTS/
    );
  }
);

test(
  "Stage 11L exposes discovery failure instead of false zero result",
  async () => {
    const source =
      await readFile(clientUrl, "utf8");

    assert.match(
      source,
      /discoveryErrors/
    );

    assert.match(
      source,
      /projectsById\.isEmpty\(\)/
    );

    assert.match(
      source,
      /discoveryErrors\.isNotEmpty\(\)/
    );
  }
);

test(
  "Stage 11L avoids duplicate Railway identity text",
  async () => {
    const source =
      await readFile(clientUrl, "utf8");

    assert.match(
      source,
      /val label =/
    );

    assert.match(
      source,
      /it != label/
    );
  }
);
