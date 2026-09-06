import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const mainUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  import.meta.url
);

const workspaceUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt",
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
  "Stage 11I clears account-scoped credentials when AppForge account changes",
  async () => {
    const source =
      await readFile(mainUrl, "utf8");

    assert.match(
      source,
      /val previousAccountKey/
    );

    assert.match(
      source,
      /val nextAccountKey/
    );

    assert.match(
      source,
      /val accountChanged/
    );

    assert.match(
      source,
      /nextSession == null\s*\|\|\s*accountChanged/
    );

    assert.match(
      source,
      /SecureAccountStore\s*[\s\S]*?\.clearAll\s*\(/
    );

    assert.match(
      source,
      /apiKey\s*=\s*""/
    );

    assert.match(
      source,
      /buildApiKey\s*=\s*""/
    );

    assert.match(
      source,
      /proStatus\s*=\s*null/
    );

    assert.match(
      source,
      /projectQuota\s*=\s*null/
    );

    assert.match(
      source,
      /session\s*=\s*nextSession/
    );

    assert.match(
      source,
      /SecureAccountStore\s*[\s\S]*?\.saveSession\s*\(\s*context,\s*nextSession/
    );
  }
);

test(
  "Stage 11I passes live AppForge account identity to Terminal",
  async () => {
    const main =
      await readFile(mainUrl, "utf8");

    const workspace =
      await readFile(workspaceUrl, "utf8");

    const pty =
      await readFile(ptyUrl, "utf8");

    assert.match(
      main,
      /TerminalWorkspaceScreen\([\s\S]*?accountEmail\s*=[\s\S]*?session[\s\S]*?\.email/
    );

    assert.match(
      workspace,
      /fun TerminalWorkspaceScreen\([\s\S]*?accountEmail:\s*String/
    );

    assert.match(
      workspace,
      /LocalPtyTerminalPanel\([\s\S]*?accountEmail\s*=\s*accountEmail/
    );

    assert.match(
      pty,
      /internal fun LocalPtyTerminalPanel\([\s\S]*?accountEmail:\s*String/
    );

    assert.match(
      pty,
      /OWNER_ACCOUNT_EMAIL_SHA256/
    );

    assert.match(
      pty,
      /ownerTerminalShortcutsEnabled\(\s*accountEmail/
    );

    assert.doesNotMatch(
      pty,
      /OWNER_RAILWAY_IDENTITY_SHA256/
    );
  }
);

test(
  "Stage 11I removes old Railway credentials before account switching",
  async () => {
    const source =
      await readFile(connectionsUrl, "utf8");

    assert.match(
      source,
      /fun switchRailwayAccount\(\)/
    );

    assert.match(
      source,
      /clearExternalConnection\s*\(\s*context,\s*provider\.key/
    );

    assert.match(
      source,
      /clearPendingExternalAuthorization\s*\(\s*context,\s*provider\.key/
    );

    assert.match(
      source,
      /updateConnection\s*\(\s*provider,\s*null/
    );

    assert.match(
      source,
      /Railway Hesabını Değiştir/
    );

    const switchStart =
      source.indexOf(
        "fun switchRailwayAccount()"
      );

    const readStart =
      source.indexOf(
        "fun testRailwayReadAccess()",
        switchStart
      );

    assert.ok(
      switchStart >= 0 &&
      readStart > switchStart
    );

    const block =
      source.slice(
        switchStart,
        readStart
      );

    const clearIndex =
      block.indexOf(
        "clearExternalConnection"
      );

    const authorizeIndex =
      block.indexOf(
        "beginRailwayFlow"
      );

    assert.ok(clearIndex >= 0);

    if (authorizeIndex >= 0) {
      assert.ok(
        clearIndex < authorizeIndex
      );
    }
  }
);

test(
  "Stage 11I preserves terminal copy IME and productivity architecture",
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

    for (const key of [
      '"KOPYA"',
      '"APK"',
      '"DASH"',
      '"ESC"',
      '"TAB"',
      '"CTRL+C"',
      '"CTRL+L"',
      '"CTRL+A"',
      '"CTRL+E"',
      '"CTRL+R"',
      '"CTRL+U"',
      '"CTRL+W"',
      '"⌫"',
      '"↵"'
    ]) {
      assert.ok(
        source.includes(key),
        `terminal shortcut disappeared: ${key}`
      );
    }
  }
);
