import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const secureUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/security/SecureAccountStore.kt",
  import.meta.url
);

const mainUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  import.meta.url
);

const ptyUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test(
  "Stage 11J namespaces secure data by hashed AppForge account",
  async () => {
    const source =
      await readFile(secureUrl, "utf8");

    assert.match(
      source,
      /ACCOUNT_SCOPE_PREFIX/
    );

    assert.match(
      source,
      /GUEST_ACCOUNT_SCOPE/
    );

    assert.match(
      source,
      /MessageDigest/
    );

    assert.match(
      source,
      /SHA-256/
    );

    assert.match(
      source,
      /private fun scopedKey\s*\(/
    );
  }
);

test(
  "Stage 11J scopes API GitHub Railway and pending OAuth storage",
  async () => {
    const source =
      await readFile(secureUrl, "utf8");

    assert.match(
      source,
      /scopedKey\s*\(\s*scope,\s*API_DATA/
    );

    assert.match(
      source,
      /scopedKey\s*\(\s*scope,\s*externalDataKey/
    );

    assert.match(
      source,
      /scopedKey\s*\(\s*scope,\s*externalIvKey/
    );

    assert.match(
      source,
      /scopedKey\s*\(\s*scope,\s*pendingDataKey/
    );

    assert.match(
      source,
      /scopedKey\s*\(\s*scope,\s*pendingIvKey/
    );
  }
);

test(
  "Stage 11J migrates legacy credentials into current account vault",
  async () => {
    const source =
      await readFile(secureUrl, "utf8");

    assert.match(
      source,
      /migrateLegacyAccountCredentials/
    );

    assert.match(
      source,
      /migrateLegacyEncryptedPair/
    );

    assert.match(
      source,
      /externalDataKey\s*\(\s*"github"/
    );

    assert.match(
      source,
      /externalDataKey\s*\(\s*"railway"/
    );

    assert.match(
      source,
      /pendingDataKey\s*\(\s*"railway"/
    );

    assert.match(
      source,
      /remove\s*\(\s*legacyDataKey/
    );

    assert.match(
      source,
      /remove\s*\(\s*legacyIvKey/
    );
  }
);

test(
  "Stage 11J logout preserves account vault and account switch closes PTY credentials",
  async () => {
    const main =
      await readFile(mainUrl, "utf8");

    const pty =
      await readFile(ptyUrl, "utf8");

    const start =
      main.indexOf("onSession = {");

    const end =
      main.indexOf(
        "onApiKeyCreated = {",
        start
      );

    assert.ok(
      start >= 0 &&
      end > start
    );

    const block =
      main.slice(
        start,
        end
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
      /saveSession[\s\S]*?loadBuildApiKey/
    );

    assert.match(
      pty,
      /fun closeAllForAccountSwitch\(\)/
    );

    assert.match(
      pty,
      /it\.session\.close\(\)/
    );

    assert.match(
      pty,
      /TerminalGitCredentialBridge[\s\S]*?clearStale/
    );
  }
);

test(
  "Stage 11J preserves terminal performance architecture",
  async () => {
    const pty =
      await readFile(ptyUrl, "utf8");

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
