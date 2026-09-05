import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

const bufferUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt",
    import.meta.url
  );

test("Stage 10W restores persisted PTY lines with CRLF semantics", async () => {
  const panel =
    await readFile(
      panelUrl,
      "utf8"
    );

  assert.match(
    panel,
    /persistedSnapshot[\s\S]*?\.replace\(\s*"\\r\\n",\s*"\\n"\s*\)[\s\S]*?\.replace\(\s*"\\r",\s*"\\n"\s*\)[\s\S]*?\.replace\(\s*"\\n",\s*"\\r\\n"\s*\)/
  );

  assert.match(
    panel,
    /buffer\.feed\([\s\S]*?persistedSnapshot/
  );
});

test("Stage 10W keeps carriage return and line feed terminal semantics separate", async () => {
  const buffer =
    await readFile(
      bufferUrl,
      "utf8"
    );

  assert.match(
    buffer,
    /'\\r'\s*->\s*cursorColumn\s*=\s*0/
  );

  assert.match(
    buffer,
    /'\\n'\s*->\s*lineFeed\(\)/
  );
});

test("Stage 10W does not regress virtualized normal rendering", async () => {
  const panel =
    await readFile(
      panelUrl,
      "utf8"
    );

  assert.match(
    panel,
    /LazyColumn\(/
  );

  assert.match(
    panel,
    /rememberLazyListState\(\)/
  );

  assert.match(
    panel,
    /TerminalShortcutMatteGray/
  );

  assert.doesNotMatch(
    panel,
    /\.imePadding\(\)/
  );

  assert.match(
    panel,
    /value\s*=\s*imeValue/
  );
});

test("Stage 10W preserves explicit copy mode", async () => {
  const panel =
    await readFile(
      panelUrl,
      "utf8"
    );

  assert.match(
    panel,
    /terminalCopyMode/
  );

  assert.match(
    panel,
    /"KOPYA"/
  );

  assert.match(
    panel,
    /"YAZ"/
  );

  assert.match(
    panel,
    /if\s*\(\s*copyMode\s*\)[\s\S]*?SelectionContainer/
  );
});
