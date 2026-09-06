import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test(
  "Stage 11N keeps single-line trailing-submit protection",
  async () => {
    const source =
      await readFile(panelUrl, "utf8");

    assert.match(
      source,
      /private fun localPtySuppressSingleLinePasteSubmit\(/
    );

    assert.match(
      source,
      /normalized\.trimEnd\(\s*'\\n'/
    );

    assert.match(
      source,
      /return withoutTrailingNewlines/
    );
  }
);

test(
  "Stage 11N single-line protection is used by bracketed paste",
  async () => {
    const source =
      await readFile(panelUrl, "utf8");

    const start =
      source.indexOf(
        "private fun localPtyBracketedPasteDispatch("
      );

    assert.ok(start >= 0);

    const block =
      source.slice(
        start,
        start + 7000
      );

    assert.match(
      block,
      /localPtySuppressSingleLinePasteSubmit\(\s*normalized/
    );

    assert.match(
      block,
      /LOCAL_PTY_BRACKETED_PASTE_START/
    );
  }
);

test(
  "Stage 11N preserves terminal IME copy and shortcuts",
  async () => {
    const source =
      await readFile(panelUrl, "utf8");

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
        `shortcut disappeared: ${key}`
      );
    }
  }
);
