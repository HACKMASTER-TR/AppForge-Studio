import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("Stage 11D bounds selectable terminal copy history", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /COPY_MODE_MAX_LINES\s*=\s*\n\s*500/
  );

  assert.match(
    source,
    /COPY_MODE_CONTEXT_BEFORE_LINES\s*=\s*\n\s*80/
  );

  assert.match(
    source,
    /firstVisibleItemIndex/
  );

  assert.match(
    source,
    /\.subList\(\s*start,\s*end\s*\)/
  );

  assert.match(
    source,
    /copySnapshot/
  );

  assert.match(
    source,
    /copyRangeLabel/
  );
});

test("Stage 11D freezes copy content instead of selecting live 5000-line history", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /val frozenCopySnapshot\s*=\s*\n\s*copySnapshot/
  );

  assert.match(
    source,
    /renderLocalPtySnapshot\(\s*snapshot\s*=\s*\n\s*frozenCopySnapshot/
  );

  assert.doesNotMatch(
    source,
    /val selectableOutput\s*=\s*[\s\S]{0,250}renderLocalPtySnapshot\(\s*snapshot\s*=\s*\n\s*state\.snapshot/
  );

  assert.match(
    source,
    /copySnapshot\s*=\s*\n\s*null/
  );
});

test("Stage 11D preserves extended virtualized normal scrollback", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /MAX_RENDERED_PTY_HISTORY_LINES\s*=\s*\n\s*5_000/
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
    /outputListState\.isScrollInProgress/
  );

  assert.match(
    source,
    /SelectionContainer/
  );
});

test("Stage 11D preserves verified IME and paste behavior", async () => {
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
    /localPtySeparateConsecutivePaste/
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


test("Stage 11D declares copy bounds exactly once", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.equal(
    (
      source.match(
        /private const val COPY_MODE_MAX_LINES/g
      ) ?? []
    ).length,
    1
  );

  assert.equal(
    (
      source.match(
        /private const val COPY_MODE_CONTEXT_BEFORE_LINES/g
      ) ?? []
    ).length,
    1
  );
});
