import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 10Q follows new terminal output to the real scroll bottom", async () => {
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
    /delay\(16L\)/
  );

  assert.match(
    source,
    /outputScroll\.scrollTo\(\s*outputScroll\.maxValue\s*\)/
  );
});

test("Stage 10Q creates physical scroll space beneath the active prompt", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /SelectionContainer\s*\{\s*Column\(/
  );

  assert.match(
    source,
    /\.verticalScroll\(\s*outputScroll\s*\)/
  );

  assert.match(
    source,
    /Spacer\([\s\S]*?\.height\(\s*bottomContentPadding\s*\)/
  );

  assert.doesNotMatch(
    source,
    /bottom\s*=\s*bottomContentPadding/
  );
});

test("Stage 10Q preserves stable IME accessory architecture", async () => {
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
    /imeBottomPx/
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
