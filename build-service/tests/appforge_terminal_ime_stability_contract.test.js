import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("PTY terminal does not re-layout the whole panel from IME padding", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.doesNotMatch(source, /\.imePadding\(\)/);
  assert.match(
    source,
    /val imeInsets\s*=\s*WindowInsets\.ime/
  );

  assert.match(
    source,
    /\.offset\s*\{\s*IntOffset\(\s*x\s*=\s*0,\s*y\s*=\s*-imeInsets\.getBottom\(this\)\s*\)\s*\}/
  );

  assert.doesNotMatch(source, /imeBottomPx/);
});

test("PTY terminal lets the user dismiss the keyboard without forced focus restore", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.doesNotMatch(source, /\.onFocusChanged\s*\{/);
  assert.doesNotMatch(source, /FOCUS_RESTORE_DELAY_MS/);
  assert.doesNotMatch(source, /FOCUS_RESTORE_GUARD_MS/);

  assert.match(
    source,
    /\.focusRequester\(\s*inputFocusRequester\s*\)/
  );

  assert.match(
    source,
    /keyboardController\?\.show\(\)/
  );
});
