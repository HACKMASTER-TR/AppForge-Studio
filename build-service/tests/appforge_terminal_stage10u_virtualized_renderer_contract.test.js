import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 10U virtualizes terminal rows instead of laying out one huge Text", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /LazyColumn\(/
  );

  assert.match(
    source,
    /rememberLazyListState\(\)/
  );

  assert.match(
    source,
    /items\([\s\S]*?state\.snapshot\.lines\.indices/
  );

  assert.match(
    source,
    /renderLocalPtyLine\(/
  );

  assert.doesNotMatch(
    source,
    /SelectionContainer/
  );

  assert.doesNotMatch(
    source,
    /\.verticalScroll\(\s*outputScroll\s*\)/
  );
});

test("Stage 10U renders only individual visible PTY lines", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /private fun renderLocalPtyLine\(/
  );

  assert.match(
    source,
    /snapshot\.lines[\s\S]*?getOrNull\(\s*lineIndex\s*\)/
  );

  assert.doesNotMatch(
    source,
    /val rendered\s*=\s*remember\(state\.snapshot/
  );
});

test("Stage 10U follows new output with LazyListState", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /val lastIndex\s*=\s*state\.snapshot\.lines\.lastIndex/
  );

  assert.match(
    source,
    /outputListState\.scrollToItem\(\s*lastIndex\s*\)/
  );
});

test("Stage 10U preserves verified IME and shortcut behavior", async () => {
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

  assert.match(
    source,
    /TerminalShortcutMatteGray/
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
      source.includes(key),
      `verified key disappeared: ${key}`
    );
  }
});
