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
  assert.doesNotMatch(source, /WindowInsets\.ime/);
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
