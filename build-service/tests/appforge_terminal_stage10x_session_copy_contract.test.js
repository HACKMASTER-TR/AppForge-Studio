import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 10X reuses the lowest available terminal number", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /\(1\.\.MAX_LOCAL_PTY_SESSIONS\)[\s\S]*?firstOrNull[\s\S]*?it !in used/
  );

  assert.doesNotMatch(
    source,
    /return\s+\(used\.maxOrNull\(\)\s*\?:\s*0\)\s*\+\s*1/
  );
});

test("Stage 10X allows the final terminal session to be closed", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.doesNotMatch(
    source,
    /if\s*\(\s*ptySessions\.size\s*>\s*1\s*\)/
  );

  assert.match(
    source,
    /closeThisSession\(\)/
  );

  assert.match(
    source,
    /LocalPtySessionRegistry[\s\S]*?\.closeSession/
  );
});

test("Stage 10X keeps copy mode as the first visible terminal shortcut", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  const copy =
    source.indexOf(
      '"KOPYA"'
    );

  const esc =
    source.indexOf(
      'PtyKey("ESC"'
    );

  assert.ok(
    copy >= 0,
    "KOPYA button missing"
  );

  assert.ok(
    esc > copy,
    "KOPYA must appear before ESC"
  );

  assert.match(
    source,
    /"YAZ"/
  );
});

test("Stage 10X gives SelectionContainer touch ownership in copy mode", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /\.pointerInput\(\s*state\.id,\s*copyMode\s*\)/
  );

  assert.match(
    source,
    /if\s*\(\s*!copyMode\s*\)\s*\{\s*detectTransformGestures/
  );

  assert.match(
    source,
    /\.clickable\(\s*enabled\s*=\s*!copyMode/
  );

  assert.match(
    source,
    /if\s*\(\s*copyMode\s*\)[\s\S]*?SelectionContainer/
  );
});

test("Stage 10X preserves fast normal terminal rendering", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
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
    /TerminalShortcutMatteGray/
  );

  assert.doesNotMatch(
    source,
    /\.imePadding\(\)/
  );
});
