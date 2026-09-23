import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const bufferUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt",
  import.meta.url
);

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

const unitUrl = new URL(
  "../../android-app/app/src/test/java/com/appforge/studio/terminal/AnsiTerminalBufferTest.kt",
  import.meta.url
);

test("terminal tracks DEC bracketed paste mode 2004", async () => {
  const source = await readFile(bufferUrl, "utf8");

  assert.ok(
    source.includes(
      "val bracketedPasteEnabled: Boolean = false"
    )
  );

  assert.ok(
    source.includes(
      "2004 -> bracketedPasteEnabled = true"
    )
  );

  assert.ok(
    source.includes(
      "2004 -> bracketedPasteEnabled = false"
    )
  );
});

test("IME wraps paste only while terminal mode 2004 is enabled", async () => {
  const source = await readFile(panelUrl, "utf8");

  assert.ok(
    source.includes(
      "bracketedPasteEnabled ="
    )
  );

  assert.ok(
    source.includes(
      "state.snapshot"
    )
  );

  assert.ok(
    source.includes(
      ".bracketedPasteEnabled"
    )
  );

  assert.ok(
    source.includes(
      "if (bracketedPasteEnabled)"
    )
  );

  assert.ok(
    source.includes(
      "ptyPasteText"
    )
  );
});

test("ANSI unit test covers bracketed paste enable disable and reset", async () => {
  const source = await readFile(unitUrl, "utf8");

  assert.ok(
    source.includes(
      "fun tracksBracketedPastePrivateMode()"
    )
  );

  assert.ok(
    source.includes(
      String.raw`\u001b[?2004h`
    )
  );

  assert.ok(
    source.includes(
      String.raw`\u001b[?2004l`
    )
  );
});
