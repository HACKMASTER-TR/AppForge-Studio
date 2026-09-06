import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const clientUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/ExternalConnectionsClient.kt",
  import.meta.url
);

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/ConnectionsPanel.kt",
  import.meta.url
);

test(
  "Stage 11M Railway workspace query has balanced GraphQL braces",
  async () => {
    const source =
      await readFile(clientUrl, "utf8");

    const queryStart =
      source.indexOf(
        '"query AppForgeWorkspaceProjects { "'
      );

    assert.ok(
      queryStart >= 0,
      "Workspace query not found"
    );

    const executeStart =
      source.lastIndexOf(
        "executeGraph(",
        queryStart
      );

    const blockEnd =
      source.indexOf(
        "}.onSuccess {",
        queryStart
      );

    assert.ok(
      executeStart >= 0 &&
      blockEnd > queryStart,
      "Workspace executeGraph block not found"
    );

    const block =
      source.slice(
        executeStart,
        blockEnd
      );

    const strings =
      [...block.matchAll(
        /"((?:\\.|[^"\\])*)"/g
      )]
        .map(
          match => match[1]
        )
        .join("");

    const openBraces =
      (strings.match(/\{/g) || [])
        .length;

    const closeBraces =
      (strings.match(/\}/g) || [])
        .length;

    assert.equal(
      openBraces,
      closeBraces,
      `GraphQL brace mismatch: ${openBraces} open / ${closeBraces} close`
    );

    assert.match(
      strings,
      /workspace\(workspaceId:/
    );

    assert.match(
      strings,
      /projects\(first: 100\)/
    );
  }
);

test(
  "Stage 11M surfaces Railway GraphQL error details",
  async () => {
    const source =
      await readFile(clientUrl, "utf8");

    assert.match(
      source,
      /response\.code !in/
    );

    assert.match(
      source,
      /graphMessage/
    );

    assert.match(
      source,
      /optJSONArray\(\s*"errors"/
    );

    assert.match(
      source,
      /MAX_ERROR_DETAIL_LENGTH/
    );
  }
);

test(
  "Stage 11M removes duplicate Railway account label parts",
  async () => {
    const panel =
      await readFile(panelUrl, "utf8");

    assert.match(
      panel,
      /\.distinctBy\s*\{/
    );

    assert.match(
      panel,
      /it\.lowercase\(\)/
    );
  }
);

test(
  "Stage 11M keeps Railway access read only",
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

    assert.doesNotMatch(
      source,
      /mutation\s+AppForgeWorkspaceProjects/
    );
  }
);
