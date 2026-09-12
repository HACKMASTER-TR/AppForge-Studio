import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test(
  "terminal typing and shortcut redraws do not retrigger viewport auto-follow",
  async () => {
    const source = await readFile(sourceUrl, "utf8");

    // Auto-follow may react to structural line changes,
    // not every echoed character / Readline redraw.
    assert.match(
      source,
      /LaunchedEffect\(\s*state\.id,\s*state\.snapshot\.lines\.size,\s*state\.snapshot\.cursorLine\s*\)/
    );

    assert.doesNotMatch(
      source,
      /LaunchedEffect\(\s*state\.outputRevision/
    );

    assert.doesNotMatch(
      source,
      /LaunchedEffect\(\s*state\.outputRevision,\s*bottomContentPaddingPx/
    );

    // Text selection/copy mode must never be pulled away by auto-follow.
    assert.match(
      source,
      /if\s*\(\s*copyMode\s*\)\s*\{\s*return@LaunchedEffect/
    );

    // Manual scrolling must remain respected.
    assert.match(
      source,
      /outputListState\.isScrollInProgress/
    );

    // If the last row is already visible, do not move the viewport.
    assert.match(
      source,
      /lastRowAlreadyVisible/
    );

    // Position the active row near the real bottom instead of jumping
    // the requested item to the top of the viewport.
    assert.match(
      source,
      /val targetTopPx\s*=/
    );

    assert.match(
      source,
      /outputListState\.scrollToItem\(\s*index\s*=\s*lastIndex,\s*scrollOffset\s*=\s*-targetTopPx\s*\)/
    );

    assert.doesNotMatch(
      source,
      /outputListState\.scrollToItem\(\s*lastIndex\s*\)/
    );
  }
);
