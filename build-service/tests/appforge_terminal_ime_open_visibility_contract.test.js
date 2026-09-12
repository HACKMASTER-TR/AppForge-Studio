import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test(
  "opening the keyboard keeps the final terminal row visible without typing jumps",
  async () => {
    const source =
      await readFile(
        sourceUrl,
        "utf8"
      );

    assert.match(
      source,
      /previousBottomContentPaddingPx/
    );

    assert.match(
      source,
      /LaunchedEffect\(\s*state\.id,\s*bottomContentPaddingPx\s*\)/
    );

    assert.match(
      source,
      /currentPaddingPx\s*<=\s*previousPaddingPx/
    );

    assert.match(
      source,
      /copyMode/
    );

    assert.match(
      source,
      /outputListState\.isScrollInProgress/
    );

    assert.match(
      source,
      /lastRowAlreadyVisible/
    );

    assert.match(
      source,
      /outputListState\.scrollToItem\(\s*index\s*=\s*lastIndex,\s*scrollOffset\s*=\s*-targetTopPx\s*\)/
    );

    // Typing must still not drive auto-follow.
    assert.doesNotMatch(
      source,
      /LaunchedEffect\([\s\S]{0,180}state\.outputRevision/
    );
  }
);
