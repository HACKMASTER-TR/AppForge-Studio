import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const bufferUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt",
  import.meta.url
);

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("Stage 11C retains 5000 terminal scrollback lines", async () => {
  const source =
    await readFile(bufferUrl, "utf8");

  assert.match(
    source,
    /maxScrollbackLines:\s*Int\s*=\s*5_000/
  );

  assert.match(
    source,
    /scrollback\.size\s*>\s*maxScrollbackLines/
  );

  assert.match(
    source,
    /scrollback\.removeFirst\(\)/
  );
});

test("Stage 11C exposes the full retained history to the terminal viewport", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /MAX_RENDERED_PTY_HISTORY_LINES\s*=\s*\n\s*5_000/
  );

  assert.match(
    source,
    /maxHistoryLines\s*=\s*\n\s*MAX_RENDERED_PTY_HISTORY_LINES/
  );
});

test("Stage 11C keeps normal terminal rendering virtualized", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /LazyColumn\(/
  );

  assert.match(
    source,
    /state\.snapshot\.lines\.indices\s*\n\s*\.toList\(\)/
  );

  assert.match(
    source,
    /rememberLazyListState\(\)/
  );

  assert.match(
    source,
    /outputListState\.isScrollInProgress/
  );
});

test("Stage 11C preserves more terminal history across app restart", async () => {
  const source =
    await readFile(panelUrl, "utf8");

  assert.match(
    source,
    /MAX_PERSISTED_SNAPSHOT_CHARS\s*=\s*12_288/
  );
});

test("Stage 11C preserves Stage 11A IME and interaction contracts", async () => {
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
    /AUTO_FOLLOW_MARGIN_LINES/
  );

  assert.match(
    source,
    /softWrap\s*=\s*false/
  );

  assert.match(
    source,
    /maxLines\s*=\s*1/
  );
});
