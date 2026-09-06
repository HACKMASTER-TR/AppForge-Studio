import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("Stage 11E copy mode renders plain text instead of rich ANSI snapshot", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /private fun renderLocalPtyCopyText\(/
  );

  assert.match(
    source,
    /val selectableOutput\s*=\s*[\s\S]{0,180}renderLocalPtyCopyText\(/
  );

  assert.doesNotMatch(
    source,
    /val selectableOutput\s*=\s*[\s\S]{0,250}renderLocalPtySnapshot\(/
  );
});

test("Stage 11E copy renderer does not create per-cell Compose styles", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  const start =
    source.indexOf(
      "private fun renderLocalPtyCopyText("
    );

  const end =
    source.indexOf(
      "private fun renderLocalPtySnapshot(",
      start
    );

  assert.ok(start >= 0);
  assert.ok(end > start);

  const copyRenderer =
    source.slice(start, end);

  assert.doesNotMatch(
    copyRenderer,
    /withStyle\(/
  );

  assert.doesNotMatch(
    copyRenderer,
    /SpanStyle\(/
  );

  assert.doesNotMatch(
    copyRenderer,
    /AnnotatedString/
  );

  assert.match(
    copyRenderer,
    /buildString/
  );

  assert.match(
    copyRenderer,
    /\.character/
  );
});

test("Stage 11E preserves bounded selection and extended scrollback", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /COPY_MODE_MAX_LINES\s*=\s*\n\s*500/
  );

  assert.match(
    source,
    /SelectionContainer/
  );

  assert.match(
    source,
    /MAX_RENDERED_PTY_HISTORY_LINES\s*=\s*\n\s*5_000/
  );

  assert.match(
    source,
    /LazyColumn\(/
  );
});

test("Stage 11E preserves terminal IME and shortcuts", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.doesNotMatch(
    source,
    /\.imePadding\(\)/
  );

  assert.match(
    source,
    /pendingMultilinePasteBoundary/
  );

  for (const key of [
    '"KOPYA"',
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
