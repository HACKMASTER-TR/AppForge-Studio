import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 10R reserves the same IME distance used by the floating shortcut row", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /val imeOcclusionPx\s*=\s*imeInsets\.getBottom\(\s*accessoryDensity\s*\)/
  );

  assert.match(
    source,
    /val accessoryReservePx[\s\S]*?imeOcclusionPx/
  );

  assert.match(
    source,
    /\.offset\s*\{\s*IntOffset\([\s\S]*?y\s*=\s*-imeInsets\.getBottom\(this\)/
  );

  assert.doesNotMatch(
    source,
    /accessoryBarHeightPx/
  );
});

test("Stage 10R follows both output and the final recalculated scroll range", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /LaunchedEffect\(\s*state\.outputRevision,\s*bottomContentPaddingPx\s*\)/
  );

  assert.match(
    source,
    /LaunchedEffect\(\s*outputScroll\.maxValue\s*\)/
  );

  const scrollCalls =
    source.match(
      /outputScroll\.scrollTo\(\s*outputScroll\.maxValue\s*\)/g
    ) ?? [];

  assert.ok(
    scrollCalls.length >= 2,
    "expected output follow and layout-range follow"
  );
});

test("Stage 10R does not resize the PTY from IME occlusion", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.doesNotMatch(
    source,
    /\.imePadding\(\)/
  );

  assert.doesNotMatch(
    source,
    /LaunchedEffect\([\s\S]{0,150}imeOcclusionPx[\s\S]{0,300}LocalPtySessionRegistry\.resize/
  );

  assert.match(
    source,
    /LaunchedEffect\(\s*surfaceSize,\s*fontSizeSp,\s*state\.id/
  );

  assert.doesNotMatch(
    source,
    /imeBottomPx/
  );
});

test("Stage 10R preserves verified keyboard and shell controls", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
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
