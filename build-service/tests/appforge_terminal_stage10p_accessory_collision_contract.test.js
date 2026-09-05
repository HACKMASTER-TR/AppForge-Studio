import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 10P reserves the floating IME accessory height inside terminal scroll content", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /var accessoryBarHeightPx by[\s\S]*?remember\(active\?\.id\)/
  );

  assert.match(
    source,
    /val keyboardVisible\s*=\s*imeInsets\.getBottom\(\s*accessoryDensity\s*\)\s*>\s*0/
  );

  assert.match(
    source,
    /val accessoryReservePx[\s\S]*?active\?\.running == true[\s\S]*?keyboardVisible[\s\S]*?accessoryBarHeightPx/
  );

  assert.doesNotMatch(
    source,
    /imeBottomPx/
  );

  assert.match(
    source,
    /bottomContentPaddingPx\s*=\s*accessoryReservePx/
  );

  assert.match(
    source,
    /\.onSizeChanged\s*\{\s*size\s*->[\s\S]*?accessoryBarHeightPx\s*=\s*size\.height/
  );

  assert.match(
    source,
    /bottomContentPaddingPx:\s*Int\s*=\s*0/
  );

  assert.match(
    source,
    /val bottomContentPadding[\s\S]*?bottomContentPaddingPx[\s\S]*?\.toDp\(\)/
  );

  assert.match(
    source,
    /Spacer\([\s\S]*?\.height\(\s*bottomContentPadding\s*\)/
  );
});

test("Stage 10P keeps terminal viewport independent from IME measurement", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.doesNotMatch(
    source,
    /\.imePadding\(\)/
  );

  assert.match(
    source,
    /\.offset\s*\{\s*IntOffset\([\s\S]*?y\s*=\s*-imeInsets\.getBottom\(this\)/
  );

  assert.match(
    source,
    /Modifier\.weight\(1f\)/
  );

  assert.match(
    source,
    /LaunchedEffect\(\s*surfaceSize,\s*fontSizeSp,\s*state\.id/
  );
});

test("Stage 10P preserves verified IME and productivity controls", async () => {
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
      `verified terminal key disappeared: ${key}`
    );
  }
});
