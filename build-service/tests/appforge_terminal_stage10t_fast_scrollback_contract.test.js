import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

test("Stage 10T limits live Compose terminal history without removing buffer scrollback", async () => {
  const [panel, buffer] =
    await Promise.all([
      read(
        "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
      ),
      read(
        "android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt"
      )
    ]);

  assert.match(
    buffer,
    /maxScrollbackLines:\s*Int\s*=\s*1_000/
  );

  assert.match(
    buffer,
    /maxHistoryLines:\s*Int\s*=\s*Int\.MAX_VALUE/
  );

  assert.match(
    buffer,
    /\.takeLast\(\s*maxHistoryLines[\s\S]*?\.coerceAtLeast\(0\)/
  );

  assert.match(
    panel,
    /MAX_RENDERED_PTY_HISTORY_LINES\s*=\s*240/
  );

  assert.match(
    panel,
    /buffer\.snapshot\([\s\S]*?maxHistoryLines\s*=\s*MAX_RENDERED_PTY_HISTORY_LINES/
  );
});

test("Stage 10T keeps restored terminal state small", async () => {
  const panel =
    await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
    );

  assert.match(
    panel,
    /MAX_PERSISTED_SNAPSHOT_CHARS\s*=\s*12_288/
  );

  assert.match(
    panel,
    /item\.optString\("snapshot"\)[\s\S]*?\.takeLast\(\s*MAX_PERSISTED_SNAPSHOT_CHARS\s*\)/
  );
});

test("Stage 10T preserves virtualized output and verified keyboard controls", async () => {
  const panel =
    await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
    );

  assert.match(
    panel,
    /LazyColumn/
  );

  assert.doesNotMatch(
    panel,
    /SelectionContainer/
  );

  assert.match(
    panel,
    /TerminalShortcutMatteGray/
  );

  assert.doesNotMatch(
    panel,
    /\.imePadding\(\)/
  );

  for (const key of [
    '"CTRL+C"',
    '"CTRL+A"',
    '"CTRL+E"',
    '"CTRL+R"',
    '"CTRL+U"',
    '"CTRL+W"',
    '"⌫"'
  ]) {
    assert.ok(
      panel.includes(key),
      `verified key disappeared: ${key}`
    );
  }
});

test("Stage 10T repairs interrupted dpkg before apt package installation", async () => {
  const repair =
    await read(
      "android-app/app/src/main/assets/terminal/appforge-repair-tools"
    );

  assert.match(
    repair,
    /dpkg --configure -a/
  );

  assert.match(
    repair,
    /apt-get -f install -y/
  );

  const dpkg =
    repair.indexOf(
      "dpkg --configure -a"
    );

  const update =
    repair.indexOf(
      "apt-get update"
    );

  assert.ok(
    dpkg >= 0 &&
      update > dpkg,
    "dpkg recovery must happen before apt-get update"
  );
});
