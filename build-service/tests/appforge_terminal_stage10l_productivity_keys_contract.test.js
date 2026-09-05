import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("Stage 10L exposes shell productivity keys on the main real PTY", async () => {
  const source = await readFile(sourceUrl, "utf8");

  for (const key of [
    '"CTRL+A"',
    '"CTRL+E"',
    '"CTRL+R"',
    '"CTRL+U"',
    '"CTRL+W"'
  ]) {
    assert.ok(
      source.includes(key),
      `missing main PTY productivity key ${key}`
    );
  }

  for (const sequence of [
    '"\\u0001"',
    '"\\u0005"',
    '"\\u0012"',
    '"\\u0015"',
    '"\\u0017"'
  ]) {
    assert.ok(
      source.includes(sequence),
      `missing PTY control sequence ${sequence}`
    );
  }

  assert.match(
    source,
    /LocalPtySessionRegistry\.write/
  );
});

test("Stage 10L keeps the verified IME accessory architecture intact", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.doesNotMatch(
    source,
    /\.imePadding\(\)/
  );

  assert.match(
    source,
    /val imeInsets\s*=\s*WindowInsets\.ime/
  );

  assert.match(
    source,
    /\.offset\s*\{\s*IntOffset\([\s\S]*?y\s*=\s*-imeInsets\.getBottom\(this\)/
  );

  assert.match(
    source,
    /value\s*=\s*imeValue/
  );

  assert.match(
    source,
    /autoCorrectEnabled\s*=\s*false/
  );
});

test("Stage 10L preserves existing critical terminal keys", async () => {
  const source = await readFile(sourceUrl, "utf8");

  for (const key of [
    '"ESC"',
    '"TAB"',
    '"CTRL+C"',
    '"CTRL+L"',
    '"⌫"',
    '"↵"',
    '"←"',
    '"↑"',
    '"↓"',
    '"→"'
  ]) {
    assert.ok(
      source.includes(key),
      `existing terminal key disappeared: ${key}`
    );
  }
});
