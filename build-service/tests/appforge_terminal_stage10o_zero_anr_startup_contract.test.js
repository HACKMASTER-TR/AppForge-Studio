import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

test("Stage 10O keeps terminal entry off heavy automatic toolchain work", async () => {
  const source =
    await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalDevelopmentEnvironment.kt"
    );

  assert.match(
    source,
    /fun ensure\([\s\S]*?scope\.launch\s*\{[\s\S]*?start\(/
  );

  assert.match(
    source,
    /fun retry\([\s\S]*?scope\.launch\s*\{[\s\S]*?start\(/
  );

  assert.match(
    source,
    /fun prepareTools\(/
  );

  const startBegin =
    source.indexOf(
      "    private fun start("
    );

  const toolsBegin =
    source.indexOf(
      "    private fun startDevelopmentToolsInBackground("
    );

  assert.ok(startBegin >= 0);
  assert.ok(toolsBegin > startBegin);

  const startupBody =
    source.slice(
      startBegin,
      toolsBegin
    );

  assert.doesNotMatch(
    startupBody,
    /startDevelopmentToolsInBackground\(/
  );

  assert.match(
    source,
    /Do NOT launch apt\/dpkg automatically/
  );
});

test("Stage 10O restores PTY registry away from the UI dispatcher", async () => {
  const source =
    await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
    );

  assert.match(
    source,
    /withContext\(\s*Dispatchers\.IO\s*\)\s*\{\s*ensureSession\(/
  );

  assert.match(
    source,
    /withContext\(\s*Dispatchers\.IO\s*\)\s*\{\s*markActivated\(id\)/
  );

  assert.match(
    source,
    /withContext\(Dispatchers\.IO\)[\s\S]*?AppForgePtyBridge\.spawn/
  );
});

test("Stage 10O preserves Stage 10N output batching and verified IME", async () => {
  const source =
    await read(
      "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
    );

  assert.match(
    source,
    /scheduleOutputPublishLocked/
  );

  assert.match(
    source,
    /OUTPUT_PUBLISH_INTERVAL_MS/
  );

  assert.match(
    source,
    /CharArray\(8_192\)/
  );

  assert.doesNotMatch(
    source,
    /\.imePadding\(\)/
  );

  assert.match(
    source,
    /WindowInsets\.ime/
  );

  assert.match(
    source,
    /value\s*=\s*imeValue/
  );

  assert.match(
    source,
    /autoCorrectEnabled\s*=\s*false/
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
