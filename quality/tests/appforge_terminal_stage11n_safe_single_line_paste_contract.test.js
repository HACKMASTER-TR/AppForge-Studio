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

test("Stage 11N single-line protection is used by bracketed paste", async () => {
  const assert =
    (await import("node:assert/strict")).default;

  const { readFile } =
    await import("node:fs/promises");

  const pty =
    await readFile(
      new URL("../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt", import.meta.url),
      "utf8"
    );

  assert.match(
    pty,
    /localPtyBracketedPasteDispatch/
  );

  assert.match(
    pty,
    /pendingPaste\s*!=\s*null/
  );

  assert.match(
    pty,
    /normalized\.length\s*==\s*1/
  );

  assert.match(
    pty,
    /LOCAL_PTY_BRACKETED_PASTE_START/
  );

  assert.match(
    pty,
    /LOCAL_PTY_BRACKETED_PASTE_END/
  );
});

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
