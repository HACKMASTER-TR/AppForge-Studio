import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const mainUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  import.meta.url
);

const ptyUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

const connectionsUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/ConnectionsPanel.kt",
  import.meta.url
);

test(
  "Stage 11I isolates live account state without deleting saved account vaults",
  async () => {
    const source =
      await readFile(mainUrl, "utf8");

    const start =
      source.indexOf(
        "onSession = {"
      );

    const end =
      source.indexOf(
        "onApiKeyCreated = {",
        start
      );

    assert.ok(
      start >= 0 &&
      end > start
    );

    const block =
      source.slice(
        start,
        end
      );

    assert.match(
      block,
      /val accountChanged/
    );

    assert.match(
      block,
      /closeAllForAccountSwitch/
    );

    assert.match(
      block,
      /clearSession/
    );

    assert.doesNotMatch(
      block,
      /\.clearAll\s*\(/
    );

    assert.match(
      block,
      /\.saveSession\s*\(\s*context,\s*nextSession/
    );

    assert.match(
      block,
      /\.loadBuildApiKey\s*\(\s*context/
    );
  }
);

test(
  "Stage 11I closes persistent PTY credentials on account switch",
  async () => {
    const source =
      await readFile(ptyUrl, "utf8");

    assert.match(
      source,
      /fun closeAllForAccountSwitch\(\)/
    );

    assert.match(
      source,
      /records\.clear\(\)/
    );

    assert.match(
      source,
      /it\.session\.close\(\)/
    );

    assert.match(
      source,
      /TerminalGitCredentialBridge[\s\S]*?\.clearStale/
    );
  }
);

test(
  "Stage 11I Railway account switch still removes only the current Railway connection",
  async () => {
    const source =
      await readFile(connectionsUrl, "utf8");

    assert.match(
      source,
      /fun switchRailwayAccount\(\)/
    );

    assert.match(
      source,
      /clearExternalConnection/
    );

    assert.match(
      source,
      /clearPendingExternalAuthorization/
    );

    assert.match(
      source,
      /Railway Hesabını Değiştir/
    );
  }
);

test(
  "Stage 11I preserves terminal performance architecture",
  async () => {
    const source =
      await readFile(ptyUrl, "utf8");

    assert.doesNotMatch(
      source,
      /\.imePadding\(\)/
    );

    assert.match(
      source,
      /COPY_MODE_MAX_LINES\s*=\s*\n\s*500/
    );

    assert.match(
      source,
      /MAX_RENDERED_PTY_HISTORY_LINES\s*=\s*\n\s*5_000/
    );
  }
);
