import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const secureUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/security/SecureAccountStore.kt",
  import.meta.url
);

const ptyUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

const mainUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  import.meta.url
);

test(
  "Stage 11K stores terminal descriptors in the encrypted account vault",
  async () => {
    const secure =
      await readFile(
        secureUrl,
        "utf8"
      );

    const pty =
      await readFile(
        ptyUrl,
        "utf8"
      );

    assert.match(
      secure,
      /TERMINAL_SESSIONS_DATA/
    );

    assert.match(
      secure,
      /TERMINAL_SESSIONS_IV/
    );

    assert.match(
      secure,
      /fun saveTerminalSessionState\(/
    );

    assert.match(
      secure,
      /fun loadTerminalSessionState\(/
    );

    assert.match(
      secure,
      /scopedKey\(\s*scope,\s*TERMINAL_SESSIONS_DATA/
    );

    assert.match(
      pty,
      /SecureAccountStore[\s\S]*?saveTerminalSessionState/
    );

    assert.match(
      pty,
      /SecureAccountStore[\s\S]*?loadTerminalSessionState/
    );
  }
);

test(
  "Stage 11K persists the outgoing account before clearing PTY memory",
  async () => {
    const pty =
      await readFile(
        ptyUrl,
        "utf8"
      );

    const start =
      pty.indexOf(
        "fun closeAllForAccountSwitch()"
      );

    const end =
      pty.indexOf(
        "fun reloadForActiveAccount(",
        start
      );

    assert.ok(
      start >= 0 &&
      end > start
    );

    const block =
      pty.slice(
        start,
        end
      );

    const persistIndex =
      block.indexOf(
        "persistLocked()"
      );

    const clearIndex =
      block.indexOf(
        "records.clear()"
      );

    assert.ok(
      persistIndex >= 0
    );

    assert.ok(
      clearIndex >= 0
    );

    assert.ok(
      persistIndex <
        clearIndex,
      "Outgoing terminal snapshot must be persisted before records.clear()"
    );

    assert.match(
      block,
      /it\.session\.close\(\)/
    );

    assert.match(
      block,
      /TerminalGitCredentialBridge[\s\S]*?clearStale/
    );
  }
);

test(
  "Stage 11K reloads the newly active account after secure session switch",
  async () => {
    const main =
      await readFile(
        mainUrl,
        "utf8"
      );

    const pty =
      await readFile(
        ptyUrl,
        "utf8"
      );

    assert.match(
      main,
      /previousAccountKey\s*!=\s*nextAccountKey/
    );

    assert.match(
      main,
      /saveSession[\s\S]*?reloadForActiveAccount[\s\S]*?loadBuildApiKey/
    );

    assert.match(
      pty,
      /fun reloadForActiveAccount\(/
    );

    assert.match(
      pty,
      /reloadForActiveAccount[\s\S]*?restoreLocked\(\)/
    );

    assert.match(
      pty,
      /LaunchedEffect\(\s*environmentState\.phase,\s*workspaceRoot\.absolutePath,\s*accountEmail/
    );
  }
);

test(
  "Stage 11K migrates and deletes legacy plaintext terminal persistence",
  async () => {
    const pty =
      await readFile(
        ptyUrl,
        "utf8"
      );

    assert.match(
      pty,
      /private fun migrateLegacyTerminalState/
    );

    assert.match(
      pty,
      /getSharedPreferences\(\s*PREFS_NAME/
    );

    assert.match(
      pty,
      /saveTerminalSessionState/
    );

    assert.match(
      pty,
      /\.remove\(\s*KEY_SESSIONS/
    );
  }
);

test(
  "Stage 11K preserves terminal performance and copy architecture",
  async () => {
    const pty =
      await readFile(
        ptyUrl,
        "utf8"
      );

    assert.doesNotMatch(
      pty,
      /\.imePadding\(\)/
    );

    assert.match(
      pty,
      /COPY_MODE_MAX_LINES\s*=\s*\n\s*500/
    );

    assert.match(
      pty,
      /MAX_RENDERED_PTY_HISTORY_LINES\s*=\s*\n\s*5_000/
    );

    for (const key of [
      '"KOPYA"',
      '"APK"',
      '"DASH"',
      '"ESC"',
      '"TAB"',
      '"CTRL+C"',
      '"CTRL+A"',
      '"CTRL+E"',
      '"CTRL+R"',
      '"CTRL+U"',
      '"CTRL+W"'
    ]) {
      assert.ok(
        pty.includes(key),
        `terminal shortcut disappeared: ${key}`
      );
    }
  }
);
