import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("terminal workspace consumes only the bottom IME inset", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /val accessoryReservePx\s*=\s*0[\s\S]{0,260}Column\([\s\S]{0,180}\.fillMaxSize\(\)[\s\S]{0,220}\.windowInsetsPadding\(\s*WindowInsets\.ime\.only\(\s*WindowInsetsSides\.Bottom\s*\)\s*\)/
  );
});

test("bottom inset fix never restores the old manual full-IME translation", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.doesNotMatch(
    source,
    /y\s*=\s*-imeInsets\.getBottom\(this\)/
  );
  assert.doesNotMatch(
    source,
    /val imeOcclusionPx\s*=\s*imeInsets\.getBottom/
  );
});

test("BUG7C command I/O bridge remains intact", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.match(source, /TermuxTerminalMirrorRegistry\s*\.publish\(/);
  assert.match(source, /LocalPtySessionRegistry\s*\.write\(/);
  assert.match(source, /BasicTextField\(/);
});
