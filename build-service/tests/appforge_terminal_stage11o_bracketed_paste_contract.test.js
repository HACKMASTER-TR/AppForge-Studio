import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test(
  "Stage 11O wraps bulk clipboard input in bracketed paste",
  async () => {
    const source =
      await readFile(panelUrl, "utf8");

    assert.match(
      source,
      /LOCAL_PTY_BRACKETED_PASTE_START\s*=\s*\n\s*"\\u001b\[200~"/
    );

    assert.match(
      source,
      /LOCAL_PTY_BRACKETED_PASTE_END\s*=\s*\n\s*"\\u001b\[201~"/
    );

    assert.match(
      source,
      /LOCAL_PTY_BRACKETED_PASTE_START \+\s*pastePayload \+\s*LOCAL_PTY_BRACKETED_PASTE_END/
    );
  }
);

test(
  "Stage 11O does not auto-submit multiline paste",
  async () => {
    const source =
      await readFile(panelUrl, "utf8");

    assert.match(
      source,
      /val bulkPaste =/
    );

    assert.match(
      source,
      /normalized\.contains\(\s*'\\n'/
    );

    assert.match(
      source,
      /pendingPaste =\s*pastePayload/
    );
  }
);

test(
  "Stage 11O converts explicit Enter after paste to one PTY Enter",
  async () => {
    const source =
      await readFile(panelUrl, "utf8");

    assert.match(
      source,
      /pendingPaste != null &&\s*normalized == "\\n"[\s\S]*?ptyText = "\\r"/
    );
  }
);

test(
  "Stage 11O suppresses Gboard full-paste replay on Enter",
  async () => {
    const source =
      await readFile(panelUrl, "utf8");

    assert.match(
      source,
      /normalized == pendingPaste[\s\S]*?ptyText = ""/
    );

    assert.match(
      source,
      /normalized ==\s*pendingPaste \+ "\\n"[\s\S]*?ptyText = "\\r"/
    );
  }
);

test(
  "Stage 11O resets hidden IME state after bulk paste",
  async () => {
    const source =
      await readFile(panelUrl, "utf8");

    assert.match(
      source,
      /if \(\s*dispatch\.resetIme/
    );

    assert.match(
      source,
      /text =\s*LOCAL_PTY_IME_SENTINEL/
    );
  }
);
