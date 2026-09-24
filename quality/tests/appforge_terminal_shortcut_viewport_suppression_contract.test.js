import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test(
  "direct terminal shortcuts share one dispatcher",
  async () => {
    const source =
      await readFile(
        sourceUrl,
        "utf8"
      );

    assert.match(
      source,
      /fun dispatchDirectPtyInput\(\s*text:\s*String\s*\)/
    );

    const requiredSequences = [
      "\\u001b",
      "\\t",
      "\\u0003",
      "\\u000c",
      "\\u0001",
      "\\u0005",
      "\\u0012",
      "\\u0015",
      "\\u0017",
      "\\u007f",
      "\\r",
      "\\u001b[D",
      "\\u001b[A",
      "\\u001b[B",
      "\\u001b[C"
    ];

    for (const sequence of requiredSequences) {
      assert.ok(
        source.includes(sequence),
        `PTY sequence bulunamadı: ${sequence}`
      );
    }

    for (const label of [
      "ESC",
      "TAB",
      "CTRL+C",
      "CTRL+L",
      "CTRL+A",
      "CTRL+E",
      "CTRL+R",
      "CTRL+U",
      "CTRL+W",
      "⌫",
      "↵",
      "←",
      "↑",
      "↓",
      "→"
    ]) {
      assert.ok(
        source.includes(`PtyKey("${label}", true)`),
        `${label} shortcut bulunamadı`
      );
    }
  }
);

test(
  "cursor-only redraw after direct shortcut does not auto-follow",
  async () => {
    const source =
      await readFile(
        sourceUrl,
        "utf8"
      );

    assert.match(
      source,
      /LaunchedEffect\(\s*state\.id,\s*state\.snapshot\.lines\.size,\s*state\.snapshot\.cursorLine,\s*directInputRevision\s*\)/
    );

    assert.ok(
      source.includes(
        "suppressNextCursorOnlyAutoFollow"
      )
    );

    assert.match(
      source,
      /!lineCountChanged\s*&&\s*cursorLineChanged\s*&&\s*suppressNextCursorOnlyAutoFollow/
    );

    assert.doesNotMatch(
      source,
      /LaunchedEffect\([^)]*state\.outputRevision/
    );
  }
);
