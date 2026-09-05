import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 11A yields auto-follow while the user scrolls", async () => {
  const source =
    await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /outputListState\.isScrollInProgress/
  );

  assert.match(
    source,
    /visibleItemsInfo/
  );

  assert.match(
    source,
    /AUTO_FOLLOW_MARGIN_LINES/
  );

  assert.match(
    source,
    /wasNearBottom/
  );
});

test("Stage 11A keeps visible terminal rows cheap to compose", async () => {
  const source =
    await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /remember\(\s*line,\s*cursorColumnForLine\s*\)/
  );

  assert.match(
    source,
    /softWrap\s*=\s*false/
  );

  assert.match(
    source,
    /maxLines\s*=\s*1/
  );

  assert.match(
    source,
    /\.height\(\s*terminalRowHeight\s*\)/
  );

  assert.match(
    source,
    /AnnotatedString\.Builder\(\)/
  );

  assert.match(
    source,
    /flushRun/
  );
});

test("Stage 11A separates consecutive multiline pastes safely", async () => {
  const source =
    await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /pendingMultilinePasteBoundary/
  );

  assert.match(
    source,
    /localPtySeparateConsecutivePaste/
  );

  assert.match(
    source,
    /localPtyLeavesOpenMultilinePaste/
  );

  assert.match(
    source,
    /return "\\r" \+ delta/
  );
});

test("Stage 11A preserves verified IME and terminal controls", async () => {
  const source =
    await readFile(sourceUrl, "utf8");

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
    /SelectionContainer/
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
