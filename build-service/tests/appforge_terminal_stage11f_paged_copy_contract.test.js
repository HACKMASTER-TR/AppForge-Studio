import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("Stage 11F freezes copy source and pages 500 lines at a time", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /var copySourceSnapshot by/
  );

  assert.match(
    source,
    /var copyWindowStart by/
  );

  assert.match(
    source,
    /fun updateCopyWindow\(/
  );

  assert.match(
    source,
    /copySourceSnapshot\s*=\s*\n\s*state\.snapshot/
  );

  assert.match(
    source,
    /\.subList\(\s*start,\s*end\s*\)/
  );

  assert.match(
    source,
    /COPY_MODE_MAX_LINES\s*=\s*\n\s*500/
  );
});

test("Stage 11F exposes previous and next copy pages", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.ok(
    source.includes('"← ÖNCEKİ 500"')
  );

  assert.ok(
    source.includes('"SONRAKİ 500 →"')
  );

  assert.match(
    source,
    /copyWindowStart\s*-\s*\n?\s*COPY_MODE_MAX_LINES/
  );

  assert.match(
    source,
    /copyWindowStart\s*\+\s*\n?\s*COPY_MODE_MAX_LINES/
  );

  assert.match(
    source,
    /copyScrollState[\s\S]{0,120}\.scrollTo\(0\)/
  );
});

test("Stage 11F never makes the complete 5000-line history selectable", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /SelectionContainer/
  );

  assert.match(
    source,
    /renderLocalPtyCopyText\(\s*frozenCopySnapshot\s*\)/
  );

  assert.doesNotMatch(
    source,
    /renderLocalPtyCopyText\(\s*state\.snapshot/
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

test("Stage 11F preserves verified terminal interaction architecture", async () => {
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

  assert.match(
    source,
    /firstVisibleItemIndex/
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
