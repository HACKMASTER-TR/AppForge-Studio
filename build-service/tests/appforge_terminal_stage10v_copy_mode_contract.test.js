import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 10V adds explicit terminal copy/write mode", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /var terminalCopyMode by/
  );

  assert.match(
    source,
    /if\s*\(\s*terminalCopyMode\s*\)\s*\{\s*"YAZ"\s*\}\s*else\s*\{\s*"KOPYA"/
  );

  assert.match(
    source,
    /terminalCopyMode\s*=\s*!terminalCopyMode/
  );

  assert.match(
    source,
    /copyMode\s*=\s*terminalCopyMode/
  );
});

test("Stage 10V keeps fast LazyColumn outside copy mode", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /if\s*\(\s*copyMode\s*\)[\s\S]*?SelectionContainer/
  );

  assert.match(
    source,
    /else\s*\{[\s\S]*?LazyColumn\(/
  );

  assert.match(
    source,
    /rememberLazyListState\(\)/
  );

  assert.match(
    source,
    /renderLocalPtyLine\(/
  );
});

test("Stage 10V makes copy mode selectable and hides the IME", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /renderLocalPtySnapshot\([\s\S]*?showCursor\s*=\s*false/
  );

  assert.match(
    source,
    /SelectionContainer/
  );

  assert.match(
    source,
    /keyboardController\?\.hide\(\)/
  );

  assert.match(
    source,
    /if\s*\(\s*!copyMode\s*\)[\s\S]*?keyboardController[\s\S]*?\.show\(\)/
  );
});

test("Stage 10V does not auto-follow while the user is selecting text", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /if\s*\(\s*copyMode\s*\)\s*\{\s*return@LaunchedEffect/
  );

  assert.match(
    source,
    /outputListState\.scrollToItem\(\s*lastIndex\s*\)/
  );
});

test("Stage 10V preserves IME stability and matte productivity keys", async () => {
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
    /TerminalShortcutMatteGray/
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
