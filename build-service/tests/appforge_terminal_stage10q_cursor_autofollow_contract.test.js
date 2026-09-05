import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test("Stage 10Q follows new terminal output with virtualized scrolling", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /rememberLazyListState\(\)/
  );

  assert.match(
    source,
    /LaunchedEffect\(\s*state\.outputRevision,\s*bottomContentPaddingPx,\s*state\.snapshot\.lines\.size\s*\)/
  );

  assert.match(
    source,
    /delay\(16L\)/
  );

  assert.match(
    source,
    /outputListState\.scrollToItem\(\s*lastIndex\s*\)/
  );
});

test("Stage 10Q keeps IME reserve inside lazy-list content padding", async () => {
  const source =
    await readFile(
      sourceUrl,
      "utf8"
    );

  assert.match(
    source,
    /LazyColumn\(/
  );

  assert.match(
    source,
    /state\s*=\s*outputListState/
  );

  assert.match(
    source,
    /contentPadding\s*=\s*PaddingValues\([\s\S]*?bottom\s*=\s*bottomContentPadding/
  );

  assert.doesNotMatch(
    source,
    /\.verticalScroll\(\s*outputScroll\s*\)/
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
