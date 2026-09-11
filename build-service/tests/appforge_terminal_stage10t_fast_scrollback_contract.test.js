import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

test("Stage 10T limits live Compose terminal history without removing buffer scrollback", async () => {
  const assert =
    (await import("node:assert/strict")).default;

  const { readFile } =
    await import("node:fs/promises");

  const [pty, buffer] =
    await Promise.all([
      readFile(
        new URL("../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt", import.meta.url),
        "utf8"
      ),
      readFile(
        new URL("../../android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt", import.meta.url),
        "utf8"
      )
    ]);

  assert.match(
    pty,
    /MAX_RENDERED_PTY_HISTORY_LINES[\s\S]*5_000/
  );

  assert.match(
    pty,
    /maxHistoryLines[\s\S]*MAX_RENDERED_PTY_HISTORY_LINES/
  );

  assert.match(
    buffer,
    /maxScrollbackLines:[\s\S]*20_000/
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

  assert.match(
    panel,
    /if\s*\(\s*copyMode\s*\)[\s\S]*?SelectionContainer/
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
