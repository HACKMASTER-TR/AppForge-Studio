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
  "Termux mirror registers only after TerminalView creation",
  async () => {
    const source =
      await readFile(
        adapterUrl,
        "utf8"
      );

    const host =
      source.indexOf(
        "internal fun TermuxTerminalCoreHost("
      );

    const mirrorHost =
      source.indexOf(
        "internal fun TermuxTerminalMirrorHost("
      );

    assert.ok(host >= 0);
    assert.ok(mirrorHost > host);

    const hostBlock =
      source.slice(
        host,
        mirrorHost
      );

    const createView =
      hostBlock.indexOf(
        ".createView("
      );

    const ensureRegistered =
      hostBlock.indexOf(
        ".ensureRegistered("
      );

    assert.ok(createView >= 0);

    /*
     * Native TerminalView must be attached first.
     * Only then may backlog replay/register occur.
     */
    assert.ok(
      ensureRegistered > createView
    );

    const registry =
      source.indexOf(
        "object TermuxTerminalMirrorControllerRegistry"
      );

    const registryEnd =
      source.indexOf(
        "object TermuxTerminalMirrorRegistry",
        registry
      );

    assert.ok(registry >= 0);
    assert.ok(registryEnd > registry);

    const registryBlock =
      source.slice(
        registry,
        registryEnd
      );

    assert.ok(
      registryBlock.includes(
        "fun ensureRegistered("
      )
    );

    assert.ok(
      registryBlock.includes(
        "TermuxTerminalMirrorRegistry"
      )
    );

    assert.ok(
      registryBlock.includes(
        ".register("
      )
    );

    assert.ok(
      registryBlock.includes(
        "Called only after TerminalView has attached"
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
