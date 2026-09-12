import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test(
  "completed command prompt remains visible without typing viewport jumps",
  async () => {
    const source =
      await readFile(
        sourceUrl,
        "utf8"
      );

    // Structural output AND prompt/cursor-row movement can trigger follow.
    assert.match(
      source,
      /LaunchedEffect\(\s*state\.id,\s*state\.snapshot\.lines\.size,\s*state\.snapshot\.cursorLine,\s*directInputRevision\s*\)/
    );

    // Never return to outputRevision-triggered scrolling.
    assert.doesNotMatch(
      source,
      /LaunchedEffect\([\s\S]{0,180}state\.outputRevision/
    );

    // Copy/select mode must never be stolen by auto-follow.
    assert.match(
      source,
      /if\s*\(\s*copyMode\s*\)\s*\{\s*return@LaunchedEffect/
    );

    // User scrolling history remains protected.
    assert.match(
      source,
      /outputListState\.isScrollInProgress/
    );

    // Already-visible prompts stay completely untouched.
    assert.match(
      source,
      /if\s*\(\s*lastRowAlreadyVisible\s*\)\s*\{\s*return@LaunchedEffect/
    );

    // A hidden final prompt is positioned near the usable viewport bottom.
    assert.match(
      source,
      /outputListState\.scrollToItem\(\s*index\s*=\s*lastIndex,\s*scrollOffset\s*=\s*-targetTopPx\s*\)/
    );

    // Existing IME/accessory reserve behavior remains intact.
    assert.match(
      source,
      /bottomContentPaddingPx\.coerceAtLeast\(0\)/
    );
  }
);
