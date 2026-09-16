import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const adapterUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt",
  import.meta.url
);

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test(
  "Termux mirror replays only after TerminalView creation",
  async () => {
    const source = await readFile(adapterUrl, "utf8");

    const host = source.indexOf(
      "internal fun TermuxTerminalCoreHost("
    );

    const mirrorHost = source.indexOf(
      "internal fun TermuxTerminalMirrorHost("
    );

    assert.ok(host >= 0);
    assert.ok(mirrorHost > host);

    const block = source.slice(host, mirrorHost);

    const createView = block.indexOf(
      ".createView("
    );

    const register = block.indexOf(
      "TermuxTerminalMirrorRegistry\n"
      + "                                        .register("
    );

    assert.ok(createView >= 0);
    assert.ok(register > createView);

    assert.ok(
      block.includes(
        "Replay only after TerminalView is attached"
      )
    );
  }
);

test(
  "Termux mirror reset clears backlog and native scrollback",
  async () => {
    const source = await readFile(adapterUrl, "utf8");

    assert.ok(
      source.includes(
        "fun reset(\n"
        + "        sessionId: String,"
      )
    );

    assert.ok(
      source.includes(
        "channel.backlog.setLength(0)"
      )
    );

    assert.ok(
      source.includes(
        "\\u001b[3J\\u001b[2J\\u001b[H"
      )
    );
  }
);

test(
  "local PTY restart resets mirror and ANSI buffer together",
  async () => {
    const source = await readFile(panelUrl, "utf8");

    const mirrorReset = source.indexOf(
      "TermuxTerminalMirrorRegistry\n"
      + "                        .reset(id)"
    );

    const bufferReset = source.indexOf(
      "current.buffer.reset()",
      mirrorReset
    );

    assert.ok(mirrorReset >= 0);
    assert.ok(bufferReset > mirrorReset);
  }
);
