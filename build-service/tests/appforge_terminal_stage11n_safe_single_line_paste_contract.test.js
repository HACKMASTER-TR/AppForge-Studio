import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test(
  "Stage 11N suppresses automatic submit for a pasted single-line command",
  async () => {
    const source =
      await readFile(
        panelUrl,
        "utf8"
      );

    assert.match(
      source,
      /private fun localPtySuppressSingleLinePasteSubmit\(/
    );

    assert.match(
      source,
      /if \(delta\.length <= 1\)/
    );

    assert.match(
      source,
      /normalized\.endsWith\('\\n'\)/
    );

    assert.match(
      source,
      /normalized\.trimEnd\(\s*'\\n'/
    );

    assert.match(
      source,
      /withoutTrailingNewlines\.contains\(\s*'\\n'/
    );

    assert.match(
      source,
      /return withoutTrailingNewlines/
    );
  }
);

test(
  "Stage 11N applies safe paste handling before PTY write",
  async () => {
    const source =
      await readFile(
        panelUrl,
        "utf8"
      );

    const safeIndex =
      source.indexOf(
        "val submitSafeDelta"
      );

    const consecutiveIndex =
      source.indexOf(
        "localPtySeparateConsecutivePaste(",
        safeIndex
      );

    assert.ok(
      safeIndex >= 0 &&
      consecutiveIndex > safeIndex
    );

    const block =
      source.slice(
        safeIndex,
        consecutiveIndex + 500
      );

    assert.match(
      block,
      /localPtySuppressSingleLinePasteSubmit\(\s*rawDelta/
    );

    assert.match(
      block,
      /delta =\s*submitSafeDelta/
    );
  }
);

test(
  "Stage 11N keeps explicit keyboard Enter and multiline paste behavior",
  async () => {
    const source =
      await readFile(
        panelUrl,
        "utf8"
      );

    /*
     * One-character newline is returned untouched,
     * so Gboard/terminal Enter still executes.
     */
    assert.match(
      source,
      /if \(delta\.length <= 1\) \{\s*return delta/
    );

    /*
     * Multiline payloads remain untouched.
     */
    assert.match(
      source,
      /withoutTrailingNewlines\.contains\(\s*'\\n'[\s\S]*?return normalized/
    );

    assert.match(
      source,
      /localPtyLeavesOpenMultilinePaste\(\s*submitSafeDelta/
    );

    assert.match(
      source,
      /localPtySeparateConsecutivePaste/
    );
  }
);

test(
  "Stage 11N preserves terminal IME copy and shortcut architecture",
  async () => {
    const source =
      await readFile(
        panelUrl,
        "utf8"
      );

    assert.doesNotMatch(
      source,
      /\.imePadding\(\)/
    );

    assert.match(
      source,
      /LOCAL_PTY_IME_SENTINEL/
    );

    assert.match(
      source,
      /COPY_MODE_MAX_LINES\s*=\s*\n\s*500/
    );

    assert.match(
      source,
      /MAX_RENDERED_PTY_HISTORY_LINES\s*=\s*\n\s*5_000/
    );

    for (const key of [
      '"KOPYA"',
      '"APK"',
      '"DASH"',
      '"CTRL+C"',
      '"CTRL+A"',
      '"CTRL+E"',
      '"CTRL+R"',
      '"CTRL+U"',
      '"CTRL+W"',
      '"⌫"',
      '"↵"'
    ]) {
      assert.ok(
        source.includes(key),
        `terminal shortcut disappeared: ${key}`
      );
    }
  }
);
