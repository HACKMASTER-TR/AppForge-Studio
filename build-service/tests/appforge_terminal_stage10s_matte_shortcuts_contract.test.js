import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 10S gives terminal shortcut keys a matte gray surface", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /ButtonDefaults\.outlinedButtonColors/
  );

  assert.match(
    source,
    /TerminalShortcutMatteGray/
  );

  assert.match(
    source,
    /Color\(0xFF4A4F55\)/
  );

  assert.match(
    source,
    /TerminalShortcutMatteText/
  );

  assert.match(
    source,
    /Color\(0xFFF2F3F4\)/
  );
});

test("Stage 10S keeps verified terminal controls and IME behavior intact", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.doesNotMatch(
    source,
    /\.imePadding\(\)/
  );

  assert.match(
    source,
    /value\s*=\s*imeValue/
  );

  assert.match(
    source,
    /autoCorrectEnabled\s*=\s*false/
  );

  for (const key of [
    '"ESC"',
    '"TAB"',
    '"CTRL+C"',
    '"CTRL+L"',
    '"CTRL+A"',
    '"CTRL+E"',
    '"CTRL+R"',
    '"CTRL+U"',
    '"CTRL+W"',
    '"⌫"'
  ]) {
    assert.ok(
      source.includes(key),
      `shortcut missing: ${key}`
    );
  }
});
