import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("Stage 11H gates owner shortcuts by AppForge account identity", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /OWNER_ACCOUNT_EMAIL_SHA256/
  );

  assert.match(
    source,
    /ownerTerminalShortcutsEnabled\(\s*accountEmail: String/
  );

  assert.match(
    source,
    /normalizedIdentityDigest\(\s*accountEmail/
  );

  assert.match(
    source,
    /MessageDigest/
  );

  assert.doesNotMatch(
    source,
    /OWNER_RAILWAY_IDENTITY_SHA256/
  );

  assert.doesNotMatch(
    source,
    /28550040284a@gmail\.com/
  );
});

test("Stage 11H adds APK and Dashboard terminal shortcuts", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.ok(
    source.includes('PtyKey(\n                            "APK"')
  );

  assert.ok(
    source.includes('PtyKey(\n                            "DASH"')
  );

  assert.ok(
    source.includes('"appforge-apk\\r"')
  );

  assert.ok(
    source.includes('"cd /root/AppForge-Studio && "')
  );

  assert.ok(
    source.includes('"./scripts/appforge dashboard\\r"')
  );

  assert.doesNotMatch(
    source,
    /\.\/dashboard\.sh/
  );
});

test("Stage 11H preserves verified terminal shortcuts", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  for (const key of [
    '"KOPYA"',
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
      `existing shortcut disappeared: ${key}`
    );
  }

  assert.doesNotMatch(
    source,
    /\.imePadding\(\)/
  );

  assert.match(
    source,
    /MAX_RENDERED_PTY_HISTORY_LINES\s*=\s*\n\s*5_000/
  );

  assert.match(
    source,
    /COPY_MODE_MAX_LINES\s*=\s*\n\s*500/
  );
});
